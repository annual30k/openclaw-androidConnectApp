package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.network.dto.ChatHistoryResponse
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

internal data class ChatHistorySnapshotReduction(
    val messages: List<ChatMessage>,
    val timelineState: ChatTimelineState,
    val v3SessionKeys: Set<String>
)

internal fun reduceTimelineHistorySnapshot(
    response: ChatHistoryResponse,
    currentMessages: List<ChatMessage>,
    currentSessionKey: String,
    timelineState: ChatTimelineState,
    replaceExistingTimelineState: Boolean = false,
    activeStreamingMessageId: String? = null
): ChatHistorySnapshotReduction? {
    val snapshot = response.timelineSnapshot ?: return null
    val authoritativePendingOverlay = if (replaceExistingTimelineState) {
        activeStreamingHistoryOverlay(currentMessages, activeStreamingMessageId)
    } else {
        currentMessages
    }
    val snapshotObject = snapshot as? JsonObject
    val isCanonicalTimelineV3 = snapshotObject?.let { obj ->
        obj["timelineProtocolVersion"]?.jsonPrimitive?.contentOrNull in setOf("3", "4") ||
            obj.containsKey("snapshotRevision") ||
            obj.containsKey("rangeStartCursor") ||
            obj.containsKey("rangeEndCursor") ||
            obj.containsKey("deletedMessageIds")
    } == true
    if (isCanonicalTimelineV3) {
        val sessionKeyFromSnapshot = snapshotObject?.get("sessionKey")?.jsonPrimitive?.contentOrNull ?: defaultSessionKey
        val v3SessionKeys = setOf(
            normalizeSessionKey(sessionKeyFromSnapshot),
            // Hermes/agent 前缀可能在快照和当前状态中表现不同，两边都登记后窗口裁剪才能走同一 v3 排序规则。
            normalizeSessionKey(currentSessionKey)
        )
        TimelineSnapshotPage.fromJsonElement(snapshot)
            ?.takeIf { it.messages.isNotEmpty() || it.deletedMessageIds.isNotEmpty() }
            ?.let { page ->
                val baseMessages = if (replaceExistingTimelineState) {
                    authoritativePendingOverlay
                } else {
                    currentMessages
                }
                val result = reconcileTimeline(
                    existing = baseMessages,
                    // 权威快照可以替换可见内容，但不能在对账前丢掉本机提交顺序。
                    // 这里只把稳定 turn 元数据作为匹配来源，不把旧消息重新塞回快照结果。
                    localOrderSources = if (replaceExistingTimelineState) {
                        currentMessages.filter { message ->
                            message.role == MessageRole.user && message.localTurnOrder != null
                        }
                    } else {
                        emptyList()
                    },
                    snapshot = page
                )
                val reconciled = result.messages + result.pending
                return ChatHistorySnapshotReduction(
                    messages = reconciled,
                    timelineState = timelineState.copy(messages = reconciled),
                    v3SessionKeys = v3SessionKeys
                )
            }
    }
    val events = TimelineEventLog.decodePayload(JsonObject(mapOf("timelineSnapshot" to snapshot)))
    if (events.isEmpty()) return null
    val baseState = if (replaceExistingTimelineState) {
        ChatTimelineState(
            messages = authoritativePendingOverlay,
            activeRunId = authoritativePendingOverlay
                .firstOrNull { it.role == MessageRole.assistant }
                ?.runId
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        )
    } else {
        timelineState.copy(messages = currentMessages)
    }
    val reducedState = ChatTimelineReducer.reduceAll(baseState, events)
    return ChatHistorySnapshotReduction(
        messages = reducedState.messages,
        timelineState = reducedState,
        v3SessionKeys = emptySet()
    )
}

private fun activeStreamingHistoryOverlay(
    currentMessages: List<ChatMessage>,
    activeStreamingMessageId: String?
): List<ChatMessage> {
    val activeId = activeStreamingMessageId?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
    val activeAssistant = currentMessages.firstOrNull { message ->
        message.id == activeId &&
            message.role == MessageRole.assistant &&
            message.state in setOf(MessageState.pending, MessageState.streaming)
    } ?: return emptyList()
    val activeTurnId = normalizedHistoryOverlayTurnId(activeAssistant.runId)
    return currentMessages.filter { message ->
        message.id == activeId ||
            (message.role == MessageRole.user &&
                message.runId.trim().startsWith("local-user-") &&
                normalizedHistoryOverlayTurnId(message.runId) == activeTurnId)
    }
}

private fun normalizedHistoryOverlayTurnId(value: String): String {
    var normalized = value.trim()
        .removePrefix("local-user-")
        .removePrefix("user-")
        .trim()
    normalized = normalized.replace(
        Regex(":(user|assistant|tool|system|waiting)$", RegexOption.IGNORE_CASE),
        ""
    )
    return normalized.trim()
}
