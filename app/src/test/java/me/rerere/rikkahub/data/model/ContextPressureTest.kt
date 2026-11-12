package me.rerere.rikkahub.data.model

import kotlinx.datetime.LocalDateTime
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.ui.MessageStatus
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ContextPressureTest {
    private fun conversation(vararg messages: UIMessage): Conversation =
        Conversation.ofId(Uuid.random()).copy(
            messageNodes = messages.map { it.toMessageNode() }
        )

    private fun assistantWithUsage(prompt: Int, completion: Int) = UIMessage(
        role = MessageRole.ASSISTANT,
        parts = listOf(UIMessagePart.Text("reply")),
        lastUsage = TokenUsage(promptTokens = prompt, completionTokens = completion),
    )

    @Test
    fun `uses the last real usage plus the messages after it`() {
        val reply = assistantWithUsage(100, 50)
        val next = UIMessage.user("next")
        val pressure = conversation(reply, next).contextPressure(limitTokens = 1_000)!!

        assertTrue(pressure.fromUsage)
        assertEquals(150 + next.estimatedTokens(), pressure.usedTokens)
        assertEquals(
            (150 + next.estimatedTokens()).toFloat() / 1_000,
            pressure.ratio,
            0.001f,
        )
    }

    @Test
    fun `falls back to the local estimate without usage`() {
        val next = UIMessage.user("next")
        val pressure = conversation(next).contextPressure(limitTokens = 1_000)!!

        assertFalse(pressure.fromUsage)
        assertEquals(next.estimatedTokens(), pressure.usedTokens)
    }

    @Test
    fun `usage before the last checkpoint is ignored`() {
        val oldReply = assistantWithUsage(900, 0)
        val checkpoint = UIMessage(
            role = MessageRole.USER,
            parts = listOf(UIMessagePart.Text("summary")),
            isContextCheckpoint = true,
        )
        val next = UIMessage.user("next")
        val pressure = conversation(oldReply, checkpoint, next).contextPressure(limitTokens = 1_000)!!

        assertFalse(pressure.fromUsage)
        assertFalse(pressure.shouldSuggestCompression)
        assertEquals(
            listOf(checkpoint, next).estimatedTokens(),
            pressure.usedTokens,
        )
    }

    @Test
    fun `last of several usage messages wins`() {
        val checkpoint = UIMessage(
            role = MessageRole.USER,
            parts = listOf(UIMessagePart.Text("summary")),
            isContextCheckpoint = true,
            createdAt = LocalDateTime(2026, 1, 2, 0, 0),
        )
        val first = assistantWithUsage(100, 0).copy(createdAt = LocalDateTime(2026, 1, 3, 0, 0))
        val second = assistantWithUsage(200, 0).copy(createdAt = LocalDateTime(2026, 1, 4, 0, 0))
        val next = UIMessage.user("next").copy(createdAt = LocalDateTime(2026, 1, 5, 0, 0))

        val pressure = conversation(checkpoint, first, second, next).contextPressure(limitTokens = 1_000)!!

        assertTrue(pressure.fromUsage)
        assertEquals(200 + next.estimatedTokens(), pressure.usedTokens)
    }

    @Test
    fun `legacy cumulative usage is still used when lastUsage is absent`() {
        val legacy = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(UIMessagePart.Text("reply")),
            usage = TokenUsage(promptTokens = 50, completionTokens = 50),
        )

        val pressure = conversation(legacy).contextPressure(limitTokens = 1_000)!!

        assertTrue(pressure.fromUsage)
        assertEquals(100, pressure.usedTokens)
    }

    @Test
    fun `suggests compression at the threshold`() {
        val atThreshold = conversation(assistantWithUsage(800, 0)).contextPressure(limitTokens = 1_000)!!
        val below = conversation(assistantWithUsage(799, 0)).contextPressure(limitTokens = 1_000)!!

        assertTrue(atThreshold.shouldSuggestCompression)
        assertFalse(below.shouldSuggestCompression)
    }

    @Test
    fun `usage above the limit still suggests compression`() {
        val pressure = conversation(assistantWithUsage(1_200, 0)).contextPressure(limitTokens = 1_000)!!

        assertTrue(pressure.shouldSuggestCompression)
        assertEquals(1.2f, pressure.ratio, 0.001f)
    }

    @Test
    fun `retained replies created before the checkpoint cannot keep the banner lit`() {
        val retained = assistantWithUsage(900, 0).copy(
            createdAt = LocalDateTime(2026, 1, 1, 0, 0),
        )
        val checkpoint = UIMessage(
            role = MessageRole.USER,
            parts = listOf(UIMessagePart.Text("summary")),
            isContextCheckpoint = true,
            createdAt = LocalDateTime(2026, 1, 2, 0, 0),
        )
        val next = UIMessage.user("next").copy(
            createdAt = LocalDateTime(2026, 1, 3, 0, 0),
        )

        val pressure = conversation(checkpoint, retained, next).contextPressure(limitTokens = 1_000)!!

        assertFalse(pressure.fromUsage)
        assertFalse(pressure.shouldSuggestCompression)
        assertEquals(
            listOf(checkpoint, retained, next).estimatedTokens(),
            pressure.usedTokens,
        )
    }

    @Test
    fun `failed replies and scratch prompts do not inflate the estimate`() {
        val failed = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(UIMessagePart.Text("half")),
            status = MessageStatus.FAILED,
            lastUsage = TokenUsage(promptTokens = 900, completionTokens = 0),
        )
        val prompt = UIMessage.user("compress prompt").copy(isCompressionPrompt = true)
        val next = UIMessage.user("next")
        val pressure = conversation(failed, prompt, next).contextPressure(limitTokens = 1_000)!!

        assertFalse(pressure.fromUsage)
        assertEquals(next.estimatedTokens(), pressure.usedTokens)
    }

    @Test
    fun `empty conversation has no usage and no ratio`() {
        val pressure = Conversation.ofId(Uuid.random()).contextPressure(limitTokens = 1_000)!!

        assertFalse(pressure.fromUsage)
        assertEquals(0, pressure.usedTokens)
        assertFalse(pressure.shouldSuggestCompression)
    }

    @Test
    fun `non-positive limit has no pressure`() {
        assertNull(conversation(UIMessage.user("next")).contextPressure(limitTokens = 0))
        assertNull(conversation(UIMessage.user("next")).contextPressure(limitTokens = -1))
    }
}
