package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock

private const val mediaPromptMergeWindowSeconds = 600.0
private const val plainDuplicateWindowSeconds = 180.0
private const val internalContinuationMarker =
    "The previous attempt did not produce a user-visible answer. Continue from the current state and produce the visible answer now. Do not restart from scratch."

internal fun normalizeChatTimelineMessages(messages: List<ChatMessage>): List<ChatMessage> {
    val materialized = messages.map(::materializedMediaReferences)
    val coalesced = coalescedMediaMessages(materialized)
    return coalesced
        .map(::normalizedInternalContinuationPrompt)
        .filterNot(::isInternalVisionContextToolMessage)
}

private fun materializedMediaReferences(message: ChatMessage): ChatMessage {
    val references = mediaReferences(message.content)
    if (references.isEmpty()) {
        val normalizedBlocks = normalizedMediaBlocks(message.contentBlocks)
        return if (normalizedBlocks.size == message.contentBlocks.size) {
            message
        } else {
            message.copy(contentBlocks = normalizedBlocks)
        }
    }

    val cleanedContent = sanitizeChatMessageText(message.content)
    val mergedBlocks = mergedMediaBlocks(
        existingBlocks = message.contentBlocks,
        candidateBlocks = references.map { reference ->
            RelayChatContentBlock(
                type = if (reference.mimeType.startsWith("image/", ignoreCase = true)) "image" else "file",
                text = reference.fileName,
                name = reference.fileName,
                fileName = reference.fileName,
                mimeType = reference.mimeType,
                downloadUrl = reference.path
            )
        }
    )
    return message.copy(
        content = cleanedContent.ifBlank { message.content },
        contentBlocks = mergedBlocks
    )
}

private fun coalescedMediaMessages(messages: List<ChatMessage>): List<ChatMessage> {
    val coalesced = mutableListOf<ChatMessage>()

    messages.forEach { message ->
        if (isUserMediaPrompt(message)) {
            val duplicatePromptIndex = previousEquivalentMediaPromptIndex(message, coalesced)
            if (duplicatePromptIndex >= 0) {
                coalesced[duplicatePromptIndex] = coalesced[duplicatePromptIndex].mergingMediaFrom(message)
                return@forEach
            }

            val matchingPromptIndex = nearbyMatchingUserPromptIndex(message, coalesced)
            if (matchingPromptIndex >= 0) {
                coalesced[matchingPromptIndex] = coalesced[matchingPromptIndex].mergingPromptAndMediaFrom(message)
                return@forEach
            }
        }

        if (isStandaloneUserMediaMessage(message)) {
            val duplicateMediaIndex = previousEquivalentMediaIndex(message, coalesced)
            if (duplicateMediaIndex >= 0) {
                coalesced[duplicateMediaIndex] = coalesced[duplicateMediaIndex].mergingMediaFrom(message)
                return@forEach
            }

            val promptIndex = nearbyUserPromptForStandaloneMedia(message, coalesced)
            if (promptIndex >= 0) {
                coalesced[promptIndex] = coalesced[promptIndex].mergingMediaFrom(message)
                return@forEach
            }
        }

        val mediaPromptIndex = nearbyMediaPromptIndexForPlainUserPrompt(message, coalesced)
        if (mediaPromptIndex >= 0) {
            coalesced[mediaPromptIndex] = coalesced[mediaPromptIndex].mergingPromptTextFrom(message)
            return@forEach
        }
        if (isInternalContinuationDuplicateOfNearbyPrompt(message, coalesced)) return@forEach
        if (isDuplicateFileTransferStatusText(message, coalesced)) return@forEach
        if (isDuplicateUserMessageInSameTurn(message, coalesced)) return@forEach

        coalesced += message
    }

    return coalesced
}

private fun isDuplicateUserMessageInSameTurn(message: ChatMessage, messages: List<ChatMessage>): Boolean {
    if (message.role != MessageRole.user ||
        message.state != MessageState.completed ||
        message.hasFileContent ||
        message.hasVoiceContent
    ) {
        return false
    }
    val normalized = normalizedPromptText(message.content)
    if (normalized.isBlank()) return false

    val previousUser = messages.asReversed().firstOrNull { candidate ->
        candidate.role == MessageRole.user || isRenderableAssistantBoundary(candidate)
    } ?: return false
    if (previousUser.role != MessageRole.user ||
        previousUser.hasFileContent ||
        previousUser.hasVoiceContent
    ) {
        return false
    }
    return normalizedPromptText(previousUser.content) == normalized
}

