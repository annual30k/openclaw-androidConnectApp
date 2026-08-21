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
    activeStreamingMessageId: String? = null,
    locallyStoppedRunIds: Set<String> = emptySet()
): ChatHistorySnapshotReduction? {
    val snapshot = response.timelineSnapshot ?: return null
    val fallbackHistoryOverlay = if (replaceExistingTimelineState) {
        authoritativeHistoryOverlay(currentMessages, activeStreamingMessageId, locallyStoppedRunIds)
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
                val result = reconcileTimeline(
                    // 完整 canonical 刷新：服务端快照是 confirmed timeline 的唯一权威来源。
                    // 历史已确认消息不能跨 session reset 残留，只保留当前未完成 turn 的 pending overlay。
                    existing = if (replaceExistingTimelineState) {
                        val incomingTurnIds = page.messages
                            .map { normalizedHistoryOverlayTurnId((it.runId?.ifBlank { it.turnId } ?: it.turnId).orEmpty()) }
                            .filter { it.isNotBlank() }
                            .toSet()
                        authoritativeHistoryOverlay(
                            currentMessages,
                            activeStreamingMessageId,
                            locallyStoppedRunIds,
                            incomingTurnIds
                        )
                    } else {
                        v3ReconciliationBaseMessages(currentMessages, locallyStoppedRunIds)
                    },
                    localOrderSources = if (replaceExistingTimelineState) {
                        currentMessages.filter { message ->
                            message.role == MessageRole.user && message.localTurnOrder != null
                        }
                    } else {
                        emptyList()
                    },
                    snapshot = page
                )
                val reconciled = preserveStoppedLocalUsers(
                    messages = result.messages + result.pending,
                    overlay = currentMessages,
                    locallyStoppedRunIds = locallyStoppedRunIds
                )
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
            messages = fallbackHistoryOverlay,
            activeRunId = fallbackHistoryOverlay
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
        messages = preserveStoppedLocalUsers(
            messages = reducedState.messages,
            overlay = fallbackHistoryOverlay,
            locallyStoppedRunIds = locallyStoppedRunIds
        ),
        timelineState = reducedState.copy(messages = preserveStoppedLocalUsers(
            messages = reducedState.messages,
            overlay = fallbackHistoryOverlay,
            locallyStoppedRunIds = locallyStoppedRunIds
        )),
        v3SessionKeys = emptySet()
    )
}

private fun preserveStoppedLocalUsers(
    messages: List<ChatMessage>,
    overlay: List<ChatMessage>,
    locallyStoppedRunIds: Set<String>
): List<ChatMessage> {
    if (locallyStoppedRunIds.isEmpty()) return messages
    val stoppedTurnIds = locallyStoppedRunIds
        .asSequence()
        .map(::normalizedHistoryOverlayTurnId)
        .filter { it.isNotBlank() }
        .toSet()
    if (stoppedTurnIds.isEmpty()) return messages
    val existingIds = messages.mapTo(mutableSetOf(), ChatMessage::id)
    return messages + overlay.filter { message ->
        message.id !in existingIds &&
            message.role == MessageRole.user &&
            message.runId.trim().startsWith("local-user-") &&
            normalizedHistoryOverlayTurnId(message.runId) in stoppedTurnIds
    }
}

private fun v3ReconciliationBaseMessages(
    currentMessages: List<ChatMessage>,
    locallyStoppedRunIds: Set<String>
): List<ChatMessage> {
    val stoppedTurnIds = locallyStoppedRunIds
        .asSequence()
        .map(::normalizedHistoryOverlayTurnId)
        .filter { it.isNotBlank() }
        .toSet()
    if (stoppedTurnIds.isEmpty()) return currentMessages
    // 用户主动停止后，已知属于该 turn 的 streaming/pending assistant 绝不能被新的
    // overlay 规则重新带回。用户消息仍保留，直到 Relay 确认或本地停止状态接管。
    return currentMessages.filterNot { message ->
        message.role == MessageRole.assistant &&
            message.state in setOf(MessageState.pending, MessageState.streaming) &&
            normalizedHistoryOverlayTurnId(message.runId.ifBlank { message.turnId }) in stoppedTurnIds
    }.filter { message ->
        if (message.state !in setOf(MessageState.pending, MessageState.streaming)) return@filter true
        // 无 local/Relay stable identity 的 transient 行不是已发送回合；它只可能是已中断
        // 的旧 UI 占位。完整 Relay snapshot 到达时应清掉，不能用 overlay 永久保留。
        message.hasUnconfirmedLocalTimelineIdentity() ||
            message.hasRelayTimelineIdentity() ||
            message.runId.trim().startsWith("local-user-")
    }
}

