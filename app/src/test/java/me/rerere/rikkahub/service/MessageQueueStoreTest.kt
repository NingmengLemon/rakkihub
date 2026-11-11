package me.rerere.rikkahub.service

import kotlinx.coroutines.runBlocking
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.uuid.Uuid

class MessageQueueStoreTest {
    private lateinit var directory: File

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("message-queue-").toFile()
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    private fun store() = MessageQueueStore(directory, JsonInstant)

    @Test
    fun `saved queue round trips including attachments and answer flag`() = runBlocking {
        val id = Uuid.random()
        val message = QueuedMessage(
            parts = listOf(
                UIMessagePart.Text("queued"),
                UIMessagePart.Image("file:upload/hash.jpg"),
            ),
            answer = false,
        )

        store().save(id, listOf(message))

        assertEquals(listOf(message), store().load(id))
    }

    @Test
    fun `editing state is not persisted`() = runBlocking {
        val id = Uuid.random()
        val message = QueuedMessage(parts = listOf(UIMessagePart.Text("queued")), isEditing = true)

        store().save(id, listOf(message))

        assertEquals(false, store().load(id).single().isEditing)
    }

    @Test
    fun `saving an empty queue removes the file`() = runBlocking {
        val id = Uuid.random()
        store().save(id, listOf(QueuedMessage(parts = listOf(UIMessagePart.Text("queued")))))

        store().save(id, emptyList())

        assertTrue(store().load(id).isEmpty())
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `corrupt queue files are ignored instead of breaking restore`() = runBlocking {
        val id = Uuid.random()
        directory.mkdirs()
        File(directory, "$id.json").writeText("not json")

        assertTrue(store().load(id).isEmpty())
    }

    @Test
    fun `queue attachments are visible to the attachment cleaner`() = runBlocking {
        val id = Uuid.random()
        store().save(
            id,
            listOf(
                QueuedMessage(
                    parts = listOf(
                        UIMessagePart.Text("look"),
                        UIMessagePart.Image("file:upload/hash.jpg"),
                    )
                )
            ),
        )

        assertTrue(store().referencedFileUrls().contains("file:upload/hash.jpg"))
    }

    @Test
    fun `orphan cleanup keeps existing conversations and removes the rest`() = runBlocking {
        val keep = Uuid.random()
        val remove = Uuid.random()
        store().save(keep, listOf(QueuedMessage(parts = listOf(UIMessagePart.Text("keep")))))
        store().save(remove, listOf(QueuedMessage(parts = listOf(UIMessagePart.Text("remove")))))

        assertEquals(1, store().deleteOrphans(setOf(keep)))
        assertEquals(1, store().load(keep).size)
        assertTrue(store().load(remove).isEmpty())
    }
}
