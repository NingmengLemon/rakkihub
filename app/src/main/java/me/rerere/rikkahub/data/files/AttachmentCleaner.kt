package me.rerere.rikkahub.data.files

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.FilesRepository
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.service.MessageQueueStore
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
 * 文件页的按天清理也走这里（[cleanOlderThan]），所以「文件很旧」不再等于「可以删」。
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
    private val messageQueueStore: MessageQueueStore,
    private val chatService: ChatService,
    private val liveReferences: LiveAttachmentReferences,
) {
    data class Result(
        val removedFiles: Int = 0,
        val freedBytes: Long = 0,
        /** 有文件没删掉，登记行留给下次启动对账 */
        val failedFiles: Int = 0,
        /** 有回复正在生成，这次没有清理 */
        val busy: Boolean = false,
    )

    companion object {
        private const val TAG = "AttachmentCleaner"
    }

    /** 删掉所有没有被引用的上传文件 */
    suspend fun clean(): Result = withContext(Dispatchers.IO) {
        cleanUploads("clean") { file, _, referenced, unresolved ->
            shouldDeleteUploadFile(file, referenced, unresolved, System.currentTimeMillis())
        }
    }

    /**
     * 只删登记时间早于 [cutoffMillis] 的无引用上传文件，文件页的按天清理用。
     *
     * 没有 `managed_files` 登记的文件按修改时间算。仍然被引用的文件不管多旧都保留：去重之后
     * 「文件旧」不代表「没人用」。登记时间在去重复用时不刷新，所以年龄是首次写进来的时间。
     */
    suspend fun cleanOlderThan(cutoffMillis: Long): Result = withContext(Dispatchers.IO) {
        // 一次取全量登记，避免每个文件点查一次
        val createdAtByPath = filesRepository.listByFolder(FileFolders.UPLOAD)
            .first()
            .associate { it.relativePath to it.createdAt }

        cleanUploads("cleanOlderThan") { file, relativePath, referenced, unresolved ->
            shouldDeleteUploadFile(
                file = file,
                referencedPaths = referenced,
                unresolvedUrls = unresolved,
                now = System.currentTimeMillis(),
                cutoffMillis = cutoffMillis,
                createdAtMillis = createdAtByPath[relativePath] ?: file.lastModified(),
            )
        }
    }

    private suspend fun cleanUploads(
        logLabel: String,
        predicate: suspend (
            file: File,
            relativePath: String,
            referenced: Set<String>,
            unresolved: List<String>
        ) -> Boolean,
    ): Result {
        if (chatService.isGenerating()) return Result(busy = true)

        val (referenced, unresolved) = collectReferences()
        // 扫描要花时间，这期间开始的生成会写出还没落库的文件，删除前再确认一次
        if (chatService.isGenerating()) return Result(busy = true)
        // 同一段时间里用户也可能切回聊天页加附件，这些只在内存里，删除前再取一次
        val (lateReferenced, lateUnresolved) = inMemoryReferences()
        val allReferenced = referenced + lateReferenced
        val allUnresolved = unresolved + lateUnresolved

        val uploadDir = File(context.filesDir, FileFolders.UPLOAD)
        var removed = 0
        var failed = 0
        var freed = 0L
        uploadDir.listFiles().orEmpty().filter { it.isFile }.forEach { file ->
            val relativePath = FileUtils.buildRelativePath(FileFolders.UPLOAD, file)
            if (!predicate(file, relativePath, allReferenced, allUnresolved)) return@forEach
            val size = file.length()
            if (!file.delete()) {
                failed += 1
                return@forEach
            }
            filesRepository.deleteByPath(relativePath)
            removed += 1
            freed += size
        }
        Log.i(TAG, "$logLabel: removed $removed files, freed $freed bytes, $failed failed")
        return Result(removedFiles = removed, freedBytes = freed, failedFiles = failed)
    }

    private suspend fun collectReferences(): Pair<Set<String>, List<String>> {
        val settings = settingsStore.awaitLoaded()
        val (referenced, unresolved) = collectReferencedPaths(
            buildList {
                conversationRepository.messageContentsWithFileReferences().forEach { addAll(extractFileUrls(it)) }
                addAll(extractFileUrls(JsonInstant.encodeToString(settings)))
                addAll(draftStore.referencedFileUrls())
                addAll(messageQueueStore.referencedFileUrls())
            },
            filesDir = context.filesDir,
        )
        val (memoryReferenced, memoryUnresolved) = inMemoryReferences()
        return (referenced + memoryReferenced) to (unresolved + memoryUnresolved)
    }

    /** 只存在于内存里的引用：输入框登记与已加载会话、队列、提交中的消息 */
    private suspend fun inMemoryReferences(): Pair<Set<String>, List<String>> =
        collectReferencedPaths(
            liveReferences.snapshot() + chatService.unsavedAttachmentUrls(),
            filesDir = context.filesDir,
        )
}

/**
 * 把各个来源的地址合成「还被引用」的规范路径集合。
 *
 * 解析不了的地址单独留下，由 [shouldDeleteUploadFile] 按文件名保守保留。
 * 消息库里新写的 `file:upload/...` 相对形态按 [filesDir] 还原；旧数据里的绝对地址照旧。
 */
internal fun collectReferencedPaths(
    urls: Iterable<String>,
    filesDir: File?,
): Pair<Set<String>, List<String>> {
    val referenced = mutableSetOf<String>()
    val unresolved = mutableListOf<String>()
    urls.forEach { url ->
        val path = fileUrlToCanonicalPath(url, filesDir)
        if (path != null) referenced += path else unresolved += url
    }
    return referenced to unresolved
}

/** 正在写的内容文件先别动 */
private const val PENDING_GRACE_MILLIS = 60 * 60 * 1000L

/**
 * 这个上传文件能不能删。
 *
 * 默认三个条件都满足才删：没有被任何引用点提到、名字不在解析不了的地址里、也不是刚写了一半的
 * 临时文件。解析不了的地址不知道指向哪个文件，只能按名字保守保留。[cutoffMillis] 不为空时
 * 还要求它的登记时间早于这个截止时间。
 */
internal fun shouldDeleteUploadFile(
    file: File,
    referencedPaths: Set<String>,
    unresolvedUrls: List<String>,
    now: Long,
    cutoffMillis: Long? = null,
    createdAtMillis: Long = file.lastModified(),
): Boolean {
    if (cutoffMillis != null && createdAtMillis >= cutoffMillis) return false
    val pending = file.name.startsWith(ContentAddressedFiles.PENDING_PREFIX)
    if (pending && now - file.lastModified() < PENDING_GRACE_MILLIS) return false
    val path = runCatching { file.canonicalPath }.getOrNull() ?: return false
    if (path in referencedPaths) return false
    return unresolvedUrls.none { it.contains(file.name) }
}
