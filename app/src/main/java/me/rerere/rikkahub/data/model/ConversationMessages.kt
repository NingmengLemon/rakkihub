package me.rerere.rikkahub.data.model

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.MessageStatus
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.finishPendingTools
import me.rerere.ai.ui.finishReasoning
import kotlin.uuid.Uuid

/**
 * 删除一条消息：节点里没有别的分支时整个节点一起删掉，否则选中的分支向前收拢。
 * 消息不存在时返回 null。
 */
internal fun Conversation.deleteMessage(messageId: Uuid): Conversation? {
    val targetNodeIndex = messageNodes.indexOfFirst { node -> node.messages.any { it.id == messageId } }
    if (targetNodeIndex == -1) return null

    val updatedNodes = messageNodes.mapIndexedNotNull { index, node ->
        if (index != targetNodeIndex) return@mapIndexedNotNull node
        val nextMessages = node.messages.filterNot { it.id == messageId }
        if (nextMessages.isEmpty()) return@mapIndexedNotNull null
        node.copy(messages = nextMessages, selectIndex = node.selectIndex.coerceAtMost(nextMessages.lastIndex))
    }
    return copy(messageNodes = updatedNodes)
}

/**
 * 删掉一个未完成的压缩对：带 [UIMessage.isCompressionPrompt] 标记的提示词节点，以及紧随其后的
 * assistant 回复节点。下一个节点不是 assistant 时只删提示词。提示词不存在时返回 null。
 */
internal fun Conversation.removeCompressionPair(promptMessageId: Uuid): Conversation? {
    val promptIndex = messageNodes.indexOfFirst { node ->
        node.messages.any { it.id == promptMessageId && it.isCompressionPrompt }
    }
    if (promptIndex == -1) return null
    val replyIndex = (promptIndex + 1).takeIf { index ->
        index < messageNodes.size && messageNodes[index].messages.any { it.role == MessageRole.ASSISTANT }
    }
    return copy(
        messageNodes = messageNodes.filterIndexed { index, _ ->
            index != promptIndex && index != replyIndex
        }
    )
}

/** 只清理崩溃留下的压缩对；失败/取消后保留的配对由状态标记保留，供用户重试或删除。 */
internal fun Conversation.removeCrashedCompressionPairs(): Conversation? {
    var conversation = this
    var result: Conversation? = null
    val promptIds = messageNodes.flatMap { node ->
        node.messages.filter { it.isCompressionPrompt }.map { it.id }
    }
    for (promptId in promptIds) {
        val promptIndex = conversation.messageNodes.indexOfFirst { node ->
            node.messages.any { it.id == promptId }
        }
        if (promptIndex == -1) continue
        val reply = conversation.messageNodes.getOrNull(promptIndex + 1)
            ?.messages
            ?.firstOrNull { it.role == MessageRole.ASSISTANT }
        if (reply != null && reply.status != MessageStatus.COMPLETE) continue
        conversation = conversation.removeCompressionPair(promptId) ?: continue
        result = conversation
    }
    return result
}

/** 清理所有未完成的压缩对；没有可清理的返回 null */
internal fun Conversation.removeCompressionPairs(): Conversation? {
    var result: Conversation? = null
    var conversation = this
    while (true) {
        val promptId = conversation.messageNodes
            .firstOrNull { node -> node.messages.any { it.isCompressionPrompt } }
            ?.messages
            ?.first { it.isCompressionPrompt }
            ?.id
            ?: break
        conversation = conversation.removeCompressionPair(promptId) ?: break
        result = conversation
    }
    return result
}

/**
 * 成功压缩：按锚点插检查点，再删掉提示词/回复对；锚点不存在返回 null。
 * 提示词已被删掉时仍返回插好检查点的结果，回复节点留给后续清理。
 */
internal fun Conversation.applyCompressionResult(
    anchorNodeId: Uuid,
    promptMessageId: Uuid,
    summary: String,
): Conversation? {
    val withCheckpoint = insertContextCheckpoint(afterNodeId = anchorNodeId, summary = summary) ?: return null
    return withCheckpoint.removeCompressionPair(promptMessageId) ?: withCheckpoint
}

/**
 * 用 [parts] 替换消息的内容，作为原节点的新分支；压缩检查点原地改写。消息不存在时原样返回。
 */
