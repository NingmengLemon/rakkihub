package me.rerere.rikkahub.data.ai

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatRequestFactoryContextTest {
    private val assistant = UIMessage.assistant("assistant")
    private val user = UIMessage.user("user")

    @Test
    fun `overflow window keeps at least two messages and starts with user`() {
        val window = overflowWindow(listOf(assistant, user, assistant))

        assertEquals(listOf(MessageRole.USER, MessageRole.ASSISTANT), window?.map { it.role })
        assertEquals(2, window?.size)
    }

    @Test
    fun `overflow window starts at the last user turn`() {
        val window = overflowWindow(listOf(user, assistant, user, assistant))

        assertEquals(listOf(MessageRole.USER, MessageRole.ASSISTANT), window?.map { it.role })
        assertEquals(2, window?.size)
    }

    @Test
    fun `cannot shrink a window that is already all retained`() {
        assertNull(overflowWindow(listOf(user, assistant, user)))
        assertNull(overflowWindow(listOf(user)))
    }
}
