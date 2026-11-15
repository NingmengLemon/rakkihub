package me.rerere.rikkahub.data.ai.tools

import me.rerere.ai.ui.MessageStatus
import me.rerere.rikkahub.data.db.fts.MessageSearchResult
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ConversationToolsTest {
    @Test
    fun `failed and missing search results are dropped`() {
        val results = listOf(
            result("ok", "conversation", "ok"),
            result("failed", "conversation", "failed"),
            result("gone", "conversation", "gone"),
            result("other-conversation", "other", "ok"),
        )
        val statuses = mapOf(
            "conversation" to mapOf(
                "ok" to MessageStatus.COMPLETE,
                "failed" to MessageStatus.FAILED,
            ),
            "other" to mapOf(
                "ok" to MessageStatus.INTERRUPTED,
            ),
        )

        val visible = results.filterVisibleSearchResults(statuses)

        assertEquals(listOf("ok", "other-conversation"), visible.map { it.snippet })
    }

    private fun result(snippet: String, conversationId: String, messageId: String) = MessageSearchResult(
        nodeId = "node",
        messageId = messageId,
        conversationId = conversationId,
        title = "title",
        updateAt = Instant.now(),
        snippet = snippet,
    )
}
