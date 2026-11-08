package me.rerere.rikkahub.service

import kotlinx.coroutines.CancellationException
import me.rerere.ai.agent.AgentFinishReason
import me.rerere.ai.ui.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class SettlementStatusTest {
    @Test
    fun `finished turn stays complete after a later cancellation`() {
        assertEquals(
            MessageStatus.COMPLETE,
            resolveSettlementStatus(CancellationException("late"), AgentFinishReason.Completed),
        )
    }

    @Test
    fun `unexpected end without finish reason is failed`() {
        assertEquals(MessageStatus.FAILED, resolveSettlementStatus(null, null))
    }

    @Test
    fun `cancellation without finish reason is interrupted`() {
        assertEquals(
            MessageStatus.INTERRUPTED,
            resolveSettlementStatus(CancellationException("stopped"), null),
        )
    }

    @Test
    fun `ordinary failure is failed`() {
        assertEquals(
            MessageStatus.FAILED,
            resolveSettlementStatus(IllegalStateException("boom"), null),
        )
    }

    @Test
    fun `finished turn keeps complete even when a failure follows`() {
        assertEquals(
            MessageStatus.COMPLETE,
            resolveSettlementStatus(IllegalStateException("after finish"), AgentFinishReason.Completed),
        )
    }
}
