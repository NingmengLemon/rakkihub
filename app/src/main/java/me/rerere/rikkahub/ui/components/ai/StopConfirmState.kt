package me.rerere.rikkahub.ui.components.ai

import kotlinx.coroutines.Job

/**
 * 停止生成确认框的状态。targetJob 记录打开确认时正在跑的那一轮，
 * 队列在确认期间换到下一轮时据此关闭弹窗，避免确认落到新的生成上。
 */
data class StopConfirmState(
    val visible: Boolean = false,
    val targetJob: Job? = null,
) {
    fun open(job: Job?): StopConfirmState = StopConfirmState(visible = true, targetJob = job)

    fun close(): StopConfirmState = StopConfirmState()

    fun matches(job: Job?): Boolean = targetJob === job
}

/** 点停止后的动作：已经在停同一轮时忽略，需要弹确认时带出目标轮次，否则直接停 */
sealed interface StopAction {
    data object Ignored : StopAction

    data object StopNow : StopAction

    data class Confirm(val state: StopConfirmState) : StopAction
}

fun resolveStopAction(
    confirmEnabled: Boolean,
    requestedJob: Job?,
    currentJob: Job?,
): StopAction = when {
    requestedJob === currentJob && currentJob != null -> StopAction.Ignored
    confirmEnabled -> StopAction.Confirm(StopConfirmState().open(currentJob))
    else -> StopAction.StopNow
}
