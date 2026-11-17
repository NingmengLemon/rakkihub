package me.rerere.rikkahub.data.datastore

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.ProviderSetting
import me.rerere.asr.ASRProviderSetting
import me.rerere.mediagen.provider.MediaGenerationProviderSetting
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.ai.mcp.StdioMcpServer
import me.rerere.rikkahub.data.ai.mcp.toRuntimeConfig
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_COMPRESS_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_OCR_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_TITLE_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_TRANSLATION_PROMPT
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.model.QuickMessage
import me.rerere.rikkahub.data.model.Tag
import me.rerere.rikkahub.data.sync.s3.S3Config
import me.rerere.rikkahub.ui.theme.CustomTheme
import me.rerere.rikkahub.ui.theme.PresetThemes
import me.rerere.search.SearchCommonOptions
import me.rerere.search.SearchServiceOptions
import me.rerere.tts.provider.TTSProviderSetting
import kotlin.uuid.Uuid

// 聊天回复建议已不再生成，这个默认值只为让已存储的 suggestionPrompt 仍有定义
private val DEFAULT_SUGGESTION_PROMPT = """
    I will provide you with some chat content in the `<content>` block, including conversations between the User and the AI assistant.
    You need to act as the **User** to reply to the assistant, generating 3~5 appropriate and contextually relevant responses to help the assistant improve its answers.

    Rules:
    1. Reply directly with suggestions, do not add any formatting, and separate suggestions with newlines, no need to add markdown list formats.
    2. Use {locale} language.
    3. Ensure each suggestion is valid.
    4. Each suggestion should not exceed 10 characters.
    5. Imitate the user's previous conversational style.
    6. Act as a User, not an Assistant!

    <content>
    {content}
    </content>
""".trimIndent()

@Serializable
data class Settings(
    @Transient
    val init: Boolean = false,
    val dynamicColor: Boolean = true,
    val themeId: String = PresetThemes[0].id,
    val customThemes: List<CustomTheme> = emptyList(),
    val developerMode: Boolean = false,
    val displaySetting: DisplaySetting = DisplaySetting(),
    val networkSetting: NetworkSetting = NetworkSetting(),
    val agentSetting: AgentSetting = AgentSetting(),
    val favoriteModels: List<Uuid> = emptyList(),
    val chatModelId: Uuid = Uuid.random(),
    val fastModelId: Uuid = Uuid.random(),
    val fastModelReasoningLevel: ReasoningLevel = ReasoningLevel.AUTO,
    // 记忆整理用的模型，没设置时用快速模型
    val memoryModelId: Uuid? = null,
    val memoryModelReasoningLevel: ReasoningLevel = ReasoningLevel.AUTO,
    val imageGenerationModelId: Uuid = Uuid.random(),
    val enableAutoTitle: Boolean = true,
    val titlePrompt: String = DEFAULT_TITLE_PROMPT,
    val translateModeId: Uuid = Uuid.random(),
    val translatePrompt: String = DEFAULT_TRANSLATION_PROMPT,
    val translateThinkingBudget: Int = 0,
    // 翻译页的目标语言，存 BCP 47 语言标签
    val translateTargetLanguage: String = "zh-CN",
    val enableSuggestion: Boolean = true,
    val suggestionPrompt: String = DEFAULT_SUGGESTION_PROMPT,
    val ocrModelId: Uuid = Uuid.random(),
    val ocrPrompt: String = DEFAULT_OCR_PROMPT,
    // 上下文压缩的指令，{locale} 生成时替换成系统语言
    val compressPrompt: String = DEFAULT_COMPRESS_PROMPT,
    val assistantId: Uuid = DEFAULT_ASSISTANT_ID,
    val providers: List<ProviderSetting> = DEFAULT_PROVIDERS,
    val assistants: List<Assistant> = DEFAULT_ASSISTANTS,
    val assistantTags: List<Tag> = emptyList(),
    val searchServices: List<SearchServiceOptions> = listOf(SearchServiceOptions.DEFAULT),
    val searchCommonOptions: SearchCommonOptions = SearchCommonOptions(),
    val searchServiceSelected: Int = 0,
    val mcpServers: List<McpServerConfig> = emptyList(),
    // 本地 stdio MCP；和 mcpServers 分开存，旧构建忽略新 key
    val stdioMcpServers: List<StdioMcpServer> = emptyList(),
    val webDavConfig: WebDavConfig = WebDavConfig(),
    val s3Config: S3Config = S3Config(),
    // RemoteFileStore 上传临时素材用的桶，和备份的 s3Config 互不影响；items 字段在这里不使用
    val uploadS3Config: S3Config = S3Config(),
    val mediaGenerationProviders: List<MediaGenerationProviderSetting> = emptyList(),
    val ttsProviders: List<TTSProviderSetting> = DEFAULT_TTS_PROVIDERS,
    val selectedTTSProviderId: Uuid = DEFAULT_SYSTEM_TTS_ID,
    val defaultTTSPlaybackSpeed: Float = 1.0f,
    val asrProviders: List<ASRProviderSetting> = emptyList(),
    val selectedASRProviderId: Uuid? = null,
    val modeInjections: List<PromptInjection.ModeInjection> = DEFAULT_MODE_INJECTIONS,
    val lorebooks: List<Lorebook> = emptyList(),
    val quickMessages: List<QuickMessage> = emptyList(),
    val webServerEnabled: Boolean = false,
    val webServerPort: Int = 8080,
    val webServerJwtEnabled: Boolean = false,
    val webServerAccessPassword: String = "",
    val webServerLocalhostOnly: Boolean = false,
    val backupReminderConfig: BackupReminderConfig = BackupReminderConfig(),
    val sponsorAlertDismissedAt: Int = 0,
) {
    companion object {
        // 构造一个用于初始化的settings, 但它不能用于保存，防止使用初始值存储
        fun dummy() = Settings(init = true)
    }
}

