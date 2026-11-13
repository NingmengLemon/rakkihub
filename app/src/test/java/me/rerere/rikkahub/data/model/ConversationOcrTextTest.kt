package me.rerere.rikkahub.data.model

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.ocrText
import me.rerere.ai.ui.withOcrText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.uuid.Uuid

class ConversationOcrTextTest {

    private fun conversation(vararg messages: UIMessage): Conversation = Conversation(
        id = Uuid.random(),
        assistantId = Uuid.random(),
        messageNodes = messages.map { it.toMessageNode() },
    )

    @Test
    fun `ocr text is written into matching images only`() {
        val image = UIMessagePart.Image("file:///img/a.jpg")
        val other = UIMessagePart.Image("file:///img/b.jpg")
        val conversation = conversation(
            UIMessage(
                role = MessageRole.USER,
                parts = listOf(image, other),
            )
        )

        val updated = conversation.withOcrTexts(mapOf("file:///img/a.jpg" to "a cat"))

        val parts = updated.messageNodes.single().messages.single().parts
        assertEquals("a cat", (parts[0] as UIMessagePart.Image).ocrText())
        assertNull((parts[1] as UIMessagePart.Image).ocrText())
    }

    @Test
    fun `ocr text is written into images of every branch`() {
        val selected = UIMessagePart.Image("file:///img/a.jpg")
        val alternative = UIMessagePart.Image("file:///img/a.jpg")
        val node = MessageNode(
            id = Uuid.random(),
            messages = listOf(
                UIMessage(role = MessageRole.USER, parts = listOf(selected)),
                UIMessage(role = MessageRole.USER, parts = listOf(alternative)),
            ),
        )
        val conversation = Conversation(
            id = Uuid.random(),
            assistantId = Uuid.random(),
            messageNodes = listOf(node),
        )

        val updated = conversation.withOcrTexts(mapOf("file:///img/a.jpg" to "a cat"))

        updated.messageNodes.single().messages.forEach { message ->
            assertEquals("a cat", (message.parts.single() as UIMessagePart.Image).ocrText())
        }
    }

    @Test
    fun `existing metadata is preserved when writing ocr text`() {
        val image = UIMessagePart.Image(
            url = "file:///img/a.jpg",
            metadata = buildJsonObject { put("thoughtSignature", "sig") },
        )
        val conversation = conversation(UIMessage(role = MessageRole.USER, parts = listOf(image)))

        val updated = conversation.withOcrTexts(mapOf("file:///img/a.jpg" to "a cat"))

        val metadata = (updated.messageNodes.single().messages.single().parts.single() as UIMessagePart.Image).metadata
        assertEquals("sig", metadata?.get("thoughtSignature")?.jsonPrimitive?.content)
        assertEquals("a cat", metadata?.get("ocr_text")?.jsonPrimitive?.content)
    }

    @Test
    fun `unchanged ocr text keeps the same conversation instance`() {
        val image = UIMessagePart.Image("file:///img/a.jpg").withOcrText("a cat")
        val conversation = conversation(UIMessage(role = MessageRole.USER, parts = listOf(image)))

        val updated = conversation.withOcrTexts(mapOf("file:///img/a.jpg" to "a cat"))

        assertSame(conversation, updated)
    }

    @Test
    fun `empty texts keep the same conversation instance`() {
        val conversation = conversation(
            UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Image("file:///img/a.jpg")))
        )

        assertSame(conversation, conversation.withOcrTexts(emptyMap()))
    }
}
