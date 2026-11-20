package me.rerere.rikkahub.data.db.migrations

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import me.rerere.rikkahub.data.db.AppDatabase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration_34_35_Test {
    private val testDb = "migration-34-35-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        RequerySQLiteOpenHelperFactory(),
    )

    @Test
    fun replacesConversationIndexWithCompositeIndex() {
        helper.createDatabase(testDb, 34).close()

        val db = helper.runMigrationsAndValidate(testDb, 35, true, Migration_34_35)
        val indexes = db.query("PRAGMA index_list(`message_node`)").use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(1))
            }
        }

        assertTrue(indexes.contains("index_message_node_conversation_id_node_index"))
        assertFalse(indexes.contains("index_message_node_conversation_id"))
        db.close()
    }
}
