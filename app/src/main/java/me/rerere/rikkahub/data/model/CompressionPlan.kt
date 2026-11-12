package me.rerere.rikkahub.data.model

import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.filterModelVisible
import me.rerere.ai.ui.limitContext
import kotlin.uuid.Uuid

/**
 * 压缩轮次的计划。必须在插入可见提示词之前算好，请求只使用 [messagesToSend]，
 * 否则提示词会被当成普通历史，或者在 keepRecent 时被重复计算。
 */
internal data class CompressionPlan(
    val messagesToSend: List<UIMessage>,
    val anchorNodeId: Uuid,
)

internal sealed interface CompressionPlanResult {
    data class Ready(val plan: CompressionPlan) : CompressionPlanResult

    data object NothingToCompress : CompressionPlanResult

    data object PendingTools : CompressionPlanResult

    /** 待摘要的历史按本地估算已经超过模型上下文，单次摘要请求放不下。 */
    data object RangeTooLarge : CompressionPlanResult
}

/** 面板和服务共用的压缩计划：预算、范围检查都从这里走，避免两处各算一遍。 */
internal fun Conversation.planCompressionFor(
    model: Model,
    assistant: Assistant,
    keepRecent: Int,
): CompressionPlanResult {
    val summaryMaxTokens = compressionSummaryMaxTokens(model.contextLength, assistant.maxTokens)
    return planCompression(
        windowStart = contextWindowStart(),
        keepRecent = keepRecent,
        maxInputTokens = compressionInputBudget(model.contextLength, summaryMaxTokens),
        contextMessageLimit = assistant.contextMessageLimit,
    )
}

internal data class CompressionPlanCounts(
    val summarized: Int,
    val kept: Int,
)

/** 面板预览用实际边界算条数：保留起点会对齐到 user，摘要起点在检查点之后。 */
internal fun Conversation.compressionPlanCounts(plan: CompressionPlan): CompressionPlanCounts? {
    val end = messageNodes.indexOfFirst { it.id == plan.anchorNodeId }
        .takeIf { it >= 0 }
        ?.plus(1)
        ?: return null
    val windowStart = contextWindowStart()
    return CompressionPlanCounts(
        summarized = (end - windowStart).coerceAtLeast(0),
        kept = (messageNodes.size - end).coerceAtLeast(0),
    )
}

/** 流式期间只有最后的 usage，tok/s 先从已生成的正文/思考估算；最终 usage 到达后取较大值。 */
internal fun UIMessage.streamedTokens(): Int = parts
    .filter { it is UIMessagePart.Text || it is UIMessagePart.Reasoning }
    .sumOf { it.estimatedTokens() }

/**
 * 以 [windowStart] 为当前请求窗口起点，保留最后 [keepRecent] 条不算在压缩范围里。
 *
 * 保留段不能从一条 assistant/tool 消息开始：那会把产生它的 user 压进摘要，压缩后的请求
 * 就没有 user 了。这里把保留起点向前对齐到最近一条 user，保留段只会变大，不会变小。
 * 范围里有未完成或可继续执行的工具调用时拒绝，避免请求被服务端拒绝或压掉还能跑的工具。
 * [maxInputTokens] 是待摘要历史按实际请求窗口的估算上限，超过时返回
 * [CompressionPlanResult.RangeTooLarge]，由调用方提示用户先截断或多保留，而不是把必然溢出的
 * 请求发给 provider。
 */
internal fun Conversation.planCompression(
    windowStart: Int,
    keepRecent: Int,
    maxInputTokens: Int = Int.MAX_VALUE,
    contextMessageLimit: Int = 0,
): CompressionPlanResult {
    var endIndex = messageNodes.size - keepRecent.coerceAtLeast(0)
    if (endIndex <= windowStart) return CompressionPlanResult.NothingToCompress
    if (endIndex < messageNodes.size && messageNodes[endIndex].currentMessage.role != MessageRole.USER) {
        val userIndex = (endIndex - 1 downTo windowStart).firstOrNull { index ->
            messageNodes[index].currentMessage.role == MessageRole.USER
        }
        if (userIndex != null) endIndex = userIndex
        if (endIndex <= windowStart) return CompressionPlanResult.NothingToCompress
    }
    val range = messageNodes.subList(windowStart, endIndex)
    if (range.any { node ->
            node.currentMessage.getTools().any { it.isPending || it.canResumeExecution }
        }
    ) {
        return CompressionPlanResult.PendingTools
    }
    // 摘要请求实际从最后一个检查点开始发，检查点之前的历史不会进入请求；预算也按这个窗口算，
    // 否则第二次压缩会把旧历史重复算一遍，误报 RangeTooLarge。条数上限和请求构建保持一致。
    val estimateStart = if (windowStart > 0) windowStart - 1 else 0
    val messagesToSend = currentMessages.subList(estimateStart, endIndex)
    if (maxInputTokens != Int.MAX_VALUE) {
        val estimated = messagesToSend
            .filterModelVisible()
            .limitContext(contextMessageLimit)
            .estimatedTokens()
        if (estimated > maxInputTokens) return CompressionPlanResult.RangeTooLarge
    }
    return CompressionPlanResult.Ready(
        CompressionPlan(
            messagesToSend = messagesToSend,
            anchorNodeId = messageNodes[endIndex - 1].id,
        )
    )
}

