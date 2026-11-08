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

    @Test
    fun `truncation clears the compression pair before placing the marker`() {
        val users = List(2) { UIMessage.user("message $it").toMessageNode() }
        val source = Conversation(
            assistantId = Uuid.random(),
            messageNodes = users + compressionPairNodes(),
        )

        val result = (source.planHistoryTruncation(1) as HistoryTruncation.Ready).conversation

        assertEquals(listOf("message 0"), result.messageNodes.take(1).map { it.currentMessage.toText() })
        assertTrue(result.messageNodes[1].currentMessage.isHistoryTruncationMarker)
        assertEquals(listOf("message 1"), result.messageNodes.drop(2).map { it.currentMessage.toText() })
        assertEquals(1, result.contextWindowSize())
        assertTrue(result.messageNodes.none { it.currentMessage.isCompressionPrompt })
    }

    @Test
    fun `truncation keep count ignores the compression pair`() {
        val users = List(6) { UIMessage.user("message $it").toMessageNode() }
        val source = Conversation(
            assistantId = Uuid.random(),
            messageNodes = users + compressionPairNodes(),
        )

        val result = (source.planHistoryTruncation(2) as HistoryTruncation.Ready).conversation

        assertEquals(2, result.contextWindowSize())
        assertEquals("message 5", result.messageNodes.last().currentMessage.toText())
    }

    @Test
    fun `truncation reports nothing to cut when only the pair remains`() {
        val source = Conversation(
            assistantId = Uuid.random(),
            messageNodes = compressionPairNodes(),
        )

        assertEquals(HistoryTruncation.NothingToCut, source.planHistoryTruncation(1))
    }

    @Test
    fun `truncation reports too many when keep is not smaller than the window`() {
        val source = Conversation(
            assistantId = Uuid.random(),
            messageNodes = List(2) { UIMessage.user("message $it").toMessageNode() },
        )

        val result = source.planHistoryTruncation(2)

        assertTrue(result is HistoryTruncation.KeepTooMany)
        assertEquals(2, (result as HistoryTruncation.KeepTooMany).windowSize)
    }

    private fun compressionPairNodes() = listOf(
        UIMessage.user("compress").copy(isCompressionPrompt = true).toMessageNode(),
        UIMessage.assistant("summary").toMessageNode(),
    )
}
