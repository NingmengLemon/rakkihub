package me.rerere.rikkahub.data.ai

import me.rerere.ai.provider.ProviderSetting
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class PromptCacheOverrideTest {

    @Test
    fun `temporary compression model strips Claude prompt cache`() {
        val claude = ProviderSetting.Claude(promptCaching = true)

        val stripped = claude.withoutClaudePromptCache() as ProviderSetting.Claude

        assertFalse(stripped.promptCaching)
        assertTrue("其他字段不应变化", stripped.apiKey == claude.apiKey)
    }

    @Test
    fun `non Claude providers are left untouched`() {
        val openai = ProviderSetting.OpenAI()
        val google = ProviderSetting.Google()

        assertSame(openai, openai.withoutClaudePromptCache())
        assertSame(google, google.withoutClaudePromptCache())
    }

    @Test
    fun `same model reuses the conversation model and keeps cache`() {
        val modelId = Uuid.random()

        assertTrue(shouldUseConversationModel(modelId, modelId))
        assertTrue(shouldUseConversationModel(null, modelId))
        assertFalse(shouldDisablePromptCache(modelId, modelId))
        assertFalse(shouldDisablePromptCache(null, modelId))
    }

    @Test
    fun `different model uses the override and disables cache`() {
        val current = Uuid.random()
        val selected = Uuid.random()

        assertFalse(shouldUseConversationModel(selected, current))
        assertTrue(shouldDisablePromptCache(selected, current))
        assertTrue(shouldDisablePromptCache(selected, null))
    }
}
