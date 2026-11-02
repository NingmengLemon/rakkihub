package me.rerere.rikkahub.data.sync.official

import me.rerere.rikkahub.data.sync.BackupManager
import me.rerere.rikkahub.data.sync.DatabaseBackup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 归档布局快照与备份实现里在用的常量对账。
 *
 * 快照刻意独立于 [BackupManager] 与 [DatabaseBackup]：它们是本仓库的实现细节，会随我们
 * 自己的改动变化；快照描述的是官方那侧，两边分开才能发现「我们改了但官方没改」。
 * 对账放在这里，就是为了让这种偏离立刻失败，而不是等合并后才发现。
 */
class OfficialBackupLayoutTest {

    @Test
    fun `entry names match the ones the backup writes`() {
        assertEquals(BackupManager.SETTINGS_ENTRY, OfficialBackupLayout.SETTINGS_ENTRY)
        assertEquals(DatabaseBackup.ARCHIVE_DATABASE, OfficialBackupLayout.DATABASE_ENTRY)
        assertEquals(BackupManager.LAUNCH_COUNT_KEY, OfficialBackupLayout.LAUNCH_COUNT_KEY)
    }

    @Test
    fun `legacy database entries match the wal side files`() {
        assertEquals(
            setOf(DatabaseBackup.WAL, DatabaseBackup.SHM),
            OfficialBackupLayout.LEGACY_DATABASE_ENTRIES,
        )
    }

    @Test
    fun `attachment folders match the ones the backup collects`() {
        assertEquals(
            BackupManager.ATTACHMENT_FOLDERS.toSet(),
            OfficialBackupLayout.ATTACHMENT_FOLDERS,
        )
    }

    @Test
    fun `nested attachment folders match the ones backed up with subdirectories`() {
        assertEquals(
            BackupManager.NESTED_ATTACHMENT_FOLDERS,
            OfficialBackupLayout.NESTED_ATTACHMENT_FOLDERS,
        )
        assertTrue(BackupManager.NESTED_ATTACHMENT_FOLDERS.all { it in BackupManager.ATTACHMENT_FOLDERS })
    }

    @Test
    fun `well formed attachment entries are classified by folder`() {
        assertTrue(OfficialBackupLayout.isAttachmentEntry("upload/a.png"))
        assertTrue(OfficialBackupLayout.isAttachmentEntry("fonts/Noto.ttf"))
        assertTrue(OfficialBackupLayout.isAttachmentEntry("skills/demo/SKILL.md"))
        assertTrue(OfficialBackupLayout.isAttachmentEntry("media_creation/1/out.png"))
    }

    @Test
    fun `malformed entries are rejected the same way the restore side does`() {
        // 没有斜杠：不是某个目录下的文件
        assertFalse(OfficialBackupLayout.isAttachmentEntry("upload"))
        // 相对路径为空
        assertFalse(OfficialBackupLayout.isAttachmentEntry("upload/"))
        // 平铺目录下不允许再分子目录
        assertFalse(OfficialBackupLayout.isAttachmentEntry("upload/a/b.png"))
        // 不在附件目录集合里，前缀相同也不行
        assertFalse(OfficialBackupLayout.isAttachmentEntry("uploads/a.png"))
        // 顶层条目
        assertFalse(OfficialBackupLayout.isAttachmentEntry("settings.json"))
        assertFalse(OfficialBackupLayout.isAttachmentEntry("rikka_hub.db"))
    }

    @Test
    fun `path traversal is left to the caller`() {
        // 快照只按目录归类，不做路径穿越检查；导入器拿到条目后仍必须走 PendingRestore.resolveInside，
        // 那里才会拒绝 ".."、反斜杠与空路径。这条路不作为安全边界，只固定两层的职责划分。
        assertTrue(OfficialBackupLayout.isAttachmentEntry("skills/../escape.txt"))
    }
}
