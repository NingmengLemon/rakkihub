package me.rerere.rikkahub.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.MessageNode
import kotlin.math.max

/** 当前回复节点的字符估算，不拼接字符串；供定期保存的节奏与堆守卫使用。 */
internal fun estimateNodeChars(node: MessageNode?): Long =
    node?.messages?.sumOf { message -> message.parts.sumOf { it.approximateChars() } } ?: 0L

private fun UIMessagePart.approximateChars(): Long = when (this) {
    is UIMessagePart.Text -> text.length.toLong()
    is UIMessagePart.Reasoning -> reasoning.length.toLong()
    is UIMessagePart.Tool ->
        input.length.toLong() + metadata.approximateChars() + output.sumOf { it.approximateChars() }
    is UIMessagePart.ServerTool ->
        input.approximateChars() + output.approximateChars()
    is UIMessagePart.Document -> (url.length + fileName.length).toLong()
    else -> 0
}

// 逐层累加，不对整个 JsonElement 调 toString()，避免每个 tick 复制大块 JSON
private fun JsonElement?.approximateChars(): Long = when (this) {
    null, is JsonNull -> 0L
    is JsonPrimitive -> content.length.toLong()
    is JsonObject -> entries.sumOf { (key, value) -> key.length.toLong() + value.approximateChars() }
    is JsonArray -> sumOf { it.approximateChars() }
}

/** 生成期间定期保存回复节点的节奏与堆守卫，纯函数便于单测。 */
internal object MessageSavePolicy {
    private const val SMALL_LIMIT_BYTES = 256L * 1024
    private const val MEDIUM_LIMIT_BYTES = 2L * 1024 * 1024
    internal const val MAX_SAVE_BYTES = 16L * 1024 * 1024
    private const val SMALL_INTERVAL_MS = 5_000L
    private const val MEDIUM_INTERVAL_MS = 15_000L
    private const val LARGE_INTERVAL_MS = 30_000L
    private const val MIN_GROWTH_BYTES = 64L * 1024
    private const val MAX_STALENESS_MS = 60_000L
    private const val MIN_FREE_BYTES = 16L * 1024 * 1024
    private const val OVERHEAD_BYTES = 1024L * 1024

    /** 编码后的内容字节估算，用于档位与增长判断 */
    fun contentBytes(chars: Long): Long = chars * 2

    /** 编码期间还要复制和绑定实体，给堆守卫留 1MB 余量 */
    fun estimatedBytes(chars: Long): Long = contentBytes(chars) + OVERHEAD_BYTES

    fun intervalMs(contentBytes: Long): Long = when {
        contentBytes < SMALL_LIMIT_BYTES -> SMALL_INTERVAL_MS
        contentBytes < MEDIUM_LIMIT_BYTES -> MEDIUM_INTERVAL_MS
        else -> LARGE_INTERVAL_MS
    }

    fun availableBytes(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
    }

    fun shouldSave(
        now: Long,
        lastSavedAt: Long,
        estimateChars: Long,
        lastSavedChars: Long,
        availableBytes: Long,
    ): Boolean {
        // 超过 16MB 的节点不写：行本身可能读不回来，留给最终全量写并记录
        if (estimatedBytes(estimateChars) > MAX_SAVE_BYTES) return false
        val contentBytes = contentBytes(estimateChars)
        val elapsed = now - lastSavedAt
        if (elapsed < intervalMs(contentBytes)) return false
        val growth = contentBytes - contentBytes(lastSavedChars)
        if (growth < MIN_GROWTH_BYTES && elapsed < MAX_STALENESS_MS) return false
        val required = max(MIN_FREE_BYTES, estimatedBytes(estimateChars) * 4)
        return availableBytes >= required
    }
}

/**
 * 生成期间的定期保存循环。依赖全部可注入：[pause] 在测试里换成虚拟时间，
 * [availableBytes] 与 [now] 换成固定值。
 */
internal class MessageSaveLoop(
    private val estimateChars: () -> Long,
    private val save: suspend () -> Long?,
    private val availableBytes: () -> Long = MessageSavePolicy::availableBytes,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val pause: suspend (Long) -> Unit = { delay(it) },
    private val onError: (Throwable) -> Unit = {},
    private val onTooLarge: (Long) -> Unit = {},
) {
    suspend fun run() {
        var lastSavedAt = now()
        var lastSavedChars = 0L
        var lastTooLargeAt = now() - 60_000
        var backoffUntil = 0L
        while (currentCoroutineContext().isActive) {
            pause(1_000)
            val time = now()
            if (time < backoffUntil) continue
            try {
                val estimate = estimateChars()
                if (estimate <= 0) continue
                if (MessageSavePolicy.estimatedBytes(estimate) > MessageSavePolicy.MAX_SAVE_BYTES) {
                    // 节点过大时每分钟记一次，别让日志和回调每秒刷
                    if (time - lastTooLargeAt >= 60_000) {
                        onTooLarge(estimate)
                        lastTooLargeAt = time
                    }
                    lastSavedAt = time
                    continue
                }
                if (!MessageSavePolicy.shouldSave(time, lastSavedAt, estimate, lastSavedChars, availableBytes())) {
                    continue
                }
                val storedChars = save()
                // 跳过的这次也按一次尝试计时，避免每秒重复拷贝和扫描；成功后记录新的大小
                lastSavedAt = time
                if (storedChars != null) {
                    lastSavedChars = estimate
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                onError(t)
                backoffUntil = time + 10_000
            }
        }
    }
}
