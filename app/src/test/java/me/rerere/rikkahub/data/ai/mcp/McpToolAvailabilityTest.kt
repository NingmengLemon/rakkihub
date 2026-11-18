package me.rerere.rikkahub.data.ai.mcp

import me.rerere.rikkahub.data.model.Assistant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class McpToolAvailabilityTest {
    private val serverId = Uuid.random()
    private val otherId = Uuid.random()

    private fun server(
        id: Uuid,
        name: String,
        enabled: Boolean = true,
        tools: List<McpTool> = listOf(McpTool(name = "echo")),
    ) = McpServerConfig.StreamableHTTPServer(
        id = id,
        commonOptions = McpCommonOptions(enable = enabled, name = name, tools = tools),
    )

    @Test
    fun `stop helper includes persisted and registry markers`() {
        assertTrue(isMcpServerStopped(serverId, setOf(serverId), { false }))
        assertTrue(isMcpServerStopped(serverId, emptySet(), { it == serverId }))
        assertFalse(isMcpServerStopped(serverId, setOf(otherId), { false }))
    }

    @Test
    fun `returns enabled tools from bound servers`() {
        val result = availableMcpTools(
            servers = listOf(server(serverId, "Echo")),
            assistant = Assistant(mcpServers = setOf(serverId)),
            isStopped = { false },
        )

        assertEquals(listOf("echo"), result.map { it.third.name })
    }

    @Test
    fun `omits manually stopped servers`() {
        val result = availableMcpTools(
            servers = listOf(server(serverId, "Echo"), server(otherId, "Other")),
            assistant = Assistant(mcpServers = setOf(serverId, otherId)),
            isStopped = { it == serverId },
        )

        assertEquals(listOf("Other"), result.map { it.second })
    }

    @Test
    fun `omits unbound and disabled servers and disabled tools`() {
        val noToolsId = Uuid.random()
        val result = availableMcpTools(
            servers = listOf(
                server(serverId, "Echo"),
                server(otherId, "Disabled", enabled = false),
                server(Uuid.random(), "Unbound"),
                server(noToolsId, "NoTools", tools = listOf(McpTool(name = "off", enable = false))),
            ),
            assistant = Assistant(mcpServers = setOf(serverId, otherId, noToolsId)),
            isStopped = { false },
        )

        assertEquals(listOf("Echo"), result.map { it.second })
    }

    @Test
    fun `returns empty list when every bound server is stopped`() {
        val result = availableMcpTools(
            servers = listOf(server(serverId, "Echo")),
            assistant = Assistant(mcpServers = setOf(serverId)),
            isStopped = { true },
        )

        assertEquals(emptyList<Triple<Uuid, String, McpTool>>(), result)
    }
}
