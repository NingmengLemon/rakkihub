package me.rerere.rikkahub.ui.pages.chat

import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationExportToolMarkdownTest {
    private val tool = UIMessagePart.Tool(
        toolCallId = "call-1",
        toolName = "search",
        input = """{"query":"secret"}""",
        output = listOf(UIMessagePart.Text("tool output secret")),
    )

    @Test
    fun `tool details are written when enabled`() {
        val markdown = toolMarkdown(tool, includeDetails = true)

        assertTrue(markdown.contains("**Tool**: `search`"))
        assertTrue(markdown.contains("Call ID: `call-1`"))
        assertTrue(markdown.contains("secret"))
        assertTrue(markdown.contains("tool output secret"))
    }

    @Test
    fun `only the tool name is written when details are disabled`() {
        val markdown = toolMarkdown(tool, includeDetails = false)

        assertTrue(markdown.contains("**Tool**: `search`"))
        assertFalse(markdown.contains("Call ID"))
        assertFalse(markdown.contains("Input:"))
        assertFalse(markdown.contains("secret"))
        assertFalse(markdown.contains("tool output"))
    }
}
