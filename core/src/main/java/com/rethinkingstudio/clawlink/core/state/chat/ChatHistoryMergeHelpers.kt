package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import com.rethinkingstudio.clawlink.core.network.dto.ChatHistoryItem
import java.time.Instant
import java.time.format.DateTimeParseException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val messageOrderEpsilon = 0.001
private const val historyTranscriptOrderWindowSeconds = 900.0
private const val sameTurnWindowSeconds = 180.0
private const val sameTurnClockSkewToleranceSeconds = 15.0
private val protocolTypingMarkerRegex = Regex("^(?:\\[\\[clawlink:typing]]\\s*)+$")

internal fun extractContent(item: ChatHistoryItem): String {
    return sanitizeChatMessageText(extractRawContent(item))
}

private fun extractRawContent(item: ChatHistoryItem): String {
    val content = item.content ?: return ""
    return when {
        content is JsonPrimitive && content.isString -> content.content
        content is JsonArray -> content.mapNotNull { element ->
            (element as? JsonObject)?.let { block ->
                block.string("text")
                    ?: block["content"]?.let { nested ->
                        when {
                            nested is JsonPrimitive && nested.isString -> nested.content
                            else -> null
                        }
                    }
            }
        }.joinToString("\n\n").trim()
        else -> try {
            content.jsonObject["text"]?.jsonPrimitive?.content ?: ""
        } catch (_: Exception) {
            ""
        }
    }
}

internal fun normalizedMessageRole(role: String): String {
    return role.trim().lowercase().replace("_", "")
}

internal fun messageRole(role: String): MessageRole {
    return when (normalizedMessageRole(role)) {
        "user" -> MessageRole.user
        "assistant" -> MessageRole.assistant
        "system" -> MessageRole.system
        "tool", "toolresult" -> MessageRole.tool
        else -> MessageRole.assistant
    }
}

