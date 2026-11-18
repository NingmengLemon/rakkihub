package me.rerere.rikkahub.ui.components.message

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.rikkahub.R

@Composable
internal fun InlineSvgBlock(
    svg: String,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(svg) { mutableStateOf(false) }
    var failed by remember(svg) { mutableStateOf(false) }
    val safe = remember(svg) { isSafeInlineSvg(svg) }
    if (!safe || failed) {
        // 不安全的 SVG 或解码失败时才回退到整条消息的 WebView 预览
        TextButton(onClick = onPreview, modifier = modifier) {
            Text(stringResource(R.string.render_with_webview))
        }
        return
    }

    val context = LocalContext.current
    val request = remember(svg) {
        ImageRequest.Builder(context)
            .data(svg.toByteArray())
            .crossfade(false)
            .build()
    }
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .padding(vertical = 4.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable { expanded = !expanded }
            .semantics { role = Role.Button }
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp, max = if (expanded) 480.dp else 240.dp),
            onError = { failed = true },
        )
        Icon(
            imageVector = if (expanded) HugeIcons.ArrowUp01 else HugeIcons.ArrowDown01,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
