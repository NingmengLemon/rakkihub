package me.rerere.rikkahub.data.sync.official

import android.database.sqlite.SQLiteDatabase
import java.io.File

/** 官方客户端的 applicationId，官方安装里附件路径的包名段就是它。 */
internal const val OFFICIAL_APPLICATION_ID = "me.rerere.rikkahub"

/**
 * 我们的数据库已经不在官方形状上，而官方兼容导出需要的裁剪逻辑还没实现。
 *
 * 用异常而不是返回空值，是为了让调用方只有一个失败分支；具体原因留给日志，界面上换成
 * 一条可翻译的提示。
 */
internal class OfficialExportUnsupportedException(
    detail: String,
) : IllegalStateException(detail)

/**
 * 把我们的数据库快照转成官方客户端能打开的库。
 *
 * 双方 schema 一致时把附件引用改写成官方安装的路径再交出；一旦发现分叉就抛
 * [OfficialExportUnsupportedException]。静默产出一份官方打不开的备份比直接失败更糟：失败
 * 当场就能看见，坏备份要等到恢复时才发现。
 */
internal object OfficialDatabaseAdapter {

    /**
     * 返回可以写进归档的数据库文件。
     *
     * @param attachmentPrefixRewrites 需要改写的路径前缀对，第一个是库里的现状，第二个是官方
     *   安装里对应的位置。官方客户端恢复时只把附件搬进自己的 files 目录，不会改库里的路径，
     *   所以路径必须由我们在导出时换掉，否则装过去之后图片与文档全断。
     */
    fun adaptForOfficial(snapshot: File, attachmentPrefixRewrites: List<Pair<String, String>>): File {
        val (version, identityHash) = readHeader(snapshot)
        return when (val verdict = OfficialDatabaseCompatibility.compare(version, identityHash)) {
            OfficialDatabaseCompatibility.Verdict.Identical ->
                rewriteAttachmentPaths(snapshot, attachmentPrefixRewrites)
            is OfficialDatabaseCompatibility.Verdict.Diverged -> throw OfficialExportUnsupportedException(
                "官方兼容导出还不能处理分叉的 schema：${verdict.reason}。裁剪逻辑尚未实现。"
            )
        }
    }

    /**
     * 逐表逐列做前缀替换，只碰快照里的 TEXT 列。
     *
     * 用 SQL 的 `REPLACE` 而不是解析 JSON：这些列里有 JSON，也有普通字符串，统一按文本替换
     * 最简单；要替换的前缀是绝对路径，不会出现在别的内容里。
     */
    private fun rewriteAttachmentPaths(snapshot: File, rewrites: List<Pair<String, String>>): File {
        if (rewrites.isEmpty() || rewrites.all { it.first == it.second }) return snapshot
        val target = File(snapshot.parentFile, "official-${snapshot.name}")
        snapshot.copyTo(target, overwrite = true)
        SQLiteDatabase.openDatabase(target.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { database ->
            val schema = OfficialSnapshots.forVersion(database.version) ?: return target
            for ((table, definition) in schema.tables) {
                val textColumns = definition.columns.filter { it.affinity == "TEXT" }.map { it.name }
                for (column in textColumns) {
                    for ((from, to) in rewrites) {
                        database.execSQL(
                            "UPDATE `$table` SET `$column` = REPLACE(`$column`, ?, ?) WHERE `$column` LIKE ?",
                            arrayOf(from, to, "%${from.removePrefix("file://")}%"),
                        )
                    }
                }
            }
        }
        return target
    }

    private fun readHeader(snapshot: File): Pair<Int, String?> {
        val database = SQLiteDatabase.openDatabase(snapshot.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        return try {
            val version = database.rawQuery("PRAGMA user_version", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
            // room_master_table 是 Room 自己维护的单行表，更早的库没有它。先查 sqlite_master
            // 而不是靠捕获异常：后者会把 SELECT 期间的锁冲突之类真正的问题也当成「身份未知」。
            val identityHash = if (hasRoomMasterTable(database)) {
                database.rawQuery("SELECT identity_hash FROM room_master_table LIMIT 1", null)
                    .use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            } else {
                null
            }
            version to identityHash
        } finally {
            database.close()
        }
    }

    private fun hasRoomMasterTable(database: SQLiteDatabase): Boolean =
        database.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'room_master_table' LIMIT 1",
            null,
        ).use { cursor -> cursor.moveToFirst() }
}
