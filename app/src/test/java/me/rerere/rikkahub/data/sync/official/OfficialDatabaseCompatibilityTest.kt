package me.rerere.rikkahub.data.sync.official

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 官方兼容导出的前置判定：我们的库还在不在官方形状上。
 *
 * 分叉之后由 OfficialSchemaTrimmer 处理已登记的 usage_record.outcome；这里只验证
 * 「能识别出分叉」，不验证转换。
 */
class OfficialDatabaseCompatibilityTest {

    @Test
    fun `identical when version and identity both match the snapshot`() {
        val verdict = OfficialDatabaseCompatibility.compare(
            version = OfficialDatabaseSchema.VERSION,
            identityHash = OfficialDatabaseSchema.IDENTITY_HASH,
        )
        assertEquals(OfficialDatabaseCompatibility.Verdict.Identical, verdict)
    }

    @Test
    fun `diverged when our schema moved past the snapshot`() {
        val verdict = OfficialDatabaseCompatibility.compare(
            version = OfficialDatabaseSchema.VERSION + 1,
            identityHash = OfficialDatabaseSchema.IDENTITY_HASH,
        )
        assertTrue(verdict is OfficialDatabaseCompatibility.Verdict.Diverged)
    }

    @Test
    fun `diverged when identity differs even at the same version`() {
        // 同号异义是最危险的情况：版本号看着对，表结构其实已经变了
        val verdict = OfficialDatabaseCompatibility.compare(
            version = OfficialDatabaseSchema.VERSION,
            identityHash = "00000000000000000000000000000000",
        )
        assertTrue(verdict is OfficialDatabaseCompatibility.Verdict.Diverged)
    }

    @Test
    fun `diverged when the library has no room master table`() {
        val verdict = OfficialDatabaseCompatibility.compare(
            version = OfficialDatabaseSchema.VERSION,
            identityHash = null,
        )
        assertTrue(verdict is OfficialDatabaseCompatibility.Verdict.Diverged)
    }
}
