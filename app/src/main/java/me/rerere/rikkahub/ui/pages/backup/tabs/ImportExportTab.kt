package me.rerere.rikkahub.ui.pages.backup.tabs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
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
import me.rerere.rikkahub.data.sync.BackupTarget
import me.rerere.rikkahub.data.sync.official.OfficialExportUnsupportedException
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.pages.backup.BackupVM
import me.rerere.rikkahub.ui.pages.backup.components.BackupActionIcon
import me.rerere.rikkahub.ui.pages.backup.components.backupContentItem
import me.rerere.ui.toast.ToastType
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 导入来源：本应用自己的备份，或官方 RikkaHub 导出的备份。两者格式相同，走同一条恢复流程。 */
private enum class ImportSource { LOCAL, OFFICIAL }

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
    // picker 打开期间可能转屏或进程被回收，目标要能跨重建保存，否则官方入口会退回自家格式
    var pendingExportTarget by rememberSaveable { mutableStateOf(BackupTarget.SELF) }
    var exportingTarget by remember { mutableStateOf<BackupTarget?>(null) }
    val isExporting = exportingTarget != null
    var isRestoring by remember { mutableStateOf(false) }
    var showImportConfirmDialog by remember { mutableStateOf(false) }
    var importSource by remember { mutableStateOf(ImportSource.LOCAL) }

    // 创建文件保存的launcher
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let { targetUri ->
            scope.launch {
                // 在进入协程时固定下来，之后即使用户再点另一个入口也不会串
                val target = pendingExportTarget
                exportingTarget = target
                try {
                    // 打包成临时文件，期间 exportProgress 给出打包进度
                    val exportFile = vm.exportToFile(target)

                    // 复制到用户选择的位置，这一段按字节走，没有逐项进度；包可能几百 MB，放 IO 上
                    try {
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openOutputStream(targetUri)?.use { outputStream ->
                                FileInputStream(exportFile).use { inputStream ->
                                    inputStream.copyTo(outputStream)
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
                        runCatching { context.contentResolver.delete(targetUri, null, null) }
                    }
                    throw e
                } catch (e: Exception) {
                    e.printStackTrace()
                    // 目标文档在选择位置时就已经建出来了，失败时删掉，别留一个 0 字节的 zip
                    runCatching { context.contentResolver.delete(targetUri, null, null) }
                    val message = if (e is OfficialExportUnsupportedException) {
                        context.getString(R.string.backup_page_export_schema_diverged)
                    } else {
                        context.getString(R.string.backup_page_export_failed)
                    }
                    toaster.show(message, type = ToastType.Error)
                } finally {
                    exportingTarget = null
                }
            }
        }
    }

    // 创建文件选择的launcher
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { sourceUri ->
            scope.launch {
                isRestoring = true
                try {
                    // 本应用备份与官方 RikkaHub 备份是同一个归档格式，恢复流程一致
                    val tempFile =
                        File(context.cacheDir, "temp_restore_${System.currentTimeMillis()}.zip")
                    try {
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                                FileOutputStream(tempFile).use { outputStream ->
                                    inputStream.copyTo(outputStream)
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
                    toaster.show(
                        context.getString(R.string.backup_page_restore_failed, e.message ?: ""),
                        type = ToastType.Error
                    )
                } finally {
                    isRestoring = false
                }
            }
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
            title = { Text(stringResource(R.string.backup_page_local_backup_export)) },
        ) {
            backupContentItem(
                options = WebDavConfig.BackupItem.entries,
                selected = selectedBackupItems,
                onChange = { vm.updateLocalBackupItems(it) },
            ) { item ->
                when (item) {
                    WebDavConfig.BackupItem.DATABASE -> stringResource(R.string.backup_page_chat_records)
                    WebDavConfig.BackupItem.FILES -> stringResource(R.string.backup_page_files)
                    WebDavConfig.BackupItem.WORKSPACES -> stringResource(R.string.backup_page_workspaces)
                }
            }
            item(
                onClick = if (!isExporting) {
                    {
                        pendingExportTarget = BackupTarget.SELF
                        val timestamp = LocalDateTime.now()
                            .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                        createDocumentLauncher.launch("rikkahub_backup_$timestamp.zip")
                    }
                } else null,
                headlineContent = { Text(stringResource(R.string.backup_page_local_backup_export)) },
                supportingContent = {
                    Text(
                        if (exportingTarget == BackupTarget.SELF) {
                            stringResource(R.string.backup_page_exporting)
                        } else {
                            stringResource(R.string.backup_page_export_desc)
                        }
                    )
                },
                leadingContent = {
                    BackupActionIcon(HugeIcons.FileExport, loading = exportingTarget == BackupTarget.SELF)
                },
            )

            // 第二个出口：产出官方客户端读得回去的备份。双方 schema 一致时库的形状不变，但附件
            // 路径会换成官方安装的位置，settings 里官方不认识的条目会被去掉；一旦我们改了结构，
            // 这个出口会直接失败，直到裁剪逻辑落地。
            item(
                onClick = if (!isExporting) {
                    {
                        pendingExportTarget = BackupTarget.OFFICIAL
                        val timestamp = LocalDateTime.now()
                            .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                        createDocumentLauncher.launch("rikkahub_backup_official_$timestamp.zip")
                    }
                } else null,
                headlineContent = { Text(stringResource(R.string.backup_page_export_official)) },
                supportingContent = {
                    Text(
                        if (exportingTarget == BackupTarget.OFFICIAL) {
                            stringResource(R.string.backup_page_exporting)
                        } else {
                            stringResource(R.string.backup_page_export_official_desc)
                        }
                    )
                },
                leadingContent = {
                    BackupActionIcon(HugeIcons.FileExport, loading = exportingTarget == BackupTarget.OFFICIAL)
                },
            )

            // 打包时按条目推进，打包结束后是写入目标文件的阶段，没有可数的进度
            if (isExporting) {
                val progress = exportProgress
                formItem(
                    headlineContent = {
                        Text(
                            if (progress != null && progress.total > 0) {
                                stringResource(
                                    R.string.backup_page_export_packing,
                                    progress.done,
                                    progress.total,
                                )
                            } else {
                                stringResource(R.string.backup_page_saving)
                            }
                        )
                    },
                ) {
                    if (progress != null && progress.total > 0) {
                        LinearProgressIndicator(
                            progress = { progress.fraction },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            item(
                onClick = if (!isRestoring) {
                    {
                        importSource = ImportSource.LOCAL
                        showImportConfirmDialog = true
                    }
                } else null,
                headlineContent = { Text(stringResource(R.string.backup_page_local_backup_import)) },
                supportingContent = {
                    Text(
                        if (isRestoring && importSource == ImportSource.LOCAL) {
                            stringResource(R.string.backup_page_importing)
                        } else {
                            stringResource(R.string.backup_page_import_desc)
                        }
                    )
                },
                leadingContent = {
                    BackupActionIcon(
                        HugeIcons.FileImport,
                        loading = isRestoring && importSource == ImportSource.LOCAL,
                    )
                },
            )
        }

        CardGroup(
            title = { Text(stringResource(R.string.backup_page_import_from_official)) },
        ) {
            item(
                onClick = if (!isRestoring) {
                    {
                        importSource = ImportSource.OFFICIAL
                        showImportConfirmDialog = true
                    }
                } else null,
                headlineContent = { Text(stringResource(R.string.backup_page_import_from_official)) },
                supportingContent = { Text(stringResource(R.string.backup_page_import_official_desc)) },
                leadingContent = {
                    BackupActionIcon(
                        HugeIcons.FileImport,
                        loading = isRestoring && importSource == ImportSource.OFFICIAL,
                    )
                },
            )
        }
    }

    if (showImportConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showImportConfirmDialog = false },
            title = {
                Text(
                    stringResource(
                        when (importSource) {
                            ImportSource.LOCAL -> R.string.backup_page_local_backup_import
                            ImportSource.OFFICIAL -> R.string.backup_page_import_from_official
                        }
                    )
                )
            },
            text = { Text(stringResource(R.string.backup_page_import_overwrite_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImportConfirmDialog = false
                        openDocumentLauncher.launch(arrayOf("application/zip"))
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
