package com.rethinkingstudio.clawlink.ui.screens.chat

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal data class ChatAttachmentLaunchers(
    val pickFiles: () -> Unit,
    val pickAlbum: () -> Unit,
    val pickCamera: () -> Unit
)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
internal fun rememberChatAttachmentLaunchers(
    context: Context,
    viewModel: ChatViewModel,
    scope: CoroutineScope,
    cameraPermissionState: PermissionState,
    dismissKeyboard: () -> Unit
): ChatAttachmentLaunchers {
    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.importComposerAttachments(context, viewModel, uris)
    }
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.importComposerAttachments(context, viewModel, uris)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap == null) {
            viewModel.showAttachmentMenu = false
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            viewModel.isUploadingAttachment = true
            viewModel.composerNotice = null
            try {
                val imported = ChatFileUtils.importCapturedImage(context, bitmap)
                viewModel.composerAttachments = viewModel.composerAttachments + imported
            } catch (e: Exception) {
                viewModel.composerNotice = context.getString(
                    R.string.chat_attachment_import_failed_with_reason,
                    e.message ?: choose("Unknown error", "未知错误")
                )
            } finally {
                viewModel.isUploadingAttachment = false
                viewModel.showAttachmentMenu = false
            }
        }
    }
    return remember(
        filePickerLauncher,
        imagePickerLauncher,
        cameraLauncher,
        cameraPermissionState.status,
        dismissKeyboard
    ) {
        ChatAttachmentLaunchers(
            pickFiles = {
                dismissKeyboard()
                viewModel.showAttachmentMenu = false
                filePickerLauncher.launch(attachmentPickerMimeTypes(ComposerAttachmentPickTarget.FILES))
            },
            pickAlbum = {
                dismissKeyboard()
                viewModel.showAttachmentMenu = false
                imagePickerLauncher.launch(attachmentPickerMimeTypes(ComposerAttachmentPickTarget.IMAGES))
            },
            pickCamera = {
                dismissKeyboard()
                viewModel.showAttachmentMenu = false
                if (cameraPermissionState.status.isGranted) {
                    runCatching {
                        cameraLauncher.launch(null)
                    }.onFailure {
                        viewModel.composerNotice = context.getString(R.string.chat_composer_camera_unavailable)
                    }
                } else {
                    cameraPermissionState.launchPermissionRequest()
                }
            }
        )
    }
}

private fun CoroutineScope.importComposerAttachments(
    context: Context,
    viewModel: ChatViewModel,
    uris: List<android.net.Uri>
) {
    launch {
        viewModel.isUploadingAttachment = true
        viewModel.composerNotice = null
        try {
            val imported = ChatFileUtils.importPickedAttachments(context, uris)
            viewModel.composerAttachments = viewModel.composerAttachments + imported
            if (imported.isEmpty()) {
                viewModel.composerNotice = context.getString(R.string.chat_attachment_import_failed)
            }
        } catch (e: Exception) {
            viewModel.composerNotice = context.getString(
                R.string.chat_attachment_import_failed_with_reason,
                e.message ?: choose("Unknown error", "未知错误")
            )
        } finally {
            viewModel.isUploadingAttachment = false
            viewModel.showAttachmentMenu = false
        }
    }
}
