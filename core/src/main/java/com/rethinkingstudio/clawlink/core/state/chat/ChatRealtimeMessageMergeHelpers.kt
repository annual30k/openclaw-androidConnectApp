package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import java.time.Instant
import java.util.UUID

private const val realtimeMessageOrderEpsilon = 0.001

internal fun mergeRemoteUserMessageIntoCurrentMessages(
    currentMessages: List<ChatMessage>,
    content: String,
    contentBlocks: List<RelayChatContentBlock>,
    runId: String?,
    sortTimestamp: Double?,
    assistantMessageId: String? = null
): List<ChatMessage> {
    val trimmed = sanitizeChatMessageText(content)
    if (trimmed.isBlank() && contentBlocks.isEmpty()) return currentMessages

    val messages = currentMessages.toMutableList()
    val normalizedRunId = runId?.trim()?.takeIf { it.isNotEmpty() }
    val incomingTurnIdentities = remoteUserTurnIdentities(normalizedRunId, contentBlocks)
    val candidateRunId = normalizedRunId ?: firstSourceRunId(contentBlocks) ?: "remote-user-${UUID.randomUUID().toString().take(8)}"
    val incomingAttachmentCandidate = ChatMessage(
        id = UUID.randomUUID().toString(),
        role = MessageRole.user,
        state = MessageState.completed,
        content = trimmed,
        contentBlocks = contentBlocks,
        createdAt = Instant.EPOCH.toString(),
        runId = candidateRunId,
        sortTimestamp = sortTimestamp
    )

    if (mergeRemoteVoiceTranscriptIntoLocalMessage(
            messages = messages,
            transcript = trimmed,
            incomingTurnIdentities = incomingTurnIdentities,
            eventSortTimestamp = sortTimestamp
        )
    ) {
        return orderMessagesWithSourceRunAnchors(messages)
    }

    val localAttachmentIndex = messages.indexOfLast { message ->
        message.role == MessageRole.user &&
            message.transferContentBlocks().isNotEmpty() &&
            (samePendingUploadMessage(message, incomingAttachmentCandidate) ||
                sameFileMessage(message, incomingAttachmentCandidate) ||
                samePendingUploadMessageByUnambiguousSourceRunId(
                    messages = messages,
                    pending = message,
                    completed = incomingAttachmentCandidate
                ))
    }
    if (localAttachmentIndex >= 0) {
        val existing = messages[localAttachmentIndex]
        // 远端 user 文件回显一旦带上稳定附件身份，就应当直接确认本地上传占位，
        // 不能等到稍后的上传完成回调再去收敛，否则中间会短暂出现两个 user 附件气泡。
        val mergedBlocks = if (existing.transferContentBlocks().isNotEmpty() && incomingAttachmentCandidate.transferContentBlocks().isNotEmpty()) {
            mergeCompletedFileMessage(existing = existing, completed = incomingAttachmentCandidate).contentBlocks
        } else if (existing.contentBlocks.isEmpty()) {
            incomingAttachmentCandidate.contentBlocks
        } else {
            existing.contentBlocks
        }
        messages[localAttachmentIndex] = existing.copy(
            state = MessageState.completed,
            content = existing.content.takeIf { it.trim().isNotEmpty() } ?: incomingAttachmentCandidate.content,
            contentBlocks = mergedBlocks,
            createdAt = existing.createdAt.ifBlank { incomingAttachmentCandidate.createdAt },
            runId = existing.runId.takeIf { it.isNotBlank() } ?: incomingAttachmentCandidate.runId,
            sortTimestamp = existing.sortTimestamp ?: incomingAttachmentCandidate.sortTimestamp
        )
        return orderMessagesWithSourceRunAnchors(messages)
    }

    // 旧 Hermes echo 可能带 user-/local-user- 前缀；实时合并必须按稳定 turn identity 对齐本地气泡。
    val localTextIndex = messages.indexOfLast { message ->
        message.role == MessageRole.user &&
            message.runId.startsWith("local-user-") &&
            !message.hasVoiceContent &&
            localUserTurnIdentities(message).any { it in incomingTurnIdentities }
    }
    if (localTextIndex >= 0) {
        val existing = messages[localTextIndex]
        messages[localTextIndex] = existing.copy(
            state = MessageState.completed,
            contentBlocks = if (existing.contentBlocks.isEmpty()) contentBlocks else existing.contentBlocks
        )
        return orderMessagesWithSourceRunAnchors(messages)
    }

    val last = messages.lastOrNull()
    if (last != null &&
        last.role == MessageRole.user &&
        localUserTurnIdentities(last).any { it in incomingTurnIdentities }
    ) {
        val incomingHasAttachment = incomingAttachmentCandidate.transferContentBlocks().isNotEmpty()
        val lastRepresentsSameAttachment = incomingHasAttachment &&
            (
                samePendingUploadMessage(last, incomingAttachmentCandidate) ||
                    sameFileMessage(last, incomingAttachmentCandidate) ||
                    samePendingUploadMessageByUnambiguousSourceRunId(
                        messages = messages,
                        pending = last,
                        completed = incomingAttachmentCandidate
                    )
                )
        if (!incomingHasAttachment || lastRepresentsSameAttachment) {
            return currentMessages
        }
    }

    val candidateTimestamp = sortTimestamp ?: (System.currentTimeMillis() / 1000.0)
    val pendingAssistant = pendingAssistantForRemoteUserEcho(
        messages = messages,
        runId = normalizedRunId,
        incomingTurnIdentities = incomingTurnIdentities,
        assistantMessageId = assistantMessageId
    )
    val insertedAt = if (pendingAssistant?.sortTimestamp != null) {
        minOf(candidateTimestamp, pendingAssistant.sortTimestamp - realtimeMessageOrderEpsilon)
    } else {
        candidateTimestamp
    }
    messages.add(
        ChatMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.user,
            state = MessageState.completed,
            content = trimmed,
            contentBlocks = contentBlocks,
            createdAt = Instant.ofEpochMilli((insertedAt * 1000).toLong()).toString(),
            runId = candidateRunId,
            sortTimestamp = insertedAt
        )
    )
    return orderMessagesWithSourceRunAnchors(messages)
}

