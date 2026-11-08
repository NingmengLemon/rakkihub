package me.rerere.rikkahub.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.ServerToolStatus
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.MessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageSaveLoopTest {
    private val mb = 1024L * 1024

    @Test
    fun `interval follows the encoded content size`() {
        assertEquals(5_000L, MessageSavePolicy.intervalMs(100 * 1024))
        assertEquals(5_000L, MessageSavePolicy.intervalMs(200_000))
        // 280KB 内容进 15 秒档；与按字符实现的差异由下面的 tier selection 用例覆盖
        assertEquals(15_000L, MessageSavePolicy.intervalMs(280_000))
        assertEquals(15_000L, MessageSavePolicy.intervalMs(1024 * 1024))
        assertEquals(30_000L, MessageSavePolicy.intervalMs(3 * 1024 * 1024))
        assertEquals(30_000L, MessageSavePolicy.intervalMs(2_500_000))
    }

    @Test
    fun `tier selection uses encoded bytes not characters`() {
        val now = 1_000_000L
        // 140K 字符是 280KB 内容，10 秒还不到 15 秒档；按字符算会在 5 秒档直接写
        assertFalse(MessageSavePolicy.shouldSave(now + 10_000, now, 140_000, 0, 512 * mb))
        assertTrue(MessageSavePolicy.shouldSave(now + 15_000, now, 140_000, 0, 512 * mb))
    }

    @Test
    fun `small ungrown node waits for max staleness`() {
        val now = 1_000_000L
        assertFalse(MessageSavePolicy.shouldSave(now + 4_999, now, 10_000, 0, 512 * mb))
        assertFalse(MessageSavePolicy.shouldSave(now + 5_000, now, 10_000, 0, 512 * mb))
        assertTrue(MessageSavePolicy.shouldSave(now + 60_000, now, 10_000, 0, 512 * mb))
    }

    @Test
    fun `growth is measured in encoded bytes`() {
        val now = 1_000_000L
        assertTrue(MessageSavePolicy.shouldSave(now + 5_000, now, 100_000, 0, 512 * mb))
        // 50K 字符的增长是 100KB 字节，按字节过阈值；旧的按字符判断会漏掉
        assertTrue(MessageSavePolicy.shouldSave(now + 5_000, now, 100_000, 50_000, 512 * mb))
        assertFalse(MessageSavePolicy.shouldSave(now + 14_999, now, 600_000, 0, 512 * mb))
        assertTrue(MessageSavePolicy.shouldSave(now + 15_000, now, 600_000, 0, 512 * mb))
    }

    @Test
    fun `heap guard refuses when the estimate needs more memory`() {
        val now = 1_000_000L
        assertFalse(MessageSavePolicy.shouldSave(now + 60_000, now, 10_000, 0, 15 * mb))
        assertTrue(MessageSavePolicy.shouldSave(now + 60_000, now, 10_000, 0, 16 * mb))
        val medium = 5 * 1024 * 1024L
        assertFalse(MessageSavePolicy.shouldSave(now + 30_000, now, medium, 0, 40 * mb))
        assertTrue(MessageSavePolicy.shouldSave(now + 30_000, now, medium, 0, 50 * mb))
    }

    @Test
    fun `size cap boundary is inclusive`() {
        val now = 1_000_000L
        val atCap = 7_864_320L
        assertEquals(16 * mb, MessageSavePolicy.estimatedBytes(atCap))
        assertTrue(MessageSavePolicy.shouldSave(now + 60_000, now, atCap, 0, 512 * mb))
        assertFalse(MessageSavePolicy.shouldSave(now + 60_000, now, atCap + 1, 0, 512 * mb))
    }

    @Test
    fun `nodes above the size cap are skipped even with heap headroom`() {
        val now = 1_000_000L
        val tooBig = 8 * 1024 * 1024L
        assertFalse(MessageSavePolicy.shouldSave(now + 60_000, now, tooBig, 0, 512 * mb))
    }

    @Test
    fun `loop writes once the interval and growth allow it`() = runTest {
        var chars = 100_000L
        var saves = 0
        val loop = MessageSaveLoop(
            estimateChars = { chars },
            save = {
                saves++
                chars
            },
            availableBytes = { 512 * mb },
            now = { testScheduler.currentTime },
        )
        backgroundScope.launch { loop.run() }

        advanceTimeBy(4_999)
        assertEquals(0, saves)
        advanceTimeBy(1_001)
        assertEquals(1, saves)
        advanceTimeBy(4_000)
        assertEquals(1, saves)
        chars += 100_000
        // 内容过了 256KB，档位从 5 秒变成 15 秒
        advanceTimeBy(1_000)
        assertEquals(1, saves)
        advanceTimeBy(9_001)
        assertEquals(2, saves)
    }

    @Test
    fun `loop does not retry a skipped save every second`() = runTest {
        var attempts = 0
        val loop = MessageSaveLoop(
            estimateChars = { 100_000L },
            save = {
                attempts++
                null
            },
            availableBytes = { 512 * mb },
            now = { testScheduler.currentTime },
        )
        backgroundScope.launch { loop.run() }

        advanceTimeBy(5_001)
        assertEquals(1, attempts)
        advanceTimeBy(1_000)
        assertEquals(1, attempts)
        advanceTimeBy(4_000)
        assertEquals(2, attempts)
    }

    @Test
    fun `oversized node reports at most once a minute`() = runTest {
        var reports = 0
        val loop = MessageSaveLoop(
            estimateChars = { 10 * 1024 * 1024L },
            save = { 1L },
            availableBytes = { 512 * mb },
            now = { testScheduler.currentTime },
            onTooLarge = { reports++ },
        )
        backgroundScope.launch { loop.run() }

        advanceTimeBy(1_001)
        assertEquals(1, reports)
        advanceTimeBy(1_000)
        assertEquals(1, reports)
        advanceTimeBy(60_000)
        assertEquals(2, reports)
    }

    @Test
    fun `loop backs off after a failure and keeps running`() = runTest {
        var chars = 100_000L
        var attempts = 0
        var errors = 0
        val loop = MessageSaveLoop(
            estimateChars = { chars },
            save = {
                attempts++
                if (attempts == 1) throw IllegalStateException("boom")
                chars
            },
            availableBytes = { 512 * mb },
            now = { testScheduler.currentTime },
            onError = { errors++ },
        )
        backgroundScope.launch { loop.run() }

        advanceTimeBy(5_001)
        assertEquals(1, attempts)
        assertEquals(1, errors)
        advanceTimeBy(9_000)
        assertEquals(1, attempts)
        advanceTimeBy(1_000)
        assertEquals(2, attempts)
    }

    @Test
    fun `estimate failure is caught and saving resumes after backoff`() = runTest {
        var estimateFails = true
        var errors = 0
        var saves = 0
        val loop = MessageSaveLoop(
            estimateChars = {
                if (estimateFails) throw IllegalStateException("estimate")
                100_000L
            },
            save = {
                saves++
                1L
            },
            availableBytes = { 512 * mb },
            now = { testScheduler.currentTime },
            onError = { errors++ },
        )
        backgroundScope.launch { loop.run() }

        advanceTimeBy(1_001)
        assertEquals(1, errors)
        estimateFails = false
        advanceTimeBy(10_000)
        assertEquals(1, errors)
        assertEquals(1, saves)
    }

    @Test
    fun `cancellation from save is rethrown without onError`() = runTest {
        var errors = 0
        val loop = MessageSaveLoop(
            estimateChars = { 100_000L },
            save = { throw CancellationException("stop") },
            availableBytes = { 512 * mb },
            now = { testScheduler.currentTime },
            onError = { errors++ },
        )
        val job = backgroundScope.launch { loop.run() }

        advanceTimeBy(5_001)

        assertTrue(job.isCancelled)
        assertEquals(0, errors)
    }

    @Test
    fun `loop stops on cancellation`() = runTest {
        val loop = MessageSaveLoop(
            estimateChars = { 100_000L },
            save = { 1L },
            availableBytes = { 512 * mb },
            now = { testScheduler.currentTime },
        )
        val job = backgroundScope.launch { loop.run() }
        advanceTimeBy(1_000)
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }

    @Test
    fun `node estimate sums primitive lengths recursively`() {
        val parts = listOf(
            UIMessagePart.Text("abc"), // 3
            UIMessagePart.Reasoning("hello"), // 5
            UIMessagePart.Tool(
                toolCallId = "call",
                toolName = "tool",
                input = "xy", // 2
                metadata = JsonObject(mapOf("k" to JsonPrimitive("v"))), // 1 + 1
                output = listOf(UIMessagePart.Text("out")), // 3
            ),
            UIMessagePart.ServerTool(
                toolCallId = "server",
                toolName = "server",
                input = JsonPrimitive("srv"), // 3
                output = JsonArray(listOf(JsonPrimitive("x"))), // 1
                status = ServerToolStatus.COMPLETED,
            ),
            UIMessagePart.Document("u", "f"), // 1 + 1
        )
        val node = MessageNode.of(UIMessage(role = MessageRole.ASSISTANT, parts = parts))

        assertEquals(21L, estimateNodeChars(node))
        assertEquals(0L, estimateNodeChars(null))
    }
}