private fun isRenderableAssistantBoundary(message: ChatMessage): Boolean {
    return message.role == MessageRole.assistant &&
        !message.hasToolContent &&
        !isTransientAssistantPlaceholder(message) &&
        message.plainTextContent.trim().isNotEmpty()
}

private fun nearbyMatchingUserPromptIndex(message: ChatMessage, messages: List<ChatMessage>): Int {
    val normalizedPrompt = normalizedPromptText(message.content)
    if (normalizedPrompt.isBlank()) return -1

    return messages.indices.reversed().firstOrNull { index ->
        val existing = messages[index]
        existing.role == MessageRole.user &&
            !existing.hasFileContent &&
            !existing.hasVoiceContent &&
            timestampsAreClose(existing, message, mediaPromptMergeWindowSeconds) &&
            normalizedPromptText(existing.content) == normalizedPrompt
    } ?: -1
}

private fun previousEquivalentMediaPromptIndex(message: ChatMessage, messages: List<ChatMessage>): Int {
    val normalizedPrompt = normalizedPromptText(message.content)
    if (normalizedPrompt.isBlank() || message.fileContentBlocks.isEmpty()) return -1

    return messages.indices.reversed().firstOrNull { index ->
        val existing = messages[index]
        existing.role == MessageRole.user &&
            existing.hasFileContent &&
            normalizedPromptText(existing.content) == normalizedPrompt &&
            fileBlocksOverlap(existing.fileContentBlocks, message.fileContentBlocks)
    } ?: -1
}

private fun previousEquivalentMediaIndex(message: ChatMessage, messages: List<ChatMessage>): Int {
    val fileBlocks = message.fileContentBlocks
    if (fileBlocks.isEmpty()) return -1

    return messages.indices.reversed().firstOrNull { index ->
        val existing = messages[index]
        existing.role == MessageRole.user &&
            existing.hasFileContent &&
            fileBlocksOverlap(existing.fileContentBlocks, fileBlocks)
    } ?: -1
}

private fun nearbyUserPromptForStandaloneMedia(message: ChatMessage, messages: List<ChatMessage>): Int {
    return messages.indices.reversed().firstOrNull { index ->
        val existing = messages[index]
        existing.role == MessageRole.user &&
            timestampsAreClose(existing, message, mediaPromptMergeWindowSeconds) &&
            (isUserMediaPrompt(existing) || promptMentionsMedia(existing.content))
    } ?: -1
}

private fun nearbyMediaPromptIndexForPlainUserPrompt(message: ChatMessage, messages: List<ChatMessage>): Int {
    if (message.role != MessageRole.user ||
        message.state != MessageState.completed ||
        message.hasFileContent ||
        message.hasVoiceContent
    ) {
        return -1
    }

    val normalizedPrompt = normalizedPromptText(message.content)
    if (normalizedPrompt.isBlank()) return -1

    return messages.indices.reversed().firstOrNull { index ->
        val existing = messages[index]
        val existingPrompt = normalizedPromptText(existing.content)
        timestampsAreClose(existing, message, plainDuplicateWindowSeconds) &&
            existing.role == MessageRole.user &&
            isUserMediaPrompt(existing) &&
            (existingPrompt == normalizedPrompt ||
                promptMentionsMedia(message.content) && isStandaloneUserMediaMessage(existing))
    } ?: -1
}

private fun isInternalContinuationDuplicateOfNearbyPrompt(message: ChatMessage, messages: List<ChatMessage>): Boolean {
    if (message.role != MessageRole.user ||
        message.state != MessageState.completed ||
        message.hasFileContent ||
        message.hasVoiceContent ||
        !message.content.contains(internalContinuationMarker)
    ) {
        return false
    }

    val normalizedPrompt = normalizedPromptText(stripInternalContinuationMarker(message.content))
    if (normalizedPrompt.isBlank()) return false

    return messages.asReversed().any { existing ->
        timestampsAreClose(existing, message, plainDuplicateWindowSeconds) &&
            existing.role == MessageRole.user &&
            !existing.hasFileContent &&
            !existing.hasVoiceContent &&
            normalizedPromptText(existing.content) == normalizedPrompt
        }
}

private fun isDuplicateFileTransferStatusText(message: ChatMessage, messages: List<ChatMessage>): Boolean {
    val statusText = normalizedFileTransferStatusText(message) ?: return false
    return messages.asReversed().any { existing ->
        val existingStatusText = normalizedFileTransferStatusText(existing) ?: return@any false
        existingStatusText == statusText && fileTransferStatusTimesMatch(existing, message)
    }
}

