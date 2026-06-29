package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import java.time.Instant
import java.time.format.DateTimeParseException

internal fun ChatTimelineState.completedAssistantOrderKey(
    event: TimelineEvent.MessageCompleted,
    existing: ChatMessage?
): String? {
    // 本地等待占位被最终 assistant 消息替换时，必须锚定到同一 turn 的 user 消息后面，不能靠到达顺序决定位置。
    if (!event.clearsWaitingAssistant()) return null
    existing?.timelineOrderKey
        ?.trim()
        ?.takeIf { it.isNotEmpty() && !it.startsWith("local:") }
        ?.let { return it }
    val user = matchingTurnUserMessage(event) ?: return null
    val turnBase = anchoredTurnBase(user) ?: return null
    val identity = completedAssistantIdentityKey(event, existing) ?: event.messageId
    return listOf(
        "v1",
        turnBase,
        "50",
        "${paddedTimelineOrderValue(1, 16)}:part-text-1:${event.messageId}",
        shortStableTimelineHash(identity)
    ).joinToString("|")
}

internal fun completedAssistantIdentityKey(
    event: TimelineEvent.MessageCompleted,
    existing: ChatMessage?
): String? {
    return event.timelineIdentityKey?.trim()?.takeIf { it.isNotEmpty() }
        ?: existing?.timelineIdentityKey?.trim()?.takeIf { it.isNotEmpty() }
        ?: event.runId?.trim()?.takeIf { it.isNotEmpty() }?.let { "local:$it:message:assistant:030-assistant" }
        ?: event.turnId?.trim()?.takeIf { it.isNotEmpty() }?.let { "local:$it:message:assistant:030-assistant" }
}

internal fun completedAssistantItemKind(
    event: TimelineEvent.MessageCompleted,
    existing: ChatMessage?,
    anchoredOrderKey: String?,
    anchoredIdentityKey: String?
): String {
    event.timelineItemKind?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    existing?.timelineItemKind?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    // 本地补齐 completion order/identity 时必须同步补 itemKind，否则 canonical 排序不会承认该锚点。
    if ((!anchoredOrderKey.isNullOrBlank() || !anchoredIdentityKey.isNullOrBlank()) && event.clearsWaitingAssistant()) {
        return "message:assistant"
    }
    return ""
}

internal fun ChatTimelineState.matchingTurnUserMessage(event: TimelineEvent.MessageCompleted): ChatMessage? {
    val identities = listOfNotNull(event.turnId, event.runId)
        .mapNotNull { normalizedTurnIdentity(it) }
        .toSet()
    if (identities.isEmpty()) return null
    return messages.lastOrNull { message ->
        message.role == MessageRole.user &&
            listOfNotNull(
                normalizedTurnIdentity(message.timelineMessageId),
                normalizedTurnIdentity(message.runId)
            ).any { it in identities }
    }
}

internal fun anchoredMessagesForCompletedTurn(
    messages: List<ChatMessage>,
    event: TimelineEvent.MessageCompleted
): List<ChatMessage> {
    // 历史 final 先到时，给本地 user echo 补齐可排序的 v1 order key，保证后续 replay 与刷新顺序一致。
    val identities = listOfNotNull(event.turnId, event.runId)
        .mapNotNull { normalizedTurnIdentity(it) }
        .toSet()
    if (identities.isEmpty()) return messages
    val index = messages.indexOfLast { message ->
        message.role == MessageRole.user &&
            listOfNotNull(
                normalizedTurnIdentity(message.timelineMessageId),
                normalizedTurnIdentity(message.runId)
            ).any { it in identities }
    }
    if (index < 0) return messages
    val user = messages[index]
    if (user.timelineOrderKey.trim().isNotEmpty() && !user.timelineOrderKey.trim().startsWith("local:")) {
        return messages
    }
    val turnBase = anchoredTurnBase(user) ?: return messages
    val identity = user.timelineIdentityKey.trim().ifEmpty {
        "local:${user.runId.ifBlank { event.runId ?: event.turnId.orEmpty() }}:message:user:010-user"
    }
    val anchoredUser = user.copy(
        timelineOrderKey = listOf(
            "v1",
            turnBase,
            "10",
            "${paddedTimelineOrderValue(1, 16)}:part-text-1:${user.id}",
            shortStableTimelineHash(identity)
        ).joinToString("|"),
        timelineIdentityKey = identity,
        timelineItemKind = user.timelineItemKind.ifBlank { "message:user" }
    )
    return messages.toMutableList().also { it[index] = anchoredUser }
}

private fun anchoredTurnBase(user: ChatMessage): String? {
    val order = user.timelineOrderKey.trim()
    if (order.isNotEmpty() && !order.startsWith("local:")) {
        val parts = order.split("|")
        if (parts.size >= 2 && parts[1].isNotBlank()) return parts[1]
    }
    return createdAtMillis(user.createdAt)?.let { paddedTimelineOrderValue(it) }
}

private fun createdAtMillis(value: String): Long? {
    return try {
        Instant.parse(value).toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}

private fun paddedTimelineOrderValue(value: Long, size: Int = 20): String {
    val text = value.coerceAtLeast(0).toString()
    return if (text.length >= size) text else "0".repeat(size - text.length) + text
}

private fun shortStableTimelineHash(value: String): String {
    var hash = -3750763034362895579L
    value.encodeToByteArray().forEach { byte ->
        hash = hash xor (byte.toLong() and 0xff)
        hash *= 1099511628211L
    }
    return hash.toULong().toString(16)
}
