package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.ui.UIMessage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateTransformerTest {
    @Test
    fun `synthetic messages skip the message template`() {
        assertTrue(UIMessage.user("content").copy(isSynthetic = true).skipsInputTemplate())
    }

    @Test
    fun `blank truncation markers skip the message template`() {
        val marker = UIMessage.user("").copy(isContextCheckpoint = true)

        assertTrue(marker.skipsInputTemplate())
    }

    @Test
    fun `normal messages and compression summaries still use the template`() {
        assertFalse(UIMessage.user("hello").skipsInputTemplate())
        assertFalse(UIMessage.user("summary").copy(isContextCheckpoint = true).skipsInputTemplate())
    }
}