/** HTTP 与 stdio MCP 的运行时配置；stdio 不写入 `mcpServers` 持久化列表。 */
fun Settings.allMcpServers(): List<McpServerConfig> =
    mcpServers + stdioMcpServers.map { it.toRuntimeConfig() }

/** HTTP 与 stdio MCP 的 id 集合；校验 assistant 引用时不要只看 mcpServers。 */
fun Settings.allMcpServerIds(): Set<Uuid> =
    buildSet {
        addAll(mcpServers.map { it.id })
        addAll(stdioMcpServers.map { it.id })
    }

@Serializable
data class NetworkSetting(
    val userAgent: String = "",
    val proxyUrl: String = "",
    val proxyUsername: String = "",
    val proxyPassword: String = "",
    // 自动重试默认关闭，用户可在网络设置里显式开启；旧版本字段保留，首次升级由 V6 迁移关掉
    val enableAutoRetry: Boolean = false,
    // 自动重试的最大次数（不包含首次请求）；0 表示只发一次，不重试
    val maxRetries: Int = 3,
    // 流式响应连续 90 秒没有任何数据（含 Anthropic 的保活 ping）时中断；关掉后只依赖网络层读超时
    val streamIdleTimeoutEnabled: Boolean = true,
) {
    companion object {
        const val MAX_RETRIES = 10
    }
}

@Serializable
data class AgentSetting(
    // 单个工具调用的外层超时（秒）；0 表示不限制。实际生效取它与工具自身声明超时的较大值
    val toolTimeoutSeconds: Int = DEFAULT_TOOL_TIMEOUT_SECONDS,
    // workspace_shell 的默认命令超时（秒）
    val shellDefaultTimeoutSeconds: Int = DEFAULT_SHELL_DEFAULT_TIMEOUT_SECONDS,
    // workspace_shell 允许请求的最大命令超时（秒），同时是外层工具超时的下限
    val shellMaxTimeoutSeconds: Int = DEFAULT_SHELL_MAX_TIMEOUT_SECONDS,
) {
    companion object {
        const val DEFAULT_TOOL_TIMEOUT_SECONDS = 300
        const val DEFAULT_SHELL_DEFAULT_TIMEOUT_SECONDS = 30
        const val DEFAULT_SHELL_MAX_TIMEOUT_SECONDS = 600
        const val MIN_TOOL_TIMEOUT_SECONDS = 60
        const val MAX_TOOL_TIMEOUT_SECONDS = 30 * 60
        const val TOOL_TIMEOUT_STEP_SECONDS = 60
        const val MIN_SHELL_DEFAULT_TIMEOUT_SECONDS = 5
        const val MIN_SHELL_TIMEOUT_SECONDS = 60
        const val MAX_SHELL_TIMEOUT_SECONDS = 3600
    }

    /**
     * 读盘与修改后统一收口：工具超时对齐到分钟（0 保持关闭），shell 上限不小于 60 秒，
     * shell 默认值落在页面默认滑块的下限与上限之间。
     */
    fun normalized(): AgentSetting {
        val maxSeconds = shellMaxTimeoutSeconds.coerceIn(
            MIN_SHELL_TIMEOUT_SECONDS,
            MAX_SHELL_TIMEOUT_SECONDS,
        )
        val toolSeconds = if (toolTimeoutSeconds <= 0) {
            0
        } else {
            (toolTimeoutSeconds / TOOL_TIMEOUT_STEP_SECONDS)
                .coerceIn(
                    MIN_TOOL_TIMEOUT_SECONDS / TOOL_TIMEOUT_STEP_SECONDS,
                    MAX_TOOL_TIMEOUT_SECONDS / TOOL_TIMEOUT_STEP_SECONDS,
                ) * TOOL_TIMEOUT_STEP_SECONDS
        }
        return copy(
            toolTimeoutSeconds = toolSeconds,
            shellMaxTimeoutSeconds = maxSeconds,
            shellDefaultTimeoutSeconds = shellDefaultTimeoutSeconds.coerceIn(
                MIN_SHELL_DEFAULT_TIMEOUT_SECONDS,
                maxSeconds,
            ),
        )
    }
}

