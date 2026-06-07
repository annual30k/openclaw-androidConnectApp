package com.rethinkingstudio.clawlink.ui.screens.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import com.rethinkingstudio.clawlink.core.state.chat.ChatState

internal fun conversationDisplayMessages(
    messages: List<ChatMessage>,
    showInvocationProcess: Boolean
): List<ChatMessage> {
    return messages
        .coalescedByMessageId()
        .filter { message ->
            message.shouldDisplayInChat(showInvocationProcess = showInvocationProcess) ||
                message.state == MessageState.streaming && message.role == MessageRole.assistant
        }
}

internal fun conversationStructureSignature(messages: List<ChatMessage>): String {
    return messages.joinToString(separator = "\u001F") { message ->
        listOf(
            message.id,
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
        message.id,
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

private fun List<ChatMessage>.coalescedByMessageId(): List<ChatMessage> {
    val merged = linkedMapOf<String, ChatMessage>()
    forEachIndexed { index, message ->
        val key = message.id.trim().ifBlank { "blank-message-id-$index" }
        val existingKey = merged.entries.firstOrNull { (existingMessageKey, existingMessage) ->
            existingMessageKey == key || displayFileMessagesMatch(existingMessage, message)
        }?.key
        val resolvedKey = existingKey ?: key
        val existing = merged[resolvedKey]
        merged[resolvedKey] = if (existing == null) {
            message
        } else {
            existing.mergingDuplicateIdMessage(message)
        }
    }
    return merged.values.toList()
}

private fun ChatMessage.mergingDuplicateIdMessage(incoming: ChatMessage): ChatMessage {
    return copy(
        content = content.trim().takeIf { it.isNotEmpty() }?.let { content } ?: incoming.content,
        contentBlocks = contentBlocks.ifEmpty { incoming.contentBlocks },
        state = mergedMessageState(state, incoming.state),
        createdAt = createdAt.ifBlank { incoming.createdAt },
        runId = runId.ifBlank { incoming.runId },
        sortTimestamp = sortTimestamp ?: incoming.sortTimestamp
    )
}

private fun displayFileMessagesMatch(existing: ChatMessage, incoming: ChatMessage): Boolean {
    val existingBlocks = existing.displayTransferBlocks()
    val incomingBlocks = incoming.displayTransferBlocks()
    if (existingBlocks.isEmpty() || incomingBlocks.isEmpty()) return false
    val existingRunId = existing.runId.trim()
    val incomingRunId = incoming.runId.trim()
    val allowMetadataFallback = existingRunId.isNotEmpty() && existingRunId == incomingRunId
    return existingBlocks.any { existingBlock ->
        incomingBlocks.any { incomingBlock ->
            displayTransferBlocksMatch(
                existing = existingBlock,
                incoming = incomingBlock,
                allowMetadataFallback = allowMetadataFallback
            )
        }
    }
}

private fun ChatMessage.displayTransferBlocks(): List<RelayChatContentBlock> {
    return fileContentBlocks + voiceContentBlocks
}

private fun displayTransferBlocksMatch(
    existing: RelayChatContentBlock,
    incoming: RelayChatContentBlock,
    allowMetadataFallback: Boolean
): Boolean {
    val existingFileId = existing.fileId?.trim()?.takeIf { it.isNotEmpty() }
    val incomingFileId = incoming.fileId?.trim()?.takeIf { it.isNotEmpty() }
    if (existingFileId != null && incomingFileId != null) {
        return existingFileId == incomingFileId
    }
    if (!allowMetadataFallback) return false

    val existingName = displayFileName(existing)
    val incomingName = displayFileName(incoming)
    if (existingName.isBlank() || existingName != incomingName) return false

    val existingMime = existing.mimeType?.trim()?.lowercase().orEmpty()
    val incomingMime = incoming.mimeType?.trim()?.lowercase().orEmpty()
    if (!displayMimeTypesCompatible(existingMime, incomingMime)) return false

    if (existing.sizeBytes != null && incoming.sizeBytes != null && existing.sizeBytes != incoming.sizeBytes) {
        return false
    }
    if (existing.imageWidth != null && incoming.imageWidth != null && existing.imageWidth != incoming.imageWidth) {
        return false
    }
    if (existing.imageHeight != null && incoming.imageHeight != null && existing.imageHeight != incoming.imageHeight) {
        return false
    }
    return true
}

private fun displayFileName(block: RelayChatContentBlock): String {
    return (block.fileName ?: block.name ?: block.text ?: block.downloadUrl ?: block.downloadPath)
        .orEmpty()
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .trim()
        .lowercase()
}

private fun displayMimeTypesCompatible(existing: String, incoming: String): Boolean {
    if (existing.isBlank() || incoming.isBlank()) return true
    if (existing == incoming) return true
    if (existing == "application/octet-stream" || incoming == "application/octet-stream") return true
    if (existing.startsWith("image/") && incoming.startsWith("image/")) return true
    if (existing.startsWith("audio/") && incoming.startsWith("audio/")) return true
    return false
}

private fun mergedMessageState(existing: MessageState, incoming: MessageState): MessageState {
    return when {
        incoming == MessageState.failed -> MessageState.failed
        existing == MessageState.completed || incoming == MessageState.completed -> MessageState.completed
        existing == MessageState.streaming || incoming == MessageState.streaming -> MessageState.streaming
        else -> incoming
    }
}
