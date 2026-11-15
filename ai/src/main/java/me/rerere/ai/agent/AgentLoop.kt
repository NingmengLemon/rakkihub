package me.rerere.ai.agent

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.sum
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.MessageStatus
import me.rerere.ai.ui.MessageStep
import me.rerere.ai.ui.StepStatus
import me.rerere.ai.ui.StreamChunk
import me.rerere.ai.ui.StreamChunkHandler
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.finishReasoning
import me.rerere.ai.util.LlmFailure
import me.rerere.ai.util.LlmFailureCode
import me.rerere.ai.util.json
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

// 输出为空的工具会被当成没执行过，之后每一步都会再执行一次，所以换成占位文本
private const val EMPTY_TOOL_OUTPUT = "(no output)"
private const val TAG = "AgentLoop"

private class StreamChunkHandlingException(override val cause: Throwable) : RuntimeException(cause)

internal class AgentLoop(
    private val request: AgentRequest,
    private val hooks: AgentHooks,
    private val events: FlowCollector<AgentEvent>,
) {
    private val history: List<UIMessage>

    // 本轮的助手消息；新的一轮在第一次请求模型时才创建
    private var message: UIMessage?

    // 本步失败重试复用第一次尝试的 base，避免 preview 已经写出的消息 id 被换掉
    private var stepBase: UIMessage? = null

    // 最后一次 attempt 的内容；preview 只向收集器发送，失败收尾要靠它保留半截内容
    private var lastAttempt: UIMessage? = null

    init {
        val last = request.messages.lastOrNull()
        if (last?.role == MessageRole.ASSISTANT) {
            history = request.messages.dropLast(1)
            message = last
        } else {
            history = request.messages
            message = null
        }
    }

    suspend fun run() {
        var steps = 0
        while (true) {
            val current = message
            if (current != null && current.getTools().any { !it.isExecuted }) {
                val pending = requestApprovals(current)
                if (pending.isNotEmpty()) return finish(AgentFinishReason.AwaitingApproval(pending))
                executeTools()
                settleExecutedStep()
            } else if (current != null && steps > 0) {
                return finish(AgentFinishReason.Completed)
            }
            if (steps == request.maxSteps) return finish(AgentFinishReason.StepLimitReached)
            generateStep()
            steps++
        }
    }

    // 新的工具调用按定义判断是否需要审批，需要的标为 Pending；返回仍在等待审批的调用
    private suspend fun requestApprovals(current: UIMessage): Set<String> {
        val updated = current.copy(
            parts = current.parts.map { part ->
                if (part is UIMessagePart.Tool && !part.isExecuted &&
                    part.approvalState == ToolApprovalState.Auto &&
                    findTool(part.toolName)?.needsApproval?.invoke(part.inputAsJson()) == true
                ) {
                    part.copy(approvalState = ToolApprovalState.Pending)
                } else {
                    part
                }
            }
        )
        if (updated != current) update(updated)
        return updated.getTools().filter { it.isPending }.mapTo(LinkedHashSet()) { it.toolCallId }
    }

    private suspend fun generateStep() {
        val current = message
        val step = hooks.prepareStep(if (current == null) history else history + current)
        val params = step.params.copy(tools = request.tools)
        val base = (current ?: stepBase ?: UIMessage(
            role = MessageRole.ASSISTANT,
            parts = emptyList(),
            modelId = params.model.id,
        ).also { stepBase = it }).startStep()
        if (current == null) stepBase = base
        lastAttempt = null
        try {
            val generated = if (request.stream) {
                stream(step.messages, params, base)
            } else {
                generate(step.messages, params, base)
            }
            val finished = hooks.finishStep(generated, newPartsFrom = base.parts.size)
            val withStep = finished.finishCurrentStep(
                if (finishedTools(finished)) StepStatus.TOOLS else StepStatus.SUCCEEDED,
            )
            update(withStep.copy(finishedAt = now()))
            lastAttempt = null
            stepBase = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // 失败 attempt 不进模型历史，但要留下 step 终态供请求投影和 UI 使用。
            // 事件收集器本身失败时不再二次 emit，保留原始异常。
            val failed = (lastAttempt ?: message?.takeIf {
                it.steps.lastOrNull()?.status == StepStatus.RUNNING
            } ?: base)
                .finishCurrentStep(StepStatus.FAILED)
            // 新消息在收到任何内容前就失败时不落空回复，只让错误卡片可见
            if (failed.parts.isNotEmpty()) {
                try {
                    update(failed.copy(finishedAt = now()))
                } catch (_: Throwable) {
                    // 原始异常优先
                }
            }
            throw e
        }
    }

    private fun finishedTools(message: UIMessage): Boolean =
        message.getTools().any { !it.isExecuted }

    @OptIn(FlowPreview::class)
    private suspend fun stream(
        messages: List<UIMessage>,
        params: TextGenerationParams,
        base: UIMessage,
    ): UIMessage {
        // 多轮工具调用共用同一条助手消息，消息上已有的 usage 和耗时来自之前的步骤。
        // 本步从清空的统计开始（merge 只适合同一次请求内的快照），再与之前的步骤累加。
        val stepStart = base.copy(usage = null, generationTimeMs = null)
        return try {
            withRetry(request.retry, onRetry = { events.emit(AgentEvent.Retrying(it)) }) {
                // 每次重试都从本步开始时的消息重新合并，不会把重试的响应接在已经展示的半截回复后面；
                // 各次尝试共用同一个消息 id，调用方直接覆盖即可。
                val handler = StreamChunkHandler(params.model)
                var attempt = stepStart
                lastAttempt = null
                var firstChunk: TimeMark? = null
                val chunks = request.provider.streamText(request.providerSetting, messages, params)
                val timedChunks = request.streamIdleTimeout?.let { timeout ->
                    chunks.timeout(timeout)
                } ?: chunks
                try {
                    timedChunks.collect { chunk ->
                        // 心跳只用来重置空闲看门狗，不改变消息，也不触发 preview
                        if (chunk is StreamChunk.Heartbeat) return@collect
                        try {
                            val mark = firstChunk ?: TimeSource.Monotonic.markNow().also { firstChunk = it }
                            attempt = handler.handle(listOf(attempt), chunk).single()
                            lastAttempt = attempt
                            preview(attempt.withStepStats(base, mark.elapsedNow()), newPartsFrom = base.parts.size)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            // 合并失败或下游处理失败都不是网络故障，不能重放模型请求
                            throw StreamChunkHandlingException(e)
                        }
                    }
                } catch (e: TimeoutCancellationException) {
                    // 用户停止和空闲超时都是 CancellationException；先确认协程仍然活着
                    currentCoroutineContext().ensureActive()
                    throw LlmFailure("Model stream idle timeout", LlmFailureCode.TIMEOUT)
                }
                val generated = attempt.withStepStats(base, firstChunk?.elapsedNow() ?: Duration.ZERO)
                if (!generated.hasGeneratedContent(from = base.parts.size)) {
                    throw LlmFailure("Model returned an empty response", LlmFailureCode.EMPTY_RESPONSE)
                }
                generated
            }
        } catch (e: StreamChunkHandlingException) {
            throw e.cause
        }
    }

    private suspend fun generate(
        messages: List<UIMessage>,
        params: TextGenerationParams,
        base: UIMessage,
    ): UIMessage {
        // 非流式拿不到首 token 时间，只能统计最后一次尝试的整个请求耗时
        var requestMark = TimeSource.Monotonic.markNow()
        val result = withRetry(request.retry, onRetry = { events.emit(AgentEvent.Retrying(it)) }) {
            requestMark = TimeSource.Monotonic.markNow()
            val result = request.provider.generateText(request.providerSetting, messages, params)
            if (!result.message.hasGeneratedContent()) {
                throw LlmFailure("Model returned an empty response", LlmFailureCode.EMPTY_RESPONSE)
            }
            result
        }
        val requestTime = requestMark.elapsedNow()
        // 和流式一样只追加 part，不并进之前步骤的文本或推理里，否则 newPartsFrom 之后就不全是这一步的内容了。
        // 一次请求只有一份 usage；没有返回时保持为空，累加时沿用之前步骤的统计
        val generated = base.copy(
            parts = base.parts + result.message.parts,
            annotations = result.message.annotations.ifEmpty { base.annotations },
            modelId = params.model.id,
            usage = result.usage,
        ).finishReasoning().withStepStats(base, requestTime)
        lastAttempt = generated
        preview(generated, newPartsFrom = base.parts.size)
        return generated
    }

    // 按调用顺序逐个执行，每执行完一个就更新一次，中途取消时已经执行的结果不会丢
    private suspend fun executeTools() {
        val tools = checkNotNull(message).getTools().filter { !it.isExecuted }
        for (tool in tools) {
            // 只给真正运行的工具计时；开始时先写一次，界面才能在执行中实时显示耗时
            val runs = tool.approvalState == ToolApprovalState.Auto || tool.approvalState == ToolApprovalState.Approved
            if (runs) updateTool(tool.toolCallId) { it.copy(startedAt = Clock.System.now(), finishedAt = null) }
            val output = execute(tool)
            updateTool(tool.toolCallId) {
                it.copy(output = output, finishedAt = if (runs) Clock.System.now() else it.finishedAt)
            }
        }
    }

    private suspend fun updateTool(toolCallId: String, transform: (UIMessagePart.Tool) -> UIMessagePart.Tool) {
        val current = checkNotNull(message)
        update(
            current.copy(
                parts = current.parts.map { part ->
                    if (part is UIMessagePart.Tool && part.toolCallId == toolCallId) transform(part) else part
                }
            )
        )
    }

    private suspend fun execute(tool: UIMessagePart.Tool): List<UIMessagePart> =
        when (val state = tool.approvalState) {
            is ToolApprovalState.Denied -> errorOutput(
                "Tool execution denied by user. Reason: ${state.reason.ifBlank { "No reason provided" }}"
            )

            is ToolApprovalState.Answered -> listOf(UIMessagePart.Text(state.answer))
            ToolApprovalState.Auto, ToolApprovalState.Approved -> runTool(tool)
            ToolApprovalState.Pending -> error("Tool ${tool.toolCallId} is still waiting for approval")
        }

    private suspend fun runTool(tool: UIMessagePart.Tool): List<UIMessagePart> = try {
        val definition = findTool(tool.toolName) ?: error("Tool ${tool.toolName} not found")
        val args = runCatching { json.parseToJsonElement(tool.input.ifBlank { "{}" }) }
            .getOrElse { error("Invalid tool arguments JSON for ${tool.toolName}: ${it.message}") }
        val output = executeToolWithTimeout(tool.toolName, definition.timeoutMillis) {
            definition.execute(args)
        }
        output.ifEmpty { listOf(UIMessagePart.Text(EMPTY_TOOL_OUTPUT)) }
    } catch (e: CancellationException) {
        // 取消必须向上传播，否则停止生成会被误报为工具执行错误
        throw e
    } catch (e: Throwable) {
        // 堆栈只留日志，不再塞进工具结果，避免把内部路径和实现细节发给模型
        Log.e(TAG, "Tool ${tool.toolName} failed", e)
        errorOutput("Tool execution failed: ${e.message ?: e.javaClass.simpleName}")
    }

    private suspend fun executeToolWithTimeout(
        toolName: String,
        declaredTimeoutMillis: Long?,
        execute: suspend () -> List<UIMessagePart>,
    ): List<UIMessagePart> {
        suspend fun call(): List<UIMessagePart> = try {
            execute()
        } catch (e: TimeoutCancellationException) {
            // 工具内部自己的 timeout 不应取消整轮；先确认不是外部停止/agent 超时
            currentCoroutineContext().ensureActive()
            errorOutput("Tool $toolName timed out internally")
        }

        // 外层超时不能先于工具自身声明的超时触发，两者都在时取较大值
        val timeout = effectiveToolTimeout(request.toolTimeout, declaredTimeoutMillis) ?: return call()
        return withTimeoutOrNull(timeout) { call() }
            ?: errorOutput("Tool execution timed out after ${timeout.inWholeSeconds}s")
    }

    private fun findTool(name: String) = request.tools.find { it.name == name }

    private suspend fun settleExecutedStep() {
        val current = message ?: return
        if (current.steps.isEmpty()) return
        val steps = current.steps.toMutableList()
        val last = steps.last()
        if (last.status != StepStatus.RUNNING && last.status != StepStatus.TOOLS) return
        steps[steps.lastIndex] = last.copy(
            status = StepStatus.SUCCEEDED,
            endPartIndex = current.parts.size,
            finishedAt = Clock.System.now(),
        )
        update(current.copy(steps = steps))
    }

    private suspend fun update(updated: UIMessage) {
        message = updated
        events.emit(AgentEvent.MessageUpdated(updated))
    }

    // 生成中途的内容只发给调用方，这一步结束前循环内部一直基于原始内容合并
    private suspend fun preview(generating: UIMessage, newPartsFrom: Int) {
        events.emit(AgentEvent.MessageUpdated(hooks.previewStep(generating, newPartsFrom)))
    }

    private suspend fun finish(reason: AgentFinishReason) {
        events.emit(AgentEvent.Finished(checkNotNull(message), reason))
    }
}

