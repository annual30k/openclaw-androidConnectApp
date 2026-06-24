package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import com.rethinkingstudio.clawlink.core.network.transport.WsEvent
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

internal fun ChatStore.handleRealtimeWsEvent(event: WsEvent) {
    pruneLocallyStoppedRuns()
    when (event.type) {
        "usage", "context_usage" -> handleRealtimeChatPayload(event.payload)
        "event" -> {
            // Relay server wraps chat events as {type: "event", event: "chat", payload: {...}}
            when (event.event) {
                "chat" -> handleRealtimeChatPayload(event.payload)
                "context_usage", "usage" -> handleRealtimeChatPayload(event.payload)
                "agent" -> handleAgentPayload(event.payload)
                "file" -> handleRealtimeChatPayload(event.payload)
                "office" -> handleOfficePayload(event.payload)
                "presence" -> { /* handled by GatewayStore */ }
                "model_selected" -> { /* model selection update */ }
            }
        }
        "cmd", "res" -> {
            // Command response: {type: "res", ok: true/false, ...}
            val obj = event.payload?.jsonObject
            val responseId = obj?.get("id")?.jsonPrimitive?.content
            bindResolvedRunScope(responseId, obj)
            if (responseId != null && abortRequestIds.remove(responseId)) {
                val isSuccess = obj?.get("ok")?.jsonPrimitive?.booleanOrNull != false
                _state.value = _state.value.copy(isStoppingRun = false)
                if (!isSuccess && _state.value.isStreaming) {
                    val errorMsg = obj?.get("error")?.jsonObject?.get("message")?.jsonPrimitive?.content
                        ?: obj?.string("message")
                        ?: choose("Stop failed. Please try again later.", "停止失败，请稍后重试。")
                    _state.value = _state.value.copy(errorMessage = errorMsg)
                }
            } else if (obj?.get("ok")?.jsonPrimitive?.booleanOrNull == false) {
                handleRealtimeError(obj)
            }
        }
        "error" -> handleRealtimeError(event.payload)
    }
}

internal fun ChatStore.handleRealtimeChatPayload(payload: JsonElement?) {
    val obj = payload as? JsonObject ?: return
    val payloadObj = obj["payload"]?.jsonObject ?: obj
    _state.value = _state.value.withContextUsageFromPayload(obj, payloadObj)

    if (applyTimelinePayloadIfPresent(obj, payloadObj)) {
        return
    }

    ChatPayloadTool.extract(payloadObj)?.let { toolPayload ->
        handleToolPayload(obj, payloadObj, toolPayload)
        return
    }

    val phase = payloadObj["state"]?.jsonPrimitive?.content
        ?: payloadObj["phase"]?.jsonPrimitive?.content
        ?: ""

    when (phase) {
        "streaming", "delta", "in_progress" -> handleDelta(obj, payloadObj)
        "completed", "complete", "done", "final" -> {
            _state.value = _state.value.copy(isStoppingRun = false)
            handleRealtimeFinal(obj, payloadObj)
        }
        "error", "failed", "fail", "aborted" -> {
            _state.value = _state.value.copy(isStoppingRun = false)
            handleRealtimeError(obj, payloadObj)
        }
    }
}

private fun ChatStore.applyTimelinePayloadIfPresent(envelope: JsonObject, payloadObj: JsonObject): Boolean {
    val events = TimelineEventLog.decodePayload(payloadObj)
    if (events.isEmpty()) return false
    val scope = resolveTimelinePayloadScope(
        envelope = envelope,
        payload = payloadObj,
        currentGatewayId = _state.value.currentGatewayId,
        currentSessionKey = _state.value.currentSessionKey
    ).withTrackedTimelineRunScope(events, chatRunScopes)
    if (!isCurrentChatScope(scope)) {
        noteSessionActivity(scope)
        return true
    }
    applyTimelineEvents(events)
    return true
}

