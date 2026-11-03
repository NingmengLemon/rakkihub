package me.rerere.rikkahub.data.sync

import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.files.FileFolders

/**
 * 导入时把库与文件登记里的附件引用换到按内容命名的新文件上。
 *
 * 引用本身怎么改由 [ImportedUploadFiles.rewrite] 负责；这里只管在暂存库里逐表逐列地找。
 * 只改名字，不改前缀，安装路径的改写由 `OfficialUrlRewriter` 负责，两件事在
 * [BackupManager.stageRestore] 里先后发生。
 */
internal object UploadNames {

    /**
     * 把暂存库里的引用换掉，在一个事务里完成。
     *
     * 表与文本列从 `sqlite_master` 与 `PRAGMA table_info` 现查，不维护一份表清单：任何一列都
     * 可能存着附件地址（消息 JSON、会话配置、工具结果），漏掉一列就会留下断链。`message_fts`
     * 与它的影子表跳过：索引里只有正文，没有地址，而且恢复之后本来就要重建。
     */
    fun rewriteDatabase(database: SupportSQLiteDatabase, renames: Map<String, String>) {
        if (renames.isEmpty()) return
        database.beginTransaction()
        try {
            rewriteManagedFiles(database, renames)
            for (table in userTables(database)) {
                val columns = columns(database, table)
                // 没有声明主键的表用 rowid 定位；Room 建的表都有主键，这条只是兜底
                val keys = columns
                    .filter { it.primaryKey > 0 }
                    .sortedBy { it.primaryKey }
                    .map { it.name }
                    .ifEmpty { listOf("rowid") }
                for (column in columns.filter { it.isText }) {
                    rewriteColumn(database, table, column.name, keys, renames)
                }
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    private const val FTS_TABLE_PREFIX = "message_fts"
    private val TEXT_AFFINITY = listOf("TEXT", "CHAR", "CLOB")

    private data class Column(val name: String, val type: String, val primaryKey: Int) {
        val isText: Boolean get() = TEXT_AFFINITY.any { it in type.uppercase() }
    }

    private fun userTables(database: SupportSQLiteDatabase): List<String> =
        database.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' " +
                "AND name NOT LIKE 'sqlite_%' AND name NOT LIKE '$FTS_TABLE_PREFIX%' " +
                "AND name NOT IN ('android_metadata', 'room_master_table')"
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }

    private fun columns(database: SupportSQLiteDatabase, table: String): List<Column> =
        database.query("PRAGMA table_info(`$table`)").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        Column(
                            name = cursor.getString(1) ?: "",
                            type = cursor.getString(2) ?: "",
                            primaryKey = cursor.getInt(5),
                        )
                    )
                }
            }
        }

    private fun rewriteColumn(
        database: SupportSQLiteDatabase,
        table: String,
        column: String,
        keys: List<String>,
        renames: Map<String, String>,
    ) {
        val keyList = keys.joinToString { "`$it`" }
        val updates = ArrayList<Pair<List<String>, String>>()
        database.query("SELECT $keyList, `$column` FROM `$table` WHERE instr(`$column`, 'upload/') > 0").use { cursor ->
            while (cursor.moveToNext()) {
                val value = cursor.getString(keys.size) ?: continue
                val rewritten = ImportedUploadFiles.rewrite(value, renames)
                if (rewritten == value) continue
                val keyValues = keys.indices.map { cursor.getString(it) }
                if (keyValues.any { it == null }) continue
                updates += keyValues.filterNotNull() to rewritten
            }
        }
        val where = keys.joinToString(" AND ") { "`$it` = ?" }
        for ((keyValues, value) in updates) {
            database.execSQL(
                "UPDATE `$table` SET `$column` = ? WHERE $where",
                (listOf<Any?>(value) + keyValues).toTypedArray(),
            )
        }
    }

    /**
     * `managed_files.relative_path` 存的是 `upload/<名字>`，没有 `file:` 前缀，所以单独处理。
     *
     * 这个列上有唯一索引：两份同内容文件合并成同一个名字之后，`relative_path` 会撞。先按新路径
     * 分组、每组只留 id 最小的一行，再改剩下的，顺序反了会撞索引。
     */
    private fun rewriteManagedFiles(database: SupportSQLiteDatabase, renames: Map<String, String>) {
        val prefix = "${FileFolders.UPLOAD}/"
        val rows = database.query("SELECT id, `relative_path` FROM `managed_files`").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val path = cursor.getString(1) ?: continue
                    if (!path.startsWith(prefix)) continue
                    val name = path.removePrefix(prefix)
                    add(Triple(cursor.getLong(0), path, prefix + (renames[name] ?: name)))
                }
            }
        }
        val dropped = rows.groupBy { it.third }.values
            .filter { it.size > 1 }
            .flatMap { group -> group.sortedBy { it.first }.drop(1) }
        val droppedIds = dropped.map { it.first }.toSet()
        dropped.forEach { database.execSQL("DELETE FROM `managed_files` WHERE id = ?", arrayOf(it.first)) }
        rows.filter { it.first !in droppedIds && it.third != it.second }.forEach {
            database.execSQL(
                "UPDATE `managed_files` SET `relative_path` = ? WHERE id = ?",
                arrayOf<Any?>(it.third, it.first),
            )
        }
    }
}
