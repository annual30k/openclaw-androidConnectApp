package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState

internal data class LocalStopCompletionResult(
    val messages: List<ChatMessage>,
    val stoppedRunId: String?
)

internal fun completeStreamingMessageLocallyAfterStop(
    messages: List<ChatMessage>,
    runId: String?
): LocalStopCompletionResult {
    val updatedMessages = messages.toMutableList()
    val index = updatedMessages.indexOfLast { it.state == MessageState.streaming }
    if (index < 0) {
        return LocalStopCompletionResult(
            messages = messages,
            stoppedRunId = runId?.takeIf { it.isNotBlank() }
        )
    }

    val existing = updatedMessages[index]
    val resolvedRunId = runId?.takeIf { it.isNotBlank() } ?: existing.runId
    if (isTransientAssistantPlaceholder(existing)) {
        updatedMessages.removeAt(index)
    } else {
        updatedMessages[index] = existing.copy(state = MessageState.completed, runId = resolvedRunId)
    }
    return LocalStopCompletionResult(
        messages = updatedMessages,
        stoppedRunId = resolvedRunId.takeIf { it.isNotBlank() }
    )
}

internal fun hasActiveVisibleTimelineRun(
    timelineState: ChatTimelineState,
    messages: List<ChatMessage>
): Boolean {
    return messages.any { message ->
        message.role == MessageRole.assistant &&
            message.state == MessageState.streaming
    }
}

internal fun buildLocalTextAssistantPlaceholderMessage(
    id: String,
    clientRunId: String,
    sortTimestamp: Double
): ChatMessage {
    return ChatMessage(
        id = id,
        role = MessageRole.assistant,
        state = MessageState.streaming,
        content = protocolTypingMarkerText,
        createdAt = "",
        runId = clientRunId,
        sortTimestamp = sortTimestamp,
        timelineOrderKey = localTimelineOrderKey(clientRunId, 20, id),
        timelineIdentityKey = localTimelineIdentityKey("waiting", clientRunId),
        timelineItemKind = "waiting"
    )
}

internal fun localTimelineOrderKey(turnIdentity: String, slot: Int, itemId: String): String {
    val turn = turnIdentity.trim().ifBlank { itemId.trim() }
    return "local:$turn|${slot.toString().padStart(2, '0')}|${itemId.trim()}"
}

internal fun localTimelineIdentityKey(kind: String, identity: String): String {
    return "local:$kind:${identity.trim()}"
}
