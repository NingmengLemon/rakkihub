package me.rerere.rikkahub.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.isEmptyInputMessage
import kotlin.uuid.Uuid

@Serializable
data class QueuedMessage(
    val id: Uuid = Uuid.random(),
    val parts: List<UIMessagePart>,
    val answer: Boolean = true,
    // 编辑占位只在当前进程里的队列面板上有意义，不随队列落盘
    @Transient val isEditing: Boolean = false,
)

data class MessageQueueState(
    val messages: List<QueuedMessage> = emptyList(),
    val paused: Boolean = false,
)

/** Pending input is kept outside conversation history until dispatched. */
class MessageQueue {
    private val mutableState = MutableStateFlow(MessageQueueState())
    val state = mutableState.asStateFlow()

    @Synchronized
    fun enqueue(parts: List<UIMessagePart>, answer: Boolean = true) {
        if (parts.isEmptyInputMessage()) return
        mutableState.value = state.value.copy(
            messages = state.value.messages + QueuedMessage(
                parts = parts.toList(),
                answer = answer,
            ),
        )
    }

    @Synchronized
    fun takeNext(): QueuedMessage? {
        val current = state.value
        if (current.paused) return null
        val next = current.messages.firstOrNull()?.takeUnless { it.isEditing } ?: return null
        mutableState.value = current.copy(messages = current.messages.drop(1))
        return next
    }

    /** 发送失败时把已出队的消息放回队首；同 id 已在队列时不重复插入。 */
    @Synchronized
    fun requeueFront(message: QueuedMessage) {
        if (state.value.messages.any { it.id == message.id }) return
        mutableState.value = state.value.copy(messages = listOf(message) + state.value.messages)
    }

    @Synchronized
    fun remove(id: Uuid): QueuedMessage? {
        val removed = state.value.messages.find { it.id == id } ?: return null
        mutableState.value =
            state.value.copy(messages = state.value.messages.filterNot { it.id == id })
        return removed
    }

    @Synchronized
    fun beginEdit(id: Uuid): QueuedMessage? {
        val message = state.value.messages.find { it.id == id && !it.isEditing } ?: return null
        mutableState.value = state.value.copy(
            messages = state.value.messages.map { if (it.id == id) it.copy(isEditing = true) else it },
        )
        return message
    }

    @Synchronized
    fun finishEdit(id: Uuid, parts: List<UIMessagePart>? = null): QueuedMessage? {
        if (parts != null && parts.isEmptyInputMessage()) return null
        val previous = state.value.messages.find { it.id == id } ?: return null
        mutableState.value = state.value.copy(
            messages = state.value.messages.map {
                if (it.id == id) {
                    it.copy(
                        parts = parts?.toList() ?: it.parts,
                        isEditing = false
                    )
                } else {
                    it
                }
            },
        )
        // 取消编辑只释放占位，不能清理原附件。
        return previous.takeIf { parts != null }
    }

    @Synchronized
    fun pause() {
        mutableState.value = state.value.copy(paused = true)
    }

    @Synchronized
    fun resume() {
        mutableState.value = state.value.copy(paused = false)
    }

    /**
     * 进程重启后把落盘的队列放回来。恢复的队列一律暂停，等用户看到队列面板后自己恢复，
     * 不在打开会话时自动发出去；编辑占位不跨进程。初始化期间用户可能已经又入队了消息，
     * 这里按 id 合并而不是整份替换，不能把新消息盖掉。
     */
    @Synchronized
    fun restore(messages: List<QueuedMessage>) {
        val merged = (messages + state.value.messages).distinctBy { it.id }
        mutableState.value = MessageQueueState(
            messages = merged.map { it.copy(isEditing = false) },
            paused = true,
        )
    }
}