private fun ChatStore.applyTimelineEvents(events: List<TimelineEvent>) {
    val seeded = timelineState.copy(messages = _state.value.messages)
    timelineState = ChatTimelineReducer.reduceAll(seeded, events)
    val ordered = orderMessagesForRealtime(timelineState.messages)
    val hasActiveVisibleRun = hasActiveVisibleTimelineRun(timelineState, ordered)
    _state.value = _state.value.copy(
        messages = ordered,
        isStreaming = hasActiveVisibleRun,
        isStoppingRun = if (hasActiveVisibleRun) _state.value.isStoppingRun else false
    )
    clearStreamingPointersIfResolved(ordered)
    if (!hasActiveVisibleRun) {
        TimelinePersistenceMiddleware.clearSnapshot()
    } else {
        TimelinePersistenceMiddleware.persistSnapshot(timelineState.copy(messages = ordered))
    }
}

private fun ChatStore.handleAgentPayload(payload: JsonElement?) {
    val obj = payload as? JsonObject ?: return
    val payloadObj = obj["payload"]?.jsonObject ?: obj

    ChatPayloadTool.extract(payloadObj)?.let { toolPayload ->
        handleToolPayload(obj, payloadObj, toolPayload)
        return
    }

    handleRealtimeChatPayload(payload)
}

private fun ChatStore.handleToolPayload(envelope: JsonObject, payload: JsonObject, toolPayload: ChatToolPayload) {
    val plan = ChatToolMessagePlanner.plan(toolPayload) ?: return
    val scope = resolveChatEventScope(envelope, payload, toolPayload.scopeRunId ?: plan.toolCallId)
    if (!isCurrentChatScope(scope)) {
        noteSessionActivity(scope)
        return
    }
    val reduction = ChatToolMessageReducer.upsert(
        messages = _state.value.messages,
        plan = plan,
        nowEpochSeconds = System.currentTimeMillis() / 1000.0,
        anchorAssistantMessageId = scope.runScope?.assistantMessageId
    )
    val ordered = orderMessagesForRealtime(reduction.messages)
    _state.value = _state.value.copy(
        messages = ordered,
        isStreaming = hasPendingAssistantPlaceholder(ordered),
        isStoppingRun = false
    )
}

private fun ChatStore.handleOfficePayload(payload: JsonElement?) {
    // Office events are surfaced through GatewayStore presence updates on Android.
    // They do not create chat messages here.
    return
}

private fun ChatStore.handleDelta(envelope: JsonObject, payload: JsonElement?) {
    val obj = payload as? JsonObject ?: return
    val content = ChatPayloadText.extract(obj)
    val runId = obj.string("runId", "run_id").orEmpty()
    val scope = resolveChatEventScope(envelope, obj, runId)
    if (!isCurrentChatScope(scope)) {
        noteSessionActivity(scope)
        return
    }
    if (shouldIgnoreLocallyStoppedEvent(runId)) {
        return
    }
    scope.runScope?.assistantMessageId?.let { scopedAssistantMessageId ->
        val existingMessage = _state.value.messages.firstOrNull { it.id == scopedAssistantMessageId }
        if (existingMessage != null && streamingMessageId != scopedAssistantMessageId) {
            streamingMessageId = scopedAssistantMessageId
            streamingContent.clear()
            streamingContent.append(existingMessage.content)
        }
    }

    // delta 只能写入当前 run 绑定的 streaming message；如果已有其他 run 的流式消息，直接丢弃本事件，避免串写。
    if (streamingMessageId != null && !shouldUseStreamingMessage(runId, scope)) {
        return
    }

    if (streamingMessageId == null) {
        streamingMessageId = UUID.randomUUID().toString()
        streamingContent.clear()
        val msg = ChatMessage(
            id = streamingMessageId!!,
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "",
            createdAt = "",
            runId = runId,
            sortTimestamp = System.currentTimeMillis() / 1000.0
        )
        _state.value = _state.value.copy(
            messages = orderMessagesForRealtime(_state.value.messages + msg),
            isStreaming = true
        )
    }

    val messages = _state.value.messages.toMutableList()
    val idx = messages.indexOfFirst { it.id == streamingMessageId }
    if (idx >= 0) {
        val existing = messages[idx]
        val updatedContent = mergedAssistantStreamingDisplayContent(existing, content)
        messages[idx] = existing.copy(
            content = updatedContent,
            runId = runId.ifBlank { existing.runId }
        )
        _state.value = _state.value.copy(messages = orderMessagesForRealtime(messages))
        streamingContent.setLength(0)
        streamingContent.append(updatedContent)
    }
}

