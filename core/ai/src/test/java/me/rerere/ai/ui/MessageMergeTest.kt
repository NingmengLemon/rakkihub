package me.rerere.ai.ui

import me.rerere.ai.core.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageMergeTest {
    @Test
    fun `adjacent user messages merge and other roles stay separate`() {
        val first = UIMessage.user("first")
        val second = UIMessage.user("second")
        val assistant = UIMessage.assistant("answer")

        val merged = listOf(first, second, assistant).mergeAdjacentUserMessages()

        assertEquals(2, merged.size)
        assertEquals(MessageRole.USER, merged[0].role)
        assertEquals(listOf("first", "second"), merged[0].texts())
        assertEquals(assistant.id, merged[1].id)
    }

    @Test
    fun `filtering a failed reply and merging leaves one user message`() {
        val first = UIMessage.user("first")
        val failed = UIMessage.assistant("partial").copy(status = MessageStatus.FAILED)
        val second = UIMessage.user("second")

        val merged = listOf(first, failed, second)
            .filterModelVisible()
            .mergeAdjacentUserMessages()

        assertEquals(1, merged.size)
        assertEquals(MessageRole.USER, merged.single().role)
        assertEquals(listOf("first", "second"), merged.single().texts())
    }

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

    private fun UIMessage.texts(): List<String> =
        parts.filterIsInstance<UIMessagePart.Text>().map { it.text }
}
