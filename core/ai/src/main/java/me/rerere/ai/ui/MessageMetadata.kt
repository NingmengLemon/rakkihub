package me.rerere.ai.ui

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import me.rerere.ai.util.json

/**
 * [UIMessagePart.metadata] 的类型安全 schema
 *
 * metadata 在序列化层仍然是 [JsonObject], 这里只是为读写提供编译期类型:
 * - 读: `part.metadataAs<ClaudeReasoningMetadata>()?.signature`
 * - 写: `part.metadata = ClaudeReasoningMetadata(signature = ...).toMetadata()`
 *
 * 所有字段必须可空且 key 与历史数据保持一致(必要时用 [SerialName]),
 * 否则旧会话中持久化的 metadata 将无法解析
 */
sealed interface PartMetadata

/**
 * Claude thinking block 的元数据, 回传时需要携带 signature
 *
 * [redactedData] 不为空时这一块是 redacted_thinking, 没有可见文本, 回传时原样带上加密数据
 */
@Serializable
data class ClaudeReasoningMetadata(
    val signature: String? = null,
    @SerialName("claude_redacted_data")
    val redactedData: String? = null,
) : PartMetadata

/**
 * OpenAI Responses API reasoning item 的元数据, 回传时需要携带 id 和 encrypted_content
 */
@Serializable
data class OpenAIReasoningMetadata(
    @SerialName("reasoning_id")
    val reasoningId: String? = null,
    @SerialName("encrypted_content")
    val encryptedContent: String? = null,
) : PartMetadata

/**
 * 服务端工具原始块所属的 wire protocol，与实际提供商品牌无关。
 */
@Serializable
enum class ServerToolProtocol {
    @SerialName("openai_responses")
    OPENAI_RESPONSES,

    @SerialName("anthropic_messages")
    ANTHROPIC_MESSAGES,

    @SerialName("google_generate_content")
    GOOGLE_GENERATE_CONTENT,

    @SerialName("google_interactions")
    GOOGLE_INTERACTIONS,
}

/**
 * Provider 服务端工具的原始协议块。
 *
 * 通用字段保存在 [UIMessagePart.ServerTool] 自身；这里额外保留 Provider 原始调用/结果，供多轮请求
 * 原样回传。OpenAI Responses API 通常只使用 [call]，Claude 则分别使用 [call] 和 [result]。
 */
@Serializable
data class ServerToolMetadata(
    @SerialName("server_tool_protocol")
    val protocol: ServerToolProtocol? = null,
    @SerialName("server_tool_call")
    val call: JsonObject? = null,
    @SerialName("server_tool_call_index")
    val callIndex: Int? = null,
    @SerialName("server_tool_result")
    val result: JsonObject? = null,
    @SerialName("server_tool_result_index")
    val resultIndex: Int? = null,
) : PartMetadata

/**
 * OpenRouter Chat Completions 的结构化 reasoning_details，工具调用续轮时需要原样回传。
 */
@Serializable
data class OpenRouterReasoningMetadata(
    @SerialName("openrouter_reasoning_details")
    val reasoningDetails: JsonArray? = null,
) : PartMetadata

/**
 * Google Gemini 部件的 thoughtSignature，回传时需要携带
 */
@Serializable
data class GoogleThoughtMetadata(
    val thoughtSignature: String? = null,
) : PartMetadata

/**
 * Google Interactions API 的 thought / function_call step 签名，无状态多轮时需要原样回传。
 *
 * 与 [GoogleThoughtMetadata] 使用不同的 key，避免在 generateContent 与 Interactions 之间切换时
 * 把一种协议的签名发给另一种协议。
 */
@Serializable
data class GoogleInteractionsMetadata(
    @SerialName("interactions_signature")
    val signature: String? = null,
) : PartMetadata

/**
 * 文件编辑类工具(如 workspace_edit_file)输出部件的元数据,
 * 携带 unified diff 文本供 UI 渲染 diff view, 不会发送给 API
 */
@Serializable
data class DiffMetadata(
    val diff: String? = null,
) : PartMetadata

/**
 * 图片的 OCR 文本，挂在图片 part 的 metadata 上。
 *
 * 纯文本模型每轮请求都要把历史图片换成文字；把识别结果落进会话后就不用重复识别，
 * 缓存过期或被清理也不影响。图片本体仍然保留，UI 照常显示。
 */
@Serializable
data class OcrMetadata(
    @SerialName("ocr_text")
    val text: String? = null,
) : PartMetadata

/** 给图片 part 挂上 OCR 文本，保留已有的其它 metadata。 */
fun UIMessagePart.Image.withOcrText(text: String): UIMessagePart.Image {
    val addition = OcrMetadata(text = text).toMetadata()
    return copy(metadata = metadata?.let { JsonObject(it + addition) } ?: addition)
}

/** 读取图片 part 上的 OCR 文本。 */
fun UIMessagePart.Image.ocrText(): String? = metadataAs<OcrMetadata>()?.text

/**
 * 将 metadata 解析为类型化的 [PartMetadata], 解析失败或 metadata 为 null 时返回 null
 *
 * 由于 json 配置了 ignoreUnknownKeys, 不同 provider 的 metadata 互不干扰
 * (例如切换 provider 后, OpenAI 写入的 reasoning 元数据不会影响 Claude 的解析)
 */
inline fun <reified T : PartMetadata> UIMessagePart.metadataAs(): T? = metadata?.let {
    runCatching { json.decodeFromJsonElement<T>(it) }.getOrNull()
}

/**
 * 将类型化的 [PartMetadata] 编码为 metadata [JsonObject]
 *
 * 由于 json 配置了 explicitNulls = false, 值为 null 的字段不会写入
 */
