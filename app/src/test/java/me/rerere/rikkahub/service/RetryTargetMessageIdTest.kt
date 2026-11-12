package me.rerere.rikkahub.service

import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.toMessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class RetryTargetMessageIdTest {
    @Test
    fun `regenerate target uses the node current message id`() {
        val user = UIMessage.user("hi")
        val oldReply = UIMessage.assistant("old")
        val currentReply = UIMessage.assistant("new")
        val assistantNode = MessageNode(
            messages = listOf(oldReply, currentReply),
            selectIndex = 1,
        )
        val conversation = Conversation.ofId(Uuid.random()).copy(
            messageNodes = listOf(user.toMessageNode(), assistantNode),
        )

        val target = conversation.retryTargetMessageId(assistantNode.id)

        assertEquals(currentReply.id, target)
        assertNotNull(target)
        assertEquals(
            assistantNode.id,
            conversation.getMessageNodeByMessageId(target!!)?.id,
        )
    }

    @Test
    fun `normal target falls back to the last user message`() {
        val user = UIMessage.user("hi")
        val next = UIMessage.user("next")
        val conversation = Conversation.ofId(Uuid.random()).copy(
            messageNodes = listOf(
                user.toMessageNode(),
                UIMessage.assistant("reply").toMessageNode(),
                next.toMessageNode(),
            ),
        )

        assertEquals(next.id, conversation.retryTargetMessageId(null))
    }

    @Test
    fun `later user turns make an earlier user retry stale`() {
        val first = UIMessage.user("first").toMessageNode()
        val firstReply = UIMessage.assistant("reply").toMessageNode()
        val second = UIMessage.user("second").toMessageNode()
        val conversation = Conversation.ofId(Uuid.random()).copy(
            messageNodes = listOf(first, firstReply, second),
        )

        assertTrue(conversation.hasLaterUserTurn(first.id))
        assertFalse(conversation.hasLaterUserTurn(second.id))
    }
}
