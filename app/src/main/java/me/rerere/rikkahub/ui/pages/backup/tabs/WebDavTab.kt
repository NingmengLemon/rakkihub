package me.rerere.rikkahub.ui.pages.backup.tabs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.View
import me.rerere.hugeicons.stroke.ViewOff
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.WebDavConfig
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.pages.backup.BackupOperation
import me.rerere.rikkahub.ui.pages.backup.BackupVM
import me.rerere.rikkahub.ui.pages.backup.components.BackupFileInfo
import me.rerere.rikkahub.ui.pages.backup.components.BackupFilesSheet
import me.rerere.rikkahub.ui.pages.backup.components.RemoteBackupLayout
import me.rerere.rikkahub.ui.pages.backup.components.RemoteBackupStatus
import me.rerere.rikkahub.ui.pages.backup.components.backupContentItem
import me.rerere.rikkahub.ui.pages.backup.components.restoreErrorMessage
import me.rerere.ui.toast.ToastType

@Composable
fun WebDavTab(
    vm: BackupVM,
    onShowRestartDialog: () -> Unit
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val webDavConfig = settings.webDavConfig
    val backupItemsState by vm.webDavBackupItems.collectAsStateWithLifecycle()
    val operation by vm.operation.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showBackupFiles by remember { mutableStateOf(false) }
    var restoringItemId by remember { mutableStateOf<String?>(null) }
    val isBackingUp = operation == BackupOperation.WEBDAV_BACKUP
    val busy = operation != null

    fun updateWebDavConfig(newConfig: WebDavConfig) {
        vm.updateSettings { it.copy(webDavConfig = newConfig) }
    }

    RemoteBackupLayout(
        isBackingUp = isBackingUp,
        enabled = !busy,
        onTestConnection = {
            scope.launch {
                try {
                    vm.testWebDav()
                    toaster.show(
                        context.getString(R.string.backup_page_connection_success),
                        type = ToastType.Success
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    toaster.show(
                        context.getString(
                            R.string.backup_page_connection_failed,
                            e.message ?: ""
                        ),
                        type = ToastType.Error
                    )
                }
            }
        },
        onBackup = {
            if (vm.claim(BackupOperation.WEBDAV_BACKUP)) {
                scope.launch {
                    try {
                        vm.backup()
                        vm.loadBackupFileItems()
                        toaster.show(
                            context.getString(R.string.backup_page_backup_success),
                            type = ToastType.Success
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        e.printStackTrace()
                        toaster.show(
                            e.message ?: context.getString(R.string.backup_page_unknown_error),
                            type = ToastType.Error
                        )
                    } finally {
                        vm.release(BackupOperation.WEBDAV_BACKUP)
                    }
                }
            }
        },
    ) {
        RemoteBackupStatus(
            lastBackupTime = settings.backupReminderConfig.lastBackupTime,
            filesState = backupItemsState,
            onShowFiles = {
                vm.loadBackupFileItems()
                showBackupFiles = true
            },
        )

        CardGroup {
            formItem(
                headlineContent = { Text(stringResource(R.string.backup_page_webdav_server_address)) },
            ) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = webDavConfig.url,
                    onValueChange = { updateWebDavConfig(webDavConfig.copy(url = it.trim())) },
                    placeholder = { Text("https://example.com/dav") },
                    singleLine = true
                )
            }
            formItem(
                headlineContent = { Text(stringResource(R.string.backup_page_username)) },
            ) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = webDavConfig.username,
                    onValueChange = { updateWebDavConfig(webDavConfig.copy(username = it.trim())) },
                    singleLine = true
                )
            }
            formItem(
                headlineContent = { Text(stringResource(R.string.backup_page_password)) },
            ) {
                var passwordVisible by remember { mutableStateOf(false) }
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = webDavConfig.password,
                    onValueChange = { updateWebDavConfig(webDavConfig.copy(password = it.trim())) },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (passwordVisible) {
                            HugeIcons.ViewOff
                        } else {
                            HugeIcons.View
                        }
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = null)
                        }
                    },
                    singleLine = true
                )
            }
            formItem(
                headlineContent = { Text(stringResource(R.string.backup_page_path)) },
            ) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = webDavConfig.path,
                    onValueChange = { updateWebDavConfig(webDavConfig.copy(path = it.trim())) },
                    singleLine = true
                )
            }
        }

        CardGroup(
            title = { Text(stringResource(R.string.backup_page_backup_items)) },
        ) {
            backupContentItem(
                options = WebDavConfig.BackupItem.SELECTABLE,
                selected = WebDavConfig.BackupItem.normalize(webDavConfig.items),
                onChange = {
                    updateWebDavConfig(
                        webDavConfig.copy(items = WebDavConfig.BackupItem.normalize(it))
                    )
                },
            ) { item ->
                when (item) {
                    WebDavConfig.BackupItem.DATABASE -> stringResource(R.string.backup_page_chat_records)
                    WebDavConfig.BackupItem.FILES -> stringResource(R.string.backup_page_files)
                    WebDavConfig.BackupItem.WORKSPACES -> stringResource(R.string.backup_page_workspaces)
                }
            }
        }
    }

    if (showBackupFiles) {
        BackupFilesSheet(
            title = stringResource(R.string.backup_page_webdav_backup_files),
            state = backupItemsState,
            restoringName = restoringItemId,
            enabled = !busy,
            info = { BackupFileInfo(name = it.displayName, size = it.size, lastModified = it.lastModified) },
            onDelete = { item ->
                scope.launch {
                    runCatching {
                        vm.deleteWebDavBackupFile(item)
                        toaster.show(
                            context.getString(R.string.backup_page_delete_success),
                            type = ToastType.Success
                        )
                        vm.loadBackupFileItems()
                    }.onFailure { err ->
                        err.printStackTrace()
                        toaster.show(
                            context.getString(
                                R.string.backup_page_delete_failed,
                                err.message ?: ""
                            ),
                            type = ToastType.Error
                        )
                    }
                }
            },
            onRestore = { item ->
                if (vm.claim(BackupOperation.WEBDAV_RESTORE)) {
                    scope.launch {
                        restoringItemId = item.displayName
                        try {
                            vm.restore(item = item)
                            toaster.show(
                                context.getString(R.string.backup_page_restore_success),
                                type = ToastType.Success
                            )
                            showBackupFiles = false
                            onShowRestartDialog()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (err: Exception) {
                            err.printStackTrace()
                            toaster.show(restoreErrorMessage(context, err), type = ToastType.Error)
                        } finally {
                            restoringItemId = null
                            vm.release(BackupOperation.WEBDAV_RESTORE)
                        }
                    }
                }
            },
            onDismiss = { showBackupFiles = false },
        )
    }
}
