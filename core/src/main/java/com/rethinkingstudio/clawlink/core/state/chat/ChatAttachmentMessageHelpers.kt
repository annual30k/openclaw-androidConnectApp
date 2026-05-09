package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.AttachmentUploadPhase
import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.ComposerAttachmentDraft
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import com.rethinkingstudio.clawlink.core.network.dto.RelayFileTransferItem
import java.io.File

internal fun mergeCompletedFileMessage(existing: ChatMessage, completed: ChatMessage): ChatMessage {
    val existingLocalBlocks = existing.transferContentBlocks()
    if (existingLocalBlocks.isEmpty() || completed.contentBlocks.isEmpty()) {
        return completed
    }

    val mergedBlocks = completed.contentBlocks.map { completedBlock ->
        val localBlock = existingLocalBlocks.firstOrNull { existingBlock ->
            existingBlock.fileId.isNullOrBlank() &&
                existingBlock.isImageFileBlock &&
                completedBlock.isImageFileBlock &&
                sameTransferIdentity(existingBlock, completedBlock)
        }
        val localPreviewPath = localBlock?.fileDownloadURLString
            ?.trim()
            ?.takeIf { it.isNotEmpty() && File(it).exists() }
        if (localPreviewPath != null) {
            completedBlock.copy(
                downloadUrl = localPreviewPath,
                downloadPath = completedBlock.fileDownloadURLString
            )
        } else {
            completedBlock
        }
    }

    return completed.copy(contentBlocks = mergedBlocks)
}

private fun sameTransferIdentity(left: RelayChatContentBlock, right: RelayChatContentBlock): Boolean {
    val leftName = left.fileDisplayName?.trim().orEmpty()
    val rightName = right.fileDisplayName?.trim().orEmpty()
    if (leftName.isBlank() || !leftName.equals(rightName, ignoreCase = true)) return false

    val leftMime = left.mimeType?.trim().orEmpty()
    val rightMime = right.mimeType?.trim().orEmpty()
    if (leftMime.isNotBlank() && rightMime.isNotBlank() && !leftMime.equals(rightMime, ignoreCase = true)) {
        return false
    }

    val leftSize = left.sizeBytes?.takeIf { it > 0 }
    val rightSize = right.sizeBytes?.takeIf { it > 0 }
    return leftSize == null || rightSize == null || leftSize == rightSize
}

internal fun makeComposerAttachmentUploadContentBlock(
    attachment: ComposerAttachmentDraft,
    gatewayId: String,
    sessionKey: String,
    senderDisplayName: String?,
    statusText: String?,
    downloadUrlString: String
): RelayChatContentBlock {
    return RelayChatContentBlock(
        type = when {
            attachment.mimeType.trim().lowercase().startsWith("audio/") -> "voice"
            attachment.mimeType.trim().lowercase().startsWith("image/") -> "image"
            else -> "file"
        },
        text = attachment.fileName,
        name = attachment.fileName,
        fileName = attachment.fileName,
        mimeType = attachment.mimeType,
        sizeBytes = attachment.sizeBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        durationMs = attachment.durationMs?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt(),
        imageWidth = attachment.imageWidth,
        imageHeight = attachment.imageHeight,
        downloadUrl = downloadUrlString,
        senderDisplayName = senderDisplayName,
        gatewayId = gatewayId,
        sessionKey = sessionKey,
        status = statusText
    )
}

internal fun makeFileContentBlock(record: RelayFileTransferItem): RelayChatContentBlock {
    val normalizedMime = record.mimeType.trim().lowercase()
    return RelayChatContentBlock(
        type = when {
            normalizedMime.startsWith("audio/") -> "voice"
            normalizedMime.startsWith("image/") -> "image"
            else -> "file"
        },
        text = record.fileName,
        name = record.fileName,
        fileId = record.fileId,
        fileName = record.fileName,
        mimeType = record.mimeType,
        sizeBytes = record.sizeBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        durationMs = record.durationMs,
        imageWidth = record.imageWidth,
        imageHeight = record.imageHeight,
        downloadUrl = record.downloadPath,
        downloadPath = record.downloadPath,
        expiresAt = record.expiresAt,
        senderDisplayName = record.senderDisplayName,
        gatewayId = record.gatewayId,
        sessionKey = record.sessionKey,
        status = record.status
    )
}

internal fun AttachmentUploadPhase.toMessageState(): MessageState {
    return when (this) {
        AttachmentUploadPhase.failed -> MessageState.failed
        AttachmentUploadPhase.uploading -> MessageState.streaming
        AttachmentUploadPhase.completed -> MessageState.completed
    }
}

internal fun composerAttachmentUploadRunId(attachment: ComposerAttachmentDraft): String {
    return "upload-${attachment.id}"
}

internal fun sanitizeChatDisplayText(text: String): String {
    return text.trim().ifBlank { "attachment" }
}
