package com.rethinkingstudio.clawlink.ui.screens.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.state.chat.ChatState

internal fun conversationDisplayMessages(
    messages: List<ChatMessage>,
    showInvocationProcess: Boolean
): List<ChatMessage> {
    return messages
        .coalescedByCanonicalIdentity()
        .filter { message ->
            message.shouldDisplayInChat(showInvocationProcess = showInvocationProcess) ||
                message.state == MessageState.streaming && message.role == MessageRole.assistant
        }
}

internal fun conversationStructureSignature(messages: List<ChatMessage>): String {
    return messages.joinToString(separator = "\u001F") { message ->
        listOf(
            message.timelineIdentityKey.ifBlank { message.id },
            message.timelineOrderKey,
            message.role.name,
            message.state.name,
            message.runId,
            message.contentBlocks.hashCode().toString()
        ).joinToString(separator = "\u001E")
    }
}

internal fun conversationStreamingTailSignature(messages: List<ChatMessage>): String {
    val message = messages.lastOrNull {
        it.role == MessageRole.assistant && it.state == MessageState.streaming
    } ?: return ""
    return listOf(
        message.timelineIdentityKey.ifBlank { message.id },
        message.content.length.toString(),
        message.content.hashCode().toString(),
        message.contentBlocks.hashCode().toString()
    ).joinToString(separator = "\u001E")
}

internal fun shouldCoalesceChatDisplayUpdate(
    current: ChatState,
    incoming: ChatState
): Boolean {
    if (current.copy(messages = emptyList()) != incoming.copy(messages = emptyList())) {
        return false
    }
    if (current.messages.size != incoming.messages.size || incoming.messages.isEmpty()) {
        return false
    }

    val lastIndex = incoming.messages.lastIndex
    for (index in 0 until lastIndex) {
        if (current.messages[index] != incoming.messages[index]) return false
    }

    val currentTail = current.messages[lastIndex]
    val incomingTail = incoming.messages[lastIndex]
    if (incomingTail.role != MessageRole.assistant || incomingTail.state != MessageState.streaming) {
        return false
    }
    return currentTail.copy(content = incomingTail.content) == incomingTail
}

private fun List<ChatMessage>.coalescedByCanonicalIdentity(): List<ChatMessage> {
    val merged = linkedMapOf<String, ChatMessage>()
    forEachIndexed { index, message ->
        val key = message.timelineIdentityKey.trim()
            .ifBlank { message.id.trim() }
            .ifBlank { "blank-message-id-$index" }
        val existing = merged[key]
        merged[key] = if (existing == null) message else mergeSameIdentityDisplayMessage(existing, message)
    }
    return merged.values.toList()
}

private fun mergeSameIdentityDisplayMessage(existing: ChatMessage, incoming: ChatMessage): ChatMessage {
    return incoming.copy(
        content = incoming.content.ifBlank { existing.content },
        contentBlocks = incoming.contentBlocks.ifEmpty { existing.contentBlocks },
        createdAt = incoming.createdAt.ifBlank { existing.createdAt },
        runId = incoming.runId.ifBlank { existing.runId },
        sortTimestamp = incoming.sortTimestamp ?: existing.sortTimestamp,
        seq = incoming.seq ?: existing.seq,
        turnSeq = incoming.turnSeq ?: existing.turnSeq,
        timelineStableKey = incoming.timelineStableKey.ifBlank { existing.timelineStableKey },
        timelineMessageId = incoming.timelineMessageId.ifBlank { existing.timelineMessageId },
        timelinePartId = incoming.timelinePartId.ifBlank { existing.timelinePartId },
        timelineOrderKey = incoming.timelineOrderKey.ifBlank { existing.timelineOrderKey },
        timelineIdentityKey = incoming.timelineIdentityKey.ifBlank { existing.timelineIdentityKey },
        timelineItemKind = incoming.timelineItemKind.ifBlank { existing.timelineItemKind }
    )
}
