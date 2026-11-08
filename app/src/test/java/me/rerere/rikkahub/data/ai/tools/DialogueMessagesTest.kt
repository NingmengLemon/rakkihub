package me.rerere.rikkahub.data.ai.tools

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.MessageStatus
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.toMessageNode
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.uuid.Uuid

class DialogueMessagesTest {
    @Test
    fun `failed replies stay out of dialogue history`() {
        val user = UIMessage.user("question")
        val failed = UIMessage.assistant("partial").copy(status = MessageStatus.FAILED)
        val answer = UIMessage.assistant("answer")
        val system = UIMessage.system("system")
        val conversation = Conversation.ofId(Uuid.random()).copy(
            messageNodes = listOf(
                user.toMessageNode(),
                failed.toMessageNode(),
                answer.toMessageNode(),
                system.toMessageNode(),
            )
        )

        assertEquals(
            listOf(user.id, answer.id),
            conversation.dialogueMessages().map { it.id },
        )
        assertEquals(MessageRole.USER, conversation.dialogueMessages().first().role)
    }
}
