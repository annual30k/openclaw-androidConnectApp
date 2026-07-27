package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import com.rethinkingstudio.clawlink.core.network.dto.ChatHistoryItem
import java.time.Instant
import java.time.format.DateTimeParseException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val messageOrderEpsilon = 0.001
private const val historyTranscriptOrderWindowSeconds = 900.0
internal const val protocolTypingMarkerText = "[[clawlink:typing]]"
private val protocolTypingMarkerRegex = Regex("^(?:\\[\\[clawlink:typing]]\\s*)+$")

private val ChatMessage.hasCanonicalTimelineOrder: Boolean
    get() = timelineOrderKey.trim().isNotEmpty() &&
        timelineIdentityKey.trim().isNotEmpty() &&
        timelineItemKind.trim().isNotEmpty()

private val ChatMessage.hasLocalTimelineOrder: Boolean
    get() = timelineOrderKey.trim().startsWith("local:")

private fun ChatMessage.canonicalTurnIdentity(): String? {
    val run = normalizeTurnIdentity(runId)
    if (run != null) return run
    return normalizeTurnIdentity(timelineMessageId)
}

private fun normalizeTurnIdentity(value: String): String? {
    var normalized = value.trim()
    if (normalized.isEmpty()) return null
    normalized = normalized.removePrefix("local-user-").removePrefix("user-").trim()
    normalized = normalized.replace(Regex(":(user|assistant|tool|system|waiting)$", RegexOption.IGNORE_CASE), "").trim()
    return normalized.takeIf { it.isNotEmpty() }
}

internal fun extractContent(item: ChatHistoryItem): String {
    return sanitizeChatMessageText(extractRawContent(item))
}

private fun extractRawContent(item: ChatHistoryItem): String {
    val content = item.content ?: return ""
    return when {
        content is JsonPrimitive && content.isString -> content.content
        content is JsonArray -> content.mapNotNull { element ->
            (element as? JsonObject)?.let { block ->
                block.string("text")
                    ?: block["content"]?.let { nested ->
                        when {
                            nested is JsonPrimitive && nested.isString -> nested.content
                            else -> null
                        }
                    }
            }
        }.joinToString("\n\n").trim()
        else -> try {
            content.jsonObject["text"]?.jsonPrimitive?.content ?: ""
        } catch (_: Exception) {
            ""
        }
    }
}

internal fun normalizedMessageRole(role: String): String {
    return role.trim().lowercase().replace("_", "")
}

internal fun messageRole(role: String): MessageRole {
    return when (normalizedMessageRole(role)) {
        "user" -> MessageRole.user
        "assistant" -> MessageRole.assistant
        "system" -> MessageRole.system
        "tool", "toolresult" -> MessageRole.tool
        else -> MessageRole.assistant
    }
}

