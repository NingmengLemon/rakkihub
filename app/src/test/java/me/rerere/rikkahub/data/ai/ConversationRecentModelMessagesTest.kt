package me.rerere.rikkahub.data.ai

import me.rerere.ai.ui.MessageStatus
import me.rerere.ai.ui.MessageStep
import me.rerere.ai.ui.StepStatus
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.toMessageNode
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Clock
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

    @Test
    fun `failed replies with successful steps stay in title context`() {
        val user = UIMessage.user("question")
        val failed = UIMessage.assistant("step one").copy(
            status = MessageStatus.FAILED,
            parts = listOf(UIMessagePart.Text("step one"), UIMessagePart.Text("failed step")),
            steps = listOf(
                MessageStep(
                    status = StepStatus.SUCCEEDED,
                    startPartIndex = 0,
                    endPartIndex = 1,
                    startedAt = Clock.System.now(),
                ),
                MessageStep(
                    status = StepStatus.FAILED,
                    startPartIndex = 1,
                    endPartIndex = 2,
                    startedAt = Clock.System.now(),
                ),
            ),
        )
        val conversation = Conversation.ofId(Uuid.random()).copy(
            messageNodes = listOf(
                user.toMessageNode(),
                failed.toMessageNode(),
            )
        )

        val recent = conversation.recentModelMessages(10)
        assertEquals(listOf(user.id, failed.id), recent.map { it.id })
        assertEquals(listOf(UIMessagePart.Text("step one")), recent.last().parts)
    }

    @Test
    fun `compression scratch stays out of title context`() {
        val user = UIMessage.user("question")
        val prompt = UIMessage.user("compress").copy(isCompressionPrompt = true)
        val half = UIMessage.assistant("half")
        val answer = UIMessage.assistant("answer")
        val conversation = Conversation.ofId(Uuid.random()).copy(
            messageNodes = listOf(
                user.toMessageNode(),
                prompt.toMessageNode(),
                half.toMessageNode(),
                answer.toMessageNode(),
            )
        )

        assertEquals(
            listOf(user.id, answer.id),
            conversation.recentModelMessages(10).map { it.id },
        )
    }
}
