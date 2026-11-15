package me.rerere.rikkahub.web.dto

import me.rerere.ai.ui.MessageStatus
import me.rerere.ai.ui.MessageStep
import me.rerere.ai.ui.StepStatus
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.toMessageNode
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Clock
import kotlin.uuid.Uuid

class WebDtoTest {
    @Test
    fun `message dto carries status and steps`() {
        val message = UIMessage.assistant("done").copy(
            status = MessageStatus.INTERRUPTED,
            steps = listOf(MessageStep(StepStatus.CANCELLED, 0, 1, Clock.System.now())),
        )

        val dto = message.toDto()

        assertEquals(MessageStatus.INTERRUPTED, dto.status)
        assertEquals(StepStatus.CANCELLED, dto.steps.single().status)
    }

    @Test
    fun `conversation dto omits compression scratch pair`() {
        val user = UIMessage.user("question")
        val prompt = UIMessage.user("compress").copy(isCompressionPrompt = true)
        val reply = UIMessage.assistant("half")
        val answer = UIMessage.assistant("answer")
        val conversation = Conversation.ofId(Uuid.random()).copy(
            messageNodes = listOf(
                user.toMessageNode(),
                prompt.toMessageNode(),
                reply.toMessageNode(),
                answer.toMessageNode(),
            )
        )

        val dto = conversation.toDto()

        assertEquals(2, dto.messages.size)
        assertEquals(user.id.toString(), dto.messages[0].messages.single().id)
        assertEquals(answer.id.toString(), dto.messages[1].messages.single().id)
    }
}
