package me.rerere.rikkahub.data.ai

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.limitContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `overflow window falls back to the previous user when the last turn has one message`() {
        val window = overflowWindow(listOf(user, assistant, user, assistant, user))

        assertEquals(listOf(MessageRole.USER, MessageRole.ASSISTANT, MessageRole.USER), window?.map { it.role })
    }

    @Test
    fun `overflow window is a subset of the configured window and follows current messages`() {
        val messages = buildList {
            repeat(10) { index ->
                add(UIMessage.user("user-$index"))
                add(UIMessage.assistant("assistant-$index"))
            }
        }
        val limited = messages.limitContext(5)
        val window = overflowWindow(limited)
        assertNotNull(window)
        assertTrue(window!!.size <= limited.size)
        assertEquals(MessageRole.USER, window.first().role)

        val oldWindow = overflowWindow(listOf(user, assistant, user, UIMessage.assistant("old")))
        val newWindow = overflowWindow(listOf(user, assistant, user, UIMessage.assistant("new")))
        assertEquals("old", (oldWindow?.last()?.parts?.last() as? UIMessagePart.Text)?.text)
        assertEquals("new", (newWindow?.last()?.parts?.last() as? UIMessagePart.Text)?.text)
    }

    @Test
    fun `cannot shrink a window that is already all retained`() {
        assertNull(overflowWindow(listOf(user, assistant, user)))
        assertNull(overflowWindow(listOf(user)))
    }
}
