package me.rerere.ai.ui

import me.rerere.ai.core.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Clock

class MessageSummaryProjectionTest {
    @Test
    fun `failed message keeps successful steps and executed tools`() {
        val parts = listOf(
            UIMessagePart.Text("A is 42"),
            tool("query"),
            UIMessagePart.Text("changing B"),
            tool("write"),
        )
        val message = failed(
            parts = parts,
            steps = listOf(
                step(StepStatus.SUCCEEDED, 0, 2),
                step(StepStatus.FAILED, 2, 4),
            ),
        )

        val projected = listOf(message).projectForSummary().single()

        assertEquals(
            listOf(UIMessagePart.Text("A is 42"), tool("query"), tool("write")),
            projected.parts,
        )
        assertEquals(MessageStatus.COMPLETE, projected.status)
        assertTrue(projected.steps.isEmpty())
    }

    @Test
    fun `failed message without steps is dropped`() {
        val message = failed(parts = listOf(UIMessagePart.Text("legacy failure")), steps = emptyList())
        assertTrue(listOf(message).projectForSummary().isEmpty())
    }

    @Test
    fun `failed step text and incomplete tools are dropped`() {
        val incomplete = UIMessagePart.Tool(
            toolCallId = "half",
            toolName = "write",
            input = "{}",
            output = emptyList(),
        )
        val message = failed(
            parts = listOf(UIMessagePart.Text("half paragraph"), incomplete, tool("done")),
            steps = listOf(step(StepStatus.FAILED, 0, 3)),
        )

        val projected = listOf(message).projectForSummary().single()

        assertEquals(listOf(tool("done")), projected.parts)
    }

    @Test
    fun `successful steps keep their parts in order`() {
        val message = failed(
            parts = listOf(
                UIMessagePart.Text("first"),
                tool("first_tool"),
                UIMessagePart.Text("second"),
                tool("second_tool"),
            ),
            steps = listOf(
                step(StepStatus.SUCCEEDED, 0, 2),
                step(StepStatus.SUCCEEDED, 2, 4),
                step(StepStatus.FAILED, 4, 4),
            ),
        )

        val projected = listOf(message).projectForSummary().single()

        assertEquals(
            listOf(
                UIMessagePart.Text("first"),
                tool("first_tool"),
                UIMessagePart.Text("second"),
                tool("second_tool"),
            ),
            projected.parts,
        )
    }

    @Test
    fun `tools step keeps text and complete tools but drops incomplete calls`() {
        val incomplete = UIMessagePart.Tool(
            toolCallId = "half",
            toolName = "write",
            input = "{}",
            output = emptyList(),
        )
        val message = failed(
            parts = listOf(UIMessagePart.Text("calling tools"), incomplete, tool("done")),
            steps = listOf(
                step(StepStatus.TOOLS, 0, 3),
                step(StepStatus.FAILED, 3, 3),
            ),
        )

        val projected = listOf(message).projectForSummary().single()

        assertEquals(
            listOf(UIMessagePart.Text("calling tools"), tool("done")),
            projected.parts,
        )
    }

    @Test
    fun `non-failed messages are unchanged`() {
        val message = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(UIMessagePart.Text("done")),
            status = MessageStatus.COMPLETE,
            steps = listOf(step(StepStatus.SUCCEEDED, 0, 1)),
        )

        assertEquals(listOf(message), listOf(message).projectForSummary())
    }

    private fun failed(parts: List<UIMessagePart>, steps: List<MessageStep>) = UIMessage(
        role = MessageRole.ASSISTANT,
        parts = parts,
        status = MessageStatus.FAILED,
        steps = steps,
    )

    private fun tool(id: String) = UIMessagePart.Tool(
        toolCallId = id,
        toolName = "write",
        input = "{}",
        output = listOf(UIMessagePart.Text("ok")),
    )

    private fun step(status: StepStatus, start: Int, end: Int) = MessageStep(
        status = status,
        startPartIndex = start,
        endPartIndex = end,
        startedAt = Clock.System.now(),
    )
}
