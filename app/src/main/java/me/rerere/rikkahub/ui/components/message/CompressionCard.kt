package me.rerere.rikkahub.ui.components.message

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.ai.ui.UIMessage
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.LeftToRightListBullet
import me.rerere.rikkahub.R
import me.rerere.rikkahub.utils.writeClipboardText

/**
 * 可见压缩轮次的紧凑卡片：提示词和流式摘要共用一张卡，默认折叠，失败时展开并给出重试/删除。
 * 不是普通聊天气泡，所以不显示头像，字号也更小。
 */
@Composable
internal fun CompressionCard(
    prompt: UIMessage,
    reply: UIMessage?,
    streaming: Boolean,
    failed: Boolean,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var expanded by rememberSaveable(prompt.id.toString()) { mutableStateOf(false) }
    // 流式期间是折叠的，转失败时只靠初始值不会重算，需要 effect
    LaunchedEffect(failed) {
        if (failed) expanded = true
    }
    val promptText = prompt.toText()
    val summaryText = reply?.toText().orEmpty()
    val containerColor = if (failed) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (failed) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = HugeIcons.LeftToRightListBullet,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (failed) {
                            stringResource(R.string.chat_page_compression_failed)
                        } else {
                            stringResource(R.string.chat_page_compression_title)
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = when {
                            streaming -> stringResource(R.string.chat_page_compressing)
                            failed -> stringResource(R.string.chat_page_compression_failed_hint)
                            else -> summaryText.take(80).ifBlank { promptText.take(80) }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (streaming) {
                    ContainedLoadingIndicator(modifier = Modifier.size(16.dp))
                    IconButton(onClick = onStop, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = HugeIcons.Cancel01,
                            contentDescription = stringResource(R.string.cancel),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                } else {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(
                            stringResource(
                                if (expanded) R.string.chat_page_compression_collapse
                                else R.string.chat_page_compression_expand
                            )
                        )
                    }
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.chat_page_compression_prompt),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = promptText,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor,
                    )
                    if (summaryText.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.chat_page_compression_summary),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            text = summaryText,
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor,
                        )
                    }
                    if (!streaming) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (failed) {
                                TextButton(onClick = onRetry) {
                                    Text(stringResource(R.string.chat_page_compression_retry))
                                }
                            }
                            TextButton(
                                onClick = {
                                    context.writeClipboardText(summaryText.ifBlank { promptText })
                                }
                            ) {
                                Text(stringResource(R.string.copy))
                            }
                            TextButton(onClick = onDelete) {
                                Text(stringResource(R.string.chat_page_compression_delete))
                            }
                        }
                    }
                }
            }
        }
    }
}
