package me.rerere.rikkahub.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.ThoughtSignatureProtocol
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.stripThoughtSignatures
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ThoughtSignaturePersistenceTest {
    @Test
    fun `stripping a signature produces a snapshot that is flushed`() {
        val id = Uuid.random()
        val signedPart = UIMessagePart.Reasoning(
            reasoning = "thinking",
            metadata = JsonObject(mapOf("thoughtSignature" to JsonPrimitive("expired"))),
        )
        val conversation = Conversation.ofId(id).copy(
            messageNodes = listOf(
                MessageNode(
                    messages = listOf(
                        UIMessage(role = MessageRole.ASSISTANT, parts = listOf(signedPart)),
                    ),
                ),
            ),
        )
        val store = FakeConversationStore(persisted = conversation)
        val session = ConversationSession(
            id = id,
            initial = conversation,
            scope = CoroutineScope(Dispatchers.Unconfined),
            store = store,
            onIdle = {},
        )

        runBlocking { session.initialize(load = { conversation }) }
        session.update { it.stripThoughtSignatures(ThoughtSignatureProtocol.all) }
        runBlocking { session.flush() }

        assertTrue("stripped conversation must be different enough to flush", store.writes.isNotEmpty())
        val persistedPart = store.persisted
            ?.messageNodes
            ?.first()
            ?.messages
            ?.first()
            ?.parts
            ?.first()
        assertNull(persistedPart?.metadata)
        // 原快照不能被顺手改掉
        assertNotNull(signedPart.metadata?.get("thoughtSignature"))
    }
}
