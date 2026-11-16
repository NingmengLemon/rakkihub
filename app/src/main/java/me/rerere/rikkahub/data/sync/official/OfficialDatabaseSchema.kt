package me.rerere.rikkahub.data.sync.official

/**
 * 本仓库 Room schema 当前对应的官方版本。
 *
 * 表定义本身在各版本的快照里（[OfficialSnapshotV31]、[OfficialSnapshotV32]），由
 * [OfficialSnapshots] 注册。这里保留 [VERSION] 与 [IDENTITY_HASH] 是因为两处要用：
 * 官方兼容导出要知道「我们的库现在对着哪一版」，`OfficialDatabaseSchemaTest` 要允许
 * [ROOM_VERSION] 只比它多出 [KNOWN_EXTRA_COLUMNS] 里登记的自定义列，以及 [KNOWN_EXTRA_TABLES] 里登记的整张表。
 *
 * [TABLES] 是 v32 的别名，留给既有引用；新代码按版本从 [OfficialSnapshots] 取。
 */
internal object OfficialDatabaseSchema {
    const val VERSION = 32

    /** 当前 Room schema 版本；它与 [VERSION] 不一致时通过 [KNOWN_EXTRA_COLUMNS] 和 [KNOWN_EXTRA_TABLES] 裁剪。 */
    const val ROOM_VERSION = 34

    /** 当前 Room schema（v34）的 identity hash，供恢复判定使用。 */
    const val ROOM_IDENTITY_HASH = "d766edcc0373886887c73b9e3869be11"

    /** 官方快照里没有、但我们后加的自定义列；官方兼容导出前会裁掉。 */
    val KNOWN_EXTRA_COLUMNS: Map<String, Set<String>> = mapOf(
        "usage_record" to setOf("outcome"),
    )

    /** 官方快照里没有、由本仓库新增的整张表；官方兼容导出前会 DROP。 */
    val KNOWN_EXTRA_TABLES: Set<String> = setOf("file_derivations")

    /** 官方 v32 的 Room schema identity，可用来和官方库的 room_master_table 对照 */
    const val IDENTITY_HASH = "c64deeb13766eaf6528a2f4f6965c836"

    val TABLES: Map<String, OfficialSchemaTypes.Table> get() = OfficialSnapshotV32.TABLES
}
