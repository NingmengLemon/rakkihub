package me.rerere.rikkahub.data.files

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.FilesRepository
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.utils.JsonInstant
import java.io.File

/**
 * 附件清理：扫一遍所有引用点，删掉没有任何引用的上传文件。
 *
 * 附件按内容寻址之后，同一个文件可能被多条消息、草稿或头像共用，删消息时不能顺手删文件。回收
 * 交给这里做标记-清除：先收集「还活着」的引用，再删没被引用的文件。
 *
 * 扫描范围是消息 JSON、设置（助手与用户头像、背景）、落盘的草稿、内存里的已加载会话、待发送队列
 * 与提交中的消息，以及 [LiveAttachmentReferences] 里登记的输入框附件。后者兜住编辑历史消息期间
 * 不写草稿的那部分：编辑期间输入框被编辑内容占用，草稿不写它，但用户照样可以往里加附件。
 *
 * Web 客户端上传之后还停在浏览器输入框里的附件不在扫描范围内，清理前别让浏览器里留着没发出去的
 * 附件。消息扫描一次把所有含本地地址的行读进内存，历史很大时是手动清理的时间与内存上限。
 *
 * 宁可少收垃圾也不能删错：解析不了的地址不知道指向哪个文件，名字对得上的文件先留着。
 */
class AttachmentCleaner(
    private val context: Context,
    private val filesRepository: FilesRepository,
    private val conversationRepository: ConversationRepository,
    private val settingsStore: SettingsStore,
    private val draftStore: ChatDraftStore,
    private val chatService: ChatService,
    private val liveReferences: LiveAttachmentReferences,
) {
    data class Result(
        val removedFiles: Int = 0,
        val freedBytes: Long = 0,
        /** 有回复正在生成，这次没有清理 */
        val busy: Boolean = false,
    )

    companion object {
        private const val TAG = "AttachmentCleaner"
    }

    suspend fun clean(): Result = withContext(Dispatchers.IO) {
        if (chatService.isGenerating()) return@withContext Result(busy = true)

        val settings = settingsStore.awaitLoaded()
        val (referenced, unresolved) = collectReferencedPaths(
            buildList {
                conversationRepository.messageContentsWithFileReferences().forEach { addAll(extractFileUrls(it)) }
                addAll(extractFileUrls(JsonInstant.encodeToString(settings)))
                addAll(draftStore.referencedFileUrls())
                addAll(chatService.unsavedAttachmentUrls())
                addAll(liveReferences.snapshot())
            }
        )
        // 扫描要花时间，这期间开始的生成会写出还没落库的文件，删除前再确认一次
        if (chatService.isGenerating()) return@withContext Result(busy = true)

        val uploadDir = File(context.filesDir, FileFolders.UPLOAD)
        val now = System.currentTimeMillis()
        var removed = 0
        var freed = 0L
        uploadDir.listFiles().orEmpty().filter { it.isFile }.forEach { file ->
            if (!isUnreferencedUploadFile(file, referenced, unresolved, now)) return@forEach
            val size = file.length()
            if (!file.delete()) return@forEach
            filesRepository.deleteByPath(FileUtils.buildRelativePath(FileFolders.UPLOAD, file))
            removed += 1
            freed += size
        }
        Log.i(TAG, "clean: removed $removed files, freed $freed bytes")
        Result(removedFiles = removed, freedBytes = freed)
    }
}

/**
 * 把各个来源的地址合成「还被引用」的规范路径集合。
 *
 * 解析不了的地址单独留下，由 [isUnreferencedUploadFile] 按文件名保守保留。
 */
internal fun collectReferencedPaths(urls: Iterable<String>): Pair<Set<String>, List<String>> {
    val referenced = mutableSetOf<String>()
    val unresolved = mutableListOf<String>()
    urls.forEach { url ->
        val path = fileUrlToCanonicalPath(url)
        if (path != null) referenced += path else unresolved += url
    }
    return referenced to unresolved
}

/** 正在写的内容文件先别动 */
private const val PENDING_GRACE_MILLIS = 60 * 60 * 1000L

/**
 * 这个上传文件能不能删。
 *
 * 三个条件都满足才删：没有被任何引用点提到、名字不在解析不了的地址里、也不是刚写了一半的
 * 临时文件。解析不了的地址不知道指向哪个文件，只能按名字保守保留。
 */
internal fun isUnreferencedUploadFile(
    file: File,
    referencedPaths: Set<String>,
    unresolvedUrls: List<String>,
    now: Long,
): Boolean {
    val pending = file.name.startsWith(ContentAddressedFiles.PENDING_PREFIX)
    if (pending && now - file.lastModified() < PENDING_GRACE_MILLIS) return false
    val path = runCatching { file.canonicalPath }.getOrNull() ?: return false
    if (path in referencedPaths) return false
    return unresolvedUrls.none { it.contains(file.name) }
}