private fun normalizedFileTransferStatusText(message: ChatMessage): String? {
    if (message.role != MessageRole.assistant ||
        message.state != MessageState.completed ||
        message.hasFileContent ||
        message.hasVoiceContent ||
        message.hasToolContent
    ) {
        return null
    }

    val normalized = normalizedPromptText(message.plainTextContent)
    if (normalized.isBlank()) return null
    val looksLikeSentStatus =
        (normalized.startsWith("已发") || normalized.startsWith("sent")) &&
            normalized.contains("completed") &&
            (normalized.contains("尺寸") || normalized.contains("size"))
    return normalized.takeIf { looksLikeSentStatus }
}

private fun fileTransferStatusTimesMatch(existing: ChatMessage, candidate: ChatMessage): Boolean {
    val existingCreatedAt = existing.createdAt.trim()
    val candidateCreatedAt = candidate.createdAt.trim()
    if (existingCreatedAt.isNotEmpty() && existingCreatedAt == candidateCreatedAt) {
        return true
    }
    return timestampsAreClose(existing, candidate, plainDuplicateWindowSeconds)
}

private fun ChatMessage.mergingPromptAndMediaFrom(candidate: ChatMessage): ChatMessage {
    return copy(
        content = content.ifBlank { candidate.content },
        contentBlocks = mergedMediaBlocks(contentBlocks, candidate.contentBlocks)
    )
}

private fun ChatMessage.mergingMediaFrom(mediaMessage: ChatMessage): ChatMessage {
    return copy(
        contentBlocks = mergedMediaBlocks(contentBlocks, mediaMessage.fileContentBlocks)
    )
}

private fun ChatMessage.mergingPromptTextFrom(promptMessage: ChatMessage): ChatMessage {
    val prompt = promptMessage.content.trim()
    if (prompt.isBlank()) return this

    val currentPrompt = normalizedPromptText(content)
    val fileNames = fileContentBlocks
        .mapNotNull { it.fileDisplayName ?: it.text }
        .map(::normalizedPromptText)
    val shouldReplaceCurrentContent = currentPrompt.isBlank() ||
        currentPrompt in fileNames ||
        isStandaloneUserMediaMessage(this)

    return if (shouldReplaceCurrentContent) copy(content = prompt) else this
}

private fun isUserMediaPrompt(message: ChatMessage): Boolean {
    return message.role == MessageRole.user && message.hasFileContent
}

private fun isStandaloneUserMediaMessage(message: ChatMessage): Boolean {
    if (message.role != MessageRole.user ||
        message.state != MessageState.completed ||
        message.fileContentBlocks.isEmpty()
    ) {
        return false
    }

    val normalizedContent = normalizedPromptText(message.content)
    val fileNames = message.fileContentBlocks
        .mapNotNull { it.fileDisplayName ?: it.text }
        .map(::normalizedPromptText)
    return normalizedContent.isBlank() ||
        normalizedContent in fileNames ||
        message.runId.startsWith("file-")
}

private fun mergedMediaBlocks(
    existingBlocks: List<RelayChatContentBlock>,
    candidateBlocks: List<RelayChatContentBlock>
): List<RelayChatContentBlock> {
    val merged = normalizedMediaBlocks(existingBlocks).toMutableList()
    candidateBlocks.filter { it.isFileBlock }.forEach { block ->
        val existingIndex = merged.indexOfFirst { mediaBlocksReferToSameFile(it, block) }
        if (existingIndex >= 0) {
            if (mediaBlockQuality(block) > mediaBlockQuality(merged[existingIndex])) {
                merged[existingIndex] = block
            }
        } else {
            merged += block
        }
    }
    return merged
}

private fun normalizedMediaBlocks(blocks: List<RelayChatContentBlock>): List<RelayChatContentBlock> {
    val normalized = mutableListOf<RelayChatContentBlock>()
    blocks.forEach { block ->
        if (!block.isFileBlock) {
            normalized += block
            return@forEach
        }
        val existingIndex = normalized.indexOfFirst { mediaBlocksReferToSameFile(it, block) }
        if (existingIndex >= 0) {
            if (mediaBlockQuality(block) > mediaBlockQuality(normalized[existingIndex])) {
                normalized[existingIndex] = block
            }
        } else {
            normalized += block
        }
    }
    return normalized
}

private fun fileBlocksOverlap(
    lhs: List<RelayChatContentBlock>,
    rhs: List<RelayChatContentBlock>
): Boolean {
    return lhs.any { left -> rhs.any { right -> mediaBlocksReferToSameFile(left, right) } }
}

