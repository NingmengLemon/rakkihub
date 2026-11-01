package me.rerere.rikkahub.ui.components.ai

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.ui.components.ui.permission.PermissionCamera
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.utils.isAllowedFileType
import me.rerere.ui.sketch.SketchDialog
import me.rerere.ui.sketch.SketchResult
import me.rerere.ui.toast.ToastType
import org.koin.compose.koinInject
import java.io.File
import kotlin.uuid.Uuid

internal data class ChatAttachmentPickerActions(
    val onTakePicture: () -> Unit,
    val onPickImage: () -> Unit,
    val onPickVideo: () -> Unit,
    val onPickAudio: () -> Unit,
    val onPickFile: () -> Unit,
    val onSketch: () -> Unit,
)

@Composable
internal fun rememberChatAttachmentPickerActions(
    inputState: ChatInputState,
    setting: Settings,
    onAttachmentAdded: () -> Unit,
): ChatAttachmentPickerActions {
    val context = LocalContext.current
    val resources = LocalResources.current
    val toaster = LocalToaster.current
    val filesManager: FilesManager = koinInject()
    val scope = rememberCoroutineScope()
    val cameraPermission = rememberPermissionState(PermissionCamera)
    PermissionManager(permissionState = cameraPermission)

    var cameraOutputUri by remember { mutableStateOf<Uri?>(null) }
    var cameraOutputFile by remember { mutableStateOf<File?>(null) }
    val launchCameraCrop = useCropLauncher(
        onCroppedImageReady = { croppedUri ->
            // 拷贝在 IO 上做，拷贝完再动输入框；裁剪用的临时文件由 CropLauncher 在回调返回后删
            inputState.addImages(
                withContext(Dispatchers.IO) { filesManager.createChatFilesByContents(listOf(croppedUri)) }
            )
            onAttachmentAdded()
        },
        onCleanup = {
            cameraOutputFile?.delete()
            cameraOutputFile = null
            cameraOutputUri = null
        }
    )
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captureSuccessful ->
        if (captureSuccessful && cameraOutputUri != null) {
            if (setting.displaySetting.skipCropImage) {
                val outputUri = cameraOutputUri!!
                val outputFile = cameraOutputFile
                cameraOutputFile = null
                cameraOutputUri = null
                scope.launch {
                    try {
                        inputState.addImages(
                            withContext(Dispatchers.IO) { filesManager.createChatFilesByContents(listOf(outputUri)) }
                        )
                    } finally {
                        outputFile?.delete()
                    }
                    onAttachmentAdded()
                }
            } else {
                launchCameraCrop(cameraOutputUri!!)
            }
        } else {
            cameraOutputFile?.delete()
            cameraOutputFile = null
            cameraOutputUri = null
        }
    }
    val onTakePicture: () -> Unit = {
        if (cameraPermission.allRequiredPermissionsGranted) {
            cameraOutputFile = context.cacheDir.resolve("camera_${Uuid.random()}.jpg")
            cameraOutputUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                cameraOutputFile!!
            )
            cameraLauncher.launch(cameraOutputUri!!)
        } else {
            cameraPermission.requestPermissions()
        }
    }

    val launchImageCrop = useCropLauncher(
        onCroppedImageReady = { croppedUri ->
            inputState.addImages(
                withContext(Dispatchers.IO) { filesManager.createChatFilesByContents(listOf(croppedUri)) }
            )
            onAttachmentAdded()
        },
    )
    val imagePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { selectedUris ->
            if (selectedUris.isNotEmpty()) {
                Log.d("ImagePickButton", "Selected URIs: $selectedUris")
                if (!setting.displaySetting.skipCropImage && selectedUris.size == 1) {
                    launchImageCrop(selectedUris.first())
                } else {
                    scope.launch {
                        inputState.addImages(
                            withContext(Dispatchers.IO) { filesManager.createChatFilesByContents(selectedUris) }
                        )
                        onAttachmentAdded()
                    }
                }
            } else {
                Log.d("ImagePickButton", "No images selected")
            }
        }

    val videoPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { selectedUris ->
            if (selectedUris.isNotEmpty()) {
                scope.launch {
                    inputState.addVideos(
                        withContext(Dispatchers.IO) { filesManager.createChatFilesByContents(selectedUris) }
                    )
                    onAttachmentAdded()
                }
            }
        }

    val audioPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { selectedUris ->
            if (selectedUris.isNotEmpty()) {
                scope.launch {
                    inputState.addAudios(
                        withContext(Dispatchers.IO) { filesManager.createChatFilesByContents(selectedUris) }
                    )
                    onAttachmentAdded()
                }
            }
        }

    val filePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) {
                scope.launch {
                    val documents = mutableListOf<UIMessagePart.Document>()
                    val errors = mutableListOf<String>()
                    withContext(Dispatchers.IO) {
                        uris.forEach { uri ->
                            val fileName = filesManager.getFileNameFromUri(uri) ?: "file"
                            val mime = filesManager.getFileMimeType(uri) ?: "text/plain"
                            if (!isAllowedFileType(fileName, mime)) {
                                errors += resources.getString(R.string.chat_input_unsupported_file_type, fileName)
                                return@forEach
                            }
                            val localUri = filesManager.createChatFilesByContents(listOf(uri)).firstOrNull()
                            if (localUri == null) {
                                errors += resources.getString(R.string.chat_input_file_read_failed, fileName)
                                return@forEach
                            }
                            documents += UIMessagePart.Document(
                                url = localUri.toString(),
                                fileName = fileName,
                                mime = mime,
                            )
                        }
                    }
                    errors.forEach { toaster.show(it, type = ToastType.Error) }
                    if (documents.isNotEmpty()) {
                        inputState.addFiles(documents)
                        onAttachmentAdded()
                    }
                }
            }
        }

    var showSketch by remember { mutableStateOf(false) }
    if (showSketch) {
        SketchDialog(
            onDismiss = { showSketch = false },
            onConfirm = { result ->
                showSketch = false
                scope.launch {
                    inputState.addImages(listOfNotNull(filesManager.createChatFileBySketch(result)))
                    onAttachmentAdded()
                }
            },
        )
    }

    return ChatAttachmentPickerActions(
        onTakePicture = onTakePicture,
        onPickImage = { imagePickerLauncher.launch("image/*") },
        onPickVideo = { videoPickerLauncher.launch("video/*") },
        onPickAudio = { audioPickerLauncher.launch("audio/*") },
        onPickFile = { filePickerLauncher.launch(arrayOf("*/*")) },
        onSketch = { showSketch = true },
    )
}

/** 把画板的结果存成聊天附件。 */
internal suspend fun FilesManager.createChatFileBySketch(result: SketchResult): Uri? = withContext(Dispatchers.IO) {
    createChatFilesByByteArrays(
        byteArrays = listOf(result.encode()),
        displayName = "sketch.${result.extension}",
        mimeType = result.mimeType,
    ).firstOrNull()
}
