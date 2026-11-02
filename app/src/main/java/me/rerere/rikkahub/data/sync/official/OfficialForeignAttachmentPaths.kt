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
                    // 先把属于当前安装的前缀整段删掉，剩下的文本里只要还有 file:///data/
                    // 就是别的安装的路径。不能写成「含 file:///data/ 且不含当前前缀」：
                    // NOT LIKE 是按整个单元格判的，同一格里两种前缀都有时会被漏掉，恢复就
                    // 走迁移链，外来的绝对路径原样留下。
                    database.rawQuery(
                        "SELECT 1 FROM `$table` WHERE REPLACE(`$column`, ?, '') LIKE '%file:///data/%' LIMIT 1",
                        arrayOf(ourPrefix),
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
