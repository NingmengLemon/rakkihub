package me.rerere.rikkahub.data.ai

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ContextPruningTest {
    private fun text(value: String) = UIMessage.user(value)

    private fun toolOutput(output: String) = UIMessage(
        role = MessageRole.ASSISTANT,
        parts = listOf(
            UIMessagePart.Tool(
                toolCallId = Uuid.random().toString(),
                toolName = "test",
                input = "{}",
                output = listOf(UIMessagePart.Text(output)),
            )
        ),
    )

    private fun image(url: String = "file:upload/x.jpg") = UIMessage(
        role = MessageRole.USER,
        parts = listOf(UIMessagePart.Image(url)),
    )

    @Test
    fun `messages under budget are returned unchanged`() {
        val messages = listOf(toolOutput("small"), text("hello"))

        val result = pruneContextForRequest(messages, contextLength = 100_000)

        assertSame(messages, result.messages)
        assertEquals(0, result.toolResultCount)
        assertEquals(0, result.imageCount)
    }

    @Test
    fun `old tool output is truncated and recent messages survive`() {
        val old = toolOutput("a".repeat(20_000) + "TAIL")
        val messages = listOf(old) + (1..7).map { text("m$it") }

        val result = pruneContextForRequest(messages, contextLength = 1_000)

        assertEquals(1, result.toolResultCount)
        val output = (result.messages.first().parts.single() as UIMessagePart.Tool).output
        val retained = (output.single() as UIMessagePart.Text).text
        assertTrue(retained.startsWith("[Tool output truncated to fit the context window]"))
        assertTrue(retained.contains("a".repeat(2_000)))
        assertFalse(retained.contains("TAIL"))
        assertEquals(messages.takeLast(6), result.messages.takeLast(6))
        assertEquals(20_000 + "TAIL".length, (old.parts.single() as UIMessagePart.Tool).output.single().toText().length)
    }

    @Test
    fun `pruning stops after the first tool output that fits the budget`() {
        val first = toolOutput("a".repeat(20_000))
        val second = toolOutput("b".repeat(20_000))
        val messages = listOf(first, second) + (1..6).map { text("m$it") }

        val result = pruneContextForRequest(messages, contextLength = 10_240)

        assertEquals(1, result.toolResultCount)
        val firstOutput = (result.messages.first().parts.single() as UIMessagePart.Tool).output
        val secondOutput = (result.messages[1].parts.single() as UIMessagePart.Tool).output
        assertTrue(firstOutput.single() is UIMessagePart.Text)
        assertEquals(20_000, (secondOutput.single() as UIMessagePart.Text).text.length)
    }

    @Test
    fun `tool output that would grow is not rewritten`() {
        val old = toolOutput("a".repeat(1_500))
        val messages = listOf(old) + (1..7).map { text("x".repeat(2_000)) }

        val result = pruneContextForRequest(messages, contextLength = 1_000)

        assertEquals(0, result.toolResultCount)
        assertEquals(1_500, (old.parts.single() as UIMessagePart.Tool).output.single().toText().length)
        assertSame(messages, result.messages)
    }

    @Test
    fun `old image is replaced but recent image is kept`() {
        val messages = listOf(image()) + (1..5).map { text("m$it") } + image("file:upload/recent.jpg")

        val result = pruneContextForRequest(messages, contextLength = 1_000)

        assertEquals(1, result.imageCount)
        assertTrue(result.messages.first().parts.single() is UIMessagePart.Text)
        assertTrue(result.messages.last().parts.single() is UIMessagePart.Image)
    }

    @Test
    fun `image inside old tool output is replaced and counted`() {
        val tool = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.Tool(
                    toolCallId = Uuid.random().toString(),
                    toolName = "test",
                    input = "{}",
                    output = listOf(
                        UIMessagePart.Image("file:upload/tool.png"),
                        UIMessagePart.Text("small"),
                    ),
                )
            ),
        )
        val messages = listOf(tool) + (1..7).map { text("m$it") }

        val result = pruneContextForRequest(messages, contextLength = 1_000)

        assertEquals(1, result.toolResultCount)
        assertEquals(1, result.imageCount)
        val output = (result.messages.first().parts.single() as UIMessagePart.Tool).output
        assertTrue(output.all { it is UIMessagePart.Text })
    }

    @Test
    fun `pending unexecuted tools are not pruned`() {
        val pending = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.Tool(
                    toolCallId = Uuid.random().toString(),
                    toolName = "test",
                    input = "{}",
                    approvalState = ToolApprovalState.Pending,
                )
            ),
        )
        val messages = listOf(pending) + (1..7).map { text("m$it") }

        val result = pruneContextForRequest(messages, contextLength = 1_000)

        assertEquals(0, result.toolResultCount)
        assertTrue((result.messages.first().parts.single() as UIMessagePart.Tool).output.isEmpty())
    }

    @Test
    fun `exactly six messages are left alone`() {
        val messages = listOf(toolOutput("a".repeat(20_000))) + (1..5).map { text("m$it") }

        val result = pruneContextForRequest(messages, contextLength = 1_000)

        assertSame(messages, result.messages)
        assertEquals(0, result.toolResultCount)
    }

    @Test
    fun `non-positive context length is left alone`() {
        val messages = listOf(toolOutput("a".repeat(20_000))) + (1..7).map { text("m$it") }

        val result = pruneContextForRequest(messages, contextLength = 0)

        assertSame(messages, result.messages)
        assertEquals(0, result.toolResultCount)
    }

    @Test
    fun `messages without tool output or images are left alone`() {
        val messages = (1..8).map { text("x".repeat(2_000) + it) }

        val result = pruneContextForRequest(messages, contextLength = 1_000)

        assertEquals(0, result.toolResultCount)
        assertEquals(0, result.imageCount)
        assertSame(messages, result.messages)
    }

    private fun UIMessagePart.toText(): String = (this as UIMessagePart.Text).text
}
