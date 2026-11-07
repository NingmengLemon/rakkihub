package me.rerere.rikkahub.data.ai.transformers

import io.pebbletemplates.pebble.PebbleEngine
import io.pebbletemplates.pebble.loader.Loader
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.utils.toLocalDate
import me.rerere.rikkahub.utils.toLocalTime
import java.io.Reader
import java.io.StringReader
import java.io.StringWriter
import kotlin.time.toJavaInstant

class TemplateTransformer(
    private val engine: PebbleEngine,
) : InputMessageTransformer {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val template = engine.getTemplate(ctx.assistant.id.toString())
        val timeZone = TimeZone.currentSystemDefault()
        return messages.map { message ->
            if (message.skipsInputTemplate()) return@map message

            // 使用消息本身的发送时间而不是当前时间, 保证多次请求时渲染结果稳定, 不破坏 prompt 缓存
            val createdAt = message.createdAt.toInstant(timeZone).toJavaInstant()
            message.copy(
                parts = message.parts.map { part ->
                    when (part) {
                        is UIMessagePart.Text -> {
                            val result = StringWriter()
                            template.evaluate(
                                result,
                                mapOf(
                                    "message" to part.text,
                                    "role" to message.role.name.lowercase(),
                                    "time" to createdAt.toLocalTime(),
                                    "date" to createdAt.toLocalDate(),
                                )
                            )
                            part.copy(
                                text = result.toString()
                            )
                        }

                        else -> part
                    }
                }
            )
        }
    }
}

/**
 * 合成消息和空检查点不参与模板改写。
 *
 * 空检查点是历史截断的边界，模板里的角色标签之类的字面文本会把它变成非空消息，
 * 从而绕过 `isValidToUpload` 的过滤被发出去。
 */
internal fun UIMessage.skipsInputTemplate(): Boolean =
    isSynthetic || (isContextCheckpoint && toText().isBlank())

class AssistantTemplateLoader(private val settingsStore: SettingsStore) : Loader<String> {
    override fun getReader(cacheKey: String?): Reader? {
        val content = settingsStore.settingsFlow.value.assistants
            .find { it.id.toString() == cacheKey }?.messageTemplate
            ?: return null
        return StringReader(content)
    }

    override fun setCharset(charset: String?) {}

    override fun setPrefix(prefix: String?) {}

    override fun setSuffix(suffix: String?) {}

    override fun resolveRelativePath(
        relativePath: String?,
        anchorPath: String?
    ): String? {
        return relativePath
    }

    override fun createCacheKey(templateName: String?): String? {
        return templateName
    }

    override fun resourceExists(templateName: String?): Boolean {
        return settingsStore.settingsFlow.value.assistants.any { it.id.toString() == templateName }
    }
}
