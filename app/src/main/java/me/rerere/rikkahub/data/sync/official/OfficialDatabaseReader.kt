package me.rerere.rikkahub.data.sync.official

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.Closeable
import java.io.File

/**
 * 官方库的只读访问层。
 *
 * 表名与列名全部来自 [OfficialDatabaseSchema] 的冻结快照，不引用本仓库的 `@Entity`：官方库的
 * 结构怎么变、我们自己的库怎么演进，都不影响这里的读法。
 *
 * 打开时核对三件事：`user_version` 与快照一致、每张表都在、每张表的列都不缺。快照里有、
 * 库里没有的列说明官方做了破坏性迁移，直接失败；库里多出来的列记日志，不参与读取。
 * `message_fts*` 这类派生表不在快照里，跳过。
 *
 * 结构判断本身在 [OfficialSchemaVerifier]、类型判断在 [OfficialFieldType]，两者都不依赖
 * Android，可以在 JVM 上直接测。
 */
internal class OfficialDatabaseReader private constructor(
    private val database: SQLiteDatabase,
    /** 按库里的 `user_version` 选中的那一版结构 */
    val snapshot: OfficialSnapshot,
) : Closeable {

    /** 这一版快照的表定义，写入层按它构造 */
    val tables: Map<String, OfficialSchemaTypes.Table> get() = snapshot.tables

    /** 库里有、快照里没有的列，诊断用 */
    val unexpectedColumns: Map<String, List<String>> = verifySchema()

    /**
     * 逐行读取一张表。
     *
     * 返回的序列只能消费一次，而且必须消费完。cursor 在序列正常走到结尾时关闭；调用方提前
     * 终止（`first()`、`take()`、`break`）或取了不消费时，cursor 会留到 [close] 或 GC。
     * 拿不准的话用 [forEachRow]。读到的每一行都自带值，序列结束后仍然可读。
     */
    fun rows(tableName: String): Sequence<OfficialRow> {
        val table = requireNotNull(snapshot.tables[tableName]) {
            "未知的官方表：$tableName"
        }
        val projection = table.columns.joinToString(",") { "`${it.name}`" }
        val cursor = database.rawQuery("SELECT $projection FROM `$tableName`", null)
        return cursorToRows(cursor, table)
    }

    /** 逐行读完一张表，保证 cursor 关闭。 */
    fun forEachRow(tableName: String, block: (OfficialRow) -> Unit) {
        rows(tableName).forEach(block)
    }

    override fun close() {
        database.close()
    }

    private fun verifySchema(): Map<String, List<String>> {
        val liveColumns = snapshot.tables.keys.associateWith { name ->
            database.rawQuery("PRAGMA table_info(`$name`)", null).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(1)) }
            }
        }.filterValues { it.isNotEmpty() }

        return when (val verdict = OfficialSchemaVerifier.verify(snapshot.tables, liveColumns)) {
            is OfficialSchemaVerifier.Verdict.Ok -> {
                val unexpected = verdict.unexpectedColumns
                    .mapValues { (table, columns) ->
                        columns.filterNot { it in OfficialDatabaseSchema.KNOWN_EXTRA_COLUMNS[table].orEmpty() }
                    }
                    .filterValues { it.isNotEmpty() }
                unexpected.forEach { (table, columns) ->
                    Log.w(TAG, "官方表 $table 有快照之外的列 ${columns.joinToString()}，已忽略")
                }
                unexpected
            }

            is OfficialSchemaVerifier.Verdict.Failed -> error(verdict.reason)
        }
    }

    /**
     * 每行在迭代时就把值读出来，不把 cursor 交给调用方。
     *
     * [OfficialRow] 如果在 Sequence 消费完之后还被持有，带着 cursor 的版本会直接越界；官方
     * 数据要跨阶段使用（校验、转换），所以行对象必须自包含。
     */
    private fun cursorToRows(cursor: Cursor, table: OfficialSchemaTypes.Table): Sequence<OfficialRow> = sequence {
        try {
            val indices = table.columns.map { it.name to cursor.getColumnIndexOrThrow(it.name) }
            while (cursor.moveToNext()) {
                val values = LinkedHashMap<String, Any?>(indices.size)
                for ((name, index) in indices) {
                    values[name] = if (cursor.isNull(index)) null else readValue(cursor, index, name, table)
                }
                yield(OfficialRow(table.name, values))
            }
        } finally {
            cursor.close()
        }
    }

    private fun readValue(cursor: Cursor, index: Int, columnName: String, table: OfficialSchemaTypes.Table): Any {
        val column = requireNotNull(table.columns.firstOrNull { it.name == columnName }) {
            "快照里没有列 ${table.name}.$columnName"
        }
        val affinity = column.affinity.uppercase()
        val actual = cursor.getType(index)
        check(OfficialFieldType.accepts(affinity, actual)) {
            "${table.name}.$columnName 期望 $affinity，库里存的是 ${OfficialFieldType.name(actual)}"
        }
        return when (affinity) {
            "INTEGER" -> cursor.getLong(index)
            "REAL" -> cursor.getDouble(index)
            "TEXT" -> cursor.getString(index)
            "BLOB" -> cursor.getBlob(index)
            else -> error("未处理的 affinity：${column.affinity}")
        }
    }

    companion object {
        private const val TAG = "OfficialDatabaseReader"

        fun open(databaseFile: File): OfficialDatabaseReader {
            require(databaseFile.isFile) { "官方数据库不存在：${databaseFile.absolutePath}" }
            val database = SQLiteDatabase.openDatabase(
                databaseFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY,
            )
            // 校验失败时要把刚打开的库关掉，否则 fd 要等 finalize 才释放
            return try {
                val version = database.version
                val snapshot = OfficialSnapshots.forVersion(version)
                    ?: if (version == OfficialDatabaseSchema.ROOM_VERSION) {
                        OfficialSnapshots.forVersion(OfficialDatabaseSchema.VERSION)
                    } else {
                        null
                    }
                requireNotNull(snapshot) {
                    "未知的官方版本 v$version，已知：${OfficialSnapshots.BY_VERSION.keys.sorted()}"
                }
                OfficialDatabaseReader(database, snapshot)
            } catch (e: Exception) {
                database.close()
                throw e
            }
        }
    }
}

/**
 * 一行官方数据。
 *
 * 带表名与列值，值按 [OfficialSchemaTypes.Column] 的 affinity 在读取时就转好类型，调用方
 * 拿到的是确定的类型；行对象不依赖 cursor，Sequence 结束之后仍然可读。
 */
internal class OfficialRow(
    val tableName: String,
    private val values: Map<String, Any?>,
) {
    fun value(columnName: String): Any? = values[columnName]

    fun string(columnName: String): String? = value(columnName) as String?

    fun long(columnName: String): Long? = value(columnName) as Long?
}