private fun UIMessage.hasGeneratedContent(from: Int = 0): Boolean = parts.drop(from).any { part ->
    when (part) {
        is UIMessagePart.Text -> part.text.isNotBlank()
        is UIMessagePart.Reasoning -> part.reasoning.isNotBlank()
        else -> true
    }
}

private fun UIMessage.startStep(): UIMessage {
    val prefix = if (steps.isEmpty() && parts.isNotEmpty()) {
        listOf(
            MessageStep(
                status = when (status) {
                    MessageStatus.COMPLETE -> StepStatus.SUCCEEDED
                    MessageStatus.INTERRUPTED -> StepStatus.CANCELLED
                    MessageStatus.FAILED -> StepStatus.FAILED
                },
                startPartIndex = 0,
                endPartIndex = parts.size,
                startedAt = Clock.System.now(),
                finishedAt = Clock.System.now(),
                legacy = true,
            )
        )
    } else {
        emptyList()
    }
    val start = parts.size
    return copy(
        steps = prefix + steps + MessageStep(
            status = StepStatus.RUNNING,
            startPartIndex = start,
            endPartIndex = start,
            startedAt = Clock.System.now(),
        )
    )
}

private fun UIMessage.finishCurrentStep(status: StepStatus): UIMessage {
    if (steps.isEmpty()) return this
    val updated = steps.toMutableList()
    val last = updated.last()
    if (last.status != StepStatus.RUNNING && last.status != StepStatus.TOOLS) return this
    updated[updated.lastIndex] = last.copy(
        status = status,
        endPartIndex = maxOf(last.startPartIndex, parts.size),
        finishedAt = Clock.System.now(),
    )
    return copy(steps = updated)
}