internal fun Conversation.editMessage(messageId: Uuid, parts: List<UIMessagePart>): Conversation {
    val node = getMessageNodeByMessageId(messageId) ?: return this
    val original = node.messages.first { it.id == messageId }
    val edited = if (original.isContextCheckpoint) {
        // 摘要原地改写：新建分支会丢掉检查点标记，删除摘要时还会露出旧版本。
        original.copy(parts = parts)
    } else {
        UIMessage(
            role = node.role,
            parts = parts,
            // 编辑的是原消息的内容，来源仍是生成它的模型
            modelId = original.modelId,
            modelSnapshot = original.modelSnapshot,
            // 失败压缩的提示词被编辑后仍要能被 scratch 清理认出来
            isCompressionPrompt = original.isCompressionPrompt,
        )
    }
    return upsertMessage(node.id, edited)
}

/** 设置消息的翻译，[translation] 为 null 时清除；消息不存在时原样返回 */
internal fun Conversation.withTranslation(messageId: Uuid, translation: String?): Conversation {
    val node = getMessageNodeByMessageId(messageId) ?: return this
    return upsertMessage(node.id, node.messages.first { it.id == messageId }.copy(translation = translation))
}

/** 切换节点选中的分支，调用方负责校验节点和下标 */
internal fun Conversation.selectBranch(nodeId: Uuid, selectIndex: Int): Conversation = copy(
    messageNodes = messageNodes.map { node ->
        if (node.id == nodeId) node.copy(selectIndex = selectIndex) else node
    }
)

internal fun Conversation.withNodeFavorite(nodeId: Uuid, favorite: Boolean): Conversation = copy(
    messageNodes = messageNodes.map { node ->
        if (node.id == nodeId) node.copy(isFavorite = favorite) else node
    }
)

/**
 * 记录用户对工具调用的审批结果。
 * 当前分支里没有这个还在等待审批的工具调用时返回 null，用来忽略重复点击和对已完成或非当前分支工具的过期审批。
 */
internal fun Conversation.withToolApproval(toolCallId: String, approvalState: ToolApprovalState): Conversation? {
    if (currentMessages.none { message ->
            message.getTools().any { it.toolCallId == toolCallId && it.isPending }
        }) {
        return null
    }
    return copy(
        messageNodes = messageNodes.map { node ->
            node.copy(messages = node.messages.map { message ->
                message.copy(parts = message.parts.map { part ->
                    if (part is UIMessagePart.Tool && part.toolCallId == toolCallId) {
                        part.copy(approvalState = approvalState)
                    } else {
                        part
                    }
                })
            })
        }
    )
}

/** 进程死亡后加载：扫描所有仍在生成的消息，补成 INTERRUPTED 并给未执行工具补恢复结果。 */
internal fun Conversation.recoverAbandonedGeneration(): Conversation? {
    var changed = false
    val nodes = messageNodes.map { node ->
        val messages = node.messages.map { message ->
            if (!message.isGenerating) {
                message
            } else {
                changed = true
                message
                    .finishPendingTools(::recoverInterruptedTool)
                    .copy(
                        status = if (message.status ==
                            MessageStatus.FAILED) {
                            MessageStatus.FAILED
                        } else {
                            MessageStatus.INTERRUPTED
                        },
                        isGenerating = false,
                    )
                    .finishReasoning()
            }
        }
        if (messages == node.messages) node else node.copy(messages = messages)
    }
    return if (changed) copy(messageNodes = nodes) else null
}

/**
 * 生成被停止时，最后一条消息里还没执行的工具补上恢复结果，否则 tool call 没有结果、之后无法发给模型。
 * 没有需要补的工具时返回 null。
 */
internal fun Conversation.cancelInterruptedTools(): Conversation? {
    val lastNode = messageNodes.lastOrNull() ?: return null
    // stop 后没有入口再执行这些工具，一律补恢复结果；startedAt 区分未开始和执行中
    val updatedMessage = lastNode.currentMessage.finishPendingTools(::recoverInterruptedTool)
    if (updatedMessage === lastNode.currentMessage) return null
    return upsertMessage(lastNode.id, updatedMessage)
}

/** 给被停止/无法继续的工具补一个模型能读的恢复结果，并保留是否真正开始过的区别。 */
internal fun recoverInterruptedTool(tool: UIMessagePart.Tool): UIMessagePart.Tool {
    val started = tool.startedAt != null
    val status = if (started) "outcome_unknown" else "not_started"
    val error = if (started) {
        "Generation stopped after the tool started; its outcome is unknown."
    } else {
        "Generation stopped before the tool started."
    }
    return tool.copy(
        output = listOf(
            UIMessagePart.Text("""{"status":"$status","error":"$error"}""")
        ),
        // 结果未知时不要把恢复时刻当结束时间，否则进程重启后 UI 会把停机时间算进工具耗时
        finishedAt = tool.finishedAt ?: tool.startedAt,
    )
}
