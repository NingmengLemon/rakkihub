package me.rerere.ai.ui

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleThoughtSignatureTest {

    @Test
    fun `signature only part is detected`() {
        val part = buildJsonObject { put("thoughtSignature", "sig") }

        assertEquals("sig", part.standaloneGoogleThoughtSignature())
    }

    @Test
    fun `content parts are not signature only`() {
        val textPart = buildJsonObject {
            put("text", "hi")
            put("thoughtSignature", "sig")
        }
        val functionCallPart = buildJsonObject {
            put("functionCall", buildJsonObject { put("name", "tool") })
            put("thoughtSignature", "sig")
        }

        assertNull(textPart.standaloneGoogleThoughtSignature())
        assertNull(functionCallPart.standaloneGoogleThoughtSignature())
    }

    @Test
    fun `empty object is not a signature part`() {
        assertNull(JsonObject(emptyMap()).standaloneGoogleThoughtSignature())
    }

    @Test
    fun `attaching keeps existing metadata`() {
        val part = UIMessagePart.Text("hi", buildJsonObject { put("other", "v") })

        part.attachGoogleThoughtSignature("sig")

        val metadata = requireNotNull(part.metadata)
        assertEquals("sig", metadata["thoughtSignature"]?.jsonPrimitive?.content)
        assertEquals("v", metadata["other"]?.jsonPrimitive?.content)
    }

    @Test
    fun `attaching creates metadata when absent`() {
        val part = UIMessagePart.Reasoning("think")

        part.attachGoogleThoughtSignature("sig")

        assertEquals("sig", part.metadata?.get("thoughtSignature")?.jsonPrimitive?.content)
    }

    @Test
    fun `attaching never overwrites an existing signature`() {
        val part = UIMessagePart.Text("hi", buildJsonObject { put("thoughtSignature", "keep") })

        part.attachGoogleThoughtSignature("new")

        assertEquals("keep", part.metadata?.get("thoughtSignature")?.jsonPrimitive?.content)
    }
}
