package me.rerere.rikkahub.data.sync

/**
 * 归档缺少恢复必须的条目，整份导入被拒绝。
 *
 * 判定发生在解压之前，暂存目录在 `finally` 里删掉，正在用的库、附件与设置都没有被改动。界面按
 * [reason] 选一条可翻译的文案，不要把 [message] 直接展示给用户。
 */
internal class BackupRejectedException(
    val reason: Reason,
) : IllegalStateException("Backup rejected: $reason") {
    enum class Reason {
        /** 归档里没有数据库。官方导出时可以只勾附件，那种备份没法安全恢复。 */
        MISSING_DATABASE,

        /** 归档里没有 settings.json。库里的会话引用助手，助手在设置里，缺一半会错配。 */
        MISSING_SETTINGS,
    }
}
