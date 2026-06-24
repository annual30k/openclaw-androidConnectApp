package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState

internal fun ChatTimelineState.latestKnownSortTimestamp(): Double? {
    return messages.maxOfOrNull { it.sortTimestamp ?: Double.NEGATIVE_INFINITY }
        ?.takeIf { it != Double.NEGATIVE_INFINITY }
}

internal fun ChatTimelineState.historyItemMatchesPlaceholder(
    item: HistorySnapshotItem,
    message: ChatMessage
): Boolean {
    val runId = item.runId?.takeIf { it.isNotBlank() }
    if (runId != null && message.runId == runId) return true

    val turnId = item.turnId.takeIf { it.isNotBlank() } ?: return false
    if (messagePartsById[message.id]?.turnId == turnId) return true
    if (activeRunsByTurnId[turnId] == message.runId) return true
    return activeTurnByRunId[message.runId] == turnId
}

internal fun ChatTimelineState.matchingLocalUser(
    excludingMessageId: String,
    turnId: String?,
    runId: String?
): ChatMessage? {
    return matchingLocalUserIndex(
        excludingMessageId = excludingMessageId,
        turnId = turnId,
        runId = runId
    )?.let(messages::get)
}

internal fun ChatTimelineState.matchingLocalUserByContent(
    excludingMessageId: String,
    turnId: String?,
    runId: String?,
    content: String
): ChatMessage? {
    // 仅兼容早期缺少稳定 user identity 的本地 echo：必须带 legacy turn/run 线索且只有一个候选，不能按文本相似度合并。
    val allowsLegacyTurnRunMatch = !turnId.isNullOrBlank() || runId?.startsWith("turn-", ignoreCase = true) == true
    if (!allowsLegacyTurnRunMatch) return null
    val trimmedContent = content.trim()
    if (trimmedContent.isEmpty()) return null
    val candidates = messages.filter { message ->
        message.role == MessageRole.user &&
            message.id != excludingMessageId &&
            isLocalTimelineIdentityKey(message.timelineIdentityKey) &&
            message.content.trim() == trimmedContent
    }
    return candidates.singleOrNull()
}

private fun isLocalTimelineIdentityKey(value: String): Boolean {
    val trimmed = value.trim()
    return trimmed.isEmpty() ||
        trimmed.startsWith("local:", ignoreCase = true) ||
        trimmed.contains(":local:", ignoreCase = true)
}

private fun ChatTimelineState.matchingLocalUserIndex(
    excludingMessageId: String,
    turnId: String?,
    runId: String?
): Int? {
    val incomingTurnIdentities = listOfNotNull(
        turnId?.takeIf { it.isNotBlank() },
        runId?.takeIf { it.isNotBlank() }
    ).mapNotNull { normalizedTurnIdentity(it) }.toSet()
    if (incomingTurnIdentities.isNotEmpty()) {
        val explicitIndex = messages.indexOfLast { message ->
            message.role == MessageRole.user &&
                message.id != excludingMessageId &&
                normalizedTurnIdentity(message.runId) in incomingTurnIdentities
        }
        if (explicitIndex >= 0) return explicitIndex
    }
    return null
}

internal fun normalizedTurnIdentity(value: String?): String? {
    var normalized = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val prefix = listOf("local-user-", "user-").firstOrNull { normalized.startsWith(it, ignoreCase = true) }
    if (prefix != null) normalized = normalized.substring(prefix.length).trim()
    normalized = normalized.replace(Regex(":(user|assistant|tool|system|waiting)$", RegexOption.IGNORE_CASE), "").trim()
    return normalized.takeIf { it.isNotEmpty() }
}

internal fun ChatTimelineState.matchesTerminalRun(message: ChatMessage, turnId: String?, runId: String?): Boolean {
    if (!runId.isNullOrBlank() && message.runId == runId) return true
    if (turnId.isNullOrBlank()) return false
    return messagePartsById[message.id]?.turnId == turnId
}

internal fun ChatTimelineState.matchesTerminalEvent(
    message: ChatMessage,
    turnId: String?,
    runId: String?,
    hasExplicitScope: Boolean
): Boolean {
    if (matchesTerminalRun(message, turnId, runId)) return true
    if (hasExplicitScope) return false
    return message.role == MessageRole.assistant &&
        message.state == MessageState.streaming &&
        !message.hasFileContent &&
        !message.hasVoiceContent &&
        !message.hasToolContent
}

internal fun ChatTimelineState.completedEventMatchesPlaceholder(
    message: ChatMessage,
    event: TimelineEvent.MessageCompleted
): Boolean {
    val runId = event.runId?.takeIf { it.isNotBlank() }
    if (runId != null && message.runId == runId) return true

    val turnId = event.turnId?.takeIf { it.isNotBlank() }
    if (turnId == null) return false
    if (messagePartsById[message.id]?.turnId == turnId) return true
    if (activeRunsByTurnId[turnId] == message.runId) return true
    return activeTurnByRunId[message.runId] == turnId
}

