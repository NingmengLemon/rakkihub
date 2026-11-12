package me.rerere.rikkahub.data.db.migrations

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import me.rerere.rikkahub.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration_32_33_Test {
    private val testDb = "migration-32-33-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        RequerySQLiteOpenHelperFactory(),
    )

    @Test
    fun existingUsageRowsDefaultToUnknownAndNewRowsUseTheDefault() {
        helper.createDatabase(testDb, 32).apply {
            execSQL(
                "INSERT INTO usage_record " +
                    "(created_at, source, prompt_tokens, completion_tokens, cached_tokens) " +
                    "VALUES (1000, 'CHAT', 10, 2, 1)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 33, true, Migration_32_33)

        db.execSQL(
            "INSERT INTO usage_record " +
                "(created_at, source, prompt_tokens, completion_tokens, cached_tokens) " +
                "VALUES (2000, 'CHAT', 1, 1, 0)"
        )

        val outcomes = db.query("SELECT outcome FROM usage_record ORDER BY created_at").use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
        db.close()

        assertEquals(listOf("UNKNOWN", "UNKNOWN"), outcomes)
    }
}
