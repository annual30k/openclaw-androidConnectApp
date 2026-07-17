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
        return completed.copy(
            timelineStableKey = completed.timelineStableKey.ifBlank { existing.timelineStableKey },
            timelineMessageId = completed.timelineMessageId.ifBlank { existing.timelineMessageId },
            timelinePartId = completed.timelinePartId.ifBlank { existing.timelinePartId },
            timelineOrderKey = completed.timelineOrderKey.ifBlank { existing.timelineOrderKey },
            timelineIdentityKey = completed.timelineIdentityKey.ifBlank { existing.timelineIdentityKey },
            timelineItemKind = completed.timelineItemKind.ifBlank { existing.timelineItemKind },
            timelineResolvesWaiting = completed.timelineResolvesWaiting ?: existing.timelineResolvesWaiting,
            source = completed.source.ifBlank { existing.source }
        )
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

    // 完成态副本经常比本地占位先到或后到；只要它没有给出更稳定的 timeline 身份字段，就必须继承本地键，
    // 否则一次普通的上传进度/文件回显合并就会把可恢复的 local timeline key 覆盖成空值。
    return completed.copy(
        contentBlocks = mergedBlocks,
        timelineStableKey = completed.timelineStableKey.ifBlank { existing.timelineStableKey },
        timelineMessageId = completed.timelineMessageId.ifBlank { existing.timelineMessageId },
        timelinePartId = completed.timelinePartId.ifBlank { existing.timelinePartId },
        timelineOrderKey = completed.timelineOrderKey.ifBlank { existing.timelineOrderKey },
        timelineIdentityKey = completed.timelineIdentityKey.ifBlank { existing.timelineIdentityKey },
        timelineItemKind = completed.timelineItemKind.ifBlank { existing.timelineItemKind },
        timelineResolvesWaiting = completed.timelineResolvesWaiting ?: existing.timelineResolvesWaiting,
        source = completed.source.ifBlank { existing.source }
    )
}

private fun isLocalPreviewReference(value: String): Boolean {
    return value.startsWith("file://", ignoreCase = true) ||
        value.startsWith("content://", ignoreCase = true) ||
        (value.startsWith("/") && !value.startsWith("/api/", ignoreCase = true)) ||
        File(value).exists()
}

private fun sameTransferIdentity(left: RelayChatContentBlock, right: RelayChatContentBlock): Boolean {
    val leftAttachmentId = left.attachmentId?.trim()?.takeIf { it.isNotEmpty() }
    val rightAttachmentId = right.attachmentId?.trim()?.takeIf { it.isNotEmpty() }
    val bothAttachmentIdsPresent = leftAttachmentId != null && rightAttachmentId != null
    if (bothAttachmentIdsPresent && leftAttachmentId == rightAttachmentId) return true

    val leftFileId = left.fileId?.trim()?.takeIf { it.isNotEmpty() }
    val rightFileId = right.fileId?.trim()?.takeIf { it.isNotEmpty() }
    if (leftFileId != null && rightFileId != null) {
        if (leftFileId == rightFileId) return true
        return false
    }
    if (bothAttachmentIdsPresent) return false

    // 这里只用于同一条本地 user 消息与服务端确认消息之间的块级预览保留，
    // 不是跨消息去重；显式 identity 缺失时，允许回退到同文件元数据匹配。
    val leftName = left.fileDisplayName?.trim()?.lowercase().orEmpty()
    val rightName = right.fileDisplayName?.trim()?.lowercase().orEmpty()
    if (leftName.isBlank() || leftName != rightName) return false

    val leftMime = left.mimeType?.trim()?.lowercase().orEmpty()
    val rightMime = right.mimeType?.trim()?.lowercase().orEmpty()
    if (!attachmentMimeTypesCompatible(leftMime, rightMime)) return false

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
    downloadUrlString: String,
    sourceRunId: String? = null
): RelayChatContentBlock {
    return RelayChatContentBlock(
        type = when {
            attachment.mimeType.trim().lowercase().startsWith("audio/") -> "voice"
            attachment.mimeType.trim().lowercase().startsWith("image/") -> "image"
            else -> "file"
        },
        text = attachment.fileName,
        name = attachment.fileName,
        attachmentId = attachment.id,
        fileName = attachment.fileName,
        mimeType = attachment.mimeType,
        sizeBytes = attachment.sizeBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        durationMs = attachment.durationMs?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt(),
        imageWidth = attachment.imageWidth,
        imageHeight = attachment.imageHeight,
        downloadUrl = downloadUrlString,
        senderDisplayName = senderDisplayName,
        sourceRunId = sourceRunId,
        gatewayId = gatewayId,
        sessionKey = sessionKey,
        status = statusText
    )
}

