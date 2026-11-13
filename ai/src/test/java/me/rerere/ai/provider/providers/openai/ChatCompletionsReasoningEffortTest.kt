package me.rerere.ai.provider.providers.openai

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.util.KeyRoulette
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 关闭思考时不能把 reasoning_effort 发成 "none"：多数 OpenAI 兼容网关只接受
 * low/medium/high，收到 none 会直接 400（#1644，OCR 默认就是关闭思考）。
 */
class ChatCompletionsReasoningEffortTest {

    private val api = ChatCompletionsAPI(OkHttpClient(), KeyRoulette.default())

    private fun buildRequest(
        baseUrl: String,
        reasoningLevel: ReasoningLevel,
    ): JsonObject {
        val method = ChatCompletionsAPI::class.java.getDeclaredMethod(
            "buildChatCompletionRequest",
            List::class.java,
            TextGenerationParams::class.java,
            ProviderSetting.OpenAI::class.java,
            Boolean::class.javaPrimitiveType,
        )
        method.isAccessible = true
        val model = Model(
            modelId = "test-model",
            abilities = listOf(ModelAbility.REASONING),
        )
        val params = TextGenerationParams(
            model = model,
            reasoningLevel = reasoningLevel,
        )
        return method.invoke(
            api,
            listOf(UIMessage.user("hi")),
            params,
            ProviderSetting.OpenAI(baseUrl = baseUrl),
            true,
        ) as JsonObject
    }

    @Test
    fun `opencode omits reasoning effort when thinking is off`() {
        val request = buildRequest("https://opencode.ai/v1", ReasoningLevel.OFF)

        assertFalse(request.containsKey("reasoning_effort"))
    }

    @Test
    fun `opencode keeps reasoning effort for explicit levels`() {
        val request = buildRequest("https://opencode.ai/v1", ReasoningLevel.MEDIUM)

        assertEquals("medium", request["reasoning_effort"]?.jsonPrimitive?.content)
    }

    @Test
    fun `generic gateway omits reasoning effort when thinking is off`() {
        val request = buildRequest("https://api.example.com/v1", ReasoningLevel.OFF)

        assertFalse(request.containsKey("reasoning_effort"))
    }

    @Test
    fun `generic gateway keeps reasoning effort for explicit levels`() {
        val request = buildRequest("https://api.example.com/v1", ReasoningLevel.HIGH)

        assertEquals("high", request["reasoning_effort"]?.jsonPrimitive?.content)
    }

    @Test
    fun `auto level never sends reasoning effort`() {
        val generic = buildRequest("https://api.example.com/v1", ReasoningLevel.AUTO)
        val opencode = buildRequest("https://opencode.ai/v1", ReasoningLevel.AUTO)

        assertFalse(generic.containsKey("reasoning_effort"))
        assertFalse(opencode.containsKey("reasoning_effort"))
    }
}
