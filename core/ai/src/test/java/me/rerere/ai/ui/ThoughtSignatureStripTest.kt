package me.rerere.ai.ui

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import me.rerere.ai.core.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThoughtSignatureStripTest {
    private fun metadata(vararg pairs: Pair<String, kotlinx.serialization.json.JsonElement>) =
        JsonObject(pairs.toMap())

    private fun text(value: String) = JsonPrimitive(value)

    @Test
    fun `only the selected protocol key is removed`() {
        val part = UIMessagePart.Reasoning(
            reasoning = "thinking",
            metadata = metadata(
                "thoughtSignature" to text("google"),
                "interactions_signature" to text("interactions"),
                "other" to text("keep"),
            ),
        )

        val stripped = part.stripThoughtSignatures(
            setOf(ThoughtSignatureProtocol.GOOGLE_GENERATE_CONTENT)
        )

        assertFalse(part === stripped)
        assertNull(stripped.metadata?.get("thoughtSignature"))
        assertEquals(text("interactions"), stripped.metadata?.get("interactions_signature"))
        assertEquals(text("keep"), stripped.metadata?.get("other"))
        // 原对象不能被顺手改掉
        assertEquals(text("google"), part.metadata?.get("thoughtSignature"))
    }

    @Test
    fun `server tool raw call loses the nested signature`() {
        val part = UIMessagePart.ServerTool(
            toolCallId = "call",
            toolName = "web_search",
            status = ServerToolStatus.COMPLETED,
            metadata = metadata(
                "server_tool_call" to metadata(
                    "thoughtSignature" to text("sig"),
                    "name" to text("web_search"),
                ),
            ),
        )

        val stripped = part.stripThoughtSignatures(
            setOf(ThoughtSignatureProtocol.GOOGLE_GENERATE_CONTENT)
        ) as UIMessagePart.ServerTool

        val call = stripped.metadata?.get("server_tool_call")?.jsonObject
        assertNull(call?.get("thoughtSignature"))
        assertEquals(text("web_search"), call?.get("name"))
        val originalCall = part.metadata?.get("server_tool_call")?.jsonObject
        assertNotNull(originalCall?.get("thoughtSignature"))
    }

    @Test
    fun `interactions nested signature is removed`() {
        val part = UIMessagePart.ServerTool(
            toolCallId = "call",
            toolName = "web_search",
            status = ServerToolStatus.COMPLETED,
            metadata = metadata(
                "server_tool_result" to metadata(
                    "signature" to text("sig"),
                    "status" to text("ok"),
                ),
            ),
        )

        val stripped = part.stripThoughtSignatures(
            setOf(ThoughtSignatureProtocol.GOOGLE_INTERACTIONS)
        ) as UIMessagePart.ServerTool

        val result = stripped.metadata?.get("server_tool_result")?.jsonObject
        assertNull(result?.get("signature"))
        assertEquals(text("ok"), result?.get("status"))
    }

    @Test
    fun `empty selection returns the same instance`() {
        val part = UIMessagePart.Text(text = "hi", metadata = metadata("x" to text("1")))

        assertTrue(part === part.stripThoughtSignatures(emptySet()))
    }

    @Test
    fun `message becomes a different value after stripping`() {
        val message = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.Reasoning(
                    reasoning = "a",
                    metadata = metadata("thoughtSignature" to text("sig")),
                ),
                UIMessagePart.Text(
                    text = "b",
                    metadata = metadata("thoughtSignature" to text("sig"), "other" to text("keep")),
                ),
            ),
        )

        val stripped = message.stripThoughtSignatures(ThoughtSignatureProtocol.all)

        assertNotEquals(message, stripped)
        assertNull(stripped.parts[0].metadata)
        assertNull(stripped.parts[1].metadata?.get("thoughtSignature"))
        assertEquals(text("keep"), stripped.parts[1].metadata?.get("other"))
    }
}