internal fun parseHistoryTimestamp(raw: String?): Double? {
    val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return try {
        Instant.parse(trimmed).toEpochMilli() / 1000.0
    } catch (_: DateTimeParseException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}

internal fun buildNotificationPreview(content: String, contentBlocks: List<RelayChatContentBlock>): String {
    val plainText = sanitizeChatMessageText(content).ifBlank {
        sanitizeChatContentBlocks(contentBlocks).firstNotNullOfOrNull { block ->
            block.text?.trim()?.takeIf { it.isNotBlank() }
                ?: block.transcript?.trim()?.takeIf { it.isNotBlank() }
        } ?: ""
    }
    if (plainText.isBlank()) return ""
    if (isProtocolTypingMarkerText(plainText)) return ""
    return plainText.replace(Regex("\\s+"), " ").take(140)
}

internal fun isProtocolTypingMarkerText(text: String): Boolean {
    val trimmed = text.trim()
    return trimmed.isNotEmpty() && protocolTypingMarkerRegex.matches(trimmed)
}

internal fun buildHistoryMessagesFromItems(items: List<ChatHistoryItem>): List<ChatMessage> {
    var previousSortTimestamp: Double? = null
    return items
        .filter {
            !it.timelineOrderKey.isNullOrBlank() &&
                !it.timelineIdentityKey.isNullOrBlank() &&
                !it.timelineItemKind.isNullOrBlank()
        }
        .mapIndexedNotNull { index, item ->
        val rawExtractedContent = extractRawContent(item)
        val extractedContent = sanitizeChatMessageText(rawExtractedContent)
        val sourceBlocks = sanitizeChatContentBlocks(item.contentBlocks ?: emptyList())
        if (sourceBlocks.isEmpty() && isProtocolTypingMarkerText(extractedContent)) {
            return@mapIndexedNotNull null
        }
        val normalizedRole = normalizedMessageRole(item.role)
        val canonicalItemKind = item.timelineItemKind?.trim()?.lowercase().orEmpty()
        val isToolRole = normalizedRole in listOf("tool", "toolresult")
        val hasCanonicalChatBody = sourceBlocks.any { block ->
            block.isFileBlock ||
                block.isVoiceMessageBlock ||
                (block.isTextBlock && !block.text.isNullOrBlank())
        }
        val isToolHistory = when (canonicalItemKind) {
            "message:assistant", "message:user" -> isToolRole ||
                (sourceBlocks.any { it.isToolCallBlock || it.isToolResultBlock } && !hasCanonicalChatBody)
            "tool" -> true
            else -> isToolRole || sourceBlocks.any { it.isToolCallBlock || it.isToolResultBlock }
        }
        val role = if (isToolHistory) {
            MessageRole.tool
        } else {
            messageRole(item.role)
        }
        val content = when {
            sourceBlocks.isNotEmpty() && role == MessageRole.tool -> sourceBlocks.firstNotNullOfOrNull { block ->
                block.text?.trim()?.takeIf { it.isNotEmpty() }
                    ?: block.result?.renderedText(listOf("content", "markdown", "text", "body", "message", "value", "result", "output"))
                    ?: block.partialResult?.renderedText(listOf("content", "markdown", "text", "body", "message", "value", "result", "output"))
                    ?: block.content?.renderedText(listOf("content", "markdown", "text", "body", "message", "value", "result", "output"))
                    ?: block.output?.renderedText(listOf("content", "markdown", "text", "body", "message", "value", "result", "output"))
                    ?: block.error?.renderedText(listOf("content", "markdown", "text", "body", "message", "value", "result", "output"))
            }.orEmpty()
            else -> extractedContent
        }
        val structuredSortTimestamp = historyItemStructuredSortTimestamp(item)
        val rawSortTimestamp = structuredSortTimestamp ?: parseHistoryTimestamp(item.createdAt)
        val syntheticFloor = previousSortTimestamp?.plus(messageOrderEpsilon)
            ?: (index * messageOrderEpsilon)
        val sortTimestamp = structuredSortTimestamp ?: historySortTimestamp(
                rawSortTimestamp = rawSortTimestamp,
                previousSortTimestamp = previousSortTimestamp,
                syntheticFloor = syntheticFloor
            )
        previousSortTimestamp = maxOf(previousSortTimestamp ?: sortTimestamp, sortTimestamp)

        ChatMessage(
            id = item.id,
            role = role,
            content = content,
            contentBlocks = sourceBlocks,
            createdAt = item.createdAt ?: "",
            runId = item.id,
            sortTimestamp = sortTimestamp,
            seq = item.conversationSeq ?: item.seq,
            timelineOrderKey = item.timelineOrderKey.orEmpty(),
            timelineIdentityKey = item.timelineIdentityKey.orEmpty(),
            timelineItemKind = item.timelineItemKind.orEmpty(),
            timelineResolvesWaiting = item.timelineResolvesWaiting,
            source = item.source.orEmpty()
        )
    }
}

private fun historyItemStructuredSortTimestamp(item: ChatHistoryItem): Double? {
    item.sortTimestamp?.let { return it }
    item.sortTimestampMs?.let { return it / 1000.0 }
    return null
}

private fun historySortTimestamp(
    rawSortTimestamp: Double?,
    previousSortTimestamp: Double?,
    syntheticFloor: Double
): Double {
    val raw = rawSortTimestamp ?: return syntheticFloor
    val previous = previousSortTimestamp ?: return raw
    if (raw >= syntheticFloor) return raw

    val backwardJumpSeconds = previous - raw
    return if (backwardJumpSeconds <= historyTranscriptOrderWindowSeconds) {
        syntheticFloor
    } else {
        raw
    }
}

internal fun mergeHistoryWithCurrentMessages(
    historyMessages: List<ChatMessage>,
    currentMessages: List<ChatMessage>,
    currentStreamingMessageId: String?,
    isTrackedPendingAssistantMessageId: (String) -> Boolean
): List<ChatMessage> {
    return mergeCanonicalHistoryWithCurrentMessages(
        historyMessages = historyMessages,
        currentMessages = currentMessages,
        currentStreamingMessageId = currentStreamingMessageId,
        isTrackedPendingAssistantMessageId = isTrackedPendingAssistantMessageId
    )
}

private fun mergeCanonicalHistoryWithCurrentMessages(
    historyMessages: List<ChatMessage>,
    currentMessages: List<ChatMessage>,
    currentStreamingMessageId: String?,
    isTrackedPendingAssistantMessageId: (String) -> Boolean
): List<ChatMessage> {
    val byIdentity = linkedMapOf<String, ChatMessage>()
    historyMessages
        .filter { it.hasCanonicalTimelineOrder }
        .forEach { message -> byIdentity[message.timelineIdentityKey] = message }

    val canonicalUserTurnIds = historyMessages
        .filter { it.role == MessageRole.user }
        .mapNotNull { it.canonicalTurnIdentity() }
        .toSet()
    val pendingTurnIds = currentMessages
        .filter { message ->
            message.id == currentStreamingMessageId ||
                isTrackedPendingAssistantMessageId(message.id) ||
                (message.role == MessageRole.assistant &&
                    message.state in setOf(MessageState.pending, MessageState.streaming) &&
                    isTransientAssistantPlaceholder(message))
        }
        .mapNotNull { it.canonicalTurnIdentity() }
        .toSet()
    val pendingOverlay = currentMessages.filter { message ->
        (!message.hasCanonicalTimelineOrder || message.hasLocalTimelineOrder) &&
            shouldPreserveCurrentMessageAcrossHistoryRefresh(
                message = message,
                currentStreamingMessageId = currentStreamingMessageId,
                isTrackedPendingAssistantMessageId = isTrackedPendingAssistantMessageId
            ) &&
            message.canonicalTurnIdentity()?.let { pendingTurnIds.isEmpty() || it in pendingTurnIds } ?: true &&
            message.canonicalTurnIdentity()?.let { it !in canonicalUserTurnIds } ?: true
    }

    return sortTimelineMessagesV3(byIdentity.values.toList() + pendingOverlay)
}

internal fun orderTimelineMessages(messages: List<ChatMessage>): List<ChatMessage> {
    return sortTimelineMessagesV3(messages)
}

private fun shouldPreserveCurrentMessageAcrossHistoryRefresh(
    message: ChatMessage,
    currentStreamingMessageId: String?,
    isTrackedPendingAssistantMessageId: (String) -> Boolean
): Boolean {
    if (isTransientAssistantPlaceholder(message)) {
        val isTrackedPending = isTrackedPendingAssistantMessageId(message.id)
        return message.state == MessageState.streaming &&
            (message.id == currentStreamingMessageId || isTrackedPending)
    }
    if (message.runId.startsWith("local-user-")) return true
    if (message.state == MessageState.streaming || message.state == MessageState.failed) return true
    if (message.role != MessageRole.assistant || message.content.trim().isEmpty()) return false
    return message.runId.isNotBlank()
}

internal fun isTransientAssistantPlaceholder(message: ChatMessage): Boolean {
    if (message.role != MessageRole.assistant) return false
    if (message.hasFileContent || message.hasVoiceContent || message.hasToolContent) return false
    return isTransientAssistantPlaceholderContent(message.content)
}

internal fun isTransientAssistantPlaceholderContent(content: String): Boolean {
    val trimmed = content.trim()
    return trimmed.isEmpty() ||
        trimmed.startsWith("正在连接") ||
        trimmed.startsWith("连接中") ||
        trimmed.startsWith("Connecting") ||
        trimmed.startsWith("连接中断") ||
        trimmed.startsWith("Connection interrupted") ||
        trimmed == "正在同步回复..." ||
        trimmed == "Syncing reply..." ||
        trimmed == "已完成，但未返回文本。" ||
        trimmed == "Completed, but no text was returned." ||
        trimmed == "正在同步最终内容..." ||
        trimmed == "Syncing final content..." ||
        trimmed == "等待宿主机识别语音..." ||
        trimmed == "Waiting for host transcription..." ||
        isProtocolTypingMarkerText(trimmed)
}

internal fun sameFileMessage(existing: ChatMessage, candidate: ChatMessage): Boolean {
    // 文件 identity 只能在同 role 消息内去重；跨 role 共享同一 attachment/file 引用时也必须保留为独立气泡。
    if (existing.role != candidate.role) return false

    val existingCanonicalRunId = canonicalFileRunId(existing)
    val candidateCanonicalRunId = canonicalFileRunId(candidate)
    if (!existingCanonicalRunId.isNullOrBlank() && existingCanonicalRunId == candidateCanonicalRunId) {
        return true
    }

    val existingFileIds = existing.transferContentBlocks().mapNotNull { block ->
        block.fileId?.trim()?.takeIf { it.isNotEmpty() }
    }.toSet()
    val candidateFileIds = candidate.transferContentBlocks().mapNotNull { block ->
        block.fileId?.trim()?.takeIf { it.isNotEmpty() }
    }.toSet()
    if (existingFileIds.isNotEmpty() &&
        candidateFileIds.isNotEmpty() &&
        existingFileIds.intersect(candidateFileIds).isNotEmpty()
    ) {
        return true
    }

    val existingBlocks = existing.transferContentBlocks()
    val candidateBlocks = candidate.transferContentBlocks()
    if (existingBlocks.isNotEmpty() &&
        candidateBlocks.isNotEmpty() &&
        existingBlocks.any { existingBlock ->
            candidateBlocks.any { candidateBlock -> transferBlocksReferToSameFile(existingBlock, candidateBlock) }
        }
    ) {
        return true
    }

    return samePendingUploadMessage(existing, candidate) || samePendingUploadMessage(candidate, existing)
}

private fun transferBlocksReferToSameFile(left: RelayChatContentBlock, right: RelayChatContentBlock): Boolean {
    val leftStableId = left.attachmentId?.trim()?.takeIf { it.isNotEmpty() }
        ?: left.fileId?.trim()?.takeIf { it.isNotEmpty() }
    val rightStableId = right.attachmentId?.trim()?.takeIf { it.isNotEmpty() }
        ?: right.fileId?.trim()?.takeIf { it.isNotEmpty() }
    val leftProjection = left.projectionOf?.trim()?.takeIf { it.isNotEmpty() }
    val rightProjection = right.projectionOf?.trim()?.takeIf { it.isNotEmpty() }
    if ((leftProjection != null && leftProjection == rightStableId) ||
        (rightProjection != null && rightProjection == leftStableId)
    ) {
        return true
    }

    val leftAttachmentId = left.attachmentId?.trim()?.takeIf { it.isNotEmpty() }
    val rightAttachmentId = right.attachmentId?.trim()?.takeIf { it.isNotEmpty() }
    if (leftAttachmentId != null || rightAttachmentId != null) {
        return leftAttachmentId != null && leftAttachmentId == rightAttachmentId
    }

    val leftFileId = left.fileId?.trim()?.takeIf { it.isNotEmpty() }
    val rightFileId = right.fileId?.trim()?.takeIf { it.isNotEmpty() }
    if (leftFileId != null || rightFileId != null) {
        return leftFileId != null && leftFileId == rightFileId
    }
    return false
}

private fun canonicalFileRunId(message: ChatMessage): String? {
    message.transferContentBlocks().firstNotNullOfOrNull { block ->
        block.fileId?.trim()?.takeIf { it.isNotEmpty() }
    }?.let { return fileMessageRunId(it) }

    return message.runId.trim().takeIf { it.startsWith("file-") }
}

internal fun ChatMessage.transferContentBlocks(): List<RelayChatContentBlock> {
    return fileContentBlocks + voiceContentBlocks
}

internal fun samePendingUploadMessage(pending: ChatMessage, completed: ChatMessage): Boolean {
    if (pending.role != completed.role) return false

    val isLocalUploadPlaceholder = pending.runId.startsWith("upload-") || pending.state == MessageState.streaming
    if (!isLocalUploadPlaceholder) return false

    val pendingBlock = pending.transferContentBlocks().firstOrNull() ?: return false
    if (!pendingBlock.fileId.isNullOrBlank()) return false

    val completedBlock = completed.transferContentBlocks().firstOrNull { !it.fileId.isNullOrBlank() } ?: return false
    // 只允许显式稳定身份把上传占位和完成文件合并；同名/同大小文件在同一会话里也必须保留为独立传输。
    val pendingAttachmentId = pendingBlock.attachmentId?.trim()?.takeIf { it.isNotEmpty() }
    val completedAttachmentId = completedBlock.attachmentId?.trim()?.takeIf { it.isNotEmpty() }
    if (pendingAttachmentId != null || completedAttachmentId != null) {
        return pendingAttachmentId != null && pendingAttachmentId == completedAttachmentId
    }

    val pendingFileId = pendingBlock.fileId?.trim()?.takeIf { it.isNotEmpty() }
    val completedFileId = completedBlock.fileId?.trim()?.takeIf { it.isNotEmpty() }
    if (pendingFileId != null || completedFileId != null) {
        return pendingFileId != null && pendingFileId == completedFileId
    }

    return false
}

internal fun samePendingUploadMessageByUnambiguousSourceRunId(
    messages: List<ChatMessage>,
    pending: ChatMessage,
    completed: ChatMessage
): Boolean {
    if (pending.role != MessageRole.user || completed.role != MessageRole.user) return false

    val isLocalUploadPlaceholder = pending.runId.startsWith("upload-") || pending.state == MessageState.streaming
    if (!isLocalUploadPlaceholder) return false

    val pendingBlock = pending.transferContentBlocks().singleOrNull() ?: return false
    if (!pendingBlock.fileId.isNullOrBlank()) return false
    val completedBlock = completed.transferContentBlocks().firstOrNull { !it.fileId.isNullOrBlank() } ?: return false

    val pendingSourceRunId = normalizedTurnIdentity(pendingBlock.sourceRunId) ?: return false
    val completedSourceRunId = normalizedTurnIdentity(completedBlock.sourceRunId) ?: return false
    if (pendingSourceRunId != completedSourceRunId) return false

    val sameSourcePendingCount = messages.count { message ->
        message.role == MessageRole.user &&
            (message.runId.startsWith("upload-") || message.state == MessageState.streaming) &&
            message.transferContentBlocks().any { block ->
                block.fileId.isNullOrBlank() &&
                    normalizedTurnIdentity(block.sourceRunId) == pendingSourceRunId
            }
    }
    // sourceRunId 只能在“当前就这一条本地 user 上传占位”时作为兜底稳定身份；
    // 同一轮多附件会共享 sourceRunId，assistant/tool 侧文件输出也会复用它，不能在这里跨角色猜测归属。
    return sameSourcePendingCount == 1
}

internal fun fileMessageRunId(fileId: String): String {
    return "file-${fileId.trim()}"
}
