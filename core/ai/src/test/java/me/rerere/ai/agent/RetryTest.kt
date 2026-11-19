package me.rerere.ai.agent

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import me.rerere.ai.util.HttpException
import me.rerere.ai.util.LlmFailure
import me.rerere.ai.util.LlmFailureCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class RetryTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `io failures are retried with doubling delays`() = runTest {
        var calls = 0
        val attempts = mutableListOf<RetryAttempt>()

        val result = withRetry(RetryPolicy.NETWORK.copy(jitterRatio = 0.0), onRetry = { attempts += it }) {
            calls++
            if (calls < 3) throw IOException("timeout")
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(listOf(1, 2), attempts.map { it.retry })
        assertEquals(listOf(1.seconds, 2.seconds), attempts.map { it.delay })
        assertEquals(3_000L, currentTime)
    }

    @Test
    fun `gives up after max retries`() = runTest {
        var calls = 0

        val error = runCatching {
            withRetry(RetryPolicy.NETWORK) {
                calls++
                throw IOException("offline")
            }
        }.exceptionOrNull()

        assertTrue(error is IOException)
        assertEquals(4, calls)
    }

    @Test
    fun `other errors are thrown without retrying`() = runTest {
        var calls = 0

        val error = runCatching {
            withRetry(RetryPolicy.NETWORK) {
                calls++
                throw IllegalStateException("bad request")
            }
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals(1, calls)
    }

    @Test
    fun `typed failures retry only recoverable codes`() = runTest {
        var rateLimitCalls = 0
        val result = withRetry(RetryPolicy.NETWORK.copy(jitterRatio = 0.0)) {
            rateLimitCalls++
            if (rateLimitCalls == 1) {
                throw HttpException("rate limited", LlmFailureCode.RATE_LIMIT, 429)
            }
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(2, rateLimitCalls)

        var authCalls = 0
        val authError = runCatching {
            withRetry(RetryPolicy.NETWORK) {
                authCalls++
                throw HttpException("unauthorized", LlmFailureCode.AUTH, 401)
            }
        }.exceptionOrNull()
        assertTrue(authError is HttpException)
        assertEquals(1, authCalls)

        assertTrue(RetryPolicy.NETWORK.shouldRetry(LlmFailure("empty", LlmFailureCode.EMPTY_RESPONSE)))
        assertFalse(RetryPolicy.NETWORK.shouldRetry(LlmFailure("overflow", LlmFailureCode.CONTEXT_WINDOW_EXCEEDED)))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `retry after is honored`() = runTest {
        val attempts = mutableListOf<RetryAttempt>()
        runCatching {
            withRetry(
                RetryPolicy(
                    maxRetries = 1,
                    initialDelay = 1.seconds,
                    maxDelay = 5.seconds,
                    jitterRatio = 0.0,
                ),
                onRetry = { attempts += it },
            ) {
                throw HttpException(
                    message = "rate limited",
                    code = LlmFailureCode.RATE_LIMIT,
                    statusCode = 429,
                    retryAfterMs = 2_500,
                )
            }
        }

        assertEquals(listOf(2.5.seconds), attempts.map { it.delay })
        assertEquals(2_500L, currentTime)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `retry after above max delay is capped`() = runTest {
        val attempts = mutableListOf<RetryAttempt>()
        runCatching {
            withRetry(
                RetryPolicy(
                    maxRetries = 1,
                    initialDelay = 1.seconds,
                    maxDelay = 1.seconds,
                    jitterRatio = 0.0,
                ),
                onRetry = { attempts += it },
            ) {
                throw HttpException(
                    message = "rate limited",
                    code = LlmFailureCode.RATE_LIMIT,
                    statusCode = 429,
                    retryAfterMs = 5_000,
                )
            }
        }

        assertEquals(listOf(1.seconds), attempts.map { it.delay })
        assertEquals(1_000L, currentTime)
    }

    @Test
    fun `quota is not retried`() {
        assertFalse(RetryPolicy.NETWORK.shouldRetry(
            HttpException("quota", LlmFailureCode.QUOTA, 429)
        ))
    }

    @Test
    fun `io failure caused by cancellation is not retried`() = runTest {
        var calls = 0

        val job = launch {
            withRetry(RetryPolicy(maxRetries = 3, initialDelay = Duration.ZERO)) {
                calls++
                // 停止生成时，底层连接会以 IOException("Canceled") 结束请求
                currentCoroutineContext().cancel()
                throw IOException("Canceled")
            }
        }
        job.join()

        assertTrue(job.isCancelled)
        assertEquals(1, calls)
    }
}
