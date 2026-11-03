package me.rerere.rikkahub.data.sync.official

/**
 * 官方 RikkaHub 备份归档的物理布局。
 *
 * 导入器将来按它识别条目，官方兼容导出将来按它重建目录，两者都还没实现。官方不认识
 * 我们新增的任何目录，所以导出时必须把附件放回这里列出的位置。
 *
 * 与 [OfficialDatabaseSchema] 一样，这份清单是冻结的，只随官方版本更新；
 * `OfficialBackupLayoutTest` 会拿它和仓库里在用的常量对账。
 */
internal object OfficialBackupLayout {
    /** settings.json 的条目名 */
    const val SETTINGS_ENTRY = "settings.json"

    /** 数据库快照的条目名 */
    const val DATABASE_ENTRY = "rikka_hub.db"

    /** 更早的官方备份可能带上的 WAL 侧文件，恢复侧保留它们只为兼容旧备份 */
    val LEGACY_DATABASE_ENTRIES: Set<String> = setOf("rikka_hub-wal", "rikka_hub-shm")

    /** settings.json 里不属于 Settings 本身的字段，早期备份就带着 */
    const val LAUNCH_COUNT_KEY = "launchCount"

    /** 官方归档会带上的附件目录 */
    val ATTACHMENT_FOLDERS: Set<String> = setOf("upload", "skills", "fonts", "media_creation")

    /** 这些目录保留子目录，其余目录只有平铺文件 */
    val NESTED_ATTACHMENT_FOLDERS: Set<String> = setOf("skills", "media_creation")

    /**
     * 条目是否是一个合法的附件条目。
     *
     * 形状判定与恢复侧 `BackupManager.isAttachment` 一致：必须落在附件目录下、带非空的
     * 相对路径，且非嵌套目录下不允许再分子目录。文件名里的反斜杠这里不判，它属于路径
     * 安全，由恢复侧在导入计划阶段单独拒绝。非法形状返回 false 而不是抛异常，由调用方
     * 决定是跳过还是报错。
     */
    fun isAttachmentEntry(name: String): Boolean {
        val folder = name.substringBefore('/')
        if (folder !in ATTACHMENT_FOLDERS || '/' !in name) return false
        val relative = name.substringAfter('/')
        if (relative.isBlank()) return false
        if (folder !in NESTED_ATTACHMENT_FOLDERS && '/' in relative) return false
        return true
    }
}
