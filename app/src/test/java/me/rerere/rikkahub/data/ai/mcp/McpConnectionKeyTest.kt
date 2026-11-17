package me.rerere.rikkahub.data.ai.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class McpConnectionKeyTest {
    private val base = McpServerConfig.StreamableHTTPServer(
        commonOptions = McpCommonOptions(name = "demo"),
        url = "https://example.com/mcp",
    )

    @Test
    fun `tool metadata does not affect connection key`() {
        val withTools = base.copy(
            commonOptions = base.commonOptions.copy(
                tools = listOf(McpTool(name = "search", enable = false))
            )
        )

        assertEquals(base.connectionKey(), withTools.connectionKey())
    }

    @Test
    fun `url transport and headers affect connection key`() {
        assertNotEquals(base.connectionKey(), base.copy(url = "https://example.com/other").connectionKey())
        assertNotEquals(
            base.connectionKey(),
            McpServerConfig.SseTransportServer(
                id = base.id,
                commonOptions = base.commonOptions,
                url = base.url,
            ).connectionKey()
        )
        assertNotEquals(
            base.connectionKey(),
            base.copy(
                commonOptions = base.commonOptions.copy(headers = listOf("X-API-Key" to "secret"))
            ).connectionKey()
        )
    }

    @Test
    fun `oauth token affects connection key unless manual authorization header wins`() {
        val oauth = McpOAuthState(enabled = true, accessToken = "oauth-token")
        val withOAuth = base.copy(commonOptions = base.commonOptions.copy(oauth = oauth))
        assertNotEquals(base.connectionKey(), withOAuth.connectionKey())

        val manualAuth = base.copy(
            commonOptions = base.commonOptions.copy(
                headers = listOf("Authorization" to "Bearer manual"),
                oauth = oauth,
            )
        )
        val manualAuthWithoutOAuth = manualAuth.copy(
            commonOptions = manualAuth.commonOptions.copy(oauth = null)
        )
        assertEquals(manualAuthWithoutOAuth.connectionKey(), manualAuth.connectionKey())
    }

    @Test
    fun `stdio command, args, env, cwd and workspace affect connection key`() {
        val stdio = McpServerConfig.StdioServer(
            commonOptions = McpCommonOptions(name = "local"),
            workspaceId = "workspace-1",
            command = "node",
            args = listOf("server.js"),
            env = listOf("TOKEN" to "secret"),
            cwd = "/workspace/app",
        )

        assertEquals("stdio", stdio.connectionKey().transportType)
        assertEquals("", stdio.serverUrl)
        assertNotEquals(stdio.connectionKey(), stdio.copy(command = "python").connectionKey())
        assertNotEquals(stdio.connectionKey(), stdio.copy(args = listOf("other.js")).connectionKey())
        assertNotEquals(stdio.connectionKey(), stdio.copy(env = emptyList()).connectionKey())
        assertNotEquals(stdio.connectionKey(), stdio.copy(cwd = "/workspace").connectionKey())
        assertNotEquals(stdio.connectionKey(), stdio.copy(workspaceId = "workspace-2").connectionKey())
        assertEquals(
            stdio.connectionKey(),
            stdio.copy(commonOptions = stdio.commonOptions.copy(tools = listOf(McpTool(name = "x")))).connectionKey()
        )
    }
}
