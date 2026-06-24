package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock

internal fun mergeLocalUserMessage(local: ChatMessage, incoming: ChatMessage): ChatMessage {
    val mergedBlocks = mergedLocalUserContentBlocks(local = local, incoming = incoming)
    return incoming.copy(
        content = local.content.takeIf { it.trim().isNotEmpty() } ?: incoming.content,
        contentBlocks = mergedBlocks,
        createdAt = incoming.createdAt.ifBlank { local.createdAt },
        runId = local.runId.takeIf { it.startsWith("local-user-") } ?: incoming.runId,
        sortTimestamp = local.sortTimestamp ?: incoming.sortTimestamp,
        seq = incoming.seq ?: local.seq,
        turnSeq = incoming.turnSeq ?: local.turnSeq,
        timelineStableKey = incoming.timelineStableKey.ifBlank { local.timelineStableKey },
        timelineMessageId = incoming.timelineMessageId.ifBlank { local.timelineMessageId },
        timelinePartId = incoming.timelinePartId.ifBlank { local.timelinePartId }
    )
}

private fun mergedLocalUserContentBlocks(local: ChatMessage, incoming: ChatMessage): List<RelayChatContentBlock> {
    if (local.hasVoiceContent) {
        val transcript = userPromptText(content = incoming.content, contentBlocks = incoming.contentBlocks).trim()
        if (transcript.isNotBlank()) {
            return local.contentBlocks.map { block ->
                if (block.isVoiceMessageBlock) block.copy(transcript = transcript) else block
            }
        }
    }
    if (local.contentBlocks.isEmpty()) return incoming.contentBlocks
    if (incoming.contentBlocks.isEmpty()) return local.contentBlocks
    if (local.hasFileContent && incoming.hasFileContent) {
        return mergeCompletedFileMessage(existing = local, completed = incoming).contentBlocks
    }
    return local.contentBlocks
}

private fun userPromptText(content: String, contentBlocks: List<RelayChatContentBlock>): String {
    val blockText = contentBlocks.mapNotNull { block ->
        if (block.isFileBlock || block.isVoiceMessageBlock || block.isToolCallBlock || block.isToolResultBlock) {
            null
        } else {
            block.text?.trim()?.takeIf { it.isNotEmpty() }
        }
    }.joinToString("\n\n")
    return blockText.ifBlank { content }
}
