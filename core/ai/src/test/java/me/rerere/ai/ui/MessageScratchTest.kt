package me.rerere.ai.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageScratchTest {
    @Test
    fun `compression scratch prompt and its reply are removed`() {
        val before = UIMessage.user("before")
        val prompt = UIMessage.user("compress").copy(isCompressionPrompt = true)
        val reply = UIMessage.assistant("half")
        val after = UIMessage.user("after")

        val filtered = listOf(before, prompt, reply, after).withoutCompressionScratch()

        assertEquals(listOf(before.id, after.id), filtered.map { it.id })
    }

    @Test
    fun `compression prompt without a reply is removed alone`() {
        val prompt = UIMessage.user("compress").copy(isCompressionPrompt = true)
        val after = UIMessage.user("after")

        val filtered = listOf(prompt, after).withoutCompressionScratch()

        assertEquals(listOf(after.id), filtered.map { it.id })
    }

    @Test
    fun `filterModelVisible removes only failed replies`() {
        val complete = UIMessage.user("complete")
        val interrupted = UIMessage.assistant("interrupted")
            .copy(status = MessageStatus.INTERRUPTED)
        val failed = UIMessage.assistant("failed").copy(status = MessageStatus.FAILED)

        val filtered = listOf(complete, interrupted, failed).filterModelVisible()

        assertEquals(listOf(complete.id, interrupted.id), filtered.map { it.id })
    }
}
