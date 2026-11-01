package me.rerere.rikkahub.ui.pages.chat

import me.rerere.rikkahub.data.files.ChatDraftStore.Draft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 编辑历史消息期间草稿的暂存与放回。
 *
 * 这里钉住的是「暂存的那份不能丢」：取消编辑后还没放回时用户又点了一次编辑，或者编辑结束后用户
 * 已经在输入框里写了新内容。
 */
class ChatEditDraftTest {

    private val first = Draft(text = "第一段没发出去的话")
    private val second = Draft(text = "第二段")
    private val empty = Draft()

    @Test
    fun `the draft comes back after the edit`() {
        val draft = ChatEditDraft()
        draft.beginEditing(first)

        assertEquals(first, draft.finishEditing(empty))
    }

    @Test
    fun `a second edit before the draft is put back does not lose it`() {
        val draft = ChatEditDraft()
        draft.beginEditing(first)
        // 取消编辑之后、草稿还没放回输入框时又点了一次编辑：这时输入框是空的
        draft.beginEditing(empty)

        assertEquals(first, draft.finishEditing(empty))
    }

    @Test
    fun `content typed after the edit wins`() {
        val draft = ChatEditDraft()
        draft.beginEditing(first)

        assertNull(draft.finishEditing(second))
        assertNull(draft.finishEditing(empty))
    }

    @Test
    fun `an edit started with an empty input puts nothing back`() {
        val draft = ChatEditDraft()
        draft.beginEditing(empty)

        assertNull(draft.finishEditing(empty))
    }

    @Test
    fun `a draft restored while an edit is running comes back after it`() {
        val draft = ChatEditDraft()
        draft.beginEditing(empty)
        // 磁盘上的草稿回来时输入框正被编辑占着：不覆盖编辑内容，编辑结束后再放回
        draft.restoreAfterEditing(first)

        assertEquals(first, draft.finishEditing(empty))
    }

    @Test
    fun `leaving while editing keeps the parked draft`() {
        val draft = ChatEditDraft()
        draft.beginEditing(first)

        assertEquals(first, draft.parkedDraft())
    }

    @Test
    fun `leaving right after the edit keeps the parked draft, not the empty input`() {
        val draft = ChatEditDraft()
        draft.beginEditing(first)

        assertEquals(first, draft.draftToPersistOnExit(empty))
    }

    @Test
    fun `leaving with new content persists the new content`() {
        val draft = ChatEditDraft()
        draft.beginEditing(first)

        assertEquals(second, draft.draftToPersistOnExit(second))
    }

    @Test
    fun `leaving with nothing parked persists the input as it is`() {
        val draft = ChatEditDraft()

        assertEquals(second, draft.draftToPersistOnExit(second))
        assertEquals(empty, draft.draftToPersistOnExit(empty))
    }
}
