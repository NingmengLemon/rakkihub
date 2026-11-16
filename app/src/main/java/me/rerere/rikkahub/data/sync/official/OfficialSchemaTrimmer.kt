package me.rerere.rikkahub.data.sync.official

import android.database.sqlite.SQLiteDatabase
import me.rerere.rikkahub.data.sync.DatabaseBackup
import java.io.File

/**
 * 官方兼容导出前把已知的自定义列和整张自定义表裁掉。
 *
 * C80 的 `usage_record.outcome` 按列裁，C87 的 `file_derivations` 按表 drop；其它表或列一旦分叉
 * 仍然直接失败，不猜。裁剪用重建表而不是 `ALTER TABLE DROP COLUMN`，兼容低版本 SQLite。
 */
internal object OfficialSchemaTrimmer {

    fun canReadAsV32(database: SQLiteDatabase): Boolean {
        if (database.version != OfficialDatabaseSchema.ROOM_VERSION) return false
        val extraTables = userTables(database) - OfficialSnapshotV32.TABLES.keys -
            OfficialDatabaseSchema.KNOWN_EXTRA_TABLES
        if (extraTables.isNotEmpty()) return false
        for ((name, table) in OfficialSnapshotV32.TABLES) {
            val live = columns(database, name)
            if (live.isEmpty()) return false
            val official = table.columns.map { it.name }.toSet()
            if (!live.containsAll(official)) return false
            val extras = live - official
            if (!extras.all { it in OfficialDatabaseSchema.KNOWN_EXTRA_COLUMNS[name].orEmpty() }) return false
        }
        return true
    }

    /** 把 source 复制并裁剪成官方 v32 形状，返回目标文件。 */
    fun trim(source: File, target: File): File {
        if (target.exists()) target.delete()
        source.copyTo(target, overwrite = false)
        SQLiteDatabase.openDatabase(target.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { database ->
            check(canReadAsV32(database)) { "数据库不在已知的可裁剪形状上" }
            database.beginTransaction()
            try {
                // 先删整张自定义表，避免重建 managed_files 时还挂着 file_derivations 的外键；
                // 再裁官方表上的已知额外列。
                for (table in OfficialDatabaseSchema.KNOWN_EXTRA_TABLES) {
                    database.execSQL("DROP TABLE IF EXISTS `$table`")
                }
                for ((name, table) in OfficialSnapshotV32.TABLES) {
                    val official = table.columns.map { it.name }.toSet()
                    val extras = columns(database, name) - official
                    if (extras.isNotEmpty()) rebuildWithout(database, table)
                }
                database.version = OfficialDatabaseSchema.VERSION
                setRoomIdentity(database)
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
        }
        DatabaseBackup.removeSidecars(target)
        return target
    }

    private fun columns(database: SQLiteDatabase, table: String): List<String> =
        database.rawQuery("PRAGMA table_info(`$table`)", null).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(1)) }
        }

    private fun userTables(database: SQLiteDatabase): Set<String> =
        database.rawQuery(
            "SELECT name FROM sqlite_master WHERE type = 'table' " +
                "AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'message_fts%' " +
                "AND name NOT IN ('android_metadata', 'room_master_table')",
            null,
        ).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }

    private fun rebuildWithout(database: SQLiteDatabase, table: OfficialSchemaTypes.Table) {
        val temporary = "__trim_${table.name}"
        database.execSQL("DROP TABLE IF EXISTS `$temporary`")
        val columnSql = table.columns.joinToString(", ") { column ->
            val inlinePrimary = if (table.autoIncrement && table.primaryKey == listOf(column.name)) {
                " PRIMARY KEY AUTOINCREMENT"
            } else {
                ""
            }
            val notNull = if (column.notNull) " NOT NULL" else ""
            val default = column.defaultValue?.let { " DEFAULT $it" } ?: ""
            "`${column.name}` ${column.affinity}$notNull$inlinePrimary$default"
        }
        val primaryConstraint = if (!table.autoIncrement && table.primaryKey.isNotEmpty()) {
            ", PRIMARY KEY (${table.primaryKey.joinToString { "`$it`" }})"
        } else {
            ""
        }
        database.execSQL("CREATE TABLE `$temporary` ($columnSql$primaryConstraint)")
        val columnNames = table.columns.joinToString { "`${it.name}`" }
        database.execSQL("INSERT INTO `$temporary` ($columnNames) SELECT $columnNames FROM `${table.name}`")
        database.execSQL("DROP TABLE `${table.name}`")
        database.execSQL("ALTER TABLE `$temporary` RENAME TO `${table.name}`")
        for (index in table.indices) {
            val unique = if (index.unique) "UNIQUE " else ""
            val columns = index.columns.joinToString { "`$it`" }
            database.execSQL("CREATE ${unique}INDEX IF NOT EXISTS `${index.name}` ON `${table.name}` ($columns)")
        }
    }

    private fun setRoomIdentity(database: SQLiteDatabase) {
        val identity = OfficialDatabaseSchema.IDENTITY_HASH
        val updated = database.compileStatement(
            "UPDATE room_master_table SET identity_hash = ?"
        ).use { statement ->
            statement.bindString(1, identity)
            statement.executeUpdateDelete()
        }
        if (updated == 0) {
            database.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, ?)",
                arrayOf(identity),
            )
        }
    }
}
