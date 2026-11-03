package me.rerere.rikkahub.data.sync

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.AppDatabaseFactory
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File
import java.nio.file.Files

/**
 * 恢复之后的判据：live 的附件目录里不能留着归档之外的文件。恢复是「装上归档里的」，不是「叠加」；
 * 旧文件留在那里会占空间，也会让同步把它们重新登记回来。
 *
 * 这个用例只覆盖替换路径下的附件结果：不验数据库内容，也不验设置恢复。settings.json 在 apply
 * 之前删掉，因为设置存储是进程级单例，写进去会覆盖这台设备上真实安装的设置。
 */
@RunWith(AndroidJUnit4::class)
class RestoreAppliedOutcomeTest {
    private lateinit var directory: File
    private lateinit var context: Context
    private lateinit var liveDatabase: AppDatabase
    private lateinit var manager: BackupManager

    private val sampleZip = File("/data/local/tmp/rikkahub-sample-v32.zip")
    private val expectations = File("/data/local/tmp/backup-expectations-v32.json")

    @Before fun setUp() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        directory = Files.createTempDirectory(app.cacheDir.toPath(), "restore-outcome-").toFile()
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
        manager = BackupManager(context, liveDatabase, GlobalContext.get().get<SettingsStore>(), JsonInstant)
    }

    /** `upload/` 下的附件导入时按内容改名，期望值要跟着换。 */
    private fun stagedPath(attachment: JsonObject): String {
        val path = attachment.getValue("path").jsonPrimitive.content
        if (!path.startsWith("upload/")) return path
        val hash = attachment.getValue("sha256").jsonPrimitive.content
        return "upload/" + ImportedUploadFiles.contentName(hash, path.removePrefix("upload/"))
    }

    @After fun tearDown() {
        liveDatabase.close()
        directory.deleteRecursively()
    }

    @Test fun liveAttachmentsBecomeExactlyTheArchiveSet() = runBlocking {
        assumeTrue("没有放整份备份样本，跳过", sampleZip.isFile && expectations.isFile)
        val expected = Json.parseToJsonElement(expectations.readText()).jsonObject
        val archivePaths = expected.getValue("attachments").jsonArray
            .map { it.jsonObject }
            .map { stagedPath(it) }
            .toSet()

        // 先放一个归档里没有的文件，模拟「上次装的东西还留着」
        val stale = File(context.filesDir, "upload/stale-from-previous-install.txt")
        stale.parentFile?.mkdirs()
        stale.writeText("should be removed by restore")

        manager.stageRestore(sampleZip, includeFiles = true)
        // 设置存储是进程级单例，pending 里的 settings.json 会被写进这台设备的真实设置；
        // 样本的设置不属于它，先删掉。数据库与附件仍然从同一个 pending 目录装。
        val pendingSettings = File(
            context.noBackupFilesDir,
            "backup-restore/pending/${BackupManager.SETTINGS_ENTRY}",
        )
        assertTrue("样本的 settings.json 应当已经暂存", pendingSettings.delete())
        val restored = BackupManager.applyPendingRestore(context, JsonInstant)
        assertTrue("应当真的恢复了东西", restored)

        val livePaths = File(context.filesDir, "upload").walkTopDown()
            .filter { it.isFile }
            .map { "upload/" + it.relativeTo(File(context.filesDir, "upload")).invariantSeparatorsPath }
            .toSet()
        assertEquals("live 的附件应当正好是归档那一份", archivePaths, livePaths)
        assertTrue("上一次装的残留文件应当被清掉", !stale.exists())
    }
}
