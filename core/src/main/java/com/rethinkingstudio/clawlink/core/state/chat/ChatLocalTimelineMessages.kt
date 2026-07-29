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

internal fun shouldPersistTimelineSnapshot(
    timelineState: ChatTimelineState,
    messages: List<ChatMessage>
): Boolean {
    val snapshotMessages = canonicalizeMessagesForTimelineSnapshot(messages)
    if (hasActiveVisibleTimelineRun(timelineState, snapshotMessages)) return true
    return snapshotMessages.any { it.hasRestorableLocalTimelineIdentity() }
}

internal fun ChatStore.persistCurrentTimelineSnapshot(
    timelineState: ChatTimelineState,
    messages: List<ChatMessage>,
    durablePendingOverlay: Boolean = false
): Boolean {
    val snapshotMessages = canonicalizeMessagesForTimelineSnapshot(messages)
    val persistenceScope = activeTimelinePersistenceScope() ?: return false
    // 同时保留已确认 canonical 窗口和本地 overlay；进程重启可先渲染最后一个内部一致状态，
    // 再由权威历史刷新完成收敛。
    return TimelinePersistenceMiddleware.persistSnapshot(
        scope = persistenceScope,
        state = timelineState.copy(messages = snapshotMessages),
        outbox = timelineOutbox.values.toList(),
        snapshotRevision = timelineSnapshotRevision,
        highWatermark = timelineHighWatermark,
        durablePendingOverlay = durablePendingOverlay
    )
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

internal fun canonicalizeMessagesForTimelineSnapshot(messages: List<ChatMessage>): List<ChatMessage> {
    var changed = false
    val canonical = messages.map { message ->
        val derived = message.withDerivedAttachmentTimelineIdentityForSnapshot()
        if (derived != message) changed = true
        derived
    }
    return if (changed) canonical else messages
}

private fun ChatMessage.hasRestorableLocalTimelineIdentity(): Boolean {
    return timelineOrderKey.startsWith("local:") &&
        timelineIdentityKey.startsWith("local:") &&
        timelineItemKind.trim().isNotEmpty()
}

private fun ChatMessage.withDerivedAttachmentTimelineIdentityForSnapshot(): ChatMessage {
    if (hasRestorableLocalTimelineIdentity()) return this
    if (role != MessageRole.user) return this
    if (contentBlocks.isEmpty() || !contentBlocks.all { it.isFileBlock || it.isVoiceMessageBlock }) return this

    val sourceRunId = transferContentBlocks()
        .firstNotNullOfOrNull { block -> block.sourceRunId?.trim()?.takeIf { it.isNotEmpty() } }
        ?: return this
    val attachmentIdentity = attachmentIdentityForOrder(transferContentBlocks()) ?: return this

    // relay legacy 的纯附件/纯语音用户回显在历史追平前可能还没有 canonical timeline key。
    // 这里用 sourceRunId + attachment identity 派生稳定本地键，保证进程重启前的恢复不依赖后续历史刷新。
    return copy(
        timelineOrderKey = timelineOrderKey.ifBlank { localTimelineOrderKey(sourceRunId, 30, id) },
        timelineIdentityKey = timelineIdentityKey.ifBlank { localTimelineIdentityKey("attachment", attachmentIdentity) },
        timelineItemKind = timelineItemKind.ifBlank { "attachment" }
    )
}
