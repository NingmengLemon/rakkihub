package me.rerere.rikkahub.data.db.migrations

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val TAG = "Migration_32_33"

/** 给 usage_record 增加 outcome；无法判断历史尝试结果的旧行按 UNKNOWN 处理，统计仍计入成本。 */
val Migration_32_33 = object : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Log.i(TAG, "migrate: start migrate from 32 to 33 (usage outcome)")
        db.execSQL(
            "ALTER TABLE `usage_record` ADD COLUMN `outcome` TEXT NOT NULL DEFAULT 'UNKNOWN'"
        )
        Log.i(TAG, "migrate: migrate from 32 to 33 success")
    }
}
