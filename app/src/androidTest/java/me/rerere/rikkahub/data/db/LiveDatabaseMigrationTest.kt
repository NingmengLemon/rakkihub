package me.rerere.rikkahub.data.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveDatabaseMigrationTest {
    @Test
    fun liveDatabaseMigratesToUsageOutcomeSchema(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = AppDatabaseFactory.create(context)
        try {
            assertEquals(33, database.openHelper.writableDatabase.version)
            // 旧 usage 行的默认值由 Migration_32_33_Test 覆盖；这里只验证真实 app 库能迁移并读出
            database.usageRecordDao().getTotals(0)
        } finally {
            database.close()
        }
    }
}