internal fun ChatTimelineState.singleUnresolvedTransientAssistantPlaceholder(
    turnId: String? = null,
    runId: String? = null
): ChatMessage? {
    // 无明确 run 绑定时只允许唯一未解决占位被 final 替换；若同一 user 后已有终态 assistant，则保守不匹配。
    val candidates = messages.filter { candidate ->
        candidate.role == MessageRole.assistant &&
            candidate.state == MessageState.streaming &&
            isTransientAssistantPlaceholder(candidate)
    }
    if (candidates.size != 1) return null
    val candidate = candidates.single()
    val ordered = messages.sortedWith(
        compareBy<ChatMessage> { it.sortTimestamp ?: Double.MAX_VALUE }
            .thenBy { it.createdAt }
            .thenBy { it.id }
    )
    val candidateIndex = ordered.indexOfFirst { it.id == candidate.id }
    if (candidateIndex <= 0) return null
    val triggeringUserIndex = ordered
        .take(candidateIndex)
        .indexOfLast { it.role == MessageRole.user }
    if (triggeringUserIndex < 0) return null
    val explicitUserIndex = explicitEventUserIndex(
        ordered = ordered,
        turnId = turnId,
        runId = runId
    )
    if (explicitUserIndex >= 0 && triggeringUserIndex != explicitUserIndex) {
        return null
    }
    val hasTerminalAssistantAfterTrigger = ordered
        .drop(triggeringUserIndex + 1)
        .any { message ->
            message.id != candidate.id &&
                message.role == MessageRole.assistant &&
                (message.state == MessageState.completed || message.state == MessageState.failed) &&
                !isTransientAssistantPlaceholder(message) &&
                (message.plainTextContent.trim().isNotEmpty() || message.contentBlocks.isNotEmpty())
    }
    return if (hasTerminalAssistantAfterTrigger) null else candidate
}

internal fun ChatTimelineState.oldestUnresolvedTransientAssistantPlaceholder(
    turnId: String? = null,
    runId: String? = null
): ChatMessage? {
    // 旧协议 final 可能缺 run/turn。只在每个 user 分段内找尚无终态回复的最早占位，避免跨 turn 抢占。
    if (!turnId.isNullOrBlank() || !runId.isNullOrBlank()) return null
    val ordered = messages.sortedWith(
        compareBy<ChatMessage> { it.sortTimestamp ?: Double.MAX_VALUE }
            .thenBy { it.createdAt }
            .thenBy { it.id }
    )
    for (candidateIndex in ordered.indices) {
        val candidate = ordered[candidateIndex]
        if (candidate.role != MessageRole.assistant ||
            candidate.state != MessageState.streaming ||
            !isTransientAssistantPlaceholder(candidate)
        ) {
            continue
        }
        val triggeringUserIndex = ordered
            .take(candidateIndex)
            .indexOfLast { it.role == MessageRole.user }
        if (triggeringUserIndex < 0) continue
        val nextUserIndex = ordered
            .drop(candidateIndex + 1)
            .indexOfFirst { it.role == MessageRole.user }
            .takeIf { it >= 0 }
            ?.let { candidateIndex + 1 + it }
            ?: ordered.size
        val hasTerminalAssistantAfterTrigger = ordered
            .subList(triggeringUserIndex + 1, nextUserIndex)
            .any { message ->
                message.id != candidate.id &&
                    message.role == MessageRole.assistant &&
                    (message.state == MessageState.completed || message.state == MessageState.failed) &&
                    !isTransientAssistantPlaceholder(message) &&
                    (message.plainTextContent.trim().isNotEmpty() || message.contentBlocks.isNotEmpty())
            }
        if (!hasTerminalAssistantAfterTrigger) return candidate
    }
    return null
}

private fun explicitEventUserIndex(
    ordered: List<ChatMessage>,
    turnId: String?,
    runId: String?
): Int {
    val normalizedTurnId = turnId?.trim()?.takeIf { it.isNotEmpty() }
    val normalizedRunId = runId?.trim()?.takeIf { it.isNotEmpty() }
    if (normalizedTurnId == null && normalizedRunId == null) return -1

    return ordered.indexOfLast { message ->
        if (message.role != MessageRole.user) return@indexOfLast false
        val messageId = message.id.trim()
        val messageRunId = message.runId.trim()
        (normalizedTurnId != null &&
            (messageId == "user-$normalizedTurnId" ||
                messageRunId == "local-user-$normalizedTurnId" ||
                messageRunId == normalizedTurnId)) ||
            (normalizedRunId != null &&
                (messageId == "user-$normalizedRunId" ||
                    messageRunId == "local-user-$normalizedRunId" ||
                    messageRunId == normalizedRunId))
    }
}
