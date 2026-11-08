package me.rerere.ai.provider.stream

import me.rerere.ai.ui.StreamChunk

/**
 * 一次 Provider 响应流所使用的有状态、传输无关解码器。
 * 每条响应流必须创建独立实例。
 */
interface StreamChunkDecoder {
    /** 将单个原始 SSE 事件转换为通用流事件。解析失败时直接抛出异常。 */
    fun accept(event: SseEvent): DecodeResult

    /**
     * SSE 正常关闭时收尾。实现必须保证该方法及显式终止事件产生的 Finish 幂等。
     *
     * 传输层正常 EOF 不代表协议完成：如果还没见到本协议的终止事件，必须抛出
     * [java.io.IOException]，由 provider 转成可重试的流错误；调用方负责 `close(e)`。
     */
    fun onClosed(): List<StreamChunk>
}

data class DecodeResult(
    val chunks: List<StreamChunk> = emptyList(),
    /** Provider 协议已经明确结束，传输层可以主动关闭连接。 */
    val completed: Boolean = false,
)
