package me.rerere.ai.util

import kotlinx.serialization.json.Json
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class ErrorParserTest {
    @Test
    fun `http status maps to retryable failure classes`() {
        assertEquals(LlmFailureCode.RATE_LIMIT, codeForStatus(429))
        assertEquals(LlmFailureCode.AUTH, codeForStatus(401))
        assertEquals(LlmFailureCode.QUOTA, codeForStatus(402))
        assertEquals(LlmFailureCode.SERVER, codeForStatus(503))
        assertEquals(LlmFailureCode.INVALID_REQUEST, codeForStatus(400, "bad request"))
        assertEquals(
            LlmFailureCode.CONTEXT_WINDOW_EXCEEDED,
            codeForStatus(400, "context length exceeded"),
        )
        assertEquals(LlmFailureCode.UNKNOWN, codeForStatus(418))
    }

    @Test
    fun `response keeps status retry after and message`() {
        val response = Response.Builder()
            .request(Request.Builder().url("https://example.com").build())
            .protocol(Protocol.HTTP_1_1)
            .code(429)
            .message("Too Many Requests")
            .header("Retry-After", "2")
            .body("rate limited".toResponseBody())
            .build()

        val error = response.toHttpException("rate limited")

        assertEquals(LlmFailureCode.RATE_LIMIT, error.code)
        assertEquals(429, error.statusCode)
        assertEquals(2_000L, error.retryAfterMs)
        assertEquals("HTTP 429: rate limited", error.message)
    }

    @Test
    fun `structured quota error wins over 429 rate limit`() {
        val body = """{"error":{"type":"insufficient_quota","code":"insufficient_quota","message":"You exceeded your current quota"}}"""
        val response = response(429, "Too Many Requests", body)

        val error = response.toHttpException(body)

        assertEquals(LlmFailureCode.QUOTA, error.code)
        assertEquals(429, error.statusCode)
        assertFalse(me.rerere.ai.agent.RetryPolicy.NETWORK.shouldRetry(error))
    }

    @Test
    fun `nested quota failure details are quota`() {
        val body = """{"error":{"status":"RESOURCE_EXHAUSTED","details":[{"@type":"type.googleapis.com/google.rpc.QuotaFailure"}]}}"""
        val error = response(429, "Too Many Requests", body).toHttpException(body)

        assertEquals(LlmFailureCode.QUOTA, error.code)
    }

    @Test
    fun `gemini resource exhausted 429 stays retryable`() {
        val body = """{"error":{"code":429,"message":"Resource has been exhausted (e.g. check quota).","status":"RESOURCE_EXHAUSTED"}}"""
        val error = response(429, "Too Many Requests", body).toHttpException(body)

        assertEquals(LlmFailureCode.RATE_LIMIT, error.code)
        assertTrue(me.rerere.ai.agent.RetryPolicy.NETWORK.shouldRetry(error))
    }

    @Test
    fun `context overflow is classified from provider message`() {
        assertEquals(
            LlmFailureCode.CONTEXT_WINDOW_EXCEEDED,
            codeForStatus(400, "prompt is too long: 200000 tokens > 128000 maximum"),
        )
        assertEquals(
            LlmFailureCode.CONTEXT_WINDOW_EXCEEDED,
            codeForStatus(400, "The input token count (461428) exceeds the maximum number of tokens allowed (131072)."),
        )
    }

    @Test
    fun `with response preserves structured code and parses http date`() {
        val at = ZonedDateTime.now().plusSeconds(30)
            .format(DateTimeFormatter.RFC_1123_DATE_TIME)
        val response = response(429, "Too Many Requests", "", retryAfter = at)
        val parsed = Json.parseToJsonElement(
            """{"error":{"type":"insufficient_quota","message":"You exceeded your current quota"}}"""
        ).parseErrorDetail().withResponse(response)

        assertEquals(LlmFailureCode.QUOTA, parsed.code)
        assertEquals(429, parsed.statusCode)
        assertTrue((parsed.retryAfterMs ?: 0) > 0)
    }

    @Test
    fun `non json body is classified by status only`() {
        val body = "<html><body>quota internal timeout</body></html>"
        val error = response(502, "Bad Gateway", body).toHttpException(body)

        assertEquals(LlmFailureCode.SERVER, error.code)
    }

    @Test
    fun `retry after parses seconds and rejects malformed values`() {
        assertEquals(3_000L, retryAfterMillis("3"))
        assertNull(retryAfterMillis("not a date"))
        assertNull(retryAfterMillis("0"))
        assertNull(retryAfterMillis(null))
    }

    private fun response(
        code: Int,
        message: String,
        body: String,
        retryAfter: String? = null,
    ): Response = Response.Builder()
        .request(Request.Builder().url("https://example.com").build())
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message(message)
        .apply { if (retryAfter != null) header("Retry-After", retryAfter) }
        .body(body.toResponseBody())
        .build()
}
