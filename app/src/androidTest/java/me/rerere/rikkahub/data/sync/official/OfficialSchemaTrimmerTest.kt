package me.rerere.rikkahub.data.sync.official

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.db.AppDatabaseFactory
import me.rerere.rikkahub.data.db.entity.UsageOutcome
import me.rerere.rikkahub.data.db.entity.UsageRecordEntity
import me.rerere.rikkahub.data.sync.DatabaseBackup
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class OfficialSchemaTrimmerTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val directory = File(context.cacheDir, "official-schema-trimmer-test")

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun trimsUsageOutcomeAndKeepsRows() = runBlocking {
        directory.mkdirs()
        val source = File(directory, "source.db")
        val target = File(directory, "target.db")
        val room = AppDatabaseFactory.create(context, source.absolutePath)
        try {
            val dao = room.usageRecordDao()
            dao.insert(usage(createdAt = 1, outcome = UsageOutcome.SUCCESS, prompt = 10, cached = 2))
            dao.insert(usage(createdAt = 2, outcome = UsageOutcome.FAILED, prompt = 5, cached = 1))
            DatabaseBackup.checkpoint(room.openHelper.writableDatabase)
        } finally {
            room.close()
        }

        OfficialSchemaTrimmer.trim(source, target)

        SQLiteDatabase.openDatabase(target.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            assertEquals(32, db.version)
            assertEquals(OfficialDatabaseSchema.IDENTITY_HASH, OfficialDatabaseHeaders.readIdentityHash(db))
            val columns = db.rawQuery("PRAGMA table_info(usage_record)", null).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(1)) }
            }
            assertFalse("裁剪后不应保留 outcome", "outcome" in columns)
            db.rawQuery("SELECT COUNT(*), SUM(prompt_tokens), SUM(cached_tokens) FROM usage_record", null).use {
                it.moveToFirst()
                assertEquals(2, it.getInt(0))
                assertEquals(15, it.getInt(1))
                assertEquals(3, it.getInt(2))
            }
        }

        OfficialDatabaseReader.open(target).use { reader ->
            assertEquals(2, reader.rows("usage_record").count())
        }
    }

    private fun usage(
        createdAt: Long,
        outcome: UsageOutcome,
        prompt: Int,
        cached: Int,
    ) = UsageRecordEntity(
        createdAt = createdAt,
        source = "CHAT",
        providerId = null,
        modelId = null,
        modelName = null,
        assistantId = null,
        conversationId = null,
        promptTokens = prompt,
        completionTokens = 1,
        cachedTokens = cached,
        outcome = outcome.name,
    )

    @Test
    fun rejectsExtraColumnsThatAreNotRegistered() = runBlocking {
        directory.mkdirs()
        val source = File(directory, "unregistered.db")
        val room = AppDatabaseFactory.create(context, source.absolutePath)
        try {
            room.openHelper.writableDatabase.execSQL(
                "ALTER TABLE workspaces ADD COLUMN future_column TEXT"
            )
            DatabaseBackup.checkpoint(room.openHelper.writableDatabase)
        } finally {
            room.close()
        }

        val error = runCatching {
            OfficialDatabaseAdapter.adaptForOfficial(source, emptyList())
        }.exceptionOrNull()

        assertTrue(
            "未登记的额外列必须让官方导出失败，实际是 $error",
            error is OfficialExportUnsupportedException,
        )
    }
}
