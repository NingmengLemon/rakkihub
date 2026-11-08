package me.rerere.rikkahub.data.model

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class CompressionPlanTest {
    private fun conversation(nodes: List<MessageNode>) =
        Conversation.ofId(Uuid.random()).copy(messageNodes = nodes)

    @Test
    fun `plan sends only the range before the kept messages`() {
        val a = MessageNode.of(UIMessage.user("a"))
        val b = MessageNode.of(UIMessage.assistant("b"))
        val c = MessageNode.of(UIMessage.user("c"))
        val conversation = conversation(listOf(a, b, c))

        val plan = (conversation.planCompression(windowStart = 0, keepRecent = 1) as CompressionPlanResult.Ready).plan

        assertEquals(listOf("a", "b"), plan.messagesToSend.map { it.toText() })
        assertEquals(b.id, plan.anchorNodeId)
    }

    @Test
    fun `plan rejects empty ranges`() {
        val a = MessageNode.of(UIMessage.user("a"))
        val conversation = conversation(listOf(a))

        assertTrue(
            conversation.planCompression(windowStart = 1, keepRecent = 0) is CompressionPlanResult.NothingToCompress
        )
        assertTrue(
            conversation.planCompression(windowStart = 0, keepRecent = 1) is CompressionPlanResult.NothingToCompress
        )
    }

    @Test
    fun `apply result inserts the checkpoint at the anchor and removes the pair`() {
        val a = MessageNode.of(UIMessage.user("a"))
        val b = MessageNode.of(UIMessage.assistant("b"))
        val c = MessageNode.of(UIMessage.user("c"))
        val prompt = UIMessage.user("prompt").copy(isCompressionPrompt = true)
        val reply = UIMessage.assistant("summary")
        val withPair = conversation(listOf(a, b, c, prompt.toMessageNode(), reply.toMessageNode()))

        val applied = withPair.applyCompressionResult(
            anchorNodeId = b.id,
            promptMessageId = prompt.id,
            summary = "compressed",
        )!!

        assertEquals(listOf("a", "b", "compressed", "c"), applied.messageNodes.map { it.currentMessage.toText() })
        assertTrue(applied.messageNodes[2].currentMessage.isContextCheckpoint)
        assertTrue(applied.messageNodes.none { node -> node.messages.any { it.id == prompt.id } })
    }

    @Test
    fun `remove pair tolerates a missing reply`() {
        val a = MessageNode.of(UIMessage.user("a"))
        val prompt = UIMessage.user("prompt").copy(isCompressionPrompt = true)
        val conversation = conversation(listOf(a, prompt.toMessageNode()))

        val removed = conversation.removeCompressionPair(prompt.id)!!

        assertEquals(listOf("a"), removed.messageNodes.map { it.currentMessage.toText() })
    }

    @Test
    fun `remove pair ignores a normal message`() {
        val a = MessageNode.of(UIMessage.user("a"))
        val conversation = conversation(listOf(a))

        assertNull(conversation.removeCompressionPair(a.id))
    }

    @Test
    fun `truncated finish reasons are normalized`() {
        assertTrue(isTruncatedCompressionFinish("length"))
        assertTrue(isTruncatedCompressionFinish("max_tokens"))
        assertTrue(isTruncatedCompressionFinish("incomplete:max_output_tokens"))
        assertTrue(isTruncatedCompressionFinish("MAX_OUTPUT_TOKENS"))
        assertFalse(isTruncatedCompressionFinish("stop"))
        assertFalse(isTruncatedCompressionFinish(null))
    }

    @Test
    fun `plan rejects a pending tool in the range`() {
        val conversation = conversation(
            listOf(
                MessageNode.of(UIMessage.user("a")),
                toolNode(ToolApprovalState.Pending),
                MessageNode.of(UIMessage.user("c")),
            )
        )

        assertTrue(
            conversation.planCompression(windowStart = 0, keepRecent = 1) is CompressionPlanResult.PendingTools
        )
    }

    @Test
    fun `plan rejects a resumable approved tool in the range`() {
        val conversation = conversation(
            listOf(
                MessageNode.of(UIMessage.user("a")),
                toolNode(ToolApprovalState.Approved),
                MessageNode.of(UIMessage.user("c")),
            )
        )

        assertTrue(
            conversation.planCompression(windowStart = 0, keepRecent = 1) is CompressionPlanResult.PendingTools
        )
    }

    @Test
    fun `pending tool only in the kept tail leaves the plan ready`() {
        val a = MessageNode.of(UIMessage.user("a"))
        val b = MessageNode.of(UIMessage.user("b"))
        val conversation = conversation(listOf(a, b, toolNode(ToolApprovalState.Pending)))

        val plan = (conversation.planCompression(windowStart = 0, keepRecent = 1) as CompressionPlanResult.Ready).plan

        assertEquals(listOf("a", "b"), plan.messagesToSend.map { it.toText() })
        assertEquals(b.id, plan.anchorNodeId)
    }

    private fun toolNode(state: ToolApprovalState) = MessageNode.of(
        UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.Tool(
                    toolCallId = Uuid.random().toString(),
                    toolName = "test",
                    input = "{}",
                    approvalState = state,
                )
            ),
        )
    )
}
