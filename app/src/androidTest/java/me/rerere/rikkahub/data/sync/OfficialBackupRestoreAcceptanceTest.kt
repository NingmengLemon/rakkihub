package me.rerere.rikkahub.data.sync

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
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
import java.security.MessageDigest

/**
 * 整份备份的验收：附件与 settings 都要真的落地。
 *
 * 表数据的验收在 `OfficialSampleAcceptanceTest`；这里补的是另一条判据——附件文件集合与内容、
 * 以及 settings 的关键字段。两者都不在某张表里，读库读不到，只能从解压结果看。
 *
 * 样本取自真实备份（含私人数据），放仓库外、设备上，缺了就跳过。
 */
@RunWith(AndroidJUnit4::class)
class OfficialBackupRestoreAcceptanceTest {
    private lateinit var directory: File
    private lateinit var context: Context
    private lateinit var liveDatabase: AppDatabase
    private lateinit var manager: BackupManager

    private val sampleZip = File("/data/local/tmp/rikkahub-sample-v32.zip")
    private val expectations = File("/data/local/tmp/backup-expectations-v32.json")

    @Before fun setUp() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        directory = Files.createTempDirectory(app.cacheDir.toPath(), "restore-acceptance-").toFile()
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

    @Test fun attachmentsAndSettingsLandAsDeclaredInTheArchive() = runBlocking {
        assumeTrue("没有放整份备份样本，跳过", sampleZip.isFile && expectations.isFile)
        val expected = Json.parseToJsonElement(expectations.readText()).jsonObject

        manager.stageRestore(sampleZip, includeFiles = true)

        val payload = File(context.noBackupFilesDir, "backup-restore/pending/payload")
        // 附件装在 payload/files 下，与 filesDir 的布局一致
        val files = File(payload, "files")
        val attachments = expected.getValue("attachments").jsonArray.map { it.jsonObject }
        assertTrue("样本里应当有附件", attachments.isNotEmpty())
        for (entry in attachments) {
            val path = stagedPath(entry)
            val file = File(files, path)
            assertTrue("附件缺失：$path", file.isFile)
            assertEquals("附件大小：$path", entry.getValue("size").jsonPrimitive.long, file.length())
            assertEquals("附件内容：$path", entry.getValue("sha256").jsonPrimitive.content, sha256(file))
        }
        // 反过来也要成立：解压出来的附件不多不少
        val staged = files.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(files).invariantSeparatorsPath }
            .filter { rel -> ATTACHMENT_FOLDERS.any { rel.startsWith("$it/") } }
            .toSet()
        assertEquals("附件集合要与归档一致", attachments.map { stagedPath(it) }.toSet(), staged)

        val settingsFile = File(context.noBackupFilesDir, "backup-restore/pending/" + BackupManager.SETTINGS_ENTRY)
        assertTrue("settings 应当落地", settingsFile.isFile)
    }

    /**
     * settings 的条数也要原样保留。
     *
     * 期望值必须从**同一份归档**里取：之前我拿另一份样本的 settings.json 去算期望值，
     * 得到 11 个助手、37 个服务商，而这份归档本身只有 5 与 29，于是看起来像是导入丢了东西。
     * 结论是处理链没问题，是期望值的来源错了。
     */
    @Test
    fun settingsEntriesSurviveTheStagingPipeline() = runBlocking {
        assumeTrue("没有放整份备份样本，跳过", sampleZip.isFile && expectations.isFile)
        val expected = Json.parseToJsonElement(expectations.readText()).jsonObject
        manager.stageRestore(sampleZip, includeFiles = true)
        val settingsFile = File(context.noBackupFilesDir, "backup-restore/pending/" + BackupManager.SETTINGS_ENTRY)
        val settings = Json.parseToJsonElement(settingsFile.readText()).jsonObject
        val expectedSettings = expected.getValue("settings").jsonObject
        assertEquals(
            "助手数量",
            expectedSettings.getValue("assistants").jsonPrimitive.int,
            (settings["assistants"]?.jsonArray)?.size,
        )
        assertEquals(
            "服务商数量",
            expectedSettings.getValue("providers").jsonPrimitive.int,
            (settings["providers"]?.jsonArray)?.size,
        )
        // 条数对不代表条目对：id 集合、当前助手与当前模型都要原样保留
        assertEquals(
            "助手 id 集合",
            expectedSettings.getValue("assistantIds").jsonArray.map { it.jsonPrimitive.content }.toSet(),
            settings["assistants"]?.jsonArray.orEmpty()
                .map { it.jsonObject.getValue("id").jsonPrimitive.content }.toSet(),
        )
        assertEquals(
            "服务商 id 集合",
            expectedSettings.getValue("providerIds").jsonArray.map { it.jsonPrimitive.content }.toSet(),
            settings["providers"]?.jsonArray.orEmpty()
                .map { it.jsonObject.getValue("id").jsonPrimitive.content }.toSet(),
        )
        assertEquals(
            "当前助手",
            expectedSettings.getValue("assistantId").jsonPrimitive.content,
            settings.getValue("assistantId").jsonPrimitive.content,
        )
        assertEquals(
            "当前模型",
            expectedSettings.getValue("chatModelId").jsonPrimitive.content,
            settings.getValue("chatModelId").jsonPrimitive.content,
        )
    }

    /** `upload/` 下的附件导入时按内容改名，期望值要跟着换；其它目录原样。 */
    private fun stagedPath(attachment: JsonObject): String {
        val path = attachment.getValue("path").jsonPrimitive.content
        if (!path.startsWith("upload/")) return path
        val hash = attachment.getValue("sha256").jsonPrimitive.content
        return "upload/" + ImportedUploadFiles.contentName(hash, path.removePrefix("upload/"))
    }
    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        val ATTACHMENT_FOLDERS = listOf("upload", "skills", "fonts", "media_creation")
    }
}
