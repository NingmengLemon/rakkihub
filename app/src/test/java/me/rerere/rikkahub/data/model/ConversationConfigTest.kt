package me.rerere.rikkahub.data.model

import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.ai.mcp.StdioMcpServer
import me.rerere.rikkahub.data.datastore.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ConversationConfigTest {
    private val modelA = Model(modelId = "model-a", displayName = "Model A", tools = setOf(BuiltInTools.Search))
    private val modelB = Model(modelId = "model-b", displayName = "Model B")
    private val mcpServer = Uuid.random()
    private val workspace = Uuid.random()

    private val assistant = Assistant(
        chatModelId = modelA.id,
        reasoningLevel = ReasoningLevel.HIGH,
        enableWebSearch = true,
        mcpServers = setOf(mcpServer),
        workspaceId = workspace,
        enabledSkills = setOf("skill"),
    )

    private fun settings(assistant: Assistant = this.assistant) = Settings(
        assistantId = assistant.id,
        assistants = listOf(assistant),
        chatModelId = modelB.id,
        providers = listOf(ProviderSetting.OpenAI(models = listOf(modelA, modelB))),
        mcpServers = listOf(McpServerConfig.StreamableHTTPServer(id = mcpServer)),
    )

    private fun conversation(assistant: Assistant = this.assistant) = Conversation(
        assistantId = assistant.id,
        messageNodes = emptyList(),
    )

    @Test
    fun `unstarted conversation follows the assistant`() {
        val conversation = conversation()

        assertSame(assistant, settings().getAssistantOf(conversation))
        assertEquals(modelA, settings().getChatModelOf(conversation))
    }

    @Test
    fun `binding snapshots the assistant configuration`() {
        val bound = conversation().bindConfig(settings())

        assertEquals(
            ConversationConfig(
                chatModelId = modelA.id,
                reasoningLevel = ReasoningLevel.HIGH,
                enableWebSearch = true,
                builtInSearch = true,
                mcpServers = setOf(mcpServer),
                workspaceId = workspace,
                enabledSkills = setOf("skill"),
            ),
            bound.config,
        )
        assertSame(bound, bound.bindConfig(settings(assistant.copy(reasoningLevel = ReasoningLevel.OFF))))
    }

    @Test
    fun `binding resolves the global default model`() {
        val assistant = assistant.copy(chatModelId = null)

        val bound = conversation(assistant).bindConfig(settings(assistant))

        assertEquals(modelB.id, bound.config?.chatModelId)
        assertEquals(false, bound.config?.builtInSearch)
    }

    @Test
    fun `bound conversation ignores later assistant changes`() {
        val bound = conversation().bindConfig(settings())
        val changed = assistant.copy(
            chatModelId = modelB.id,
            reasoningLevel = ReasoningLevel.OFF,
            enableWebSearch = false,
            mcpServers = emptySet(),
            workspaceId = null,
            enabledSkills = emptySet(),
            systemPrompt = "changed",
        )

        val resolved = settings(changed).getAssistantOf(bound)

        assertEquals(assistant.copy(systemPrompt = "changed"), resolved)
        assertEquals(modelA, settings(changed).getChatModelOf(bound))
    }

    @Test
    fun `bound built-in search overrides the model switch`() {
        val bound = conversation().bindConfig(settings())
        val disabled = bound.copy(config = bound.config?.copy(builtInSearch = false))
        val withoutModelSearch = settings().copy(
            providers = listOf(ProviderSetting.OpenAI(models = listOf(modelA.copy(tools = emptySet()), modelB))),
        )

        assertFalse(BuiltInTools.Search in settings().getChatModelOf(disabled)!!.tools)
        assertTrue(BuiltInTools.Search in withoutModelSearch.getChatModelOf(bound)!!.tools)
    }

    @Test
    fun `bound conversation falls back to the assistant model when its model is removed`() {
        val bound = conversation().bindConfig(settings())
        val withoutModelA = settings(assistant.copy(chatModelId = null)).copy(
            providers = listOf(ProviderSetting.OpenAI(models = listOf(modelB))),
        )

        assertEquals(modelB.id, withoutModelA.getChatModelOf(bound)?.id)
        // 会话上固定的内置搜索开关属于被删掉的模型，不能套到退回的模型上
        assertEquals(modelB, withoutModelA.getChatModelOf(bound))
    }

    @Test
    fun `binding again pins the assistant model once the pinned model is removed`() {
        val bound = conversation().bindConfig(settings())
        val withoutModelA = settings(assistant.copy(chatModelId = null)).copy(
            providers = listOf(ProviderSetting.OpenAI(models = listOf(modelB))),
        )

        val rebound = bound.bindConfig(withoutModelA)

        assertEquals(bound.config?.copy(chatModelId = modelB.id, builtInSearch = false), rebound.config)
        assertSame(rebound, rebound.bindConfig(withoutModelA))
    }

    @Test
    fun `bound conversation keeps stdio MCP references`() {
        val stdio = StdioMcpServer(workspaceId = "workspace-1", command = "node")
        val withStdio = assistant.copy(mcpServers = setOf(mcpServer, stdio.id))
        val base = settings(withStdio).copy(stdioMcpServers = listOf(stdio))
        val bound = conversation(withStdio).bindConfig(base)

        val resolved = base.getAssistantOf(bound)

        assertTrue(stdio.id in resolved.mcpServers)
        assertTrue(mcpServer in resolved.mcpServers)
    }

    @Test
    fun `bound conversation drops references to removed items`() {
        val bound = conversation().bindConfig(settings())
        val emptied = settings().copy(mcpServers = emptyList())

        val resolved = emptied.getAssistantOf(bound)

        assertEquals(emptySet<Uuid>(), resolved.mcpServers)
    }

    @Test
    fun `chat page update goes to the assistant before the conversation starts`() {
        val conversation = conversation()
        val updated = assistant.copy(reasoningLevel = ReasoningLevel.LOW)

        assertSame(conversation, conversation.withAssistantUpdate(updated, settings()))
        assertSame(updated, updated.withoutConversationFields(conversation, assistant))
    }

    @Test
    fun `chat page update stays on the started conversation`() {
        val bound = conversation().bindConfig(settings())
        val updated = settings().getAssistantOf(bound).copy(
            reasoningLevel = ReasoningLevel.LOW,
            quickMessageIds = setOf(Uuid.random()),
        )

        val conversation = bound.withAssistantUpdate(updated, settings())
        val stored = updated.withoutConversationFields(bound, assistant)

        assertEquals(ReasoningLevel.LOW, conversation.config?.reasoningLevel)
        // 不随会话固定的字段仍然写回助手
        assertEquals(assistant.copy(quickMessageIds = updated.quickMessageIds), stored)
    }

    @Test
    fun `switching model follows the new model's built-in search`() {
        val bound = conversation().bindConfig(settings())
        val updated = settings().getAssistantOf(bound).copy(chatModelId = modelB.id)

        val conversation = bound.withAssistantUpdate(updated, settings())

        assertEquals(modelB.id, conversation.config?.chatModelId)
        assertEquals(false, conversation.config?.builtInSearch)
    }

    @Test
    fun `switching workspace resets the working directory`() {
        val bound = conversation().bindConfig(settings()).copy(workspaceCwd = "/workspace/project")
        val assistantView = settings().getAssistantOf(bound)

        val sameWorkspace = bound.withAssistantUpdate(
            assistantView.copy(reasoningLevel = ReasoningLevel.LOW),
            settings()
        )
        val otherWorkspace = bound.withAssistantUpdate(assistantView.copy(workspaceId = Uuid.random()), settings())

        assertEquals("/workspace/project", sameWorkspace.workspaceCwd)
        assertNull(otherWorkspace.workspaceCwd)
    }
}
