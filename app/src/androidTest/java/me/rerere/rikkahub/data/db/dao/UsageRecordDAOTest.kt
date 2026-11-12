package me.rerere.rikkahub.data.db.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.entity.UsageOutcome
import me.rerere.rikkahub.data.db.entity.UsageRecordEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class UsageRecordDAOTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: UsageRecordDAO

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).openHelperFactory(RequerySQLiteOpenHelperFactory()).build()
        dao = database.usageRecordDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun requestsAreCountedPerLocalDayForTheSourceSinceTheStart() = runBlocking {
        insert("2026-08-11T23:00:00", "CHAT")
        insert("2026-08-12T00:30:00", "CHAT")
        insert("2026-08-12T23:59:00", "CHAT")
        insert("2026-08-12T12:00:00", "TITLE")
        insert("2026-08-13T08:00:00", "CHAT")

        val counts = dao.getRequestCountPerDay("CHAT", epochMillis("2026-08-12T00:00:00"))

        assertEquals(mapOf("2026-08-12" to 2, "2026-08-13" to 1), counts.associate { it.day to it.count })
    }

    @Test
    fun totalsKeepFailedCostButExcludeItFromCacheRate() = runBlocking {
        insert("2026-08-12T00:00:00", "CHAT", UsageOutcome.SUCCESS, prompt = 10, cached = 2)
        insert("2026-08-12T00:01:00", "CHAT", UsageOutcome.FAILED, prompt = 10, cached = 1)
        insert("2026-08-12T00:02:00", "CHAT", UsageOutcome.CANCELLED, prompt = 10, cached = 1)
        insert("2026-08-12T00:03:00", "CHAT", UsageOutcome.UNKNOWN, prompt = 10, cached = 1)

        val totals = dao.getTotals(epochMillis("2026-08-12T00:00:00"))

        assertEquals(40, totals.promptTokens)
        assertEquals(5, totals.cachedTokens)
        // 成功与未知计入缓存率，FAILED 和 CANCELLED 只保留成本
        assertEquals(20, totals.cachePromptTokens)
        assertEquals(3, totals.cacheCachedTokens)
    }

    private suspend fun insert(
        localDateTime: String,
        source: String,
        outcome: UsageOutcome = UsageOutcome.UNKNOWN,
        prompt: Int = 10,
        cached: Int = 0,
    ) {
        dao.insert(
            UsageRecordEntity(
                createdAt = epochMillis(localDateTime),
                source = source,
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
        )
    }

    private fun epochMillis(localDateTime: String): Long =
        LocalDateTime.parse(localDateTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
