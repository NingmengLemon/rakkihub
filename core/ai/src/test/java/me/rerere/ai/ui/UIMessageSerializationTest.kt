package me.rerere.ai.ui

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

class UIMessageSerializationTest {

    @Test
    fun `synthetic marker is not serialized`() {
        val message = UIMessage.user("internal").copy(isSynthetic = true)

        val encoded = Json.encodeToString(message)
        val decoded = Json.decodeFromString<UIMessage>(encoded)

        assertTrue(message.isSynthetic)
        assertFalse(encoded.contains("isSynthetic"))
        assertFalse(decoded.isSynthetic)
    }

    @Test
    fun `context checkpoint marker survives serialization`() {
        val message = UIMessage.user("summary").copy(isContextCheckpoint = true)

        val decoded = Json.decodeFromString<UIMessage>(Json.encodeToString(message))

        assertTrue(decoded.isContextCheckpoint)
    }

    @Test
    fun `messages stored before checkpoints existed decode as ordinary messages`() {
        val legacy = """{"id":"00000000-0000-0000-0000-000000000001","role":"user","parts":[]}"""

        assertFalse(Json.decodeFromString<UIMessage>(legacy).isContextCheckpoint)
    }

    @Test
    fun `model snapshot survives serialization`() {
        val modelId = Uuid.random()
        val message = UIMessage.assistant("hi").copy(
            modelId = modelId,
            modelSnapshot = ModelSnapshot(modelId = "gpt-test", displayName = "GPT Test"),
        )

        val shown = Json.decodeFromString<UIMessage>(Json.encodeToString(message)).snapshotModel()

        assertEquals(modelId, shown?.id)
        assertEquals("gpt-test", shown?.modelId)
        assertEquals("GPT Test", shown?.displayName)
    }

    @Test
    fun `messages stored before model snapshots existed decode without one`() {
        val legacy = """{"role":"assistant","parts":[],"modelId":"00000000-0000-0000-0000-000000000001"}"""

        assertNull(Json.decodeFromString<UIMessage>(legacy).snapshotModel())
    }

    @Test
    fun `message status survives serialization`() {
        val failed = UIMessage.assistant("partial").copy(status = MessageStatus.FAILED)

        val decoded = Json.decodeFromString<UIMessage>(Json.encodeToString(failed))

        assertEquals(MessageStatus.FAILED, decoded.status)
    }

    @Test
    fun `messages stored before status existed decode as complete`() {
        val legacy = """{"id":"00000000-0000-0000-0000-000000000001","role":"assistant","parts":[]}"""

        assertEquals(MessageStatus.COMPLETE, Json.decodeFromString<UIMessage>(legacy).status)
    }

    @Test
    fun `messages stored before steps existed decode as a single implicit step`() {
        val legacy = """{"id":"00000000-0000-0000-0000-000000000001","role":"assistant","parts":[]}"""

        val decoded = Json.decodeFromString<UIMessage>(legacy)

        assertTrue(decoded.steps.isEmpty())
    }

    @Test
    fun `steps and tool completeness survive serialization`() {
        val now = kotlin.time.Instant.fromEpochSeconds(1)
        val message = UIMessage(
            role = me.rerere.ai.core.MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.Text("answer"),
                UIMessagePart.Tool(
                    toolCallId = "call-1",
                    toolName = "read",
                    input = "{}",
                    output = listOf(UIMessagePart.Text("ok")),
                    callComplete = false,
                ),
            ),
            steps = listOf(
                MessageStep(
                    StepStatus.FAILED, 0, 2, now, now,
                    attempts = listOf(
                        MessageAttempt(
                            attempt = 1,
                            maxRetries = 3,
                            code = "SERVER",
                            message = "upstream failed",
                            delayMs = 1_000,
                            recordedAt = now,
                        )
                    ),
                    failureCode = "SERVER",
                )
            ),
        )

        val stepId = message.steps.single().id
        val decoded = Json.decodeFromString<UIMessage>(Json.encodeToString(message))

