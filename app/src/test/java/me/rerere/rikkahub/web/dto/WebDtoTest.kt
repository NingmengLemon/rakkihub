package me.rerere.rikkahub.web.dto

import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.toMessageNode
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.uuid.Uuid

class WebDtoTest {
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
