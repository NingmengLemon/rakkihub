package me.rerere.rikkahub.data.ai

import android.content.Context
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.filterModelVisible
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.isHistoryTruncationMarker
import me.rerere.rikkahub.utils.applyPlaceholders
import java.util.Locale

/** 用快速模型生成会话标题，只负责请求模型，写回会话由调用方处理 */
class ConversationMetaGenerator(
    private val context: Context,
    private val providerManager: ProviderManager,
    private val usageRecorder: UsageRecorder,
) {
    /** 快速模型或它的提供商找不到时抛出异常，提示用户检查设置 */
    suspend fun generateTitle(settings: Settings, conversation: Conversation): String {
        val model = settings.findModelById(settings.fastModelId)
            ?: throw IllegalStateException(context.getString(R.string.error_fast_model_not_found))
        val provider = model.findProvider(settings.providers)
            ?: throw IllegalStateException(context.getString(R.string.error_fast_model_provider_not_found))
        return generate(
            settings,
            model,
            provider,
            conversation,
            settings.titlePrompt,
            recentMessages = 4,
            UsageSource.TITLE
        )
            .trim()
    }

    private suspend fun generate(
        settings: Settings,
        model: Model,
        provider: ProviderSetting,
        conversation: Conversation,
        prompt: String,
        recentMessages: Int,
        source: UsageSource,
    ): String {
        val result = usageRecorder.wrap(
            providerManager.getProviderByType(provider),
            UsageTag(source, conversation.assistantId, conversation.id),
        ).generateText(
            providerSetting = provider,
            messages = listOf(
                UIMessage.user(
                    prompt.applyPlaceholders(
                        "locale" to Locale.getDefault().displayName,
                        "content" to conversation.recentModelMessages(recentMessages)
                            .joinToString("\n\n") { it.summaryAsText(maxLength = 500) },
                    )
                ),
            ),
            params = backgroundTextGenerationParams(model, conversation.id, settings.fastModelReasoningLevel),
        )
        return result.message.toText()
    }
}

/** 标题提示词只看模型可见的最近消息，失败回复和截断标记不参与 */
internal fun Conversation.recentModelMessages(count: Int): List<UIMessage> =
    currentMessages.filterModelVisible().filterNot { it.isHistoryTruncationMarker }.takeLast(count)
