package me.rerere.rikkahub.data.sync.official

/**
 * 判断我们当前的数据库还在不在官方 v32 的形状上。
 *
 * 官方兼容导出只有在分叉时才需要裁剪；C80 已登记 `usage_record.outcome` 这一处可由
 * [OfficialSchemaTrimmer] 裁剪，其它分叉仍直接失败。这里只负责说清楚「在不在同一形状上」，
 * 不负责转换。
 */
internal object OfficialDatabaseCompatibility {

    sealed interface Verdict {
        /** 版本与 identity hash 都与官方一致，库的形状官方能直接打开 */
        data object Identical : Verdict

        /** 已经分叉；已知列可由 OfficialSchemaTrimmer 裁剪，其它分叉仍失败 */
        data class Diverged(val reason: String) : Verdict
    }

    fun compare(version: Int, identityHash: String?): Verdict = when {
        version != OfficialDatabaseSchema.VERSION -> Verdict.Diverged(
            "库版本 v$version 与官方 v${OfficialDatabaseSchema.VERSION} 不一致"
        )

        identityHash != OfficialDatabaseSchema.IDENTITY_HASH -> Verdict.Diverged(
            "schema identity 与官方不同"
        )

        else -> Verdict.Identical
    }
}
