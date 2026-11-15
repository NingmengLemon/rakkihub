package me.rerere.rikkahub.data.repository

import me.rerere.ai.ui.MessageStatus
import me.rerere.rikkahub.data.db.fts.MessageSearchResult
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class SearchVisibilityTest {
    @Test
    fun `model search drops failed missing and scratch snippets`() {
        val results = listOf(
            result("ok", "conversation", "ok"),
            result("failed", "conversation", "failed"),
            result("gone", "conversation", "gone"),
            result("scratch", "conversation", "scratch"),
            result("other-conversation", "other", "ok"),
            result("missing-conversation", "missing", "ok"),
        )
        val contexts = mapOf(
            "conversation" to SearchConversationContext(
                scratchIds = setOf("scratch"),
                statuses = mapOf(
                    "ok" to MessageStatus.COMPLETE,
                    "failed" to MessageStatus.FAILED,
                    "scratch" to MessageStatus.COMPLETE,
                ),
            ),
            "other" to SearchConversationContext(
                scratchIds = emptySet(),
                statuses = mapOf("ok" to MessageStatus.INTERRUPTED),
            ),
        )

        val visible = results.filterSearchResults(contexts, dropFailed = true)

        assertEquals(listOf("ok", "other-conversation"), visible.map { it.snippet })
    }

    @Test
    fun `ui search keeps failed text but still hides scratch`() {
        val results = listOf(
            result("ok", "conversation", "ok"),
            result("failed", "conversation", "failed"),
            result("scratch", "conversation", "scratch"),
            result("missing-conversation", "missing", "ok"),
            result("gone", "conversation", "gone"),
        )
        val contexts = mapOf(
            "conversation" to SearchConversationContext(
                scratchIds = setOf("scratch"),
                statuses = mapOf(
                    "ok" to MessageStatus.COMPLETE,
                    "failed" to MessageStatus.FAILED,
                    "scratch" to MessageStatus.COMPLETE,
                ),
            ),
        )

        val visible = results.filterSearchResults(contexts, dropFailed = false)

        assertEquals(listOf("ok", "failed", "missing-conversation", "gone"), visible.map { it.snippet })
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
