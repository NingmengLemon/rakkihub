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
class Migration_33_34_Test {
    private val testDb = "migration-33-34-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        RequerySQLiteOpenHelperFactory(),
    )

    @Test
    fun createsDerivationTableAndKeepsManagedFiles() {
        helper.createDatabase(testDb, 33).apply {
            execSQL(
                "INSERT INTO managed_files " +
                    "(folder, relative_path, display_name, mime_type, size_bytes, created_at, updated_at) " +
                    "VALUES ('upload', 'upload/H1.png', 'H1.png', 'image/png', 3, 1000, 1000)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 34, true)

        db.execSQL(
            "INSERT INTO file_derivations " +
                "(file_id, kind, text, producer, edited, created_at, updated_at) " +
                "VALUES (1, 'ocr', 'hello', 'ocr:v1', 0, 2000, 2000)"
        )

        val rows = db.query(
            "SELECT file_id, kind, text, producer FROM file_derivations"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(listOf(cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getString(3)))
                }
            }
        }
        assertEquals(listOf(listOf(1L, "ocr", "hello", "ocr:v1")), rows)

        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("DELETE FROM managed_files WHERE id = 1")
        val remaining = db.query("SELECT COUNT(*) FROM file_derivations").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
        assertEquals(0, remaining)
        val tableCount = db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'file_derivations'"
        ).use { cursor -> if (cursor.moveToFirst()) 1 else 0 }
        assertEquals(1, tableCount)
        db.close()
    }
}
