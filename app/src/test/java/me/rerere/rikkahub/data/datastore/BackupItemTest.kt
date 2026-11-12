package me.rerere.rikkahub.data.datastore

import me.rerere.rikkahub.data.datastore.WebDavConfig.BackupItem
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupItemTest {

    @Test
    fun normalizeAlwaysKeepsChatRecords() {
        assertEquals(listOf(BackupItem.DATABASE), BackupItem.normalize(emptyList()))
        assertEquals(listOf(BackupItem.DATABASE, BackupItem.FILES), BackupItem.normalize(listOf(BackupItem.FILES)))
        assertEquals(
            listOf(BackupItem.DATABASE, BackupItem.FILES, BackupItem.WORKSPACES),
            BackupItem.normalize(listOf(BackupItem.WORKSPACES, BackupItem.FILES, BackupItem.DATABASE)),
        )
        assertEquals(
            listOf(BackupItem.DATABASE, BackupItem.WORKSPACES),
            BackupItem.normalize(listOf(BackupItem.WORKSPACES)),
        )
    }

    @Test
    fun localDefaultSelectionIncludesAttachmentsAndWorkspaces() {
        assertEquals(
            listOf(BackupItem.DATABASE, BackupItem.FILES, BackupItem.WORKSPACES),
            BackupItem.LOCAL_DEFAULT_SELECTION
        )
    }

    @Test
    fun defaultSelectionKeepsChatRecords() {
        assertEquals(listOf(BackupItem.DATABASE, BackupItem.FILES), BackupItem.DEFAULT_SELECTION)
    }

    @Test
    fun selectableItemsDoNotOfferChatRecords() {
        assertEquals(listOf(BackupItem.FILES, BackupItem.WORKSPACES), BackupItem.SELECTABLE)
    }
}
