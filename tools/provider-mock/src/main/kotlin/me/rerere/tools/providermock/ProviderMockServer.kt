package me.rerere.tools.providermock

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.queryString
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * JVM mock HTTP server for the four streaming protocols the app speaks.
 *
 * The server binds `127.0.0.1` on an ephemeral port (port `0`) and starts in the constructor, so a
 * test cannot collide with another test or with a developer's local services. [close] stops it.
 *
 * Scenario selection is documented on [MockScenario]: use the model id (`mock-tool`) or the
 * `X-Mock-Scenario` request header.
 */
class ProviderMockServer(
    private val host: String = DEFAULT_HOST,
    port: Int = 0,
    val slowChunkDelayMillis: Long = DEFAULT_SLOW_DELAY_MILLIS,
) : AutoCloseable {
    val recorder = RequestRecorder()

    private val engine: ApplicationEngine

    /** Resolved port; equals the requested one when a fixed port was passed. */
    val port: Int

    init {
        val server = embeddedServer(CIO, port = port, host = host) { installRoutes() }
        server.start(wait = false)
        engine = server.engine
        this.port = runBlocking { engine.resolvedConnectors().first().port }
    }

    val baseUrl: String get() = "http://$host:$port"

    /** Base URL for OpenAI Chat Completions and Responses (`/v1/chat/completions`, `/v1/responses`). */
    val openAiBaseUrl: String get() = "$baseUrl/v1"

    /** Base URL for OpenAI Responses, same `/v1` mount as Chat Completions. */
    val responsesBaseUrl: String get() = "$baseUrl/v1"

    /** Base URL for Anthropic Messages (`/v1/messages`). */
    val claudeBaseUrl: String get() = "$baseUrl/v1"

    /** Base URL for Gemini (`/v1beta/models/{model}:streamGenerateContent`). */
    val googleBaseUrl: String get() = "$baseUrl/v1beta"

    override fun close() {
        engine.stop(gracePeriodMillis = 0, timeoutMillis = STOP_TIMEOUT_MILLIS)
    }

    private fun Application.installRoutes() {
        routing {
            get("/v1/models") {
                record(call, body = "")
                call.respondText(MockPayloads.modelsJson(), ContentType.Application.Json)
            }

            get("/v1beta/models") {
                record(call, body = "")
                call.respondText(MockPayloads.googleModelsJson(), ContentType.Application.Json)
            }

            post("/v1/chat/completions") {
                val rawBody = call.receiveText()
                record(call, rawBody)
                val request = MockRequest.parse(rawBody)
                val scenario = resolveScenario(call, request.model)
                if (scenario.isFailure) {
                    call.respondFailure(scenario, MockProtocol.OPENAI)
                    return@post
                }
                if (request.stream) {
                    call.respondEventStream(scenario, MockPayloads.chatCompletionsFrames(scenario, request.model))
                } else {
                    call.respondText(MockPayloads.chatCompletionsJson(request.model), ContentType.Application.Json)
                }
            }

            post("/v1/responses") {
                val rawBody = call.receiveText()
                record(call, rawBody)
                val request = MockRequest.parse(rawBody)
                val scenario = resolveScenario(call, request.model)
                if (scenario.isFailure) {
                    call.respondFailure(scenario, MockProtocol.OPENAI)
                    return@post
                }
                if (request.stream) {
                    call.respondEventStream(scenario, MockPayloads.responsesFrames(scenario, request.model))
                } else {
                    call.respondText(MockPayloads.responsesJson(request.model), ContentType.Application.Json)
                }
            }

            post("/v1/messages") {
                val rawBody = call.receiveText()
                record(call, rawBody)
                val request = MockRequest.parse(rawBody)
                val scenario = resolveScenario(call, request.model)
                if (scenario.isFailure) {
                    call.respondFailure(scenario, MockProtocol.CLAUDE)
                    return@post
                }
                if (request.stream) {
                    call.respondEventStream(scenario, MockPayloads.claudeFrames(scenario, request.model))
                } else {
                    call.respondText(MockPayloads.claudeJson(request.model), ContentType.Application.Json)
                }
            }

            post("/v1beta/models/{modelAction}") {
                val rawBody = call.receiveText()
                record(call, rawBody)
                val modelAction = call.parameters["modelAction"].orEmpty()
                val model = modelAction.substringBeforeLast(':', missingDelimiterValue = modelAction)
                val action = modelAction.substringAfterLast(':', missingDelimiterValue = "")
                val scenario = resolveScenario(call, model)
                if (scenario.isFailure) {
                    call.respondFailure(scenario, MockProtocol.GOOGLE)
                    return@post
                }
                when (action) {
                    "streamGenerateContent" ->
                        call.respondEventStream(scenario, MockPayloads.geminiFrames(scenario, model))

                    "generateContent" ->
                        call.respondText(MockPayloads.geminiJson(model), ContentType.Application.Json)

                    else -> call.respondText(
                        "unsupported model action: $modelAction",
                        ContentType.Text.Plain,
                        HttpStatusCode.NotFound,
                    )
                }
            }
        }
    }

    private fun record(call: ApplicationCall, body: String) {
        recorder.record(
            method = call.request.httpMethod.value,
            path = call.request.path(),
            query = call.request.queryString(),
            headers = call.request.headers.entries().associate { entry ->
                entry.key to entry.value.map { value -> RequestRecorder.redact(entry.key, value) }
            },
            rawBody = body,
        )
    }

    private fun resolveScenario(call: ApplicationCall, model: String?): MockScenario =
        MockScenario.resolve(call.request.headers[MockScenario.HEADER], model)

    private suspend fun ApplicationCall.respondFailure(scenario: MockScenario, protocol: MockProtocol) {
        when (scenario) {
            MockScenario.RATE_LIMIT -> {
                response.header(HttpHeaders.RetryAfter, RETRY_AFTER_SECONDS)
                respondText(
                    text = MockPayloads.errorBody(protocol, HttpStatusCode.TooManyRequests.value),
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.TooManyRequests,
                )
            }

            MockScenario.SERVER_ERROR -> respondText(
                text = MockPayloads.SERVER_ERROR_HTML,
                contentType = ContentType.Text.Html,
                status = HttpStatusCode.InternalServerError,
            )

            else -> error("not a failure scenario: $scenario")
        }
    }

    private suspend fun ApplicationCall.respondEventStream(scenario: MockScenario, frames: List<SseFrame>) {
        val delayMillis = when (scenario) {
            MockScenario.SLOW -> request.headers[MockScenario.DELAY_HEADER]
                ?.toLongOrNull()
                ?.coerceIn(0L, MAX_DELAY_MILLIS)
                ?: slowChunkDelayMillis

            else -> 0L
        }
        response.header(HttpHeaders.CacheControl, "no-cache")
        response.header("X-Accel-Buffering", "no")
        respondBytesWriter(
            contentType = ContentType.Text.EventStream.withCharset(Charsets.UTF_8),
            status = HttpStatusCode.OK,
        ) {
            frames.forEach { frame ->
                val bytes = frame.encode().toByteArray(Charsets.UTF_8)
                    .let { if (scenario == MockScenario.UTF8_BROKEN) dropContinuationByte(it) else it }
                if (scenario == MockScenario.UTF8_SPLIT) {
                    writeUtf8Split(bytes)
                } else {
                    writeFully(bytes)
                    flush()
                }
                if (delayMillis > 0) delay(delayMillis)
            }
        }
    }

    private suspend fun ByteWriteChannel.writeUtf8Split(bytes: ByteArray) {
        splitForUtf8Boundary(bytes).forEach { chunk ->
            writeFully(chunk)
            flush()
            delay(UTF8_SPLIT_DELAY_MILLIS)
        }
    }

    /** Splits the stream into fixed-size chunks so multi-byte characters cross write boundaries. */
    internal fun splitForUtf8Boundary(bytes: ByteArray): List<ByteArray> =
        (0 until bytes.size step UTF8_SPLIT_CHUNK_BYTES).map { offset ->
            bytes.copyOfRange(offset, minOf(offset + UTF8_SPLIT_CHUNK_BYTES, bytes.size))
        }

    /** Drops the continuation byte of the first 3-byte character, producing invalid UTF-8. */
    private fun dropContinuationByte(bytes: ByteArray): ByteArray {
        val lead = bytes.indexOfFirst { (it.toInt() and 0xFF) in 0xE0..0xEF }
        if (lead < 0 || lead + 1 >= bytes.size) return bytes
        val continuation = bytes[lead + 1].toInt() and 0xFF
        if (continuation !in 0x80..0xBF) return bytes
        return ByteArray(bytes.size - 1) { index ->
            if (index <= lead) bytes[index] else bytes[index + 1]
        }
    }

    private data class MockRequest(val model: String, val stream: Boolean) {
        companion object {
            fun parse(rawBody: String): MockRequest {
                val body = runCatching { Json.parseToJsonElement(rawBody) as? JsonObject }.getOrNull()
                val model = (body?.get("model") as? JsonPrimitive)?.contentOrNull.orEmpty()
                val stream = (body?.get("stream") as? JsonPrimitive)?.booleanOrNull == true
                return MockRequest(model = model, stream = stream)
            }
        }
    }

    companion object {
        const val DEFAULT_HOST = "127.0.0.1"
        const val DEFAULT_SLOW_DELAY_MILLIS = 200L
        private const val RETRY_AFTER_SECONDS = "2"
        private const val MAX_DELAY_MILLIS = 10_000L
        private const val UTF8_SPLIT_CHUNK_BYTES = 7
        private const val UTF8_SPLIT_DELAY_MILLIS = 1L
        private const val STOP_TIMEOUT_MILLIS = 1_000L
    }
}
