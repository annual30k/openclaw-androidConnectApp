package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import java.time.Instant
import java.time.format.DateTimeParseException

internal const val timelineMessageOrderEpsilon = 0.001

internal fun ChatMessage.hasRenderableTimelineCompletedContent(): Boolean {
    return content.trim().isNotEmpty() || contentBlocks.hasRenderableTimelineCompletedContent()
}

internal fun TimelineEvent.MessageCompleted.clearsWaitingAssistant(): Boolean {
    timelineResolvesWaiting?.let { return it }
    val completedRole = role.toMessageRole(default = MessageRole.assistant)
    val kind = timelineItemKind?.trim()?.lowercase().orEmpty()
    if (kind == "message:assistant") return true
    return completedRole == MessageRole.assistant && !content.hasToolTimelineContent()
}

internal fun ChatMessage.hasAssistantAnswerTimelineContent(): Boolean {
    if (role != MessageRole.assistant) return false
    if (contentBlocks.hasNonAnswerResultContent()) return false
    val text = contentBlocks.assistantAnswerText().ifBlank { content.trim() }
    return text.isNotBlank() && !isTransientAssistantPlaceholderContent(text)
}

internal fun TimelineEvent.TurnUserCreated.hasCanonicalTimelineKeys(): Boolean {
    return !timelineOrderKey.isNullOrBlank() &&
        !timelineIdentityKey.isNullOrBlank() &&
        !timelineItemKind.isNullOrBlank()
}

internal fun TimelineEvent.MessagePartDelta.hasCanonicalTimelineKeys(): Boolean {
    return !timelineOrderKey.isNullOrBlank() &&
        !timelineIdentityKey.isNullOrBlank() &&
        !timelineItemKind.isNullOrBlank()
}

internal fun TimelineEvent.MessageCompleted.hasCanonicalTimelineKeys(): Boolean {
    return !timelineOrderKey.isNullOrBlank() &&
        !timelineIdentityKey.isNullOrBlank() &&
        !timelineItemKind.isNullOrBlank()
}

internal fun TimelineEvent.ToolInvocationUpdated.hasCanonicalTimelineKeys(): Boolean {
    return !timelineOrderKey.isNullOrBlank() &&
        !timelineIdentityKey.isNullOrBlank() &&
        !timelineItemKind.isNullOrBlank()
}

internal fun TimelineEvent.ToolInvocationUpdated.hasToolInvocationIdentity(): Boolean {
    return toolCallId.isNotBlank() || !messageId.isNullOrBlank()
}

internal fun String?.toMessageRole(default: MessageRole): MessageRole {
    return when (this?.trim()?.lowercase()) {
        "user" -> MessageRole.user
        "assistant" -> MessageRole.assistant
        "system" -> MessageRole.system
        "tool" -> MessageRole.tool
        else -> default
    }
}

internal fun String?.toMessageState(): MessageState? {
    return when (this?.trim()?.lowercase()) {
        "completed", "complete", "done", "success", "final", "result" -> MessageState.completed
        "streaming", "delta", "in_progress", "running", "active" -> MessageState.streaming
        "failed", "fail", "error", "cancelled", "canceled", "denied" -> MessageState.failed
        else -> null
    }
}

internal fun partKey(messageId: String, partId: String): String = "$messageId|$partId"

internal fun partSeqKey(messageId: String, partId: String, seq: Long): String = "$messageId|$partId|$seq"

internal fun List<RelayChatContentBlock>.timelineText(): String {
    return mapNotNull { it.text?.takeIf { value -> value.isNotBlank() } }.joinToString("\n\n")
}

internal fun List<RelayChatContentBlock>.hasRenderableTimelineCompletedContent(): Boolean {
    return any { block ->
        block.isToolCallBlock ||
            block.isToolResultBlock ||
            block.isFileBlock ||
            block.isVoiceMessageBlock ||
            !block.text.isNullOrBlank() ||
            !block.transcript.isNullOrBlank() ||
            listOf(block.result, block.partialResult, block.content, block.output, block.error).any { value ->
                !value?.renderedText(listOf("content", "markdown", "text", "body", "message", "value", "result", "output")).isNullOrBlank()
            }
    }
}

internal fun List<RelayChatContentBlock>.hasAssistantAnswerTimelineContent(): Boolean {
    if (hasNonAnswerResultContent()) return false
    val text = assistantAnswerText()
    return text.isNotBlank() && !isTransientAssistantPlaceholderContent(text)
}

internal fun List<RelayChatContentBlock>.canReplaceAssistantDeltaPlaceholder(): Boolean {
    if (hasNonAnswerResultContent()) return false
    val text = assistantAnswerText()
    return text.isNotBlank()
}

internal fun List<RelayChatContentBlock>.hasToolTimelineContent(): Boolean {
    return any { block -> block.isToolCallBlock || block.isToolResultBlock }
}

internal fun List<RelayChatContentBlock>.hasNonAnswerResultContent(): Boolean {
    return any { block ->
        block.isToolCallBlock ||
            block.isToolResultBlock ||
            block.isFileBlock ||
            block.isVoiceMessageBlock
    }
}

internal fun List<RelayChatContentBlock>.assistantAnswerText(): String {
    return mapNotNull { block ->
        block.text?.trim()?.takeIf { it.isNotEmpty() }
            ?: block.transcript?.trim()?.takeIf { it.isNotEmpty() }
    }.joinToString("\n\n").trim()
}

internal fun isWaitingOnlyStreamingContent(content: String): Boolean {
    return isTransientAssistantPlaceholderContent(content)
}

internal fun timelineSortTimestamp(createdAt: String?, fallback: Double? = null): Double? {
    val parsed = createdAt
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { value ->
            try {
                Instant.parse(value).toEpochMilli() / 1000.0
            } catch (_: DateTimeParseException) {
                null
            }
        }
    return parsed ?: fallback
}

internal fun String?.isLocalTimelineSource(): Boolean {
    return this?.trim()?.equals("local", ignoreCase = true) == true
}
