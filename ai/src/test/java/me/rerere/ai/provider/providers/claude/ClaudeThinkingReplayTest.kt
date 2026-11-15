package me.rerere.ai.provider.providers.claude

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.TextGenerationResult
import me.rerere.ai.provider.stream.SseEvent
import me.rerere.ai.ui.ClaudeReasoningMetadata
import me.rerere.ai.ui.MessageStep
import me.rerere.ai.ui.StepStatus
import me.rerere.ai.ui.StreamChunkHandler
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.toMetadata
import me.rerere.ai.util.json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Clock

class ClaudeThinkingReplayTest {
    private val provider = ClaudeProvider(OkHttpClient())
    private val model = Model(modelId = "claude-test")

    @Test
    fun `non streaming adjacent thinking blocks should keep their own signatures`() {
        val content = blocks(
            """
            [
                {"type":"thinking","thinking":"Thought A","signature":"sig-A"},
                {"type":"thinking","thinking":"Thought B","signature":"sig-B"},
                {"type":"text","text":"answer"}
            ]
            """
        )

        val message = provider.parseMessage(content)

        assertEquals(
            content.toList(),
            assistantBlocks(listOf(UIMessage.user("first"), message, UIMessage.user("second"))),
        )
    }

    @Test
    fun `pause turn continuation should not merge thinking blocks of different responses`() = runBlocking {
        val first = blocks("""[{"type":"thinking","thinking":"Thought A","signature":"sig-A"}]""")
        val second = blocks(
            """
            [
                {"type":"thinking","thinking":"Thought B","signature":"sig-B"},
                {"type":"text","text":"answer"}
            ]
            """
        )
        val history = listOf(UIMessage.user("first"))
        var calls = 0

        val result = generateClaudeWithPauseTurn(history, model) { messages ->
            if (calls++ == 0) {
                TextGenerationResult("resp-1", model.modelId, provider.parseMessage(first), "pause_turn")
            } else {
                assertEquals(first.toList(), assistantBlocks(messages))
                TextGenerationResult("resp-2", model.modelId, provider.parseMessage(second), "end_turn")
            }
        }

        assertEquals(
            first.toList() + second.toList(),
            assistantBlocks(history + result.message + UIMessage.user("second")),
        )
    }

    @Test
    fun `non streaming redacted thinking should be replayed`() {
        val content = blocks(
            """
            [
                {"type":"redacted_thinking","data":"opaque-data"},
                {"type":"text","text":"answer"}
            ]
            """
        )

        val message = provider.parseMessage(content)

        assertEquals(
            content.toList(),
            assistantBlocks(listOf(UIMessage.user("first"), message, UIMessage.user("second"))),
        )
    }

    @Test
    fun `streamed signature deltas should accumulate within their block`() {
        val message = stream(
            "content_block_start" to
                """{"index":0,"content_block":{"type":"thinking","thinking":"","signature":""}}""",
            "content_block_delta" to
                """{"index":0,"delta":{"type":"thinking_delta","thinking":"Thought A"}}""",
            "content_block_delta" to
                """{"index":0,"delta":{"type":"signature_delta","signature":"sig-"}}""",
            "content_block_delta" to
                """{"index":0,"delta":{"type":"signature_delta","signature":"A"}}""",
            "content_block_stop" to """{"index":0}""",
            "content_block_start" to
                """{"index":1,"content_block":{"type":"thinking","thinking":""}}""",
            "content_block_delta" to
                """{"index":1,"delta":{"type":"thinking_delta","thinking":"Thought B"}}""",
            "content_block_delta" to
                """{"index":1,"delta":{"type":"signature_delta","signature":"sig-B"}}""",
            "content_block_stop" to """{"index":1}""",
            "content_block_start" to """{"index":2,"content_block":{"type":"text","text":""}}""",
            "content_block_delta" to """{"index":2,"delta":{"type":"text_delta","text":"answer"}}""",
            "content_block_stop" to """{"index":2}""",
            "message_stop" to "{}",
        )

        assertEquals(
            blocks(
                """
                [
                    {"type":"thinking","thinking":"Thought A","signature":"sig-A"},
                    {"type":"thinking","thinking":"Thought B","signature":"sig-B"},
                    {"type":"text","text":"answer"}
                ]
                """
            ).toList(),
            assistantBlocks(listOf(UIMessage.user("first"), message, UIMessage.user("second"))),
        )
    }

