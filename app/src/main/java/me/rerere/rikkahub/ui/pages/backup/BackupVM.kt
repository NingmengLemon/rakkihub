package me.rerere.rikkahub.ui.pages.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.WebDavConfig
import me.rerere.rikkahub.data.sync.BackupProgress
import me.rerere.rikkahub.data.sync.BackupTarget
import me.rerere.rikkahub.data.sync.webdav.WebDavBackupItem
import me.rerere.rikkahub.data.sync.webdav.WebDavSync
import me.rerere.rikkahub.utils.UiState
import java.io.File

class BackupVM(
    private val settingsStore: SettingsStore,
    private val webDavSync: WebDavSync,
) : ViewModel() {
    val settings = settingsStore.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = Settings.dummy()
    )

    val webDavBackupItems = MutableStateFlow<UiState<List<WebDavBackupItem>>>(UiState.Idle)

    /** 本地导出/导入的选择项。默认与设置里的持久化默认一致：不含工作区。 */
    val localBackupItems = MutableStateFlow(WebDavConfig.BackupItem.DEFAULT_SELECTION)

    private val _exportProgress = MutableStateFlow<BackupProgress?>(null)

    /** 打包进度；为 null 表示当前没有在打包。 */
    val exportProgress = _exportProgress.asStateFlow()

    init {
        loadBackupFileItems()
    }

    fun updateSettings(fn: (Settings) -> Settings) {
        viewModelScope.launch {
            settingsStore.update(fn)
        }
    }

    fun updateLocalBackupItems(items: List<WebDavConfig.BackupItem>) {
        localBackupItems.value = items
    }

    fun loadBackupFileItems() {
        viewModelScope.launch {
            runCatching {
                webDavBackupItems.emit(UiState.Loading)
                webDavBackupItems.emit(
                    value = UiState.Success(
                        data = webDavSync.listBackupFiles(
                            config = settings.value.webDavConfig
                        ).sortedByDescending { it.lastModified }
                    )
                )
            }.onFailure {
                webDavBackupItems.emit(UiState.Error(it))
            }
        }
    }

    suspend fun testWebDav() {
        webDavSync.testConnection(settings.value.webDavConfig)
    }

    suspend fun backup() {
        webDavSync.backup(settings.value.webDavConfig)
        recordBackupTime()
    }

    suspend fun restore(item: WebDavBackupItem) {
        webDavSync.restore(config = settings.value.webDavConfig, item = item)
    }

    suspend fun deleteWebDavBackupFile(item: WebDavBackupItem) {
        webDavSync.deleteBackupFile(settings.value.webDavConfig, item)
    }

    /**
     * 打包成临时 zip 文件。打包期间 [exportProgress] 给出已写入的条目数，
     * 返回后置为 null，调用方据此切换到"写入目标文件"的阶段。
     *
     * [target] 决定产出给谁读：自家备份带全量数据，官方兼容备份要交给官方客户端。
     * 现在双方 schema 一致，两份产物相同；分叉后官方出口会直接失败。
     * 只有自家备份更新备份提醒的时间戳，官方兼容那份是用来交还数据的，不计入提醒。
     */
    suspend fun exportToFile(target: BackupTarget): File {
        _exportProgress.value = BackupProgress(done = 0, total = 0)
        try {
            val file = webDavSync.prepareBackupFile(
                config = settings.value.webDavConfig.copy(items = localBackupItems.value),
                target = target,
                onProgress = { _exportProgress.value = it },
            )
            if (target == BackupTarget.SELF) recordBackupTime()
            return file
        } finally {
            _exportProgress.value = null
        }
    }

    suspend fun restoreFromLocalFile(file: File) {
        webDavSync.restoreFromLocalFile(
            file,
            settings.value.webDavConfig.copy(items = localBackupItems.value),
        )
    }

    private suspend fun recordBackupTime() {
        settingsStore.update { settings ->
            settings.copy(
                backupReminderConfig = settings.backupReminderConfig.copy(
                    lastBackupTime = System.currentTimeMillis()
                )
            )
        }
    }
}
