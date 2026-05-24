package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import java.time.Instant
import java.util.UUID

private const val realtimeMessageOrderEpsilon = 0.001
private const val recentVoiceTranscriptWindowSeconds = 180.0

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
    if (mergeRemoteVoiceTranscriptIntoLocalMessage(
            messages = messages,
            transcript = trimmed,
            runId = runId,
            eventSortTimestamp = sortTimestamp
        )
    ) {
        return orderMessagesWithSourceRunAnchors(messages)
    }

    val normalizedRunId = runId?.trim()?.takeIf { it.isNotEmpty() }
    val localUserRunId = normalizedRunId?.let { "local-user-$it" }
    val localTextIndex = messages.indexOfLast { message ->
        message.role == MessageRole.user &&
            message.runId.startsWith("local-user-") &&
            !message.hasFileContent &&
            !message.hasVoiceContent &&
            ((localUserRunId != null && message.runId == localUserRunId) ||
                userTextMatchesForRealtimeMerge(message.content, trimmed))
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
        userTextMatchesForRealtimeMerge(last.content, trimmed) &&
        (normalizedRunId == null || last.runId == normalizedRunId)
    ) {
        return currentMessages
    }

    val candidateTimestamp = sortTimestamp ?: (System.currentTimeMillis() / 1000.0)
    val pendingAssistant = pendingAssistantForRemoteUserEcho(
        messages = messages,
        runId = normalizedRunId,
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
            runId = normalizedRunId ?: "remote-user-${UUID.randomUUID().toString().take(8)}",
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

private fun mergeRemoteVoiceTranscriptIntoLocalMessage(
    messages: MutableList<ChatMessage>,
    transcript: String,
    runId: String?,
    eventSortTimestamp: Double?
): Boolean {
    if (transcript.isBlank()) return false

    val normalizedRunId = runId?.trim()?.takeIf { it.isNotEmpty() }
    val runMatchedIndex = normalizedRunId?.let { resolvedRunId ->
        messages.indexOfLast { message ->
            message.role == MessageRole.user &&
                message.runId == "local-user-$resolvedRunId" &&
                message.hasVoiceContent
        }
    } ?: -1

    val eventTimestamp = eventSortTimestamp ?: (System.currentTimeMillis() / 1000.0)
    val fallbackIndex = messages.indexOfLast { message ->
        if (message.role != MessageRole.user ||
            !message.runId.startsWith("local-user-") ||
            !message.hasVoiceContent ||
            message.voiceTranscriptText == transcript
        ) {
            return@indexOfLast false
        }
        val voiceTimestamp = message.sortTimestamp ?: return@indexOfLast true
        kotlin.math.abs(eventTimestamp - voiceTimestamp) < recentVoiceTranscriptWindowSeconds
    }

    val index = runMatchedIndex.takeIf { it >= 0 } ?: fallbackIndex
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
    assistantMessageId: String?
): ChatMessage? {
    if (!assistantMessageId.isNullOrBlank()) {
        messages.firstOrNull { it.role == MessageRole.assistant && it.id == assistantMessageId }?.let { return it }
    }
    if (!runId.isNullOrBlank()) {
        messages.firstOrNull { it.role == MessageRole.assistant && it.runId == runId }?.let { return it }
    }
    return null
}

private fun userTextMatchesForRealtimeMerge(left: String, right: String): Boolean {
    return normalizeRealtimeUserText(left) == normalizeRealtimeUserText(right)
}

private fun normalizeRealtimeUserText(value: String): String {
    return sanitizeChatMessageText(value)
        .trim()
        .replace(Regex("[\\s\\u2000-\\u200A\\u202F\\u205F\\u3000]+"), " ")
}
