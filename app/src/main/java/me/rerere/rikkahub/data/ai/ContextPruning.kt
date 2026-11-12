package me.rerere.rikkahub.data.ai

import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.estimatedTokens

/**
 * 发送前的请求级上下文裁剪。只改这次请求的消息副本，不碰会话树和落盘。
 *
 * 预算按模型的 `contextLength` 留出 2048 给系统提示词、工具定义和格式开销，最低 1024。
 * 超过预算时先截断最近 [KEEP_RECENT_MESSAGES] 条之外的工具输出，再把同一范围内的图片换成
 * 占位文本；最近几条不动，避免把当前工具链或用户刚发的图片裁掉。工具输出只有在截断后真的
 * 变短、或内部带非文本 part 时才重写，避免 1025 到 2000 字符的输出加前缀反而变大。
 * 裁剪完仍超预算时不再继续处理，裁剪结果照发，交给 C63 overflow 恢复或 provider 错误。
 */
internal data class PrunedContext(
    val messages: List<UIMessage>,
    val toolResultCount: Int,
    val imageCount: Int,
)

private const val CONTEXT_RESERVE_TOKENS = 2048
private const val MIN_CONTEXT_BUDGET = 1024
private const val KEEP_RECENT_MESSAGES = 6
private const val MAX_RETAINED_TOOL_OUTPUT_CHARS = 2_000
private const val TOOL_OUTPUT_PREFIX = "[Tool output truncated to fit the context window]\n"
private const val TOOL_OUTPUT_PARTS_PLACEHOLDER = "[Tool output parts omitted to fit the context window]"
private const val IMAGE_PLACEHOLDER = "[Image omitted to fit the context window]"

internal fun pruneContextForRequest(
    messages: List<UIMessage>,
    contextLength: Int,
): PrunedContext {
    if (contextLength <= 0) return PrunedContext(messages, 0, 0)
    val budget = (contextLength - CONTEXT_RESERVE_TOKENS).coerceAtLeast(MIN_CONTEXT_BUDGET)
    if (messages.estimatedTokens() <= budget) return PrunedContext(messages, 0, 0)
    val cutoff = (messages.size - KEEP_RECENT_MESSAGES).coerceAtLeast(0)
    if (cutoff <= 0) return PrunedContext(messages, 0, 0)

    val current = messages.toMutableList()
    var toolResultCount = 0
    var imageCount = 0

    // 先裁工具输出。大的旧输出对后续推理价值最低，保留前 2000 字符给模型一点上下文。
    for (index in 0 until cutoff) {
        val message = current[index]
        var changed = false
        val parts = message.parts.map { part ->
            if (part is UIMessagePart.Tool) {
                val output = part.output
                val text = toolOutputText(output)
                val nonTextParts = output.filterNot { it is UIMessagePart.Text }
                val retainedText = TOOL_OUTPUT_PREFIX + text.take(MAX_RETAINED_TOOL_OUTPUT_CHARS)
                val shouldPrune = nonTextParts.isNotEmpty() || retainedText.length < text.length
                if (shouldPrune) {
                    changed = true
                    toolResultCount++
                    imageCount += nonTextParts.count { it is UIMessagePart.Image }
                    part.copy(
                        output = buildList {
                            add(UIMessagePart.Text(retainedText))
                            if (nonTextParts.isNotEmpty()) {
                                add(UIMessagePart.Text(TOOL_OUTPUT_PARTS_PLACEHOLDER))
                            }
                        }
                    )
                } else {
                    part
                }
            } else {
                part
            }
        }
        if (changed) {
            current[index] = message.copy(parts = parts)
            if (current.estimatedTokens() <= budget) {
                return PrunedContext(current, toolResultCount, imageCount)
            }
        }
    }

    // 图片比工具输出更难替代，放在第二阶段，且只换旧消息里的图。
    for (index in 0 until cutoff) {
        val message = current[index]
        var changed = false
        val parts = message.parts.map { part ->
            if (part is UIMessagePart.Image) {
                changed = true
                imageCount++
                UIMessagePart.Text(IMAGE_PLACEHOLDER)
            } else {
                part
            }
        }
        if (changed) {
            current[index] = message.copy(parts = parts)
            if (current.estimatedTokens() <= budget) break
        }
    }

    if (toolResultCount == 0 && imageCount == 0) return PrunedContext(messages, 0, 0)
    return PrunedContext(current, toolResultCount, imageCount)
}

private fun toolOutputText(output: List<UIMessagePart>): String =
    output.filterIsInstance<UIMessagePart.Text>().joinToString("\n") { it.text }
