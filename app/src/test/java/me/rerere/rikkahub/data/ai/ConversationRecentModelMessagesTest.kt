package me.rerere.rikkahub.data.ai

import me.rerere.ai.ui.MessageStatus
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.toMessageNode
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.uuid.Uuid

class ConversationRecentModelMessagesTest {
    @Test
    fun `failed replies stay out of title context`() {
        val user = UIMessage.user("question")
        val failed = UIMessage.assistant("partial").copy(status = MessageStatus.FAILED)
        val answer = UIMessage.assistant("answer")
        val conversation = Conversation.ofId(Uuid.random()).copy(
            messageNodes = listOf(
                user.toMessageNode(),
                failed.toMessageNode(),
                answer.toMessageNode(),
            )
        )

        assertEquals(
            listOf(user.id, answer.id),
            conversation.recentModelMessages(10).map { it.id },
        )
    }
}
