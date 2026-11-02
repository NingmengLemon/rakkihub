package me.rerere.rikkahub.data.sync

import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.WebDavConfig
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.AppDatabaseFactory
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.sync.official.OFFICIAL_APPLICATION_ID
import me.rerere.rikkahub.data.sync.official.OfficialDatabaseHeaders
import me.rerere.rikkahub.data.sync.official.OfficialDatabaseSchema
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.workspace.WorkspaceManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class BackupManagerTest {
    private lateinit var directory: File
    private lateinit var context: Context
    private lateinit var liveDatabase: AppDatabase
    private lateinit var manager: BackupManager

    @Before fun setUp() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        directory = Files.createTempDirectory(app.cacheDir.toPath(), "backup-manager-test-").toFile()
        context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir() = File(directory, "files").apply { mkdirs() }
            override fun getCacheDir() = File(directory, "cache").apply { mkdirs() }
            override fun getNoBackupFilesDir() = File(directory, "no-backup").apply { mkdirs() }
            override fun getDatabasePath(name: String): File = if (File(name).isAbsolute) {
                File(name)
            } else {
                File(directory, "databases/$name").also { it.parentFile!!.mkdirs() }
            }
        }
        liveDatabase = AppDatabaseFactory.create(context)
        liveDatabase.openHelper.writableDatabase.execSQL("CREATE TABLE backup_probe (text TEXT)")
        liveDatabase.openHelper.writableDatabase.execSQL("INSERT INTO backup_probe VALUES ('live')")
        manager = BackupManager(context, liveDatabase, GlobalContext.get().get<SettingsStore>(), JsonInstant)
    }

    @After fun tearDown() {
        liveDatabase.close()
        directory.deleteRecursively()
    }

    @Test fun oldArchiveIgnoresShmAndLeavesLiveDatabaseUntouchedUntilStartup() = runBlocking {
        val source = File(directory, "source")
        val archive = File(directory, "legacy.zip")
        withRoom(source) { room ->
            val db = room.openHelper.writableDatabase
            db.execSQL("CREATE TABLE backup_probe (text TEXT)")
            DatabaseBackup.checkpoint(db)
            db.query("PRAGMA wal_autocheckpoint=0").use { assertTrue(it.moveToFirst()) }
            db.execSQL("INSERT INTO backup_probe VALUES ('archived')")
            ZipOutputStream(archive.outputStream()).use { zip ->
                addFile(zip, DatabaseBackup.ARCHIVE_DATABASE, source)
                addFile(zip, DatabaseBackup.WAL, File(source.path + "-wal"))
                zip.putNextEntry(ZipEntry(DatabaseBackup.SHM))
                zip.write("deliberately invalid SHM".toByteArray())
                zip.closeEntry()
            }
        }
        manager.stageRestore(archive, includeDatabase = true, includeFiles = false)
        assertEquals("live", probe(liveDatabase))
        val staged = File(context.noBackupFilesDir, "backup-restore/pending/payload/database/rikka_hub")
        assertTrue(staged.isFile)
        assertFalse(File(staged.path + "-wal").exists())
        assertFalse(File(staged.path + "-shm").exists())
        liveDatabase.close()
        BackupManager.applyPendingRestore(context, JsonInstant)
        liveDatabase = AppDatabaseFactory.create(context)
        assertEquals("archived", probe(liveDatabase))
        ZipFile(archive).use { assertTrue(it.getEntry(DatabaseBackup.SHM) != null) }
    }

    @Test fun newArchiveContainsStandaloneDatabaseAndNoWalOrShm() = runBlocking {
        val archive = manager.createBackup(includeDatabase = true, includeFiles = false)
        ZipFile(archive).use { zip ->
            assertTrue(zip.getEntry("settings.json") != null)
            assertTrue(zip.getEntry(DatabaseBackup.ARCHIVE_DATABASE) != null)
            assertEquals(null, zip.getEntry(DatabaseBackup.WAL))
            assertEquals(null, zip.getEntry(DatabaseBackup.SHM))
        }
        manager.stageRestore(archive, includeDatabase = true, includeFiles = false)
        assertEquals("live", probe(liveDatabase))
    }

    /**
     * 官方兼容出口的核心安全属性：双方 schema 一致时改写附件路径后交出，分叉时直接失败。
     *
     * 现在本仓库的 schema 与官方 v32 一致，所以这里只能验证「一致时能产出归档，且该归档能被
     * 恢复流程接受」。分叉分支由 `OfficialDatabaseCompatibilityTest` 覆盖。
     */
    @Test fun officialTargetWritesArchiveThatStagingAccepts() = runBlocking {
        val archive = manager.createBackup(
            includeDatabase = true,
            includeFiles = false,
            backupTarget = BackupTarget.OFFICIAL,
        )
        ZipFile(archive).use { zip ->
            assertTrue(zip.getEntry("settings.json") != null)
            assertTrue(zip.getEntry(DatabaseBackup.ARCHIVE_DATABASE) != null)
        }
        manager.stageRestore(archive, includeDatabase = true, includeFiles = false)
        assertEquals("live", probe(liveDatabase))
    }

    @Test fun launchCountStaysInSettingsJsonThroughBackupAndStaging() = runBlocking {
        val archive = manager.createBackup(includeDatabase = false, includeFiles = false)
        val archived = ZipFile(archive).use { zip ->
            launchCountOf(zip.getInputStream(zip.getEntry("settings.json")).readBytes().decodeToString())
        }
        assertTrue(archived != null)
        manager.stageRestore(archive, includeDatabase = false, includeFiles = false)
        val staged = File(context.noBackupFilesDir, "backup-restore/pending/settings.json").readText()
        assertEquals(archived, launchCountOf(staged))
    }

    @Test fun archiveWithoutLaunchCountRestoresItAsZero() = runBlocking {
        val current = manager.createBackup(includeDatabase = false, includeFiles = false)
        val settingsJson = ZipFile(current).use { zip ->
            zip.getInputStream(zip.getEntry("settings.json")).readBytes().decodeToString()
        }
        val archive = File(directory, "no-launch-count.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("settings.json"))
            zip.write(
                JsonObject(
                    JsonInstant.parseToJsonElement(settingsJson).jsonObject - "launchCount"
                ).toString().toByteArray()
            )
            zip.closeEntry()
        }
        manager.stageRestore(archive, includeDatabase = false, includeFiles = false)
        val staged = File(context.noBackupFilesDir, "backup-restore/pending/settings.json").readText()
        assertEquals(0, launchCountOf(staged))
    }

    private fun launchCountOf(settingsJson: String): Int? =
        JsonInstant.parseToJsonElement(settingsJson).jsonObject["launchCount"]?.jsonPrimitive?.int

    /**
     * 设置存储是进程级单例，apply 会把 pending 里的 settings 写进这台设备的真实设置。
     * 不验设置恢复的用例在 apply 之前把这份 Settings 删掉。
     */
    private fun dropStagedSettings() {
        assertTrue(
            File(context.noBackupFilesDir, "backup-restore/pending/${BackupManager.SETTINGS_ENTRY}").delete()
        )
    }

    @Test fun mediaCreationFilesKeepTheirDirectoriesAndSkipUnfinishedDownloads() = runBlocking {
        val session = File(context.filesDir, "media_creation/session")
        File(session, "record").mkdirs()
        File(session, "draft").mkdirs()
        File(session, "record/out_0.png").writeText("image")
        File(session, "record/out_1.mp4.part").writeText("half a video")
        File(session, "draft/asset.jpg").writeText("asset")
        val archive = manager.createBackup(includeDatabase = false, includeFiles = true)
        ZipFile(archive).use { zip ->
            assertTrue(zip.getEntry("media_creation/session/record/out_0.png") != null)
            assertTrue(zip.getEntry("media_creation/session/draft/asset.jpg") != null)
            assertEquals(null, zip.getEntry("media_creation/session/record/out_1.mp4.part"))
        }
        session.deleteRecursively()
        manager.stageRestore(archive, includeDatabase = false, includeFiles = true)
        assertFalse(session.exists())
        dropStagedSettings()
        assertTrue(BackupManager.applyPendingRestore(context, JsonInstant))
        assertEquals("image", File(session, "record/out_0.png").readText())
        assertEquals("asset", File(session, "draft/asset.jpg").readText())
        assertFalse(File(session, "record/out_1.mp4.part").exists())
    }

    @Test fun unsupportedSchemaIsRejectedBeforePublishing() = runBlocking {
        val future = File(directory, "future")
        withRoom(future) {
            it.openHelper.writableDatabase.version = 9999
        }
        val archive = File(directory, "future.zip")
        ZipOutputStream(archive.outputStream()).use { addFile(it, DatabaseBackup.ARCHIVE_DATABASE, future) }
        var failed = false
        try {
            manager.stageRestore(archive, includeDatabase = true, includeFiles = false)
        } catch (_: Exception) {
            failed = true
        }
        assertTrue(failed)
        assertFalse(File(context.noBackupFilesDir, "backup-restore/pending").exists())
        assertEquals("live", probe(liveDatabase))
    }

    /** 同版本但结构不同：迁移链不管，Room 会拒绝，必须先转成当前版本再发布 */
    @Test fun sameVersionWithDifferentIdentityIsConvertedBeforePublishing() = runBlocking {
        val source = File(directory, "same-version")
        val archive = File(directory, "same-version.zip")
        withRoom(source) { room ->
            val db = room.openHelper.writableDatabase
            db.execSQL(
                "INSERT INTO workspaces (id, name, root, shell_status, created_at, updated_at, type) " +
                    "VALUES ('w1', 'demo', '/tmp/demo', 'READY', 1, 2, 'LOCAL')"
            )
            // 冒充「同版本但 schema 被改过」的库
            db.execSQL("UPDATE room_master_table SET identity_hash = '00000000000000000000000000000000'")
            DatabaseBackup.checkpoint(db)
        }
        ZipOutputStream(archive.outputStream()).use { zip -> addFile(zip, DatabaseBackup.ARCHIVE_DATABASE, source) }

        manager.stageRestore(archive, includeDatabase = true, includeFiles = false)

        val staged = File(context.noBackupFilesDir, "backup-restore/pending/payload/database/rikka_hub")
        assertTrue("转换结果应当已发布到 pending", staged.isFile)
        SQLiteDatabase.openDatabase(staged.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            assertEquals(OfficialDatabaseSchema.VERSION, db.version)
            OfficialDatabaseHeaders.readIdentityHash(db)?.let {
                assertEquals("转换后应当是我们的结构", OfficialDatabaseSchema.IDENTITY_HASH, it)
            } ?: error("转换结果应当带 schema 指纹")
            db.rawQuery("SELECT name FROM workspaces WHERE id = 'w1'", null).use {
                assertTrue("行数据应当被搬过去", it.moveToFirst())
                assertEquals("demo", it.getString(0))
            }
        }
        assertEquals("live 在重启前不动", "live", probe(liveDatabase))
    }

    /** 版本高于本应用：迁移链降不了，明确拒绝，不要回落到迁移链让 Room 报个看不懂的错 */
    @Test fun newerVersionIsRejectedWithoutTouchingLiveDatabase() = runBlocking {
        val source = File(directory, "newer")
        val archive = File(directory, "newer.zip")
        withRoom(source) { room ->
            val db = room.openHelper.writableDatabase
            DatabaseBackup.checkpoint(db)
            db.execSQL("PRAGMA user_version = 33")
        }
        ZipOutputStream(archive.outputStream()).use { zip -> addFile(zip, DatabaseBackup.ARCHIVE_DATABASE, source) }

        val error = runCatching { manager.stageRestore(archive, includeDatabase = true, includeFiles = false) }
            .exceptionOrNull()
        assertTrue("高版本必须失败，实际是 $error", error is IllegalStateException)
        assertTrue(
            "提示要说明版本高，实际是 ${error?.message}",
            error?.message.orEmpty().contains("高于"),
        )
        assertFalse(File(context.noBackupFilesDir, "backup-restore/pending").exists())
        assertEquals("live", probe(liveDatabase))
    }

    /**
     * 同版本、结构一致，但库里的附件路径还指着别的安装（官方备份、或别的包名的我们）。
     *
     * 这种情况走迁移链不会改数据，装进来之后图片全断。它必须也过转换器，由写入层把文本列里
     * 的路径重写成本机。
     */
    @Test fun foreignAttachmentPathsAreRewrittenOnSameVersionRestore() = runBlocking {
        val source = File(directory, "foreign-paths")
        val archive = File(directory, "foreign-paths.zip")
        val foreign = "file:///data/user/0/me.rerere.rikkahub/files/upload/abc.png"
        withRoom(source) { room ->
            val db = room.openHelper.writableDatabase
            db.execSQL(
                "INSERT INTO ConversationEntity (id, title, nodes, create_at, update_at) " +
                    "VALUES ('c1', 'demo', '[]', 1, 2)",
            )
            db.execSQL(
                "INSERT INTO message_node (id, conversation_id, node_index, messages, select_index) " +
                    "VALUES ('n1', 'c1', 0, ?, 0)",
                arrayOf("""[{"type":"image","url":"$foreign"}]"""),
            )
            DatabaseBackup.checkpoint(db)
        }
        ZipOutputStream(archive.outputStream()).use { zip -> addFile(zip, DatabaseBackup.ARCHIVE_DATABASE, source) }

        manager.stageRestore(archive, includeDatabase = true, includeFiles = false)

        val staged = File(context.noBackupFilesDir, "backup-restore/pending/payload/database/rikka_hub")
        assertTrue("暂存库应当存在", staged.isFile)
        SQLiteDatabase.openDatabase(staged.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val messages = db.rawQuery("SELECT messages FROM message_node WHERE id = 'n1'", null).use {
                check(it.moveToFirst())
                it.getString(0)
            }
            val ours = localFileUrl("upload/abc.png")
            assertTrue("应当重写成本机路径，实际是 $messages", messages.contains(ours))
            assertFalse("不应残留官方包的路径，实际是 $messages", messages.contains("me.rerere.rikkahub/files/"))
        }
    }

    /**
     * 工作区文件默认不进备份。
     *
     * 工作区里可能有一个完整的 PRoot 根文件系统，多数情况下不需要跟着备份走，所以它是一项
     * 独立的选择项，默认关。
     */
    @Test fun workspaceFilesStayOutOfTheArchiveUnlessAsked() = runBlocking {
        seedWorkspace("ws1", "notes/a.txt" to "hello")

        val archive = manager.createBackup(includeDatabase = false, includeFiles = false)
        ZipFile(archive).use { zip ->
            assertFalse(
                "默认不应打包工作区文件",
                zip.entries().asSequence().any { it.name.startsWith("${BackupManager.WORKSPACE_FOLDER}/") },
            )
        }
    }

    /** 勾选之后只打包文件区；根文件系统与临时目录再大也不进归档。 */
    @Test fun workspaceBackupTakesOnlyTheFilesArea() = runBlocking {
        seedWorkspace("ws1", "notes/a.txt" to "hello", "empty/.keep" to "")
        // 根文件系统与临时目录是文件区的兄弟目录，不能被打包
        File(workspaceDir("ws1"), "linux/bin").apply { mkdirs() }.resolve("sh").writeText("rootfs")
        File(workspaceDir("ws1"), "tmp").apply { mkdirs() }.resolve("scratch").writeText("tmp")

        val archive = manager.createBackup(includeDatabase = false, includeFiles = false, includeWorkspaceFiles = true)
        val entries = ZipFile(archive).use { zip -> zip.entries().asSequence().map { it.name }.toSet() }
        // 精确断言：多出任何条目（根文件系统、临时目录、目录条目）都会被抓到
        assertEquals(
            "归档里只应有 settings 与工作区文件区的文件",
            setOf(
                "settings.json",
                workspaceArchivePath("ws1", "notes/a.txt"),
                workspaceArchivePath("ws1", "empty/.keep"),
            ),
            entries,
        )
    }

    /** 库里有工作区行但磁盘上没有目录（或 root 不合法）时跳过，不该让整次导出失败。 */
    @Test fun workspaceWithoutADirectoryIsSkipped() = runBlocking {
        liveDatabase.workspaceDao().upsert(
            WorkspaceEntity(id = "ws9", name = "ws9", root = "ws9", createdAt = 1, updatedAt = 1),
        )
        // root 不合法：正常由 uuid 生成，这里模拟库被改过的情况
        liveDatabase.workspaceDao().upsert(
            WorkspaceEntity(id = "ws-bad", name = "bad", root = "..", createdAt = 1, updatedAt = 1),
        )

        val archive = manager.createBackup(includeDatabase = false, includeFiles = false, includeWorkspaceFiles = true)
        val entries = ZipFile(archive).use { zip -> zip.entries().asSequence().map { it.name }.toSet() }
        assertEquals("没有可打包的文件时只应剩 settings", setOf("settings.json"), entries)
    }

    /**
     * 官方出口的 settings.json 必须能被官方那个版本解码。
     *
     * `BackupItem` 里有本仓库新增的枚举值，官方没有；未知枚举值不像未知字段那样能被
     * `ignoreUnknownKeys` 吞掉，会让整份设置解码失败。所以用户勾了工作区之后，官方出口的
     * settings.json 里也不能出现它。
     */
    @Test fun officialTargetSettingsStayDecodableByTheOfficialApp() = runBlocking {
        val store = GlobalContext.get().get<SettingsStore>()
        // 这里改的是进程内共享的 SettingsStore，finally 会还原；跑 test 的构建被打断时，
        // 设备上可能留在「全选」状态，只影响这台测试机。
        val original = store.settingsFlow.value.webDavConfig.items
        try {
            store.update { settings ->
                settings.copy(
                    webDavConfig = settings.webDavConfig.copy(
                        items = WebDavConfig.BackupItem.entries.toList(),
                    ),
                )
            }

            val official = manager.createBackup(
                includeDatabase = false,
                includeFiles = false,
                backupTarget = BackupTarget.OFFICIAL,
            )
            assertEquals(
                "官方出口只应保留官方认识的选项",
                listOf("DATABASE", "FILES"),
                backupItems(official),
            )

            val self = manager.createBackup(includeDatabase = false, includeFiles = false)
            assertTrue("自家的出口应保留用户勾选", backupItems(self).contains("WORKSPACES"))
        } finally {
            store.update { settings ->
                settings.copy(webDavConfig = settings.webDavConfig.copy(items = original))
            }
        }
    }

    /** 恢复时把文件搬进 live 的工作区文件区，和导出时的位置一致。 */
    @Test fun workspaceFilesLandInTheLiveWorkspace() = runBlocking {
        seedWorkspace("ws1", "notes/a.txt" to "hello")
        val archive = manager.createBackup(includeDatabase = false, includeFiles = false, includeWorkspaceFiles = true)

        workspaceLocalDir("ws1").deleteRecursively()
        manager.stageRestore(
            archive,
            includeDatabase = false,
            includeFiles = false,
            includeWorkspaceFiles = true,
        )
        liveDatabase.close()
        dropStagedSettings()
        BackupManager.applyPendingRestore(context, JsonInstant)
        liveDatabase = AppDatabaseFactory.create(context)

        val restored = workspaceLocalFile("ws1", "notes/a.txt")
        assertTrue("工作区文件应当被恢复", restored.isFile)
        assertEquals("hello", restored.readText())
    }

    /**
     * 恢复整份备份（带数据库与附件）时，工作区文件不参与附件那套替换语义。
     *
     * 这条是承重墙：替换目录清单只认四个附件目录，工作区不在其中。如果哪天有人把工作区也塞进
     * 那份清单，用户恢复一份旧备份时就会丢掉归档之后新建的工作区文件。为了证明替换真的跑了，
     * 归档里带一个附件，恢复之后再多出来的附件文件必须被删掉。
     */
    @Test fun restoringADatabaseBackupLeavesExtraWorkspaceFilesAlone() = runBlocking {
        seedWorkspace("ws1", "notes/a.txt" to "hello")
        File(context.filesDir, "upload/keep.bin").apply {
            parentFile!!.mkdirs()
            writeText("keep")
        }
        val archive = manager.createBackup(
            includeDatabase = true,
            includeFiles = true,
            includeWorkspaceFiles = true,
        )
        // 归档之后新建的文件，都不在归档里
        val staleAttachment = File(context.filesDir, "upload/stale.bin").apply { writeText("stale") }
        val extraWorkspace = workspaceLocalFile("ws1", "notes/extra.txt").apply { writeText("keep me") }

        manager.stageRestore(
            archive,
            includeDatabase = true,
            includeFiles = true,
            includeWorkspaceFiles = true,
        )
        liveDatabase.close()
        dropStagedSettings()
        BackupManager.applyPendingRestore(context, JsonInstant)
        liveDatabase = AppDatabaseFactory.create(context)

        assertTrue("归档里的工作区文件应当恢复", workspaceLocalFile("ws1", "notes/a.txt").isFile)
        assertFalse("附件替换要照常删掉归档之外的文件", staleAttachment.exists())
        assertTrue("不在归档里的工作区文件不能被删", extraWorkspace.isFile)
        assertEquals("keep me", extraWorkspace.readText())
    }

    /** 不勾选时既不打包也不恢复，恢复出来的目录保持原样。 */
    @Test fun workspaceFilesAreIgnoredWhenNotSelected() = runBlocking {
        seedWorkspace("ws1", "notes/a.txt" to "hello")
        val archive = manager.createBackup(includeDatabase = false, includeFiles = false, includeWorkspaceFiles = true)

        workspaceLocalDir("ws1").deleteRecursively()
        manager.stageRestore(archive, includeDatabase = false, includeFiles = false)

        assertFalse("没勾选就不该恢复工作区文件", workspaceLocalFile("ws1", "notes/a.txt").exists())
    }

    /** 归档里指向根文件系统的条目一律拒绝，写坏或被改过的归档不能覆盖 PRoot 根文件系统。 */
    @Test fun workspaceEntriesOutsideTheFilesAreaAreRejected() = runBlocking {
        val archive = File(directory, "workspace-rootfs.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("workspaces/ws1/linux/bin/sh"))
            zip.write("evil".toByteArray())
            zip.closeEntry()
        }

        // 没勾选工作区也要拒绝：形状校验不看选择
        for (includeWorkspaceFiles in listOf(true, false)) {
            val error = runCatching {
                manager.stageRestore(
                    archive,
                    includeDatabase = false,
                    includeFiles = false,
                    includeWorkspaceFiles = includeWorkspaceFiles,
                )
            }.exceptionOrNull()
            assertTrue("必须拒绝（勾选=$includeWorkspaceFiles），实际是 $error", error is IllegalArgumentException)
        }
        assertFalse(File(context.filesDir, "workspaces/ws1/linux/bin/sh").exists())
        assertFalse(
            "被拒绝的归档不该留下 pending",
            File(context.noBackupFilesDir, "backup-restore/pending").exists(),
        )
    }

    /**
     * 官方兼容出口把附件引用改写成官方安装的路径。
     *
     * 官方客户端恢复时只把附件搬进自己的 files 目录，不会改库里的路径，所以路径必须由我们在
     * 导出时换掉，否则装过去之后图片与文档全断。自家的出口不能改。
     */
    @Test fun officialExportPointsAttachmentsAtTheOfficialInstall() = runBlocking {
        val ours = canonicalFileUrl("upload/x.png")
        liveDatabase.openHelper.writableDatabase.execSQL(
            "INSERT INTO ConversationEntity (id, title, nodes, create_at, update_at) " +
                "VALUES ('c1', 'demo', '[]', 1, 2)",
        )
        liveDatabase.openHelper.writableDatabase.execSQL(
            "INSERT INTO message_node (id, conversation_id, node_index, messages, select_index) " +
                "VALUES ('n1', 'c1', 0, ?, 0)",
            arrayOf("""[{"type":"image","url":"$ours"}]"""),
        )

        val official = manager.createBackup(
            includeDatabase = true,
            includeFiles = false,
            backupTarget = BackupTarget.OFFICIAL,
        )
        val officialMessages = archivedMessages(official)
        assertTrue(
            "应当改写成官方包名，实际是 $officialMessages",
            officialMessages.contains("/$OFFICIAL_APPLICATION_ID/"),
        )
        assertFalse(
            "不应残留本应用的路径，实际是 $officialMessages",
            officialMessages.contains("/${context.packageName}/"),
        )

        val self = manager.createBackup(includeDatabase = true, includeFiles = false)
        val selfMessages = archivedMessages(self)
        assertTrue(
            "自家出口要保留本应用的路径，实际是 $selfMessages",
            selfMessages.contains("/${context.packageName}/"),
        )
    }

    /**
     * 官方出口的归档再恢复回本应用。
     *
     * 库里的路径现在属于官方安装，分流会把它当成外来路径交给转换器，最后必须换回本机路径；
     * 这是本批改动新触发的路径，值得钉住。
     */
    @Test fun officialArchiveRestoresBackIntoThisInstall() = runBlocking {
        liveDatabase.openHelper.writableDatabase.execSQL(
            "INSERT INTO ConversationEntity (id, title, nodes, create_at, update_at) " +
                "VALUES ('c1', 'demo', '[]', 1, 2)",
        )
        liveDatabase.openHelper.writableDatabase.execSQL(
            "INSERT INTO message_node (id, conversation_id, node_index, messages, select_index) " +
                "VALUES ('n1', 'c1', 0, ?, 0)",
            arrayOf("""[{"type":"image","url":"${canonicalFileUrl("upload/x.png")}"}]"""),
        )
        val archive = manager.createBackup(
            includeDatabase = true,
            includeFiles = false,
            backupTarget = BackupTarget.OFFICIAL,
        )
        // 先证明导出侧确实把路径换成了官方安装，再证明回灌时被换回来；否则这条用例只覆盖了后半段
        val exported = archivedMessages(archive)
        val officialUrl = "file:///data/user/0/$OFFICIAL_APPLICATION_ID/files/upload/x.png"
        assertTrue("导出时应当换成官方安装的路径，实际是 $exported", exported.contains(officialUrl))

        manager.stageRestore(archive, includeDatabase = true, includeFiles = false)

        val staged = stagedMessages()
        val ours = localFileUrl("upload/x.png")
        assertTrue("应当换回本机路径，实际是 $staged", staged.contains(ours))
        assertFalse("不应残留官方安装的路径，实际是 $staged", staged.contains("/$OFFICIAL_APPLICATION_ID/"))
    }

    /** settings 里的头像同样是绝对路径，官方出口也要换成官方安装的位置。 */
    @Test fun officialExportPointsAvatarsAtTheOfficialInstall() = runBlocking {
        val store = GlobalContext.get().get<SettingsStore>()
        val original = store.settingsFlow.value.displaySetting.userAvatar
        // settings 那条改写走的是「任何安装的绝对 files 路径」正则，所以这里按真机布局构造
        val ours = "file:///data/user/0/${context.packageName}/files/upload/avatar.png"
        try {
            store.update { settings ->
                settings.copy(displaySetting = settings.displaySetting.copy(userAvatar = Avatar.Image(ours)))
            }

            val official = manager.createBackup(
                includeDatabase = false,
                includeFiles = false,
                backupTarget = BackupTarget.OFFICIAL,
            )
            val officialSettings = archiveText(official, "settings.json")
            // 设备上存的设置里别的字段可能本来就有路径，所以只看这张头像的那一条
            val officialAvatars = avatarRefs(officialSettings)
            assertTrue("应当能找到头像引用，实际是 $officialAvatars", officialAvatars.isNotEmpty())
            assertTrue(
                "官方出口的头像路径要指向官方安装，实际是 $officialAvatars",
                officialAvatars.all { it.contains("/$OFFICIAL_APPLICATION_ID/") },
            )

            val self = manager.createBackup(includeDatabase = false, includeFiles = false)
            val selfAvatars = avatarRefs(archiveText(self, "settings.json"))
            assertTrue(
                "自家出口要保留本应用的路径，实际是 $selfAvatars",
                selfAvatars.isNotEmpty() && selfAvatars.all { it.contains("/${context.packageName}/") },
            )
        } finally {
            store.update { settings ->
                settings.copy(displaySetting = settings.displaySetting.copy(userAvatar = original))
            }
        }
    }

    /** 真机上的附件路径形态；测试的 filesDir 挂在 cache 下，导出逻辑会补上规范形态。 */
    private fun canonicalFileUrl(path: String): String =
        "file:///data/user/0/${context.packageName}/files/$path"

    /** 本安装实际的 files 目录下的路径。 */
    private fun localFileUrl(path: String): String =
        context.filesDir.toUri().toString().trimEnd('/') + "/$path"

    /** 暂存库里 `message_node.messages` 拼起来的内容。 */
    private fun stagedMessages(): String {
        val staged = File(context.noBackupFilesDir, "backup-restore/pending/payload/database/rikka_hub")
        return SQLiteDatabase.openDatabase(staged.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT messages FROM message_node", null).use { cursor ->
                buildString {
                    while (cursor.moveToNext()) append(cursor.getString(0))
                }
            }
        }
    }

    /** settings.json 里这张头像的路径引用。 */
    private fun avatarRefs(settingsJson: String): List<String> =
        Regex("""file://[^"]*upload/avatar\.png""").findAll(settingsJson).map { it.value }.toList()

    private fun seedWorkspace(root: String, vararg files: Pair<String, String>) = runBlocking {
        liveDatabase.workspaceDao().upsert(
            WorkspaceEntity(
                id = root,
                name = root,
                root = root,
                createdAt = 1,
                updatedAt = 1,
            ),
        )
        for ((path, content) in files) {
            workspaceLocalFile(root, path).apply {
                parentFile!!.mkdirs()
                writeText(content)
            }
        }
    }

    private fun workspaceDir(root: String): File =
        File(context.filesDir, "${WorkspaceManager.BASE_DIR_NAME}/$root")

    /** 归档里某个条目的文本内容。 */
    private fun archiveText(archive: File, entry: String): String = ZipFile(archive).use { zip ->
        zip.getInputStream(zip.getEntry(entry)).readBytes().decodeToString()
    }

    /** 归档里的数据库里 `message_node.messages` 拼起来的内容，用来断言附件路径被改写成什么样。 */
    private fun archivedMessages(archive: File): String {
        val database = File(directory, "archived-${archive.nameWithoutExtension}.db")
        ZipFile(archive).use { zip ->
            zip.getInputStream(zip.getEntry(DatabaseBackup.ARCHIVE_DATABASE)).use { input ->
                database.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return SQLiteDatabase.openDatabase(database.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT messages FROM message_node", null).use { cursor ->
                buildString {
                    while (cursor.moveToNext()) append(cursor.getString(0))
                }
            }
        }
    }

    private fun workspaceLocalDir(root: String): File = File(workspaceDir(root), WorkspaceManager.FILES_DIR)

    private fun workspaceLocalFile(root: String, relative: String): File =
        File(workspaceLocalDir(root), relative)

    private fun workspaceArchivePath(root: String, relative: String): String =
        "${WorkspaceManager.BASE_DIR_NAME}/$root/${WorkspaceManager.FILES_DIR}/$relative"

    /** 归档里 settings.json 的 `webDavConfig.items`，用来断言官方出口到底写了什么。 */
    private fun backupItems(archive: File): List<String> = ZipFile(archive).use { zip ->
        val settings = zip.getInputStream(zip.getEntry("settings.json")).readBytes().decodeToString()
        JsonInstant.parseToJsonElement(settings).jsonObject["webDavConfig"]!!.jsonObject["items"]!!
            .jsonArray.map { it.jsonPrimitive.content }
    }

    private fun probe(database: AppDatabase): String = database.openHelper.readableDatabase
        .query("SELECT text FROM backup_probe").use {
            check(it.moveToFirst())
            it.getString(0)
        }

    private fun addFile(zip: ZipOutputStream, name: String, file: File) {
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun <T> withRoom(file: File, block: (AppDatabase) -> T): T {
        val room = AppDatabaseFactory.create(context, file.path)
        return try {
            block(room)
        } finally {
            room.close()
        }
    }
}
