package me.rerere.rikkahub.data.ai.mcp

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class McpStdioLogTest {
    @Test
    fun `buffer keeps only the trailing characters`() {
        val buffer = StdioLogBuffer(maxChars = 5)
        buffer.append("0123456789".toByteArray(), 0, 10)

        assertEquals("56789", buffer.text.value)
    }

    @Test
    fun `buffer appends utf8 and clears`() {
        val buffer = StdioLogBuffer()
        val text = "错误\n".toByteArray()
        buffer.append(text, 0, text.size)
        assertEquals("错误\n", buffer.text.value)

        buffer.clear()
        assertEquals("", buffer.text.value)
    }

    @Test
    fun `tee forwards single byte reads and captures them`() {
        val buffer = StdioLogBuffer()
        val input = TeeInputStream(ByteArrayInputStream("abc".toByteArray()), buffer)

        val result = buildString {
            while (true) {
                val value = input.read()
                if (value < 0) break
                append(value.toChar())
            }
        }

        assertEquals("abc", result)
        assertEquals("abc", buffer.text.value)
    }

    @Test
    fun `tee forwards array reads and captures only the read range`() {
        val buffer = StdioLogBuffer()
        val input = TeeInputStream(ByteArrayInputStream("hello".toByteArray()), buffer)
        val target = ByteArray(8)

        val count = input.read(target, 1, 5)

        assertEquals(5, count)
        assertEquals("hello", target.decodeToString(1, 6))
        assertEquals("hello", buffer.text.value)
    }
}