internal fun applyAssistantErrorToCurrentMessages(
    currentMessages: List<ChatMessage>,
    runId: String?,
    assistantMessageId: String?,
    errorMessage: String,
    sortTimestamp: Double?
): List<ChatMessage> {
    val trimmed = errorMessage.trim()
    if (trimmed.isBlank()) return currentMessages

    val normalizedRunId = runId?.trim()?.takeIf { it.isNotEmpty() }
    val messages = currentMessages.toMutableList()
    val index = messages.indexOfFirst { message ->
        message.role == MessageRole.assistant &&
            ((assistantMessageId != null && message.id == assistantMessageId) ||
                (normalizedRunId != null && message.runId == normalizedRunId))
    }

    if (index >= 0) {
        val existing = messages[index]
        messages[index] = existing.copy(
            state = MessageState.failed,
            content = trimmed,
            contentBlocks = emptyList(),
            runId = normalizedRunId ?: existing.runId,
            sortTimestamp = existing.sortTimestamp ?: sortTimestamp
        )
        return orderMessagesWithSourceRunAnchors(messages)
    }

    return currentMessages
}

internal fun mergedAssistantStreamingDisplayContent(existing: ChatMessage, delta: String): String {
    val sanitizedDelta = sanitizeChatMessageText(delta)
    if (sanitizedDelta.isBlank() || isProtocolTypingMarkerText(sanitizedDelta)) {
        return existing.content
    }
    return if (isTransientAssistantPlaceholder(existing) || isProtocolTypingMarkerText(existing.content)) {
        sanitizedDelta
    } else {
        existing.content + sanitizedDelta
    }
}

internal fun shouldSyncAssistantFinalFromHistory(
    existing: ChatMessage?,
    finalText: String,
    finalContentBlocks: List<RelayChatContentBlock>
): Boolean {
    val normalizedFinalText = sanitizeChatMessageText(finalText).trim()
    if ((normalizedFinalText.isNotBlank() && !isProtocolTypingMarkerText(normalizedFinalText)) ||
        hasRenderableFinalContentBlocks(finalContentBlocks)
    ) {
        return false
    }
    if (existing == null) return false
    val existingText = existing.content.trim()
    return existingText.isBlank() ||
        isTransientAssistantPlaceholder(existing) ||
        isProtocolTypingMarkerText(existingText)
}

