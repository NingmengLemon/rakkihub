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

    @Test
    fun `boundary excludes the last assistant reply from the request window`() {
        val conversation = conversationOf(9, trailingAssistant = true)

        // 上限 2：含回复时起点是回复（9），去掉回复后起点落在最后一条 user（8）
        assertEquals(8, conversation.contextLimitBoundaryIndex(2))
    }

    @Test
    fun `trailing assistant reply keeps the checkpoint guard`() {
        val conversation = conversationOf(6, checkpointAt = 2, trailingAssistant = true)

        // 去掉回复后 6 条，上限 2 起点 5，仍在检查点（2）之后
        assertEquals(5, conversation.contextLimitBoundaryIndex(2))
    }

    @Test
    fun `trailing assistant reply without truncation returns null`() {
        val conversation = conversationOf(3, trailingAssistant = true)

        // 上限 3：去掉回复后正好 3 条，不截断
        assertNull(conversation.contextLimitBoundaryIndex(3))
    }

    @Test
    fun `compression scratch pair hides the boundary`() {
        val conversation = Conversation(
            assistantId = Uuid.random(),
            messageNodes = List(9) { index -> UIMessage.user("message $index").toMessageNode() } +
                UIMessage.user("compress").copy(isCompressionPrompt = true).toMessageNode() +
                UIMessage.assistant("summary").toMessageNode(),
        )

        assertNull(conversation.contextLimitBoundaryIndex(2))
    }

    private fun conversationOf(
        size: Int,
        checkpointAt: Int = -1,
        trailingAssistant: Boolean = false,
    ) = Conversation(
        assistantId = Uuid.random(),
        messageNodes = List(size) { index ->
            UIMessage.user("message $index")
                .copy(isContextCheckpoint = index == checkpointAt)
                .toMessageNode()
        } + if (trailingAssistant) {
            listOf(UIMessage.assistant("reply").toMessageNode())
        } else {
            emptyList()
        },
    )
}
