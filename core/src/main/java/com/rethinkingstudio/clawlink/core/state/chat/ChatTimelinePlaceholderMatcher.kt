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
    val itemIdentities = listOf(item.turnId, item.runId, item.clientMessageId, item.idempotencyKey)
        .mapNotNull(::normalizedTurnIdentity)
        .toSet()
    val messageIdentities = listOf(message.turnId, message.runId, message.clientMessageId, message.idempotencyKey)
        .mapNotNull(::normalizedTurnIdentity)
        .toSet()
    if (itemIdentities.isNotEmpty() && messageIdentities.any(itemIdentities::contains)) return true

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
    runId: String?,
    clientMessageId: String? = null,
    idempotencyKey: String? = null
): ChatMessage? {
    return matchingLocalUserIndex(
        excludingMessageId = excludingMessageId,
        turnId = turnId,
        runId = runId,
        clientMessageId = clientMessageId,
        idempotencyKey = idempotencyKey
    )?.let(messages::get)
}

private fun ChatTimelineState.matchingLocalUserIndex(
    excludingMessageId: String,
    turnId: String?,
    runId: String?,
    clientMessageId: String?,
    idempotencyKey: String?
): Int? {
    val incomingTurnIdentities = listOfNotNull(
        turnId?.takeIf { it.isNotBlank() },
        runId?.takeIf { it.isNotBlank() },
        clientMessageId?.takeIf { it.isNotBlank() },
        idempotencyKey?.takeIf { it.isNotBlank() }
    ).mapNotNull { normalizedTurnIdentity(it) }.toSet()
    if (incomingTurnIdentities.isNotEmpty()) {
        val explicitIndex = messages.indexOfLast { message ->
                message.role == MessageRole.user &&
                message.id != excludingMessageId &&
                message.isLocalUserEchoCandidate() &&
                listOf(message.turnId, message.runId, message.clientMessageId, message.idempotencyKey)
                    .mapNotNull(::normalizedTurnIdentity)
                    .any { it in incomingTurnIdentities }
        }
        if (explicitIndex >= 0) return explicitIndex
    }
    return null
}

private fun ChatMessage.isLocalUserEchoCandidate(): Boolean {
    if (role != MessageRole.user) return false
    val hasConfirmedIdentity = timelineIdentityKey.trim().isNotEmpty() &&
        !timelineIdentityKey.trim().startsWith("local:")
    return source.trim().equals("local", ignoreCase = true) ||
        timelineOrderKey.trim().startsWith("local:") ||
        timelineIdentityKey.trim().startsWith("local:") ||
        (!hasConfirmedIdentity && runId.trim().startsWith("local-user-"))
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
    return false
}

internal fun ChatTimelineState.completedEventMatchesPlaceholder(
    message: ChatMessage,
    event: TimelineEvent.MessageCompleted
): Boolean {
    val eventIdentities = listOf(event.turnId, event.runId, event.clientMessageId, event.idempotencyKey)
        .mapNotNull(::normalizedTurnIdentity)
        .toSet()
    val messageIdentities = listOf(message.turnId, message.runId, message.clientMessageId, message.idempotencyKey)
        .mapNotNull(::normalizedTurnIdentity)
        .toSet()
    if (eventIdentities.isNotEmpty() && messageIdentities.any(eventIdentities::contains)) return true

    val runId = event.runId?.takeIf { it.isNotBlank() }
    if (runId != null && message.runId == runId) return true

    val turnId = event.turnId?.takeIf { it.isNotBlank() }
    if (turnId == null) return false
    if (messagePartsById[message.id]?.turnId == turnId) return true
    if (activeRunsByTurnId[turnId] == message.runId) return true
    return activeTurnByRunId[message.runId] == turnId
}
