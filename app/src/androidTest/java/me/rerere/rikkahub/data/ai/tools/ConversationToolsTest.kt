package me.rerere.rikkahub.data.ai.tools

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.AppDatabaseFactory
import me.rerere.rikkahub.data.db.fts.MessageFtsManager
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.repository.ConversationRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files
import kotlin.uuid.Uuid

/**
 * 上游 #2372：conversation_search 曾经不带 assistantId，当前助手能搜到其它助手的对话。
 * FTS 的过滤本身由 [me.rerere.rikkahub.data.db.fts.MessageFtsManagerTest] 覆盖，这里钉住工具接线。
 */
@RunWith(AndroidJUnit4::class)
class ConversationToolsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var directory: File
    private lateinit var database: AppDatabase
    private lateinit var fts: MessageFtsManager
    private lateinit var repository: ConversationRepository

    @Before
    fun setUp() {
        directory = Files.createTempDirectory(context.cacheDir.toPath(), "conversation-tools-test-").toFile()
        database = AppDatabaseFactory.create(context, File(directory, "db").path)
        fts = MessageFtsManager(database)
        repository = ConversationRepository(
            conversationDAO = database.conversationDao(),
            messageNodeDAO = database.messageNodeDao(),
            favoriteDAO = database.favoriteDao(),
            database = database,
            messageFtsManager = fts,
            attachmentFilesDirUri = "file:///data/user/0/${context.packageName}/files",
        )
    }

    @After
    fun tearDown() {
        database.close()
        directory.deleteRecursively()
    }

    @Test
    fun searchOnlyReturnsConversationsOfTheCurrentAssistant() = runBlocking {
        val mine = conversation("mine", UIMessage.user("toolscope needle in mine"))
        val theirs = conversation("theirs", UIMessage.user("toolscope needle in theirs"), owner = Uuid.random())
        save(mine)
        save(theirs)

        val tool = createConversationTools(repository, mine.assistantId)
            .single { it.name == "conversation_search" }
        val output = tool.execute(buildJsonObject { put("query", "toolscope") })

        val payload = Json.parseToJsonElement((output.single() as UIMessagePart.Text).text).jsonArray
        val conversationIds = payload.map { it.jsonObject["conversation_id"]?.jsonPrimitive?.content }
        assertEquals(listOf(mine.id.toString()), conversationIds)
    }

    private fun conversation(
        title: String,
        vararg messages: UIMessage,
        owner: Uuid = Uuid.random(),
    ) = Conversation(
        assistantId = owner,
        title = title,
        messageNodes = messages.map { MessageNode.of(it) },
    )

    private suspend fun save(conversation: Conversation) {
        repository.insertConversation(conversation)
    }
}
