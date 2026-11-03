package me.rerere.rikkahub.ui.pages.backup.tabs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.FileExport
import me.rerere.hugeicons.stroke.FileImport
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.WebDavConfig
import me.rerere.rikkahub.data.sync.BackupProgress
import me.rerere.rikkahub.data.sync.BackupStage
import me.rerere.rikkahub.data.sync.BackupTarget
import me.rerere.rikkahub.data.sync.official.OfficialExportUnsupportedException
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupScope
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.pages.backup.BackupOperation
import me.rerere.rikkahub.ui.pages.backup.BackupVM
import me.rerere.rikkahub.ui.pages.backup.components.BackupActionIcon
import me.rerere.rikkahub.ui.pages.backup.components.backupContentItem
import me.rerere.rikkahub.ui.pages.backup.components.backupProgressItem
import me.rerere.rikkahub.ui.pages.backup.components.restoreErrorMessage
import me.rerere.ui.toast.ToastType
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 导出到文件的目标换成本应用或官方 RikkaHub 两个入口。 */
private fun exportOperation(target: BackupTarget): BackupOperation =
    if (target == BackupTarget.SELF) BackupOperation.EXPORT_SELF else BackupOperation.EXPORT_OFFICIAL

@Composable
fun ImportExportTab(
    vm: BackupVM,
    onShowRestartDialog: () -> Unit
) {
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val selectedBackupItems by vm.localBackupItems.collectAsStateWithLifecycle()
    val exportProgress by vm.exportProgress.collectAsStateWithLifecycle()
    val restoreProgress by vm.restoreProgress.collectAsStateWithLifecycle()
    val operation by vm.operation.collectAsStateWithLifecycle()
    // picker 打开期间可能转屏或进程被回收，目标要能跨重建保存，否则官方入口会退回自家格式
    var pendingExportTarget by rememberSaveable { mutableStateOf(BackupTarget.SELF) }
    var pendingImportOperation by rememberSaveable { mutableStateOf(BackupOperation.IMPORT_SELF) }
    var showImportConfirmDialog by remember { mutableStateOf(false) }

    // 创建文件保存的launcher
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        // 抢占之后其他入口都点不动了，这里读到的目标不会串
        val target = pendingExportTarget
        val kind = exportOperation(target)
        if (uri == null) {
            vm.release(kind)
            return@rememberLauncherForActivityResult
        }
        // 进程在 picker 打开期间被回收时，重建的 VM 里没有这次操作的占用，这里补上
        if (vm.operation.value == null) vm.claim(kind)
        scope.launch {
            try {
                // 打包成临时文件，期间 exportProgress 给出打包进度
                val exportFile = vm.exportToFile(target)

                // 复制到用户选择的位置，这一段按字节走，没有逐项进度；包可能几百 MB，放 IO 上
                try {
                    withContext(Dispatchers.IO) {
                        val outputStream = context.contentResolver.openOutputStream(uri)
                            ?: error("Cannot open the target document")
                        outputStream.use { stream ->
                            FileInputStream(exportFile).use { inputStream ->
                                inputStream.copyTo(stream)
                            }
                        }
                    }
                } finally {
                    exportFile.delete()
                }

                toaster.show(
                    context.getString(R.string.backup_page_backup_success),
                    type = ToastType.Success
                )
            } catch (e: CancellationException) {
                // 复制没走完，目标文档留着也是半个包；静默删掉，不弹失败
                withContext(NonCancellable) {
                    runCatching { context.contentResolver.delete(uri, null, null) }
                }
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                // 目标文档在选择位置时就已经建出来了，失败时删掉，别留一个 0 字节的 zip
                runCatching { context.contentResolver.delete(uri, null, null) }
                val message = if (e is OfficialExportUnsupportedException) {
                    context.getString(R.string.backup_page_export_schema_diverged)
                } else {
                    context.getString(R.string.backup_page_export_failed)
                }
                toaster.show(message, type = ToastType.Error)
            } finally {
                vm.release(kind)
            }
        }
    }

    // 创建文件选择的launcher
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val kind = pendingImportOperation
        if (uri == null) {
            vm.release(kind)
            return@rememberLauncherForActivityResult
        }
        // 进程在 picker 打开期间被回收时，重建的 VM 里没有这次操作的占用，这里补上
        if (vm.operation.value == null) vm.claim(kind)
        scope.launch {
            try {
                // 本应用备份与官方 RikkaHub 备份是同一个归档格式，恢复流程一致
                val tempFile = File(context.cacheDir, "temp_restore_${System.currentTimeMillis()}.zip")
                try {
                    withContext(Dispatchers.IO) {
                        val inputStream = context.contentResolver.openInputStream(uri)
                            ?: error("Cannot read the selected backup")
                        inputStream.use { stream ->
                            FileOutputStream(tempFile).use { outputStream ->
                                stream.copyTo(outputStream)
                            }
                        }
                        vm.restoreFromLocalFile(tempFile)
                    }
                } finally {
                    tempFile.delete()
                }

                toaster.show(
                    context.getString(R.string.backup_page_restore_success),
                    type = ToastType.Success
                )
                onShowRestartDialog()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                toaster.show(restoreErrorMessage(context, e), type = ToastType.Error)
            } finally {
                vm.release(kind)
            }
        }
    }

    fun startExport(target: BackupTarget) {
        if (!vm.claim(exportOperation(target))) return
        pendingExportTarget = target
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val name = if (target == BackupTarget.SELF) {
            "rikkahub_backup_$timestamp.zip"
        } else {
            "rikkahub_backup_official_$timestamp.zip"
        }
        createDocumentLauncher.launch(name)
    }

    fun startImport(operation: BackupOperation) {
        pendingImportOperation = operation
        showImportConfirmDialog = true
    }

    // 进度行放在触发操作所在的分组里：两组用同一套取文案与进度的逻辑
    fun CardGroupScope.runningOperationProgress() {
        when (operation) {
            BackupOperation.EXPORT_SELF, BackupOperation.EXPORT_OFFICIAL ->
                backupProgressItem(exportProgress) { exportProgressText(exportProgress) }

            BackupOperation.IMPORT_SELF, BackupOperation.IMPORT_OFFICIAL ->
                backupProgressItem(restoreProgress) { importProgressText(restoreProgress) }

            else -> Unit
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CardGroup(
            title = { Text(stringResource(R.string.backup_page_backup_items)) },
        ) {
            backupContentItem(
                options = WebDavConfig.BackupItem.SELECTABLE,
                selected = selectedBackupItems,
                onChange = { vm.updateLocalBackupItems(it) },
            ) { item ->
                when (item) {
                    WebDavConfig.BackupItem.DATABASE -> stringResource(R.string.backup_page_chat_records)
                    WebDavConfig.BackupItem.FILES -> stringResource(R.string.backup_page_files)
                    WebDavConfig.BackupItem.WORKSPACES -> stringResource(R.string.backup_page_workspaces)
                }
            }
        }

        CardGroup(
            title = { Text(stringResource(R.string.backup_page_group_app)) },
        ) {
            item(
                onClick = if (operation == null) {
                    { startExport(BackupTarget.SELF) }
                } else null,
                headlineContent = { Text(stringResource(R.string.backup_page_local_backup_export)) },
                supportingContent = {
                    Text(
                        if (operation == BackupOperation.EXPORT_SELF) {
                            stringResource(R.string.backup_page_exporting)
                        } else {
                            stringResource(R.string.backup_page_export_desc)
                        }
                    )
                },
                leadingContent = {
                    BackupActionIcon(HugeIcons.FileExport, loading = operation == BackupOperation.EXPORT_SELF)
                },
            )

            item(
                onClick = if (operation == null) {
                    { startImport(BackupOperation.IMPORT_SELF) }
                } else null,
                headlineContent = { Text(stringResource(R.string.backup_page_local_backup_import)) },
                supportingContent = {
                    Text(
                        if (operation == BackupOperation.IMPORT_SELF) {
                            stringResource(R.string.backup_page_importing)
                        } else {
                            stringResource(R.string.backup_page_import_desc)
                        }
                    )
                },
                leadingContent = {
                    BackupActionIcon(
                        HugeIcons.FileImport,
                        loading = operation == BackupOperation.IMPORT_SELF,
                    )
                },
            )

            if (operation == BackupOperation.EXPORT_SELF || operation == BackupOperation.IMPORT_SELF) {
                runningOperationProgress()
            }
        }

        CardGroup(
            title = { Text(stringResource(R.string.backup_page_group_official)) },
        ) {
            // 产出官方客户端读得回去的备份。双方 schema 一致时库的形状不变，但附件路径
            // 会换成官方安装的位置，settings 里官方不认识的条目会被去掉；一旦我们改了
            // 结构，这个出口会直接失败，直到裁剪逻辑落地。
            item(
                onClick = if (operation == null) {
                    { startExport(BackupTarget.OFFICIAL) }
                } else null,
                headlineContent = { Text(stringResource(R.string.backup_page_export_official)) },
                supportingContent = {
                    Text(
                        if (operation == BackupOperation.EXPORT_OFFICIAL) {
                            stringResource(R.string.backup_page_exporting)
                        } else {
                            stringResource(R.string.backup_page_export_official_desc)
                        }
                    )
                },
                leadingContent = {
                    BackupActionIcon(
                        HugeIcons.FileExport,
                        loading = operation == BackupOperation.EXPORT_OFFICIAL,
                    )
                },
            )

            item(
                onClick = if (operation == null) {
                    { startImport(BackupOperation.IMPORT_OFFICIAL) }
                } else null,
                headlineContent = { Text(stringResource(R.string.backup_page_import_from_official)) },
                supportingContent = {
                    Text(
                        if (operation == BackupOperation.IMPORT_OFFICIAL) {
                            stringResource(R.string.backup_page_importing)
                        } else {
                            stringResource(R.string.backup_page_import_official_desc)
                        }
                    )
                },
                leadingContent = {
                    BackupActionIcon(
                        HugeIcons.FileImport,
                        loading = operation == BackupOperation.IMPORT_OFFICIAL,
                    )
                },
            )

            if (operation == BackupOperation.EXPORT_OFFICIAL || operation == BackupOperation.IMPORT_OFFICIAL) {
                runningOperationProgress()
            }
        }
    }

    if (showImportConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showImportConfirmDialog = false },
            title = {
                Text(
                    stringResource(
                        if (pendingImportOperation == BackupOperation.IMPORT_OFFICIAL) {
                            R.string.backup_page_import_from_official
                        } else {
                            R.string.backup_page_local_backup_import
                        }
                    )
                )
            },
            text = { Text(stringResource(R.string.backup_page_import_overwrite_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImportConfirmDialog = false
                        val kind = pendingImportOperation
                        if (vm.claim(kind)) {
                            openDocumentLauncher.launch(arrayOf("application/zip"))
                        }
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun exportProgressText(progress: BackupProgress?): String = when {
    progress == null -> stringResource(R.string.backup_page_saving)
    progress.stage == BackupStage.PREPARING -> stringResource(R.string.backup_page_export_preparing)
    progress.stage == BackupStage.DATABASE -> stringResource(R.string.backup_page_export_database)
    progress.stage == BackupStage.FILES ->
        stringResource(R.string.backup_page_export_files, progress.done, progress.total)

    progress.stage == BackupStage.WORKSPACES ->
        stringResource(R.string.backup_page_export_workspaces, progress.done, progress.total)

    else -> stringResource(R.string.backup_page_exporting)
}

@Composable
private fun importProgressText(progress: BackupProgress?): String = when {
    progress == null -> stringResource(R.string.backup_page_import_reading)
    progress.stage == BackupStage.EXTRACTING && progress.total > 0 ->
        stringResource(R.string.backup_page_import_extracting, progress.done, progress.total)

    progress.stage == BackupStage.DATABASE -> stringResource(R.string.backup_page_import_database)
    else -> stringResource(R.string.backup_page_import_reading)
}