    @Test
    fun `streamed redacted thinking should be replayed`() {
        val message = stream(
            "content_block_start" to
                """{"index":0,"content_block":{"type":"redacted_thinking","data":"opaque-data"}}""",
            "content_block_stop" to """{"index":0}""",
            "content_block_start" to """{"index":1,"content_block":{"type":"text","text":""}}""",
            "content_block_delta" to """{"index":1,"delta":{"type":"text_delta","text":"answer"}}""",
            "content_block_stop" to """{"index":1}""",
            "message_stop" to "{}",
        )

        assertEquals(
            blocks(
                """
                [
                    {"type":"redacted_thinking","data":"opaque-data"},
                    {"type":"text","text":"answer"}
                ]
                """
            ).toList(),
            assistantBlocks(listOf(UIMessage.user("first"), message, UIMessage.user("second"))),
        )
    }

    @Test
    fun `legacy relay replay strips all thinking because steps are unknown`() {
        val message = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(
                reasoning("sig-1"),
                UIMessagePart.Text("first"),
                tool("tool-1"),
                reasoning("sig-2"),
                UIMessagePart.Text("second"),
                tool("tool-2"),
            ),
        )

        val blocks = assistantBlocks(listOf(UIMessage.user("first"), message), stripHistoricalThinking = true)
        val thinking = blocks.filter { it["type"]?.jsonPrimitive?.content == "thinking" }

