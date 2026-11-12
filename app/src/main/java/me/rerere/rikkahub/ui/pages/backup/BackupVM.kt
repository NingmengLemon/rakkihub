package me.rerere.rikkahub.ui.pages.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.WebDavConfig
import me.rerere.rikkahub.data.sync.BackupProgress
import me.rerere.rikkahub.data.sync.BackupStage
import me.rerere.rikkahub.data.sync.BackupTarget
import me.rerere.rikkahub.data.sync.webdav.WebDavBackupItem
import me.rerere.rikkahub.data.sync.webdav.WebDavSync
import me.rerere.rikkahub.utils.UiState
import java.io.File

/** 备份与恢复的入口。同一时间只允许一个在跑，见 [BackupVM.claim]。 */
enum class BackupOperation {
    EXPORT_SELF,
    EXPORT_OFFICIAL,
    IMPORT_SELF,
    IMPORT_OFFICIAL,
    WEBDAV_BACKUP,
    WEBDAV_RESTORE,
}

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

    /** 本地导出/导入的选择项。聊天记录始终包含，这里只放用户能关掉的附件与工作区。 */
    val localBackupItems = MutableStateFlow(WebDavConfig.BackupItem.LOCAL_DEFAULT_SELECTION)

    private val _exportProgress = MutableStateFlow<BackupProgress?>(null)

    /** 打包进度；为 null 表示当前不在打包（可能在写目标文件）。 */
    val exportProgress: StateFlow<BackupProgress?> = _exportProgress.asStateFlow()

    private val _restoreProgress = MutableStateFlow<BackupProgress?>(null)

    /** 导入进度；为 null 表示当前不在暂存（可能在读源文件）。 */
    val restoreProgress: StateFlow<BackupProgress?> = _restoreProgress.asStateFlow()

    private val _operation = MutableStateFlow<BackupOperation?>(null)

    /**
     * 正在进行的操作，非空时全部入口禁用。
     *
     * 除了真正在跑的备份与恢复，选文件的系统面板打开期间也算占用：那段时间还没有开始写，
     * 但再开第二个只会让两个 picker 互抢结果。
     */
    val operation: StateFlow<BackupOperation?> = _operation.asStateFlow()

    init {
        loadBackupFileItems()
    }

    /** 抢占一次操作。已经有一个在进行时返回 false，调用方不要启动。 */
    fun claim(operation: BackupOperation): Boolean = _operation.compareAndSet(null, operation)

    /** 释放抢占，并清掉进度，避免下一次操作闪一下上一份进度。 */
    fun release(operation: BackupOperation) {
        _operation.compareAndSet(operation, null)
        _exportProgress.value = null
        _restoreProgress.value = null
    }

    fun updateSettings(fn: (Settings) -> Settings) {
        viewModelScope.launch {
            settingsStore.update(fn)
        }
    }

    fun updateLocalBackupItems(items: List<WebDavConfig.BackupItem>) {
        localBackupItems.value = WebDavConfig.BackupItem.normalize(items)
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
     * 打包成临时 zip 文件。打包期间 [exportProgress] 给出阶段与阶段内条目数，
     * 返回后置为 null，调用方据此切换到"写入目标文件"的阶段。
     *
     * [target] 决定产出给谁读：自家备份带全量数据，官方兼容备份要交给官方客户端，所以后者会
     * 去掉官方那个版本不认识的东西（工作区选项就属于这一类）。双方结构分叉之后官方出口会直接失败。
     * 只有自家备份更新备份提醒的时间戳，官方兼容那份是用来交还数据的，不计入提醒。
     */
    suspend fun exportToFile(target: BackupTarget): File {
        _exportProgress.value = BackupProgress(BackupStage.PREPARING)
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
        try {
            webDavSync.restoreFromLocalFile(
                file,
                settings.value.webDavConfig.copy(items = localBackupItems.value),
                onProgress = { _restoreProgress.value = it },
            )
        } finally {
            _restoreProgress.value = null
        }
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
