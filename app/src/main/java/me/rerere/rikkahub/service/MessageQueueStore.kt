package me.rerere.rikkahub.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.ai.ui.isEmptyInputMessage
import me.rerere.rikkahub.data.files.extractFileUrls
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlin.uuid.Uuid

/**
 * 待发送队列的落盘存储：一个会话一个文件，放在 `noBackupFilesDir`。
 *
 * 队列在进程死亡后丢失是 C66 的已知缺口。这里只存“还没出队”的消息，恢复时一律按暂停处理，
 * 不在打开会话时自动发送。文件不进备份，也不在 Web 暴露的 `filesDir` 下。
 *
 * 附件清理要能看见队列里的引用，所以 [referencedFileUrls] 直接扫原始文本、不解析 JSON；
 * 坏文件也按“仍在引用”处理，宁可多留一个附件。
 */
class MessageQueueStore(
    private val directory: File,
    private val json: Json,
) {
    @Serializable
    private data class PersistedQueue(
        val messages: List<QueuedMessage> = emptyList(),
    )

    suspend fun load(conversationId: Uuid): List<QueuedMessage> = withContext(Dispatchers.IO) {
        val file = fileOf(conversationId)
        if (!file.isFile) return@withContext emptyList()
        runCatching {
            json.decodeFromString<PersistedQueue>(file.readText()).messages
                .filterNot { it.parts.isEmptyInputMessage() }
        }.getOrDefault(emptyList())
    }

    suspend fun save(conversationId: Uuid, messages: List<QueuedMessage>) = withContext(Dispatchers.IO) {
        if (messages.isEmpty()) {
            deleteFile(fileOf(conversationId))
            return@withContext
        }
        directory.mkdirs()
        check(directory.isDirectory) { "Cannot create the message queue directory" }
        val target = fileOf(conversationId)
        val temporary = File(directory, "${target.name}.${Uuid.random()}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(json.encodeToString(PersistedQueue(messages = messages)).toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            Files.move(temporary.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            temporary.delete()
        }
    }

    suspend fun delete(conversationId: Uuid) {
        withContext(Dispatchers.IO) { fileOf(conversationId).delete() }
    }

    /** 附件清理用：队列里出现的文件地址，坏文件也扫 */
    suspend fun referencedFileUrls(): Set<String> = withContext(Dispatchers.IO) {
        directory.listFiles().orEmpty()
            .filter { it.isFile }
            .flatMapTo(mutableSetOf()) { file ->
                runCatching { file.readText() }.getOrNull()?.let(::extractFileUrls).orEmpty()
            }
    }

    /** 删掉对应会话已经不在库里的队列文件，以及写坏名字的临时文件；返回真正删掉的文件数。 */
    suspend fun deleteOrphans(existingConversationIds: Set<Uuid>): Int = withContext(Dispatchers.IO) {
        var removed = 0
        directory.listFiles().orEmpty().filter { it.isFile }.forEach { file ->
            val id = file.queueConversationId()
            if ((id == null || id !in existingConversationIds) && file.delete()) removed++
        }
        removed
    }

    private fun fileOf(conversationId: Uuid) = File(directory, "$conversationId.json")

    private fun File.queueConversationId(): Uuid? {
        if (!name.endsWith(".json")) return null
        return runCatching { Uuid.parse(nameWithoutExtension) }.getOrNull()
    }

    private fun deleteFile(file: File) {
        check(!file.exists() || file.delete()) { "Cannot delete the message queue file" }
    }
}
