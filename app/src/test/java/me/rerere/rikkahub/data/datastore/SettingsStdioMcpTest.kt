package me.rerere.rikkahub.data.datastore

import me.rerere.rikkahub.data.ai.mcp.McpCommonOptions
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.ai.mcp.StdioMcpServer
import me.rerere.rikkahub.data.sync.official.forOfficialExport
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsStdioMcpTest {
    private fun stdioServer() = StdioMcpServer(
        commonOptions = McpCommonOptions(name = "local"),
        workspaceId = "workspace-1",
        command = "node",
        args = listOf("server.js"),
        env = listOf("TOKEN" to "super-secret"),
    )

    @Test
    fun `stdio config round trips and masks env values`() {
        val server = stdioServer()
        val decoded = JsonInstant.decodeFromString<Settings>(
            JsonInstant.encodeToString(Settings(stdioMcpServers = listOf(server)))
        )

        assertEquals(server, decoded.stdioMcpServers.single())
        assertFalse(server.toString().contains("super-secret"))
        assertTrue(server.toString().contains("TOKEN"))
    }

    @Test
    fun `stdio config joins the runtime server list`() {
        val server = stdioServer()

        val runtime = Settings(stdioMcpServers = listOf(server)).allMcpServers().single()

        assertTrue(runtime is McpServerConfig.StdioServer)
        assertEquals(server.id, runtime.id)
        assertEquals("workspace-1", (runtime as McpServerConfig.StdioServer).workspaceId)
        assertEquals("node", runtime.command)
    }

    @Test
    fun `normalization keeps one stdio server per workspace`() {
        val first = stdioServer()
        val second = stdioServer().copy(workspaceId = first.workspaceId, command = "python")
        val third = stdioServer().copy(workspaceId = "workspace-2", command = "node")

        val normalized = Settings(stdioMcpServers = listOf(first, second, third)).normalized()

        assertEquals(listOf(first, third), normalized.stdioMcpServers)
    }

    @Test
    fun `dropped duplicate workspace reference is removed from assistant in the same pass`() {
        val first = stdioServer()
        val duplicate = stdioServer().copy(workspaceId = first.workspaceId, command = "python")
        val assistant = Settings().assistants.first().copy(mcpServers = setOf(duplicate.id))

        val normalized = Settings(
            assistants = listOf(assistant),
            stdioMcpServers = listOf(first, duplicate),
        ).normalized()

        assertTrue(normalized.assistants.first { it.id == assistant.id }.mcpServers.isEmpty())
    }

    @Test
    fun `blank workspace entries remain distinct by id`() {
        val first = stdioServer().copy(workspaceId = "")
        val second = stdioServer().copy(workspaceId = "", command = "python")

        val normalized = Settings(stdioMcpServers = listOf(first, second)).normalized()

        assertEquals(listOf(first, second), normalized.stdioMcpServers)
    }

    @Test
    fun `old settings json without stdio field decodes as empty`() {
        assertTrue(JsonInstant.decodeFromString<Settings>("{}").stdioMcpServers.isEmpty())
    }

    @Test
    fun `normalization keeps assistant references to stdio servers`() {
        val server = stdioServer()
        val assistant = Settings().assistants.first().copy(mcpServers = setOf(server.id))
        val normalized = Settings(
            assistants = listOf(assistant),
            stdioMcpServers = listOf(server),
        ).normalized()

        val preserved = normalized.assistants.first { it.id == assistant.id }
        assertTrue(server.id in preserved.mcpServers)
    }

    @Test
    fun `official export drops stdio config and its assistant references`() {
        val http = McpServerConfig.StreamableHTTPServer(
            commonOptions = McpCommonOptions(name = "remote"),
        )
        val stdio = stdioServer()
        val assistant = Settings().assistants.first().copy(
            mcpServers = setOf(http.id, stdio.id),
        )

        val exported = Settings(
            mcpServers = listOf(http),
            stdioMcpServers = listOf(stdio),
            assistants = listOf(assistant),
        ).forOfficialExport()

        assertTrue(exported.stdioMcpServers.isEmpty())
        val exportedAssistant = exported.assistants.first { it.id == assistant.id }
        assertTrue(http.id in exportedAssistant.mcpServers)
        assertFalse(stdio.id in exportedAssistant.mcpServers)
    }
}
