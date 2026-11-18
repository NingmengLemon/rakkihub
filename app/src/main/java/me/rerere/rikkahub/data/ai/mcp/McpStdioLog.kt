package me.rerere.rikkahub.data.ai.mcp

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
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
    init {
        require(maxChars > 0)
    }

    private val lock = Any()
    private val builder = StringBuilder()
    private val decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
    private var pending = ByteArray(0)
    private val mutableText = MutableStateFlow("")

    val text: StateFlow<String> = mutableText.asStateFlow()

    fun append(bytes: ByteArray, offset: Int, length: Int) {
        if (length <= 0) return
        synchronized(lock) {
            // 保留上一次未完成的 UTF-8 序列，避免在 read 分块边界处产生 U+FFFD。
            val combined = ByteArray(pending.size + length)
            pending.copyInto(combined)
            bytes.copyInto(combined, pending.size, offset, offset + length)

            val input = ByteBuffer.wrap(combined)
            val output = CharBuffer.allocate(combined.size + 2)
            decoder.decode(input, output, false)
            output.flip()
            builder.append(output)
            pending = ByteArray(input.remaining()).also { input.get(it) }

            trimLocked()
            mutableText.value = builder.toString()
        }
    }

    fun clear() {
        synchronized(lock) {
            builder.clear()
            pending = ByteArray(0)
            decoder.reset()
            mutableText.value = ""
        }
    }

    private fun trimLocked() {
        if (builder.length <= maxChars) return
        builder.delete(0, builder.length - maxChars)
        // 截断点如果落在代理对中间，删掉开头孤立的低代理项。
        while (builder.isNotEmpty() && builder[0].isLowSurrogate()) {
            builder.deleteCharAt(0)
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
    private val singleByte = ByteArray(1)

    override fun read(): Int {
        val value = source.read()
        if (value >= 0) {
            singleByte[0] = value.toByte()
            sink.append(singleByte, 0, 1)
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