private fun mediaBlocksReferToSameFile(
    lhs: RelayChatContentBlock,
    rhs: RelayChatContentBlock
): Boolean {
    val leftId = lhs.fileId?.trim().orEmpty()
    val rightId = rhs.fileId?.trim().orEmpty()
    if (leftId.isNotBlank() && leftId == rightId) return true

    val leftName = lhs.fileDisplayName?.trim()?.lowercase().orEmpty()
    val rightName = rhs.fileDisplayName?.trim()?.lowercase().orEmpty()
    if (leftName.isNotBlank() && leftName == rightName) return true

    val leftStem = stableMediaFileStem(lhs)
    val rightStem = stableMediaFileStem(rhs)
    return leftStem.isNotBlank() && leftStem == rightStem
}

private fun mediaBlockQuality(block: RelayChatContentBlock): Int {
    var score = 0
    val mimeType = block.mimeType?.trim()?.lowercase().orEmpty()
    val fileId = block.fileId?.trim().orEmpty()
    val downloadUrl = block.fileDownloadURLString?.trim().orEmpty()

    if (mimeType.startsWith("image/")) {
        score += 3
    } else if (mimeType.isNotBlank() && mimeType != "application/octet-stream") {
        score += 1
    }
    if (fileId.isNotBlank()) score += 4
    if (downloadUrl.startsWith("/api/") ||
        downloadUrl.startsWith("http://") ||
        downloadUrl.startsWith("https://")
    ) {
        score += 4
    } else if (downloadUrl.startsWith("file://")) {
        score += 3
    } else if (downloadUrl.startsWith("/")) {
        score += 1
    } else if (downloadUrl.startsWith("media://")) {
        score -= 2
    }

    return score
}

private fun stableMediaFileStem(block: RelayChatContentBlock): String {
    val rawName = block.fileDisplayName ?: block.fileDownloadURLString ?: ""
    val name = rawName.substringAfterLast('/').substringAfterLast('\\').trim()
    val stem = name.substringBeforeLast('.', name).lowercase()
    return stem.substringBefore("---")
}

private fun normalizedInternalContinuationPrompt(message: ChatMessage): ChatMessage {
    if (message.role != MessageRole.user) return message
    val cleanedContent = stripInternalContinuationMarker(message.content)
    return if (cleanedContent == message.content) message else message.copy(content = cleanedContent)
}

private fun stripInternalContinuationMarker(text: String): String {
    val index = text.indexOf(internalContinuationMarker)
    if (index < 0) return text
    return text.substring(0, index).trim()
}

private fun isInternalVisionContextToolMessage(message: ChatMessage): Boolean {
    if (!message.hasToolContent) return false

    val text = (listOf(message.content) + message.toolContentBlocks.mapNotNull { it.text })
        .joinToString("\n")
        .trim()
        .lowercase()
    if (text.isBlank()) return false

    return text.contains("image loaded into your context") &&
        text.contains("use your built-in vision")
}

private fun mediaReferences(text: String): List<MediaReference> {
    return chatMediaAttachmentReferences(text).map { reference ->
        MediaReference(
            path = reference.path,
            mimeType = reference.mimeType ?: inferMediaReferenceMimeType(reference.fileName),
            fileName = reference.fileName
        )
    }
}

private fun inferMediaReferenceMimeType(fileName: String): String {
    val lower = fileName.lowercase()
    return when {
        lower.endsWith(".png") -> "image/png"
        lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
        lower.endsWith(".heic") || lower.endsWith(".heif") -> "image/heic"
        lower.endsWith(".gif") -> "image/gif"
        lower.endsWith(".webp") -> "image/webp"
        lower.endsWith(".m4a") -> "audio/mp4"
        lower.endsWith(".mp3") -> "audio/mpeg"
        lower.endsWith(".wav") -> "audio/wav"
        else -> "application/octet-stream"
    }
}

private fun timestampsAreClose(
    left: ChatMessage,
    right: ChatMessage,
    maxDelta: Double
): Boolean {
    val leftTimestamp = left.sortTimestamp ?: return true
    val rightTimestamp = right.sortTimestamp ?: return true
    return kotlin.math.abs(leftTimestamp - rightTimestamp) < maxDelta
}

private fun promptMentionsMedia(text: String): Boolean {
    val normalized = normalizedPromptText(text)
    return normalized.contains("图片") ||
        normalized.contains("照片") ||
        normalized.contains("图") ||
        normalized.contains("image") ||
        normalized.contains("photo")
}

private fun normalizedPromptText(text: String): String {
    return sanitizeChatMessageText(text)
        .split(Regex("[\\s\\u2000-\\u200A\\u202F\\u205F\\u3000]+"))
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .lowercase()
}

private data class MediaReference(
    val path: String,
    val mimeType: String,
    val fileName: String
)
