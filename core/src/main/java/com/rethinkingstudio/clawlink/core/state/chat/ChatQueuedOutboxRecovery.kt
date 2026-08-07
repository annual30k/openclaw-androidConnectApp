package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock

/**
 * 以持久化 outbox 的稳定标识恢复排队消息 overlay。
 *
 * 历史刷新、进程恢复和实时事件都可能重建消息列表，但尚未激活的排队项不属于
 * 当前 canonical history。只要 outbox 仍标记 queued，就必须保留对应的本地用户消息，
 * 不能因一次较旧的历史快照而丢失，也不能依赖文本或时间猜测身份。
 */
internal fun restoreQueuedTimelineOutboxMessages(
    messages: List<ChatMessage>,
    outboxEntries: Collection<TimelineOutboxEntry>
): List<ChatMessage> {
    val queuedEntries = outboxEntries
        .asSequence()
        .filter { entry -> entry.queued && entry.kind == TimelineOutboxKind.TEXT }
        .sortedWith(
            compareBy<TimelineOutboxEntry>(::queuedTimelineOutboxOrder)
                .thenBy { entry -> entry.idempotencyKey }
        )
        .toList()
    if (queuedEntries.isEmpty()) return messages

    val restored = messages.toMutableList()
    queuedEntries.forEach { entry ->
        val existingIndex = restored.indexOfFirst { message ->
            message.id == queuedUserMessageId(entry) ||
                message.runId == queuedUserRunId(entry)
        }
        val recoveredMessage = existingIndex
            .takeIf { it >= 0 }
            ?.let { index ->
                restored[index].copy(
                    deliveryState = "queued",
                    clientMessageText = entry.content,
                    queuePosition = entry.queuePosition
                )
            }
            ?: buildQueuedTimelineOutboxUserMessage(entry)
        if (existingIndex >= 0) {
            restored[existingIndex] = recoveredMessage
        } else {
            restored += recoveredMessage
        }
    }
    return restored
}

internal fun buildQueuedTimelineOutboxUserMessage(entry: TimelineOutboxEntry): ChatMessage {
    val clientMessageId = entry.clientMessageId.trim()
    val messageId = queuedUserMessageId(entry)
    val normalizedContent = entry.content.trim().takeIf { it.isNotEmpty() && it != " " }.orEmpty()
    val attachmentBlocks = entry.attachments.map { attachment ->
        RelayChatContentBlock(
            type = "file",
            attachmentId = attachment.fileId,
            fileId = attachment.fileId,
            fileName = attachment.fileName,
            mimeType = attachment.mimeType,
            sizeBytes = attachment.sizeBytes.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
            sha256 = attachment.sha256,
            sourceRunId = attachment.sourceRunId?.trim()?.takeIf { it.isNotEmpty() }
                ?: entry.idempotencyKey
        )
    }
    val contentBlocks = when {
        normalizedContent.isBlank() -> attachmentBlocks
        attachmentBlocks.isEmpty() -> emptyList()
        else -> listOf(RelayChatContentBlock(type = "text", text = normalizedContent)) + attachmentBlocks
    }
    return ChatMessage(
        id = messageId,
        role = MessageRole.user,
        state = MessageState.completed,
        content = normalizedContent,
        contentBlocks = contentBlocks,
        createdAt = "",
        runId = queuedUserRunId(entry),
        sortTimestamp = entry.createdAtEpochMs / 1_000.0,
        timelineOrderKey = localTimelineOrderKey(clientMessageId, 10, messageId),
        timelineIdentityKey = localTimelineIdentityKey("message:user", clientMessageId),
        timelineItemKind = "message:user",
        deliveryState = "queued",
        clientMessageText = entry.content,
        queuePosition = entry.queuePosition
    )
}

private fun queuedUserMessageId(entry: TimelineOutboxEntry): String {
    return "user-${entry.clientMessageId.trim()}"
}

private fun queuedUserRunId(entry: TimelineOutboxEntry): String {
    return "local-user-${entry.clientMessageId.trim()}"
}

private fun queuedTimelineOutboxOrder(entry: TimelineOutboxEntry): Long {
    return entry.queuePosition ?: entry.createdAtEpochMs
}
