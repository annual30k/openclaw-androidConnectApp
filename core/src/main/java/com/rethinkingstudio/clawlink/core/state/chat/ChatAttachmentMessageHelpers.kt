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
            existingBlock.isImageFileBlock &&
                completedBlock.isImageFileBlock &&
                sameTransferIdentity(existingBlock, completedBlock)
        }
        val localPreviewPath = localBlock?.fileDownloadURLString
            ?.trim()
            ?.takeIf { it.isNotEmpty() && isLocalPreviewReference(it) }
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

private fun isLocalPreviewReference(value: String): Boolean {
    return value.startsWith("file://", ignoreCase = true) ||
        value.startsWith("content://", ignoreCase = true) ||
        (value.startsWith("/") && !value.startsWith("/api/", ignoreCase = true)) ||
        File(value).exists()
}

private fun sameTransferIdentity(left: RelayChatContentBlock, right: RelayChatContentBlock): Boolean {
    val leftFileId = left.fileId?.trim()?.takeIf { it.isNotEmpty() }
    val rightFileId = right.fileId?.trim()?.takeIf { it.isNotEmpty() }
    if (leftFileId != null && rightFileId != null) {
        return leftFileId == rightFileId
    }

    val leftName = left.fileDisplayName?.trim().orEmpty()
    val rightName = right.fileDisplayName?.trim().orEmpty()
    if (leftName.isBlank() || !leftName.equals(rightName, ignoreCase = true)) return false

    val leftMime = left.mimeType?.trim().orEmpty()
    val rightMime = right.mimeType?.trim().orEmpty()
    if (!attachmentMimeTypesCompatible(leftMime.lowercase(), rightMime.lowercase())) {
        return false
    }

    val leftSize = left.sizeBytes?.takeIf { it > 0 }
    val rightSize = right.sizeBytes?.takeIf { it > 0 }
    return leftSize == null || rightSize == null || leftSize == rightSize
}

private fun attachmentMimeTypesCompatible(left: String, right: String): Boolean {
    if (left.isBlank() || right.isBlank()) return true
    if (left == right) return true
    if (left == "application/octet-stream" || right == "application/octet-stream") return true
    if (left.startsWith("image/") && right.startsWith("image/")) return true
    if (left.startsWith("audio/") && right.startsWith("audio/")) return true
    return false
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

fun makeUploadedAttachmentContentBlock(
    record: RelayFileTransferItem,
    localDownloadUrlString: String? = null
): RelayChatContentBlock {
    val block = makeFileContentBlock(record)
    val localPreviewPath = localDownloadUrlString
        ?.trim()
        ?.takeIf { it.isNotEmpty() && isLocalPreviewReference(it) }
        ?: return block
    return block.copy(
        downloadUrl = localPreviewPath,
        downloadPath = block.fileDownloadURLString
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
private val compactMediaAttachmentReferenceRegex = Regex(
    "(?m)[ \\t]*\\[media attached:\\s*([^\\]\\n]+)][ \\t]*"
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
        .replace(compactMediaAttachmentReferenceRegex, "")
        .replace(fileAttachmentReferenceRegex, "")
        .replace(mobileBridgeTimestampPrefixRegex, "")
        .replace("\u001B", "")
        .replace(excessiveBlankLinesRegex, "\n\n")
        .trim()
}

internal fun chatMediaAttachmentReferenceFileNames(text: String): List<String> {
    return chatMediaAttachmentReferences(text).map { it.fileName }
}

internal fun chatMediaAttachmentReferences(text: String): List<ChatMediaAttachmentReference> {
    val normalized = text
        .replace("\r\n", "\n")
        .replace("\r", "\n")
    val legacyMatches = mediaAttachmentReferenceRegex.findAll(normalized).mapNotNull { match ->
        val resolvedPath = match.groupValues.getOrNull(3)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: match.groupValues.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
        resolvedPath?.let { path ->
            ChatMediaAttachmentReference(
                path = path,
                mimeType = match.groupValues.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() },
                fileName = path.mediaReferenceFileName()
            )
        }
    }.toList()
    val legacyRanges = mediaAttachmentReferenceRegex.findAll(normalized).map { it.range }.toList()
    val compactMatches = compactMediaAttachmentReferenceRegex.findAll(normalized)
        .filterNot { match -> legacyRanges.any { range -> match.range.first <= range.last && range.first <= match.range.last } }
        .mapNotNull { match ->
            match.groupValues.getOrNull(1)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { path ->
                    ChatMediaAttachmentReference(
                        path = path,
                        mimeType = null,
                        fileName = path.mediaReferenceFileName()
                    )
                }
        }
        .toList()
    return legacyMatches + compactMatches
}

internal data class ChatMediaAttachmentReference(
    val path: String,
    val mimeType: String?,
    val fileName: String
)

private fun String.mediaReferenceFileName(): String {
    return substringAfterLast('/')
        .substringAfterLast('\\')
        .trim()
        .takeIf { it.isNotEmpty() }
        ?: "attachment"
}

internal fun sanitizeChatDisplayText(text: String): String {
    return sanitizeChatMessageText(text).ifBlank { "attachment" }
}
