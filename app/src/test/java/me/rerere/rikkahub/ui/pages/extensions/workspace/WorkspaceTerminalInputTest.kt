package me.rerere.rikkahub.ui.pages.extensions.workspace

import android.view.KeyEvent
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
    fun `control symbols use the terminal table`() {
        assertEquals("\u001B", terminalInputText("[", control = true, alt = false))
        assertEquals("\u001C", terminalInputText("\\", control = true, alt = false))
        assertEquals("\u001D", terminalInputText("]", control = true, alt = false))
        assertEquals("\u001F", terminalInputText("/", control = true, alt = false))
    }

    @Test
    fun `alt keeps the original character case`() {
        assertEquals("\u001Bb", terminalInputText("b", control = false, alt = true))
        assertEquals("\u001BB", terminalInputText("B", control = false, alt = true))
    }

    @Test
    fun `key code fallback is lowercase`() {
        assertEquals('a', terminalKeyChar(KeyEvent.KEYCODE_A))
        assertEquals('b', terminalKeyChar(KeyEvent.KEYCODE_B))
        assertEquals(null, terminalKeyChar(KeyEvent.KEYCODE_ENTER))
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
