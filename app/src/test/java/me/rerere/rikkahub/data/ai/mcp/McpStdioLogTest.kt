package me.rerere.rikkahub.data.ai.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun `split utf8 sequence keeps one character`() {
        val bytes = "错".toByteArray()
        val buffer = StdioLogBuffer()

        buffer.append(bytes, 0, 2)
        assertEquals("", buffer.text.value)

        buffer.append(bytes, 2, 1)
        assertEquals("错", buffer.text.value)
    }

    @Test
    fun `split emoji keeps one surrogate pair`() {
        val bytes = "😀".toByteArray()
        val buffer = StdioLogBuffer()
        for (index in bytes.indices) {
            buffer.append(bytes, index, 1)
        }

        assertEquals("😀", buffer.text.value)
    }

    @Test
    fun `trim does not leave a lone surrogate`() {
        val buffer = StdioLogBuffer(maxChars = 1)
        val bytes = "😀".toByteArray()
        buffer.append(bytes, 0, bytes.size)

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

    @Test
    fun `tee forwards available and close`() {
        var closed = false
        val source = object : ByteArrayInputStream("x".toByteArray()) {
            override fun available(): Int = 7
            override fun close() {
                closed = true
            }
        }
        val input = TeeInputStream(source, StdioLogBuffer())

        assertEquals(7, input.available())
        input.close()
        assertTrue(closed)
    }
}