internal fun mergeCompletedAssistantFinalIntoCurrentMessages(
    currentMessages: List<ChatMessage>,
    candidate: ChatMessage
): List<ChatMessage>? {
    if (candidate.role != MessageRole.assistant ||
        candidate.state != MessageState.completed ||
        candidate.hasFileContent ||
        candidate.hasVoiceContent ||
        candidate.hasToolContent
    ) {
        return null
    }
    val candidateText = sanitizeChatMessageText(candidate.plainTextContent).trim()
    if (candidateText.isBlank()) return null

    val messages = currentMessages.toMutableList()
    val existingIndex = sameRunCompletedAssistantFinalIndex(messages, candidate, candidateText)
    if (existingIndex < 0) return null

    val existing = messages[existingIndex]
    messages[existingIndex] = existing.copy(
        state = MessageState.completed,
        content = existing.content.ifBlank { candidate.content },
        contentBlocks = if (existing.contentBlocks.isEmpty()) candidate.contentBlocks else existing.contentBlocks,
        createdAt = existing.createdAt.ifBlank { candidate.createdAt },
        runId = existing.runId.ifBlank { candidate.runId },
        sortTimestamp = existing.sortTimestamp ?: candidate.sortTimestamp
    )
    return orderMessagesWithSourceRunAnchors(messages)
}

private fun sameRunCompletedAssistantFinalIndex(
    messages: List<ChatMessage>,
    candidate: ChatMessage,
    candidateText: String
): Int {
    if (candidate.runId.isBlank()) return -1
    return messages.indexOfFirst { existing ->
        existing.id != candidate.id &&
            existing.role == MessageRole.assistant &&
            existing.state == MessageState.completed &&
            existing.runId == candidate.runId &&
            isPlainAssistantTextMessage(existing) &&
            sanitizeChatMessageText(existing.plainTextContent).trim() == candidateText
    }
}

private fun isPlainAssistantTextMessage(message: ChatMessage): Boolean {
    return !message.hasFileContent &&
        !message.hasVoiceContent &&
        !message.hasToolContent
}

internal fun removeResolvedTransientAssistantPlaceholders(
    messages: List<ChatMessage>
): List<ChatMessage> {
    val terminalAssistantRunIds = messages
        .asSequence()
        .filter { message ->
            message.role == MessageRole.assistant &&
                (message.state == MessageState.completed || message.state == MessageState.failed) &&
                message.runId.isNotBlank() &&
                !isTransientAssistantPlaceholder(message) &&
                !isProtocolTypingMarkerText(message.plainTextContent)
        }
        .map { it.runId }
        .toSet()
    val sameTurnResolvedPlaceholderIds = sameTurnResolvedTransientAssistantIds(messages)
    if (
        terminalAssistantRunIds.isEmpty() &&
        sameTurnResolvedPlaceholderIds.isEmpty()
    ) {
        return messages
    }

    return messages.filterNot { message ->
        message.role == MessageRole.assistant &&
            message.state == MessageState.streaming &&
            (
                message.runId in terminalAssistantRunIds ||
                    (isTransientAssistantPlaceholder(message) && message.id in sameTurnResolvedPlaceholderIds)
                )
    }
}

private fun sameTurnResolvedTransientAssistantIds(messages: List<ChatMessage>): Set<String> {
    val ordered = messages.sortedWith(
        compareBy<ChatMessage> { it.sortTimestamp ?: Double.MAX_VALUE }
            .thenBy { it.createdAt }
            .thenBy { it.id }
    )
    return ordered.mapIndexedNotNull { index, message ->
        if (message.role != MessageRole.assistant ||
            message.state != MessageState.streaming ||
            !isTransientAssistantPlaceholder(message)
        ) {
            return@mapIndexedNotNull null
        }

        val triggeringUserIndex = ordered
            .take(index)
            .indexOfLast { candidate -> candidate.role == MessageRole.user }
        if (triggeringUserIndex < 0) return@mapIndexedNotNull null

        val nextUserIndex = ordered
            .drop(index + 1)
            .indexOfFirst { candidate -> candidate.role == MessageRole.user }
            .takeIf { it >= 0 }
            ?.let { relativeIndex -> index + 1 + relativeIndex }
            ?: ordered.size

        val hasTerminalAssistantInTurn = (triggeringUserIndex + 1 until nextUserIndex).any { candidateIndex ->
            candidateIndex != index && isTerminalRenderableAssistant(ordered[candidateIndex])
        }
        if (hasTerminalAssistantInTurn) message.id else null
    }.toSet()
}

