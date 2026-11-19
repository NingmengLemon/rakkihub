package me.rerere.ai.provider.providers.claude

import me.rerere.ai.provider.stream.SseEvent
import me.rerere.ai.ui.StreamChunk
import org.junit.Assert.assertEquals
import org.junit.Test

class ClaudeStreamDecoderTest {
    @Test
    fun `ping becomes a heartbeat chunk`() {
        val decoder = ClaudeStreamDecoder()

        val result = decoder.accept(
            SseEvent(event = "ping", data = """{"type":"ping"}""")
        )

        assertEquals(listOf<StreamChunk>(StreamChunk.Heartbeat), result.chunks)
    }
}
