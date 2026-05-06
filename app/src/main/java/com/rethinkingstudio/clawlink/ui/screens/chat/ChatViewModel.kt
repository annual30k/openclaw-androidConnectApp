package com.rethinkingstudio.clawlink.ui.screens.chat

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.core.models.chat.ComposerAttachmentUploadItem
import com.rethinkingstudio.clawlink.core.models.chat.AttachmentUploadPhase
import com.rethinkingstudio.clawlink.core.network.dto.RelayFileTransferItem
import com.rethinkingstudio.clawlink.core.models.gateway.AggregateStatus
import com.rethinkingstudio.clawlink.core.state.chat.ChatStore
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import com.rethinkingstudio.clawlink.core.state.model.ModelStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.io.File

internal data class ChatImagePreviewState(
    val url: String,
    val accessToken: String,
    val fileName: String?,
    val cacheKey: String? = null
)

internal data class ChatDocumentPreviewState(
    val url: String,
    val accessToken: String,
    val fileName: String?,
    val mimeType: String?,
    val cacheKey: String? = null
)

/**
 * ViewModel for the Chat screen, managing local UI state and coordinating business logic.
 */
internal class ChatViewModel(
    val chatStore: ChatStore,
    val gatewayStore: GatewayStore,
    val modelStore: ModelStore,
    private val scope: CoroutineScope
) {
    // Local UI State
    var messageText by mutableStateOf("")
    var showGatewaySheet by mutableStateOf(false)
    var showSkillExpansionSheet by mutableStateOf(false)
    var showModelPicker by mutableStateOf(false)
    var showAttachmentMenu by mutableStateOf(false)
    var attachmentButtonPosition by mutableStateOf(IntOffset.Zero)
    var attachmentButtonSize by mutableStateOf(IntSize.Zero)
    var voiceMode by mutableStateOf(false)
    var composerNotice by mutableStateOf<String?>(null)
    var composerAttachments by mutableStateOf<List<ComposerAttachmentDraft>>(emptyList())
    var composerAttachmentUploadItems by mutableStateOf<List<ComposerAttachmentUploadItem>>(emptyList())
    var isUploadingAttachment by mutableStateOf(false)
    var composerHeight by mutableStateOf(0.dp)
    var imagePreview by mutableStateOf<ChatImagePreviewState?>(null)
    var documentPreview by mutableStateOf<ChatDocumentPreviewState?>(null)

    fun clearError() {
        chatStore.clearError()
        gatewayStore.clearError()
        composerNotice = null
    }

    fun removeAttachment(attachment: ComposerAttachmentDraft) {
        composerAttachments = composerAttachments.filterNot { it.filePath == attachment.filePath }
        runCatching { File(attachment.filePath).delete() }
    }

    private fun setUploadItemPhase(
        attachmentId: String,
        phase: AttachmentUploadPhase,
        failureMessage: String? = null
    ) {
        composerAttachmentUploadItems = composerAttachmentUploadItems.map { item ->
            if (item.attachment.id == attachmentId) {
                item.copy(phase = phase, failureMessage = failureMessage)
            } else {
                item
            }
        }
    }

    private fun setUploadItemProgress(
        attachmentId: String,
        progress: Double
    ) {
        composerAttachmentUploadItems = composerAttachmentUploadItems.map { item ->
            if (item.attachment.id == attachmentId) {
                item.copy(progress = progress.coerceIn(0.0, 1.0))
            } else {
                item
            }
        }
    }

    private fun clearCompletedUploadItemsAndTempFiles() {
        val completedItems = composerAttachmentUploadItems.filter { it.phase == AttachmentUploadPhase.completed }
        composerAttachmentUploadItems = composerAttachmentUploadItems.filterNot { it.phase == AttachmentUploadPhase.completed }
        completedItems.forEach { item ->
            runCatching { File(item.attachment.filePath).delete() }
        }
    }

    fun toggleModelPicker() {
        showModelPicker = !showModelPicker
        if (showModelPicker && modelStore.state.value.models.isEmpty()) {
            gatewayStore.state.value.selectedGatewayId?.let { id ->
                scope.launch { modelStore.loadModels(id) }
            }
        }
    }

    fun onSend(context: Context) {
        val gatewayId = gatewayStore.state.value.selectedGateway?.id.orEmpty()
        val sessionKey = chatStore.state.value.currentSessionKey
        
        scope.launch {
            sendComposerMessage(
                context = context,
                gatewayId = gatewayId,
                sessionKey = sessionKey,
                rawInput = messageText,
                attachments = composerAttachments
            )
        }
    }

    private suspend fun sendComposerMessage(
        context: Context,
        gatewayId: String,
        sessionKey: String,
        rawInput: String,
        attachments: List<ComposerAttachmentDraft>
    ) {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank() && attachments.isEmpty()) return
        
        if (gatewayId.isBlank() || sessionKey.isBlank()) {
            composerNotice = context.getString(R.string.gateway_unpaired_host)
            return
        }
        
        if (trimmed.startsWith("/")) {
            chatStore.sendCommand(
                gatewayId = gatewayId,
                command = trimmed
            )
            messageText = ""
            composerNotice = null
            return
        }

        isUploadingAttachment = true
        try {
            composerAttachmentUploadItems = attachments.map { attachment ->
                ComposerAttachmentUploadItem(
                    gatewayId = gatewayId,
                    attachment = attachment,
                    progress = 0.0,
                    phase = AttachmentUploadPhase.uploading,
                    failureMessage = null
                )
            }
            chatStore.beginComposerAttachmentUploadMessages(
                attachments = attachments,
                gatewayId = gatewayId,
                sessionKey = sessionKey,
                senderDisplayName = gatewayStore.state.value.selectedGateway?.displayName,
                messageSortBaseTimestamp = System.currentTimeMillis() / 1000.0
            )
            composerAttachments = emptyList()

            attachments.forEach { attachment ->
                val record = withContext(Dispatchers.IO) {
                    uploadComposerAttachment(
                        gatewayId = gatewayId,
                        sessionKey = sessionKey,
                        attachment = attachment,
                        onProgress = { progress ->
                            scope.launch(Dispatchers.Main) {
                                setUploadItemProgress(attachment.id, progress)
                                chatStore.updateComposerAttachmentUploadMessage(
                                    attachment = attachment,
                                    gatewayId = gatewayId,
                                    sessionKey = sessionKey,
                                    progress = progress,
                                    phase = AttachmentUploadPhase.uploading,
                                    senderDisplayName = gatewayStore.state.value.selectedGateway?.displayName
                                )
                            }
                        }
                    )
                }
                chatStore.completeComposerAttachmentUploadMessage(
                    attachment = attachment,
                    record = record,
                    gatewayId = gatewayId,
                    sessionKey = sessionKey,
                    completionSortTimestamp = (System.currentTimeMillis() / 1000.0)
                )
                setUploadItemPhase(attachment.id, AttachmentUploadPhase.completed)
            }
            if (trimmed.isNotBlank()) {
                // iOS sends chat.send only for the text part; attachments are uploaded first
                // and then surfaced through the relay's file events.
                chatStore.sendMessage(
                    content = trimmed,
                    gatewayId = gatewayId,
                    attachmentIds = emptyList(),
                    attachmentBlocks = emptyList()
                )
            }

            messageText = ""
            composerNotice = null
            if (composerAttachmentUploadItems.isNotEmpty() && composerAttachmentUploadItems.all { it.phase == AttachmentUploadPhase.completed }) {
                scope.launch {
                    delay(900)
                    if (composerAttachmentUploadItems.all { it.phase == AttachmentUploadPhase.completed }) {
                        clearCompletedUploadItemsAndTempFiles()
                    }
                }
            }
            
        } catch (e: Exception) {
            val failedAttachmentId = composerAttachmentUploadItems.firstOrNull { it.phase == AttachmentUploadPhase.uploading }?.attachment?.id
                ?: attachments.firstOrNull()?.id
            if (failedAttachmentId != null) {
                val failedAttachment = attachments.firstOrNull { it.id == failedAttachmentId }
                setUploadItemPhase(
                    attachmentId = failedAttachmentId,
                    phase = AttachmentUploadPhase.failed,
                    failureMessage = e.message ?: "Unknown error"
                )
                if (failedAttachment != null) {
                    chatStore.updateComposerAttachmentUploadMessage(
                        attachment = failedAttachment,
                        gatewayId = gatewayId,
                        sessionKey = sessionKey,
                        progress = composerAttachmentUploadItems.firstOrNull { it.attachment.id == failedAttachmentId }?.progress ?: 0.0,
                        phase = AttachmentUploadPhase.failed,
                        failureMessage = e.message ?: "Unknown error",
                        senderDisplayName = gatewayStore.state.value.selectedGateway?.displayName
                    )
                }
            }
            composerNotice = context.getString(
                R.string.chat_attachment_send_failed_with_reason,
                e.message ?: "Unknown error"
            )
        } finally {
            isUploadingAttachment = false
        }
    }

    private suspend fun uploadComposerAttachment(
        gatewayId: String,
        sessionKey: String,
        attachment: ComposerAttachmentDraft,
        onProgress: ((Double) -> Unit)? = null
    ): RelayFileTransferItem {
        val bytes = withContext(Dispatchers.IO) {
            File(attachment.filePath).readBytes()
        }
        return chatStore.uploadAttachment(
            gatewayId = gatewayId,
            fileName = attachment.fileName,
            mimeType = attachment.mimeType,
            bytes = bytes,
            sha256 = ChatFileUtils.sha256Hex(bytes),
            durationMs = attachment.durationMs?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt(),
            imageWidth = attachment.imageWidth,
            imageHeight = attachment.imageHeight,
            onProgress = onProgress
        )
    }
}
