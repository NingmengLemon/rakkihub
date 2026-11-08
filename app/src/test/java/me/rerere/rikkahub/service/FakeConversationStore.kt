package me.rerere.rikkahub.service

import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import java.time.Instant
import kotlin.uuid.Uuid

/** 记录每次落库的内存实现；[beforeWrite] 可以让测试在写入前挂起 */
class FakeConversationStore(var persisted: Conversation? = null) : ConversationStore {
    val writes = mutableListOf<Pair<Conversation?, Conversation>>()
    val selectIndexUpdates = mutableListOf<Triple<Uuid, Uuid, Int>>()
    val persistedNodes = mutableMapOf<Uuid, MessageNode>()
    val persistedNodeIndices = mutableMapOf<Uuid, Int>()
    var touchedUpdateAt: Instant? = null
    var beforeWrite: suspend () -> Unit = {}

    override suspend fun prepareWrite(): (Conversation) -> Conversation = { it }

    override suspend fun write(previous: Conversation?, current: Conversation) {
        beforeWrite()
        writes.add(previous to current)
        persisted = current
    }

    override suspend fun updateNodeSelectIndex(conversationId: Uuid, nodeId: Uuid, selectIndex: Int): Int {
        selectIndexUpdates.add(Triple(conversationId, nodeId, selectIndex))
        return 1
    }

    override suspend fun persistNode(conversationId: Uuid, nodeIndex: Int, node: MessageNode): Long {
        persistedNodes[node.id] = node
        persistedNodeIndices[node.id] = nodeIndex
        return node.messages.sumOf { message -> message.parts.sumOf { it.toString().length } }.toLong()
    }

    override suspend fun touchConversationUpdateAt(conversationId: Uuid, updateAt: Instant): Int {
        touchedUpdateAt = updateAt
        return 1
    }
}
