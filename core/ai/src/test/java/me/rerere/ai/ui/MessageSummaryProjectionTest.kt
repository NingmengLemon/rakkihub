package me.rerere.ai.ui

import kotlinx.serialization.json.JsonPrimitive
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
    fun `cancelled step drops text but keeps completed tools`() {
        val message = failed(
            parts = listOf(UIMessagePart.Text("partial"), tool("done")),
            steps = listOf(step(StepStatus.CANCELLED, 0, 2)),
        )

        val projected = listOf(message).projectForSummary().single()

        assertEquals(listOf(tool("done")), projected.parts)
    }

    @Test
    fun `running-only projection is dropped`() {
        val message = failed(
            parts = listOf(UIMessagePart.Text("streaming")),
            steps = listOf(step(StepStatus.RUNNING, 0, 1)),
        )

        assertTrue(listOf(message).projectForSummary().isEmpty())
    }

    @Test
    fun `failed step with only text is dropped`() {
        val message = failed(
            parts = listOf(UIMessagePart.Text("nothing useful")),
            steps = listOf(step(StepStatus.FAILED, 0, 1)),
        )

        assertTrue(listOf(message).projectForSummary().isEmpty())
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
    fun `tools step keeps text and complete tools but drops incomplete calls and media`() {
        val incomplete = UIMessagePart.Tool(
            toolCallId = "half",
            toolName = "write",
            input = "{}",
            output = emptyList(),
        )
        val message = failed(
            parts = listOf(
                UIMessagePart.Text("calling tools"),
                UIMessagePart.Image("file:///tmp/a.png"),
                incomplete,
                tool("done"),
            ),
            steps = listOf(
                step(StepStatus.TOOLS, 0, 4),
                step(StepStatus.FAILED, 4, 4),
            ),
        )

        val projected = listOf(message).projectForSummary().single()

        assertEquals(
            listOf(UIMessagePart.Text("calling tools"), tool("done")),
            projected.parts,
        )
    }

    @Test
    fun `tool output media is stripped but text output is kept`() {
        val mixed = UIMessagePart.Tool(
            toolCallId = "mixed",
            toolName = "read_image",
            input = "{}",
            output = listOf(
                UIMessagePart.Image("file:///tmp/result.png"),
                UIMessagePart.Text("a red circle"),
            ),
        )
        val imageOnly = UIMessagePart.Tool(
            toolCallId = "image-only",
            toolName = "read_image",
            input = "{}",
            output = listOf(UIMessagePart.Image("file:///tmp/only.png")),
        )
        val message = failed(
            parts = listOf(mixed, imageOnly),
            steps = listOf(step(StepStatus.FAILED, 0, 2)),
        )

        val projected = listOf(message).projectForSummary().single()

        assertEquals(
            listOf(mixed.copy(output = listOf(UIMessagePart.Text("a red circle")))),
            projected.parts,
        )
    }

    @Test
    fun `completed server tools are kept and in-progress ones are dropped`() {
        val completed = serverTool("completed", ServerToolStatus.COMPLETED)
        val inProgress = serverTool("running", ServerToolStatus.IN_PROGRESS)
        val message = failed(
            parts = listOf(UIMessagePart.Text("text"), completed, inProgress),
            steps = listOf(step(StepStatus.SUCCEEDED, 0, 3)),
        )

        val projected = listOf(message).projectForSummary().single()

        assertEquals(listOf(UIMessagePart.Text("text"), completed), projected.parts)
    }

    @Test
    fun `failed step keeps completed server tools`() {
        val completed = serverTool("completed", ServerToolStatus.COMPLETED)
        val message = failed(
            parts = listOf(UIMessagePart.Text("failed text"), completed),
            steps = listOf(step(StepStatus.FAILED, 0, 2)),
        )

        val projected = listOf(message).projectForSummary().single()

        assertEquals(listOf(completed), projected.parts)
    }

    @Test
    fun `tool with output but incomplete call is dropped`() {
        val part = UIMessagePart.Tool(
            toolCallId = "half",
            toolName = "write",
            input = "{}",
            output = listOf(UIMessagePart.Text("partial")),
            callComplete = false,
        )
        val message = failed(
            parts = listOf(part),
            steps = listOf(step(StepStatus.FAILED, 0, 1)),
        )

        assertTrue(listOf(message).projectForSummary().isEmpty())
    }

    @Test
    fun `parts outside step ranges are ignored`() {
        val message = failed(
            parts = listOf(
                UIMessagePart.Text("inside"),
                UIMessagePart.Text("outside"),
            ),
            steps = listOf(step(StepStatus.SUCCEEDED, 0, 1)),
        )

        val projected = listOf(message).projectForSummary().single()

        assertEquals(listOf(UIMessagePart.Text("inside")), projected.parts)
    }

    @Test
    fun `annotations are dropped from summary projection`() {
        val message = failed(
            parts = listOf(UIMessagePart.Text("inside")),
            steps = listOf(step(StepStatus.SUCCEEDED, 0, 1)),
            annotations = listOf(UIMessageAnnotation.UrlCitation(title = "x", url = "https://example.com")),
        )

        val projected = listOf(message).projectForSummary().single()

        assertTrue(projected.annotations.isEmpty())
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

    private fun failed(
        parts: List<UIMessagePart>,
        steps: List<MessageStep>,
        annotations: List<UIMessageAnnotation> = emptyList(),
    ) = UIMessage(
        role = MessageRole.ASSISTANT,
        parts = parts,
        annotations = annotations,
        status = MessageStatus.FAILED,
        steps = steps,
    )

    private fun tool(id: String) = UIMessagePart.Tool(
        toolCallId = id,
        toolName = "write",
        input = "{}",
        output = listOf(UIMessagePart.Text("ok")),
    )

    private fun serverTool(id: String, status: ServerToolStatus) = UIMessagePart.ServerTool(
        toolCallId = id,
        toolName = "web_search",
        output = JsonPrimitive("result"),
        status = status,
    )

    private fun step(status: StepStatus, start: Int, end: Int) = MessageStep(
        status = status,
        startPartIndex = start,
        endPartIndex = end,
        startedAt = Clock.System.now(),
    )
}