internal fun parseHistoryTimestamp(raw: String?): Double? {
    val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return try {
        Instant.parse(trimmed).toEpochMilli() / 1000.0
    } catch (_: DateTimeParseException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}

internal fun buildNotificationPreview(content: String, contentBlocks: List<RelayChatContentBlock>): String {
    val plainText = sanitizeChatMessageText(content).ifBlank {
        contentBlocks.firstNotNullOfOrNull { block ->
            block.text?.trim()?.takeIf { it.isNotBlank() }
                ?: block.transcript?.trim()?.takeIf { it.isNotBlank() }
        } ?: ""
    }
    if (plainText.isBlank()) return ""
    if (isProtocolTypingMarkerText(plainText)) return ""
    return plainText.replace(Regex("\\s+"), " ").take(140)
}

internal fun isProtocolTypingMarkerText(text: String): Boolean {
    val trimmed = text.trim()
    return trimmed.isNotEmpty() && protocolTypingMarkerRegex.matches(trimmed)
}

internal fun buildHistoryMessagesFromItems(items: List<ChatHistoryItem>): List<ChatMessage> {
    var previousSortTimestamp: Double? = null
    val mediaReferenceFileSortAnchors = mutableMapOf<String, Double>()
    return items.mapIndexedNotNull { index, item ->
        val rawExtractedContent = extractRawContent(item)
        val extractedContent = sanitizeChatMessageText(rawExtractedContent)
        val sourceBlocks = item.contentBlocks ?: emptyList()
        if (sourceBlocks.isEmpty() && isProtocolTypingMarkerText(extractedContent)) {
            return@mapIndexedNotNull null
        }
        val normalizedRole = normalizedMessageRole(item.role)
        val isToolRole = normalizedRole in listOf("tool", "toolresult")
        val isToolHistory = isToolRole || sourceBlocks.any { it.isToolCallBlock || it.isToolResultBlock }
        val role = if (isToolHistory) {
            MessageRole.tool
        } else {
            messageRole(item.role)
        }
        val content = when {
            sourceBlocks.isNotEmpty() && role == MessageRole.tool -> sourceBlocks.firstNotNullOfOrNull { block ->
                block.text?.trim()?.takeIf { it.isNotEmpty() }
                    ?: block.result?.renderedText(listOf("content", "markdown", "text", "body", "message", "value", "result", "output"))
                    ?: block.partialResult?.renderedText(listOf("content", "markdown", "text", "body", "message", "value", "result", "output"))
                    ?: block.content?.renderedText(listOf("content", "markdown", "text", "body", "message", "value", "result", "output"))
                    ?: block.output?.renderedText(listOf("content", "markdown", "text", "body", "message", "value", "result", "output"))
                    ?: block.error?.renderedText(listOf("content", "markdown", "text", "body", "message", "value", "result", "output"))
            }.orEmpty()
            else -> extractedContent
        }
        val rawSortTimestamp = parseHistoryTimestamp(item.createdAt)
        val syntheticFloor = previousSortTimestamp?.plus(messageOrderEpsilon)
            ?: (index * messageOrderEpsilon)
        val baseSortTimestamp = historySortTimestamp(
            rawSortTimestamp = rawSortTimestamp,
            previousSortTimestamp = previousSortTimestamp,
            syntheticFloor = syntheticFloor
        )
        val sortTimestamp = mediaReferenceFileSortAnchor(
            role = role,
            sourceBlocks = sourceBlocks,
            anchorsByFileName = mediaReferenceFileSortAnchors
        )?.let { anchor -> minOf(baseSortTimestamp, anchor) } ?: baseSortTimestamp
        previousSortTimestamp = maxOf(previousSortTimestamp ?: sortTimestamp, sortTimestamp)
        if (role == MessageRole.user && extractedContent.isNotBlank()) {
            val referencedFileNames = chatMediaAttachmentReferenceFileNames(rawExtractedContent)
            referencedFileNames.forEachIndexed { referenceIndex, fileName ->
                val anchorSortTimestamp = sortTimestamp -
                    ((referencedFileNames.size - referenceIndex) * messageOrderEpsilon)
                mediaReferenceFileSortAnchors[normalizeReferencedFileName(fileName)] = anchorSortTimestamp
            }
        }

        ChatMessage(
            id = item.id,
            role = role,
            content = content,
            contentBlocks = sourceBlocks,
            createdAt = item.createdAt ?: "",
            runId = item.id,
            sortTimestamp = sortTimestamp
        )
    }
}

private fun mediaReferenceFileSortAnchor(
    role: MessageRole,
    sourceBlocks: List<RelayChatContentBlock>,
    anchorsByFileName: Map<String, Double>
): Double? {
    if (role != MessageRole.user || sourceBlocks.isEmpty()) return null
    return sourceBlocks.firstNotNullOfOrNull { block ->
        val fileName = block.fileDisplayName?.trim()?.takeIf { it.isNotEmpty() }
            ?: block.text?.trim()?.takeIf { it.isNotEmpty() }
        fileName?.let { anchorsByFileName[normalizeReferencedFileName(it)] }
    }
}

private fun normalizeReferencedFileName(fileName: String): String {
    return fileName.trim().lowercase()
}

private fun historySortTimestamp(
    rawSortTimestamp: Double?,
    previousSortTimestamp: Double?,
    syntheticFloor: Double
): Double {
    val raw = rawSortTimestamp ?: return syntheticFloor
    val previous = previousSortTimestamp ?: return raw
    if (raw >= syntheticFloor) return raw

    val backwardJumpSeconds = previous - raw
    return if (backwardJumpSeconds <= historyTranscriptOrderWindowSeconds) {
        syntheticFloor
    } else {
        raw
    }
}

internal fun mergeHistoryWithCurrentMessages(
    historyMessages: List<ChatMessage>,
    currentMessages: List<ChatMessage>,
    currentStreamingMessageId: String?,
    isTrackedPendingAssistantMessageId: (String) -> Boolean
): List<ChatMessage> {
    if (currentMessages.isEmpty()) return orderMessagesWithSourceRunAnchors(historyMessages)

    val merged = historyMessages.toMutableList()
    val resolvedPendingAssistantMessageIds = resolvedPendingAssistantMessageIds(
        historyMessages = historyMessages,
        currentMessages = currentMessages,
        currentStreamingMessageId = currentStreamingMessageId,
        isTrackedPendingAssistantMessageId = isTrackedPendingAssistantMessageId
    )
    currentMessages.forEachIndexed { index, message ->
        if (!shouldPreserveCurrentMessageAcrossHistoryRefresh(
                message = message,
                currentStreamingMessageId = currentStreamingMessageId,
                isTrackedPendingAssistantMessageId = isTrackedPendingAssistantMessageId
            )
        ) {
            return@forEachIndexed
        }
        if (message.id in resolvedPendingAssistantMessageIds) {
            return@forEachIndexed
        }

        if (message.role == MessageRole.user && message.runId.startsWith("local-user-") && message.hasVoiceContent) {
            val transcriptIndex = localVoiceTranscriptHistoryIndex(merged, message)
            if (transcriptIndex >= 0) {
                merged[transcriptIndex] = localVoiceMessageReplacingTranscript(
                    localVoiceMessage = message,
                    transcriptMessage = merged[transcriptIndex]
                )
                return@forEachIndexed
            }
        }

        if (message.role == MessageRole.user && message.runId.startsWith("local-user-")) {
            val historyEchoIndex = historyUserIndexMatchingLocalUserEcho(merged, message)
            if (historyEchoIndex >= 0) {
                merged[historyEchoIndex] = localUserMessageReplacingHistoryUser(
                    localUserMessage = message,
                    historyUserMessage = merged[historyEchoIndex]
                )
                return@forEachIndexed
            }
        }

        val matchIndex = merged.indexOfFirst { existing ->
            (existing.runId.isNotBlank() && existing.runId == message.runId) || sameFileMessage(existing, message)
        }

        if (matchIndex >= 0) {
            merged[matchIndex] = mergeFileMessage(existing = merged[matchIndex], candidate = message)
            return@forEachIndexed
        }

        val matchingIndices = merged.indices.filter { mergedIndex ->
            isSameChatMessageInstance(merged[mergedIndex], message)
        }
        val sameContentCurrentCount = currentMessages
            .take(index + 1)
            .count { isSameChatMessageInstance(it, message) }

        if (matchingIndices.count() >= sameContentCurrentCount) {
            return@forEachIndexed
        }

        merged.add(message)
    }

    return orderMessagesWithSourceRunAnchors(merged)
}

internal fun orderMessagesWithSourceRunAnchors(messages: List<ChatMessage>): List<ChatMessage> {
    val anchoredMessages = messages.map { message ->
        anchorAssistantFileMessageToSourceRun(message, messages)
    }
    return anchoredMessages.sortedWith(
        compareBy<ChatMessage> { it.sortTimestamp ?: Double.MAX_VALUE }
            .thenBy { it.createdAt }
    )
}

internal fun anchorAssistantFileMessageToSourceRun(
    message: ChatMessage,
    messages: List<ChatMessage>
): ChatMessage {
    if (message.role != MessageRole.assistant || !message.hasFileContent) {
        return message
    }
    val sourceRunId = message.transferContentBlocks()
        .firstNotNullOfOrNull { it.sourceRunId?.trim()?.takeIf { value -> value.isNotEmpty() } }
        ?: return message
    val anchorTimestamp = messages
        .filter { it.role == MessageRole.assistant && it.runId == sourceRunId }
        .mapNotNull { it.sortTimestamp }
        .maxOrNull()
        ?: return message
    val minimumSortTimestamp = anchorTimestamp + messageOrderEpsilon
    val messageSortTimestamp = message.sortTimestamp
    if (messageSortTimestamp != null && messageSortTimestamp >= minimumSortTimestamp) {
        return message
    }
    return message.copy(
        createdAt = Instant.ofEpochMilli((minimumSortTimestamp * 1000).toLong()).toString(),
        sortTimestamp = minimumSortTimestamp
    )
}

private fun localVoiceTranscriptHistoryIndex(messages: List<ChatMessage>, localVoiceMessage: ChatMessage): Int {
    val localRunId = localVoiceClientRunId(localVoiceMessage)
    if (!localRunId.isNullOrBlank()) {
        val runMatchedIndex = messages.indexOfFirst { message ->
            message.role == MessageRole.user &&
                !message.hasVoiceContent &&
                !message.hasFileContent &&
                message.runId == localRunId
        }
        if (runMatchedIndex >= 0) return runMatchedIndex
    }

    val knownTranscript = localVoiceMessage.voiceTranscriptText?.trim()?.takeIf { it.isNotBlank() }
    val localContent = localVoiceMessage.content.trim()
    val localTimestamp = localVoiceMessage.sortTimestamp
    return messages.indices.mapNotNull { index ->
        val message = messages[index]
        val transcript = message.content.trim()
        if (message.role != MessageRole.user ||
            message.hasVoiceContent ||
            message.hasFileContent ||
            transcript.isBlank() ||
            transcript == localContent
        ) {
            return@mapNotNull null
        }

        if (knownTranscript != null) {
            return@mapNotNull if (knownTranscript == transcript) index to 0.0 else null
        }

        val historyTimestamp = message.sortTimestamp
        if (localTimestamp == null || historyTimestamp == null) {
            return@mapNotNull index to 0.0
        }
        if (historyTimestamp < localTimestamp) return@mapNotNull null
        val distance = kotlin.math.abs(historyTimestamp - localTimestamp)
        if (distance < 900.0) index to distance else null
    }.minWithOrNull(
        compareBy<Pair<Int, Double>> { it.second }
            .thenByDescending { it.first }
    )?.first ?: -1
}

private fun historyUserIndexMatchingLocalUserEcho(messages: List<ChatMessage>, localUserMessage: ChatMessage): Int {
    return messages.indices.reversed().firstOrNull { index ->
        val candidate = messages[index]
        candidate.role == MessageRole.user &&
            userMessagesMatchForLocalHistoryMerge(candidate, localUserMessage) &&
            timestampsCanRepresentSameTurn(
                historyTimestamp = candidate.sortTimestamp,
                localTimestamp = localUserMessage.sortTimestamp
            )
    } ?: -1
}

private fun localUserMessageReplacingHistoryUser(
    localUserMessage: ChatMessage,
    historyUserMessage: ChatMessage
): ChatMessage {
    val resolvedSortTimestamp = when {
        historyUserMessage.sortTimestamp != null && localUserMessage.sortTimestamp != null ->
            minOf(historyUserMessage.sortTimestamp, localUserMessage.sortTimestamp)
        historyUserMessage.sortTimestamp != null -> historyUserMessage.sortTimestamp
        else -> localUserMessage.sortTimestamp
    }
    return localUserMessage.copy(
        contentBlocks = if (localUserMessage.contentBlocks.isEmpty()) historyUserMessage.contentBlocks else localUserMessage.contentBlocks,
        createdAt = historyUserMessage.createdAt.ifBlank { localUserMessage.createdAt },
        sortTimestamp = resolvedSortTimestamp
    )
}

private fun localVoiceClientRunId(message: ChatMessage): String? {
    if (message.role != MessageRole.user || !message.hasVoiceContent || !message.runId.startsWith("local-user-")) {
        return null
    }
    return message.runId.removePrefix("local-user-").trim().takeIf { it.isNotBlank() }
}

private fun localVoiceMessageReplacingTranscript(
    localVoiceMessage: ChatMessage,
    transcriptMessage: ChatMessage
): ChatMessage {
    val transcript = transcriptMessage.content.trim()
    val blocks = localVoiceMessage.contentBlocks.map { block ->
        if (block.isVoiceMessageBlock) block.copy(transcript = transcript) else block
    }
    return localVoiceMessage.copy(
        state = MessageState.completed,
        contentBlocks = blocks,
        sortTimestamp = transcriptMessage.sortTimestamp ?: localVoiceMessage.sortTimestamp
    )
}

private fun shouldPreserveCurrentMessageAcrossHistoryRefresh(
    message: ChatMessage,
    currentStreamingMessageId: String?,
    isTrackedPendingAssistantMessageId: (String) -> Boolean
): Boolean {
    if (isTransientAssistantPlaceholder(message)) {
        val isTrackedPending = isTrackedPendingAssistantMessageId(message.id)
        return message.state == MessageState.streaming &&
            (message.id == currentStreamingMessageId || isTrackedPending)
    }
    if (message.hasFileContent || message.hasVoiceContent || message.runId.startsWith("file-")) return true
    if (message.runId.startsWith("local-user-")) return true
    if (message.state == MessageState.streaming || message.state == MessageState.failed) return true
    if (message.role != MessageRole.assistant || message.content.trim().isEmpty()) return false
    return message.runId.isNotBlank()
}

internal fun isTransientAssistantPlaceholder(message: ChatMessage): Boolean {
    if (message.role != MessageRole.assistant) return false
    if (message.hasFileContent || message.hasVoiceContent || message.hasToolContent) return false
    val trimmed = message.content.trim()
    return trimmed.isEmpty() ||
        trimmed.startsWith("正在连接") ||
        trimmed.startsWith("Connecting") ||
        trimmed.startsWith("连接中断") ||
        trimmed.startsWith("Connection interrupted") ||
        trimmed == "正在同步回复..." ||
        trimmed == "Syncing reply..." ||
        trimmed == "已完成，但未返回文本。" ||
        trimmed == "Completed, but no text was returned." ||
        trimmed == "正在同步最终内容..." ||
        trimmed == "Syncing final content..." ||
        trimmed == "等待宿主机识别语音..." ||
        trimmed == "Waiting for host transcription..." ||
        isProtocolTypingMarkerText(trimmed)
}

private fun resolvedPendingAssistantMessageIds(
    historyMessages: List<ChatMessage>,
    currentMessages: List<ChatMessage>,
    currentStreamingMessageId: String?,
    isTrackedPendingAssistantMessageId: (String) -> Boolean
): Set<String> {
    if (historyMessages.isEmpty()) return emptySet()
    return currentMessages
        .filter { message ->
            message.role == MessageRole.assistant &&
                message.state == MessageState.streaming &&
                (message.id == currentStreamingMessageId || isTrackedPendingAssistantMessageId(message.id)) &&
                historyResolvesPendingAssistant(
                    historyMessages = historyMessages,
                    currentMessages = currentMessages,
                    pendingAssistant = message
                )
        }
        .map { it.id }
        .toSet()
}

private fun historyResolvesPendingAssistant(
    historyMessages: List<ChatMessage>,
    currentMessages: List<ChatMessage>,
    pendingAssistant: ChatMessage
): Boolean {
    val orderedCurrentMessages = orderMessagesWithSourceRunAnchors(currentMessages)
    val pendingIndex = orderedCurrentMessages.indexOfFirst { it.id == pendingAssistant.id }
    if (pendingIndex <= 0) return false
    val triggeringUser = orderedCurrentMessages
        .take(pendingIndex)
        .lastOrNull { it.role == MessageRole.user }
        ?: return false

    return historyMessages.indices.reversed().any { historyUserIndex ->
        val historyUser = historyMessages[historyUserIndex]
        if (!userMessagesMatchForLocalHistoryMerge(historyUser, triggeringUser)) {
            return@any false
        }
        val historyAssistantIndex = nextRenderableAssistantIndexAfter(historyUserIndex, historyMessages)
        if (historyAssistantIndex < 0) return@any false
        val historyAssistant = historyMessages[historyAssistantIndex]
        timestampsCanRepresentSameTurn(
            historyTimestamp = historyUser.sortTimestamp,
            localTimestamp = triggeringUser.sortTimestamp
        ) && timestampsCanRepresentSameTurn(
            historyTimestamp = historyAssistant.sortTimestamp,
            localTimestamp = pendingAssistant.sortTimestamp
        )
    }
}

private fun nextRenderableAssistantIndexAfter(index: Int, messages: List<ChatMessage>): Int {
    if (index + 1 >= messages.size) return -1
    for (candidateIndex in (index + 1) until messages.size) {
        val candidate = messages[candidateIndex]
        if (candidate.role == MessageRole.user) return -1
        if (candidate.role != MessageRole.assistant) continue
        val text = candidate.plainTextContent.trim()
        if (text.isNotEmpty() && !isTransientAssistantPlaceholder(candidate)) {
            return candidateIndex
        }
    }
    return -1
}

private fun userMessagesMatchForLocalHistoryMerge(left: ChatMessage, right: ChatMessage): Boolean {
    if (left.role != MessageRole.user || right.role != MessageRole.user) return false
    if (left.hasFileContent || right.hasFileContent || left.hasVoiceContent || right.hasVoiceContent) {
        return false
    }
    return normalizeUserMessageText(left.content) == normalizeUserMessageText(right.content)
}

private fun normalizeUserMessageText(value: String): String {
    return sanitizeChatMessageText(value)
        .trim()
        .replace(Regex("[\\s\\u2000-\\u200A\\u202F\\u205F\\u3000]+"), " ")
}

private fun timestampsCanRepresentSameTurn(
    historyTimestamp: Double?,
    localTimestamp: Double?
): Boolean {
    if (historyTimestamp == null || localTimestamp == null) return false
    val delta = historyTimestamp - localTimestamp
    return delta >= -sameTurnClockSkewToleranceSeconds && delta <= sameTurnWindowSeconds
}

private fun isSameChatMessageInstance(existing: ChatMessage, candidate: ChatMessage): Boolean {
    if (existing.runId.isNotBlank() && candidate.runId.isNotBlank() && existing.runId == candidate.runId) {
        return true
    }
    if (existing.role == MessageRole.user && candidate.role == MessageRole.user) {
        if (!userMessagesMatchForLocalHistoryMerge(existing, candidate)) return false
    } else if (existing.role != candidate.role || existing.content != candidate.content) {
        return false
    }
    val existingTime = existing.sortTimestamp
    val candidateTime = candidate.sortTimestamp
    if (existingTime != null && candidateTime != null) {
        return kotlin.math.abs(existingTime - candidateTime) < 120.0
    }
    return true
}

internal fun sameFileMessage(existing: ChatMessage, candidate: ChatMessage): Boolean {
    val existingCanonicalRunId = canonicalFileRunId(existing)
    val candidateCanonicalRunId = canonicalFileRunId(candidate)
    if (!existingCanonicalRunId.isNullOrBlank() && existingCanonicalRunId == candidateCanonicalRunId) {
        return true
    }

    val existingFileIds = existing.transferContentBlocks().mapNotNull { block ->
        block.fileId?.trim()?.takeIf { it.isNotEmpty() }
    }.toSet()
    val candidateFileIds = candidate.transferContentBlocks().mapNotNull { block ->
        block.fileId?.trim()?.takeIf { it.isNotEmpty() }
    }.toSet()
    if (existingFileIds.isNotEmpty() &&
        candidateFileIds.isNotEmpty() &&
        existingFileIds.intersect(candidateFileIds).isNotEmpty()
    ) {
        return true
    }

    return samePendingUploadMessage(existing, candidate) || samePendingUploadMessage(candidate, existing)
}

private fun canonicalFileRunId(message: ChatMessage): String? {
    message.transferContentBlocks().firstNotNullOfOrNull { block ->
        block.fileId?.trim()?.takeIf { it.isNotEmpty() }
    }?.let { return fileMessageRunId(it) }

    return message.runId.trim().takeIf { it.startsWith("file-") }
}

internal fun ChatMessage.transferContentBlocks(): List<RelayChatContentBlock> {
    return fileContentBlocks + voiceContentBlocks
}

internal fun samePendingUploadMessage(pending: ChatMessage, completed: ChatMessage): Boolean {
    val isLocalUploadPlaceholder = pending.runId.startsWith("upload-") || pending.state == MessageState.streaming
    if (!isLocalUploadPlaceholder) return false

    val pendingBlock = pending.transferContentBlocks().firstOrNull() ?: return false
    if (!pendingBlock.fileId.isNullOrBlank()) return false

    val completedBlock = completed.transferContentBlocks().firstOrNull { !it.fileId.isNullOrBlank() } ?: return false
    val pendingName = pendingBlock.fileDisplayName?.trim().orEmpty()
    val completedName = completedBlock.fileDisplayName?.trim().orEmpty()
    if (pendingName.isBlank() || !pendingName.equals(completedName, ignoreCase = true)) return false

    val pendingMime = pendingBlock.mimeType?.trim().orEmpty()
    val completedMime = completedBlock.mimeType?.trim().orEmpty()
    if (pendingMime.isNotBlank() && completedMime.isNotBlank() && !pendingMime.equals(completedMime, ignoreCase = true)) {
        return false
    }

    val pendingSize = pendingBlock.sizeBytes?.takeIf { it > 0 }
    val completedSize = completedBlock.sizeBytes?.takeIf { it > 0 }
    if (pendingSize != null && completedSize != null && pendingSize != completedSize) {
        return false
    }

    val pendingGatewayId = pendingBlock.gatewayId?.trim().orEmpty()
    val completedGatewayId = completedBlock.gatewayId?.trim().orEmpty()
    if (pendingGatewayId.isNotBlank() && completedGatewayId.isNotBlank() && pendingGatewayId != completedGatewayId) {
        return false
    }

    val pendingSessionKey = pendingBlock.sessionKey?.trim().orEmpty()
    val completedSessionKey = completedBlock.sessionKey?.trim().orEmpty()
    return pendingSessionKey.isBlank() || completedSessionKey.isBlank() || pendingSessionKey == completedSessionKey
}

private fun mergeFileMessage(existing: ChatMessage, candidate: ChatMessage): ChatMessage {
    val shouldUseLocalUserFileOrdering =
        existing.role == MessageRole.user &&
            candidate.role == MessageRole.user &&
            existing.hasFileContent &&
            candidate.hasFileContent &&
            candidate.sortTimestamp != null &&
            (existing.sortTimestamp == null || candidate.sortTimestamp < existing.sortTimestamp)

    if (!shouldUseLocalUserFileOrdering && (existing.contentBlocks.isNotEmpty() || candidate.contentBlocks.isEmpty())) {
        return existing
    }

    val preferred = candidate.copy(
        id = existing.id,
        sortTimestamp = existing.sortTimestamp ?: candidate.sortTimestamp
    )
    if (!shouldUseLocalUserFileOrdering) {
        return preferred
    }

    return preferred.copy(
        contentBlocks = candidate.contentBlocks.ifEmpty { existing.contentBlocks },
        createdAt = candidate.createdAt.ifBlank { existing.createdAt },
        sortTimestamp = candidate.sortTimestamp
    )
}

internal fun fileMessageRunId(fileId: String): String {
    return "file-${fileId.trim()}"
}
