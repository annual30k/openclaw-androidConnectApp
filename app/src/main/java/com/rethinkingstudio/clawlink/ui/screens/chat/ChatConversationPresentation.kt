package com.rethinkingstudio.clawlink.ui.screens.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock

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
    return existingBlocks.any { existingBlock ->
        incomingBlocks.any { incomingBlock -> displayTransferBlocksMatch(existingBlock, incomingBlock) }
    }
}

private fun ChatMessage.displayTransferBlocks(): List<RelayChatContentBlock> {
    return fileContentBlocks + voiceContentBlocks
}

private fun displayTransferBlocksMatch(
    existing: RelayChatContentBlock,
    incoming: RelayChatContentBlock
): Boolean {
    val existingFileId = existing.fileId?.trim()?.takeIf { it.isNotEmpty() }
    val incomingFileId = incoming.fileId?.trim()?.takeIf { it.isNotEmpty() }
    if (existingFileId != null && incomingFileId != null && existingFileId == incomingFileId) {
        return true
    }

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
