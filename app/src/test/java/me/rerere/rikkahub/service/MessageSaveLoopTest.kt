package me.rerere.rikkahub.service

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageSaveLoopTest {
    private val mb = 1024L * 1024

    @Test
    fun `interval follows the encoded content size`() {
        assertEquals(5_000L, MessageSavePolicy.intervalMs(100 * 1024))
        assertEquals(15_000L, MessageSavePolicy.intervalMs(1024 * 1024))
        assertEquals(30_000L, MessageSavePolicy.intervalMs(3 * 1024 * 1024))
    }

    @Test
    fun `small ungrown node waits for max staleness`() {
        val now = 1_000_000L
        assertFalse(MessageSavePolicy.shouldSave(now + 4_999, now, 10_000, 0, 512 * mb))
        assertFalse(MessageSavePolicy.shouldSave(now + 5_000, now, 10_000, 0, 512 * mb))
        assertTrue(MessageSavePolicy.shouldSave(now + 60_000, now, 10_000, 0, 512 * mb))
    }

    @Test
    fun `growth triggers at the tier interval`() {
        val now = 1_000_000L
        assertTrue(MessageSavePolicy.shouldSave(now + 5_000, now, 100_000, 0, 512 * mb))
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
        advanceTimeBy(11_000)
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
    fun `estimate failure is caught like a save failure`() = runTest {
        var estimateFails = true
        var errors = 0
        val loop = MessageSaveLoop(
            estimateChars = {
                if (estimateFails) throw IllegalStateException("estimate")
                100_000L
            },
            save = { 1L },
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
}
