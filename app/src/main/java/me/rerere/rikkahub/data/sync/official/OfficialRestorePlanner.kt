package me.rerere.rikkahub.data.sync.official

import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * 备份里那个库的版本与 schema 指纹。
 *
 * 恢复流程要用它决定「交给迁移链、转成当前版本、还是直接拒绝」，判定发生在碰任何业务表之前。
 */
internal data class OfficialDatabaseHeader(
    val version: Int,
    val identityHash: String?,
)

internal object OfficialDatabaseHeaders {

    /** 只读打开、读两处元数据、关掉；读不出来时返回 null（让调用方回到迁移链） */
    fun read(file: File): OfficialDatabaseHeader? = runCatching {
        val database = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            OfficialDatabaseHeader(database.version, readIdentityHash(database))
        } finally {
            database.close()
        }
    }.getOrNull()

    /** `room_master_table` 是 Room 维护的单行表，更早的库没有它 */
    fun readIdentityHash(database: SQLiteDatabase): String? {
        val exists = database.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'room_master_table' LIMIT 1",
            null,
        ).use { it.moveToFirst() }
        if (!exists) return null
        return database.rawQuery("SELECT identity_hash FROM room_master_table LIMIT 1", null)
            .use { if (it.moveToFirst()) it.getString(0) else null }
    }
}

/**
 * 恢复一个库时该走哪条路。
 *
 * 判定只看两处元数据：`user_version` 与 schema 指纹。版本高于当前 Room 版本时拒绝，低于时
 * 交给迁移链；同版本只有 identity 不同且有该版本官方快照时才交给转换器，当前 Room 33 没有
 * v33 官方快照，所以同版本未知 identity 直接拒绝。
 */
internal sealed interface OfficialRestorePlan {
    /** 交给 Room 迁移链：自家旧备份、以及与我们结构一致的库 */
    data object Migrate : OfficialRestorePlan

    /** 用该版本的官方快照读出来，写成当前版本的库 */
    data class Convert(val snapshot: OfficialSnapshot) : OfficialRestorePlan

    /** 判不出来或支持不了，明确失败，不猜 */
    data class Reject(val reason: String) : OfficialRestorePlan
}

internal object OfficialRestorePlanner {

    /**
     * @param version 备份库里库的 `user_version`
     * @param identityHash 备份库的 schema 指纹，缺失为 null
     * @param ourVersion 本应用编译进来的 Room 版本
     * @param ourIdentity 本应用当前的 schema 指纹
     * @param snapshots 已知的官方版本快照
     */
    fun plan(
        version: Int,
        identityHash: String?,
        ourVersion: Int,
        ourIdentity: String,
        snapshots: Map<Int, OfficialSnapshot>,
    ): OfficialRestorePlan {
        if (version > ourVersion) {
            // 迁移链只能往上升，降不了。没有该版本的快照就没法读，有也不能用旧的写进新的：
            // 官方升版通常是加列，我们的表放不下，硬写会在插入时失败得比这更晚。
            return OfficialRestorePlan.Reject(
                "备份的库版本 v$version 高于本应用支持的 v$ourVersion，请先升级应用",
            )
        }
        if (version < ourVersion) {
            return OfficialRestorePlan.Migrate
        }
        if (identityHash == ourIdentity) {
            return OfficialRestorePlan.Migrate
        }
        if (identityHash == null) {
            // 同版本却没有 schema 指纹：来源判不出来。Room 会因此拒绝打开，而按官方快照硬读
            // 等于把一份来路不明的库当成官方数据写进来，两种都不接受。
            return OfficialRestorePlan.Reject(
                "备份的库缺少 schema 指纹，无法判定来源",
            )
        }
        val snapshot = snapshots[version] ?: return OfficialRestorePlan.Reject(
            "备份的库版本 v$version 与本应用相同但结构不同，且没有该版本的官方快照，无法判定",
        )
        return OfficialRestorePlan.Convert(snapshot)
    }
}
