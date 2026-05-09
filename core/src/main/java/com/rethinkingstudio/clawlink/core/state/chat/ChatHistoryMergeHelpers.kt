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

internal fun extractContent(item: ChatHistoryItem): String {
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
    val plainText = content.trim().ifBlank {
        contentBlocks.firstNotNullOfOrNull { block ->
            block.text?.trim()?.takeIf { it.isNotBlank() }
                ?: block.transcript?.trim()?.takeIf { it.isNotBlank() }
        } ?: ""
    }
    if (plainText.isBlank()) return ""
    return plainText.replace(Regex("\\s+"), " ").take(140)
}

internal fun mergeHistoryWithCurrentMessages(
    historyMessages: List<ChatMessage>,
    currentMessages: List<ChatMessage>,
    currentStreamingMessageId: String?,
    isTrackedPendingAssistantMessageId: (String) -> Boolean
): List<ChatMessage> {
    if (currentMessages.isEmpty()) return historyMessages

    val merged = historyMessages.toMutableList()
    currentMessages.forEachIndexed { index, message ->
        if (!shouldPreserveCurrentMessageAcrossHistoryRefresh(
                message = message,
                currentStreamingMessageId = currentStreamingMessageId,
                isTrackedPendingAssistantMessageId = isTrackedPendingAssistantMessageId
            )
        ) {
            return@forEachIndexed
        }

        val matchIndex = merged.indexOfFirst { existing ->
            (existing.runId.isNotBlank() && existing.runId == message.runId) || sameFileMessage(existing, message)
        }

        if (matchIndex >= 0) {
            merged[matchIndex] = mergeFileMessage(existing = merged[matchIndex], candidate = message)
            return@forEachIndexed
        }

        val matchingIndices = merged.indices.filter { mergedIndex ->
            isSameChatMessageInstance(merged[mergedIndex], message)
        }
        val sameContentCurrentCount = currentMessages
            .take(index + 1)
            .count { isSameChatMessageInstance(it, message) }

        if (matchingIndices.count() >= sameContentCurrentCount) {
            return@forEachIndexed
        }

        merged.add(message)
    }

    return merged.sortedWith(
        compareBy<ChatMessage> { it.sortTimestamp ?: Double.MAX_VALUE }
            .thenBy { it.createdAt }
    )
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
    if (message.hasFileContent || message.hasVoiceContent || message.runId.startsWith("file-")) return true
    if (message.runId.startsWith("local-user-")) return true
    if (message.state == MessageState.streaming || message.state == MessageState.failed) return true
    if (message.role != MessageRole.assistant || message.content.trim().isEmpty()) return false
    return message.runId.isNotBlank()
}

internal fun isTransientAssistantPlaceholder(message: ChatMessage): Boolean {
    if (message.role != MessageRole.assistant) return false
    if (message.hasFileContent || message.hasVoiceContent || message.hasToolContent) return false
    val trimmed = message.content.trim()
    return trimmed.isEmpty() ||
        trimmed.startsWith("正在连接") ||
        trimmed.startsWith("Connecting") ||
        trimmed.startsWith("连接中断") ||
        trimmed.startsWith("Connection interrupted") ||
        trimmed == "正在同步回复..." ||
        trimmed == "Syncing reply..." ||
        trimmed == "已完成，但未返回文本。" ||
        trimmed == "Completed, but no text was returned." ||
        trimmed == "正在同步最终内容..." ||
        trimmed == "Syncing final content..."
}

private fun isSameChatMessageInstance(existing: ChatMessage, candidate: ChatMessage): Boolean {
    if (existing.runId.isNotBlank() && candidate.runId.isNotBlank() && existing.runId == candidate.runId) {
        return true
    }
    if (existing.role != candidate.role || existing.content != candidate.content) {
        return false
    }
    val existingTime = existing.sortTimestamp
    val candidateTime = candidate.sortTimestamp
    if (existingTime != null && candidateTime != null) {
        return kotlin.math.abs(existingTime - candidateTime) < 120.0
    }
    return true
}

internal fun sameFileMessage(existing: ChatMessage, candidate: ChatMessage): Boolean {
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

    return samePendingUploadMessage(existing, candidate) || samePendingUploadMessage(candidate, existing)
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
    val isLocalUploadPlaceholder = pending.runId.startsWith("upload-") || pending.state == MessageState.streaming
    if (!isLocalUploadPlaceholder) return false

    val pendingBlock = pending.transferContentBlocks().firstOrNull() ?: return false
    if (!pendingBlock.fileId.isNullOrBlank()) return false

    val completedBlock = completed.transferContentBlocks().firstOrNull { !it.fileId.isNullOrBlank() } ?: return false
    val pendingName = pendingBlock.fileDisplayName?.trim().orEmpty()
    val completedName = completedBlock.fileDisplayName?.trim().orEmpty()
    if (pendingName.isBlank() || !pendingName.equals(completedName, ignoreCase = true)) return false

    val pendingMime = pendingBlock.mimeType?.trim().orEmpty()
    val completedMime = completedBlock.mimeType?.trim().orEmpty()
    if (pendingMime.isNotBlank() && completedMime.isNotBlank() && !pendingMime.equals(completedMime, ignoreCase = true)) {
        return false
    }

    val pendingSize = pendingBlock.sizeBytes?.takeIf { it > 0 }
    val completedSize = completedBlock.sizeBytes?.takeIf { it > 0 }
    if (pendingSize != null && completedSize != null && pendingSize != completedSize) {
        return false
    }

    val pendingGatewayId = pendingBlock.gatewayId?.trim().orEmpty()
    val completedGatewayId = completedBlock.gatewayId?.trim().orEmpty()
    if (pendingGatewayId.isNotBlank() && completedGatewayId.isNotBlank() && pendingGatewayId != completedGatewayId) {
        return false
    }

    val pendingSessionKey = pendingBlock.sessionKey?.trim().orEmpty()
    val completedSessionKey = completedBlock.sessionKey?.trim().orEmpty()
    return pendingSessionKey.isBlank() || completedSessionKey.isBlank() || pendingSessionKey == completedSessionKey
}

private fun mergeFileMessage(existing: ChatMessage, candidate: ChatMessage): ChatMessage {
    if (existing.contentBlocks.isNotEmpty() || candidate.contentBlocks.isEmpty()) {
        return existing
    }

    return candidate.copy(
        id = existing.id,
        sortTimestamp = existing.sortTimestamp ?: candidate.sortTimestamp
    )
}

internal fun fileMessageRunId(fileId: String): String {
    return "file-${fileId.trim()}"
}
