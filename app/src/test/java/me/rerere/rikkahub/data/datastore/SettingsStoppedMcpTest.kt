package me.rerere.rikkahub.data.datastore

import me.rerere.rikkahub.data.ai.mcp.McpCommonOptions
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.ai.mcp.StdioMcpServer
import me.rerere.rikkahub.data.sync.official.forOfficialExport
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class SettingsStoppedMcpTest {
    private fun http(name: String, enable: Boolean = true) = McpServerConfig.StreamableHTTPServer(
        commonOptions = McpCommonOptions(name = name, enable = enable),
    )

    @Test
    fun `stopped ids default to empty and round trip`() {
        val server = http("remote")
        val settings = Settings(
            mcpServers = listOf(server),
            stoppedMcpServerIds = setOf(server.id),
        )

        val decoded = JsonInstant.decodeFromString<Settings>(JsonInstant.encodeToString(settings))

        assertEquals(setOf(server.id), decoded.stoppedMcpServerIds)
        assertTrue(JsonInstant.decodeFromString<Settings>("{}").stoppedMcpServerIds.isEmpty())
    }

    @Test
    fun `normalization keeps only enabled and existing stopped servers`() {
        val enabled = http("enabled")
        val disabled = http("disabled", enable = false)
        val blank = http(" ")
        val normalized = Settings(
            mcpServers = listOf(enabled, disabled, blank),
            stoppedMcpServerIds = setOf(enabled.id, disabled.id, blank.id, Uuid.random()),
        ).normalized()

        assertEquals(setOf(enabled.id), normalized.stoppedMcpServerIds)
    }

    @Test
    fun `normalization keeps stopped stdio servers`() {
        val stdio = StdioMcpServer(
            commonOptions = McpCommonOptions(name = "local"),
            workspaceId = "workspace-1",
            command = "node",
        )
        val normalized = Settings(
            stdioMcpServers = listOf(stdio),
            stoppedMcpServerIds = setOf(stdio.id),
        ).normalized()

        assertEquals(setOf(stdio.id), normalized.stoppedMcpServerIds)
    }

    @Test
    fun `normalization drops stopped ids of duplicate workspace servers`() {
        val first = StdioMcpServer(
            commonOptions = McpCommonOptions(name = "first"),
            workspaceId = "workspace-1",
            command = "node",
        )
        val second = StdioMcpServer(
            commonOptions = McpCommonOptions(name = "second"),
            workspaceId = "workspace-1",
            command = "node",
        )
        val normalized = Settings(
            stdioMcpServers = listOf(first, second),
            stoppedMcpServerIds = setOf(first.id, second.id),
        ).normalized()

        assertEquals(setOf(first.id), normalized.stoppedMcpServerIds)
    }

    @Test
    fun `official export drops stopped ids`() {
        val server = http("remote")
        val exported = Settings(
            mcpServers = listOf(server),
            stoppedMcpServerIds = setOf(server.id),
        ).forOfficialExport()

        assertTrue(exported.stoppedMcpServerIds.isEmpty())
    }
}
