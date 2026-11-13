package me.rerere.ui.richtext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiffViewTest {

    @Test
    fun `short lines are left unchanged`() {
        assertEquals("+val x = 1", truncateDiffLine("+val x = 1"))
        assertEquals("", truncateDiffLine(""))
    }

    @Test
    fun `long lines are truncated with an ellipsis`() {
        val line = "+" + "x".repeat(10_000)

        val shown = truncateDiffLine(line, maxChars = 20)

        assertEquals("+" + "x".repeat(19) + "…", shown)
        assertEquals(21, shown.length)
    }

    @Test
    fun `default truncation keeps intrinsic width bounded`() {
        // IntrinsicSize.Max 会把这个宽度交给 Constraints，超过 262143px 就会抛异常；
        // 按字符截断后即使字体被系统放大也远低于该上限。
        val shown = truncateDiffLine("-" + "y".repeat(500_000))

        assertTrue(shown.length <= MAX_DIFF_LINE_CHARS + 1)
        assertTrue(shown.endsWith("…"))
    }
}
