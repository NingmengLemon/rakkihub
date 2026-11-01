package me.rerere.rikkahub.data.files

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.ai.ui.UIMessagePart
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlin.uuid.Uuid

/**
 * 聊天输入框的草稿：用户没发出去的文字与附件。
 *
 * 输入状态本来只在 ViewModel 里，进程一死就没了，附件引用也跟着消失。草稿落盘之后有两个好处：
 * 重进应用还在，以及「哪些附件仍被引用」这件事对库外的清理逻辑可见（去重的标记-清除要用）。
 *
 * 一个会话一个文件，取 [Uuid] 作文件名，内容坏掉时当作没有草稿，不阻塞聊天页。放
 * `noBackupFilesDir`：`filesDir` 整个目录被 web 路由暴露，草稿不该出现在那里；它也不进备份。
 *
 * 方法都在 [Dispatchers.IO] 上跑：调用点在输入观察与页面生命周期里，不能占主线程。
 */
class ChatDraftStore(
    private val directory: File,
    private val json: Json,
) {

    @Serializable
    data class Draft(
        val text: String = "",
        val parts: List<UIMessagePart> = emptyList(),
    ) {
        val isEmpty: Boolean get() = text.isBlank() && parts.isEmpty()
    }

    suspend fun load(conversationId: Uuid): Draft? = withContext(Dispatchers.IO) {
        val file = fileOf(conversationId)
        if (!file.isFile) return@withContext null
        runCatching { json.decodeFromString<Draft>(file.readText()) }.getOrNull()
    }

    suspend fun save(conversationId: Uuid, draft: Draft) = withContext(Dispatchers.IO) {
        if (draft.isEmpty) {
            // 空草稿不留空壳，否则引用扫描得一直保着它对应的附件。删不掉要报出来，
            // 否则调用方会把「还是旧草稿」当成「已经清空」
            val file = fileOf(conversationId)
            check(!file.exists() || file.delete()) { "Cannot delete the draft file" }
            return@withContext
        }
        // 并发写时 mkdirs 可能返回 false（目录已经被另一个写者建好），看目录在不在而不是看返回值
        directory.mkdirs()
        check(directory.isDirectory) { "Cannot create the draft directory" }
        val target = fileOf(conversationId)
        // 临时文件名带随机后缀：同一个会话可能同时有两个写者（旧页面的 onCleared 与新页面的防抖），
        // 共用一个临时文件会让它们的字节互相覆盖，严重时把已经搬好的好草稿写坏
        val temporary = File(directory, "${target.name}.${Uuid.random()}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(json.encodeToString(draft).toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            // renameTo 在 Windows 上不会覆盖已存在的目标，统一用 Files.move
            Files.move(temporary.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            // 移动成功时临时文件已经不在了，失败时别把半截文件留在目录里
            temporary.delete()
        }
    }

    suspend fun delete(conversationId: Uuid) {
        withContext(Dispatchers.IO) { fileOf(conversationId).delete() }
    }

    /**
     * 草稿里引用到的本地文件地址，附件清理用。
     *
     * 直接扫原始文本、不解析 JSON：坏掉的草稿也要算数，宁可多留一个文件。
     */
    suspend fun referencedFileUrls(): Set<String> = withContext(Dispatchers.IO) {
        directory.listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(".json") }
            .flatMapTo(mutableSetOf()) { file ->
                runCatching { file.readText() }.getOrNull()?.let(::extractFileUrls).orEmpty()
            }
    }

    /**
     * 删掉不该再留的草稿：对应会话已经不在库里的、写到一半留下的临时文件，以及名字根本不是草稿的文件。
     *
     * 「不在库里」不等于「回不去」：新建会话的 id 只在内存里，用户没发消息就退进程，库里查不到它，
     * 但关掉「启动新建会话」时下次启动会直接打开它（[resumableConversationId]），草稿得留着。
     * 其余的库外 id 不会再出现，留着只会占空间、将来还会让附件清理一直保着文件。
     *
     * 计数是真正删掉的文件数，目录不可写时是 0。
     */
    suspend fun deleteOrphans(
        existingConversationIds: Set<Uuid>,
        resumableConversationId: Uuid? = null,
    ): Int = withContext(Dispatchers.IO) {
        val keep = existingConversationIds + listOfNotNull(resumableConversationId)
        var removed = 0
        directory.listFiles().orEmpty().filter { it.isFile }.forEach { file ->
            val id = file.draftConversationId()
            if ((id == null || id !in keep) && file.delete()) removed++
        }
        removed
    }

    private fun fileOf(conversationId: Uuid) = File(directory, "$conversationId.json")

    /** 草稿文件名里的会话 id；临时文件与其它名字返回 null */
    private fun File.draftConversationId(): Uuid? {
        if (!name.endsWith(".json")) return null
        return runCatching { Uuid.parse(nameWithoutExtension) }.getOrNull()
    }
}
