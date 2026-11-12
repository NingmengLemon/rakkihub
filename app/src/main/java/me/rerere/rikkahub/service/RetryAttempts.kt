package me.rerere.rikkahub.service

import me.rerere.ai.agent.RetryAttempt

/** 一次重试尝试及其进入等待的时间，用于界面上显示倒计时和已等待时间。 */
data class RecordedRetryAttempt(
    val attempt: RetryAttempt,
    val recordedAtMillis: Long = System.currentTimeMillis(),
)

/** 距离这次重试开始还剩几秒；向上取整，已经到点返回 0。 */
internal fun retryRemainingSeconds(
    recordedAtMillis: Long,
    delayMillis: Long,
    nowMillis: Long = System.currentTimeMillis(),
): Long = ((recordedAtMillis + delayMillis - nowMillis).coerceAtLeast(0) + 999) / 1_000
