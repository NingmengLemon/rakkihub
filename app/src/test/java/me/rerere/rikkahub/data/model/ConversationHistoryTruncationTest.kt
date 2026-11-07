package me.rerere.rikkahub.data.model

import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.limitContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ConversationHistoryTruncationTest {
    @Test
    fun `history truncation marker is empty and only becomes the request start`() {
        val nodes = List(4) { UIMessage.user("message $it").toMessageNode() }
        val source = Conversation(assistantId = Uuid.random(), messageNodes = nodes)

        val result = source.insertHistoryTruncation(afterNodeId = nodes[1].id)!!

        assertEquals(nodes.subList(0, 2), result.messageNodes.subList(0, 2))
        assertEquals(nodes.subList(2, 4), result.messageNodes.subList(3, 5))
        val marker = result.messageNodes[2].currentMessage
        assertTrue(marker.isContextCheckpoint)
        assertEquals("", marker.toText())
        // 空标记不会被发给模型，只是后续请求的新起点
        assertFalse(marker.isValidToUpload())
        assertEquals(result.currentMessages.subList(2, 5), result.currentMessages.limitContext(0))
        // 窗口从标记之后算，标记本身不算一条消息
        assertEquals(3, result.contextWindowStart())
        assertEquals(2, result.messageNodes.size - result.contextWindowStart())
    }

    @Test
    fun `history truncation marker is not inserted when the anchor node is gone`() {
        val source = Conversation(
            assistantId = Uuid.random(),
            messageNodes = listOf(UIMessage.user("message").toMessageNode()),
        )

        assertNull(source.insertHistoryTruncation(afterNodeId = Uuid.random()))
    }

    @Test
    fun `empty checkpoint can be told apart from a compression summary`() {
        val summary = UIMessage.user("summary").copy(isContextCheckpoint = true)
        val marker = UIMessage.user("").copy(isContextCheckpoint = true)

        assertFalse(summary.toText().isBlank())
        assertTrue(marker.toText().isBlank())
    }
}
