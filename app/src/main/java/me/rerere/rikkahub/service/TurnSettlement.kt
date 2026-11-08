package me.rerere.rikkahub.service

import kotlinx.coroutines.CancellationException
import me.rerere.ai.agent.AgentFinishReason
import me.rerere.ai.ui.MessageStatus

/**
 * 本轮回复的结算状态。
 *
 * 已经收到 [AgentFinishReason] 就保持完成，之后才发生的取消不覆盖它；没有完成原因时，
 * 取消算用户中断，其余失败算生成失败。进入 agent 循环之前的失败没有本轮回复可标记，
 * 这里不处理。
 */
internal fun resolveSettlementStatus(
    error: Throwable?,
    finishReason: AgentFinishReason?,
): MessageStatus = when {
    finishReason != null -> MessageStatus.COMPLETE
    error is CancellationException -> MessageStatus.INTERRUPTED
    else -> MessageStatus.FAILED
}
