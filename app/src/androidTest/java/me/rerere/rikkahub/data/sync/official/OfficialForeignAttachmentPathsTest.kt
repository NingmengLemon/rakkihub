package me.rerere.rikkahub.data.sync.official

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * `OfficialForeignAttachmentPaths` 的判据：文本列里只要有一处不属于当前安装的绝对路径就算命中。
 *
 * 探测走真实 SQLite，和恢复时一样按快照的列扫描；这里只造 `message_node`，其余表缺失时
 * 探测会跳过，不影响这条判据。
 */
@RunWith(AndroidJUnit4::class)
class OfficialForeignAttachmentPathsTest {
    private lateinit var database: SQLiteDatabase
    private lateinit var file: File

    private val ourPrefix = "file:///data/user/0/top.lemoneko.rakkihub.debug/files/"
    private val ours = "file:///data/user/0/top.lemoneko.rakkihub.debug/files/upload/ours.png"
    private val foreign = "file:///data/user/0/me.rerere.rikkahub/files/upload/foreign.png"

    @Before fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        file = File(context.cacheDir, "foreign-paths-${UUID.randomUUID()}.db")
        database = SQLiteDatabase.openOrCreateDatabase(file, null)
        database.execSQL(
            "CREATE TABLE message_node (id TEXT, conversation_id TEXT, node_index INTEGER, messages TEXT, select_index INTEGER)"
        )
        database.version = OfficialDatabaseSchema.VERSION
    }

    @After fun tearDown() {
        database.close()
        file.delete()
    }

    @Test fun reportsForeignReference() {
        insert("""[{"type":"image","url":"$foreign"}]""")
        assertTrue(OfficialForeignAttachmentPaths.hasForeign(database, ourPrefix))
    }

    @Test fun ignoresReferenceOfTheCurrentInstall() {
        insert("""[{"type":"image","url":"$ours"}]""")
        assertFalse(OfficialForeignAttachmentPaths.hasForeign(database, ourPrefix))
    }

    @Test fun reportsForeignReferenceInACellThatAlsoHoldsOneOfOurs() {
        insert("""[{"type":"image","url":"$ours"},{"type":"image","url":"$foreign"}]""")
        assertTrue(OfficialForeignAttachmentPaths.hasForeign(database, ourPrefix))
    }

    @Test fun ignoresTextWithoutAnyFileUri() {
        insert("""[{"type":"text","text":"no attachment here"}]""")
        assertFalse(OfficialForeignAttachmentPaths.hasForeign(database, ourPrefix))
    }

    private fun insert(messages: String) {
        database.execSQL("INSERT INTO message_node (conversation_id, messages) VALUES ('c', ?)", arrayOf(messages))
    }
}
