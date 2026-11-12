package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.ContextPressure
import me.rerere.rikkahub.utils.formatNumber
import kotlin.math.roundToInt

/** 上下文接近上限时的轻量建议条；只提示和打开压缩对话框，不自动压缩。 */
@Composable
internal fun ContextPressureBanner(
    pressure: ContextPressure,
    onCompress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val percent = (pressure.ratio * 100).roundToInt().coerceIn(0, 100)
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.chat_context_pressure_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(
                        R.string.chat_context_pressure_message,
                        percent,
                        pressure.usedTokens.formatNumber(),
                        pressure.limitTokens.formatNumber(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onCompress) {
                Text(stringResource(R.string.chat_page_manage_context_tab_compress))
            }
        }
    }
}
