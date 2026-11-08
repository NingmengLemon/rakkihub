package me.rerere.rikkahub.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
        input.length.toLong() + (metadata?.toString()?.length ?: 0).toLong() +
            output.sumOf { it.approximateChars() }
    is UIMessagePart.ServerTool ->
        (input?.toString()?.length ?: 0).toLong() + (output?.toString()?.length ?: 0).toLong()
    is UIMessagePart.Document -> (url.length + fileName.length).toLong()
    else -> 0
}

/** 生成期间定期保存回复节点的节奏与堆守卫，纯函数便于单测。 */
internal object MessageSavePolicy {
    private const val SMALL_LIMIT_CHARS = 256L * 1024
    private const val MEDIUM_LIMIT_CHARS = 2L * 1024 * 1024
    private const val SMALL_INTERVAL_MS = 5_000L
    private const val MEDIUM_INTERVAL_MS = 15_000L
    private const val LARGE_INTERVAL_MS = 30_000L
    private const val MIN_GROWTH_CHARS = 64L * 1024
    private const val MAX_STALENESS_MS = 60_000L
    private const val MIN_FREE_BYTES = 16L * 1024 * 1024
    private const val OVERHEAD_BYTES = 1024L * 1024

    fun estimatedBytes(chars: Long): Long = chars * 2 + OVERHEAD_BYTES

    fun intervalMs(chars: Long): Long = when {
        chars < SMALL_LIMIT_CHARS -> SMALL_INTERVAL_MS
        chars < MEDIUM_LIMIT_CHARS -> MEDIUM_INTERVAL_MS
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
        val elapsed = now - lastSavedAt
        if (elapsed < intervalMs(estimateChars)) return false
        val growth = estimateChars - lastSavedChars
        if (growth < MIN_GROWTH_CHARS && elapsed < MAX_STALENESS_MS) return false
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
) {
    suspend fun run() {
        var lastSavedAt = now()
        var lastSavedChars = 0L
        var backoffUntil = 0L
        while (currentCoroutineContext().isActive) {
            pause(1_000)
            val time = now()
            if (time < backoffUntil) continue
            val estimate = estimateChars()
            if (estimate <= 0) continue
            if (!MessageSavePolicy.shouldSave(time, lastSavedAt, estimate, lastSavedChars, availableBytes())) {
                continue
            }
            val storedChars = try {
                save()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                onError(t)
                backoffUntil = time + 10_000
                continue
            }
            if (storedChars != null) {
                lastSavedAt = time
                lastSavedChars = estimate
            }
        }
    }
}
