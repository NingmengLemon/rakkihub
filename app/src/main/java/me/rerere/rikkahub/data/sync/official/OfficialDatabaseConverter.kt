package me.rerere.rikkahub.data.sync.official

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.net.toUri
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.db.AppDatabaseFactory
import me.rerere.rikkahub.data.sync.DatabaseBackup
import java.io.File

/**
 * 把官方库转换成当前版本的空库。
 *
 * 用于「版本相同但结构不同」的官方库，以及 v33 自家库带外来附件路径的恢复：前者 Room 的
 * identity 校验会拒绝，后者迁移链不会改写路径，所以要用读取层按官方快照读出来、再写进
 * 一个新建的当前版本库。v33 源库的 usage outcome 在写完后按 id 补回。转换完交给现有的
 * `stageRestore` 流程继续走。
 *
 * 只转换，不安装：目标文件写在 staging 下，由调用方决定怎么用。
 */
internal class OfficialDatabaseConverter(
    private val context: Context,
) {

    /**
     * @param source 备份里解压出来的官方库
     * @param target 转换结果，必须是不存在的路径（[AppDatabaseFactory] 会创建它）
     */
    suspend fun convert(source: File, target: File) = withContext(Dispatchers.IO) {
        val filesUriPrefix = context.filesDir.toUri().toString().trimEnd('/') + "/"
        OfficialDatabaseReader.open(source).use { reader ->
            val room = AppDatabaseFactory.create(context, target.absolutePath)
            try {
                val database = room.openHelper.writableDatabase
                requireTargetCoversSnapshot(database, reader.tables)
                OfficialDatabaseWriter(database, filesUriPrefix).write(reader)
                preserveUsageOutcome(source, database)
                // WAL 里可能还压着内容，先合进主文件再关，否则留下非空的 -wal 会让
                // PendingRestore 对同一路径生成两条 entry
                DatabaseBackup.checkpoint(database)
            } finally {
                room.close()
            }
        }
        DatabaseBackup.removeSidecars(target)
    }

    /**
     * 转换器按官方 v32 列写库，当前 Room 33 的 `usage_record.outcome` 不在官方快照里，
     * 写完后会回默认 UNKNOWN。v33 自家库带外来附件路径时也会走这条转换器，所以要把
     * outcome 按 id 从源库补回去；官方 v32 源没有这一列，跳过。
     */
    private fun preserveUsageOutcome(source: java.io.File, target: SupportSQLiteDatabase) {
        val outcomes = SQLiteDatabase.openDatabase(
            source.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        ).use { db ->
            val columns = db.rawQuery("PRAGMA table_info(usage_record)", null).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(1)) }
            }
            if ("outcome" !in columns) {
                emptyList()
            } else {
                db.rawQuery("SELECT id, outcome FROM usage_record", null).use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) add(cursor.getLong(0) to cursor.getString(1))
                    }
                }
            }
        }
        for ((id, outcome) in outcomes) {
            target.execSQL(
                "UPDATE usage_record SET outcome = ? WHERE id = ?",
                arrayOf(outcome, id),
            )
        }
    }

    /**
     * 快照里有、目标库没有的表或列一律拒绝，不要等到插入时才报错。
     *
     * 官方升版通常就是加列（v31 到 v32 加了 `workspaces.type` 与 `workspaces.ssh_config`），
     * 分叉之后我们这边删表或改表名也会走到这里；两种情况都该明确失败，而不是写一半再崩。
     */
    private fun requireTargetCoversSnapshot(
        database: SupportSQLiteDatabase,
        tables: Map<String, OfficialSchemaTypes.Table>,
    ) {
        for ((name, table) in tables) {
            val live = database.query("PRAGMA table_info(`$name`)").use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(1)) }
            }
            check(live.isNotEmpty()) {
                "官方表 $name 在本应用里没有对应的表，无法直接转换"
            }
            val missing = table.columns.map { it.name }.filter { it !in live }
            check(missing.isEmpty()) {
                "官方表 $name 有本应用还不认识的列 ${missing.joinToString()}，无法直接转换"
            }
        }
    }
}
