package me.rerere.ai.provider

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.agent.AgentHooks
import me.rerere.ai.agent.AgentRequest
import me.rerere.ai.agent.RetryPolicy
import me.rerere.ai.agent.StepRequest
import me.rerere.ai.agent.runAgent
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.provider.providers.claude.ClaudeProvider
import me.rerere.ai.provider.providers.google.GoogleProvider
import me.rerere.ai.provider.providers.openai.OpenAIProvider
import me.rerere.ai.provider.providers.openai.ResponseAPI
import me.rerere.ai.ui.ClaudeReasoningMetadata
import me.rerere.ai.ui.StreamChunk
import me.rerere.ai.ui.StreamChunkHandler
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.metadataAs
import me.rerere.ai.util.HttpException
import me.rerere.ai.util.KeyRoulette
import me.rerere.ai.util.LlmFailure
import me.rerere.ai.util.LlmFailureCode
import me.rerere.tools.providermock.MockPayloads
import me.rerere.tools.providermock.MockScenario
import me.rerere.tools.providermock.ProviderMockRule
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * End-to-end provider tests over the real HTTP path: the stock provider implementations talk to
 * [me.rerere.tools.providermock.ProviderMockServer], decode its SSE streams and map failures.
 *
 * `ProviderManager` needs an Android `Context`, but every provider class accepts `context = null`,
 * so the full provider path runs in these JVM unit tests. Only `AgentRequest.provider` typing
 * needs an unchecked cast because [Provider] is invariant.
 */
class ProviderMockIntegrationTest {
    @get:Rule
    val mock = ProviderMockRule()

    private val client: OkHttpClient = OkHttpClient()

    // ---- OpenAI Chat Completions ----

    @Test
    fun `openai chat completions decodes text usage and finish reason`() = runTest {
        val model = model(MockScenario.NORMAL)
        val provider = OpenAIProvider(client)

        val streamed = collectStream(provider.streamText(openAiSetting(), userMessages, params(model)), model)

        assertEquals(MockPayloads.TEXT, streamed.message.text())
        assertEquals(
            TokenUsage(promptTokens = 11, completionTokens = 5, cachedTokens = 3, totalTokens = 16),
            streamed.message.usage
        )
        assertEquals("stop", streamed.finish?.finishReason)
        val request = mock.recorder.assertHasRequest("POST", "/v1/chat/completions")
        assertEquals("Bearer <redacted>", request.header("Authorization"))
    }

    @Test
    fun `openai chat completions decodes a tool call with parsed arguments`() = runTest {
        val model = model(MockScenario.TOOL)
        val provider = OpenAIProvider(client)

        val streamed = collectStream(provider.streamText(openAiSetting(), userMessages, params(model)), model)

        val tool = streamed.message.parts.filterIsInstance<UIMessagePart.Tool>().single()
        assertEquals(MockPayloads.TOOL_CALL_ID, tool.toolCallId)
        assertEquals(MockPayloads.TOOL_NAME, tool.toolName)
        assertEquals("Paris", Json.parseToJsonElement(tool.input).jsonObject["city"]!!.jsonPrimitive.content)
        assertEquals("tool_calls", streamed.finish?.finishReason)
    }

    // ---- OpenAI Responses ----

    @Test
    fun `openai responses decodes text usage and completed terminal`() = runTest {
        val model = model(MockScenario.NORMAL)
        val api = ResponseAPI(client, KeyRoulette.default())

        val streamed = collectStream(api.streamText(openAiSetting(), userMessages, params(model)), model)

        assertEquals(MockPayloads.TEXT, streamed.message.text())
        assertEquals(
            TokenUsage(promptTokens = 11, completionTokens = 5, cachedTokens = 3, totalTokens = 16),
            streamed.message.usage
        )
        assertEquals("completed", streamed.finish?.finishReason)
        mock.recorder.assertHasRequest("POST", "/v1/responses")
    }

    @Test
    fun `openai responses decodes a tool call with parsed arguments`() = runTest {
        val model = model(MockScenario.TOOL)
        val api = ResponseAPI(client, KeyRoulette.default())

        val streamed = collectStream(api.streamText(openAiSetting(), userMessages, params(model)), model)

        val tool = streamed.message.parts.filterIsInstance<UIMessagePart.Tool>().single()
        assertEquals(MockPayloads.TOOL_CALL_ID, tool.toolCallId)
        assertEquals(MockPayloads.TOOL_NAME, tool.toolName)
        assertEquals("Paris", Json.parseToJsonElement(tool.input).jsonObject["city"]!!.jsonPrimitive.content)
    }

    // ---- Anthropic Messages ----

    @Test
    fun `claude decodes text usage and stop reason`() = runTest {
        val model = model(MockScenario.NORMAL)
        val provider = ClaudeProvider(client)

        val streamed = collectStream(provider.streamText(claudeSetting(), userMessages, params(model)), model)

        assertEquals(MockPayloads.TEXT, streamed.message.text())
        assertEquals(
            TokenUsage(promptTokens = 11, completionTokens = 5, cachedTokens = 0, totalTokens = 16),
            streamed.message.usage
        )
        assertEquals("end_turn", streamed.finish?.finishReason)
        mock.recorder.assertHasRequest("POST", "/v1/messages")
    }

