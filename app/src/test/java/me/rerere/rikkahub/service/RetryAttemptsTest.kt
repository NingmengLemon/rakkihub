package me.rerere.rikkahub.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import me.rerere.ai.agent.RetryAttempt
import me.rerere.rikkahub.data.model.Conversation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class RetryAttemptsTest {
    @Test
    fun `countdown rounds up and clamps at zero`() {
        assertEquals(3L, retryRemainingSeconds(0, 3_000, 0))
        assertEquals(2L, retryRemainingSeconds(0, 3_000, 1_000))
        assertEquals(1L, retryRemainingSeconds(0, 3_000, 2_999))
        assertEquals(0L, retryRemainingSeconds(0, 3_000, 3_000))
        assertEquals(0L, retryRemainingSeconds(0, 3_000, 5_000))
    }

    @Test
    fun `session records and clears retry attempts`() {
        val id = Uuid.random()
        val session = ConversationSession(
            id = id,
            initial = Conversation.ofId(id),
            scope = CoroutineScope(Dispatchers.Unconfined),
            store = FakeConversationStore(),
            onIdle = {},
        )
        val attempt = RetryAttempt(
            cause = IOException("network"),
            retry = 1,
            maxRetries = 3,
            delay = 2.seconds,
        )

        session.recordRetryAttempt(attempt)

        assertEquals(1, session.retryAttempts.value.size)
        assertEquals(attempt, session.retryAttempts.value.single().attempt)
        assertEquals(true, session.retrying.value)
        session.clearRetryAttempts()
        assertTrue(session.retryAttempts.value.isEmpty())
        assertEquals(false, session.retrying.value)
    }

    @Test
    fun `session keeps the newest twenty attempts`() {
        val id = Uuid.random()
        val session = ConversationSession(
            id = id,
            initial = Conversation.ofId(id),
            scope = CoroutineScope(Dispatchers.Unconfined),
            store = FakeConversationStore(),
            onIdle = {},
        )
        repeat(25) { index ->
            session.recordRetryAttempt(
                RetryAttempt(
                    cause = IOException("network $index"),
                    retry = index + 1,
                    maxRetries = 30,
                    delay = 1.seconds,
                )
            )
        }

        assertEquals(20, session.retryAttempts.value.size)
        assertEquals(6, session.retryAttempts.value.first().attempt.retry)
        assertEquals(25, session.retryAttempts.value.last().attempt.retry)
    }

    @Test
    fun `replace clears attempts and retrying state`() {
        val id = Uuid.random()
        val session = ConversationSession(
            id = id,
            initial = Conversation.ofId(id),
            scope = CoroutineScope(Dispatchers.Unconfined),
            store = FakeConversationStore(),
            onIdle = {},
        )
        session.recordRetryAttempt(
            RetryAttempt(
                cause = IOException("network"),
                retry = 1,
                maxRetries = 3,
                delay = 1.seconds,
            )
        )

        kotlinx.coroutines.runBlocking {
            session.replace(Conversation.ofId(id), existsInDatabase = true) {}
        }

        assertTrue(session.retryAttempts.value.isEmpty())
        assertEquals(false, session.retrying.value)
    }
}
