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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.AppDatabaseFactory
import me.rerere.rikkahub.data.sync.official.OfficialDatabaseHeaders
import me.rerere.rikkahub.data.sync.official.OfficialDatabaseSchema
import me.rerere.rikkahub.utils.JsonInstant
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
     * 官方兼容出口的核心安全属性：双方 schema 一致时原样产出，分叉时直接失败。
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
        // Applying the staged settings would write to the real settings store of the app under test.
        assertTrue(File(context.noBackupFilesDir, "backup-restore/pending/settings.json").delete())
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
            val ours = context.filesDir.toUri().toString().trimEnd('/') + "/upload/abc.png"
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
        // 根文件系统与临时目录都是工作区目录下的兄弟目录，不能被打包
        File(context.filesDir, "workspaces/ws1/linux/bin").apply { mkdirs() }.resolve("sh").writeText("rootfs")
        File(context.filesDir, "workspaces/ws1/temp").apply { mkdirs() }.resolve("scratch").writeText("tmp")

        val archive = manager.createBackup(includeDatabase = false, includeFiles = false, includeWorkspaceFiles = true)
        ZipFile(archive).use { zip ->
            assertTrue(zip.getEntry("workspaces/ws1/files/notes/a.txt") != null)
            assertTrue(zip.getEntry("workspaces/ws1/files/empty/.keep") != null)
            assertFalse(
                "根文件系统不该进归档",
                zip.entries().asSequence().any { it.name.contains("/linux/") },
            )
            assertFalse(
                "临时目录不该进归档",
                zip.entries().asSequence().any { it.name.contains("/temp/") },
            )
        }
    }

    /** 恢复时把文件搬进 live 的工作区文件区，和导出时的位置一致。 */
    @Test fun workspaceFilesLandInTheLiveWorkspace() = runBlocking {
        seedWorkspace("ws1", "notes/a.txt" to "hello")
        val archive = manager.createBackup(includeDatabase = false, includeFiles = false, includeWorkspaceFiles = true)

        File(context.filesDir, "workspaces/ws1/files").deleteRecursively()
        manager.stageRestore(
            archive,
            includeDatabase = false,
            includeFiles = false,
            includeWorkspaceFiles = true,
        )
        liveDatabase.close()
        BackupManager.applyPendingRestore(context, JsonInstant)
        liveDatabase = AppDatabaseFactory.create(context)

        val restored = File(context.filesDir, "workspaces/ws1/files/notes/a.txt")
        assertTrue("工作区文件应当被恢复", restored.isFile)
        assertEquals("hello", restored.readText())
    }

    /** 不勾选时既不打包也不恢复，恢复出来的目录保持原样。 */
    @Test fun workspaceFilesAreIgnoredWhenNotSelected() = runBlocking {
        seedWorkspace("ws1", "notes/a.txt" to "hello")
        val archive = manager.createBackup(includeDatabase = false, includeFiles = false, includeWorkspaceFiles = true)

        File(context.filesDir, "workspaces/ws1/files").deleteRecursively()
        manager.stageRestore(archive, includeDatabase = false, includeFiles = false)

        assertFalse(
            "没勾选就不该恢复工作区文件",
            File(context.filesDir, "workspaces/ws1/files/notes/a.txt").exists(),
        )
    }

    /** 归档里指向根文件系统的条目一律拒绝，写坏或被改过的归档不能覆盖 PRoot 根文件系统。 */
    @Test fun workspaceEntriesOutsideTheFilesAreaAreRejected() = runBlocking {
        val archive = File(directory, "workspace-rootfs.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("workspaces/ws1/linux/bin/sh"))
            zip.write("evil".toByteArray())
            zip.closeEntry()
        }

        val error = runCatching {
            manager.stageRestore(
                archive,
                includeDatabase = false,
                includeFiles = false,
                includeWorkspaceFiles = true,
            )
        }.exceptionOrNull()

        assertTrue("必须拒绝，实际是 $error", error is IllegalArgumentException)
        assertFalse(File(context.filesDir, "workspaces/ws1/linux/bin/sh").exists())
    }

    private fun seedWorkspace(root: String, vararg files: Pair<String, String>) = runBlocking {
        liveDatabase.workspaceDao().upsert(
            me.rerere.rikkahub.data.db.entity.WorkspaceEntity(
                id = root,
                name = root,
                root = root,
                createdAt = 1,
                updatedAt = 1,
            ),
        )
        val filesDir = File(context.filesDir, "workspaces/$root/files").apply { mkdirs() }
        for ((path, content) in files) {
            val target = File(filesDir, path)
            target.parentFile!!.mkdirs()
            target.writeText(content)
        }
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
