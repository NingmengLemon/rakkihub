package me.rerere.rikkahub.data.sync.official

import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.rerere.rikkahub.data.db.AppDatabaseFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 读取层的正向与负向验证。
 *
 * 正向用一个结构等同官方 v32 的库（由应用自己创建，它的 schema 与官方快照一致），负向用临时
 * 改坏的库。真正的官方备份样本要等拿到之后补进来。
 */
@RunWith(AndroidJUnit4::class)
class OfficialDatabaseReaderTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val workspace get() = File(context.cacheDir, "official-reader-test")

    @After fun tearDown() {
        workspace.deleteRecursively()
    }

    @Test fun readsEveryTableOfTheAppDatabase() {
        val file = appDatabase("positive.db") {
            it.execSQL(
                "INSERT INTO workspaces (id, name, root, shell_status, created_at, updated_at, type) " +
                    "VALUES ('w1', 'demo', '/tmp/demo', 'READY', 7, 8, 'LOCAL')"
            )
        }

        OfficialDatabaseReader.open(file).use { reader ->
            assertTrue("应用库不该有快照之外的列", reader.unexpectedColumns.isEmpty())
            for (table in OfficialDatabaseSchema.TABLES.keys) {
                // 空库是常态，只有 workspaces 里有上面那一行
                val expected = if (table == "workspaces") 1 else 0
                assertEquals("表 $table 的行数", expected, reader.rows(table).count())
            }
        }
    }

    @Test fun rowValuesMatchTheSnapshotAffinity() {
        val file = appDatabase("values.db") {
            it.execSQL(
                "INSERT INTO workspaces (id, name, root, shell_status, created_at, updated_at, type) " +
                    "VALUES ('w1', 'demo', '/tmp/demo', 'READY', 7, 8, 'LOCAL')"
            )
        }

        OfficialDatabaseReader.open(file).use { reader ->
            val row = reader.rows("workspaces").single()
            assertEquals("w1", row.string("id"))
            assertEquals("/tmp/demo", row.string("root"))
            assertEquals("LOCAL", row.string("type"))
            assertEquals(7L, row.long("created_at"))
        }
    }

    @Test
    fun failsWhenTheSnapshotTableIsMissing() {
        val file = rawDatabase("missing-table.db")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
            // 版本先于表检查，fixture 要把版本设对，否则命中的是版本分支
            database.version = OfficialDatabaseSchema.VERSION
            database.execSQL("CREATE TABLE unrelated (id TEXT)")
        }
        val error = runCatching { OfficialDatabaseReader.open(file) }.exceptionOrNull()
        assertTrue("缺表必须失败，实际是 $error", error is IllegalStateException)
        assertTrue("错误信息要指出缺表，实际是 ${error?.message}", error?.message.orEmpty().contains("没有表"))
    }

    @Test
    fun failsWhenAColumnIsMissing() {
        val file = rawDatabase("missing-column.db")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
            database.version = OfficialDatabaseSchema.VERSION
            // 只建一张缺列的 ConversationEntity，遍历顺序里它排在第一位，所以命中的是缺列分支
            database.execSQL("CREATE TABLE ConversationEntity (id TEXT PRIMARY KEY)")
        }
        val error = runCatching { OfficialDatabaseReader.open(file) }.exceptionOrNull()
        assertTrue("缺列必须失败，实际是 $error", error is IllegalStateException)
        assertTrue("错误信息要指出缺列，实际是 ${error?.message}", error?.message.orEmpty().contains("缺少列"))
    }

    @Test
    fun failsWhenTheSchemaVersionIsUnknown() {
        val file = appDatabase("version.db") {}
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { database ->
            database.version = 34
        }
        val error = runCatching { OfficialDatabaseReader.open(file) }.exceptionOrNull()
        // 未知版本没有快照可读，读取层拒绝；分流要在更早的地方就把它挡下来
        assertTrue("未知版本必须失败，实际是 $error", error is IllegalArgumentException)
        assertTrue(
            "错误信息要指出未知版本，实际是 ${error?.message}",
            error?.message.orEmpty().contains("未知的官方版本"),
        )
    }

    @Test
    fun reportsColumnsTheSnapshotDoesNotKnow() {
        val file = appDatabase("extra-column.db") {
            it.execSQL("ALTER TABLE workspaces ADD COLUMN future_column TEXT")
        }
        OfficialDatabaseReader.open(file).use { reader ->
            assertEquals(listOf("future_column"), reader.unexpectedColumns["workspaces"])
            // 多出来的列不影响读取
            assertTrue(reader.rows("workspaces").count() >= 0)
        }
    }

    /**
     * 读一份真实的官方 v32 备份里的库。
     *
     * 样本不在仓库里，也不进 APK：测试从设备上的固定路径读，没有就跳过（CI 上不会因此失败）。
     * 路径用 `adb push` 放进去，见 `docs/custom/backup/acceptance.md`。
     */
    @Test fun readsTheProvidedOfficialSample() {
        val sample = File("/data/local/tmp/rikkahub-sample-v32.db")
        assumeTrue("没有放官方样本，跳过", sample.isFile)

        OfficialDatabaseReader.open(sample).use { reader ->
            assertTrue("官方 v32 样本不该有快照之外的列", reader.unexpectedColumns.isEmpty())
            assertEquals(168, reader.rows("ConversationEntity").count())
            assertEquals(673, reader.rows("message_node").count())
            assertEquals(97, reader.rows("managed_files").count())
            assertEquals(476, reader.rows("usage_record").count())

            // 附件引用是绝对 file:// URI，跨安装迁移时必须重写前缀
            val attachmentRefs = reader.rows("message_node").count { row ->
                row.string("messages")?.contains("file://") == true
            }
            assertTrue("样本里应当有指向 upload 的附件引用", attachmentRefs > 0)
        }
    }

    /** 用应用自己的 schema 建一个库，可选地往里写点数据 */
    private fun appDatabase(name: String, prepare: (SupportSQLiteDatabase) -> Unit = {}): File {
        val file = rawDatabase(name)
        val room = AppDatabaseFactory.create(context, file.absolutePath)
        try {
            prepare(room.openHelper.writableDatabase)
        } finally {
            room.close()
        }
        return file
    }

    private fun rawDatabase(name: String): File {
        workspace.mkdirs()
        val file = File(workspace, name)
        file.delete()
        File(file.path + "-wal").delete()
        File(file.path + "-shm").delete()
        File(file.path + "-journal").delete()
        return file
    }
}
