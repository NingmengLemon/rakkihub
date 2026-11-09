package me.rerere.rikkahub.data.model

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Clock
import kotlin.uuid.Uuid

class ConversationToolCallsTest {
    @Test
    fun `unresumable tools get a recovery result instead of deleting the message`() {
        val user = UIMessage.user("run it")
        val answered = toolReply(ToolApprovalState.Pending)
        val stale = toolReply(ToolApprovalState.Pending)
        val conversation = Conversation.ofId(Uuid.random()).copy(
            messageNodes = listOf(
                user.toMessageNode(),
                MessageNode(messages = listOf(answered, stale), selectIndex = 1),
            )
        )

        val cleaned = conversation.withoutUnresumableToolCalls()

        assertEquals(2, cleaned.messageNodes.size)
        val recovered = cleaned.currentMessages.last().getTools().single()
        assertTrue(recovered.isExecuted)
        assertTrue((recovered.output.single() as UIMessagePart.Text).text.contains("not_started"))
    }

    @Test
    fun `recovery distinguishes started tools from not started tools`() {
        val started = toolReply(ToolApprovalState.Pending, startedAt = Clock.System.now())
        val notStarted = toolReply(ToolApprovalState.Pending)
        val conversation = Conversation.ofId(Uuid.random()).copy(
            messageNodes = listOf(
                UIMessage.user("run it").toMessageNode(),
                started.toMessageNode(),
                notStarted.toMessageNode(),
            )
        )

        val cleaned = conversation.withoutUnresumableToolCalls()

        assertTrue(
            (cleaned.currentMessages[1].getTools().single().output.single() as UIMessagePart.Text).text.contains(
                "outcome_unknown"
            )
        )
        assertTrue(
            (cleaned.currentMessages[2].getTools().single().output.single() as UIMessagePart.Text).text.contains(
                "not_started"
            )
        )
    }

    @Test
    fun `recovery keeps text and reasoning parts`() {
        val text = UIMessagePart.Text("visible answer")
        val reasoning = UIMessagePart.Reasoning("internal thought")
        val message = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(text, reasoning, toolPart(ToolApprovalState.Pending)),
        )
        val conversation = Conversation.ofId(Uuid.random()).copy(
            messageNodes = listOf(UIMessage.user("run it").toMessageNode(), message.toMessageNode())
        )

        val recovered = conversation.withoutUnresumableToolCalls().currentMessages.last()

        assertEquals(text, recovered.parts[0])
        assertEquals(reasoning, recovered.parts[1])
        assertTrue((recovered.parts[2] as UIMessagePart.Tool).isExecuted)
    }

    @Test
    fun `pending and auto tools are both recovered together`() {
        val message = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(
                toolPart(ToolApprovalState.Pending),
                toolPart(ToolApprovalState.Auto),
            ),
        )
        val conversation = Conversation.ofId(Uuid.random()).copy(
            messageNodes = listOf(UIMessage.user("run both").toMessageNode(), message.toMessageNode())
        )

        val tools = conversation.withoutUnresumableToolCalls().currentMessages.last().getTools()

        assertEquals(2, tools.size)
        assertTrue(tools.all { it.isExecuted })
    }

    @Test
    fun `regenerating a reply that waits for approval keeps it as a branch`() {
        val user = UIMessage.user("run it")
        val earlierStale = toolReply(ToolApprovalState.Pending)
        val pending = toolReply(ToolApprovalState.Pending)
        val conversation = Conversation.ofId(Uuid.random()).copy(
            messageNodes = listOf(earlierStale.toMessageNode(), user.toMessageNode(), pending.toMessageNode())
        )

        // 只处理要发给模型的节点：之前的节点保留并补恢复结果，被重新生成的节点保留
        val cleaned = conversation.withoutUnresumableToolCalls(untilNode = 2)

        assertEquals(listOf(earlierStale.id, user.id, pending.id), cleaned.currentMessages.map { it.id })
        assertTrue(cleaned.currentMessages.first().getTools().single().isExecuted)
    }

    @Test
    fun `approved tools and finished replies are kept`() {
        val approved = toolReply(ToolApprovalState.Approved)
        val executed = UIMessage.assistant("done")
        val conversation = Conversation.ofId(Uuid.random()).copy(
            messageNodes = listOf(executed.toMessageNode(), approved.toMessageNode())
        )

        assertTrue(conversation.withoutUnresumableToolCalls() === conversation)
    }

    @Test
    fun `resumable tool keeps its auto sibling untouched`() {
        val message = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(
                toolPart(ToolApprovalState.Approved),
                toolPart(ToolApprovalState.Auto),
            ),
        )
        val conversation = Conversation.ofId(Uuid.random()).copy(
            messageNodes = listOf(UIMessage.user("run both").toMessageNode(), message.toMessageNode())
        )

        val cleaned = conversation.withoutUnresumableToolCalls()

        assertTrue(cleaned === conversation)
        assertTrue(cleaned.currentMessages.last().getTools().none { it.isExecuted })
    }

    @Test
    fun `node with only an unresumable tool keeps the tool call and adds a result`() {
        val conversation = Conversation.ofId(Uuid.random()).copy(
            messageNodes = listOf(
                UIMessage.user("hi").toMessageNode(),
                toolReply(ToolApprovalState.Auto).toMessageNode()
            )
        )

        val cleaned = conversation.withoutUnresumableToolCalls()

        assertEquals(2, cleaned.messageNodes.size)
        assertTrue(cleaned.currentMessages.last().getTools().single().isExecuted)
    }

    @Test
    fun `awaiting approval only looks at the selected branch`() {
        val pending = toolReply(ToolApprovalState.Pending)
        val other = UIMessage.assistant("other branch")
        val selectingPending = Conversation.ofId(Uuid.random()).copy(
            messageNodes = listOf(MessageNode(messages = listOf(other, pending), selectIndex = 1))
        )

        assertTrue(selectingPending.isAwaitingToolApproval)
        assertFalse(
            selectingPending.copy(messageNodes = listOf(selectingPending.messageNodes.single().copy(selectIndex = 0)))
                .isAwaitingToolApproval
        )
    }

    private fun toolReply(
        approvalState: ToolApprovalState,
        startedAt: kotlin.time.Instant? = null,
    ) = UIMessage(
        role = MessageRole.ASSISTANT,
        parts = listOf(toolPart(approvalState, startedAt)),
    )

    private fun toolPart(
        approvalState: ToolApprovalState,
        startedAt: kotlin.time.Instant? = null,
    ) = UIMessagePart.Tool(
        toolCallId = Uuid.random().toString(),
        toolName = "workspace_shell",
        input = "{}",
        approvalState = approvalState,
        startedAt = startedAt,
    )
}
