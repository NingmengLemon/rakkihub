package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Remove01
import me.rerere.hugeicons.stroke.TriangleAlert
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.shouldUseConversationModel
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.model.CompressionPlanCounts
import me.rerere.rikkahub.data.model.CompressionPlanResult
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.compressionPlanCounts
import me.rerere.rikkahub.data.model.contextWindowSize
import me.rerere.rikkahub.data.model.getAssistantOf
import me.rerere.rikkahub.data.model.getChatModelOf
import me.rerere.rikkahub.data.model.planCompressionFor
import me.rerere.rikkahub.data.model.removeCompressionPairs
import me.rerere.rikkahub.ui.context.LocalSettings
import kotlin.uuid.Uuid

private enum class ContextTab { COMPRESS, TRUNCATE }

/**
 * 上下文管理：压缩（调用模型写摘要）与截断（不调用模型，直接丢弃旧消息）。
 *
 * 两者共用「保留最近 N 条」的边界概念，也都以消息里的检查点作为后续请求的起点。
 */
@Composable
fun ContextManagementDialog(
    conversation: Conversation,
    onDismiss: () -> Unit,
    onCompress: (
        additionalPrompt: String,
        keepRecentMessages: Int,
        modelId: Uuid?,
        reasoningLevel: ReasoningLevel?,
    ) -> Unit,
    onTruncate: (keep: Int) -> Unit,
) {
    val settings = LocalSettings.current
    var tab by rememberSaveable { mutableStateOf(ContextTab.COMPRESS) }
    var additionalPrompt by rememberSaveable { mutableStateOf("") }
    // 压缩默认全部压缩，请求前缀与对话一致，缓存命中最好
    var keepRecentMessages by rememberSaveable { mutableIntStateOf(0) }
    var truncateInput by rememberSaveable { mutableStateOf("") }
    var showAssistantWarning by remember { mutableStateOf(false) }
    val assistant = remember(settings, conversation) { settings.getAssistantOf(conversation) }
    val currentModel = remember(settings, conversation) { settings.getChatModelOf(conversation) }
    val currentModelId = currentModel?.id
    var selectedModelId by remember { mutableStateOf(currentModelId) }
    var reasoningLevel by remember { mutableStateOf(assistant.reasoningLevel) }

    // 失败/停止的压缩会留下 scratch 对，不在请求里；窗口和保留条数按清理后的节点算
    val requestConversation = remember(conversation) {
        conversation.removeCompressionPairs() ?: conversation
    }
    val nodes = requestConversation.messageNodes
    val windowSize = requestConversation.contextWindowSize().coerceAtLeast(0)
    // 选中会话当前模型时必须走会话模型，保留 builtInSearch 等快照；临时换模型才用 provider 里的原始模型
    val selectedModel = remember(selectedModelId, currentModel, settings) {
        if (shouldUseConversationModel(selectedModelId, currentModel?.id)) {
            currentModel
        } else {
            settings.findModelById(selectedModelId) ?: currentModel
        }
    }
    var planLoading by remember { mutableStateOf(true) }
    var compressionPlan by remember { mutableStateOf<CompressionPlanResult?>(null) }
    LaunchedEffect(requestConversation, keepRecentMessages, selectedModel, assistant) {
        planLoading = true
        compressionPlan = withContext(Dispatchers.Default) {
            selectedModel?.let { requestConversation.planCompressionFor(it, assistant, keepRecentMessages) }
        }
        planLoading = false
    }
    val compressionCounts = remember(compressionPlan, requestConversation) {
        (compressionPlan as? CompressionPlanResult.Ready)
            ?.let { requestConversation.compressionPlanCounts(it.plan) }
    }
    val compressionReady = compressionPlan is CompressionPlanResult.Ready
    val reasoningSupported = selectedModel?.abilities?.contains(ModelAbility.REASONING) == true
    val truncateKeep = truncateInput.toIntOrNull()
    val truncateValid = truncateKeep != null && truncateKeep in 1 until windowSize
    val firstKeptRole = truncateKeep
        ?.takeIf { truncateValid }
        ?.let { nodes[nodes.size - it].currentMessage.role }
    val assistantStart = firstKeptRole == MessageRole.ASSISTANT

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.chat_page_manage_context))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ContextTab.entries.forEachIndexed { index, item ->
                        SegmentedButton(
                            selected = tab == item,
                            onClick = { tab = item },
                            shape = SegmentedButtonDefaults.itemShape(index, ContextTab.entries.size),
                        ) {
                            Text(
                                stringResource(
                                    when (item) {
                                        ContextTab.COMPRESS -> R.string.chat_page_manage_context_tab_compress
                                        ContextTab.TRUNCATE -> R.string.chat_page_manage_context_tab_truncate
                                    }
                                )
                            )
                        }
                    }
                }

                when (tab) {
                    ContextTab.COMPRESS -> CompressionPane(
                        additionalPrompt = additionalPrompt,
                        onAdditionalPromptChange = { additionalPrompt = it },
                        keepRecentMessages = keepRecentMessages,
                        onKeepRecentChange = { keepRecentMessages = it },
                        providers = settings.providers,
                        selectedModelId = selectedModelId,
                        onSelectModel = { selectedModelId = it },
                        fallbackModelId = currentModelId,
                        reasoningLevel = reasoningLevel,
                        reasoningSupported = reasoningSupported,
                        onReasoningLevelChange = { reasoningLevel = it },
                        plan = compressionPlan,
                        planLoading = planLoading,
                        counts = compressionCounts,
                    )

                    ContextTab.TRUNCATE -> TruncationPane(
                        windowSize = windowSize,
                        input = truncateInput,
                        keep = truncateKeep,
                        valid = truncateValid,
                        assistantStart = assistantStart,
                        onInputChange = { truncateInput = it },
                        onMinus = {
                            truncateKeep?.let {
                                truncateInput = (it - 1).coerceAtLeast(1).toString()
                            }
                        },
                        onPlus = {
                            truncateInput = ((truncateKeep ?: 0) + 1).toString()
                        },
                        onShowAssistantWarning = { showAssistantWarning = true },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (tab) {
                        ContextTab.COMPRESS -> {
                            if (compressionReady) {
                                onCompress(
                                    additionalPrompt,
                                    keepRecentMessages,
                                    selectedModelId,
                                    reasoningLevel,
                                )
                                onDismiss()
                            }
                        }

                        ContextTab.TRUNCATE -> {
                            val keep = truncateKeep
                            if (keep != null && truncateValid) {
                                onTruncate(keep)
                                onDismiss()
                            }
                        }
                    }
                },
                enabled = when (tab) {
                    ContextTab.COMPRESS -> compressionReady
                    ContextTab.TRUNCATE -> truncateValid
                },
            ) {
                Text(
                    stringResource(
                        when (tab) {
                            ContextTab.COMPRESS -> R.string.confirm
                            ContextTab.TRUNCATE -> R.string.chat_page_truncate_confirm
                        }
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )

    if (showAssistantWarning) {
        AlertDialog(
            onDismissRequest = { showAssistantWarning = false },
            title = { Text(stringResource(R.string.chat_page_truncate_assistant_title)) },
            text = { Text(stringResource(R.string.chat_page_truncate_assistant_desc)) },
            confirmButton = {
                TextButton(onClick = { showAssistantWarning = false }) {
                    Text(stringResource(R.string.confirm))
                }
            },
        )
    }
}

@Composable
private fun CompressionPane(
    additionalPrompt: String,
    onAdditionalPromptChange: (String) -> Unit,
    keepRecentMessages: Int,
    onKeepRecentChange: (Int) -> Unit,
    providers: List<ProviderSetting>,
    selectedModelId: Uuid?,
    onSelectModel: (Uuid) -> Unit,
    fallbackModelId: Uuid?,
    reasoningLevel: ReasoningLevel,
    reasoningSupported: Boolean,
    onReasoningLevelChange: (ReasoningLevel) -> Unit,
    plan: CompressionPlanResult?,
    planLoading: Boolean,
    counts: CompressionPlanCounts?,
) {
    Text(stringResource(R.string.chat_page_compress_context_desc))

    Text(
        text = stringResource(R.string.chat_page_compress_model),
        style = MaterialTheme.typography.labelLarge,
    )
    CompressionModelPicker(
        providers = providers,
        selectedModelId = selectedModelId,
        fallbackModelId = fallbackModelId,
        onSelect = onSelectModel,
    )
    Text(
        text = stringResource(R.string.chat_page_compress_temporary_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (reasoningSupported) {
        Text(
            text = stringResource(R.string.chat_page_compress_reasoning),
            style = MaterialTheme.typography.labelLarge,
        )
        ReasoningLevelChips(
            selected = reasoningLevel,
            onSelect = onReasoningLevelChange,
        )
    }

    OutlinedTextField(
        value = additionalPrompt,
        onValueChange = onAdditionalPromptChange,
        label = { Text(stringResource(R.string.chat_page_compress_additional_prompt)) },
        placeholder = { Text(stringResource(R.string.chat_page_compress_additional_prompt_hint)) },
        modifier = Modifier.fillMaxWidth(),
        maxLines = 4,
    )

    Text(
        text = stringResource(R.string.chat_page_compress_prompt_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    KeepRecentStepper(
        text = keepRecentMessages.toString(),
        onTextChange = { onKeepRecentChange(it.toIntOrNull() ?: 0) },
        onMinus = { onKeepRecentChange((keepRecentMessages - 1).coerceAtLeast(0)) },
        onPlus = { onKeepRecentChange(keepRecentMessages + 1) },
    )

    Text(
        text = stringResource(R.string.chat_page_compress_keep_recent_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    CompressionPlanPreview(plan = plan, planLoading = planLoading, counts = counts)
}

@Composable
private fun CompressionModelPicker(
    providers: List<ProviderSetting>,
    selectedModelId: Uuid?,
    fallbackModelId: Uuid?,
    onSelect: (Uuid) -> Unit,
) {
    val models = remember(providers) {
        providers
            .filter { it.enabled }
            .flatMap { provider ->
                provider.models
                    .filter { it.type == ModelType.CHAT }
                    .map { provider to it }
            }
    }
    val allModels = remember(providers) {
        providers.flatMap { provider -> provider.models.map { provider to it } }
    }
    val selected = models.firstOrNull { it.second.id == selectedModelId }?.second
        ?: allModels.firstOrNull { it.second.id == fallbackModelId }?.second
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = selected?.displayName?.ifBlank { selected.modelId }
                    ?: stringResource(R.string.chat_page_compress_model_none),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            models.forEach { (provider, model) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "${model.displayName.ifBlank { model.modelId }} · ${provider.name}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        onSelect(model.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReasoningLevelChips(
    selected: ReasoningLevel,
    onSelect: (ReasoningLevel) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ReasoningLevel.entries.forEach { level ->
            FilterChip(
                selected = level == selected,
                onClick = { onSelect(level) },
                label = { Text(level.reasoningLevelName()) },
            )
        }
    }
}

@Composable
private fun CompressionPlanPreview(
    plan: CompressionPlanResult?,
    planLoading: Boolean,
    counts: CompressionPlanCounts?,
) {
    val (text, isError) = when {
        planLoading -> stringResource(R.string.chat_page_compress_preview_loading) to false
        plan is CompressionPlanResult.Ready && counts != null -> {
            stringResource(
                R.string.chat_page_compress_preview_ready,
                counts.summarized,
                counts.kept,
            ) to false
        }

        plan is CompressionPlanResult.Ready ||
            plan == null -> stringResource(R.string.chat_page_compress_model_none) to true

        plan == CompressionPlanResult.NothingToCompress ->
            stringResource(R.string.chat_page_compress_nothing_to_compress) to true

        plan == CompressionPlanResult.PendingTools ->
            stringResource(R.string.chat_page_compress_handle_pending_tools) to true

        else -> stringResource(R.string.chat_page_compress_range_too_large) to true
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TruncationPane(
    windowSize: Int,
    input: String,
    keep: Int?,
    valid: Boolean,
    assistantStart: Boolean,
    onInputChange: (String) -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onShowAssistantWarning: () -> Unit,
) {
    Text(stringResource(R.string.chat_page_truncate_desc))

    KeepRecentStepper(
        text = input,
        onTextChange = onInputChange,
        onMinus = onMinus,
        onPlus = onPlus,
        trailing = if (assistantStart) {
            {
                IconButton(onClick = onShowAssistantWarning) {
                    Icon(
                        imageVector = HugeIcons.TriangleAlert,
                        contentDescription = stringResource(R.string.chat_page_truncate_assistant_title),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        } else {
            null
        },
    )

    val status = when {
        windowSize < 2 -> stringResource(R.string.chat_page_truncate_nothing_to_cut)
        keep == null -> stringResource(R.string.chat_page_truncate_window_empty, windowSize)
        keep < 1 -> stringResource(R.string.chat_page_truncate_keep_at_least_one)
        keep >= windowSize -> stringResource(R.string.chat_page_truncate_window_too_many, windowSize)
        else -> stringResource(R.string.chat_page_truncate_preview, windowSize, keep, windowSize - keep)
    }
    Text(
        text = status,
        style = MaterialTheme.typography.bodySmall,
        color = if (keep != null && !valid) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

@Composable
private fun KeepRecentStepper(
    text: String,
    onTextChange: (String) -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { raw -> onTextChange(raw.filter { it.isDigit() }.take(6)) },
            label = { Text(stringResource(R.string.chat_page_compress_keep_recent)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
        FilledTonalIconButton(onClick = onMinus, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = HugeIcons.Remove01,
                contentDescription = stringResource(R.string.chat_page_keep_recent_decrease),
                modifier = Modifier.size(18.dp),
            )
        }
        FilledTonalIconButton(onClick = onPlus, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = HugeIcons.Add01,
                contentDescription = stringResource(R.string.chat_page_keep_recent_increase),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
