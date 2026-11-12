package me.rerere.rikkahub.service

import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.uuid.Uuid

class ChatErrorStoreTest {
    private lateinit var directory: File
    private lateinit var file: File

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("chat-errors-").toFile()
        file = File(directory, "chat-errors.json")
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    private fun store() = ChatErrorStore(file, JsonInstant)

    @Test
    fun `saved errors round trip title message timestamp solution and retryable`() = runBlocking {
        val conversationId = Uuid.random()
        val retryMessageId = Uuid.random()
        val timestamp = 1_700_000_000_000L
        val error = ChatError(
            title = "Generation failed",
            error = IllegalStateException("bad key"),
            conversationId = conversationId,
            timestamp = timestamp,
            solution = ChatErrorSolution.CheckFastModelSettings,
            retryable = true,
            retryMessageId = retryMessageId,
        )

        store().save(listOf(error))
        val loaded = store().load().single()

        assertEquals(error.id, loaded.id)
        assertEquals("Generation failed", loaded.title)
        assertEquals("bad key", loaded.error.message)
        assertEquals(conversationId, loaded.conversationId)
        assertEquals(timestamp, loaded.timestamp)
        assertEquals(ChatErrorSolution.CheckFastModelSettings, loaded.solution)
        assertTrue(loaded.retryable)
        assertEquals(retryMessageId, loaded.retryMessageId)
    }

    @Test
    fun `saving an empty list removes the file`() = runBlocking {
        store().save(
            listOf(
                ChatError(
                    error = IllegalStateException("bad key"),
                    conversationId = Uuid.random(),
                )
            )
        )

        store().save(emptyList())

        assertFalse(file.exists())
        assertTrue(store().load().isEmpty())
    }

    @Test
    fun `corrupt error files are ignored instead of breaking startup`() = runBlocking {
        directory.mkdirs()
        file.writeText("not json")

        assertTrue(store().load().isEmpty())
    }

    @Test
    fun `unknown solutions decode as no solution`() = runBlocking {
        directory.mkdirs()
        file.writeText(
            """
            [{"id":"${Uuid.random()}","errorMessage":"boom","conversationId":"${Uuid.random()}","timestamp":1,"solution":"FutureSolution"}]
            """.trimIndent()
        )

        val loaded = store().load().single()
        assertNull(loaded.solution)
        assertFalse(loaded.retryable)
        assertNull(loaded.retryMessageId)
    }

    @Test
    fun `errors for two conversations round trip`() = runBlocking {
        val firstConversation = Uuid.random()
        val secondConversation = Uuid.random()
        store().save(
            listOf(
                ChatError(error = IllegalStateException("first"), conversationId = firstConversation),
                ChatError(error = IllegalStateException("second"), conversationId = secondConversation),
            )
        )

        val loaded = store().load()

        assertEquals(
            setOf(firstConversation, secondConversation),
            loaded.map { it.conversationId }.toSet(),
        )
    }

    @Test
    fun `null throwable message is restored with the fallback text`() = runBlocking {
        store().save(
            listOf(
                ChatError(
                    error = Throwable(null as String?),
                    conversationId = Uuid.random(),
                )
            )
        )

        assertEquals("Unknown error", store().load().single().error.message)
    }
}
