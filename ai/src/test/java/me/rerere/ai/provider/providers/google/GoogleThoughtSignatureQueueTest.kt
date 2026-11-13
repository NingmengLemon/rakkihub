package me.rerere.ai.provider.providers.google

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleThoughtSignatureQueueTest {

    private val parse: (JsonObject, Int) -> UIMessagePart = { json, _ ->
        UIMessagePart.Text(json["text"]?.jsonPrimitive?.content ?: "tool")
    }

    private fun signatureOf(part: UIMessagePart): String? =
        part.metadata?.get("thoughtSignature")?.jsonPrimitive?.content

    @Test
    fun `signature before content is attached to the next part`() {
        val parts = buildJsonArray {
            add(buildJsonObject { put("thoughtSignature", "sig") })
            add(buildJsonObject { put("text", "hi") })
        }

        val parsed = parseGoogleParts(parts, GoogleThoughtSignatureQueue(), parse)

        assertEquals(1, parsed.size)
        assertEquals("sig", signatureOf(parsed.single()))
    }

    @Test
    fun `signature after content is attached to the previous part`() {
        val parts = buildJsonArray {
            add(buildJsonObject { put("text", "hi") })
            add(buildJsonObject { put("thoughtSignature", "sig") })
        }

        val parsed = parseGoogleParts(parts, GoogleThoughtSignatureQueue(), parse)

        assertEquals(1, parsed.size)
        assertEquals("sig", signatureOf(parsed.single()))
    }

    @Test
    fun `signature only part never becomes a content part`() {
        val parts = buildJsonArray {
            add(buildJsonObject { put("thoughtSignature", "sig") })
        }

        val parsed = parseGoogleParts(parts, GoogleThoughtSignatureQueue(), parse)

        assertTrue(parsed.isEmpty())
    }

    @Test
    fun `signature survives across events`() {
        val queue = GoogleThoughtSignatureQueue()
        val signatureOnly = buildJsonArray {
            add(buildJsonObject { put("thoughtSignature", "sig") })
        }
        val content = buildJsonArray {
            add(buildJsonObject { put("text", "hi") })
        }

        val first = parseGoogleParts(signatureOnly, queue, parse)
        val second = parseGoogleParts(content, queue, parse)

        assertTrue(first.isEmpty())
        assertEquals("sig", signatureOf(second.single()))
    }

    @Test
    fun `non primitive signature is treated as unknown content`() {
        val parts = buildJsonArray {
            add(buildJsonObject { put("thoughtSignature", buildJsonObject { put("nested", true) }) })
            add(buildJsonObject { put("text", "hi") })
        }

        val parsed = parseGoogleParts(parts, GoogleThoughtSignatureQueue(), parse)

        // 非字符串签名不算独立签名，按普通 part 解析，不会抛异常
        assertEquals(2, parsed.size)
        assertEquals("hi", (parsed.last() as UIMessagePart.Text).text)
    }
}
