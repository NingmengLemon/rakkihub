package me.rerere.rikkahub.ui.pages.extensions.workspace

import android.graphics.Typeface
import android.view.KeyEvent
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick as semanticsOnClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.theme.ColorMode
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@Composable
fun WorkspaceTerminalPage(id: String) {
    val vm: WorkspaceDetailVM = koinViewModel(parameters = { parametersOf(id) })
    val state by vm.state.collectAsStateWithLifecycle()
    val sessionManager: WorkspaceTerminalSessionManager = koinInject()
    val settingsStore: SettingsStore = koinInject()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val showImeToggle = settings.displaySetting.terminalImeToggleVisible
    val twoRowKeys = settings.displaySetting.terminalTwoRowKeys
    val workspace = state.workspace
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    DisposableEffect(Unit) {
        onDispose {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }
    val root = workspace?.root
    val terminalStateFlow = remember(root, sessionManager) {
        root?.let(sessionManager::observeWorkspace) ?: flowOf(WorkspaceTerminalTabsState())
    }
    val terminalState by terminalStateFlow.collectAsStateWithLifecycle(
        initialValue = WorkspaceTerminalTabsState(),
    )
    var pendingCloseTabId by remember(root) { mutableStateOf<Long?>(null) }

    val shellCompatibilityMode = workspace?.shellCompatibilityMode ?: false
    LaunchedEffect(root, shellCompatibilityMode) {
        workspace?.let(sessionManager::ensureSession)
    }

    RikkahubTheme(colorMode = ColorMode.DARK) {
        Scaffold { innerPadding ->
            WorkspaceTerminalContent(
                root = root,
                state = terminalState,
                contentPadding = innerPadding,
                showImeToggle = showImeToggle,
                twoRowKeys = twoRowKeys,
                onCreateTab = { workspace?.let(sessionManager::createTab) },
                onSelectTab = { tabId ->
                    root?.let { sessionManager.selectTab(it, tabId) }
                },
                onCloseTab = { tabId ->
                    pendingCloseTabId = tabId
                },
            )
        }

        val pendingCloseTab = terminalState.tabs.firstOrNull { it.id == pendingCloseTabId }
        if (pendingCloseTab != null) {
            AlertDialog(
                onDismissRequest = { pendingCloseTabId = null },
                title = {
                    Text(
                        stringResource(
                            R.string.workspace_terminal_close_confirm_title,
                            pendingCloseTab.number,
                        ),
                    )
                },
                text = {
                    Text(stringResource(R.string.workspace_terminal_close_confirm_message))
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            root?.let { sessionManager.closeTab(it, pendingCloseTab.id) }
                            pendingCloseTabId = null
                        },
                    ) {
                        Text(stringResource(R.string.workspace_terminal_close))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingCloseTabId = null }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                },
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun WorkspaceTerminalContent(
    root: String?,
    state: WorkspaceTerminalTabsState,
    contentPadding: PaddingValues,
    showImeToggle: Boolean,
    twoRowKeys: Boolean,
    onCreateTab: () -> Unit,
    onSelectTab: (Long) -> Unit,
    onCloseTab: (Long) -> Unit,
) {
    val selectedIndex = state.tabs.indexOfFirst { it.id == state.selectedTabId }
        .takeIf { it >= 0 }
        ?: 0
    val selectedTab = state.tabs.getOrNull(selectedIndex)

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .consumeWindowInsets(contentPadding)
            // Resize to the final IME height at animation start so the toolbar moves immediately
            // and terminal rows stay stable instead of sending a SIGWINCH for every frame.
            .windowInsetsPadding(WindowInsets.imeAnimationTarget),
        color = Color.Black,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(MaterialTheme.colorScheme.surface),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackButton()
                Box(modifier = Modifier.weight(1f)) {
                    if (selectedTab != null) {
                        SecondaryScrollableTabRow(
                            selectedTabIndex = selectedIndex,
                            modifier = Modifier.fillMaxWidth(),
                            edgePadding = 0.dp,
                            minTabWidth = 160.dp,
                            containerColor = MaterialTheme.colorScheme.surface,
                        ) {
                            state.tabs.forEach { tab ->
                                val tabDescription = stringResource(
                                    R.string.workspace_terminal_tab,
                                    tab.number,
                                )
                                val tabTitle = tab.title ?: tabDescription
                                val isSelected = selectedTab.id == tab.id
                                Tab(
                                    selected = isSelected,
                                    onClick = { onSelectTab(tab.id) },
                                    modifier = Modifier
                                        .height(48.dp)
                                        .widthIn(min = 160.dp, max = 240.dp)
                                        .semantics {
                                            contentDescription = tabTitle
                                        },
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp, end = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            text = tabTitle,
                                            modifier = Modifier
                                                .weight(1f)
                                                .then(
                                                    if (isSelected) Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                                                    else Modifier,
                                                ),
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = if (isSelected) TextOverflow.Clip else TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                        val closeDescription = stringResource(
                                            R.string.workspace_terminal_close_tab,
                                            tab.number,
                                        )
                                        IconButton(
                                            onClick = { onCloseTab(tab.id) },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .semantics {
                                                    contentDescription = closeDescription
                                                },
                                        ) {
                                            Text(text = "×", fontSize = 18.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                val newTabDescription = stringResource(R.string.workspace_terminal_new_tab)
                IconButton(
                    onClick = onCreateTab,
                    enabled = root != null && !state.isCreating,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = newTabDescription },
                ) {
                    Text(text = "+", fontSize = 24.sp)
                }
            }
            if (selectedTab != null) {
                WorkspaceTerminalTabContent(
                    tab = selectedTab,
                    showImeToggle = showImeToggle,
                    twoRowKeys = twoRowKeys,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = when {
                            root == null || state.isCreating || state.readiness == WorkspaceTerminalReadiness.Loading -> {
                                stringResource(R.string.workspace_terminal_loading)
                            }
                            state.readiness == WorkspaceTerminalReadiness.NotInstalled -> {
                                stringResource(R.string.workspace_terminal_not_installed)
                            }
                            else -> stringResource(R.string.workspace_terminal_no_tabs)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkspaceTerminalTabContent(
    tab: WorkspaceTerminalTab,
    showImeToggle: Boolean,
    twoRowKeys: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val terminalTextSizePx = with(LocalDensity.current) { 12.sp.roundToPx() }
    val terminalTypeface = remember(context) {
        ResourcesCompat.getFont(context, R.font.jetbrains_mono) ?: Typeface.MONOSPACE
    }
    var controlDown by remember(tab.id) { mutableStateOf(false) }
    var altDown by remember(tab.id) { mutableStateOf(false) }
    val effectiveIme = tab.imeEnabled.value && showImeToggle
    val viewClient = remember(tab.id) {
        WorkspaceTerminalViewClient(context)
    }
    viewClient.controlDown = controlDown
    viewClient.altDown = altDown

    LaunchedEffect(showImeToggle) {
        if (!showImeToggle) tab.imeEnabled.value = false
    }

    DisposableEffect(tab.id, viewClient) {
        onDispose {
            if (tab.client.terminalView === viewClient.terminalView) {
                tab.client.terminalView = null
            }
            viewClient.terminalView = null
            viewClient.imeInputView = null
        }
    }

    LaunchedEffect(effectiveIme) {
        if (!effectiveIme) viewClient.imeInputView = null
        delay(50)
        viewClient.focusAndShowKeyboard()
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    TerminalView(viewContext, null).apply {
                        isFocusable = true
                        isFocusableInTouchMode = true
                        setTextSize(terminalTextSizePx)
                        setTypeface(terminalTypeface)
                        setTerminalViewClient(viewClient)
                        attachSession(tab.session)
                        tab.client.terminalView = this
                        viewClient.terminalView = this
                        post {
                            viewClient.focusAndShowKeyboard()
                        }
                    }
                },
                update = { terminalView ->
                    terminalView.isFocusable = true
                    terminalView.isFocusableInTouchMode = true
                    terminalView.setTextSize(terminalTextSizePx)
                    terminalView.setTypeface(terminalTypeface)
                    terminalView.setTerminalViewClient(viewClient)
                    tab.client.terminalView = terminalView
                    viewClient.terminalView = terminalView
                    terminalView.attachSession(tab.session)
                    terminalView.onScreenUpdated()
                },
            )
            if (effectiveIme) {
                AndroidView(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .size(1.dp),
                    factory = { viewContext ->
                        TerminalImeEditText(viewContext).apply {
                            alpha = 0f
                            onCommitText = { text ->
                                tab.session.writeText(
                                    terminalInputText(
                                        text = text,
                                        control = viewClient.controlDown,
                                        alt = viewClient.altDown,
                                    )
                                )
                            }
                            onDelete = {
                                tab.session.write(byteArrayOf(0x7f), 0, 1)
                            }
                            onKeyEvent = { keyCode, event ->
                                val terminal = viewClient.terminalView
                                when {
                                    terminal == null -> false
                                    viewClient.onKeyDown(keyCode, event, tab.session) -> true
                                    event.action == KeyEvent.ACTION_UP -> terminal.onKeyUp(keyCode, event)
                                    else -> terminal.onKeyDown(keyCode, event)
                                }
                            }
                            viewClient.imeInputView = this
                            post {
                                viewClient.focusAndShowKeyboard()
                            }
                        }
                    },
                    update = { inputView ->
                        viewClient.imeInputView = inputView
                    },
                )
            }
            if (tab.finished) {
                Text(
                    text = stringResource(R.string.workspace_terminal_exited),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )
            }
        }
        TerminalExtraKeysBar(
            controlDown = controlDown,
            altDown = altDown,
            imeEnabled = effectiveIme,
            showImeToggle = showImeToggle,
            twoRowKeys = twoRowKeys,
            onControlToggle = { controlDown = !controlDown },
            onAltToggle = { altDown = !altDown },
            onImeToggle = { tab.imeEnabled.value = !tab.imeEnabled.value },
            onSendText = { tab.session.writeText(it) },
        )
    }
}

private const val KEY_REPEAT_DELAY_MILLIS = 350L
private const val KEY_REPEAT_INTERVAL_MILLIS = 70L

private data class TerminalExtraKeySpec(
    val label: String,
    val selected: Boolean = false,
    val repeatable: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
private fun TerminalExtraKeysBar(
    controlDown: Boolean,
    altDown: Boolean,
    imeEnabled: Boolean,
    showImeToggle: Boolean,
    twoRowKeys: Boolean,
    onControlToggle: () -> Unit,
    onAltToggle: () -> Unit,
    onImeToggle: () -> Unit,
    onSendText: (String) -> Unit,
) {
    val primaryKeys = buildList {
        if (showImeToggle) add(TerminalExtraKeySpec("IME", selected = imeEnabled, onClick = onImeToggle))
        add(TerminalExtraKeySpec("ESC") { onSendText("\u001B") })
        add(TerminalExtraKeySpec("TAB") { onSendText("\t") })
        add(TerminalExtraKeySpec("CTRL", selected = controlDown, onClick = onControlToggle))
        add(TerminalExtraKeySpec("ALT", selected = altDown, onClick = onAltToggle))
        add(TerminalExtraKeySpec("-") { onSendText("-") })
        add(TerminalExtraKeySpec("/") { onSendText("/") })
        add(TerminalExtraKeySpec("|") { onSendText("|") })
    }
    val navigationKeys = listOf(
        TerminalExtraKeySpec("←", repeatable = true) { onSendText("\u001B[D") },
        TerminalExtraKeySpec("↓", repeatable = true) { onSendText("\u001B[B") },
        TerminalExtraKeySpec("↑", repeatable = true) { onSendText("\u001B[A") },
        TerminalExtraKeySpec("→", repeatable = true) { onSendText("\u001B[C") },
        TerminalExtraKeySpec("HOME", repeatable = true) { onSendText("\u001B[H") },
        TerminalExtraKeySpec("END", repeatable = true) { onSendText("\u001B[F") },
        TerminalExtraKeySpec("PGUP", repeatable = true) { onSendText("\u001B[5~") },
        TerminalExtraKeySpec("PGDN", repeatable = true) { onSendText("\u001B[6~") },
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        if (twoRowKeys) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TerminalExtraKeyRow(primaryKeys, slotCount = 8)
                TerminalExtraKeyRow(navigationKeys, slotCount = 8)
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                (primaryKeys + navigationKeys).forEach { key ->
                    TerminalExtraKey(
                        label = key.label,
                        selected = key.selected,
                        repeatable = key.repeatable,
                        onClick = key.onClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun TerminalExtraKeyRow(keys: List<TerminalExtraKeySpec>, slotCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        keys.forEach { key ->
            TerminalExtraKey(
                label = key.label,
                selected = key.selected,
                modifier = Modifier.weight(1f),
                compact = true,
                repeatable = key.repeatable,
                onClick = key.onClick,
            )
        }
        // IME 开关隐藏时保留一个空槽，两行仍然按同样的列宽对齐
        repeat(slotCount - keys.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun TerminalExtraKey(
    label: String,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    repeatable: Boolean = false,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()
    val currentOnClick by rememberUpdatedState(onClick)
    val keyModifier = modifier
        .background(
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            },
            shape = RoundedCornerShape(6.dp),
        )
        .then(
            if (repeatable) {
                Modifier
                    .indication(interactionSource, LocalIndication.current)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val press = PressInteraction.Press(down.position)
                            interactionSource.tryEmit(press)
                            var repeated = false
                            val repeatJob = scope.launch {
                                delay(KEY_REPEAT_DELAY_MILLIS)
                                repeated = true
                                while (isActive) {
                                    currentOnClick()
                                    delay(KEY_REPEAT_INTERVAL_MILLIS)
                                }
                            }
                            try {
                                val up = waitForUpOrCancellation()
                                // 同步取消连发循环，松手那一帧不再补一拍
                                repeatJob.cancel()
                                if (up != null) {
                                    interactionSource.tryEmit(PressInteraction.Release(press))
                                    if (!repeated) currentOnClick()
                                } else {
                                    interactionSource.tryEmit(PressInteraction.Cancel(press))
                                }
                            } finally {
                                repeatJob.cancel()
                            }
                        }
                    }
                    .semantics {
                        role = Role.Button
                        semanticsOnClick(label = null) {
                            currentOnClick()
                            true
                        }
                    }
            } else {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = currentOnClick,
                )
            },
        )
        .padding(
            horizontal = if (compact) 0.dp else 12.dp,
            vertical = 8.dp,
        )

    Text(
        text = label,
        modifier = keyModifier,
        style = if (compact) {
            MaterialTheme.typography.labelSmall
        } else {
            MaterialTheme.typography.labelMedium
        },
        color = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
    )
}

private fun TerminalSession.writeText(text: String) {
    val bytes = text.toByteArray()
    write(bytes, 0, bytes.size)
}

internal fun terminalInputText(text: String, control: Boolean, alt: Boolean): String {
    if (!control && !alt) return text
    return buildString {
        if (alt) append('\u001B')
        text.forEach { char ->
            val controlCode = if (control) char.controlCode() else null
            if (controlCode != null) {
                append(controlCode.toChar())
            } else {
                append(char)
            }
        }
    }
}

private fun Char.controlCode(): Int? = when {
    this in 'a'..'z' -> uppercaseChar().code and 0x1f
    this in 'A'..'Z' -> code and 0x1f
    this == '@' -> 0
    this == '[' -> 27
    this == '\\' -> 28
    this == ']' -> 29
    this == '^' -> 30
    this == '_' -> 31
    this == '/' -> 31
    this == '?' -> 127
    else -> null
}