@Serializable
enum class ChatFontFamily {
    @SerialName("default")
    DEFAULT,

    @SerialName("serif")
    SERIF,

    @SerialName("monospace")
    MONOSPACE,

    @SerialName("custom")
    CUSTOM,
}

@Serializable
enum class BackgroundEffectType {
    @SerialName("blur")
    BLUR,

    @SerialName("glass")
    GLASS,
}

@Serializable
enum class ConversationSortOrder {
    @SerialName("update_time")
    UPDATE_TIME,

    @SerialName("create_time")
    CREATE_TIME,
}

@Serializable
data class DisplaySetting(
    val userAvatar: Avatar = Avatar.Dummy,
    val userNickname: String = "",
    val showUserAvatar: Boolean = true,
    val showAssistantBubble: Boolean = false,
    val bubbleOpacity: Float = 1.0f,
    val showModelIcon: Boolean = true,
    val showModelName: Boolean = true,
    val showDateTimeInMessage: Boolean = false,
    val showTokenUsage: Boolean = true,
    // 错误卡片默认一直保留：网络之类的问题可能发生在用户离开时，自动消失会让人看不到原因
    val autoDismissErrorCards: Boolean = false,
    val showThinkingContent: Boolean = true,
    val autoCloseThinking: Boolean = true,
    val updateCheckDisabledUntilEpochMillis: Long = 0L,
    val showMessageJumper: Boolean = true,
    val messageJumperOnLeft: Boolean = false,
    val fontSizeRatio: Float = 1.0f,
    val enableMessageGenerationHapticEffect: Boolean = false,
    val skipCropImage: Boolean = true,
    val enableNotificationOnMessageGeneration: Boolean = false,
    val enableLiveUpdateNotification: Boolean = false,
    val enableFloatingChatWindow: Boolean = false,
    val codeBlockAutoWrap: Boolean = false,
    val codeBlockAutoCollapse: Boolean = false,
    val showLineNumbers: Boolean = false,
    val ttsOnlyReadQuoted: Boolean = false,
    val ttsOnlyReadOutsideBrackets: Boolean = false,
    val autoPlayTTSAfterGeneration: Boolean = false,
    val voiceModeListenWhileReplying: Boolean = true,
    val pasteLongTextAsFile: Boolean = false,
    val pasteLongTextThreshold: Int = 1000,
    val sendOnEnter: Boolean = false,
    // 停止生成是不可撤销的，误触代价高；默认先确认，用户可以在设置里关掉
    val confirmStopGeneration: Boolean = true,
    val enableAutoScroll: Boolean = true,
    val enableLatexRendering: Boolean = true,
    val enableQuoteHighlight: Boolean = false,
    val enableBlurEffect: Boolean = false,
    val backgroundEffectType: BackgroundEffectType = BackgroundEffectType.BLUR,
    val chatFontFamily: ChatFontFamily = ChatFontFamily.DEFAULT,
    val chatCustomFontPath: String = "",
    val chatCustomFontName: String = "",
    val enableVolumeKeyScroll: Boolean = false,
    val volumeKeyScrollRatio: Float = 1.0f,
    val conversationSortOrder: ConversationSortOrder = ConversationSortOrder.UPDATE_TIME,
)

@Serializable
data class WebDavConfig(
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val path: String = "rikkahub_backups",
    val items: List<BackupItem> = BackupItem.DEFAULT_SELECTION,
) {
    @Serializable
    enum class BackupItem {
        /** 聊天记录与设置。始终包含，不出现在「备份内容」的选项里。 */
        DATABASE,
        FILES,

        /** 工作区文件区（`workspaces/<root>/files`），不含 PRoot 根文件系统。本地导出默认选；WebDAV 默认不选。 */
        WORKSPACES,
        ;

        companion object {
            /** 可以在界面上关掉的两项。 */
            val SELECTABLE = listOf(FILES, WORKSPACES)

            /** WebDAV 备份默认只带附件；工作区体积大，仍由用户明确勾选。 */
            val DEFAULT_SELECTION = normalize(listOf(FILES))

            /** 本地导出/导入页默认勾选附件与工作区文件。 */
            val LOCAL_DEFAULT_SELECTION = normalize(listOf(FILES, WORKSPACES))

            /**
             * 归一成「聊天记录 + 选中的可选项」。
             *
             * 一份归档必须有数据库：附件名由库里的文本列引用，工作区文件也挂在库里的工作区行上
             * （C40），只导出附件在恢复时对不上任何引用。旧的设置与旧备份里的 settings 可能带着
             * 非法组合，所以读取与写入两侧都过一遍。
             */
            fun normalize(items: Collection<BackupItem>): List<BackupItem> =
                listOf(DATABASE) + SELECTABLE.filter { it in items }
        }
    }
}

@Serializable
data class BackupReminderConfig(
    val enabled: Boolean = false,
    val intervalDays: Int = 7,
    val lastBackupTime: Long = 0L,
)
