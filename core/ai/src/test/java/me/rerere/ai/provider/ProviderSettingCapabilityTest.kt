package me.rerere.ai.provider

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderSettingCapabilityTest {
    @Test
    fun `claude always merges adjacent users`() {
        assertTrue(ProviderSetting.Claude().mergesAdjacentUserMessages)
    }

    @Test
    fun `official openai and xai hosts keep turn boundaries`() {
        assertFalse(
            ProviderSetting.OpenAI(baseUrl = "https://api.openai.com/v1").mergesAdjacentUserMessages
        )
        assertFalse(
            ProviderSetting.OpenAI(baseUrl = "https://api.x.ai/v1").mergesAdjacentUserMessages
        )
    }

    @Test
    fun `custom openai hosts and invalid urls merge by default`() {
        assertTrue(
            ProviderSetting.OpenAI(baseUrl = "https://gateway.example.com/v1").mergesAdjacentUserMessages
        )
        assertTrue(ProviderSetting.OpenAI(baseUrl = "").mergesAdjacentUserMessages)
    }

    @Test
    fun `account providers are not handled by this capability`() {
        assertFalse(ProviderSetting.ChatGPT().mergesAdjacentUserMessages)
        assertFalse(ProviderSetting.Grok().mergesAdjacentUserMessages)
    }
}
