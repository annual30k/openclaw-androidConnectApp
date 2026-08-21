package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole

internal fun ChatMessage.hasUnconfirmedLocalTimelineIdentity(): Boolean {
    val hasProvisionalConversationLifecycle = role == MessageRole.user && (
        conversationSeqState.equals("provisional", ignoreCase = true) ||
            (conversationSeqState.isBlank() && source.equals("local", ignoreCase = true))
        )
    return hasProvisionalConversationLifecycle ||
        timelineOrderKey.trim().startsWith("local:") ||
        timelineIdentityKey.trim().startsWith("local:")
}

internal fun ChatMessage.hasRelayTimelineIdentity(): Boolean {
    return timelineOrderKey.trim().isNotEmpty() &&
        !timelineOrderKey.trim().startsWith("local:") &&
        timelineIdentityKey.trim().isNotEmpty() &&
        timelineItemKind.trim().isNotEmpty()
}

internal fun newestBoundedHistoryWindowMessages(
    messages: List<ChatMessage>,
    maxMessages: Int
): List<ChatMessage> {
    if (maxMessages <= 0) return emptyList()
    return orderTimelineMessages(messages).takeLast(maxMessages)
}

internal fun olderBoundedHistoryWindowMessages(
    messages: List<ChatMessage>,
    maxMessages: Int,
    shouldPreserveActiveMessage: (ChatMessage) -> Boolean
): List<ChatMessage> {
    if (maxMessages <= 0) return emptyList()
    val ordered = orderTimelineMessages(messages)
    if (ordered.size <= maxMessages) return ordered

    val oldestWindow = ordered.take(maxMessages)
    val oldestWindowIds = oldestWindow.mapTo(mutableSetOf()) { it.id }
    val activeMessagesOutsideOldestWindow = ordered.filter { message ->
        message.id !in oldestWindowIds && shouldPreserveActiveMessage(message)
    }
    if (activeMessagesOutsideOldestWindow.isEmpty()) {
        return oldestWindow
    }

    // 历史向上翻页时要优先保留仍在流式输出/等待同步的消息，避免窗口裁剪把活跃 run 移出 UI。
    val retainedOldestCount = (maxMessages - activeMessagesOutsideOldestWindow.size).coerceAtLeast(0)
    val retainedOldestWindow = oldestWindow.take(retainedOldestCount)
    return orderTimelineMessages(retainedOldestWindow + activeMessagesOutsideOldestWindow)
        .take(maxMessages)
}