internal fun makeFileContentBlock(
    record: RelayFileTransferItem,
    sourceRunIdOverride: String? = null,
    attachmentIdOverride: String? = null
): RelayChatContentBlock {
    val normalizedMime = record.mimeType.trim().lowercase()
    val sourceRunId = record.sourceRunId?.trim()?.takeIf { it.isNotEmpty() }
        ?: sourceRunIdOverride?.trim()?.takeIf { it.isNotEmpty() }
    val attachmentId = attachmentIdOverride?.trim()?.takeIf { it.isNotEmpty() }
    return RelayChatContentBlock(
        type = when {
            normalizedMime.startsWith("audio/") -> "voice"
            normalizedMime.startsWith("image/") -> "image"
            else -> "file"
        },
        text = record.fileName,
        name = record.fileName,
        attachmentId = attachmentId,
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
        sourceRunId = sourceRunId,
        gatewayId = record.gatewayId,
        sessionKey = record.sessionKey,
        status = record.status
    )
}

fun makeUploadedAttachmentContentBlock(
    record: RelayFileTransferItem,
    localDownloadUrlString: String? = null,
    sourceRunIdOverride: String? = null,
    attachmentIdOverride: String? = null
): RelayChatContentBlock {
    val block = makeFileContentBlock(
        record,
        sourceRunIdOverride = sourceRunIdOverride,
        attachmentIdOverride = attachmentIdOverride
    )
    val localPreviewPath = localDownloadUrlString
        ?.trim()
        ?.takeIf { it.isNotEmpty() && isLocalPreviewReference(it) }
        ?: return block
    return block.copy(
        downloadUrl = localPreviewPath,
        downloadPath = block.fileDownloadURLString
    )
}

internal fun contentBlocksWithOutgoingSourceRunId(
    blocks: List<RelayChatContentBlock>,
    sourceRunId: String
): List<RelayChatContentBlock> {
    val normalizedSourceRunId = sourceRunId.trim().takeIf { it.isNotEmpty() } ?: return blocks
    var changed = false
    val updated = blocks.map { block ->
        if ((block.isFileBlock || block.isVoiceMessageBlock) && block.sourceRunId.isNullOrBlank()) {
            changed = true
            block.copy(sourceRunId = normalizedSourceRunId)
        } else {
            block
        }
    }
    return if (changed) updated else blocks
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
    "(^|\\n)[ \\t]*\\[Hermes runtime context][\\s\\S]*?(?=\\n[ \\t]*\\[ClawConnect mobile bridge]|$)"
)
private val mediaAttachmentReferenceRegex = Regex(
    "(?m)[ \\t]*\\[media attached:\\s*(.+?)\\s*\\((.+?)\\)\\s*\\|\\s*(.+?)][ \\t]*"
)
private val compactMediaAttachmentReferenceRegex = Regex(
    "(?m)[ \\t]*\\[media attached:\\s*([^\\]\\n]+)][ \\t]*"
)
private val openClawMediaControlReferenceRegex = Regex(
    "(?m)^[ \\t]*MEDIA:\\s*(?:file://|~/|/)[^\\n]*(?:\\n|$)",
    RegexOption.IGNORE_CASE
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
    val withoutInternalHints = normalized
        .replace(ansiEscapeRegex, "")
        .replace(hermesRuntimeContextRegex, "$1")
        .replace(mediaAttachmentReferenceRegex, "")
        .replace(compactMediaAttachmentReferenceRegex, "")
        .replace(openClawMediaControlReferenceRegex, "")
        .replace(fileAttachmentReferenceRegex, "")
        .replace(mobileBridgeTimestampPrefixRegex, "")
        .replace("\u001B", "")
    val bridgeIndex = withoutInternalHints.indexOf("[ClawConnect mobile bridge]")
    return (if (bridgeIndex >= 0) withoutInternalHints.take(bridgeIndex) else withoutInternalHints)
        .replace(excessiveBlankLinesRegex, "\n\n")
        .trim()
}

internal fun sanitizeChatContentBlocks(blocks: List<RelayChatContentBlock>): List<RelayChatContentBlock> {
    val sanitized = blocks.map { block ->
        val text = block.text ?: return@map block
        block.copy(text = sanitizeChatMessageText(text))
    }
    if (sanitized.none { it.isFileBlock || it.isVoiceMessageBlock }) return sanitized

    val canonicalText = sanitized.mapNotNull { block ->
        if (!block.isTextBlock || block.contentBlockId.isNullOrBlank()) return@mapNotNull null
        block.text?.trim()?.takeIf(String::isNotEmpty)
    }.toSet()
    if (canonicalText.isEmpty()) return sanitized

    return sanitized.filterNot { block ->
        block.isTextBlock &&
            block.contentBlockId.isNullOrBlank() &&
            block.text?.trim()?.let(canonicalText::contains) == true
    }
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
