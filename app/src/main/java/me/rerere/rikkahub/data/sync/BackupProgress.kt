package me.rerere.rikkahub.data.sync

/**
 * 备份或恢复正在做的那一段。
 *
 * 导出与导入共用一套阶段：`EXTRACTING` 只出现在导入，`FILES` 与 `WORKSPACES` 只出现在导出。
 */
enum class BackupStage {
    /** 导出：清点附件与工作区文件。 */
    PREPARING,

    /** 导出：数据库快照与官方兼容改写；导入：校验、转换与迁移。两边都没法按条目切分。 */
    DATABASE,

    /** 导出：附件。 */
    FILES,

    /** 导出：工作区文件区。 */
    WORKSPACES,

    /** 导入：解压归档条目。 */
    EXTRACTING,
}

/**
 * 打包或恢复的进度。
 *
 * [done] 与 [total] 是阶段内的条目数：`FILES`、`WORKSPACES` 是文件数，`EXTRACTING` 是归档条目数。
 * 数据库那两段做的是快照、校验与转换，没有可数的单位，`total` 恒为 0，界面按不确定进度显示。
 */
data class BackupProgress(
    val stage: BackupStage,
    val done: Int = 0,
    val total: Int = 0,
) {
    val fraction: Float
        get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
}
