package me.rerere.rikkahub.data.datastore

import me.rerere.rikkahub.data.ai.mcp.McpCommonOptions
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
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
        val normalized = Settings(
            mcpServers = listOf(enabled, disabled),
            stoppedMcpServerIds = setOf(enabled.id, disabled.id, Uuid.random()),
        ).normalized()

        assertEquals(setOf(enabled.id), normalized.stoppedMcpServerIds)
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
