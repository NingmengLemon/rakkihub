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
    fun `only failed messages are hidden from the model`() {
        val messages = listOf(
            UIMessage.user("question"),
            UIMessage.assistant("failed").copy(status = MessageStatus.FAILED),
            UIMessage.assistant("stopped").copy(status = MessageStatus.INTERRUPTED),
            UIMessage.assistant("complete"),
        )

        assertEquals(
            listOf(messages[0].id, messages[2].id, messages[3].id),
            messages.filterModelVisible().map { it.id },
        )
    }
}
