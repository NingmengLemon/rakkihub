package me.rerere.rikkahub.data.model

import me.rerere.ai.ui.filterModelVisible
import me.rerere.ai.ui.withoutCompressionScratch

internal const val CONTEXT_PRESSURE_SUGGEST_RATIO = 0.8f

/**
 * 当前请求窗口的上下文水位。
 *
 * [usedTokens] 优先来自最近一次真实请求的 usage；如果窗口里没有可用 usage，或者最近的
 * usage 在最后一个检查点之前（压缩后旧 usage 已经不能代表当前窗口），退回本地估算。
 * 检查点本身会计入，因为请求会带着摘要一起发。
 */
internal data class ContextPressure(
    val usedTokens: Int,
    val limitTokens: Int,
    val ratio: Float,
    val fromUsage: Boolean,
) {
    val shouldSuggestCompression: Boolean
        get() = ratio >= CONTEXT_PRESSURE_SUGGEST_RATIO
}

internal fun Conversation.contextPressure(limitTokens: Int): ContextPressure? {
    if (limitTokens <= 0) return null
    val messages = currentMessages.filterModelVisible().withoutCompressionScratch()
    val checkpointIndex = messages.indexOfLast { it.isContextCheckpoint }
    val currentWindow = if (checkpointIndex >= 0) {
        messages.subList(checkpointIndex, messages.size)
    } else {
        messages
    }
    // 检查点按插入顺序在保留段之前，但保留段里的旧回复 createdAt 更早；只接受检查点之后创建的
    // usage，否则压缩/截断后 banner 还会按旧比例常亮。
    val checkpoint = currentWindow.firstOrNull { it.isContextCheckpoint }
    val usageIndex = currentWindow.indexOfLast { message ->
        (message.contextTokens() ?: 0) > 0 &&
            (checkpoint == null || message.createdAt > checkpoint.createdAt)
    }
    val usedTokens = if (usageIndex >= 0) {
        currentWindow[usageIndex].contextTokens()!! +
            currentWindow.subList(usageIndex + 1, currentWindow.size).estimatedTokens()
    } else {
        currentWindow.estimatedTokens()
    }
    val used = usedTokens.coerceAtLeast(0)
    return ContextPressure(
        usedTokens = used,
        limitTokens = limitTokens,
        ratio = used.toFloat() / limitTokens,
        fromUsage = usageIndex >= 0,
    )
}
