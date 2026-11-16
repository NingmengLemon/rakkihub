package me.rerere.ai.ui

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
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
                    StepStatus.SUCCEEDED, 0, 2, now, now,
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
                )
            ),
        )

        val decoded = Json.decodeFromString<UIMessage>(Json.encodeToString(message))

        assertEquals(StepStatus.SUCCEEDED, decoded.steps.single().status)
        assertEquals(now, decoded.steps.single().startedAt)
        assertEquals(1, decoded.steps.single().attempts.single().attempt)
        assertEquals("SERVER", decoded.steps.single().attempts.single().code)
        assertFalse(decoded.parts.filterIsInstance<UIMessagePart.Tool>().single().callComplete)
    }

    @Test
    fun `steps stored before attempts existed decode with empty attempts`() {
        val legacy = """{"id":"00000000-0000-0000-0000-000000000001","role":"assistant","parts":[],"steps":[{"status":"succeeded","startPartIndex":0,"endPartIndex":0,"startedAt":"1970-01-01T00:00:01Z"}]}"""

        val step = Json.decodeFromString<UIMessage>(legacy).steps.single()

        assertTrue(step.attempts.isEmpty())
    }

    @Test
    fun `messages stored before tool completeness existed decode as complete`() {
        val legacy = """{"id":"00000000-0000-0000-0000-000000000001","role":"assistant","parts":[{"type":"tool","toolCallId":"call-1","toolName":"read","input":"{}"}]}"""

        val tool = Json.decodeFromString<UIMessage>(legacy).parts.filterIsInstance<UIMessagePart.Tool>().single()

        assertTrue(tool.callComplete)
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