internal fun ChatStore.handleRealtimeFinal(envelope: JsonObject, payload: JsonElement?) {
    val obj = payload as? JsonObject ?: return
    val runId = obj.string("runId", "run_id").orEmpty()
    val scope = resolveChatEventScope(envelope, obj, runId)
    if (shouldIgnoreLocallyStoppedEvent(runId)) {
        return
    }
    val extractedContent = ChatPayloadText.extract(obj)
    val contentBlocks = parseContentBlocks(obj)
    val role = try {
        MessageRole.valueOf(
            obj.string("role")
                ?: ((obj["message"] as? JsonObject)?.string("role"))
                ?: "assistant"
        )
    } catch (_: Exception) {
        MessageRole.assistant
    }
    val contentBlockFallbackText = contentBlocks.firstNotNullOfOrNull { block ->
        block.text?.trim()?.takeIf { it.isNotEmpty() }
            ?: block.fileDisplayName?.trim()?.takeIf { it.isNotEmpty() }
    }.orEmpty()
    val content = if (role == MessageRole.user) {
        extractedContent.ifBlank { contentBlockFallbackText }
    } else {
        extractedContent.ifBlank {
            streamingContent.toString().ifBlank { contentBlockFallbackText }
        }
    }

    val sourceRunId = attachmentSourceRunId(
        payload = obj,
        runId = runId,
        contentBlocks = contentBlocks,
        runScope = scope.runScope,
        messages = _state.value.messages
    )
    val finalContentBlocks = contentBlocksWithAttachmentSourceRunId(contentBlocks, sourceRunId)

    val finalRole = if (finalContentBlocks.any { it.isToolCallBlock || it.isToolResultBlock }) MessageRole.tool else role
    val preview = buildNotificationPreview(content, contentBlocks)
    if (!isCurrentChatScope(scope)) {
        noteSessionActivity(scope, lastActivityAt = eventTimestampIso(obj))
        if (finalRole != MessageRole.user && scope.hasSessionKey && preview.isNotBlank()) {
            notificationPort.showReplyNotification(
                sessionKey = scope.sessionKey,
                title = "PocketClaw reply",
                body = preview
            )
        }
        completeHiddenRunIfNeeded(runId, scope.runScope)
        forgetRunScope(runId, scope.runScope)
        return
    }

    val existingAssistantForFinal = if (finalRole != MessageRole.user) {
        pendingAssistantMessageForFinal(
            scope = scope,
            messages = _state.value.messages,
            streamingMessageId = streamingMessageId
        )
    } else {
        null
    }
    if (finalRole != MessageRole.user &&
        shouldSyncAssistantFinalFromHistory(
            existing = existingAssistantForFinal,
            finalText = extractedContent,
            finalContentBlocks = finalContentBlocks
        )
    ) {
        markAssistantFinalSyncingFromHistory(
            runId = runId,
            runScope = scope.runScope,
            existingAssistant = existingAssistantForFinal
        )
        noteSessionActivity(scope, lastActivityAt = eventTimestampIso(obj))
        return
    }

    if (finalRole == MessageRole.user) {
        appendOrMergeRemoteUserMessage(
            content = content,
            contentBlocks = finalContentBlocks,
            runId = runId,
            sortTimestamp = eventTimestampMillis(obj)?.toDouble()?.div(1000.0),
            assistantMessageId = scope.runScope?.assistantMessageId
        )
        noteSessionActivity(scope, lastActivityAt = eventTimestampIso(obj))
        return
    }

    if (finalRole != MessageRole.user) {
        scope.runScope?.assistantMessageId?.let { scopedAssistantMessageId ->
            val existingMessage = _state.value.messages.firstOrNull { it.id == scopedAssistantMessageId }
            if (existingMessage != null && streamingMessageId != scopedAssistantMessageId) {
                streamingMessageId = scopedAssistantMessageId
                streamingContent.clear()
                streamingContent.append(existingMessage.content)
            }
        }
    }

    // final 优先完成同 run 的 streaming message；只有没有可绑定占位时才追加新消息，保证 final/delta 幂等收敛。
    if (streamingMessageId != null && finalRole != MessageRole.user && shouldUseStreamingMessage(runId, scope)) {
        val messages = _state.value.messages.toMutableList()
        val idx = messages.indexOfFirst { it.id == streamingMessageId }
        if (idx >= 0) {
            messages[idx] = messages[idx].copy(
                role = finalRole,
                content = content,
                contentBlocks = finalContentBlocks,
                state = MessageState.completed,
                runId = runId.ifBlank { messages[idx].runId }
            )
            _state.value = _state.value.copy(
                messages = orderMessagesForRealtime(messages),
                isStreaming = false
            )
        }
        completeCurrentRun(runId, scope.runScope)
    } else {
        appendCompletedFinalMessage(
            obj = obj,
            runId = runId,
            finalRole = finalRole,
            content = content,
            contentBlocks = contentBlocks,
            finalContentBlocks = finalContentBlocks,
            sourceRunId = sourceRunId,
            scope = scope
        )
    }

    noteSessionActivity(scope, lastActivityAt = eventTimestampIso(obj))
    if (scope.sessionKey.isNotBlank() && preview.isNotBlank()) {
        notificationPort.showReplyNotification(
            sessionKey = scope.sessionKey,
            title = "PocketClaw reply",
            body = preview
        )
    }

    streamingMessageId = null
    streamingContent.clear()
}

