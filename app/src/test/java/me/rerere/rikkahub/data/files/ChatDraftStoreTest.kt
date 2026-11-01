package me.rerere.rikkahub.data.files

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.CountDownLatch
import kotlin.uuid.Uuid

/**
 * 草稿落盘的判据。
 *
 * 草稿是「没发出去的东西」，写坏了比丢了还难受：解析失败时宁可当作没有草稿，也不能让聊天页
 * 打不开；同理，空草稿要删掉文件，不能留一堆空壳让外部的引用扫描一直保着附件。
 */
class ChatDraftStoreTest {

    private lateinit var directory: File
    private lateinit var store: ChatDraftStore
    private val conversationId = Uuid.random()

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("chat-draft-test-").toFile().resolve("drafts")
        store = ChatDraftStore(directory, JsonInstant)
    }

    @After
    fun tearDown() {
        directory.parentFile?.deleteRecursively()
    }

    @Test
    fun `a draft round trips with its attachments`() = runTest {
        val parts = listOf(UIMessagePart.Image("file:///data/user/0/pkg/files/upload/a.png"))
        store.save(conversationId, ChatDraftStore.Draft(text = "还没发出去", parts = parts))

        val loaded = store.load(conversationId)
        assertEquals("还没发出去", loaded?.text)
        assertEquals(parts, loaded?.parts)
    }

    @Test
    fun `saving again replaces the previous draft`() = runTest {
        store.save(conversationId, ChatDraftStore.Draft(text = "第一版"))
        store.save(conversationId, ChatDraftStore.Draft(text = "第二版"))

        assertEquals("第二版", store.load(conversationId)?.text)
    }

    @Test
    fun `an empty draft removes the file`() = runTest {
        store.save(conversationId, ChatDraftStore.Draft(text = "有内容"))
        assertTrue(fileOf(conversationId).isFile)

        store.save(conversationId, ChatDraftStore.Draft())
        assertNull(store.load(conversationId))
    }

    @Test
    fun `saving leaves no temporary file behind`() = runTest {
        store.save(conversationId, ChatDraftStore.Draft(text = "a"))

        assertEquals(listOf("$conversationId.json"), directory.listFiles().orEmpty().map { it.name })
    }

    @Test
    fun `a broken file is treated as no draft`() = runTest {
        directory.mkdirs()
        fileOf(conversationId).writeText("{not json")

        assertTrue(fileOf(conversationId).isFile)
        assertNull(store.load(conversationId))
    }

    @Test
    fun `the next empty save clears a broken file`() = runTest {
        directory.mkdirs()
        fileOf(conversationId).writeText("{not json")

        store.save(conversationId, ChatDraftStore.Draft())
        assertFalse(fileOf(conversationId).exists())
    }

    @Test
    fun `a failing delete is reported instead of pretending the draft is gone`() = runTest {
        // 用非空目录占住草稿路径，模拟删不掉
        val occupied = fileOf(conversationId)
        assertTrue(occupied.mkdirs())
        val keep = File(occupied, "keep")
        keep.writeText("x")
        assertTrue(keep.isFile)

        val failure = runCatching { store.save(conversationId, ChatDraftStore.Draft()) }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message.orEmpty().contains("Cannot delete the draft file"))
    }

    @Test
    fun `each conversation gets its own file`() = runTest {
        val other = Uuid.random()
        store.save(conversationId, ChatDraftStore.Draft(text = "a"))
        store.save(other, ChatDraftStore.Draft(text = "b"))

        assertEquals(
            setOf("$conversationId.json", "$other.json"),
            directory.listFiles().orEmpty().map { it.name }.toSet(),
        )
        assertEquals("b", store.load(other)?.text)
    }

    @Test
    fun `draft conversation ids are listed for the drawer`() = runTest {
        val other = Uuid.random()
        store.save(conversationId, ChatDraftStore.Draft(text = "a"))
        store.save(other, ChatDraftStore.Draft(text = "b"))
        directory.resolve("not-a-draft.tmp").writeText("x")

        assertEquals(setOf(conversationId, other), store.conversationIds())
    }

    @Test
    fun `orphan drafts are removed and kept ones survive`() = runTest {
        val kept = Uuid.random()
        store.save(kept, ChatDraftStore.Draft(text = "留着"))
        store.save(conversationId, ChatDraftStore.Draft(text = "孤儿"))

        assertEquals(1, store.deleteOrphans(setOf(kept)))
        assertEquals("留着", store.load(kept)?.text)
        assertNull(store.load(conversationId))
    }

    @Test
    fun `a draft for the conversation that gets reopened survives the sweep`() = runTest {
        // 新建会话还没落库，库里查不到它；但关掉「启动新建会话」时，它就是下次启动直接打开的会话
        val resumable = Uuid.random()
        store.save(resumable, ChatDraftStore.Draft(text = "打了一半"))

        assertEquals(0, store.deleteOrphans(emptySet(), resumableConversationId = resumable))
        assertEquals("打了一半", store.load(resumable)?.text)

        // 没有这条启动路径时它才是孤儿
        assertEquals(1, store.deleteOrphans(emptySet()))
        assertNull(store.load(resumable))
    }

    @Test
    fun `the sweep also removes temporary files and names that are not drafts`() = runTest {
        store.save(conversationId, ChatDraftStore.Draft(text = "留着"))
        directory.resolve("$conversationId.json.${Uuid.random()}.tmp").writeText("写到一半")
        directory.resolve("not-a-uuid.json").writeText("{}")

        assertEquals(2, store.deleteOrphans(setOf(conversationId)))
        assertEquals("留着", store.load(conversationId)?.text)
        assertEquals(listOf("$conversationId.json"), directory.listFiles().orEmpty().map { it.name })
    }

    @Test
    fun `sweeping a missing directory removes nothing and creates nothing`() = runTest {
        val missing = ChatDraftStore(directory.resolve("missing"), JsonInstant)

        assertEquals(0, missing.deleteOrphans(emptySet()))
        assertFalse(directory.resolve("missing").exists())
    }

    @Test
    fun `concurrent saves leave one readable draft`() = runTest {
        val texts = List(8) { "第 $it 版" }
        val failures = Collections.synchronizedList(mutableListOf<Throwable>())
        // 先都停在起跑线上再一起放行，否则前一个可能已经写完，测不到真的并发
        val start = CountDownLatch(1)
        coroutineScope {
            texts.forEach { text ->
                launch(Dispatchers.IO) {
                    start.await()
                    runCatching { store.save(conversationId, ChatDraftStore.Draft(text = text)) }
                        .onFailure(failures::add)
                }
            }
            start.countDown()
        }
        // Windows 上两个写者同时替换同一个目标时，系统可能瞬时拒绝其中一个（Android 的 rename 不会）。
        // 那一次丢失只是少写一版，不算错误；其它异常都不行
        failures.forEach { assertTrue("unexpected failure: $it", it is AccessDeniedException) }

        val loaded = store.load(conversationId)
        assertNotNull(loaded)
        assertTrue(loaded!!.text in texts)
        assertEquals(listOf("$conversationId.json"), directory.listFiles().orEmpty().map { it.name })
    }

    @Test
    fun `draft file urls are collected for the attachment cleanup`() = runTest {
        store.save(
            conversationId,
            ChatDraftStore.Draft(
                text = "看看这张",
                parts = listOf(UIMessagePart.Image("file:///data/user/0/pkg/files/upload/a.png")),
            ),
        )
        // 坏掉的草稿也要算数：清理靠它保住文件
        directory.resolve("broken.json")
            .writeText("""{"parts":[{"url":"file:///data/user/0/pkg/files/upload/b.png"}""")

        assertEquals(
            setOf(
                "file:///data/user/0/pkg/files/upload/a.png",
                "file:///data/user/0/pkg/files/upload/b.png",
            ),
            store.referencedFileUrls(),
        )
    }

    private fun fileOf(conversationId: Uuid) = File(directory, "$conversationId.json")
}
