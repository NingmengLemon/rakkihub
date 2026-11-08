package me.rerere.ai.provider.stream

import me.rerere.ai.provider.providers.claude.ClaudeStreamDecoder
import me.rerere.ai.provider.providers.google.GoogleStreamDecoder
import me.rerere.ai.provider.providers.google.InteractionsStreamDecoder
import me.rerere.ai.provider.providers.openai.ChatCompletionsStreamDecoder
import me.rerere.ai.provider.providers.openai.ResponseApiStreamDecoder
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class TruncatedStreamTest {
    private val decoders: List<StreamChunkDecoder> = listOf(
        ClaudeStreamDecoder(),
        ChatCompletionsStreamDecoder(),
        ResponseApiStreamDecoder(),
        GoogleStreamDecoder(responseId = "response", model = "model"),
        InteractionsStreamDecoder(),
    )

    @Test
    fun `closing before a terminal event is an error`() {
        decoders.forEach { decoder ->
            assertThrows(IOException::class.java) { decoder.onClosed() }
        }
    }

    @Test
    fun `closing after a terminal event is normal`() {
        val done = SseEvent(data = "[DONE]")
        decoders.forEach { decoder ->
            val result = if (decoder is ClaudeStreamDecoder) {
                decoder.accept(SseEvent(event = "message_stop", data = "{}"))
            } else {
                decoder.accept(done)
            }
            assertTrue(result.completed)
            assertTrue(decoder.onClosed().isEmpty())
        }
    }
}