        assertEquals(0, thinking.size)
        assertEquals(2, blocks.count { it["type"]?.jsonPrimitive?.content == "tool_use" })
    }

    @Test
    fun `stepped relay replay keeps thinking paired with its own tool group`() {
        val message = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(
                reasoning("sig-1"),
                UIMessagePart.Text("first"),
                tool("tool-1"),
                reasoning("sig-2"),
                UIMessagePart.Text("second"),
                tool("tool-2"),
            ),
            steps = listOf(
                step(StepStatus.SUCCEEDED, 0, 3),
                step(StepStatus.SUCCEEDED, 3, 6),
            ),
        )

        val blocks = assistantBlocks(listOf(UIMessage.user("first"), message), stripHistoricalThinking = true)
        val thinking = blocks.filter { it["type"]?.jsonPrimitive?.content == "thinking" }

        assertEquals(2, thinking.size)
        assertEquals(2, blocks.count { it["type"]?.jsonPrimitive?.content == "tool_use" })
    }

    @Test
    fun `stepped relay replay drops thinking from a previous text only step`() {
        val message = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(
                reasoning("sig-1"),
                UIMessagePart.Text("thinking only"),
                UIMessagePart.Tool(
                    toolCallId = "tool-1",
                    toolName = "read",
                    input = "{}",
                    output = listOf(UIMessagePart.Text("ok")),
                ),
            ),
            steps = listOf(
                step(StepStatus.SUCCEEDED, 0, 2),
                step(StepStatus.TOOLS, 2, 3),
            ),
        )

        val blocks = assistantBlocks(listOf(UIMessage.user("first"), message), stripHistoricalThinking = true)

        assertTrue(blocks.none { it["type"]?.jsonPrimitive?.content == "thinking" })
        assertEquals(1, blocks.count { it["type"]?.jsonPrimitive?.content == "tool_use" })
    }

    @Test
    fun `relay replay strips thinking when there is no tool continuation`() {
        val message = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(reasoning("sig-1"), UIMessagePart.Text("answer")),
        )

        val blocks = assistantBlocks(
            listOf(UIMessage.user("first"), message, UIMessage.user("next")),
            stripHistoricalThinking = true,
        )

        assertTrue(blocks.none { it["type"]?.jsonPrimitive?.content in setOf("thinking", "redacted_thinking") })
    }

    @Test
    fun `relay replay strips historical redacted thinking`() {
        val redacted = UIMessagePart.Reasoning(
            reasoning = "",
            metadata = ClaudeReasoningMetadata(redactedData = "opaque").toMetadata(),
        )
        val message = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(redacted, UIMessagePart.Text("answer")),
        )

        val blocks = assistantBlocks(
            listOf(UIMessage.user("first"), message, UIMessage.user("next")),
            stripHistoricalThinking = true,
        )

        assertTrue(blocks.none { it["type"]?.jsonPrimitive?.content == "redacted_thinking" })
    }

    @Test
    fun `only custom relays drop legacy thinking`() {
        assertTrue(shouldDropLegacyThinking("https://api.site/claude"))
        assertTrue(shouldDropLegacyThinking("not a url"))
        assertFalse(shouldDropLegacyThinking("https://api.anthropic.com/v1"))
    }

    @Test
    fun `stepped replay skips a step whose content converts to nothing`() {
        val message = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(UIMessagePart.Text("")),
            steps = listOf(step(StepStatus.SUCCEEDED, 0, 1)),
        )

        val blocks = assistantBlocks(listOf(UIMessage.user("first"), message), stripHistoricalThinking = true)

        assertTrue(blocks.isEmpty())
    }

    @Test
    fun `official replay keeps all thinking blocks`() {
        val message = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(
                reasoning("sig-1"),
                tool("tool-1"),
                reasoning("sig-2"),
                tool("tool-2"),
            ),
        )

        val blocks = assistantBlocks(listOf(UIMessage.user("first"), message))
        val thinking = blocks.filter { it["type"]?.jsonPrimitive?.content == "thinking" }

        assertEquals(2, thinking.size)
    }

    private fun reasoning(signature: String) = UIMessagePart.Reasoning(
        reasoning = "thought",
        metadata = ClaudeReasoningMetadata(signature = signature).toMetadata(),
    )

    private fun tool(id: String) = UIMessagePart.Tool(
        toolCallId = id,
        toolName = "read",
        input = "{}",
        output = listOf(UIMessagePart.Text("ok")),
    )

    private fun step(status: StepStatus, start: Int, end: Int) = MessageStep(
        status = status,
        startPartIndex = start,
        endPartIndex = end,
        startedAt = Clock.System.now(),
    )

    private fun blocks(value: String): JsonArray = json.parseToJsonElement(value).jsonArray

    private fun assistantBlocks(messages: List<UIMessage>, stripHistoricalThinking: Boolean = false): List<JsonObject> {
        val method = ClaudeProvider::class.java.getDeclaredMethod(
            "buildMessages",
            List::class.java,
            Boolean::class.javaPrimitiveType,
        )
        method.isAccessible = true
        val request = method.invoke(provider, messages, stripHistoricalThinking) as JsonArray
        return request
            .filter { it.jsonObject["role"]?.jsonPrimitive?.content == "assistant" }
            .flatMap { message -> message.jsonObject.getValue("content").jsonArray.map { it.jsonObject } }
    }

    private fun stream(vararg events: Pair<String, String>): UIMessage {
        val decoder = ClaudeStreamDecoder()
        val handler = StreamChunkHandler(model)
        var messages = listOf(UIMessage.user("first"))
        events.forEach { (name, data) ->
            decoder.accept(SseEvent(event = name, data = data)).chunks.forEach {
                messages = handler.handle(messages, it)
            }
        }
        // 模拟落盘后再读出来
        return json.decodeFromString<UIMessage>(json.encodeToString(UIMessage.serializer(), messages.last()))
    }
}
