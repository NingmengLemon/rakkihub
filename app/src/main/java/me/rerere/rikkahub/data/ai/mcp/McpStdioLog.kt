package me.rerere.rikkahub.data.ai.mcp

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * 单个 stdio MCP Server 的 stderr 环形缓冲。
 *
 * SDK 会消费 stderr 做协议错误日志，这里通过 [TeeInputStream] 旁路一份给设置页；
 * 只保留尾部 [maxChars] 个字符，避免长时间运行的 Server 把内存吃满。
 */
internal class StdioLogBuffer(
    private val maxChars: Int = DEFAULT_MAX_CHARS,
) {
    private val lock = Any()
    private val builder = StringBuilder()
    private val mutableText = MutableStateFlow("")

    val text: StateFlow<String> = mutableText.asStateFlow()

    fun append(bytes: ByteArray, offset: Int, length: Int) {
        if (length <= 0) return
        synchronized(lock) {
            builder.append(String(bytes, offset, length, StandardCharsets.UTF_8))
            trimLocked()
            mutableText.value = builder.toString()
        }
    }

    fun clear() {
        synchronized(lock) {
            builder.clear()
            mutableText.value = ""
        }
    }

    private fun trimLocked() {
        if (builder.length > maxChars) {
            builder.delete(0, builder.length - maxChars)
        }
    }

    companion object {
        const val DEFAULT_MAX_CHARS = 16_384
    }
}

/** 把 stderr 的每个字节同时写给 [sink]，读取行为完全转发给源流。 */
internal class TeeInputStream(
    private val source: InputStream,
    private val sink: StdioLogBuffer,
) : InputStream() {
    override fun read(): Int {
        val value = source.read()
        if (value >= 0) {
            sink.append(byteArrayOf(value.toByte()), 0, 1)
        }
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val count = source.read(buffer, offset, length)
        if (count > 0) {
            sink.append(buffer, offset, count)
        }
        return count
    }

    override fun available(): Int = source.available()

    override fun close() {
        source.close()
    }
}