/**
 * 摘要输出上限。取模型上下文的一小部分，夹在 1024 到 4096 之间；assistant 自己设了更小的
 * 上限时尊重它。摘要请求以前 `maxTokens = null`，由 provider 默认值决定，输出被截断时只能
 * 整次失败；现在有明确预算，截断后也可以退回部分摘要。4096 是保守默认，避免超过小输出模型的
 * 服务端上限。
 */
internal fun compressionSummaryMaxTokens(contextLength: Int, assistantMaxTokens: Int? = null): Int {
    val cap = (contextLength / 8).coerceIn(1024, 4096)
    return assistantMaxTokens?.takeIf { it > 0 }?.let { minOf(it, cap) } ?: cap
}

/**
 * 待摘要历史可用的估算 token 上限。预留 2048 给系统提示词、工具定义、压缩提示词和格式开销；
 * 这是保守估算，不替代 provider 的真实 tokenizer。
 */
internal fun compressionInputBudget(contextLength: Int, summaryMaxTokens: Int): Int =
    (contextLength - summaryMaxTokens - 2048).coerceAtLeast(0)

/** 粗略 token 估算：CJK 字符按 1 token，其它非空白字符按 4 字符/token，向上取整。 */
internal fun estimateTokens(text: String): Int {
    if (text.isEmpty()) return 0
    var cjk = 0
    var other = 0
    for (char in text) {
        if (char.code >= 0x2E80) {
            cjk++
        } else if (!char.isWhitespace()) {
            other++
        }
    }
    return cjk + (other + 3) / 4
}

/** 图片/视频按固定预算；文档按固定 4096（不解析文件内容，转换器后续还会追加全文）；工具按内容估算。 */
@Suppress("DEPRECATION")
internal fun UIMessagePart.estimatedTokens(): Int = when (this) {
    is UIMessagePart.Text -> estimateTokens(text)
    is UIMessagePart.Image -> 1024
    is UIMessagePart.Video -> 1024
    is UIMessagePart.Audio -> 512
    is UIMessagePart.Document -> 4096 + estimateTokens(fileName)
    is UIMessagePart.Reasoning -> estimateTokens(reasoning)
    is UIMessagePart.Tool -> 16 + estimateTokens(input) + output.sumOf { it.estimatedTokens() }
    is UIMessagePart.ServerTool ->
        16 + estimateTokens(input?.toString().orEmpty()) + estimateTokens(output?.toString().orEmpty())
    is UIMessagePart.ToolCall -> 16 + estimateTokens(arguments)
    is UIMessagePart.ToolResult -> 16 + estimateTokens(content.toString())
    UIMessagePart.Search -> 16
}

internal fun UIMessage.estimatedTokens(): Int = 4 + parts.sumOf { it.estimatedTokens() }

internal fun List<UIMessage>.estimatedTokens(): Int = sumOf { it.estimatedTokens() }

/**
 * 压缩摘要的 finish reason 是否表示被截断。提供商的写法不同：`length`、`max_tokens`、
 * `incomplete:max_output_tokens`、单纯 `incomplete` 都算，按包含关系归一化。
 */
internal fun isTruncatedCompressionFinish(reason: String?): Boolean {
    val normalized = reason?.lowercase() ?: return false
    return normalized.contains("length") ||
        normalized.contains("max_tokens") ||
        normalized.contains("max_output_tokens") ||
        normalized.contains("max_completion_tokens") ||
        normalized.contains("incomplete")
}
