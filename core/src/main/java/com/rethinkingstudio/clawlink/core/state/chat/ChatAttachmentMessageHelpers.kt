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
        sourceRunId = record.sourceRunId,
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

private val ansiEscapeRegex = Regex("\u001B\\[[0-?]*[ -/]*[@-~]")
private val hermesRuntimeContextRegex = Regex(
    "(^|\\n)[ \\t]*\\[Hermes runtime context][ \\t]*(?:\\n[ \\t]*Current runtime:[^\\n]*)?(?:\\n[ \\t]*If the user asks which model or provider is currently being used, answer from this runtime context\\.)?"
)
private val mediaAttachmentReferenceRegex = Regex(
    "(?m)[ \\t]*\\[media attached:\\s*(.+?)\\s*\\((.+?)\\)\\s*\\|\\s*(.+?)][ \\t]*"
)
private val fileAttachmentReferenceRegex = Regex(
    "(?m)[ \\t]*\\[file attached:\\s*.+?][ \\t]*"
)
private val mobileBridgeTimestampPrefixRegex = Regex(
    "^\\s*\\[(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun)\\s+\\d{4}-\\d{2}-\\d{2}\\s+\\d{1,2}:\\d{2}(?::\\d{2})?\\s+(?:GMT|UTC)(?:[+-]\\d{1,2}(?::?\\d{2})?)?]\\s*",
    RegexOption.IGNORE_CASE
)
private val excessiveBlankLinesRegex = Regex("\\n{3,}")

internal fun sanitizeChatMessageText(text: String): String {
    val normalized = text
        .replace("\r\n", "\n")
        .replace("\r", "\n")
    return normalized
        .replace(ansiEscapeRegex, "")
        .replace(hermesRuntimeContextRegex, "$1")
        .replace(mediaAttachmentReferenceRegex, "")
        .replace(fileAttachmentReferenceRegex, "")
        .replace(mobileBridgeTimestampPrefixRegex, "")
        .replace("\u001B", "")
        .replace(excessiveBlankLinesRegex, "\n\n")
        .trim()
}

internal fun chatMediaAttachmentReferenceFileNames(text: String): List<String> {
    val normalized = text
        .replace("\r\n", "\n")
        .replace("\r", "\n")
    return mediaAttachmentReferenceRegex.findAll(normalized)
        .mapNotNull { match ->
            val resolvedPath = match.groupValues.getOrNull(3)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: match.groupValues.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
            resolvedPath
                ?.substringAfterLast('/')
                ?.substringAfterLast('\\')
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }
        .toList()
}

internal fun sanitizeChatDisplayText(text: String): String {
    return sanitizeChatMessageText(text).ifBlank { "attachment" }
}
