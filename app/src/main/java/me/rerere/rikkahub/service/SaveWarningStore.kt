package me.rerere.rikkahub.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.uuid.Uuid

/**
 * 生成期间定期保存警告，按会话记录。
 *
 * 只保留最近一次失败原因；成功保存或用户关闭都会清除。不落盘，进程重启后重新判断。
 */
internal class SaveWarningStore {
    private val _warnings = MutableStateFlow<Map<Uuid, MessageSaveWarning>>(emptyMap())
    val warnings: StateFlow<Map<Uuid, MessageSaveWarning>> = _warnings.asStateFlow()

    fun report(conversationId: Uuid, warning: MessageSaveWarning) {
        _warnings.update { it + (conversationId to warning) }
    }

    fun clear(conversationId: Uuid) {
        _warnings.update { it - conversationId }
    }
}
