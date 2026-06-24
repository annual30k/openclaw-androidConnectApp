package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import kotlinx.serialization.json.JsonObject

internal fun pendingAssistantMessageForFinal(
    scope: ChatEventScope,
    messages: List<ChatMessage>,
    streamingMessageId: String?
): ChatMessage? {
    scope.runScope?.assistantMessageId
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { assistantMessageId ->
            messages.firstOrNull { it.id == assistantMessageId }?.let { return it }
        }
    streamingMessageId
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { assistantMessageId ->
            messages.firstOrNull { it.id == assistantMessageId }?.let { return it }
        }
    return null
}

internal fun attachmentSourceRunId(
    payload: JsonObject,
    runId: String,
    contentBlocks: List<RelayChatContentBlock>,
    runScope: ChatRunScope?,
    messages: List<ChatMessage>
): String? {
    if (contentBlocks.none { it.isFileBlock || it.isVoiceMessageBlock }) return null
    return contentBlocks.firstNotNullOfOrNull { block ->
        block.sourceRunId?.trim()?.takeIf { it.isNotEmpty() }
    }
        ?: payload.string("sourceRunId", "source_run_id")?.trim()?.takeIf { it.isNotEmpty() }
        ?: runId.trim().takeIf { it.isNotEmpty() && !it.startsWith("file-") }
        ?: pendingRunIdentityForAttachment(runScope, messages)
}

private fun pendingRunIdentityForAttachment(
    runScope: ChatRunScope?,
    messages: List<ChatMessage>
): String? {
    val assistantMessageId = runScope?.assistantMessageId?.trim()?.takeIf { it.isNotEmpty() }
    if (assistantMessageId != null) {
        val assistantRunId = messages.firstOrNull { message ->
            message.id == assistantMessageId &&
                message.role == MessageRole.assistant &&
                message.state == MessageState.streaming
        }?.runId?.trim()
        normalizeAttachmentSourceRunId(assistantRunId)?.let { return it }
    }
    val triggeringUserMessageId = runScope?.triggeringUserMessageId?.trim()?.takeIf { it.isNotEmpty() }
    if (triggeringUserMessageId != null) {
        val userRunId = messages.firstOrNull { message ->
            message.id == triggeringUserMessageId &&
                message.role == MessageRole.user
        }?.runId?.trim()
        normalizeAttachmentSourceRunId(userRunId)?.let { return it }
    }
    return null
}

private fun normalizeAttachmentSourceRunId(value: String?): String? {
    val normalized = value
        ?.trim()
        ?.removePrefix("local-user-")
        ?.trim()
        ?.takeIf { it.isNotEmpty() && !it.startsWith("file-") }
    return normalized
}

internal fun contentBlocksWithAttachmentSourceRunId(
    contentBlocks: List<RelayChatContentBlock>,
    sourceRunId: String?
): List<RelayChatContentBlock> {
    val normalizedSourceRunId = sourceRunId?.trim()?.takeIf { it.isNotEmpty() } ?: return contentBlocks
    var changed = false
    val updated = contentBlocks.map { block ->
        if ((block.isFileBlock || block.isVoiceMessageBlock) && block.sourceRunId.isNullOrBlank()) {
            changed = true
            block.copy(sourceRunId = normalizedSourceRunId)
        } else {
            block
        }
    }
    return if (changed) updated else contentBlocks
}

internal fun attachmentIdentityForOrder(contentBlocks: List<RelayChatContentBlock>): String? {
    return contentBlocks.firstNotNullOfOrNull { block ->
        block.attachmentId?.trim()?.takeIf { it.isNotEmpty() }
            ?: block.fileId?.trim()?.takeIf { it.isNotEmpty() }
            ?: block.fileDownloadURLString?.trim()?.takeIf { it.isNotEmpty() }
    }
}
