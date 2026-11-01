package me.rerere.rikkahub.data.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 输入框附件的进程内登记：附件清理靠它看见编辑期间不写草稿的那部分。 */
class LiveAttachmentReferencesTest {

    private val references = LiveAttachmentReferences()

    @Test
    fun `registered urls are visible in the snapshot`() {
        references.put("chat", setOf("file:///a.png", "file:///b.png"))

        assertEquals(setOf("file:///a.png", "file:///b.png"), references.snapshot())
    }

    @Test
    fun `several owners are merged`() {
        references.put("first", setOf("file:///a.png"))
        references.put("second", setOf("file:///b.png"))

        assertEquals(setOf("file:///a.png", "file:///b.png"), references.snapshot())
    }

    @Test
    fun `overwriting an owner replaces its urls`() {
        references.put("chat", setOf("file:///a.png"))
        references.put("chat", setOf("file:///b.png"))

        assertEquals(setOf("file:///b.png"), references.snapshot())
    }

    @Test
    fun `an empty set removes the owner`() {
        references.put("chat", setOf("file:///a.png"))
        references.put("chat", emptySet())

        assertTrue(references.snapshot().isEmpty())
    }

    @Test
    fun `removing an owner drops its urls`() {
        references.put("first", setOf("file:///a.png"))
        references.put("second", setOf("file:///b.png"))
        references.remove("first")

        assertEquals(setOf("file:///b.png"), references.snapshot())
    }
}
