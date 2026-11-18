package me.rerere.rikkahub.data.ai.mcp

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpToolRetryTest {
    @Test
    fun `unmarked tools are not retried`() = runTest {
        var calls = 0
        val result = withMcpToolRetry(
            tool = McpTool(name = "echo"),
            retryDelayMillis = 0,
            call = {
                calls++
                "error"
            },
            isErrorResult = { it == "error" },
        )

        assertEquals("error", result)
        assertEquals(1, calls)
    }

    @Test
    fun `retryable tools retry a request that was not sent`() = runTest {
        var calls = 0
        val result = withMcpToolRetry(
            tool = McpTool(name = "echo", retryable = true),
            retryDelayMillis = 0,
            call = {
                calls++
                if (calls == 1) throw McpClientUnavailableException("not connected")
                "ok"
            },
            isErrorResult = { false },
        )

        assertEquals("ok", result)
        assertEquals(2, calls)
    }

    @Test
    fun `retryable but not reentrant tools do not retry a sent failure`() = runTest {
        var calls = 0
        val failure = runCatching {
            withMcpToolRetry(
                tool = McpTool(name = "echo", retryable = true),
                retryDelayMillis = 0,
                call = {
                    calls++
                    throw IllegalStateException("transport failed")
                },
                isErrorResult = { false },
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(1, calls)
    }

    @Test
    fun `reentrant tools retry a sent failure`() = runTest {
        var calls = 0
        val result = withMcpToolRetry(
            tool = McpTool(name = "echo", retryable = true, reentrant = true),
            retryDelayMillis = 0,
            call = {
                calls++
                if (calls == 1) throw IllegalStateException("transport failed")
                "ok"
            },
            isErrorResult = { false },
        )

        assertEquals("ok", result)
        assertEquals(2, calls)
    }

    @Test
    fun `reentrant tools retry an error result`() = runTest {
        var calls = 0
        val result = withMcpToolRetry(
            tool = McpTool(name = "echo", retryable = true, reentrant = true),
            retryDelayMillis = 0,
            call = {
                calls++
                if (calls == 1) "error" else "ok"
            },
            isErrorResult = { it == "error" },
        )

        assertEquals("ok", result)
        assertEquals(2, calls)
    }

    @Test
    fun `retry stops after the attempt budget`() = runTest {
        var calls = 0
        val result = withMcpToolRetry(
            tool = McpTool(name = "echo", retryable = true, reentrant = true),
            retryDelayMillis = 0,
            call = {
                calls++
                "error"
            },
            isErrorResult = { it == "error" },
        )

        assertEquals("error", result)
        assertEquals(2, calls)
    }

    @Test
    fun `not-sent failures stop at the attempt budget`() = runTest {
        var calls = 0
        val failure = runCatching {
            withMcpToolRetry(
                tool = McpTool(name = "echo", retryable = true),
                retryDelayMillis = 0,
                call = {
                    calls++
                    throw McpClientUnavailableException("not connected")
                },
                isErrorResult = { false },
            )
        }.exceptionOrNull()

        assertTrue(failure is McpClientUnavailableException)
        assertEquals(2, calls)
    }

    @Test
    fun `sent failures stop at the attempt budget`() = runTest {
        var calls = 0
        val failure = runCatching {
            withMcpToolRetry(
                tool = McpTool(name = "echo", retryable = true, reentrant = true),
                retryDelayMillis = 0,
                call = {
                    calls++
                    throw IllegalStateException("transport failed")
                },
                isErrorResult = { false },
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(2, calls)
    }

    @Test
    fun `cancellation is not retried`() = runTest {
        var calls = 0
        val failure = runCatching {
            withMcpToolRetry(
                tool = McpTool(name = "echo", retryable = true, reentrant = true),
                retryDelayMillis = 0,
                call = {
                    calls++
                    throw kotlinx.coroutines.CancellationException("stopped")
                },
                isErrorResult = { false },
            )
        }.exceptionOrNull()

        assertTrue(failure is kotlinx.coroutines.CancellationException)
        assertEquals(1, calls)
    }

    @Test
    fun `retry decision requires retryable and reentrant for sent failures`() {
        val tool = McpTool(name = "echo", retryable = true)
        assertTrue(shouldRetryMcpTool(tool, McpToolFailure.NotSent))
        assertFalse(shouldRetryMcpTool(tool, McpToolFailure.Sent))
        assertFalse(shouldRetryMcpTool(null, McpToolFailure.NotSent))
        assertTrue(shouldRetryMcpTool(tool.copy(reentrant = true), McpToolFailure.Sent))
        assertFalse(shouldRetryMcpTool(McpTool(name = "echo", reentrant = true), McpToolFailure.Sent))
    }

    @Test
    fun `retry flags default to false and survive serialization`() {
        val defaults = Json.decodeFromString(
            McpTool.serializer(),
            """{"enable":true,"name":"echo"}""",
        )
        assertFalse(defaults.retryable)
        assertFalse(defaults.reentrant)

        val marked = Json.decodeFromString(
            McpTool.serializer(),
            """{"enable":true,"name":"echo","retryable":true,"reentrant":true}""",
        )
        assertTrue(marked.retryable)
        assertTrue(marked.reentrant)
        assertEquals(
            marked,
            Json.decodeFromString(McpTool.serializer(), Json.encodeToString(McpTool.serializer(), marked))
        )
    }
}
