package me.rerere.ai.provider.providers.google

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.attachGoogleThoughtSignature
import me.rerere.ai.ui.standaloneGoogleThoughtSignature

/**
 * Gemini 独立 `thoughtSignature` part 的挂载顺序。
 *
 * 这种 part 没有内容：优先挂到前一个 part；前面还没有内容时先排队，等下一个 part 出现再挂。
 * 流式解码把队列放在解码器实例上，签名可以跨 SSE 事件保留，不会因为一次事件只有签名就丢掉。
 */
internal class GoogleThoughtSignatureQueue {
    private var pending: String? = null

    /** 如果 [part] 只是签名，挂到 [previous] 或排队，返回 true 表示已消费。 */
    fun consume(part: JsonObject, previous: UIMessagePart?): Boolean {
        val signature = part.standaloneGoogleThoughtSignature() ?: return false
        if (previous != null) {
            previous.attachGoogleThoughtSignature(signature)
        } else {
            pending = signature
        }
        return true
    }

    /** 新 part 解析完成后补上排队的签名。 */
    fun applyTo(part: UIMessagePart) {
        val signature = pending ?: return
        part.attachGoogleThoughtSignature(signature)
        pending = null
    }
}

/** 按出现顺序解析 parts，并把独立签名挂到相邻 part 上。 */
internal fun parseGoogleParts(
    parts: JsonArray?,
    queue: GoogleThoughtSignatureQueue,
    parse: (JsonObject, Int) -> UIMessagePart,
): List<UIMessagePart> = buildList {
    parts.orEmpty().forEachIndexed { index, element ->
        val json = element.jsonObject
        if (queue.consume(json, lastOrNull())) return@forEachIndexed
        val part = parse(json, index)
        queue.applyTo(part)
        add(part)
    }
}
