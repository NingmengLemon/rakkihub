package me.rerere.rikkahub.data.sync

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.WebDavConfig
import me.rerere.rikkahub.data.datastore.migration.SettingsJsonMigrator
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.AppDatabaseFactory
import me.rerere.rikkahub.data.db.SQLiteConfiguration
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.files.MediaCreationFiles
import me.rerere.rikkahub.data.sync.official.OFFICIAL_APPLICATION_ID
import me.rerere.rikkahub.data.sync.official.OfficialDatabaseAdapter
import me.rerere.rikkahub.data.sync.official.OfficialDatabaseConverter
import me.rerere.rikkahub.data.sync.official.OfficialDatabaseHeaders
import me.rerere.rikkahub.data.sync.official.OfficialDatabaseSchema
import me.rerere.rikkahub.data.sync.official.OfficialForeignAttachmentPaths
import me.rerere.rikkahub.data.sync.official.OfficialRestorePlan
import me.rerere.rikkahub.data.sync.official.OfficialRestorePlanner
import me.rerere.rikkahub.data.sync.official.OfficialSnapshots
import me.rerere.rikkahub.data.sync.official.OfficialUrlRewriter
import me.rerere.workspace.WorkspaceManager
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** 打包进度：已写入的条目数与总条目数，含 settings.json 与数据库快照。 */
data class BackupProgress(val done: Int, val total: Int) {
    val fraction: Float
        get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
}

private const val TAG = "BackupManager"

