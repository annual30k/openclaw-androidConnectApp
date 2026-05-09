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
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
import com.rethinkingstudio.clawlink.core.state.chat.ChatStore
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import com.rethinkingstudio.clawlink.core.state.model.ModelStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.io.File
import java.util.UUID

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
    var voiceInputPhase by mutableStateOf(VoiceInputPhase.Idle)
    var voiceInputTranscript by mutableStateOf("")
    var voiceInputBaseText by mutableStateOf("")
    var voiceInputAudioLevel by mutableStateOf(0.0)
    var voiceInputCancelPreview by mutableStateOf(false)
    private var voiceInputHoldToken: UUID? = null
    private var voiceInputHoldJob: Job? = null
    var composerNotice by mutableStateOf<String?>(null)
    var composerAttachments by mutableStateOf<List<ComposerAttachmentDraft>>(emptyList())
    var composerAttachmentUploadItems by mutableStateOf<List<ComposerAttachmentUploadItem>>(emptyList())
    var isUploadingAttachment by mutableStateOf(false)
    var composerHeight by mutableStateOf(0.dp)
    var imagePreview by mutableStateOf<ChatImagePreviewState?>(null)
    var documentPreview by mutableStateOf<ChatDocumentPreviewState?>(null)
    private val speechCoordinator = ComposerSpeechCoordinator(
        scope = scope,
        onPartialTranscript = { transcript -> updateVoiceInputTranscript(transcript) },
        onFinalTranscript = { transcript -> completeVoiceInput(transcript) },
        onAudioLevel = { audioLevel -> updateVoiceInputAudioLevel(audioLevel) },
        onError = { error -> handleVoiceInputFailure(error) }
    )

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

    private fun resetComposerForNewSession() {
        val pendingAttachments = composerAttachments
        val uploadItems = composerAttachmentUploadItems
        messageText = ""
        composerNotice = null
        composerAttachments = emptyList()
        composerAttachmentUploadItems = emptyList()
        isUploadingAttachment = false
        showAttachmentMenu = false
        imagePreview = null
        documentPreview = null
        resetVoiceInputState(restoreComposer = false)
        (pendingAttachments.map { it.filePath } + uploadItems.map { it.attachment.filePath })
            .distinct()
            .forEach { filePath -> runCatching { File(filePath).delete() } }
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

    fun toggleVoiceMode() {
        if (voiceInputPhase.isBusy) return
        voiceMode = !voiceMode
    }

    fun beginVoiceInputHold(context: Context, hasRecordAudioPermission: Boolean) {
        if (voiceInputPhase != VoiceInputPhase.Idle || voiceInputHoldToken != null) return
        val token = UUID.randomUUID()
        voiceInputHoldToken = token
        voiceInputHoldJob?.cancel()
        voiceInputHoldJob = scope.launch {
            delay(240)
            if (voiceInputHoldToken == token) {
                startVoiceInput(context, holdToken = token, hasRecordAudioPermission = hasRecordAudioPermission)
            }
            if (voiceInputHoldToken == token) {
                voiceInputHoldJob = null
            }
        }
    }

    fun endVoiceInputHold() {
        voiceInputHoldJob?.cancel()
        voiceInputHoldJob = null
        if (voiceInputHoldToken == null) return
        when (voiceInputPhase) {
            VoiceInputPhase.Recording -> stopVoiceInput()
            VoiceInputPhase.Starting -> cancelVoiceInput()
            VoiceInputPhase.Stopping, VoiceInputPhase.Confirming, VoiceInputPhase.Idle -> {
                voiceInputHoldToken = null
            }
        }
    }

    fun startVoiceInput(
        context: Context,
        holdToken: UUID? = null,
        hasRecordAudioPermission: Boolean
    ) {
        if (holdToken != null && voiceInputHoldToken != holdToken) return

        val gatewayId = gatewayStore.state.value.selectedGateway?.id.orEmpty()
        val hasActiveSession = gatewayId.isNotBlank() && chatStore.state.value.currentSessionKey.isNotBlank()
        when {
            !hasActiveSession -> {
                composerNotice = context.getString(R.string.voice_input_requires_pairing)
                voiceInputHoldToken = null
                return
            }
            chatStore.state.value.isStreaming || chatStore.state.value.isStoppingRun -> {
                composerNotice = context.getString(R.string.voice_input_pending_run)
                voiceInputHoldToken = null
                return
            }
            !hasRecordAudioPermission -> {
                composerNotice = context.getString(R.string.voice_input_microphone_denied)
                voiceInputHoldToken = null
                return
            }
            voiceInputPhase != VoiceInputPhase.Idle -> {
                voiceInputHoldToken = null
                return
            }
        }

        composerNotice = null
        voiceInputBaseText = messageText
        voiceInputTranscript = ""
        voiceInputAudioLevel = 0.0
        voiceMode = true
        voiceInputPhase = VoiceInputPhase.Starting

        try {
            speechCoordinator.start(context)
            if (holdToken != null && voiceInputHoldToken != holdToken) {
                speechCoordinator.cancel()
                resetVoiceInputState(restoreComposer = true)
                return
            }
            voiceInputPhase = VoiceInputPhase.Recording
        } catch (error: VoiceInputError) {
            resetVoiceInputState(restoreComposer = true)
            composerNotice = error.message
        } catch (error: Exception) {
            resetVoiceInputState(restoreComposer = true)
            composerNotice = context.getString(R.string.voice_input_start_failed, error.message ?: choose("Unknown error", "未知错误"))
        }
    }

    fun stopVoiceInput() {
        if (voiceInputPhase != VoiceInputPhase.Recording && voiceInputPhase != VoiceInputPhase.Starting) return
        voiceInputHoldToken = null
        voiceInputHoldJob?.cancel()
        voiceInputHoldJob = null
        voiceInputPhase = VoiceInputPhase.Stopping
        voiceInputAudioLevel = 0.0
        speechCoordinator.stop()
    }

    fun cancelVoiceInput() {
        voiceInputHoldToken = null
        voiceInputHoldJob?.cancel()
        voiceInputHoldJob = null
        voiceInputCancelPreview = false
        voiceInputAudioLevel = 0.0
        speechCoordinator.cancel()
        resetVoiceInputState(restoreComposer = true)
    }

    fun continueVoiceInputEditing() {
        if (voiceInputPhase != VoiceInputPhase.Confirming) return
        speechCoordinator.cancel()
        voiceInputCancelPreview = false
        voiceInputPhase = VoiceInputPhase.Idle
        voiceInputTranscript = ""
        voiceInputBaseText = ""
        voiceInputHoldToken = null
        voiceMode = true
    }

    fun confirmVoiceInput(context: Context) {
        scope.launch {
            sendComposerMessage(
                context = context,
                gatewayId = gatewayStore.state.value.selectedGateway?.id.orEmpty(),
                sessionKey = chatStore.state.value.currentSessionKey,
                rawInput = messageText,
                attachments = composerAttachments
            )
            resetVoiceInputState(restoreComposer = false)
        }
    }

    fun disposeVoiceInput() {
        voiceInputHoldJob?.cancel()
        speechCoordinator.destroy()
    }

    private fun updateVoiceInputTranscript(transcript: String) {
        if (voiceInputPhase != VoiceInputPhase.Starting &&
            voiceInputPhase != VoiceInputPhase.Recording &&
            voiceInputPhase != VoiceInputPhase.Stopping
        ) return

        voiceInputTranscript = transcript
        messageText = composedVoiceInputText(
            baseText = voiceInputBaseText,
            transcript = transcript.trim()
        )
    }

    private fun updateVoiceInputAudioLevel(audioLevel: Double) {
        if (voiceInputPhase != VoiceInputPhase.Starting &&
            voiceInputPhase != VoiceInputPhase.Recording &&
            voiceInputPhase != VoiceInputPhase.Stopping
        ) return

        val normalized = audioLevel.coerceIn(0.0, 1.0)
        voiceInputAudioLevel = maxOf(normalized, voiceInputAudioLevel * 0.72)
    }

    private fun completeVoiceInput(transcript: String) {
        if (voiceInputPhase != VoiceInputPhase.Starting &&
            voiceInputPhase != VoiceInputPhase.Recording &&
            voiceInputPhase != VoiceInputPhase.Stopping
        ) return

        val resolvedTranscript = transcript.trim()
        if (resolvedTranscript.isEmpty()) {
            resetVoiceInputState(restoreComposer = true)
            return
        }

        messageText = composedVoiceInputText(
            baseText = voiceInputBaseText,
            transcript = resolvedTranscript
        )
        voiceInputTranscript = resolvedTranscript
        voiceInputCancelPreview = false
        voiceInputAudioLevel = 0.0
        voiceInputPhase = VoiceInputPhase.Confirming
    }

    private fun handleVoiceInputFailure(error: VoiceInputError) {
        voiceInputCancelPreview = false
        voiceInputAudioLevel = 0.0
        resetVoiceInputState(restoreComposer = false)
        composerNotice = error.message
    }

    private fun resetVoiceInputState(restoreComposer: Boolean) {
        voiceInputHoldJob?.cancel()
        voiceInputHoldJob = null
        if (restoreComposer) {
            messageText = voiceInputBaseText
        }
        voiceInputPhase = VoiceInputPhase.Idle
        voiceInputTranscript = ""
        voiceInputBaseText = ""
        voiceInputAudioLevel = 0.0
        voiceInputCancelPreview = false
        voiceInputHoldToken = null
        voiceMode = true
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
            if (trimmed == "/new") {
                chatStore.newSession()
                resetComposerForNewSession()
            } else {
                messageText = ""
                composerNotice = null
            }
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
                    failureMessage = e.message ?: choose("Unknown error", "未知错误")
                )
                if (failedAttachment != null) {
                    chatStore.updateComposerAttachmentUploadMessage(
                        attachment = failedAttachment,
                        gatewayId = gatewayId,
                        sessionKey = sessionKey,
                        progress = composerAttachmentUploadItems.firstOrNull { it.attachment.id == failedAttachmentId }?.progress ?: 0.0,
                        phase = AttachmentUploadPhase.failed,
                        failureMessage = e.message ?: choose("Unknown error", "未知错误"),
                        senderDisplayName = gatewayStore.state.value.selectedGateway?.displayName
                    )
                }
            }
            composerNotice = context.getString(
                R.string.chat_attachment_send_failed_with_reason,
                e.message ?: choose("Unknown error", "未知错误")
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