inline fun <reified T : PartMetadata> T.toMetadata(): JsonObject =
    json.encodeToJsonElement(this).jsonObject

/**
 * Gemini 偶尔把 `thoughtSignature` 单独作为一个 part 下发（思考模型、多轮工具调用）。
 *
 * 这种 part 没有任何内容，直接当未知类型会让解析抛异常。返回签名，由调用方挂到相邻 part 上，
 * 回传历史时才不会丢掉。值不是 JSON 基本类型时不认，交给调用方按未知类型处理。
 */
internal fun JsonObject.standaloneGoogleThoughtSignature(): String? {
    if (keys.isEmpty() || keys.any { it != "thoughtSignature" }) return null
    return (this["thoughtSignature"] as? JsonPrimitive)?.contentOrNull
}

/**
 * 把 `thoughtSignature` 合并进 part 的 metadata。
 *
 * part 已经有签名时不覆盖：独立签名通常只是同一段思考的补充，覆盖掉原有的会让回传的签名对不上。
 */
internal fun UIMessagePart.attachGoogleThoughtSignature(signature: String) {
    val existing = (metadata?.get("thoughtSignature") as? JsonPrimitive)?.contentOrNull
    if (existing != null) return
    val addition = GoogleThoughtMetadata(thoughtSignature = signature).toMetadata()
    metadata = metadata?.let { JsonObject(it + addition) } ?: addition
}

/**
 * 可以清除的思考签名协议。
 *
 * 只收 Google 两种：它们的签名挂在 part 的 metadata 或 server tool 的原始块上，删掉后请求
 * 退化成不带签名重放，服务端可接受。Claude/OpenAI Responses/OpenRouter 的签名块在重放时
 * 必须原样带回，单删签名会造出非法块，等 provider 侧重放能跳过无签名块时再开放。
 */
enum class ThoughtSignatureProtocol(
    val metadataKeys: List<String>,
    val nestedKeys: List<String>,
) {
    GOOGLE_GENERATE_CONTENT(
        metadataKeys = listOf("thoughtSignature"),
        nestedKeys = listOf("thoughtSignature"),
    ),
    GOOGLE_INTERACTIONS(
        metadataKeys = listOf("interactions_signature"),
        nestedKeys = listOf("signature", "interactions_signature"),
    ),
    ;

    companion object {
        val all: Set<ThoughtSignatureProtocol> = entries.toSet()
    }
}

private fun JsonElement.withoutSignatureKeys(keys: Set<String>): JsonElement = when (this) {
    is JsonObject -> JsonObject(
        entries.mapNotNull { (key, value) ->
            if (key in keys) null else key to value.withoutSignatureKeys(keys)
        }.toMap()
    )

    is JsonArray -> JsonArray(map { it.withoutSignatureKeys(keys) })
    else -> this
}

private fun JsonObject?.withoutTopLevelKeys(keys: Set<String>): JsonObject? {
    if (this == null || keys.isEmpty()) return this
    val stripped = JsonObject(
        entries.mapNotNull { (key, value) ->
            if (key in keys) null else key to value.withoutSignatureKeys(keys)
        }.toMap()
    )
    return stripped.ifEmpty { null }
}

/**
 * 返回去掉指定协议签名的副本，原 part 保持不变。
 *
 * metadata 是 var，但这里不能原地改：原地改会让新旧 Conversation 相等，StateFlow 的
 * 相等性判断会跳过更新，最终不会落库。server tool 的原始调用/结果里也可能嵌套签名，
 * 按协议一并清掉。
 */
fun UIMessagePart.stripThoughtSignatures(
    protocols: Set<ThoughtSignatureProtocol>,
): UIMessagePart {
    if (protocols.isEmpty()) return this
    val metadataKeys = protocols.flatMap { it.metadataKeys }.toSet()
    val nestedKeys = protocols.flatMap { it.nestedKeys }.toSet()
    return when (this) {
        is UIMessagePart.Text -> copy(metadata = metadata.withoutTopLevelKeys(metadataKeys))
        is UIMessagePart.Image -> copy(metadata = metadata.withoutTopLevelKeys(metadataKeys))
        is UIMessagePart.Video -> copy(metadata = metadata.withoutTopLevelKeys(metadataKeys))
        is UIMessagePart.Audio -> copy(metadata = metadata.withoutTopLevelKeys(metadataKeys))
        is UIMessagePart.Document -> copy(metadata = metadata.withoutTopLevelKeys(metadataKeys))
        is UIMessagePart.Reasoning -> copy(metadata = metadata.withoutTopLevelKeys(metadataKeys))
        is UIMessagePart.ToolCall -> copy(metadata = metadata.withoutTopLevelKeys(metadataKeys))
        is UIMessagePart.ToolResult -> copy(metadata = metadata.withoutTopLevelKeys(metadataKeys))
        is UIMessagePart.Tool -> copy(metadata = metadata.withoutTopLevelKeys(metadataKeys))
        is UIMessagePart.ServerTool -> {
            val stripped = metadata
                ?.withoutTopLevelKeys(metadataKeys)
                ?.let { obj ->
                    JsonObject(
                        obj.mapValues { (key, value) ->
                            if (key == "server_tool_call" || key == "server_tool_result") {
                                value.withoutSignatureKeys(nestedKeys)
                            } else {
                                value
                            }
                        }
                    )
                }
                ?.ifEmpty { null }
            copy(metadata = stripped)
        }

        is UIMessagePart.Search -> this
    }
}

/** 去掉一条消息里所有 part 上指定协议的思考签名。 */
fun UIMessage.stripThoughtSignatures(
    protocols: Set<ThoughtSignatureProtocol>,
): UIMessage =
    copy(parts = parts.map { it.stripThoughtSignatures(protocols) })
