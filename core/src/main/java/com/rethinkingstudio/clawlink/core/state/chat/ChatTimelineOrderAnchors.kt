package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole

internal fun ChatTimelineState.completedAssistantOrderKey(
    event: TimelineEvent.MessageCompleted,
    existing: ChatMessage?
): String? {
    return preferredTimelineField(event.timelineOrderKey, existing?.timelineOrderKey)
}

internal fun completedAssistantIdentityKey(
    event: TimelineEvent.MessageCompleted,
    existing: ChatMessage?
): String? {
    return preferredTimelineField(event.timelineIdentityKey, existing?.timelineIdentityKey)
        ?: event.runId?.trim()?.takeIf { it.isNotEmpty() }?.let { "local:$it:message:assistant:030-assistant" }
        ?: event.turnId?.trim()?.takeIf { it.isNotEmpty() }?.let { "local:$it:message:assistant:030-assistant" }
}

private fun preferredTimelineField(incoming: String?, existing: String?): String? {
    val incomingValue = incoming?.trim()?.takeIf { it.isNotEmpty() }
    val existingValue = existing?.trim()?.takeIf { it.isNotEmpty() }
    // canonical chat 与旧 file envelope 重放同一附件时，不能让后到的 local 键降级权威身份与顺序。
    return incomingValue?.takeUnless { it.startsWith("local:") }
        ?: existingValue?.takeUnless { it.startsWith("local:") }
        ?: incomingValue
        ?: existingValue
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
    val identities = listOfNotNull(event.turnId, event.runId, event.clientMessageId, event.idempotencyKey)
        .mapNotNull { normalizedTurnIdentity(it) }
        .toSet()
    if (identities.isEmpty()) return null
    return messages.lastOrNull { message ->
        message.role == MessageRole.user &&
            listOfNotNull(
                normalizedTurnIdentity(message.timelineMessageId),
                normalizedTurnIdentity(message.turnId),
                normalizedTurnIdentity(message.runId),
                normalizedTurnIdentity(message.clientMessageId),
                normalizedTurnIdentity(message.idempotencyKey)
            ).any { it in identities }
    }
}

internal fun anchoredMessagesForCompletedTurn(
    messages: List<ChatMessage>,
    event: TimelineEvent.MessageCompleted
): List<ChatMessage> {
    // Assistant completion cannot confirm or derive the user's archive slot.
    return messages
}

internal fun anchoredMessagesForToolTurn(
    messages: List<ChatMessage>,
    event: TimelineEvent.ToolInvocationUpdated
): List<ChatMessage> {
    // Tool arrival also cannot assign a confirmed archive slot to the user.
    return messages
}
