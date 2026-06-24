package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
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
    replaceExistingTimelineState: Boolean = false
): ChatHistorySnapshotReduction? {
    val snapshot = response.timelineSnapshot ?: return null
    val snapshotObject = snapshot as? JsonObject
    val isCanonicalTimelineV3 = snapshotObject?.let { obj ->
        obj["timelineProtocolVersion"]?.jsonPrimitive?.contentOrNull == "3" ||
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
                    currentMessages.filter {
                        it.state == MessageState.pending ||
                            it.state == MessageState.streaming ||
                            it.runId.startsWith("local-user-")
                    }
                } else {
                    currentMessages
                }
                val result = reconcileTimeline(
                    existing = baseMessages,
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
        ChatTimelineState()
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