private fun ChatStore.appendCompletedFinalMessage(
    obj: JsonObject,
    runId: String,
    finalRole: MessageRole,
    content: String,
    contentBlocks: List<RelayChatContentBlock>,
    finalContentBlocks: List<RelayChatContentBlock>,
    sourceRunId: String?,
    scope: ChatEventScope
) {
    val eventSortTimestamp = eventTimestampMillis(obj)?.toDouble()?.div(1000.0)
    val msgId = UUID.randomUUID().toString()
    val msg = ChatMessage(
        id = msgId,
        role = finalRole,
        state = MessageState.completed,
        content = content,
        contentBlocks = finalContentBlocks,
        createdAt = eventTimestampIso(obj),
        runId = runId,
        sortTimestamp = eventSortTimestamp ?: (System.currentTimeMillis() / 1000.0),
        timelineOrderKey = sourceRunId
            ?.let { localTimelineOrderKey(it, 30, msgId) }
            .orEmpty(),
        timelineIdentityKey = sourceRunId
            ?.let { localTimelineIdentityKey("attachment", attachmentIdentityForOrder(finalContentBlocks) ?: msgId) }
            .orEmpty(),
        timelineItemKind = if (sourceRunId != null) "attachment" else ""
    )
    val mergedCompletedAssistant = mergeCompletedAssistantFinalIntoCurrentMessages(
        currentMessages = _state.value.messages,
        candidate = msg
    )
    if (mergedCompletedAssistant != null) {
        _state.value = _state.value.copy(
            messages = orderMessagesForRealtime(mergedCompletedAssistant),
            isStreaming = false
        )
        completeCurrentRun(runId, scope.runScope)
        return
    }

    val fileIds = contentBlocks.mapNotNull { it.fileId?.trim()?.takeIf { id -> id.isNotEmpty() } }
    if (fileIds.isNotEmpty()) {
        val messages = _state.value.messages.toMutableList()
        val existingIndex = messages.indexOfFirst { existing -> sameFileMessage(existing, msg) }
        if (existingIndex >= 0) {
            val mergedMessage = mergeCompletedFileMessage(
                existing = messages[existingIndex],
                completed = msg.copy(
                    id = messages[existingIndex].id,
                    sortTimestamp = messages[existingIndex].sortTimestamp ?: msg.sortTimestamp
                )
            )
            messages[existingIndex] = mergedMessage
            _state.value = _state.value.copy(messages = orderMessagesForRealtime(messages), isStreaming = false)
            removeDuplicateFileMessages(mergedMessage)
            streamingContent.clear()
            streamingMessageId = null
            completeCurrentRun(runId, scope.runScope)
            return
        }
    }
    _state.value = _state.value.copy(
        messages = orderMessagesForRealtime(_state.value.messages + msg),
        isStreaming = false
    )
    completeCurrentRun(runId, scope.runScope)
}

