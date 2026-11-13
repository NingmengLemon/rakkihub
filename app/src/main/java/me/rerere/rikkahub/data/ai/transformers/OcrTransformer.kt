package me.rerere.rikkahub.data.ai.transformers

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.ocrText
import me.rerere.common.cache.LruCache
import me.rerere.common.cache.SingleFileCacheStore
import me.rerere.rikkahub.data.ai.UsageRecorder
import me.rerere.rikkahub.data.ai.UsageSource
import me.rerere.rikkahub.data.ai.UsageTag
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.service.ChatService
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.io.File
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid

private const val TAG = "OcrTransformer"

/** 缓存里存的是原始识别文本，key 带版本前缀，旧版本存过的格式化结果自然失效 */
private const val CACHE_KEY_PREFIX = "v2:"

object OcrTransformer : InputMessageTransformer, KoinComponent {
    private val cache by lazy {
        val context = get<Context>()
        val json = Json { allowStructuredMapKeys = true }
        val store = SingleFileCacheStore(
            file = File(context.cacheDir, "ocr_cache.json"),
            keySerializer = String.serializer(),
            valueSerializer = String.serializer(),
            json = json
        )
        LruCache(
            capacity = 64,
            store = store,
            deleteOnEvict = true,
            preloadFromStore = true,
            expireAfterWriteMillis = 3.days.inWholeMilliseconds,
        )
    }

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        if (ctx.model.inputModalities.contains(Modality.IMAGE)) {
            return messages
        }

        val images = messages
            .flatMap { message -> message.parts }
            .filterIsInstance<UIMessagePart.Image>()
            .filter { part -> part.url.startsWith("file:") }
        if (images.isEmpty()) return messages

        return withContext(Dispatchers.IO) {
            // 识别结果优先从图片 part 的 metadata 取：写回过会话的图片不用再识别，
            // 缓存过期或被清理也不影响
            val replacements = mutableMapOf<String, String>()
            images.forEach { part ->
                part.ocrText()?.let { text -> replacements[part.url] = formatOcr(text) }
            }
            val missing = images.filter { part -> part.url !in replacements }
            // 缓存命中的也要写回，否则同一张图在别的会话里每轮都只能靠缓存
            val toPersist = mutableMapOf<String, String>()

            try {
                if (missing.isNotEmpty()) {
                    val settings = get<SettingsStore>().settingsFlow.value
                    val model = settings.findModelById(settings.ocrModelId)
                    val providerSetting = model?.findProvider(settings.providers)
                    if (model == null || providerSetting == null) {
                        // 没配置 OCR 模型：保持原来的中性占位，不发请求也不报错
                        missing.forEach { part -> replacements[part.url] = NO_OCR_MODEL_PLACEHOLDER }
                    } else {
                        val provider = get<UsageRecorder>().wrap(
                            get<ProviderManager>().getProviderByType(providerSetting),
                            UsageTag(UsageSource.OCR),
                        )
                        var statusShown = false
                        missing.forEach { part ->
                            val cacheKey = CACHE_KEY_PREFIX + part.url
                            val cached = cache.get(cacheKey)
                            val text = if (cached != null) {
                                cached
                            } else {
                                // 只有真正要调模型时才显示识别状态，缓存命中不打扰
                                if (!statusShown) {
                                    ctx.processingStatus.value = "正在识别图片..."
                                    statusShown = true
                                }
                                callOcrModel(part, settings, model, providerSetting, provider)
                                    ?.also { cache.put(cacheKey, it) }
                            }
                            if (text != null) {
                                replacements[part.url] = formatOcr(text)
                                toPersist[part.url] = text
                            } else {
                                // 失败不写回，下一轮还会重试
                                replacements[part.url] = OCR_ERROR_PLACEHOLDER
                            }
                        }
                    }
                }
                persistOcrTexts(ctx.conversationId, toPersist)

                messages.map { message ->
                    message.copy(
                        parts = message.parts.map { part ->
                            if (part is UIMessagePart.Image && part.url.startsWith("file:")) {
                                UIMessagePart.Text(replacements.getValue(part.url))
                            } else {
                                part
                            }
                        }
                    )
                }
            } finally {
                ctx.processingStatus.value = null
            }
        }
    }

    /** 调 OCR 模型识别单张图片，返回原始文本；失败返回 null，不写缓存也不写回会话 */
    private suspend fun callOcrModel(
        part: UIMessagePart.Image,
        settings: Settings,
        model: Model,
        providerSetting: ProviderSetting,
        provider: Provider<ProviderSetting>,
    ): String? = try {
        val result = provider.generateText(
            providerSetting = providerSetting,
            messages = listOf(
                UIMessage.system(settings.ocrPrompt),
                UIMessage(
                    role = MessageRole.USER,
                    parts = listOf(UIMessagePart.Image(part.url))
                )
            ),
            params = TextGenerationParams(
                model = model,
                customHeaders = model.customHeaders,
                customBody = model.customBodies,
            ),
        )
        val content = result.message.toText()
        if (content.isBlank()) {
            null
        } else {
            Log.i(TAG, "ocrText: $content")
            content
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "ocrText failed for ${part.url}", e)
        null
    }

    private suspend fun persistOcrTexts(conversationId: Uuid?, texts: Map<String, String>) {
        if (conversationId == null || texts.isEmpty()) return
        try {
            get<ChatService>().attachOcrTexts(conversationId, texts)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "persistOcrTexts failed", e)
        }
    }

    private fun formatOcr(content: String): String = """
        <image_file_ocr>
           $content
        </image_file_ocr>
        * The image_file_ocr tag contains a description of an image that the user uploaded to you, not the user's prompt.
    """.trimIndent()
}

private const val OCR_ERROR_PLACEHOLDER = "[ERROR, OCR failed]"
private const val NO_OCR_MODEL_PLACEHOLDER = "[Image]"
