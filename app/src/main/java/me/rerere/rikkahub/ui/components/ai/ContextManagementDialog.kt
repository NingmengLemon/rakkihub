package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import me.rerere.ai.core.MessageRole
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Remove01
import me.rerere.hugeicons.stroke.TriangleAlert
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.contextWindowSize
import me.rerere.rikkahub.data.model.removeCompressionPairs

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
    onCompress: (additionalPrompt: String, keepRecentMessages: Int) -> Unit,
    onTruncate: (keep: Int) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(ContextTab.COMPRESS) }
    var additionalPrompt by rememberSaveable { mutableStateOf("") }
    // 压缩默认全部压缩，请求前缀与对话一致，缓存命中最好
    var keepRecentMessages by rememberSaveable { mutableIntStateOf(0) }
    var truncateInput by rememberSaveable { mutableStateOf("") }
    var showAssistantWarning by remember { mutableStateOf(false) }

    // 失败/停止的压缩会留下 scratch 对，不在请求里；窗口和保留条数按清理后的节点算
    val requestConversation = remember(conversation) {
        conversation.removeCompressionPairs() ?: conversation
    }
    val nodes = requestConversation.messageNodes
    val windowSize = requestConversation.contextWindowSize().coerceAtLeast(0)
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
                            onCompress(additionalPrompt, keepRecentMessages)
                            onDismiss()
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
                enabled = tab == ContextTab.COMPRESS || truncateValid,
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
) {
    Text(stringResource(R.string.chat_page_compress_context_desc))

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
