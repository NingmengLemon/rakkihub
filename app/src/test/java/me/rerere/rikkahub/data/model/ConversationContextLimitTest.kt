package me.rerere.rikkahub.data.model

import me.rerere.ai.ui.UIMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.uuid.Uuid

class ConversationContextLimitTest {
    @Test
    fun `no boundary without a limit or without count truncation`() {
        val conversation = conversationOf(10)

        assertNull(conversation.contextLimitBoundaryIndex(0))
        assertNull(conversation.contextLimitBoundaryIndex(20))
    }

    @Test
    fun `checkpoint boundary alone does not draw a line`() {
        val conversation = conversationOf(6, checkpointAt = 2)

        // 检查点之后只有 3 条，上限 10 不截断
        assertNull(conversation.contextLimitBoundaryIndex(10))
    }

    @Test
    fun `boundary is the first kept message when the limit truncates`() {
        val conversation = conversationOf(10)

        // 上限 5: 步幅 2, 起点 6, 保留 4 条
        assertEquals(6, conversation.contextLimitBoundaryIndex(5))
    }

    @Test
    fun `boundary is measured after the last checkpoint`() {
        val conversation = conversationOf(20, checkpointAt = 3)

        // 检查点之后 16 条，上限 10 保留最后 6 条
        assertEquals(14, conversation.contextLimitBoundaryIndex(10))
    }

    private fun conversationOf(size: Int, checkpointAt: Int = -1) = Conversation(
        assistantId = Uuid.random(),
        messageNodes = List(size) { index ->
            UIMessage.user("message $index")
                .copy(isContextCheckpoint = index == checkpointAt)
                .toMessageNode()
        },
    )
}
