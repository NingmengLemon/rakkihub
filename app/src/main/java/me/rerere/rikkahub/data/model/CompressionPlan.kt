package me.rerere.rikkahub.data.model

import me.rerere.ai.ui.UIMessage
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
}

/**
 * 以 [windowStart] 为当前请求窗口起点，保留最后 [keepRecent] 条不算在压缩范围里。
 * 范围里有未完成或可继续执行的工具调用时拒绝，避免请求被服务端拒绝或压掉还能跑的工具。
 */
internal fun Conversation.planCompression(windowStart: Int, keepRecent: Int): CompressionPlanResult {
    val endIndex = messageNodes.size - keepRecent.coerceAtLeast(0)
    if (endIndex <= windowStart) return CompressionPlanResult.NothingToCompress
    val range = messageNodes.subList(windowStart, endIndex)
    if (range.any { node ->
            node.currentMessage.getTools().any { it.isPending || it.canResumeExecution }
        }
    ) {
        return CompressionPlanResult.PendingTools
    }
    return CompressionPlanResult.Ready(
        CompressionPlan(
            messagesToSend = currentMessages.take(endIndex),
            anchorNodeId = messageNodes[endIndex - 1].id,
        )
    )
}

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
