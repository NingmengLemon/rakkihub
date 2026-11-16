package me.rerere.rikkahub.ui.pages.extensions.workspace

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import me.rerere.ui.toast.ToastType
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.richtext.MaterialMarkdown
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.JetbrainsMono
import me.rerere.ui.webview.WebView
import me.rerere.ui.webview.rememberWebViewState
import me.rerere.workspace.WorkspaceStorageArea
import org.koin.compose.koinInject

/**
 * 工作区文本文件编辑/预览页.
 *
 * FILES 区文件可编辑并保存; LINUX (rootfs) 区文件仅只读预览 (readOnly), 避免误改系统文件.
 */
@Composable
fun WorkspaceFileEditorPage(
    id: String,
    area: WorkspaceStorageArea,
    path: String,
) {
    val repository = koinInject<WorkspaceRepository>()
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val editable = area == WorkspaceStorageArea.FILES
    val fileName = path.substringAfterLast('/').ifBlank { path }
    val extension = fileName.substringAfterLast('.', "").lowercase()
    val supportsMarkdownPreview = extension in setOf("md", "markdown")
    val supportsPreview = supportsMarkdownPreview || extension in setOf("html", "htm", "svg")
    var showPreview by rememberSaveable(id, area, path) { mutableStateOf(supportsPreview) }

    // 不能用 rememberTextFieldState: 它会把全文存进 saved state Bundle, 大文件切后台时触发 TransactionTooLargeException (#1953).
    // 内容本就由下方 LaunchedEffect 从磁盘加载, 无需 saveable.
    val textState = remember(id, area, path) { TextFieldState() }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var loadedText by remember { mutableStateOf("") }
    var showUnsavedDialog by remember { mutableStateOf(false) }
    val navController = LocalNavController.current
    val currentText = textState.text.toString()
    val dirty = editable && !loading && loadError == null && currentText != loadedText
    val scrollState = rememberScrollState()
    var savedScroll by rememberSaveable(id, area, path) { mutableIntStateOf(0) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 切到 Recents 时主动收起焦点和输入法；回来时不再自动聚焦，也不会把页面拉回光标处
    DisposableEffect(lifecycleOwner, focusManager) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    savedScroll = scrollState.value
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                }
                Lifecycle.Event.ON_RESUME -> scope.launch { scrollState.scrollTo(savedScroll) }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(loading) {
        if (!loading && loadError == null) {
            scrollState.scrollTo(savedScroll)
        }
    }

    LaunchedEffect(id, area, path) {
        loading = true
        loadError = null
        runCatching {
            repository.readTextForPreview(id, area, path)
        }.onSuccess { content ->
            loadedText = content
            textState.setTextAndPlaceCursorAtEnd(content)
            loading = false
        }.onFailure {
            loadError = it.message ?: "读取文件失败"
            loading = false
        }
    }

    fun save(onSaved: (() -> Unit)? = null) {
        if (saving) return
        saving = true
        val text = textState.text.toString()
        scope.launch {
            try {
                runCatching {
                    repository.writeText(
                        id = id,
                        path = path,
                        text = text,
                        overwrite = true,
                    )
                }.onSuccess {
                    loadedText = text
                    toaster.show("已保存", type = ToastType.Success)
                    onSaved?.invoke()
                }.onFailure {
                    toaster.show(it.message ?: "保存失败", type = ToastType.Error)
                }
            } finally {
                saving = false
            }
        }
    }

    fun requestExit() {
        if (dirty) {
            showUnsavedDialog = true
        } else {
            navController.popBackStack()
        }
    }

    BackHandler(enabled = dirty) {
        showUnsavedDialog = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = fileName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { BackButton(onClick = ::requestExit) },
                actions = {
                    if (supportsPreview && !loading && loadError == null) {
                        TextButton(onClick = { showPreview = !showPreview }) {
                            Text(if (showPreview) "源码" else "预览")
                        }
                    }
                    if (editable && !loading && loadError == null) {
                        TextButton(
                            onClick = { save() },
                            enabled = !saving,
                        ) {
                            Text("Save")
                        }
                    }
                },
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        when {
            loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            loadError != null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
            ) {
                Text(
                    text = loadError ?: "",
                    color = MaterialTheme.colorScheme.error,
                )
            }

            showPreview -> when {
                supportsMarkdownPreview && currentText.length > MAX_MARKDOWN_PREVIEW_CHARS -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.workspace_markdown_too_large),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                supportsMarkdownPreview -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    item {
                        MaterialMarkdown(
                            content = currentText,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }

                else -> WorkspaceWebPreview(
                    content = currentText,
                    isSvg = extension == "svg",
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                )
            }

            else -> TextField(
                state = textState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding(),
                readOnly = !editable,
                lineLimits = TextFieldLineLimits.MultiLine(),
                scrollState = scrollState,
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = JetbrainsMono,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                ),
            )
        }
    }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text(stringResource(R.string.workspace_file_unsaved_title)) },
            text = { Text(stringResource(R.string.workspace_file_unsaved_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        save {
                            showUnsavedDialog = false
                            navController.popBackStack()
                        }
                    },
                    enabled = !saving,
                ) {
                    Text(stringResource(R.string.common_save))
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showUnsavedDialog = false }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                    TextButton(
                        onClick = {
                            showUnsavedDialog = false
                            navController.popBackStack()
                        },
                        enabled = !saving,
                    ) {
                        Text(stringResource(R.string.workspace_file_unsaved_discard))
                    }
                }
            },
        )
    }
}

@Composable
private fun WorkspaceWebPreview(
    content: String,
    isSvg: Boolean,
    modifier: Modifier = Modifier,
) {
    // SVG 预览按静态图片看待：工作区文件是模型可写的，不能当可执行文档加载
    if (isSvg && content.length > MAX_SVG_PREVIEW_CHARS) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.workspace_preview_too_large),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
        return
    }

    val state = rememberWebViewState(
        data = content,
        baseUrl = "https://workspace-preview.invalid/",
        mimeType = if (isSvg) "image/svg+xml" else "text/html",
        settings = {
            allowFileAccess = false
            allowContentAccess = false
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        },
    ).also { webViewState ->
        // 必须在组合期间就定死：AndroidView 的 update 会同步读这个字段并立即加载内容，
        // 放到 SideEffect 里就晚了一步，首帧仍可能执行 SVG 里的脚本
        if (isSvg) webViewState.javaScriptEnabled = false
    }
    WebView(state = state, modifier = modifier)
}

/** SVG 预览上限，超过后只给提示，不把整份文件交给 WebView。 */
private const val MAX_SVG_PREVIEW_CHARS = 1_000_000

/** Markdown 预览上限，和 workspace 文件大小上限对齐；超过后保留源码入口。 */
private const val MAX_MARKDOWN_PREVIEW_CHARS = 512_000
