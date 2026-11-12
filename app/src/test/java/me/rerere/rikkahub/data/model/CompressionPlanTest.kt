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

        // keep=1 指向 tool 消息；保留起点向前对齐到产生这条 tool 的 user b，所以只压缩 a
        assertEquals(listOf("a"), plan.messagesToSend.map { it.toText() })
        assertEquals(a.id, plan.anchorNodeId)
    }

    @Test
    fun `plan keeps the user that produced the last assistant`() {
        val a = MessageNode.of(UIMessage.user("a"))
        val b = MessageNode.of(UIMessage.assistant("b"))
        val c = MessageNode.of(UIMessage.user("c"))
        val d = MessageNode.of(UIMessage.assistant("d"))
        val conversation = conversation(listOf(a, b, c, d))

        val plan = (conversation.planCompression(windowStart = 0, keepRecent = 1) as CompressionPlanResult.Ready).plan

        assertEquals(listOf("a", "b"), plan.messagesToSend.map { it.toText() })
        assertEquals(b.id, plan.anchorNodeId)
    }

    @Test
    fun `plan refuses to compress the only user turn away`() {
        val conversation = conversation(
            listOf(
                MessageNode.of(UIMessage.user("a")),
                MessageNode.of(UIMessage.assistant("b")),
            )
        )

        assertTrue(
            conversation.planCompression(windowStart = 0, keepRecent = 1) is CompressionPlanResult.NothingToCompress
        )
    }

    @Test
    fun `plan rejects a summary range over the token budget`() {
        val conversation = conversation(
            listOf(
                MessageNode.of(UIMessage.user("a".repeat(200))),
                MessageNode.of(UIMessage.user("kept")),
            )
        )

        assertTrue(
            conversation.planCompression(windowStart = 0, keepRecent = 1, maxInputTokens = 10) is
                CompressionPlanResult.RangeTooLarge
        )
    }

    @Test
    fun `plan accepts a summary range under the token budget`() {
        val a = MessageNode.of(UIMessage.user("a"))
        val b = MessageNode.of(UIMessage.user("b"))
        val conversation = conversation(listOf(a, b))

        val plan = conversation.planCompression(
            windowStart = 0,
            keepRecent = 1,
            maxInputTokens = 100,
        )

        assertTrue(plan is CompressionPlanResult.Ready)
    }

    @Test
    fun `summary token budget respects context and assistant limits`() {
        assertEquals(1024, compressionSummaryMaxTokens(contextLength = 8_000))
        assertEquals(4096, compressionSummaryMaxTokens(contextLength = 1_000_000))
        assertEquals(512, compressionSummaryMaxTokens(contextLength = 128_000, assistantMaxTokens = 512))
        assertEquals(4096, compressionSummaryMaxTokens(contextLength = 128_000, assistantMaxTokens = 20_000))
        assertEquals(1024, compressionSummaryMaxTokens(contextLength = 8_000, assistantMaxTokens = 0))
    }

    @Test
    fun `summary input budget reserves output and prompt overhead`() {
        assertEquals(4928, compressionInputBudget(contextLength = 8_000, summaryMaxTokens = 1024))
        assertEquals(0, compressionInputBudget(contextLength = 1_000, summaryMaxTokens = 1024))
    }

    @Test
    fun `token estimate counts CJK and ASCII conservatively`() {
        assertEquals(2, estimateTokens("你好"))
        assertEquals(1, estimateTokens("abcd"))
        assertEquals(3, estimateTokens("hello world"))
        assertEquals(1024, UIMessagePart.Image("file:upload/hash.jpg").estimatedTokens())
        assertEquals(
            4096 + estimateTokens("x.pdf"),
            UIMessagePart.Document("file:upload/x.pdf", "x.pdf").estimatedTokens(),
        )
    }

    @Test
    fun `budget ignores history before the last checkpoint`() {
        val old = MessageNode.of(UIMessage.user("a".repeat(2_000)))
        val checkpoint = MessageNode.of(
            UIMessage.assistant("summary").copy(isContextCheckpoint = true)
        )
        val newUser = MessageNode.of(UIMessage.user("new"))
        val conversation = conversation(listOf(old, checkpoint, newUser))

        val plan = (
            conversation.planCompression(
                windowStart = conversation.contextWindowStart(),
                keepRecent = 0,
                maxInputTokens = 100,
            ) as CompressionPlanResult.Ready
            ).plan

        // 请求实际从检查点开始发，旧历史不再重复计入预算
        assertEquals(listOf("summary", "new"), plan.messagesToSend.map { it.toText() })
        assertEquals(newUser.id, plan.anchorNodeId)
    }

    @Test
    fun `no user in the range leaves the plan ready`() {
        val a = MessageNode.of(UIMessage.assistant("a"))
        val b = MessageNode.of(UIMessage.assistant("b"))
        val conversation = conversation(listOf(a, b))

        val plan = (conversation.planCompression(windowStart = 0, keepRecent = 1) as CompressionPlanResult.Ready).plan

        assertEquals(listOf("a"), plan.messagesToSend.map { it.toText() })
        assertEquals(a.id, plan.anchorNodeId)
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