    @Test
    fun `claude decodes thinking deltas and the replayed signature`() = runTest {
        val model = model(MockScenario.REASONING)
        val provider = ClaudeProvider(client)

        val streamed = collectStream(provider.streamText(claudeSetting(), userMessages, params(model)), model)

        val reasoning = streamed.message.parts.filterIsInstance<UIMessagePart.Reasoning>().single()
        assertEquals(MockPayloads.REASONING, reasoning.reasoning)
        assertEquals(MockPayloads.CLAUDE_SIGNATURE, reasoning.metadataAs<ClaudeReasoningMetadata>()?.signature)
        assertEquals(MockPayloads.TEXT, streamed.message.text())
    }

    @Test
    fun `claude decodes a tool call with parsed arguments`() = runTest {
        val model = model(MockScenario.TOOL)
        val provider = ClaudeProvider(client)

        val streamed = collectStream(provider.streamText(claudeSetting(), userMessages, params(model)), model)

        val tool = streamed.message.parts.filterIsInstance<UIMessagePart.Tool>().single()
        assertEquals(MockPayloads.TOOL_CALL_ID, tool.toolCallId)
        assertEquals(MockPayloads.TOOL_NAME, tool.toolName)
        assertEquals("Paris", Json.parseToJsonElement(tool.input).jsonObject["city"]!!.jsonPrimitive.content)
        assertEquals("tool_use", streamed.finish?.finishReason)
    }

    // ---- Gemini ----

    @Test
    fun `gemini decodes text usage and finish reason`() = runTest {
        val model = model(MockScenario.NORMAL)
        val provider = GoogleProvider(client)

        val streamed = collectStream(provider.streamText(googleSetting(), userMessages, params(model)), model)

        assertEquals(MockPayloads.TEXT, streamed.message.text())
        assertEquals(
            TokenUsage(promptTokens = 11, completionTokens = 5, cachedTokens = 3, totalTokens = 16),
            streamed.message.usage
        )
        assertEquals("STOP", streamed.finish?.finishReason)
        val request = mock.recorder.assertHasRequest(
            "POST",
            "/v1beta/models/${model.modelId}:streamGenerateContent",
        )
        assertEquals("alt=sse", request.query)
    }

    @Test
    fun `gemini decodes a function call with parsed arguments`() = runTest {
        val model = model(MockScenario.TOOL)
        val provider = GoogleProvider(client)

        val streamed = collectStream(provider.streamText(googleSetting(), userMessages, params(model)), model)

        val tool = streamed.message.parts.filterIsInstance<UIMessagePart.Tool>().single()
        assertEquals(MockPayloads.TOOL_CALL_ID, tool.toolCallId)
        assertEquals(MockPayloads.TOOL_NAME, tool.toolName)
        assertEquals("Paris", Json.parseToJsonElement(tool.input).jsonObject["city"]!!.jsonPrimitive.content)
    }

    // ---- Failure mapping ----

    @Test
    fun `openai rate limit maps to rate limit failure with retry after`() = runTest {
        val provider = OpenAIProvider(client)
        val failure = failure(
            provider.streamText(openAiSetting(), userMessages, params(model(MockScenario.RATE_LIMIT))),
        )

        val http = failure as? HttpException ?: error("expected HttpException, got $failure")
        assertEquals(LlmFailureCode.RATE_LIMIT, http.code)
        assertEquals(429, http.statusCode)
        assertEquals(2_000L, http.retryAfterMs)
    }

    @Test
    fun `responses server error maps to server failure`() = runTest {
        val api = ResponseAPI(client, KeyRoulette.default())
        val failure = failure(
            api.streamText(openAiSetting(), userMessages, params(model(MockScenario.SERVER_ERROR))),
        )

        val http = failure as? HttpException ?: error("expected HttpException, got $failure")
        assertEquals(LlmFailureCode.SERVER, http.code)
        assertEquals(500, http.statusCode)
    }

    @Test
    fun `claude rate limit maps to rate limit failure with retry after`() = runTest {
        val provider = ClaudeProvider(client)
        val failure = failure(
            provider.streamText(claudeSetting(), userMessages, params(model(MockScenario.RATE_LIMIT))),
        )

        val http = failure as? HttpException ?: error("expected HttpException, got $failure")
        assertEquals(LlmFailureCode.RATE_LIMIT, http.code)
        assertEquals(429, http.statusCode)
        assertEquals(2_000L, http.retryAfterMs)
    }

    @Test
    fun `gemini rate limit maps to rate limit failure with retry after`() = runTest {
        val provider = GoogleProvider(client)
        val failure = failure(
            provider.streamText(googleSetting(), userMessages, params(model(MockScenario.RATE_LIMIT))),
        )

        val http = failure as? HttpException ?: error("expected HttpException, got $failure")
        assertEquals(LlmFailureCode.RATE_LIMIT, http.code)
        assertEquals(429, http.statusCode)
        assertEquals(2_000L, http.retryAfterMs)
    }