        assertEquals(StepStatus.FAILED, decoded.steps.single().status)
        assertEquals(now, decoded.steps.single().startedAt)
        assertEquals(1, decoded.steps.single().attempts.single().attempt)
        assertEquals("SERVER", decoded.steps.single().attempts.single().code)
        assertEquals("SERVER", decoded.steps.single().failureCode)
        assertEquals(stepId, decoded.steps.single().id)
        assertFalse(decoded.parts.filterIsInstance<UIMessagePart.Tool>().single().callComplete)
    }

    @Test
    fun `steps stored before attempts existed decode with empty attempts`() {
        val legacy = """{"id":"00000000-0000-0000-0000-000000000001","role":"assistant","parts":[],"steps":[{"status":"succeeded","startPartIndex":0,"endPartIndex":0,"startedAt":"1970-01-01T00:00:01Z"}]}"""

        val step = Json.decodeFromString<UIMessage>(legacy).steps.single()

        assertTrue(step.attempts.isEmpty())
        assertNull(step.failureCode)
        assertNotNull(step.id)
    }

    @Test
    fun `succeeded step keeps a null failure code and stable id`() {
        val stepId = Uuid.random()
        val message = UIMessage.assistant("ok").copy(
            steps = listOf(
                MessageStep(
                    status = StepStatus.SUCCEEDED,
                    startPartIndex = 0,
                    endPartIndex = 1,
                    startedAt = kotlin.time.Instant.fromEpochSeconds(1),
                    id = stepId,
                )
            )
        )

        val decoded = Json.decodeFromString<UIMessage>(Json.encodeToString(message))
        val step = decoded.steps.single()

        assertEquals(StepStatus.SUCCEEDED, step.status)
        assertNull(step.failureCode)
        assertEquals(stepId, step.id)
    }

    @Test
    fun `messages stored before tool completeness existed decode as complete`() {
        val legacy = """{"id":"00000000-0000-0000-0000-000000000001","role":"assistant","parts":[{"type":"tool","toolCallId":"call-1","toolName":"read","input":"{}"}]}"""

        val tool = Json.decodeFromString<UIMessage>(legacy).parts.filterIsInstance<UIMessagePart.Tool>().single()

        assertTrue(tool.callComplete)
    }

    @Test
    fun `in-progress server tools are dropped from the request projection`() {
        val message = UIMessage(
            role = me.rerere.ai.core.MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.Text("done"),
                UIMessagePart.ServerTool(
                    toolCallId = "finished",
                    toolName = "search",
                    status = ServerToolStatus.COMPLETED,
                ),
                UIMessagePart.ServerTool(
                    toolCallId = "running",
                    toolName = "search",
                    status = ServerToolStatus.IN_PROGRESS,
                ),
            ),
            status = MessageStatus.COMPLETE,
        )

        val projected = listOf(message).projectForRequest().single()

        val names = projected.parts.filterIsInstance<UIMessagePart.ServerTool>().map { it.toolCallId }
        assertEquals(listOf("finished"), names)
        assertTrue(projected.parts.any { it is UIMessagePart.Text })
    }

    @Test
    fun `failed request projection keeps finished server tools`() {
        val failed = UIMessage(
            role = me.rerere.ai.core.MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.Text("failed narration"),
                UIMessagePart.Tool(
                    toolCallId = "local",
                    toolName = "write",
                    input = "{}",
                    output = listOf(UIMessagePart.Text("written")),
                ),
                UIMessagePart.ServerTool(
                    toolCallId = "finished",
                    toolName = "search",
                    status = ServerToolStatus.COMPLETED,
                ),
                UIMessagePart.ServerTool(
                    toolCallId = "running",
                    toolName = "search",
                    status = ServerToolStatus.IN_PROGRESS,
                ),
            ),
            status = MessageStatus.FAILED,
        )

        val projected = listOf(failed).projectForRequest().single()

        val ids = projected.parts.mapNotNull {
            when (it) {
                is UIMessagePart.Tool -> it.toolCallId
                is UIMessagePart.ServerTool -> it.toolCallId
                else -> null
            }
        }
        assertEquals(listOf("local", "finished"), ids)
        assertTrue(projected.parts.none { it is UIMessagePart.Text })
    }

    @Test
    fun `request projection keeps the successful step body of a failed message`() {
        val failed = UIMessage(
            role = me.rerere.ai.core.MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.Text("first step ok"),
                UIMessagePart.Text("second step failed"),
                UIMessagePart.Tool(
                    toolCallId = "done",
                    toolName = "write",
                    input = "{}",
                    output = listOf(UIMessagePart.Text("written")),
                ),
            ),
            status = MessageStatus.FAILED,
            steps = listOf(
                step(StepStatus.SUCCEEDED, 0, 1),
                step(StepStatus.FAILED, 1, 3),
            ),
        )

        val projected = listOf(failed).projectForRequest().single()

        assertEquals(
            listOf("first step ok"),
            projected.parts.filterIsInstance<UIMessagePart.Text>().map { it.text },
        )
        assertEquals(listOf("done"), projected.parts.filterIsInstance<UIMessagePart.Tool>().map { it.toolCallId })
        assertEquals(listOf(0 to 1, 1 to 2), projected.steps.map { it.startPartIndex to it.endPartIndex })
    }

    @Test
    fun `request projection drops unexecuted tools from a tools step`() {
        val message = UIMessage(
            role = me.rerere.ai.core.MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.Text("working"),
                UIMessagePart.Tool(
                    toolCallId = "pending",
                    toolName = "write",
                    input = "{}",
                    output = emptyList(),
                    callComplete = false,
                ),
                UIMessagePart.Tool(
                    toolCallId = "done",
                    toolName = "write",
                    input = "{}",
                    output = listOf(UIMessagePart.Text("written")),
                ),
                UIMessagePart.ServerTool(
                    toolCallId = "running",
                    toolName = "search",
                    status = ServerToolStatus.IN_PROGRESS,
                ),
                UIMessagePart.ServerTool(
                    toolCallId = "finished",
                    toolName = "search",
                    status = ServerToolStatus.COMPLETED,
                ),
            ),
            status = MessageStatus.COMPLETE,
            steps = listOf(step(StepStatus.TOOLS, 0, 5)),
        )

        val projected = listOf(message).projectForRequest().single()

        val ids = projected.parts.mapNotNull {
            when (it) {
                is UIMessagePart.Tool -> it.toolCallId
                is UIMessagePart.ServerTool -> it.toolCallId
                else -> null
            }
        }
        assertEquals(listOf("done", "finished"), ids)
        assertTrue(projected.parts.any { it is UIMessagePart.Text })
        assertEquals(0 to 3, projected.steps.single().startPartIndex to projected.steps.single().endPartIndex)
    }

    private fun step(status: StepStatus, start: Int, end: Int) = MessageStep(
        status = status,
        startPartIndex = start,
        endPartIndex = end,
        startedAt = Instant.fromEpochMilliseconds(0),
    )

    @Test
    fun `step-less messages keep their identity in the request projection`() {
        val message = UIMessage(
            role = me.rerere.ai.core.MessageRole.ASSISTANT,
            parts = listOf(UIMessagePart.Text("done")),
            status = MessageStatus.COMPLETE,
        )

        assertSame(message, listOf(message).projectForRequest().single())
    }

    @Test
    fun `completed multi-step messages round-trip unchanged through the request projection`() {
        val message = UIMessage(
            role = me.rerere.ai.core.MessageRole.ASSISTANT,
            parts = listOf(UIMessagePart.Text("first"), UIMessagePart.Text("second")),
            status = MessageStatus.COMPLETE,
            steps = listOf(
                step(StepStatus.SUCCEEDED, 0, 1),
                step(StepStatus.SUCCEEDED, 1, 2),
            ),
        )

        assertEquals(listOf(message), listOf(message).projectForRequest())
    }

    @Test
    fun `cancelled step keeps its body for requests and drops it for summaries`() {
        val message = UIMessage(
            role = me.rerere.ai.core.MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.Text("delivered prefix"),
                UIMessagePart.Tool(
                    toolCallId = "done",
                    toolName = "write",
                    input = "{}",
                    output = listOf(UIMessagePart.Text("written")),
                ),
            ),
            status = MessageStatus.COMPLETE,
            steps = listOf(step(StepStatus.CANCELLED, 0, 2)),
        )

        val request = listOf(message).projectForRequest().single()
        assertEquals(listOf("delivered prefix"), request.parts.filterIsInstance<UIMessagePart.Text>().map { it.text })
        assertTrue(request.parts.any { it is UIMessagePart.Tool })

        val summary = listOf(message).projectForSummary().single()
        assertTrue(summary.parts.none { it is UIMessagePart.Text })
        assertEquals(listOf("done"), summary.parts.filterIsInstance<UIMessagePart.Tool>().map { it.toolCallId })
    }

    @Test
    fun `failed messages keep executed tool facts in request projection`() {
        val failed = UIMessage(
            role = me.rerere.ai.core.MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.Text("failed narration"),
                UIMessagePart.Tool(
                    toolCallId = "done",
                    toolName = "write",
                    input = "{}",
                    output = listOf(UIMessagePart.Text("written")),
                ),
                UIMessagePart.Tool(
                    toolCallId = "halF",
                    toolName = "write",
                    input = "{}",
                    output = emptyList(),
                ),
            ),
            status = MessageStatus.FAILED,
        )

        val projected = listOf(UIMessage.user("next"), failed).projectForRequest()

        assertEquals(2, projected.size)
        val tools = projected[1].parts.filterIsInstance<UIMessagePart.Tool>()
        assertEquals(listOf("done"), tools.map { it.toolCallId })
        assertTrue(projected[1].parts.none { it is UIMessagePart.Text })
    }
}
