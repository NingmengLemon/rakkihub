package me.rerere.rikkahub.ui.pages.chat

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.isEmptyInputMessage
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.files.ChatDraftStore
import me.rerere.rikkahub.data.files.LiveAttachmentReferences
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.NodeFavoriteTarget
import me.rerere.rikkahub.data.model.getAssistantOf
import me.rerere.rikkahub.data.model.getChatModelOf
import me.rerere.rikkahub.data.model.localFileUrls
import me.rerere.rikkahub.data.repository.FavoriteRepository
import me.rerere.rikkahub.service.ChatError
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.service.RecordedRetryAttempt
import me.rerere.rikkahub.ui.hooks.writeLastConversationId
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.web.ApiException
import java.util.Locale
import kotlin.uuid.Uuid

private const val TAG = "ChatVM"

/** 输入停顿多久之后写草稿：太短会频繁写盘，太长会让刚打的字在退出时丢 */
private const val DRAFT_SAVE_DELAY = 500L

class ChatVM(
    id: String,
    private val context: Application,
    private val settingsStore: SettingsStore,
    private val chatService: ChatService,
    private val favoriteRepository: FavoriteRepository,
    private val draftStore: ChatDraftStore,
    private val liveReferences: LiveAttachmentReferences,
) : ViewModel() {
    private val _conversationId: Uuid = Uuid.parse(id)
    val conversation: StateFlow<Conversation> = chatService.getConversationFlow(_conversationId)
    var chatListInitialized by mutableStateOf(false) // 聊天列表是否已经滚动到底部

    // 聊天输入状态 - 保存在 ViewModel 中避免 TransactionTooLargeException
    val inputState = ChatInputState()

    /**
     * 草稿状态。
     *
     * [lastPersistedDraft] 是磁盘上那份，用来跳过「恢复之后又把同样的内容写一遍」；
     * [editDraft] 暂存编辑历史消息之前输入框里的内容，编辑结束后放回去。
     */
    private var lastPersistedDraft: ChatDraftStore.Draft? = null
    private val editDraft = ChatEditDraft()

    // 这个会话已经被删掉了，退出时不能把草稿写回一个不存在的会话
    private var conversationDeleted = false

    // 异步任务 (从ChatService获取，响应式)
    val conversationJob: StateFlow<Job?> =
        chatService
            .getGenerationJobStateFlow(_conversationId)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val processingStatus: StateFlow<String?> =
        chatService
            .getProcessingStatusFlow(_conversationId)

    val retryAttempts: StateFlow<List<RecordedRetryAttempt>> =
        chatService.getRetryAttemptsFlow(_conversationId)

    val retrying: StateFlow<Boolean> =
        chatService.getRetryingFlow(_conversationId)

    val conversationJobs = chatService
        .getConversationJobs()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    init {
        // 添加对话引用
        chatService.addConversationReference(_conversationId)

        // 初始化对话
        viewModelScope.launch {
            chatService.initializeConversation(_conversationId)
        }

        // 记住对话ID, 方便下次启动恢复
        context.writeLastConversationId(_conversationId)

        // 输入框里的附件要让附件清理看得见：编辑历史消息期间草稿不写输入框，这段只在内存里
        viewModelScope.launch {
            snapshotFlow { inputState.messageContent.localFileUrls() }
                .collect { urls -> liveReferences.put(this@ChatVM, urls) }
        }

        // 草稿：先把上次的恢复出来，再开始观察，否则恢复本身会被当成一次「清空」写回去。
        // 编辑历史消息期间观察到的值是 null（不写盘），编辑结束由下面那段观察把编辑前的内容放回输入框
        viewModelScope.launch {
            val restored = draftStore.load(_conversationId)
            lastPersistedDraft = restored
            restored?.let {
                if (inputState.isEditing()) {
                    // 恢复得刚好撞上进编辑：别覆盖编辑内容，编辑结束后再放回
                    editDraft.restoreAfterEditing(it)
                } else {
                    applyDraftToInput(it)
                }
            }
            snapshotFlow { if (inputState.isEditing()) null else currentDraft() }
                .debounce(DRAFT_SAVE_DELAY)
                .collectLatest { draft -> if (draft != null) writeDraft(draft) }
        }
        viewModelScope.launch {
            // 编辑结束（发送、长按发送、取消）都要把输入框还给用户，不能等防抖
            snapshotFlow { inputState.isEditing() }
                .distinctUntilChanged()
                .collect { editing ->
                    if (!editing) editDraft.finishEditing(currentDraft())?.let(::applyDraftToInput)
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // 防抖窗口里的最后一行还没落盘，这里补一次；草稿很小，同步写不会卡住退出
        if (!conversationDeleted) {
            val draft = if (inputState.isEditing()) {
                // 编辑中被销毁：输入框里是编辑内容，磁盘上该留的是进编辑前那份
                editDraft.parkedDraft()
            } else {
                editDraft.draftToPersistOnExit(currentDraft())
            }
            draft?.let {
                runCatching { runBlocking { writeDraft(it) } }
                    .onFailure { error -> Log.w(TAG, "Failed to persist the chat draft", error) }
            }
        }
        // 移除对话引用与输入框的附件登记
        liveReferences.remove(this)
        chatService.removeConversationReference(_conversationId)
    }

    /**
     * 开始编辑历史消息。
     *
     * 输入框要被编辑内容占用，编辑前的内容先交给 [editDraft]，编辑结束后放回去。
     * 这份内容同时另起一次写盘（不等防抖）：否则还在防抖窗口里的那几行会因为进了编辑而丢掉。
     */
    fun beginEditingMessage(messageId: Uuid, parts: List<UIMessagePart>) {
        if (!inputState.isEditing()) {
            val current = currentDraft()
            editDraft.beginEditing(current)
            if (!current.isEmpty) viewModelScope.launch { writeDraft(current) }
        }
        inputState.editingMessage = messageId
        inputState.setContents(parts)
    }

    private suspend fun writeDraft(draft: ChatDraftStore.Draft) {
        if (draft == lastPersistedDraft) return
        try {
            draftStore.save(_conversationId, draft)
            lastPersistedDraft = draft
        } catch (e: CancellationException) {
            // 取消不是写失败，交给上层按取消处理
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist the chat draft", e)
        }
    }

    private fun currentDraft() = ChatDraftStore.Draft(
        text = inputState.textContent.text.toString(),
        parts = inputState.messageContent,
    )

    private fun applyDraftToInput(draft: ChatDraftStore.Draft) {
        inputState.messageContent = draft.parts
        inputState.setMessageText(draft.text)
    }

    // 用户设置
    val settings: StateFlow<Settings> =
        settingsStore.settingsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings.dummy())

    // 流式输出时会话每个 chunk 都在变，助手和模型只取决于下面这几个字段
    private val configuredConversation = conversation.distinctUntilChanged { old, new ->
        old.assistantId == new.assistantId && old.config == new.config &&
            old.modeInjectionIds == new.modeInjectionIds && old.lorebookIds == new.lorebookIds
    }

    // 会话视角下的助手：会话开始后，模型、思考级别、搜索等以会话上固定的配置为准
    val assistant: StateFlow<Assistant> = combine(settings, configuredConversation) { settings, conversation ->
        settings.getAssistantOf(conversation)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, settings.value.getAssistantOf(conversation.value))

    // 当前模型
    val currentChatModel: StateFlow<Model?> = combine(settings, configuredConversation) { settings, conversation ->
        settings.getChatModelOf(conversation)
    }.stateIn(viewModelScope, SharingStarted.Lazily, settings.value.getChatModelOf(conversation.value))

    // 错误状态：只留当前会话的错误，别的会话的错误不在这页显示
    val errors: StateFlow<List<ChatError>> = chatService.errors
        .map { errors -> errors.filter { it.conversationId == _conversationId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun dismissError(id: Uuid) = chatService.dismissError(id)

    fun clearErrors() = chatService.clearErrors(_conversationId)

    val messageQueue = chatService.getMessageQueueFlow(_conversationId)

    fun removeQueuedMessage(id: Uuid) = chatService.removeQueuedMessage(_conversationId, id)

    fun beginEditQueuedMessage(id: Uuid) = chatService.beginEditQueuedMessage(_conversationId, id)

    fun finishEditQueuedMessage(id: Uuid, parts: List<UIMessagePart>?) =
        chatService.finishEditQueuedMessage(_conversationId, id, parts)

    fun resumeMessageQueue() = chatService.resumeMessageQueue(_conversationId)

    // MCP管理器
    val mcpManager = chatService.mcpManager

    // 更新设置
    fun updateSettings(fn: (Settings) -> Settings): Job {
        return viewModelScope.launch {
            settingsStore.update(fn)
        }
    }

    // 聊天页对助手的修改：新会话直接改助手，会话开始后只改当前会话
    fun updateAssistant(assistant: Assistant) {
        viewModelScope.launch {
            chatService.updateChatAssistant(_conversationId) { assistant }
        }
    }

    // 新会话开始前归入抽屉里选中的文件夹
    suspend fun updateDraftFolder(folderId: Uuid?) {
        chatService.updateDraftConversationFolder(_conversationId, folderId)
    }

    // 设置聊天模型
    fun setChatModel(model: Model) {
        viewModelScope.launch {
            chatService.updateChatAssistant(_conversationId) { it.copy(chatModelId = model.id) }
        }
    }

    // 切换搜索方式
    fun updateSearch(enableWebSearch: Boolean, builtInSearch: Boolean) {
        viewModelScope.launch {
            chatService.updateChatSearch(_conversationId, enableWebSearch, builtInSearch)
        }
    }

    /**
     * 处理消息发送
     *
     * @param content 消息内容
     * @param answer 是否触发消息生成，如果为false，则仅添加消息到消息列表中
     */
    fun handleMessageSend(content: List<UIMessagePart>,answer: Boolean = true) {
        if (content.isEmptyInputMessage()) return

        chatService.sendMessage(_conversationId, content, answer)
    }

    fun handleMessageEdit(parts: List<UIMessagePart>, messageId: Uuid) {
        if (parts.isEmptyInputMessage()) return

        viewModelScope.launch {
            try {
                chatService.editMessage(_conversationId, messageId, parts)
            } catch (e: ApiException) {
                showStopGeneratingFirstError()
            }
        }
    }

    fun handleCompressContext(additionalPrompt: String, keepRecentMessages: Int) {
        chatService.startCompression(_conversationId, additionalPrompt, keepRecentMessages)
            .onFailure {
                chatService.addError(
                    error = it,
                    conversationId = _conversationId,
                    title = context.getString(R.string.error_title_compress_conversation),
                    persistable = false,
                )
            }
    }

    fun deleteCompressionPair(promptMessageId: Uuid) {
        viewModelScope.launch {
            try {
                chatService.deleteCompressionPair(_conversationId, promptMessageId)
            } catch (e: ApiException) {
                chatService.addError(
                    error = e,
                    conversationId = _conversationId,
                    title = context.getString(R.string.error_title_operation),
                    persistable = false,
                )
            }
        }
    }

    fun handleTruncateHistory(keep: Int): Job {
        return viewModelScope.launch {
            chatService.truncateHistory(_conversationId, keep).onFailure {
                chatService.addError(
                    error = it,
                    conversationId = _conversationId,
                    title = context.getString(R.string.error_title_operation),
                    persistable = false,
                )
            }
        }
    }

    suspend fun forkMessage(message: UIMessage): Conversation? {
        return try {
            chatService.forkConversationAtMessage(_conversationId, message.id)
        } catch (e: ApiException) {
            showStopGeneratingFirstError()
            null
        }
    }

    fun deleteMessage(message: UIMessage) {
        viewModelScope.launch {
            try {
                chatService.deleteMessage(_conversationId, message)
            } catch (e: ApiException) {
                showStopGeneratingFirstError()
            }
        }
    }

    fun showStopGeneratingFirstError() {
        chatService.addError(
            error = IllegalStateException(context.getString(R.string.chat_page_stop_generation_first)),
            conversationId = _conversationId,
            title = context.getString(R.string.error_title_operation),
            persistable = false,
        )
    }

    fun regenerateAtMessage(message: UIMessage) {
        chatService.regenerateAtMessage(_conversationId, message.id)
    }

    fun handleToolApproval(
        toolCallId: String,
        approved: Boolean,
        reason: String = ""
    ) {
        chatService.handleToolApproval(_conversationId, toolCallId, approved, reason)
    }

    fun handleToolAnswer(
        toolCallId: String,
        answer: String,
    ) {
        chatService.handleToolApproval(_conversationId, toolCallId, approved = true, answer = answer)
    }

    fun stopGeneration(expectedJob: Job? = null) {
        viewModelScope.launch {
            chatService.stopGeneration(_conversationId, expectedJob)
        }
    }

    fun updateTitle(title: String) {
        viewModelScope.launch {
            chatService.updateTitle(_conversationId, title)
        }
    }

    fun deleteConversation(conversation: Conversation): Job =
        viewModelScope.launch {
            chatService.deleteConversation(conversation.id)
            // 删会话时 ChatService 已经顺手删了草稿，别在退出时又给它写回去
            if (conversation.id == _conversationId) conversationDeleted = true
        }

    fun updatePinnedStatus(conversation: Conversation) {
        viewModelScope.launch {
            chatService.toggleConversationPinned(conversation.id)
        }
    }

    fun moveConversationToAssistant(conversation: Conversation, targetAssistantId: Uuid) {
        viewModelScope.launch {
            chatService.moveConversationToAssistant(conversation.id, targetAssistantId)
            if (conversation.id == _conversationId) {
                settingsStore.selectAssistant(targetAssistantId)
            }
        }
    }

    fun translateMessage(message: UIMessage, targetLanguage: Locale) {
        chatService.translateMessage(_conversationId, message, targetLanguage)
    }

    fun generateTitle(conversation: Conversation, force: Boolean = false) {
        viewModelScope.launch {
            chatService.generateTitle(conversation.id, force)
        }
    }

    fun clearTranslationField(messageId: Uuid) {
        viewModelScope.launch {
            chatService.clearTranslationField(_conversationId, messageId)
        }
    }

    fun selectMessageNode(nodeId: Uuid, selectIndex: Int) {
        viewModelScope.launch {
            try {
                chatService.selectMessageNode(_conversationId, nodeId, selectIndex)
            } catch (e: ApiException) {
                // 界面上的分支已经过时（比如刚被清理掉），切换不了就算了
                Log.w(TAG, "selectMessageNode: ${e.message}")
            }
        }
    }

    fun updateConversationSystemPrompt(systemPrompt: String?) {
        viewModelScope.launch {
            chatService.updateConversationSystemPrompt(_conversationId, systemPrompt)
        }
    }

    fun updateWorkspaceCwd(workspaceCwd: String?) {
        viewModelScope.launch {
            chatService.updateWorkspaceCwd(_conversationId, workspaceCwd)
        }
    }

    fun toggleMessageFavorite(node: MessageNode) {
        viewModelScope.launch {
            val currentlyFavorited = favoriteRepository.isNodeFavorited(_conversationId, node.id)
            if (currentlyFavorited) {
                favoriteRepository.removeNodeFavorite(_conversationId, node.id)
            } else {
                favoriteRepository.addNodeFavorite(
                    NodeFavoriteTarget(
                        conversationId = _conversationId,
                        conversationTitle = conversation.value.title,
                        nodeId = node.id,
                        node = node
                    )
                )
            }

            chatService.setNodeFavorite(_conversationId, node.id, favorite = !currentlyFavorited)
        }
    }

}