    @Test
    fun `closing a truncated claude stream raises IOException`() = runTest {
        val provider = ClaudeProvider(client)
        val failure = failure(
            provider.streamText(claudeSetting(), userMessages, params(model(MockScenario.TRUNCATE))),
        )

        assertTrue("expected IOException, got $failure", failure is IOException)
    }

    @Test
    fun `closing a truncated gemini stream raises IOException`() = runTest {
        val provider = GoogleProvider(client)
        val failure = failure(
            provider.streamText(googleSetting(), userMessages, params(model(MockScenario.TRUNCATE))),
        )

        assertTrue("expected IOException, got $failure", failure is IOException)
    }

    @Test
    fun `empty chat stream becomes EMPTY_RESPONSE in the agent loop`() = runTest {
        val failure = emptyResponseFailure(
            OpenAIProvider(client).erased(),
            openAiSetting(),
            model(MockScenario.EMPTY),
        )

        val llmFailure = failure as? LlmFailure ?: error("expected LlmFailure, got $failure")
        assertEquals(LlmFailureCode.EMPTY_RESPONSE, llmFailure.code)
    }

    @Test
    fun `empty gemini stream becomes EMPTY_RESPONSE in the agent loop`() = runTest {
        val failure = emptyResponseFailure(
            GoogleProvider(client).erased(),
            googleSetting(),
            model(MockScenario.EMPTY),
        )

        val llmFailure = failure as? LlmFailure ?: error("expected LlmFailure, got $failure")
        assertEquals(LlmFailureCode.EMPTY_RESPONSE, llmFailure.code)
    }

    // ---- Model listing ----

    @Test
    fun `provider listModels paths are served by the mock`() = runTest {
        val openAiModels = OpenAIProvider(client).listModels(openAiSetting())
        assertTrue(openAiModels.any { it.modelId == MockScenario.NORMAL.modelName })

        val claudeModels = ClaudeProvider(client).listModels(claudeSetting())
        assertTrue(claudeModels.any { it.modelId == MockScenario.NORMAL.modelName })

        val googleModels = GoogleProvider(client).listModels(googleSetting())
        assertTrue(googleModels.any { it.modelId == MockScenario.NORMAL.modelName })

        assertNotNull(mock.recorder.find("GET", "/v1/models"))
        assertNotNull(mock.recorder.find("GET", "/v1beta/models"))
    }

    // ---- Helpers ----

    private data class StreamedMessage(val message: UIMessage, val finish: StreamChunk.Finish?)

    private suspend fun collectStream(flow: Flow<StreamChunk>, model: Model): StreamedMessage {
        val handler = StreamChunkHandler(model)
        var message = UIMessage(role = MessageRole.ASSISTANT, parts = emptyList(), modelId = model.id)
        var finish: StreamChunk.Finish? = null
        flow.collect { chunk ->
            if (chunk is StreamChunk.Finish) finish = chunk
            message = handler.handle(listOf(message), chunk).single()
        }
        return StreamedMessage(message, finish)
    }

    private suspend fun failure(flow: Flow<StreamChunk>): Throwable =
        runCatching { flow.collect { } }.exceptionOrNull()
            ?: throw AssertionError("expected the stream to fail")

    private suspend fun emptyResponseFailure(
        provider: Provider<ProviderSetting>,
        setting: ProviderSetting,
        model: Model,
    ): Throwable {
        val request = AgentRequest(
            provider = provider,
            providerSetting = setting,
            messages = userMessages,
            retry = RetryPolicy.NONE,
            streamIdleTimeout = null,
            toolTimeout = null,
        )
        val hooks = object : AgentHooks {
            override suspend fun prepareStep(messages: List<UIMessage>) =
                StepRequest(messages, TextGenerationParams(model = model))
        }
        return runCatching { runAgent(request, hooks).collect { } }.exceptionOrNull()
            ?: throw AssertionError("expected the agent loop to fail on an empty response")
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : ProviderSetting> Provider<T>.erased(): Provider<ProviderSetting> =
        this as Provider<ProviderSetting>

    private fun UIMessage.text(): String =
        parts.filterIsInstance<UIMessagePart.Text>().joinToString("") { it.text }

    private fun model(scenario: MockScenario) = Model(modelId = scenario.modelName, displayName = scenario.wireName)

    private fun params(model: Model) = TextGenerationParams(model = model, maxTokens = 64)

    private val userMessages: List<UIMessage>
        get() = listOf(UIMessage.user("hi"))

    private fun openAiSetting() = ProviderSetting.OpenAI(
        apiKey = "test-key",
        baseUrl = mock.openAiBaseUrl,
    )

    private fun claudeSetting() = ProviderSetting.Claude(
        apiKey = "test-key",
        baseUrl = mock.claudeBaseUrl,
    )

    private fun googleSetting() = ProviderSetting.Google(
        apiKey = "test-key",
        baseUrl = mock.googleBaseUrl,
    )
}
