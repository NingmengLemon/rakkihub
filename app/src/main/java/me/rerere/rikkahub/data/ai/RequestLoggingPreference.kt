package me.rerere.rikkahub.data.ai

import android.content.Context

/**
 * 请求日志开关的持久化。日志本身仍是 [me.rerere.common.android.Logging] 里的内存环形缓冲，
 * 重启清空；这里只保存开关状态，避免每次重启都要手动打开。
 */
object RequestLoggingPreference {
    private const val PREFS = "request_logging"
    private const val KEY_ENABLED = "enabled"

    fun load(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun save(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }
}
