package me.rerere.rikkahub.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlin.uuid.Uuid

/**
 * 错误卡片的落盘存储：一个文件保存所有会话的错误。
 *
 * 错误卡片默认保留到用户手动关闭，但只存在内存里，进程死亡就没了。这里把卡片恢复出来，
 * 让用户重开应用后还能看到上次失败的原因。文件不进备份，放在 `noBackupFilesDir`。
 *
 * `Throwable` 本身不能可靠序列化，只保存卡片 id、标题、消息、会话 id、时间、解决方案
 * 枚举名、是否可重试与重试目标；恢复时用消息重建一个普通 `Throwable`。卡片文本是给人看的，
 * 恢复后的异常类型不参与重试或流程判断。
 */
class ChatErrorStore(
    private val file: File,
    private val json: Json,
) {
    @Serializable
    private data class StoredError(
        val id: Uuid,
        val title: String? = null,
        val errorMessage: String,
        val conversationId: Uuid,
        val timestamp: Long,
        val solution: String? = null,
        val retryable: Boolean = false,
        val retryMessageId: Uuid? = null,
    )

    suspend fun load(): List<ChatError> = withContext(Dispatchers.IO) {
        if (!file.isFile) return@withContext emptyList()
        runCatching {
            json.decodeFromString<List<StoredError>>(file.readText()).map { stored ->
                ChatError(
                    id = stored.id,
                    title = stored.title,
                    error = Throwable(stored.errorMessage),
                    conversationId = stored.conversationId,
                    timestamp = stored.timestamp,
                    solution = stored.solution?.let { name ->
                        runCatching { ChatErrorSolution.valueOf(name) }.getOrNull()
                    },
                    retryable = stored.retryable,
                    retryMessageId = stored.retryMessageId,
                )
            }
        }.getOrDefault(emptyList())
    }

    suspend fun save(errors: List<ChatError>) = withContext(Dispatchers.IO) {
        if (errors.isEmpty()) {
            check(!file.exists() || file.delete()) { "Cannot delete the chat error file" }
            return@withContext
        }
        val directory = file.parentFile
        directory?.mkdirs()
        check(directory?.isDirectory != false) { "Cannot create the chat error directory" }
        val temporary = File(directory, "${file.name}.${Uuid.random()}.tmp")
        try {
            val stored = errors.map { error ->
                StoredError(
                    id = error.id,
                    title = error.title,
                    errorMessage = error.error.message ?: "Unknown error",
                    conversationId = error.conversationId,
                    timestamp = error.timestamp,
                    solution = error.solution?.name,
                    retryable = error.retryable,
                    retryMessageId = error.retryMessageId,
                )
            }
            FileOutputStream(temporary).use { output ->
                output.write(json.encodeToString(stored).toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            Files.move(temporary.toPath(), file.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            temporary.delete()
        }
    }
}