internal fun ChatStore.appendOrMergeRemoteUserMessage(
    content: String,
    contentBlocks: List<RelayChatContentBlock>,
    runId: String,
    sortTimestamp: Double?,
    assistantMessageId: String? = null
) {
    val messages = mergeRemoteUserMessageIntoCurrentMessages(
        currentMessages = _state.value.messages,
        content = content,
        contentBlocks = contentBlocks,
        runId = runId,
        sortTimestamp = sortTimestamp,
        assistantMessageId = assistantMessageId
    )
    _state.value = _state.value.copy(messages = orderMessagesForRealtime(messages))
}

internal fun ChatStore.markAssistantFinalSyncingFromHistory(
    runId: String,
    runScope: ChatRunScope?,
    existingAssistant: ChatMessage?
) {
    val messages = _state.value.messages.toMutableList()
    val assistantMessageId = runScope?.assistantMessageId
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: existingAssistant?.id
        ?: streamingMessageId
    val index = assistantMessageId
        ?.let { id -> messages.indexOfFirst { it.id == id } }
        ?: -1
    val resolvedRunId = runId.trim().takeIf { it.isNotEmpty() }
        ?: existingAssistant?.runId?.trim()?.takeIf { it.isNotEmpty() }
        ?: assistantMessageId.orEmpty()

    if (index >= 0) {
        val existing = messages[index]
        messages[index] = existing.copy(
            state = MessageState.streaming,
            content = choose("Syncing final content...", "正在同步最终内容..."),
            runId = resolvedRunId.ifBlank { existing.runId }
        )
        streamingMessageId = existing.id
        streamingContent.clear()
        streamingContent.append(messages[index].content)
        _state.value = _state.value.copy(
            messages = orderMessagesForRealtime(messages),
            isStreaming = true,
            isStoppingRun = false
        )
    } else {
        _state.value = _state.value.copy(isStreaming = true, isStoppingRun = false)
    }

    if (resolvedRunId.isNotBlank() && runScope != null) {
        rememberRunScopeForRealtime(resolvedRunId, runScope)
        scheduleChatFinalSync(resolvedRunId, runScope)
    }
}

internal fun ChatStore.handleRealtimeError(payload: JsonElement?) {
    val obj = payload as? JsonObject
    if (obj == null) {
        logWarning("Ignoring chat error without payload")
        _state.value = _state.value.copy(isStreaming = false, isStoppingRun = false)
        return
    }
    handleRealtimeError(obj, obj["payload"] as? JsonObject ?: obj)
}

internal fun ChatStore.handleRealtimeError(envelope: JsonObject, payload: JsonElement?) {
    val obj = payload as? JsonObject
    val runId = obj?.string("runId", "run_id")
    val scope = obj?.let { resolveChatEventScope(envelope, it, runId.orEmpty()) }
    if (shouldIgnoreLocallyStoppedEvent(runId.orEmpty())) {
        return
    }
    if (scope != null && !isCurrentChatScope(scope)) {
        noteSessionActivity(scope)
        completeHiddenRunIfNeeded(runId.orEmpty(), scope.runScope)
        forgetRunScope(runId.orEmpty(), scope.runScope)
        return
    }
    val errorObj = obj?.get("error") as? JsonObject
    val msg = errorObj?.string("message")
        ?: obj?.string("message", "errorMessage")
        ?: obj?.let { ChatPayloadText.extract(it).takeIf { text -> text.isNotBlank() } }
        ?: "Unknown error"
    val assistantMessageId = scope?.runScope?.assistantMessageId
    val currentMessages = _state.value.messages
    val updatedMessages = applyAssistantErrorToCurrentMessages(
        currentMessages = currentMessages,
        runId = runId,
        assistantMessageId = assistantMessageId,
        errorMessage = msg,
        sortTimestamp = obj?.let { eventTimestampMillis(it)?.toDouble()?.div(1000.0) }
    )
    val updatedAssistant = updatedMessages != currentMessages
    if (updatedAssistant && assistantMessageId != null && streamingMessageId == assistantMessageId) {
        streamingMessageId = null
        streamingContent.clear()
    }
    _state.value = _state.value.copy(
        messages = updatedMessages,
        errorMessage = if (updatedAssistant) null else msg,
        isStreaming = false,
        isStoppingRun = false
    )
    completeCurrentRun(runId.orEmpty(), scope?.runScope)
}
