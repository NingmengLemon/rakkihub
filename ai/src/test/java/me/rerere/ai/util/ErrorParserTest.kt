package me.rerere.ai.util

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
    fun `retry after parses seconds and rejects malformed values`() {
        assertEquals(3_000L, retryAfterMillis("3"))
        assertNull(retryAfterMillis("not a date"))
        assertNull(retryAfterMillis("0"))
        assertNull(retryAfterMillis(null))
    }
}