private fun errorOutput(error: String): List<UIMessagePart> =
    listOf(UIMessagePart.Text(buildJsonObject { put("error", error) }.toString()))

/** 工具声明超时之外再留的余量，保证同样的时长下先由工具自己的计时触发。 */
private val DECLARED_TIMEOUT_GRACE = 5.seconds

/** 外层工具超时与工具自身声明的超时取较大值；外层为空表示不限制。 */
internal fun effectiveToolTimeout(outer: Duration?, declaredMillis: Long?): Duration? =
    outer?.let { outerTimeout ->
        val declared = declaredMillis?.milliseconds
        if (declared == null) outerTimeout else maxOf(outerTimeout, declared + DECLARED_TIMEOUT_GRACE)
    }

// 把本步的 usage 和生成耗时累加到之前步骤的统计上；接收者只带本步的 usage
private fun UIMessage.withStepStats(stepBase: UIMessage, stepTime: Duration): UIMessage = copy(
    usage = stepBase.usage.sum(usage),
    generationTimeMs = (stepBase.generationTimeMs ?: 0L) + stepTime.inWholeMilliseconds,
    // 上下文占用只看最后一次请求，usage 是多步累计值；本步没返回 usage 时沿用之前步骤的
    lastUsage = usage ?: stepBase.lastUsage,
    stepCount = (stepBase.stepCount ?: 0) + 1,
)

private fun now() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
