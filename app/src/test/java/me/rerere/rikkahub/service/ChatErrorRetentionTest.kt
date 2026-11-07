package me.rerere.rikkahub.service

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.uuid.Uuid

class ChatErrorRetentionTest {
    private val conversation = Uuid.random()
    private val otherConversation = Uuid.random()

    private fun failure(
        conversationId: Uuid = conversation,
        message: String = "boom",
        title: String? = null,
    ) = ChatError(
        title = title,
        error = IllegalStateException(message),
        conversationId = conversationId,
    )

    @Test
    fun `same failure keeps only the newest card`() {
        val first = failure()
        val second = failure()

        val kept = listOf(first).withNewError(second)

        assertEquals(listOf(second.id), kept.map { it.id })
    }

    @Test
    fun `different failures are both kept`() {
        val first = failure(message = "boom")
        val second = failure(message = "bang")

        val kept = listOf(first).withNewError(second)

        assertEquals(listOf(first.id, second.id), kept.map { it.id })
    }

    @Test
    fun `the same failure in another conversation is kept`() {
        val first = failure()
        val second = failure(conversationId = otherConversation)

        val kept = listOf(first).withNewError(second)

        assertEquals(listOf(first.id, second.id), kept.map { it.id })
    }

    @Test
    fun `the default cap keeps the newest five`() {
        val all = (1..7).map { failure(message = "mine $it") }

        var kept = emptyList<ChatError>()
        all.forEach { kept = kept.withNewError(it) }

        assertEquals(all.takeLast(5).map { it.id }, kept.map { it.id })
    }

    @Test
    fun `the cap applies per conversation`() {
        val mine = (1..7).map { failure(message = "mine $it") }
        val theirs = (1..2).map { failure(conversationId = otherConversation, message = "theirs $it") }

        var kept = emptyList<ChatError>()
        (mine + theirs).forEach { kept = kept.withNewError(it, maxPerConversation = 2) }

        // 自己会话只留最近两条，另一个会话的两条都还在
        assertEquals(mine.takeLast(2).map { it.id } + theirs.map { it.id }, kept.map { it.id })
    }
}