private fun authoritativeHistoryOverlay(
    currentMessages: List<ChatMessage>,
    activeStreamingMessageId: String?,
    locallyStoppedRunIds: Set<String>,
    incomingTurnIds: Set<String> = emptySet()
): List<ChatMessage> {
    val activeId = activeStreamingMessageId?.trim()?.takeIf { it.isNotEmpty() }
    val visibleUserTurnIds = currentMessages
        .asSequence()
        .filter { it.role == MessageRole.user }
        .map { normalizedHistoryOverlayTurnId(it.turnId.ifBlank { it.runId }) }
        .filter { it.isNotBlank() }
        .toSet()
    val stoppedTurnIds = locallyStoppedRunIds
        .asSequence()
        .map(::normalizedHistoryOverlayTurnId)
        .filter { it.isNotBlank() }
        .toSet()
    val unconfirmedLocalTurnIds = currentMessages
        .asSequence()
        .filter { it.role == MessageRole.user }
        .filter {
            it.state in setOf(MessageState.pending, MessageState.streaming) ||
                it.clientMessageId.isNotBlank() ||
                it.idempotencyKey.isNotBlank()
        }
        .map { normalizedHistoryOverlayTurnId(it.turnId.ifBlank { it.runId }) }
        .filter { it.isNotBlank() }
        .toSet()

    // Hermes 的 lifecycle 可能先于 final 清掉单一 streaming 指针。此时仍要像小程序一样，
    // 依赖稳定 turn 身份保留可见的未完成覆盖层；没有对应 user turn 的孤立旧占位仍由权威快照删除。
    val activeAssistants = currentMessages.filter { message ->
        val turnId = normalizedHistoryOverlayTurnId(message.turnId.ifBlank { message.runId })
        message.role == MessageRole.assistant &&
            turnId !in stoppedTurnIds &&
            (
                (message.state in setOf(MessageState.pending, MessageState.streaming) && (message.id == activeId || turnId in visibleUserTurnIds)) ||
                (turnId.isNotBlank() && turnId in unconfirmedLocalTurnIds)
            )
    }
    val activeAssistantIds = activeAssistants.mapTo(mutableSetOf(), ChatMessage::id)
    val activeTurnIds = (activeAssistants.map { normalizedHistoryOverlayTurnId(it.turnId.ifBlank { it.runId }) } + unconfirmedLocalTurnIds)
        .filterTo(mutableSetOf()) { it.isNotBlank() }

    return currentMessages.filter { message ->
        val turnId = normalizedHistoryOverlayTurnId(message.turnId.ifBlank { message.runId })
        message.isPendingRelayAttachmentProjection(activeTurnIds, stoppedTurnIds, incomingTurnIds) ||
            message.id in activeAssistantIds ||
            (message.role == MessageRole.user && (
                (message.state in setOf(MessageState.pending, MessageState.streaming)) ||
                (message.clientMessageId.isNotBlank() && turnId.isNotBlank() && turnId in unconfirmedLocalTurnIds) ||
                (turnId.isNotBlank() && (turnId in activeTurnIds || turnId in stoppedTurnIds || turnId in incomingTurnIds))
            ))
    }
}

private fun ChatMessage.isPendingRelayAttachmentProjection(
    activeTurnIds: Set<String>,
    stoppedTurnIds: Set<String>,
    incomingTurnIds: Set<String>
): Boolean {
    val turnId = normalizedHistoryOverlayTurnId(turnId.ifBlank { runId })
    val isAttachedToRecognizedTurn = turnId.isNotBlank() &&
        (turnId in activeTurnIds || turnId in stoppedTurnIds || turnId in incomingTurnIds)
    if (isAttachedToRecognizedTurn) {
        return fileContentBlocks.any { block ->
            !block.attachmentId.isNullOrBlank() || !block.fileId.isNullOrBlank()
        }
    }
    return state in setOf(MessageState.pending, MessageState.streaming) && fileContentBlocks.isNotEmpty()
}

private fun normalizedHistoryOverlayTurnId(value: String): String {
    var normalized = value.trim()
        .removePrefix("local-user-")
        .removePrefix("local-assistant-")
        .removePrefix("user-")
        .trim()
    normalized = normalized.replace(
        Regex(":(user|assistant|tool|system|waiting)$", RegexOption.IGNORE_CASE),
        ""
    )
    return normalized.trim()
}