private fun isTerminalRenderableAssistant(message: ChatMessage): Boolean {
    if (message.role != MessageRole.assistant) return false
    if (message.state != MessageState.completed && message.state != MessageState.failed) return false
    if (isTransientAssistantPlaceholder(message)) return false
    return message.plainTextContent.trim().isNotEmpty() || message.contentBlocks.isNotEmpty()
}

private fun hasRenderableFinalContentBlocks(blocks: List<RelayChatContentBlock>): Boolean {
    return blocks.any { block ->
        block.isToolCallBlock ||
            block.isToolResultBlock ||
            block.isFileBlock ||
            block.isVoiceMessageBlock ||
            !block.text.isNullOrBlank() ||
            listOf(block.result, block.partialResult, block.content, block.output, block.error).any { value ->
                !value?.renderedText(listOf("content", "markdown", "text", "body", "message", "value", "result", "output")).isNullOrBlank()
            }
    }
}

private fun mergeRemoteVoiceTranscriptIntoLocalMessage(
    messages: MutableList<ChatMessage>,
    transcript: String,
    incomingTurnIdentities: Set<String>,
    eventSortTimestamp: Double?
): Boolean {
    if (transcript.isBlank()) return false

    val runMatchedIndex = if (incomingTurnIdentities.isNotEmpty()) {
        messages.indexOfLast { message ->
            message.role == MessageRole.user &&
                localUserTurnIdentities(message).any { it in incomingTurnIdentities } &&
                message.hasVoiceContent
        }
    } else {
        -1
    }

    val index = runMatchedIndex
    if (index < 0) return false

    val existing = messages[index]
    val updatedBlocks = existing.contentBlocks.map { block ->
        if (block.isVoiceMessageBlock) block.copy(transcript = transcript) else block
    }
    messages[index] = existing.copy(
        state = MessageState.completed,
        contentBlocks = updatedBlocks
    )
    return true
}

private fun pendingAssistantForRemoteUserEcho(
    messages: List<ChatMessage>,
    runId: String?,
    incomingTurnIdentities: Set<String>,
    assistantMessageId: String?
): ChatMessage? {
    if (!assistantMessageId.isNullOrBlank()) {
        messages.firstOrNull { it.role == MessageRole.assistant && it.id == assistantMessageId }?.let { return it }
    }
    val runIdentities = incomingTurnIdentities.ifEmpty {
        normalizedTurnIdentity(runId)?.let(::setOf).orEmpty()
    }
    if (runIdentities.isNotEmpty()) {
        messages.firstOrNull {
            it.role == MessageRole.assistant &&
                normalizedTurnIdentity(it.runId) in runIdentities
        }?.let { return it }
    }
    return null
}

private fun remoteUserTurnIdentities(runId: String?, contentBlocks: List<RelayChatContentBlock>): Set<String> {
    return (listOf(runId) + contentBlocks.mapNotNull { it.sourceRunId })
        .mapNotNull { normalizedTurnIdentity(it) }
        .toSet()
}

private fun localUserTurnIdentities(message: ChatMessage): Set<String> {
    return (listOf(message.runId) + message.contentBlocks.mapNotNull { it.sourceRunId })
        .mapNotNull { normalizedTurnIdentity(it) }
        .toSet()
}

private fun firstSourceRunId(contentBlocks: List<RelayChatContentBlock>): String? {
    return contentBlocks.firstNotNullOfOrNull { it.sourceRunId?.trim()?.takeIf(String::isNotEmpty) }
}
