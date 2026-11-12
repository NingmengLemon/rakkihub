package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.rerere.rikkahub.R
import me.rerere.rikkahub.service.RecordedRetryAttempt
import me.rerere.rikkahub.service.retryRemainingSeconds

/**
 * 当前一轮的自动重试记录。只显示会话内存中的 attempt，不落盘；新一轮发送/压缩、审批恢复或
 * replace 时由 ChatService 清空。最新一条在等待重试时显示倒计时，成功后留下计划等待时间，
 * 失败时和错误卡片一起保留；重试记录在错误浮层上方，不会被错误卡片盖住。
 */
@Composable
internal fun RetryAttemptsCard(
    attempts: List<RecordedRetryAttempt>,
    isRetrying: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        val scrollState = rememberScrollState()
        LaunchedEffect(attempts.size) {
            if (attempts.isEmpty()) return@LaunchedEffect
            withFrameNanos { }
            if (scrollState.maxValue != Int.MAX_VALUE) {
                scrollState.scrollTo(scrollState.maxValue)
            }
        }
        Column(
            modifier = Modifier
                .heightIn(max = 240.dp)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.chat_retry_attempts_title),
                style = MaterialTheme.typography.titleSmall,
            )
            attempts.forEachIndexed { index, record ->
                RetryAttemptRow(
                    record = record,
                    isCurrent = index == attempts.lastIndex && isRetrying,
                )
            }
        }
    }
}

@Composable
private fun RetryAttemptRow(
    record: RecordedRetryAttempt,
    isCurrent: Boolean,
) {
    val reason = record.attempt.cause.message
        ?.take(160)
        ?.ifBlank { null }
        ?: record.attempt.cause::class.simpleName
        ?: stringResource(R.string.chat_retry_attempt_unknown)
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    R.string.chat_retry_attempt_row,
                    record.attempt.retry,
                    record.attempt.maxRetries,
                ),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = if (isCurrent) {
                RetryCountdown(record)
            } else {
                stringResource(
                    R.string.chat_retry_attempt_waited,
                    retryRemainingSeconds(0, record.attempt.delay.inWholeMilliseconds, 0),
                )
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RetryCountdown(record: RecordedRetryAttempt): String {
    val delayMillis = record.attempt.delay.inWholeMilliseconds
    var remaining by remember(record) {
        mutableLongStateOf(retryRemainingSeconds(record.recordedAtMillis, delayMillis))
    }
    LaunchedEffect(record) {
        while (true) {
            remaining = retryRemainingSeconds(record.recordedAtMillis, delayMillis)
            if (remaining <= 0) break
            delay(250)
        }
    }
    return if (remaining > 0) {
        stringResource(R.string.chat_retry_attempt_countdown, remaining)
    } else {
        stringResource(R.string.chat_retry_attempt_now)
    }
}
