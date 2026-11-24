package me.rerere.ai.provider.providers

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.ui.UIMessagePart

/**
 * 消息 parts 按工具边界分组的结果
 * - Content: 普通内容（Text、Image、Reasoning 等）
 * - Tools: 连续的已执行工具
 */
internal sealed class PartGroup {
    data class Content(val parts: List<UIMessagePart>) : PartGroup()
    data class Tools(val tools: List<UIMessagePart.Tool>) : PartGroup()
}

/**
 * 将消息 parts 按工具边界分组
 *
 * 例如 [Text1, Tool1, Tool2, Text2, Tool3] 会分组为:
 * - Content([Text1])
 * - Tools([Tool1, Tool2])
 * - Content([Text2])
 * - Tools([Tool3])
 *
 * 这样可以确保 tool_use/functionCall 后面紧跟 tool_result/functionResponse
 */
internal fun groupPartsByToolBoundary(parts: List<UIMessagePart>): List<PartGroup> {
    val groups = mutableListOf<PartGroup>()
    val currentContent = mutableListOf<UIMessagePart>()
    val currentTools = mutableListOf<UIMessagePart.Tool>()

    fun flushContent() {
        if (currentContent.isNotEmpty()) {
            groups.add(PartGroup.Content(currentContent.toList()))
            currentContent.clear()
        }
    }

    fun flushTools() {
        if (currentTools.isNotEmpty()) {
            groups.add(PartGroup.Tools(currentTools.toList()))
            currentTools.clear()
        }
    }

    for (part in parts) {
        if (part is UIMessagePart.Tool && part.isExecuted) {
            flushContent()
            currentTools.add(part)
        } else {
            flushTools()
            currentContent.add(part)
        }
    }

    flushContent()
    flushTools()
    return groups
}

/** OpenAI 兼容协议 wire 层：合并相邻 user 项，content 统一成数组后按顺序拼接。 */
internal fun JsonArray.mergeAdjacentUserTurns(textContentType: String = "text"): JsonArray {
    val merged = mutableListOf<JsonObject>()
    for (element in this) {
        val current = element.jsonObject
        val previous = merged.lastOrNull()
        if (previous != null &&
            previous["role"]?.jsonPrimitive?.contentOrNull == "user" &&
            current["role"]?.jsonPrimitive?.contentOrNull == "user"
        ) {
            val content = buildJsonArray {
                addAll(previous["content"].asContentArray(textContentType))
                addAll(current["content"].asContentArray(textContentType))
            }
            merged[merged.lastIndex] = JsonObject(previous + ("content" to content))
        } else {
            merged.add(current)
        }
    }
    return JsonArray(merged)
}

private fun JsonElement?.asContentArray(textContentType: String): JsonArray = when (this) {
    is JsonArray -> this
    is JsonPrimitive -> buildJsonArray {
        add(buildJsonObject {
            put("type", textContentType)
            put("text", content)
        })
    }
    else -> JsonArray(emptyList())
}
