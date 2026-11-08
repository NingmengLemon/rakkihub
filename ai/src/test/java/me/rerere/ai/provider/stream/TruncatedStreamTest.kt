package me.rerere.ai.provider.stream

import me.rerere.ai.provider.providers.claude.ClaudeStreamDecoder
import me.rerere.ai.provider.providers.google.GoogleStreamDecoder
import me.rerere.ai.provider.providers.google.InteractionsStreamDecoder
import me.rerere.ai.provider.providers.openai.ChatCompletionsStreamDecoder
import me.rerere.ai.provider.providers.openai.ResponseApiStreamDecoder
import me.rerere.ai.ui.StreamChunk
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class TruncatedStreamTest {
    @Test
    fun `closing without any event throws in every decoder`() {
        listOf(
            ClaudeStreamDecoder(),
            ChatCompletionsStreamDecoder(),
            ResponseApiStreamDecoder(),
            GoogleStreamDecoder(responseId = "response", model = "model"),
            InteractionsStreamDecoder(),
        ).forEach { decoder ->
            assertThrows(IOException::class.java) { decoder.onClosed() }
        }
    }

    @Test
    fun `closing after a partial event throws in every decoder`() {
        val claude = ClaudeStreamDecoder()
        claude.accept(SseEvent(data = """{"index":0,"content_block":{"type":"text"}}""", event = "content_block_start"))
        claude.accept(SseEvent(data = """{"index":0,"delta":{"type":"text_delta","text":"hi"}}""", event = "content_block_delta"))
        assertThrows(IOException::class.java) { claude.onClosed() }

        val chat = ChatCompletionsStreamDecoder()
        chat.accept(SseEvent(data = """{"choices":[{"delta":{"content":"hi"}}]}"""))
        assertThrows(IOException::class.java) { chat.onClosed() }

        val responses = ResponseApiStreamDecoder()
        responses.accept(SseEvent(data = """{"type":"response.output_text.delta","item_id":"msg_1","output_index":0,"content_index":0,"delta":"hi"}"""))
        assertThrows(IOException::class.java) { responses.onClosed() }

        val google = GoogleStreamDecoder(responseId = "response", model = "model")
        google.accept(SseEvent(data = """{"candidates":[{"content":{"role":"model","parts":[{"text":"hi"}]}}]}"""))
        assertThrows(IOException::class.java) { google.onClosed() }

        val interactions = InteractionsStreamDecoder()
        interactions.accept(SseEvent(data = """{"index":0,"step":{"type":"model_output","content":[{"type":"text","text":"hi"}]},"event_type":"step.start"}"""))
        assertThrows(IOException::class.java) { interactions.onClosed() }
    }

    @Test
    fun `each real terminal signal completes the stream`() {
        val claude = ClaudeStreamDecoder()
        assertTrue(claude.accept(SseEvent(event = "message_stop", data = "{}")).completed)
        assertTrue(claude.onClosed().isEmpty())

        val chat = ChatCompletionsStreamDecoder()
        chat.accept(SseEvent(data = """{"choices":[{"delta":{"content":"hi"},"finish_reason":"stop"}]}"""))
        assertTrue(chat.onClosed().any { it is StreamChunk.Finish })

        val responses = ResponseApiStreamDecoder()
        assertTrue(
            responses.accept(
                SseEvent(data = """{"type":"response.completed","response":{"id":"resp_1"}}""")
            ).completed
        )
        assertTrue(responses.onClosed().isEmpty())

        val google = GoogleStreamDecoder(responseId = "response", model = "model")
        google.accept(SseEvent(data = """{"candidates":[{"finishReason":"STOP","content":{"role":"model","parts":[{"text":"hi"}]}}]}"""))
        assertTrue(google.onClosed().any { it is StreamChunk.Finish })

        val interactions = InteractionsStreamDecoder()
        assertTrue(
            interactions.accept(
                SseEvent(data = """{"interaction":{"id":"interaction-1","status":"completed"},"event_type":"interaction.completed"}""")
            ).completed
        )
        assertTrue(interactions.onClosed().isEmpty())
    }
}
