package me.rerere.rikkahub.ui.pages.extensions.workspace

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceTerminalInputTest {
    @Test
    fun `plain text passes through`() {
        assertEquals("hello", terminalInputText("hello", control = false, alt = false))
    }

    @Test
    fun `control letters become control bytes`() {
        assertEquals("\u0003", terminalInputText("c", control = true, alt = false))
        assertEquals("\u001a", terminalInputText("Z", control = true, alt = false))
    }

    @Test
    fun `alt prefixes escape`() {
        assertEquals("\u001Bx", terminalInputText("x", control = false, alt = true))
    }

    @Test
    fun `CJK text is not transformed`() {
        assertEquals("你好", terminalInputText("你好", control = false, alt = false))
        assertEquals("你好", terminalInputText("你好", control = true, alt = false))
    }
}
