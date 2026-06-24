package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole

internal fun ChatTimelineState.upsertMessage(
    message: ChatMessage,
    replaceMessageId: String? = null,
    insertBeforeMessageId: String? = null
): List<ChatMessage> {
    // 时间线合并必须以协议身份键为准；缺少稳定 identityKey 时不做内容/位置猜测，避免吞掉同文案的独立消息。
    val identityKey = message.timelineIdentityKey.trim().takeIf { it.isNotEmpty() } ?: return messages
    val index = messages.indexOfFirst { current ->
        current.timelineIdentityKey == identityKey || (replaceMessageId != null && current.id == replaceMessageId)
    }
    if (index < 0 && insertBeforeMessageId != null) {
        val anchorIndex = messages.indexOfFirst { it.id == insertBeforeMessageId }
        if (anchorIndex >= 0) {
            return messages.toMutableList().also { it.add(anchorIndex, message) }
        }
    }
    if (index < 0) return messages + message
    return messages.toMutableList().also { current ->
        val existing = current[index]
        current[index] = message.copy(
            createdAt = message.createdAt.ifBlank { existing.createdAt },
            runId = message.runId.ifBlank { existing.runId },
            sortTimestamp = message.sortTimestamp ?: existing.sortTimestamp,
            seq = message.seq ?: existing.seq,
            turnSeq = message.turnSeq ?: existing.turnSeq,
            timelineStableKey = message.timelineStableKey.ifBlank { existing.timelineStableKey },
            timelineMessageId = message.timelineMessageId.ifBlank { existing.timelineMessageId },
            timelinePartId = message.timelinePartId.ifBlank { existing.timelinePartId },
            timelineOrderKey = message.timelineOrderKey.ifBlank { existing.timelineOrderKey },
            timelineIdentityKey = message.timelineIdentityKey.ifBlank { existing.timelineIdentityKey },
            timelineItemKind = message.timelineItemKind.ifBlank { existing.timelineItemKind },
            timelineResolvesWaiting = message.timelineResolvesWaiting ?: existing.timelineResolvesWaiting,
            source = message.source.ifBlank { existing.source }
        )
    }
}

internal fun ChatTimelineState.upsertToolMessage(message: ChatMessage): List<ChatMessage> {
    // 工具消息优先使用 timelineIdentityKey；老事件没有该键时才退回到 tool role + message id 的明确身份。
    val identityKey = message.timelineIdentityKey.trim().takeIf { it.isNotEmpty() }
    val index = messages.indexOfFirst { current ->
        if (identityKey != null) {
            current.timelineIdentityKey == identityKey
        } else {
            current.role == MessageRole.tool && current.id == message.id
        }
    }
    if (index < 0) return messages + message
    return messages.toMutableList().also { current ->
        val existing = current[index]
        current[index] = message.copy(
            createdAt = message.createdAt.ifBlank { existing.createdAt },
            runId = message.runId.ifBlank { existing.runId },
            sortTimestamp = message.sortTimestamp ?: existing.sortTimestamp,
            seq = message.seq ?: existing.seq,
            turnSeq = message.turnSeq ?: existing.turnSeq,
            timelineStableKey = message.timelineStableKey.ifBlank { existing.timelineStableKey },
            timelineMessageId = message.timelineMessageId.ifBlank { existing.timelineMessageId },
            timelinePartId = message.timelinePartId.ifBlank { existing.timelinePartId },
            timelineOrderKey = message.timelineOrderKey.ifBlank { existing.timelineOrderKey },
            timelineIdentityKey = message.timelineIdentityKey.ifBlank { existing.timelineIdentityKey },
            timelineItemKind = message.timelineItemKind.ifBlank { existing.timelineItemKind },
            timelineResolvesWaiting = message.timelineResolvesWaiting ?: existing.timelineResolvesWaiting,
            source = message.source.ifBlank { existing.source }
        )
    }
}
