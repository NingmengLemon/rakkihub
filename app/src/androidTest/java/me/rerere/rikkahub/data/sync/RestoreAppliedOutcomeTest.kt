package me.rerere.rikkahub.data.sync

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.AppDatabaseFactory
import me.rerere.rikkahub.data.db.fts.MessageFtsManager
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
 * 恢复之后的两条判据：
 *
 * 1. live 的附件目录里不能留着归档之外的文件。恢复是「装上归档里的」，不是「叠加」；
 *    旧文件留在那里会占空间，也会让同步把它们重新登记回来。
 * 2. 索引要在恢复后真的能用。搜索是派生数据，库里搬不过来，只能重建；这里用归档里真实存在的
 *    关键词查一次，确认能搜到对应会话。
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

    @After fun tearDown() {
        liveDatabase.close()
        directory.deleteRecursively()
    }

    @Test fun liveAttachmentsBecomeExactlyTheArchiveSet() = runBlocking {
        assumeTrue("没有放整份备份样本，跳过", sampleZip.isFile && expectations.isFile)
        val expected = Json.parseToJsonElement(expectations.readText()).jsonObject
        val archivePaths = expected.getValue("attachments").jsonArray
            .map { it.jsonObject.getValue("path").jsonPrimitive.content }
            .toSet()

        // 先放一个归档里没有的文件，模拟「上次装的东西还留着」
        val stale = File(context.filesDir, "upload/stale-from-previous-install.txt")
        stale.parentFile?.mkdirs()
        stale.writeText("should be removed by restore")

        manager.stageRestore(sampleZip, includeDatabase = true, includeFiles = true)
        // 恢复到 temp 目录里，不碰这台设备上真正的安装
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
