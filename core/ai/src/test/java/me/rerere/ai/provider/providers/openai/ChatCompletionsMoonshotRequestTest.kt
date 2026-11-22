package me.rerere.ai.provider.providers.openai

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
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
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for MoonShot thinking parameters:
 * - K2.6 kept-thinking via thinking.keep = "all" (#1586)
 * - per-model dispatch for K3 and K2.7 Code, and the international/Kimi Code hosts (#1573)
 */
class ChatCompletionsMoonshotRequestTest {

    private lateinit var api: ChatCompletionsAPI

    @Before
    fun setUp() {
        api = ChatCompletionsAPI(OkHttpClient(), KeyRoulette.default())
    }

    // Helper to invoke private buildChatCompletionRequest via reflection
    private fun buildRequest(
        modelId: String,
        reasoningLevel: ReasoningLevel,
        baseUrl: String = "https://api.moonshot.cn/v1",
    ): JsonObject {
        val method = ChatCompletionsAPI::class.java.getDeclaredMethod(
            "buildChatCompletionRequest",
            List::class.java,
            TextGenerationParams::class.java,
            ProviderSetting.OpenAI::class.java,
            Boolean::class.javaPrimitiveType
        )
        method.isAccessible = true
        val model = Model(
            modelId = modelId,
            abilities = listOf(ModelAbility.REASONING)
        )
        val params = TextGenerationParams(
            model = model,
            reasoningLevel = reasoningLevel,
        )
        val providerSetting = ProviderSetting.OpenAI(baseUrl = baseUrl)
        return method.invoke(
            api,
            listOf(UIMessage.user("hi")),
            params,
            providerSetting,
            true
        ) as JsonObject
    }

    // #1586: K2.6 思考开启时发送 thinking.keep = "all"（保留式思考）
    @Test
    fun `k2_6 sends thinking keep all when reasoning enabled`() {
        val body = buildRequest("kimi-k2.6", ReasoningLevel.HIGH)
        val thinking = body["thinking"]?.jsonObject
        assertEquals("enabled", thinking?.get("type")?.jsonPrimitive?.content)
        assertEquals("all", thinking?.get("keep")?.jsonPrimitive?.content)
    }

    // #1586: K2.6 关闭思考时不发送 keep（文档推荐 keep 与 enabled 搭配）
    @Test
    fun `k2_6 omits keep when reasoning disabled`() {
        val body = buildRequest("kimi-k2.6", ReasoningLevel.OFF)
        val thinking = body["thinking"]?.jsonObject
        assertEquals("disabled", thinking?.get("type")?.jsonPrimitive?.content)
        assertFalse(thinking?.containsKey("keep") == true)
    }

    // #1586: K2.5 不支持 keep 参数，即使思考开启也不发送
    @Test
    fun `k2_5 never sends keep`() {
        val body = buildRequest("kimi-k2.5", ReasoningLevel.HIGH)
        val thinking = body["thinking"]?.jsonObject
        assertEquals("enabled", thinking?.get("type")?.jsonPrimitive?.content)
        assertFalse(thinking?.containsKey("keep") == true)
    }

    // #1573: K3 系列不用 thinking，改发顶层 reasoning_effort，档位映射到 low/high/max
    @Test
    fun `k3 maps reasoning levels to reasoning effort`() {
        val expected = mapOf(
            ReasoningLevel.OFF to "low",
            ReasoningLevel.LOW to "low",
            ReasoningLevel.MEDIUM to "high",
            ReasoningLevel.HIGH to "high",
            ReasoningLevel.XHIGH to "max",
            ReasoningLevel.MAX to "max",
        )
        for ((level, effort) in expected) {
            val body = buildRequest("kimi-k3", level)
            assertFalse("K3 must not send thinking", body.containsKey("thinking"))
            assertEquals(effort, body["reasoning_effort"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun `k3 omits reasoning effort for notset and auto`() {
        for (level in listOf(ReasoningLevel.NOTSET, ReasoningLevel.AUTO)) {
            val body = buildRequest("kimi-k3", level)
            assertFalse(body.containsKey("reasoning_effort"))
            assertFalse(body.containsKey("thinking"))
        }
    }

    @Test
    fun `k3 family covers bare k3 and k3-256k on the kimi code gateway`() {
        for (modelId in listOf("k3", "k3-256k", "kimi-k3")) {
            val body = buildRequest(modelId, ReasoningLevel.HIGH, baseUrl = "https://api.kimi.com/coding/v1")
            assertFalse(body.containsKey("thinking"))
            assertEquals("high", body["reasoning_effort"]?.jsonPrimitive?.content)
        }
    }

    // #1573: K2.7 Code 始终开启思考，显式 thinking 会被拒绝，官方建议完全不传
    @Test
    fun `k2_7 code never sends thinking parameters`() {
        val ids = listOf(
            "kimi-k2.7-code",
            "kimi-k2.7-code-highspeed",
            "kimi-for-coding",
            "kimi-for-coding-highspeed",
        )
        for (modelId in ids) {
            for (level in listOf(ReasoningLevel.OFF, ReasoningLevel.HIGH)) {
                val body = buildRequest(modelId, level)
                assertFalse("$modelId must not send thinking", body.containsKey("thinking"))
                assertFalse("$modelId must not send reasoning_effort", body.containsKey("reasoning_effort"))
            }
        }
    }

    // C86 语义：NOTSET 不发参数；AUTO 在 K2.x 按开启处理（K2.6 会带 keep）
    @Test
    fun `k2_6 respects notset and auto semantics`() {
        val notset = buildRequest("kimi-k2.6", ReasoningLevel.NOTSET)
        assertFalse(notset.containsKey("thinking"))

        val auto = buildRequest("kimi-k2.6", ReasoningLevel.AUTO)
        val thinking = auto["thinking"]?.jsonObject
        assertEquals("enabled", thinking?.get("type")?.jsonPrimitive?.content)
        assertEquals("all", thinking?.get("keep")?.jsonPrimitive?.content)
    }

    // 非月之暗面 host 不能走新分派：kimi-k3 在通用 OpenAI 兼容网关仍是旧分支
    @Test
    fun `kimi k3 on a non moonshot host uses the generic branch`() {
        val body = buildRequest("kimi-k3", ReasoningLevel.MEDIUM, baseUrl = "https://api.openai.com/v1")
        assertFalse(body.containsKey("thinking"))
        assertEquals("medium", body["reasoning_effort"]?.jsonPrimitive?.content)
    }

    // #1573: 国际站与 Kimi Code 网关同样按模型分派，不再只有 api.moonshot.cn
    @Test
    fun `moonshot international host gets the same kimi handling`() {
        val body = buildRequest("kimi-k2.6", ReasoningLevel.HIGH, baseUrl = "https://api.moonshot.ai/v1")
        val thinking = body["thinking"]?.jsonObject
        assertEquals("enabled", thinking?.get("type")?.jsonPrimitive?.content)
        assertEquals("all", thinking?.get("keep")?.jsonPrimitive?.content)
    }
}
