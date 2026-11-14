package me.rerere.rikkahub.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AIIconMatcherTest {
    // RikkaHub 提供商已下线，名字里带 rikka 或 auto 的提供商不再套用官方图标
    @Test
    fun `rikka and auto names have no dedicated icon`() {
        assertNull(computeAIIconByName("RikkaHub"))
        assertNull(computeAIIconByName("auto"))
    }

    @Test
    fun `known providers still resolve to their icons`() {
        assertEquals("openai.svg", computeAIIconByName("OpenAI"))
        assertEquals("deepseek-color.svg", computeAIIconByName("DeepSeek"))
    }
}