/** Shared archive format and restore lifecycle for local and WebDAV backups. */
class BackupManager(
    private val context: Context,
    private val database: AppDatabase,
    private val settingsStore: SettingsStore,
    private val json: Json,
) {
    private val restoreMutex = Mutex()

    suspend fun createBackup(
        includeDatabase: Boolean,
        includeFiles: Boolean,
        includeWorkspaceFiles: Boolean = false,
        backupTarget: BackupTarget = BackupTarget.SELF,
        onProgress: ((BackupProgress) -> Unit)? = null,
    ): File = withContext(Dispatchers.IO) {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val archive = File.createTempFile("backup_${timestamp}_", ".zip", context.cacheDir)
        val staging = Files.createTempDirectory(context.cacheDir.toPath(), "backup-").toFile()
        try {
            val settings = settingsStore.awaitLoaded()
            // 官方兼容出口要能被官方客户端恢复。官方那个版本的 BackupItem 只有 DATABASE
            // 与 FILES，未知枚举值不像未知字段那样能被 ignoreUnknownKeys 吞掉，会让整份设置
            // 解码失败，所以这一项必须从 settings.json 里去掉，光不打包条目不够。
            val encodedSettings = if (backupTarget == BackupTarget.OFFICIAL) {
                settings.copy(
                    webDavConfig = settings.webDavConfig.copy(
                        items = settings.webDavConfig.items
                            .filter { it != WebDavConfig.BackupItem.WORKSPACES },
                    ),
                )
            } else {
                settings
            }
            // 先把附件列出来，才能给出确定的总条目数；列表里只是路径，不占多少内存
            val attachments = if (includeFiles) collectAttachments() else emptyList()
            // 官方兼容出口按官方那套归档格式产出，官方格式里没有工作区文件，带过去只会是死重量。
            val workspaceFiles = if (includeWorkspaceFiles && backupTarget == BackupTarget.SELF) {
                collectWorkspaceFiles()
            } else {
                emptyList()
            }
            val total = 1 + (if (includeDatabase) 1 else 0) + attachments.size + workspaceFiles.size
            var done = 0
            fun advance() {
                done++
                onProgress?.invoke(BackupProgress(done, total))
            }

            ZipOutputStream(FileOutputStream(archive)).use { zip ->
                zip.putNextEntry(ZipEntry(SETTINGS_ENTRY))
                val settingsJson = json.encodeSettings(encodedSettings, settingsStore.launchCountFlow.first())
                    // 官方安装里附件与头像的路径带着官方包名；官方恢复只搬文件、不改库与设置里的
                    // 路径，所以导出时得先把它们换过去，否则装过去之后全断。
                    .let { if (backupTarget == BackupTarget.OFFICIAL) rewriteForOfficial(it) else it }
                zip.write(settingsJson.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                advance()
                if (includeDatabase) {
                    val snapshot = File(staging, SQLiteConfiguration.DATABASE_NAME)
                    DatabaseBackup.createSnapshot(database.openHelper.writableDatabase, snapshot)
                    val databaseEntry = when (backupTarget) {
                        BackupTarget.SELF -> snapshot
                        BackupTarget.OFFICIAL -> OfficialDatabaseAdapter.adaptForOfficial(
                            snapshot,
                            attachmentPrefixRewrites = attachmentPrefixRewritesForOfficial(),
                        )
                    }
                    addFile(zip, databaseEntry, DatabaseBackup.ARCHIVE_DATABASE)
                    advance()
                }
                for (attachment in attachments) {
                    currentCoroutineContext().ensureActive()
                    addFile(zip, attachment.file, attachment.archiveName)
                    advance()
                }
                for (file in workspaceFiles) {
                    currentCoroutineContext().ensureActive()
                    addFile(zip, file.file, file.archiveName)
                    advance()
                }
            }
            archive
        } catch (e: Throwable) {
            archive.delete()
            throw e
        } finally {
            staging.deleteRecursively()
        }
    }

    private suspend fun collectAttachments(): List<AttachmentEntry> {
        val result = ArrayList<AttachmentEntry>()
        for (folder in ATTACHMENT_FOLDERS) {
            val directory = File(context.filesDir, folder)
            val files = if (folder in NESTED_ATTACHMENT_FOLDERS) {
                directory.walkTopDown().asSequence()
            } else {
                directory.listFiles().orEmpty().asSequence()
            }
            for (file in files.filter { it.isFile }) {
                currentCoroutineContext().ensureActive()
                // A media result still being downloaded is incomplete and gets renamed when it finishes.
                if (folder == FileFolders.MEDIA_CREATION &&
                    file.name.endsWith(MediaCreationFiles.PARTIAL_SUFFIX)
                ) {
                    continue
                }
                val relative = file.relativeTo(directory).invariantSeparatorsPath
                PendingRestore.resolveInside(directory, relative)
                result += AttachmentEntry(file = file, archiveName = "$folder/$relative")
            }
        }
        return result
    }

    /**
     * 工作区文件：每个工作区的文件区（`<root>/files`），不含 PRoot 根文件系统与临时目录。
     *
     * 归档里的名字与 `filesDir` 下的实际布局一致（`workspaces/<root>/files/<relative>`），
     * 恢复侧因此可以沿用同一条 `files/` 映射，不需要另开一套落盘规则。
     */
    private suspend fun collectWorkspaceFiles(): List<AttachmentEntry> {
        val result = ArrayList<AttachmentEntry>()
        val base = File(context.filesDir, WORKSPACE_FOLDER)
        for (workspace in database.workspaceDao().getAll()) {
            currentCoroutineContext().ensureActive()
            val workspaceDir = runCatching { PendingRestore.resolveInside(base, workspace.root) }
                .onFailure { Log.w(TAG, "Skipping workspace with an unusable root: ${workspace.id}", it) }
                .getOrNull() ?: continue
            val files = File(workspaceDir, WORKSPACE_FILES_DIR)
            if (!files.isDirectory) continue
            for (file in files.walkTopDown().asSequence().filter { it.isFile }) {
                currentCoroutineContext().ensureActive()
                val relative = file.relativeTo(files).invariantSeparatorsPath
                PendingRestore.resolveInside(files, relative)
                result += AttachmentEntry(
                    file = file,
                    archiveName = "$WORKSPACE_FOLDER/${workspace.root}/$WORKSPACE_FILES_DIR/$relative",
                )
            }
        }
        return result
    }

    suspend fun stageRestore(
        archive: File,
        includeDatabase: Boolean,
        includeFiles: Boolean,
        includeWorkspaceFiles: Boolean = false,
    ) =
        withContext(Dispatchers.IO) {
            restoreMutex.withLock {
                val restore = pendingRestore(context)
                val staging = restore.createStagingDirectory()
                try {
                    val payload = File(staging, "payload")
                    val stagedDatabase = File(payload, "database/${SQLiteConfiguration.DATABASE_NAME}")
                    val stagedWal = File(stagedDatabase.path + "-wal")
                    val seen = mutableSetOf<String>()
                    var restoredEntries = 0
                    ZipFile(archive).use { zip ->
                        for (entry in zip.entries()) {
                            currentCoroutineContext().ensureActive()
                            if (entry.isDirectory) continue
                            // 工作区条目的形状无条件校验：即使这次没勾选工作区，也不接受指向
                            // 根文件系统的条目，免得它被静默放过。
                            if (entry.name.startsWith("$WORKSPACE_FOLDER/")) {
                                require(isWorkspaceFile(entry.name))
                            }
                            val target = when (entry.name) {
                                SETTINGS_ENTRY -> File(staging, SETTINGS_ENTRY)
                                DatabaseBackup.ARCHIVE_DATABASE -> if (includeDatabase) stagedDatabase else null
                                DatabaseBackup.WAL -> if (includeDatabase) stagedWal else null
                                DatabaseBackup.SHM -> null // Rebuilt by SQLite; never restore shared-memory state.
                                else -> if (includeFiles && isAttachment(entry.name)) {
                                    PendingRestore.resolveInside(File(payload, "files"), entry.name)
                                } else if (includeWorkspaceFiles && isWorkspaceFile(entry.name)) {
                                    PendingRestore.resolveInside(File(payload, "files"), entry.name)
                                } else {
                                    null
                                }
                            } ?: continue
                            require(seen.add(entry.name)) { "Duplicate backup entry: ${entry.name}" }
                            check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs()) {
                                "Cannot create backup staging directory"
                            }
                            zip.getInputStream(entry).use { input ->
                                FileOutputStream(target).use { output ->
                                    input.copyTo(output)
                                    output.fd.sync()
                                }
                            }
                            restoredEntries++
                        }
                    }
                    require(restoredEntries > 0) { "No selected data found in the backup" }
                    require(!stagedWal.exists() || stagedDatabase.exists()) { "Backup WAL has no matching database" }
                    if (stagedDatabase.exists()) {
                        // 先把源库的 WAL 合进主文件：转换要读它，而且旧 sidecar 留到替换之后
                        // 会被下一次 normalize 的 checkpoint 合进新库，混进去的是旧页。
                        DatabaseBackup.normalize(context, stagedDatabase)
                        // 判定只看元数据，在读任何业务表之前完成。读不出来就交给迁移链，
                        // 由 Room 自己报错，而不是在这里猜。
                        OfficialDatabaseHeaders.read(stagedDatabase)?.let { header ->
                            val filesUriPrefix = context.filesDir.toUri().toString().trimEnd('/') + "/"
                            val plan = OfficialRestorePlanner.plan(
                                version = header.version,
                                identityHash = header.identityHash,
                                ourVersion = OfficialDatabaseSchema.VERSION,
                                ourIdentity = OfficialDatabaseSchema.IDENTITY_HASH,
                                snapshots = OfficialSnapshots.BY_VERSION,
                            )
                            // 同版本的库会直接走迁移链，而迁移链不改数据；如果里面还带着别的安装
                            // 的附件路径（官方备份、或别的包名的我们），装进来之后图片就全断了。
                            // 这种情况也交给转换器：它写文本列时会把路径重写成本机。
                            val rewriteNeeded = plan == OfficialRestorePlan.Migrate &&
                                OfficialForeignAttachmentPaths.hasForeign(stagedDatabase, filesUriPrefix)
                            when {
                                plan is OfficialRestorePlan.Reject -> error(plan.reason)
                                plan is OfficialRestorePlan.Convert || rewriteNeeded ->
                                    convertStagedDatabase(context, staging, stagedDatabase)
                            }
                        }
                        // Reject unsupported schemas before publishing; run supported old migrations on the copy.
                        val room = AppDatabaseFactory.create(context, stagedDatabase.absolutePath)
                        try {
                            DatabaseBackup.checkpoint(room.openHelper.writableDatabase)
                        } finally {
                            room.close()
                        }
                        DatabaseBackup.removeSidecars(stagedDatabase)
                    }

                    val settingsFile = File(staging, SETTINGS_ENTRY)
                    if (settingsFile.exists()) {
                        // 头像与背景是 settings 里的绝对 file:// 路径，包名写死在路径里；不重写的话
                        // 导入到改过包名的安装之后，它们会整体断链。数据库那边由写入层重写。
                        val filesUriPrefix = context.filesDir.toUri().toString().trimEnd('/') + "/"
                        val migrated = SettingsJsonMigrator.migrate(
                            OfficialUrlRewriter.rewriteEmbedded(settingsFile.readText(), filesUriPrefix)
                        )
                        // 解码器会吞掉形状问题：空对象或别家的 JSON 也能解成一份全默认值的 Settings，
                        // 然后静默覆盖用户的配置。所以解码之前先看关键项在不在、类型对不对。
                        BackupSettingsShape.findProblem(migrated)?.let { problem ->
                            error("Backup settings.json is not a settings file: $problem")
                        }
                        val settings = json.decodeFromString<Settings>(migrated)
                        require(!settings.init) { "Backup contains uninitialized settings" }
                        // Persist the migrated value once, including generated IDs, for restart/retry consistency.
                        PendingRestore.writeDurably(
                            settingsFile,
                            json.encodeSettings(settings, json.launchCountOf(migrated))
                        )
                    }
                    currentCoroutineContext().ensureActive()
                    restore.publish(staging)
                } finally {
                    staging.deleteRecursively()
                }
            }
        }

    /**
     * 当前安装的 files 目录 URI 前缀，以 `/` 结尾。
     */
    private fun currentFilesUriPrefix(): String =
        context.filesDir.toUri().toString().trimEnd('/') + "/"

    /**
     * 官方安装里对应的前缀：只有包名段不同。用户号取自本机当前安装的路径，所以工作资料里
     * 导出也能对上。
     */
    private fun officialFilesUriPrefix(): String =
        currentFilesUriPrefix().replace("/${context.packageName}/", "/$OFFICIAL_APPLICATION_ID/")

    /** 官方兼容出口的 settings.json：把附件与头像的绝对路径换成官方安装的位置。 */
    private fun rewriteForOfficial(settingsJson: String): String {
        val official = officialFilesUriPrefix()
        return if (official == currentFilesUriPrefix()) {
            settingsJson
        } else {
            OfficialUrlRewriter.rewriteEmbedded(settingsJson, official)
        }
    }

    private fun attachmentPrefixRewritesForOfficial(): List<Pair<String, String>> {
        val ours = currentFilesUriPrefix()
        val official = officialFilesUriPrefix()
        return if (official == ours) emptyList() else listOf(ours to official)
    }

    /**
     * 把暂存库转成当前版本并替换掉它。
     *
     * 转换器写文本列时会把带别的安装路径的附件引用重写成当前安装的，所以两条路都要经过它：
     * 版本/结构不一致的库，以及结构一致但带着外来路径的库。
     */
    private suspend fun convertStagedDatabase(context: Context, staging: File, stagedDatabase: File) {
        val converted = File(staging, "converted-${stagedDatabase.name}")
        OfficialDatabaseConverter(context).convert(stagedDatabase, converted)
        check(stagedDatabase.delete()) { "Cannot replace staged database" }
        Files.move(converted.toPath(), stagedDatabase.toPath())
    }
    private fun isAttachment(name: String): Boolean {
        val folder = name.substringBefore('/')
        if (folder !in ATTACHMENT_FOLDERS || '/' !in name) return false
        val relative = name.substringAfter('/')
        require(relative.isNotBlank()) { "Invalid backup attachment: $name" }
        require(folder in NESTED_ATTACHMENT_FOLDERS || '/' !in relative) { "Invalid backup attachment: $name" }
        return true
    }

    /**
     * 工作区文件：`workspaces/<root>/files/<relative>`。
     *
     * 只认文件区。`workspaces/<root>/linux` 那类根文件系统路径一律拒绝：归档写坏或被改过时
     * 不能让它覆盖到 PRoot 根文件系统。
     */
    private fun isWorkspaceFile(name: String): Boolean {
        if (!name.startsWith("$WORKSPACE_FOLDER/")) return false
        val parts = name.split('/')
        require(parts.size >= 4 && parts[1].isNotBlank() && parts[2] == WORKSPACE_FILES_DIR) {
            "Invalid backup workspace entry: $name"
        }
        return true
    }

    private fun addFile(zip: ZipOutputStream, file: File, name: String) {
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private data class AttachmentEntry(val file: File, val archiveName: String)

    companion object {
        // 归档里的顶层条目名。官方格式由 data/sync/official/OfficialBackupLayout 冻结，
        // 那里与这里对账，改动其中一个就要同步另一个。
        internal const val SETTINGS_ENTRY = "settings.json"

        internal val ATTACHMENT_FOLDERS =
            listOf(FileFolders.UPLOAD, FileFolders.SKILLS, FileFolders.FONTS, FileFolders.MEDIA_CREATION)

        /** Backed up with their subdirectories; the other folders only contain top-level files. */
        internal val NESTED_ATTACHMENT_FOLDERS = setOf(FileFolders.SKILLS, FileFolders.MEDIA_CREATION)

        // 启动次数不在 Settings 里，备份文件里仍放在 settings.json 的这个字段，和旧备份保持一致
        internal const val LAUNCH_COUNT_KEY = "launchCount"

        // 工作区文件放 filesDir 下的这个目录，每个工作区一个子目录，文件区再下一层。
        // 归档里的名字与它一致，恢复侧复用同一条 files/ 映射。
        internal const val WORKSPACE_FOLDER = WorkspaceManager.BASE_DIR_NAME
        internal const val WORKSPACE_FILES_DIR = WorkspaceManager.FILES_DIR

        private fun Json.encodeSettings(settings: Settings, launchCount: Int): String {
            val fields = encodeToJsonElement(settings).jsonObject + (LAUNCH_COUNT_KEY to JsonPrimitive(launchCount))
            return encodeToString(JsonObject(fields))
        }

        // 更早的备份没有这个字段，按 0 恢复，和它还在 Settings 里时的默认值一致
        private fun Json.launchCountOf(settingsJson: String): Int =
            parseToJsonElement(settingsJson).jsonObject[LAUNCH_COUNT_KEY]?.jsonPrimitive?.intOrNull ?: 0

        private fun pendingRestore(context: Context) = PendingRestore(
            root = File(context.noBackupFilesDir, "backup-restore"),
            databaseFile = context.getDatabasePath(SQLiteConfiguration.DATABASE_NAME),
            filesDir = context.filesDir,
        )

        /** Must finish before Koin, Room, SettingsStore or any background consumers are initialized. */
        suspend fun applyPendingRestore(context: Context, json: Json): Boolean = withContext(Dispatchers.IO) {
            pendingRestore(context).apply { settingsJson ->
                SettingsStore.restoreBeforeInitialization(
                    context = context,
                    settings = json.decodeFromString<Settings>(settingsJson),
                    launchCount = json.launchCountOf(settingsJson),
                )
            }
        }
    }
}
