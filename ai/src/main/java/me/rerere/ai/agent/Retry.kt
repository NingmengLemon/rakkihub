package me.rerere.ai.agent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import me.rerere.ai.util.LlmFailure
import me.rerere.ai.util.LlmFailureCode
import java.io.IOException
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * 请求模型失败时的重试策略：最多重试 [maxRetries] 次，等待时间从 [initialDelay] 开始每次翻倍，
 * 叠加 [jitterRatio] 抖动并限制在 [maxDelay] 内；服务端给出 `Retry-After` 时优先使用。
 *
 * 默认重试网络故障（[IOException]）和可恢复的 [LlmFailureCode]；鉴权、配额、请求非法和
 * context overflow 不在默认重试范围内。
 */
data class RetryPolicy(
    val maxRetries: Int,
    val initialDelay: Duration = 1.seconds,
    val maxDelay: Duration = 10.seconds,
    val jitterRatio: Double = 0.1,
    val retryableCodes: Set<LlmFailureCode> = DEFAULT_RETRYABLE_CODES,
    val retryOn: ((Throwable) -> Boolean)? = null,
) {
    fun shouldRetry(error: Throwable): Boolean {
        retryOn?.let { return it(error) }
        return error is IOException || (error is LlmFailure && error.code in retryableCodes)
    }

    companion object {
        private val DEFAULT_RETRYABLE_CODES = setOf(
            LlmFailureCode.RATE_LIMIT,
            LlmFailureCode.SERVER,
            LlmFailureCode.TIMEOUT,
            LlmFailureCode.TRANSPORT,
            LlmFailureCode.EMPTY_RESPONSE,
        )

        val NONE = RetryPolicy(maxRetries = 0)

        /** 网络自动重试：最多 3 次，基础间隔 1s、2s、4s，带 10% 抖动 */
        val NETWORK = RetryPolicy(maxRetries = 3)
    }
}

/** 第 [retry] 次重试（共 [maxRetries] 次）将在 [delay] 后开始，[cause] 是上一次失败的原因 */
data class RetryAttempt(
    val cause: Throwable,
    val retry: Int,
    val maxRetries: Int,
    val delay: Duration,
)

/**
 * 按 [policy] 执行 [block]：失败且允许重试时先调用 [onRetry]，等待后再执行一次。
 */
suspend fun <T> withRetry(
    policy: RetryPolicy,
    onRetry: suspend (RetryAttempt) -> Unit = {},
    block: suspend () -> T,
): T {
    var retries = 0
    while (true) {
        try {
            return block()
        } catch (error: Throwable) {
            // 用户主动停止生成时，底层连接也可能以 IOException("canceled") 收尾；
            // 先检查协程状态，确保取消不会被当作网络波动重新拉起。
            currentCoroutineContext().ensureActive()
            if (error is CancellationException || retries >= policy.maxRetries || !policy.shouldRetry(error)) {
                throw error
            }
            retries++
            val backoff = retryDelay(error, policy, retries)
            onRetry(RetryAttempt(error, retries, policy.maxRetries, backoff))
            delay(backoff)
        }
    }
}

private fun retryDelay(error: Throwable, policy: RetryPolicy, retry: Int): Duration {
    val serverDelay = (error as? LlmFailure)?.retryAfterMs?.takeIf { it > 0 }?.milliseconds
    if (serverDelay != null) return serverDelay.coerceAtMost(policy.maxDelay)

    val base = policy.initialDelay * (1 shl (retry - 1))
    val jitter = policy.jitterRatio.coerceIn(0.0, 1.0)
    val factor = if (jitter == 0.0) 1.0 else 1.0 + Random.nextDouble(-jitter, jitter)
    return (base * factor).coerceAtMost(policy.maxDelay).coerceAtLeast(Duration.ZERO)
}
