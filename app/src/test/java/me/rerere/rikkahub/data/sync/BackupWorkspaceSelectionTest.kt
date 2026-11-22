package me.rerere.rikkahub.data.sync

import me.rerere.rikkahub.data.datastore.WebDavConfig
import me.rerere.rikkahub.data.datastore.effectiveWorkspaceIds
import me.rerere.rikkahub.data.datastore.toggleWorkspaceSelection
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupWorkspaceSelectionTest {
    private fun workspace(id: String) = WorkspaceEntity(
        id = id,
        name = "name-$id",
        root = "root-$id",
        createdAt = 1,
        updatedAt = 2,
    )

    @Test
    fun `null selection exports every workspace`() {
        val all = listOf(workspace("a"), workspace("b"))

        assertEquals(all, workspacesToExport(all, null))
    }

    @Test
    fun `explicit selection keeps only listed workspaces`() {
        val all = listOf(workspace("a"), workspace("b"), workspace("c"))

        assertEquals(listOf(all[0], all[2]), workspacesToExport(all, listOf("a", "c")))
    }

    @Test
    fun `empty selection exports no workspace`() {
        val all = listOf(workspace("a"))

        assertEquals(emptyList<WorkspaceEntity>(), workspacesToExport(all, emptyList()))
    }

    @Test
    fun `archive without workspace entries does not warn about missing files`() {
        val staged = listOf(StagedWorkspace("root-a", "A"))

        assertEquals(emptyList<String>(), workspacesMissingFiles(staged, emptySet()))
    }

    @Test
    fun `missing workspace detection skips staged roots present in the archive`() {
        val staged = listOf(StagedWorkspace("root-a", "A"), StagedWorkspace("root-b", "B"))

        assertEquals(listOf("B"), workspacesMissingFiles(staged, setOf("root-a")))
    }

    @Test
    fun `webdav config without workspace ids decodes as all`() {
        val config = JsonInstant.decodeFromString<WebDavConfig>("""{"url":"https://example.com"}""")

        assertNull(config.workspaceIds)
    }

    @Test
    fun `webdav config round-trips an explicit workspace selection`() {
        val config = WebDavConfig(workspaceIds = listOf("a", "b"))

        val decoded = JsonInstant.decodeFromString<WebDavConfig>(
            JsonInstant.encodeToString(WebDavConfig.serializer(), config)
        )

        assertEquals(listOf("a", "b"), decoded.workspaceIds)
    }

    @Test
    fun `unchecking the last workspace yields an empty selection`() {
        assertEquals(
            emptyList<String>(),
            toggleWorkspaceSelection(listOf("a"), listOf("a"), "a", false),
        )
    }

    @Test
    fun `selecting every workspace normalizes back to all`() {
        assertNull(toggleWorkspaceSelection(listOf("a"), listOf("a", "b"), "b", true))
    }

    @Test
    fun `unchecking one workspace materializes the remaining ids`() {
        assertEquals(
            listOf("b"),
            toggleWorkspaceSelection(null, listOf("a", "b"), "a", false),
        )
    }

    @Test
    fun `stale ids are ignored in the effective selection`() {
        assertEquals(setOf("a"), effectiveWorkspaceIds(listOf("a", "gone"), listOf("a", "b")))
    }
}
