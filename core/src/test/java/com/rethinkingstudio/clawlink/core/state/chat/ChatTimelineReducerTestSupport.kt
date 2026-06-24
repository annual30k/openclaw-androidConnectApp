package com.rethinkingstudio.clawlink.core.state.chat

internal fun event(raw: String): TimelineEvent {
    return canonicalizedTestEvent(requireNotNull(TimelineEventLog.decodeEvent(raw)))
}

internal fun canonicalizedTestEvent(event: TimelineEvent): TimelineEvent {
    return when (event) {
        is TimelineEvent.TurnUserCreated -> event.copy(
            timelineOrderKey = event.timelineOrderKey ?: testOrderKey(event.messageId, "10"),
            timelineIdentityKey = event.timelineIdentityKey ?: "message:user:${event.messageId}",
            timelineItemKind = event.timelineItemKind ?: "message:user"
        )
        is TimelineEvent.MessagePartDelta -> {
            val kind = testMessageKind(event.role ?: "assistant")
            event.copy(
                timelineOrderKey = event.timelineOrderKey ?: testOrderKey(event.messageId, testOrderSlot(kind)),
                timelineIdentityKey = event.timelineIdentityKey ?: "$kind:${event.messageId}",
                timelineItemKind = event.timelineItemKind ?: kind
            )
        }
        is TimelineEvent.MessageCompleted -> {
            val kind = testMessageKind(event.role ?: "assistant")
            event.copy(
                timelineOrderKey = event.timelineOrderKey ?: testOrderKey(event.messageId, testOrderSlot(kind)),
                timelineIdentityKey = event.timelineIdentityKey ?: "$kind:${event.messageId}",
                timelineItemKind = event.timelineItemKind ?: kind
            )
        }
        is TimelineEvent.ToolInvocationUpdated -> event.copy(
            timelineOrderKey = event.timelineOrderKey ?: testOrderKey(event.messageId ?: event.toolCallId, "30"),
            timelineIdentityKey = event.timelineIdentityKey ?: "tool:${event.toolCallId}",
            timelineItemKind = event.timelineItemKind ?: "tool"
        )
        is TimelineEvent.HistorySnapshotPage -> event.copy(
            items = event.items.map { item ->
                val kind = testMessageKind(item.role)
                item.copy(
                    timelineOrderKey = item.timelineOrderKey ?: testOrderKey(item.messageId, testOrderSlot(kind)),
                    timelineIdentityKey = item.timelineIdentityKey ?: "$kind:${item.messageId}",
                    timelineItemKind = item.timelineItemKind ?: kind
                )
            }
        )
        else -> event
    }
}

internal fun testMessageKind(role: String): String {
    return when (role.trim().lowercase()) {
        "user" -> "message:user"
        "tool" -> "tool"
        "system" -> "system"
        else -> "message:assistant"
    }
}

internal fun testOrderSlot(kind: String): String {
    return when (kind) {
        "message:user" -> "10"
        "tool" -> "30"
        "system" -> "40"
        else -> "50"
    }
}

internal fun testOrderKey(messageId: String, slot: String): String {
    return "v1|00000000000000000001|$slot|000000|$messageId"
}
