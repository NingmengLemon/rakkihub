package me.rerere.rikkahub.data.ai.prompts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompressPromptTest {
    @Test
    fun `default prompt fills the locale`() {
        val prompt = buildCompressPrompt(
            template = DEFAULT_COMPRESS_PROMPT,
            additionalPrompt = "",
            locale = "简体中文",
        )

        assertTrue(prompt.contains("if unclear, use 简体中文"))
        assertFalse(prompt.contains("{locale}"))
    }

    @Test
    fun `additional instructions are appended after the template`() {
        val prompt = buildCompressPrompt(
            template = "Summarize the conversation.",
            additionalPrompt = "Keep the tool results.",
            locale = "English",
        )

        assertEquals(
            "Summarize the conversation.\n\nAdditional instructions from user: Keep the tool results.",
            prompt,
        )
    }

    @Test
    fun `blank additional instructions add nothing`() {
        val prompt = buildCompressPrompt(
            template = "Summarize the conversation.",
            additionalPrompt = "   ",
            locale = "English",
        )

        assertEquals("Summarize the conversation.", prompt)
    }
}
