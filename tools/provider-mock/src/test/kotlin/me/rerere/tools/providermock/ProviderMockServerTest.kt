package me.rerere.tools.providermock

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class ProviderMockServerTest {
    @get:Rule
    val mock = ProviderMockRule()

    // ---- OpenAI Chat Completions ----

    @Test
    fun `chat completions normal stream is an exact SSE trace`() {
        val response = postJson(
            "${mock.openAiBaseUrl}/chat/completions",
            """{"model":"mock-normal","stream":true}""",
            headers = mapOf("Authorization" to "Bearer test-key", "x-api-key" to "test-key"),
        )

        assertEquals(200, response.statusCode())
        assertTrue(response.headers().firstValue("content-type").orElse("").startsWith("text/event-stream"))
        val frames = sseFrames(response.body())
        assertEquals(
            """{"id":"chatcmpl-mock","object":"chat.completion.chunk","model":"mock-normal",""" +
                """"choices":[{"index":0,"delta":{"content":"Hello from "},"finish_reason":null}]}""",
            frames.first().data,
        )
        assertEquals("[DONE]", frames.last().data)
        assertEquals(MockPayloads.TEXT, chatText(frames))
        assertEquals(MockPayloads.TOTAL_TOKENS, chatUsage(frames)["total_tokens"]!!.jsonPrimitive.int)

        // 记录器只留 Authorization 的 scheme 前缀，真实值不进记录、日志或断言消息
        val recorded = mock.recorder.single()
        assertEquals("POST", recorded.method)
        assertEquals("/v1/chat/completions", recorded.path)
        assertEquals("Bearer <redacted>", recorded.header("Authorization"))
        assertEquals("<redacted>", recorded.header("x-api-key"))
        assertEquals("""{"model":"mock-normal","stream":true}""", recorded.rawBody)
        assertFalse(recorded.toString().contains("test-key"))
    }

    @Test
    fun `chat completions tool call carries incremental arguments and finish reason`() {
        val frames = chatFrames("mock-tool")

        assertEquals(MockPayloads.TOOL_ARGUMENTS, chatToolArguments(frames))
        assertEquals("tool_calls", chatFinishReason(frames))
        val firstTool = frames.first { it.data.contains("\"tool_calls\"") }
            .json()["choices"]!!.jsonArray[0].jsonObject["delta"]!!.jsonObject
            .get("tool_calls")!!.jsonArray[0].jsonObject
        assertEquals(MockPayloads.TOOL_CALL_ID, firstTool["id"]!!.jsonPrimitive.content)
        assertEquals(
            MockPayloads.TOOL_NAME,
            firstTool["function"]!!.jsonObject["name"]!!.jsonPrimitive.content,
        )
        assertEquals("[DONE]", frames.last().data)
    }

    @Test
    fun `chat completions failure scenarios use status retry-after and html`() {
        val rateLimited = postJson("${mock.openAiBaseUrl}/chat/completions", """{"model":"mock-rate-limit"}""")
        assertEquals(429, rateLimited.statusCode())
        assertEquals("2", rateLimited.headers().firstValue("Retry-After").orElse(null))
        assertEquals(
            "rate_limit_error",
            Json.parseToJsonElement(rateLimited.body()).jsonObject["error"]!!
                .jsonObject["type"]!!.jsonPrimitive.content,
        )

        val serverError = postJson("${mock.openAiBaseUrl}/chat/completions", """{"model":"mock-server-error"}""")
        assertEquals(500, serverError.statusCode())
        assertTrue(serverError.body().startsWith("<html>"))
        assertTrue(
            serverError.headers().firstValue("content-type").orElse("").startsWith("text/html"),
        )
    }

    @Test
    fun `chat completions empty stream carries only the done sentinel`() {
        val frames = chatFrames("mock-empty")
        assertEquals(1, frames.size)
        assertEquals("[DONE]", frames.single().data)
    }

    @Test
    fun `chat completions non-stream request returns plain json`() {
        val response = postJson("${mock.openAiBaseUrl}/chat/completions", """{"model":"mock-normal"}""")
        assertEquals(200, response.statusCode())
        assertFalse(response.body().startsWith("data: "))
        val body = Json.parseToJsonElement(response.body()).jsonObject
        assertEquals(
            MockPayloads.TEXT,
            body["choices"]!!.jsonArray[0].jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content,
        )
        assertEquals(MockPayloads.TOTAL_TOKENS, body["usage"]!!.jsonObject["total_tokens"]!!.jsonPrimitive.int)
    }

    // ---- OpenAI Responses ----

    @Test
    fun `responses normal stream has typed events and completed terminal`() {
        val frames = responsesFrames("mock-normal")
        assertEquals(
            listOf(
                "response.created",
                "response.output_item.added",
                "response.content_part.added",
                "response.output_text.delta",
                "response.output_text.delta",
                "response.output_text.done",
                "response.content_part.done",
                "response.output_item.done",
                "response.completed",
            ),
            frames.map { it.event },
        )
        assertEquals(MockPayloads.TEXT, responsesText(frames))
        val completed = frames.last().json()["response"]!!.jsonObject
        assertEquals("completed", completed["status"]!!.jsonPrimitive.content)
        assertEquals(MockPayloads.TOTAL_TOKENS, completed["usage"]!!.jsonObject["total_tokens"]!!.jsonPrimitive.int)
        frames.forEach { frame -> assertEquals(frame.event, frame.json()["type"]!!.jsonPrimitive.content) }
    }

    @Test
    fun `responses tool call streams argument deltas then function_call item`() {
        val frames = responsesFrames("mock-tool")
        assertEquals(MockPayloads.TOOL_ARGUMENTS, responsesToolArguments(frames))
        assertEquals("response.completed", frames.last().event)
        val functionItem = frames.first { it.event == "response.output_item.done" }
            .json()["item"]!!.jsonObject
        assertEquals("function_call", functionItem["type"]!!.jsonPrimitive.content)
        assertEquals(MockPayloads.TOOL_CALL_ID, functionItem["call_id"]!!.jsonPrimitive.content)
    }

    @Test
    fun `responses failure scenarios use OpenAI error shape`() {
        val rateLimited = postJson("${mock.openAiBaseUrl}/responses", """{"model":"mock-rate-limit","stream":true}""")
        assertEquals(429, rateLimited.statusCode())
        assertEquals("2", rateLimited.headers().firstValue("Retry-After").orElse(null))
        assertTrue(rateLimited.body().contains("rate_limit_error"))

        val serverError = postJson("${mock.openAiBaseUrl}/responses", """{"model":"mock-server-error","stream":true}""")
        assertEquals(500, serverError.statusCode())
        assertTrue(serverError.body().startsWith("<html>"))
    }

    // ---- Anthropic Messages ----

    @Test
    fun `claude normal stream has message events and usage`() {
        val frames = claudeFrames("mock-normal")
        assertEquals(
            listOf(
                "message_start",
                "content_block_start",
                "content_block_delta",
                "content_block_delta",
                "content_block_stop",
                "message_delta",
                "message_stop",
            ),
            frames.map { it.event },
        )
        assertEquals(MockPayloads.TEXT, claudeText(frames))
        assertEquals("end_turn", claudeStopReason(frames))
        assertEquals(MockPayloads.PROMPT_TOKENS, claudeInputTokens(frames))
        assertFalse(frames.any { it.data == "[DONE]" })
    }

    @Test
    fun `claude reasoning stream has thinking block and signature delta`() {
        val frames = claudeFrames("mock-reasoning")
        assertEquals(MockPayloads.REASONING, claudeThinking(frames))
        assertTrue(frames.any { it.event == "content_block_delta" && it.data.contains("\"signature_delta\"") })
        assertTrue(frames.any { it.data.contains(MockPayloads.CLAUDE_SIGNATURE) })
    }

    @Test
    fun `claude tool stream carries tool_use block and input deltas`() {
        val frames = claudeFrames("mock-tool")
        assertEquals(MockPayloads.TOOL_ARGUMENTS, claudeToolInput(frames))
        assertEquals("tool_use", claudeStopReason(frames))
    }

    @Test
    fun `claude failure scenarios use Anthropic error shape`() {
        val rateLimited = postJson("${mock.claudeBaseUrl}/messages", """{"model":"mock-rate-limit","stream":true}""")
        assertEquals(429, rateLimited.statusCode())
        assertEquals("2", rateLimited.headers().firstValue("Retry-After").orElse(null))
        assertEquals(
            "error",
            Json.parseToJsonElement(rateLimited.body()).jsonObject["type"]!!.jsonPrimitive.content,
        )

        val serverError = postJson("${mock.claudeBaseUrl}/messages", """{"model":"mock-server-error","stream":true}""")
        assertEquals(500, serverError.statusCode())
        assertTrue(serverError.body().startsWith("<html>"))
    }

    @Test
    fun `claude empty stream has only message start and stop`() {
        val frames = claudeFrames("mock-empty")
        assertEquals(listOf("message_start", "message_stop"), frames.map { it.event })
        assertTrue(claudeText(frames).isEmpty())
    }

    // ---- Gemini ----

    @Test
    fun `gemini normal stream ends with finish reason and usage`() {
        val response = postStream("${mock.googleBaseUrl}/models/mock-normal:streamGenerateContent?alt=sse")
        assertEquals(200, response.statusCode())
        val frames = sseFrames(response.body())
        assertEquals(3, frames.size)
        assertEquals(MockPayloads.TEXT, geminiText(frames))
        val finish = frames.last().json()["candidates"]!!.jsonArray[0].jsonObject
        assertEquals("STOP", finish["finishReason"]!!.jsonPrimitive.content)
        val usage = frames.last().json()["usageMetadata"]!!.jsonObject
        assertEquals(MockPayloads.TOTAL_TOKENS, usage["totalTokenCount"]!!.jsonPrimitive.int)
        assertEquals("alt=sse", mock.recorder.single().query)
    }

    @Test
    fun `gemini reasoning stream uses thought parts`() {
        val frames = geminiFrames("mock-reasoning")
        assertEquals(MockPayloads.REASONING, geminiText(frames, thought = true))
        assertEquals(MockPayloads.TEXT, geminiText(frames, thought = false))
    }

    @Test
    fun `gemini tool stream carries functionCall with parsed args`() {
        val frames = geminiFrames("mock-tool")
        val functionCall = frames.first().json()["candidates"]!!.jsonArray[0].jsonObject["content"]!!
            .jsonObject["parts"]!!.jsonArray[0].jsonObject["functionCall"]!!.jsonObject
        assertEquals(MockPayloads.TOOL_NAME, functionCall["name"]!!.jsonPrimitive.content)
        assertEquals(MockPayloads.TOOL_CALL_ID, functionCall["id"]!!.jsonPrimitive.content)
        assertEquals("Paris", functionCall["args"]!!.jsonObject["city"]!!.jsonPrimitive.content)
    }

    @Test
    fun `gemini failure scenarios use Google error shape`() {
        val rateLimited = postStream("${mock.googleBaseUrl}/models/mock-rate-limit:streamGenerateContent?alt=sse")
        assertEquals(429, rateLimited.statusCode())
        assertEquals("2", rateLimited.headers().firstValue("Retry-After").orElse(null))
        assertEquals(
            "RESOURCE_EXHAUSTED",
            Json.parseToJsonElement(rateLimited.body()).jsonObject["error"]!!
                .jsonObject["status"]!!.jsonPrimitive.content,
        )

        val serverError = postStream("${mock.googleBaseUrl}/models/mock-server-error:streamGenerateContent?alt=sse")
        assertEquals(500, serverError.statusCode())
        assertTrue(serverError.body().startsWith("<html>"))
    }

    @Test
    fun `gemini non-stream request returns plain json`() {
        val response = postJson("${mock.googleBaseUrl}/models/mock-normal:generateContent", """{"contents":[]}""")
        assertEquals(200, response.statusCode())
        val body = Json.parseToJsonElement(response.body()).jsonObject
        val candidate = body["candidates"]!!.jsonArray[0].jsonObject
        assertEquals("STOP", candidate["finishReason"]!!.jsonPrimitive.content)
        val text = candidate["content"]!!.jsonObject["parts"]!!.jsonArray
            .joinToString("") { part -> part.jsonObject["text"]!!.jsonPrimitive.content }
        assertEquals(MockPayloads.TEXT, text)
    }

    // ---- Cross-protocol behavior ----

    @Test
    fun `truncation closes without the protocol terminal event`() {
        val chat = chatFrames("mock-truncate")
        assertFalse(chat.any { it.data == "[DONE]" })
        assertFalse(chat.any { it.data.contains("\"finish_reason\":\"") })
        assertEquals(MockPayloads.TEXT, chatText(chat))

        val responses = responsesFrames("mock-truncate")
        assertFalse(responses.any { it.event == "response.completed" })
        assertEquals("response.output_text.delta", responses.last().event)

        val claude = claudeFrames("mock-truncate")
        assertFalse(claude.any { it.event == "message_stop" })
        assertFalse(claude.any { it.event == "message_delta" })

        val gemini = geminiFrames("mock-truncate")
        assertEquals(1, gemini.size)
        assertFalse(gemini.last().json().containsKey("usageMetadata"))
        assertFalse(gemini.last().json()["candidates"]!!.jsonArray[0].jsonObject.containsKey("finishReason"))
    }

    @Test
    fun `strict chat assertion rejects a stream missing the terminal event`() {
        val truncated = chatFrames("mock-truncate")
        assertThrows(AssertionError::class.java) { assertChatTextTrace(truncated) }
    }

    @Test
    fun `request header overrides the model scenario`() {
        val frames = sseFrames(
            postJson(
                "${mock.openAiBaseUrl}/chat/completions",
                """{"model":"mock-normal","stream":true}""",
                headers = mapOf(MockScenario.HEADER to "tool"),
            ).body(),
        )
        assertEquals(MockPayloads.TOOL_ARGUMENTS, chatToolArguments(frames))
    }

    @Test
    fun `slow scenario delays between chunks and honors the delay header`() {
        val started = System.currentTimeMillis()
        val response = postJson(
            "${mock.openAiBaseUrl}/chat/completions",
            """{"model":"mock-slow","stream":true}""",
            headers = mapOf(MockScenario.DELAY_HEADER to "40"),
        )
        val elapsed = System.currentTimeMillis() - started
        assertEquals(200, response.statusCode())
        assertEquals(MockPayloads.TEXT, chatText(sseFrames(response.body())))
        assertTrue("elapsed=${elapsed}ms", elapsed >= 150)
    }

    @Test
    fun `model listing endpoints serve both shapes`() {
        val openAi = Json.parseToJsonElement(get("${mock.openAiBaseUrl}/models").body()).jsonObject
        assertTrue(
            openAi["data"]!!.jsonArray.any {
                it.jsonObject["id"]!!.jsonPrimitive.content == MockScenario.NORMAL.modelName
            },
        )

        val google = Json.parseToJsonElement(get("${mock.googleBaseUrl}/models").body()).jsonObject
        assertTrue(
            google["models"]!!.jsonArray.any {
                it.jsonObject["name"]!!.jsonPrimitive.content == "models/${MockScenario.NORMAL.modelName}"
            },
        )
    }

    // ---- HTTP helpers ----

    private fun get(url: String): HttpResponse<String> = HTTP.send(
        HttpRequest.newBuilder(URI.create(url)).timeout(REQUEST_TIMEOUT).GET().build(),
        HttpResponse.BodyHandlers.ofString(),
    )

    private fun postJson(
        url: String,
        body: String,
        headers: Map<String, String> = emptyMap(),
    ): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(REQUEST_TIMEOUT)
            .header("Content-Type", "application/json")
        headers.forEach { (name, value) -> builder.header(name, value) }
        return HTTP.send(
            builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
            HttpResponse.BodyHandlers.ofString(),
        )
    }

    private fun postStream(url: String): HttpResponse<String> =
        postJson(url, """{"contents":[]}""", headers = mapOf("Accept" to "text/event-stream"))

    // ---- SSE parsing and assertions ----

    private fun sseFrames(body: String): List<SseFrame> = body.split("\n\n")
        .filter { it.isNotBlank() }
        .map { block ->
            var event: String? = null
            var id: String? = null
            val data = StringBuilder()
            block.lineSequence().forEach { line ->
                when {
                    line.startsWith("event: ") -> event = line.removePrefix("event: ")
                    line.startsWith("id: ") -> id = line.removePrefix("id: ")
                    line.startsWith("data: ") -> {
                        if (data.isNotEmpty()) data.append('\n')
                        data.append(line.removePrefix("data: "))
                    }
                }
            }
            SseFrame(data = data.toString(), event = event, id = id)
        }

    private fun SseFrame.json(): JsonObject = Json.parseToJsonElement(data).jsonObject

    private fun chatFrames(model: String): List<SseFrame> = sseFrames(
        postJson("${mock.openAiBaseUrl}/chat/completions", """{"model":"$model","stream":true}""").body(),
    )

    private fun responsesFrames(model: String): List<SseFrame> = sseFrames(
        postJson("${mock.openAiBaseUrl}/responses", """{"model":"$model","stream":true}""").body(),
    )

    private fun claudeFrames(model: String): List<SseFrame> = sseFrames(
        postJson("${mock.claudeBaseUrl}/messages", """{"model":"$model","stream":true}""").body(),
    )

    private fun geminiFrames(model: String): List<SseFrame> = sseFrames(
        postStream("${mock.googleBaseUrl}/models/$model:streamGenerateContent?alt=sse").body(),
    )

    private fun geminiText(frames: List<SseFrame>, thought: Boolean = false): String = frames.joinToString("") {
        it.json()["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject
            ?.get("parts")?.jsonArray.orEmpty()
            .filter { part -> (part.jsonObject["thought"]?.jsonPrimitive?.booleanOrNull == true) == thought }
            .joinToString("") { part -> part.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
    }

    private fun chatJsonFrames(frames: List<SseFrame>): List<SseFrame> = frames.filter { it.data != "[DONE]" }

    private fun chatText(frames: List<SseFrame>): String = chatJsonFrames(frames).joinToString("") {
        it.json()["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("delta")?.jsonObject
            ?.get("content")?.jsonPrimitive?.contentOrNull.orEmpty()
    }

    private fun chatUsage(frames: List<SseFrame>): JsonObject = chatJsonFrames(frames)
        .first { it.data.contains("\"usage\"") }
        .json()["usage"]!!.jsonObject

    private fun chatFinishReason(frames: List<SseFrame>): String? = chatJsonFrames(frames).firstNotNullOfOrNull {
        it.json()["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("finish_reason")?.jsonPrimitive?.contentOrNull
    }

    private fun chatToolArguments(frames: List<SseFrame>): String = chatJsonFrames(frames).joinToString("") {
        it.json()["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("delta")?.jsonObject
            ?.get("tool_calls")?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("function")?.jsonObject
            ?.get("arguments")?.jsonPrimitive?.contentOrNull.orEmpty()
    }

    private fun claudeText(frames: List<SseFrame>): String = frames.joinToString("") {
        it.json()["delta"]?.jsonObject
            ?.takeIf { delta -> delta["type"]?.jsonPrimitive?.contentOrNull == "text_delta" }
            ?.get("text")?.jsonPrimitive?.contentOrNull.orEmpty()
    }

    private fun claudeThinking(frames: List<SseFrame>): String = frames.joinToString("") {
        it.json()["delta"]?.jsonObject
            ?.takeIf { delta -> delta["type"]?.jsonPrimitive?.contentOrNull == "thinking_delta" }
            ?.get("thinking")?.jsonPrimitive?.contentOrNull.orEmpty()
    }

    private fun claudeToolInput(frames: List<SseFrame>): String = frames.joinToString("") {
        it.json()["delta"]?.jsonObject
            ?.takeIf { delta -> delta["type"]?.jsonPrimitive?.contentOrNull == "input_json_delta" }
            ?.get("partial_json")?.jsonPrimitive?.contentOrNull.orEmpty()
    }

    private fun claudeStopReason(frames: List<SseFrame>): String? =
        frames.mapNotNull { it.json()["delta"]?.jsonObject?.get("stop_reason")?.jsonPrimitive?.contentOrNull }
            .lastOrNull()

    private fun claudeInputTokens(frames: List<SseFrame>): Int? =
        frames.mapNotNull { it.json()["message"]?.jsonObject?.get("usage")?.jsonObject }
            .firstOrNull()?.get("input_tokens")?.jsonPrimitive?.int

    private fun responsesText(frames: List<SseFrame>): String = frames.joinToString("") {
        if (it.event != "response.output_text.delta") return@joinToString ""
        it.json()["delta"]!!.jsonPrimitive.content
    }

    private fun responsesToolArguments(frames: List<SseFrame>): String = frames.joinToString("") {
        if (it.event != "response.function_call_arguments.delta") return@joinToString ""
        it.json()["delta"]!!.jsonPrimitive.content
    }

    /** Strict positive assertion; the positive-control test proves it fails on a malformed trace. */
    private fun assertChatTextTrace(frames: List<SseFrame>) {
        assertEquals("[DONE]", frames.last().data)
        assertEquals(MockPayloads.TEXT, chatText(frames))
        assertEquals(MockPayloads.TOTAL_TOKENS, chatUsage(frames)["total_tokens"]!!.jsonPrimitive.int)
        assertEquals("stop", chatFinishReason(frames))
    }

    companion object {
        private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(10)
        private val HTTP: HttpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build()
    }
}
