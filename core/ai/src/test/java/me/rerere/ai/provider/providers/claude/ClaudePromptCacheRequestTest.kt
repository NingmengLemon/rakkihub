package me.rerere.ai.provider.providers.claude

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.ClaudePromptCacheStrategy
import me.rerere.ai.provider.ClaudePromptCacheTtl
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ClaudePromptCacheRequestTest {
    private lateinit var provider: ClaudeProvider

    @Before
    fun setUp() {
        provider = ClaudeProvider(OkHttpClient())
    }

    private fun buildRequest(
        providerSetting: ProviderSetting.Claude,
        messages: List<UIMessage>,
        params: TextGenerationParams,
        stream: Boolean = false
    ): JsonObject {
        val method = ClaudeProvider::class.java.getDeclaredMethod(
            "buildMessageRequest",
            ProviderSetting.Claude::class.java,
            List::class.java,
            TextGenerationParams::class.java,
            Boolean::class.javaPrimitiveType!!
        )
        method.isAccessible = true
        return method.invoke(provider, providerSetting, messages, params, stream) as JsonObject
    }

    private fun dummyTool(): Tool {
        return Tool(
            name = "dummy_tool",
            description = "dummy",
            parameters = { InputSchema.Obj(properties = JsonObject(emptyMap())) },
            execute = { emptyList() }
        )
    }

    private fun countCacheControl(element: JsonElement?): Int = when (element) {
        null -> 0
        is JsonObject -> element.entries.sumOf { (key, value) ->
            (if (key == "cache_control") 1 else 0) + countCacheControl(value)
        }
        is JsonArray -> element.sumOf { countCacheControl(it) }
        else -> 0
    }

    private fun assertNoMessageCacheControl(request: JsonObject) {
        request["messages"]!!.jsonArray.forEach { msg ->
            msg.jsonObject["content"]?.jsonArray?.forEach { block ->
                assertNull(block.jsonObject["cache_control"])
            }
        }
    }

    private fun JsonObject.realUserMessages(): List<JsonObject> =
        this["messages"]!!.jsonArray.map { it.jsonObject }.filter { msg ->
            msg["role"]?.jsonPrimitive?.content == "user" &&
                msg["content"]?.jsonArray?.any {
                    it.jsonObject["type"]?.jsonPrimitive?.content == "tool_result"
                } != true
        }

    @Test
    fun `promptCaching=false should not add cache_control anywhere`() {
        val providerSetting = ProviderSetting.Claude(
            promptCaching = false,
            promptCacheStrategy = ClaudePromptCacheStrategy.STANDARD,
        )
        val messages = listOf(
            UIMessage.system("system prompt"),
            UIMessage.user("hello")
        )
        val params = TextGenerationParams(
            model = Model(modelId = "claude-test", abilities = listOf(ModelAbility.TOOL)),
            tools = listOf(dummyTool())
        )

        val request = buildRequest(providerSetting, messages, params)

        assertNull(request["cache_control"])
        assertEquals(0, countCacheControl(request))
        assertNull(request["system"]!!.jsonArray.last().jsonObject["cache_control"])
        assertNull(request["tools"]!!.jsonArray.last().jsonObject["cache_control"])
        assertNoMessageCacheControl(request)
    }

    @Test
    fun `standard strategy uses the automatic and system breakpoints only`() {
        val providerSetting = ProviderSetting.Claude(
            promptCaching = true,
            promptCacheStrategy = ClaudePromptCacheStrategy.STANDARD,
        )
        val messages = listOf(
            UIMessage.system("system prompt"),
            UIMessage.user("hello")
        )
        val params = TextGenerationParams(
            model = Model(modelId = "claude-test", abilities = listOf(ModelAbility.TOOL)),
            tools = listOf(dummyTool())
        )

        val request = buildRequest(providerSetting, messages, params)

        // 顶层自动缓存 + system 静态断点；system 之后就是 tools，不需要再给 tool 单独加
        assertEquals(2, countCacheControl(request))
        assertNotNull(request["cache_control"])
        val systemCacheControl = request["system"]!!.jsonArray.last().jsonObject["cache_control"]!!.jsonObject
        assertEquals("ephemeral", systemCacheControl["type"]!!.jsonPrimitive.content)
        assertNull(systemCacheControl["ttl"])
        assertNull(request["tools"]!!.jsonArray.last().jsonObject["cache_control"])
        assertNoMessageCacheControl(request)
    }

    @Test
    fun `standard strategy without system uses the automatic and tool breakpoints`() {
        val providerSetting = ProviderSetting.Claude(
            promptCaching = true,
            promptCacheStrategy = ClaudePromptCacheStrategy.STANDARD,
        )
        val messages = listOf(UIMessage.user("hello"))
        val params = TextGenerationParams(
            model = Model(modelId = "claude-test", abilities = listOf(ModelAbility.TOOL)),
            tools = listOf(dummyTool())
        )

        val request = buildRequest(providerSetting, messages, params)

        assertEquals(2, countCacheControl(request))
        assertNotNull(request["cache_control"])
        assertNull(request["system"])
        val toolCacheControl = request["tools"]!!.jsonArray.last().jsonObject["cache_control"]!!.jsonObject
        assertEquals("ephemeral", toolCacheControl["type"]!!.jsonPrimitive.content)
        assertNull(toolCacheControl["ttl"])
        assertNoMessageCacheControl(request)
    }

    @Test
    fun `standard strategy with no system and no tools has one automatic breakpoint`() {
        val providerSetting = ProviderSetting.Claude(
            promptCaching = true,
            promptCacheStrategy = ClaudePromptCacheStrategy.STANDARD,
        )
        val messages = listOf(UIMessage.user("hello"))
        val params = TextGenerationParams(
            model = Model(modelId = "claude-test", abilities = emptyList()),
            tools = emptyList()
        )

        val request = buildRequest(providerSetting, messages, params)

        assertEquals(1, countCacheControl(request))
        assertNotNull(request["cache_control"])
        assertNull(request["system"])
        assertNull(request["tools"])
        assertNoMessageCacheControl(request)
    }

    @Test
    fun `standard one hour ttl is on the automatic and system breakpoints`() {
        val providerSetting = ProviderSetting.Claude(
            promptCaching = true,
            promptCacheStrategy = ClaudePromptCacheStrategy.STANDARD,
            promptCacheTtl = ClaudePromptCacheTtl.ONE_HOUR,
        )
        val messages = listOf(
            UIMessage.system("system prompt"),
            UIMessage.user("first question"),
            UIMessage.assistant("first answer"),
            UIMessage.user("second question")
        )
        val params = TextGenerationParams(
            model = Model(modelId = "claude-test", abilities = listOf(ModelAbility.TOOL)),
            tools = listOf(dummyTool())
        )

        val request = buildRequest(providerSetting, messages, params)

        assertEquals(2, countCacheControl(request))
        val automatic = request["cache_control"]!!.jsonObject
        assertEquals("1h", automatic["ttl"]!!.jsonPrimitive.content)
        val systemCacheControl = request["system"]!!.jsonArray.last().jsonObject["cache_control"]!!.jsonObject
        assertEquals("ephemeral", systemCacheControl["type"]!!.jsonPrimitive.content)
        assertEquals("1h", systemCacheControl["ttl"]!!.jsonPrimitive.content)
        assertNull(request["tools"]!!.jsonArray.last().jsonObject["cache_control"])
        assertNoMessageCacheControl(request)
    }

    @Test
    fun `standard strategy never adds message breakpoints`() {
        val providerSetting = ProviderSetting.Claude(
            promptCaching = true,
            promptCacheStrategy = ClaudePromptCacheStrategy.STANDARD,
        )
        val messages = listOf(
            UIMessage.system("system prompt"),
            UIMessage.user("first question"),
            UIMessage.assistant("first answer"),
            UIMessage.user("second question"),
            UIMessage.assistant("second answer"),
            UIMessage.user("third question")
        )
        val params = TextGenerationParams(
            model = Model(modelId = "claude-test", abilities = emptyList()),
            tools = emptyList()
        )

        val request = buildRequest(providerSetting, messages, params)

        assertEquals(2, countCacheControl(request))
        assertNoMessageCacheControl(request)
    }

    @Test
    fun `standard strategy with only one user message has no message breakpoint`() {
        val providerSetting = ProviderSetting.Claude(
            promptCaching = true,
            promptCacheStrategy = ClaudePromptCacheStrategy.STANDARD,
        )
        val messages = listOf(
            UIMessage.system("system prompt"),
            UIMessage.user("only question")
        )
        val params = TextGenerationParams(
            model = Model(modelId = "claude-test", abilities = emptyList()),
            tools = emptyList()
        )

        val request = buildRequest(providerSetting, messages, params)

        assertEquals(2, countCacheControl(request))
        assertNoMessageCacheControl(request)
    }

    @Test
    fun `aggressive is the default and keeps the original four breakpoints`() {
        val providerSetting = ProviderSetting.Claude(promptCaching = true)
        val messages = listOf(
            UIMessage.system("system prompt"),
            UIMessage.user("first question"),
            UIMessage.assistant("first answer"),
            UIMessage.user("second question")
        )
        val params = TextGenerationParams(
            model = Model(modelId = "claude-test", abilities = listOf(ModelAbility.TOOL)),
            tools = listOf(dummyTool())
        )

        val request = buildRequest(providerSetting, messages, params)

        // 顶层 + system + tool + 第二条 user = 上游原来的 4 个断点
        assertEquals(4, countCacheControl(request))
        assertNotNull(request["cache_control"])
        assertNotNull(request["system"]!!.jsonArray.last().jsonObject["cache_control"])
        assertNotNull(request["tools"]!!.jsonArray.last().jsonObject["cache_control"])
        val realUsers = request.realUserMessages()
        assertEquals(2, realUsers.size)
        assertNotNull(realUsers.first()["content"]!!.jsonArray.last().jsonObject["cache_control"])
        assertNull(realUsers.last()["content"]!!.jsonArray.last().jsonObject["cache_control"])
    }

    @Test
    fun `aggressive without system uses the automatic, tool and message breakpoints`() {
        val providerSetting = ProviderSetting.Claude(promptCaching = true)
        val messages = listOf(
            UIMessage.user("first question"),
            UIMessage.assistant("first answer"),
            UIMessage.user("second question"),
        )
        val params = TextGenerationParams(
            model = Model(modelId = "claude-test", abilities = listOf(ModelAbility.TOOL)),
            tools = listOf(dummyTool())
        )

        val request = buildRequest(providerSetting, messages, params)

        assertEquals(3, countCacheControl(request))
        assertNotNull(request["cache_control"])
        assertNull(request["system"])
        assertNotNull(request["tools"]!!.jsonArray.last().jsonObject["cache_control"])
        val realUsers = request.realUserMessages()
        assertNotNull(realUsers.first()["content"]!!.jsonArray.last().jsonObject["cache_control"])
        assertNull(realUsers.last()["content"]!!.jsonArray.last().jsonObject["cache_control"])
    }

    @Test
    fun `aggressive with one user message has no message breakpoint`() {
        val providerSetting = ProviderSetting.Claude(promptCaching = true)
        val messages = listOf(
            UIMessage.system("system prompt"),
            UIMessage.user("only question"),
        )
        val params = TextGenerationParams(
            model = Model(modelId = "claude-test", abilities = listOf(ModelAbility.TOOL)),
            tools = listOf(dummyTool())
        )

        val request = buildRequest(providerSetting, messages, params)

        assertEquals(3, countCacheControl(request))
        assertNotNull(request["cache_control"])
        assertNotNull(request["system"]!!.jsonArray.last().jsonObject["cache_control"])
        assertNotNull(request["tools"]!!.jsonArray.last().jsonObject["cache_control"])
        assertNoMessageCacheControl(request)
    }

    @Test
    fun `aggressive one hour ttl is on all four breakpoints`() {
        val providerSetting = ProviderSetting.Claude(
            promptCaching = true,
            promptCacheTtl = ClaudePromptCacheTtl.ONE_HOUR,
        )
        val messages = listOf(
            UIMessage.system("system prompt"),
            UIMessage.user("first question"),
            UIMessage.assistant("first answer"),
            UIMessage.user("second question"),
        )
        val params = TextGenerationParams(
            model = Model(modelId = "claude-test", abilities = listOf(ModelAbility.TOOL)),
            tools = listOf(dummyTool())
        )

        val request = buildRequest(providerSetting, messages, params)

        assertEquals(4, countCacheControl(request))
        assertEquals("1h", request["cache_control"]!!.jsonObject["ttl"]!!.jsonPrimitive.content)
        assertEquals(
            "1h",
            request["system"]!!.jsonArray.last().jsonObject["cache_control"]!!.jsonObject["ttl"]!!
                .jsonPrimitive.content,
        )
        assertEquals(
            "1h",
            request["tools"]!!.jsonArray.last().jsonObject["cache_control"]!!.jsonObject["ttl"]!!
                .jsonPrimitive.content,
        )
        val target = request.realUserMessages().first()["content"]!!.jsonArray.last().jsonObject
        assertEquals("1h", target["cache_control"]!!.jsonObject["ttl"]!!.jsonPrimitive.content)
    }

    @Test
    fun `aggressive marks only the second-to-last of three real users`() {
        val providerSetting = ProviderSetting.Claude(promptCaching = true)
        val messages = listOf(
            UIMessage.system("system prompt"),
            UIMessage.user("first question"),
            UIMessage.assistant("first answer"),
            UIMessage.user("second question"),
            UIMessage.assistant("second answer"),
            UIMessage.user("third question"),
        )
        val params = TextGenerationParams(
            model = Model(modelId = "claude-test", abilities = listOf(ModelAbility.TOOL)),
            tools = listOf(dummyTool())
        )

        val request = buildRequest(providerSetting, messages, params)

        assertEquals(4, countCacheControl(request))
        val realUsers = request.realUserMessages()
        assertEquals(3, realUsers.size)
        assertNull(realUsers[0]["content"]!!.jsonArray.last().jsonObject["cache_control"])
        assertNotNull(realUsers[1]["content"]!!.jsonArray.last().jsonObject["cache_control"])
        assertNull(realUsers[2]["content"]!!.jsonArray.last().jsonObject["cache_control"])
    }

    @Test
    fun `aggressive with no system and no tools has automatic and message breakpoints`() {
        val providerSetting = ProviderSetting.Claude(promptCaching = true)
        val messages = listOf(
            UIMessage.user("first question"),
            UIMessage.assistant("first answer"),
            UIMessage.user("second question"),
        )
        val params = TextGenerationParams(
            model = Model(modelId = "claude-test", abilities = emptyList()),
            tools = emptyList()
        )

        val request = buildRequest(providerSetting, messages, params)

        assertEquals(2, countCacheControl(request))
        assertNotNull(request["cache_control"])
        assertNull(request["system"])
        assertNull(request["tools"])
        val realUsers = request.realUserMessages()
        assertNotNull(realUsers.first()["content"]!!.jsonArray.last().jsonObject["cache_control"])
        assertNull(realUsers.last()["content"]!!.jsonArray.last().jsonObject["cache_control"])
    }
}
