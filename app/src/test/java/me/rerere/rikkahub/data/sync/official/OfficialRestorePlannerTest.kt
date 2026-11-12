package me.rerere.rikkahub.data.sync.official

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分流判定的纯逻辑验证。
 *
 * 这一段决定「备份里的库交给谁」：迁移链、转换器、还是直接失败。判错的方向只有两种，都不
 * 能接受：把该转换的库交给迁移链会让恢复失败，把不认识的库硬读会写坏数据。
 */
class OfficialRestorePlannerTest {

    private val ourVersion = OfficialDatabaseSchema.ROOM_VERSION
    private val ourIdentity = OfficialDatabaseSchema.ROOM_IDENTITY_HASH
    private val snapshots = OfficialSnapshots.BY_VERSION

    private fun plan(version: Int, identity: String?) = planAt(ourVersion, ourIdentity, version, identity)

    private fun planAt(
        appVersion: Int,
        appIdentity: String,
        version: Int,
        identity: String?,
    ) = OfficialRestorePlanner.plan(
        version = version,
        identityHash = identity,
        ourVersion = appVersion,
        ourIdentity = appIdentity,
        snapshots = snapshots,
    )

    @Test fun `our own current backup goes through the migration chain`() {
        assertEquals(OfficialRestorePlan.Migrate, plan(ourVersion, ourIdentity))
    }

    @Test fun `an older backup goes through the migration chain`() {
        assertEquals(OfficialRestorePlan.Migrate, plan(31, "a428043e3256791d9bc0855715c6355a"))
        assertEquals(OfficialRestorePlan.Migrate, plan(32, OfficialDatabaseSchema.IDENTITY_HASH))
        // 低于当前 Room 33 的库一律交给迁移链；identity 不能用来跳过迁移
        assertEquals(OfficialRestorePlan.Migrate, plan(32, "00000000000000000000000000000000"))
        assertEquals(OfficialRestorePlan.Migrate, plan(16, null))
    }

    @Test fun `same version unknown identity is rejected without a snapshot`() {
        val result = plan(ourVersion, "00000000000000000000000000000000")
        assertTrue("没有当前版本的官方快照时必须拒绝，实际是 $result", result is OfficialRestorePlan.Reject)
        assertTrue((result as OfficialRestorePlan.Reject).reason.contains("没有该版本的官方快照"))
    }

    @Test fun `same version different identity still converts when a snapshot exists`() {
        // 生产当前 Room 33 没有 v33 官方快照，这条覆盖转换器仍然支持的同期官方库分支
        val result = planAt(
            appVersion = OfficialDatabaseSchema.VERSION,
            appIdentity = OfficialDatabaseSchema.IDENTITY_HASH,
            version = OfficialDatabaseSchema.VERSION,
            identity = "00000000000000000000000000000000",
        )
        assertTrue("应当走转换，实际是 $result", result is OfficialRestorePlan.Convert)
        assertEquals(OfficialDatabaseSchema.VERSION, (result as OfficialRestorePlan.Convert).snapshot.version)
    }

    @Test fun `same version without a schema fingerprint is rejected`() {
        // 连 room_master_table 都没有，判不出来源，不能猜
        val result = plan(ourVersion, null)
        assertTrue("判不出来要明确失败，实际是 $result", result is OfficialRestorePlan.Reject)
    }

    @Test fun `a newer backup is rejected instead of falling back`() {
        // 迁移链降不了版本；官方升版是加列，硬写会在插入时崩，所以要在这里就拒绝
        val result = plan(ourVersion + 1, "00000000000000000000000000000000")
        assertTrue("高版本必须明确失败，实际是 $result", result is OfficialRestorePlan.Reject)
        assertTrue((result as OfficialRestorePlan.Reject).reason.contains("高于"))
    }

    @Test fun `the rejection explains what to do`() {
        val result = plan(ourVersion + 1, null) as OfficialRestorePlan.Reject
        assertTrue("提示要告诉用户升级应用", result.reason.contains("升级应用"))
    }
}
