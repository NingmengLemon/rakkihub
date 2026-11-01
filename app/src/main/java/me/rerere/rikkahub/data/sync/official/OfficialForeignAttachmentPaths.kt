package me.rerere.rikkahub.data.sync.official

import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * 备份里的库还带着别的安装的附件路径吗。
 *
 * 附件引用是绝对 `file://` 路径，包名与用户号都在路径里。官方备份、以及我们自己在别的包名
 * 下（debug / release、改过包名的构建）产生的备份，都会带着生产者的路径；直接装进当前安装
 * 之后，文件在本地但引用指向别处，图片与文档就全断了。
 *
 * 只探测、不改数据：命中就说明这份库要么交给转换器（它会重写文本列），要么走别的重写路径。
 */
internal object OfficialForeignAttachmentPaths {

    /**
     * @param ourPrefix 当前安装的 files 目录 URI 前缀，以 `/` 结尾
     */
    fun hasForeign(database: SQLiteDatabase, ourPrefix: String): Boolean {
        val version = database.version
        val tables = OfficialSnapshots.forVersion(version)?.tables ?: return false
        for ((table, definition) in tables) {
            val columns = definition.columns
                .filter { it.affinity == "TEXT" }
                .map { it.name }
            if (columns.isEmpty()) continue
            for (column in columns) {
                val hit = runCatching {
                    database.rawQuery(
                        "SELECT 1 FROM `$table` WHERE `$column` LIKE '%file:///data/%' " +
                            "AND `$column` NOT LIKE ? ESCAPE '\\' LIMIT 1",
                        arrayOf(
                            "%${OfficialExportPaths.escapeLikePattern(ourPrefix.removePrefix("file://"))}%",
                        ),
                    ).use { it.moveToFirst() }
                }.getOrDefault(false)
                if (hit) return true
            }
        }
        return false
    }

    /** 库的版本能被我们的快照读到时才探测，读不到就不猜 */
    fun hasForeign(file: File, ourPrefix: String): Boolean = runCatching {
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use {
            hasForeign(it, ourPrefix)
        }
    }.getOrDefault(false)
}
