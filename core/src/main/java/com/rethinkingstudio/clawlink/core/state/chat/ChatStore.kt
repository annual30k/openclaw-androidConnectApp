package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.ChatSessionItem
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import com.rethinkingstudio.clawlink.core.models.chat.AttachmentUploadPhase
import com.rethinkingstudio.clawlink.core.models.chat.ComposerAttachmentDraft
import com.rethinkingstudio.clawlink.core.models.chat.ComposerAttachmentUploadItem
import com.rethinkingstudio.clawlink.core.models.gateway.GatewayType
import com.rethinkingstudio.clawlink.core.domain.NotificationPort
import com.rethinkingstudio.clawlink.core.network.RelayAPIClient
import com.rethinkingstudio.clawlink.core.network.dto.ChatHistoryResponse
import com.rethinkingstudio.clawlink.core.network.dto.RelayFileTransferItem
import com.rethinkingstudio.clawlink.core.network.transport.RelayChatSendAttachmentPayload
import com.rethinkingstudio.clawlink.core.network.transport.RelayWebSocketClient
import com.rethinkingstudio.clawlink.core.network.transport.VoiceSendAudioPayload
import com.rethinkingstudio.clawlink.core.network.transport.WsEvent
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
import com.rethinkingstudio.clawlink.core.state.chat.RemoteImageCache
import com.rethinkingstudio.clawlink.core.state.chat.RemoteImageSizeCache
import com.rethinkingstudio.clawlink.core.state.chat.RemoteAttachmentCache
import android.graphics.BitmapFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.SocketTimeoutException
import java.time.Instant
import java.util.UUID

internal typealias ChatHistoryPageFetcher = suspend (
    gatewayId: String,
    sessionKey: String,
    limit: Int,
    cursor: String?,
    direction: String
) -> ChatHistoryResponse

internal data class LocalStopCompletionResult(
    val messages: List<ChatMessage>,
    val stoppedRunId: String?
)

internal fun completeStreamingMessageLocallyAfterStop(
    messages: List<ChatMessage>,
    runId: String?
): LocalStopCompletionResult {
    val updatedMessages = messages.toMutableList()
    val index = updatedMessages.indexOfLast { it.state == MessageState.streaming }
    if (index < 0) {
        return LocalStopCompletionResult(
            messages = messages,
            stoppedRunId = runId?.takeIf { it.isNotBlank() }
        )
    }

    val existing = updatedMessages[index]
    val resolvedRunId = runId?.takeIf { it.isNotBlank() } ?: existing.runId
    if (isTransientAssistantPlaceholder(existing)) {
        updatedMessages.removeAt(index)
    } else {
        updatedMessages[index] = existing.copy(state = MessageState.completed, runId = resolvedRunId)
    }
    return LocalStopCompletionResult(
        messages = updatedMessages,
        stoppedRunId = resolvedRunId.takeIf { it.isNotBlank() }
    )
}

class ChatStore(
    private val apiClient: RelayAPIClient,
    private val wsClient: RelayWebSocketClient,
    private val notificationPort: NotificationPort,
    private val sessionSelectionStore: ChatSessionSelectionStore? = null,
    private val chatHistoryPageFetcher: ChatHistoryPageFetcher? = null
) {
    val relayBaseUrl: String get() = apiClient.baseUrl
    val accessToken: String get() = apiClient.accessToken

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var streamingMessageId: String? = null
    private var streamingContent = StringBuilder()
    private val chatRunScopes = linkedMapOf<String, ChatRunScope>()
    private val abortRequestIds = mutableSetOf<String>()
    private val locallyStoppedRunIds = mutableSetOf<String>()
    private val chatFinalSyncJobs = mutableMapOf<String, Job>()
    private var ignoreRunlessStoppedEventsUntilMs: Long = 0

    init {
        _state.value = _state.value.copy(
            readVoicePlaybackIdentifiers = VoicePlaybackReadStore.getReadIdentifiers()
        )
        wsClient.events
            .onEach { event -> handleWsEvent(event) }
            .launchIn(scope)
    }

    private fun handleWsEvent(event: WsEvent) {
        pruneLocallyStoppedRuns()
        when (event.type) {
            "usage", "context_usage" -> handleChatPayload(event.payload)
            "event" -> {
                // Relay server wraps chat events as {type: "event", event: "chat", payload: {...}}
                when (event.event) {
                    "chat" -> handleChatPayload(event.payload)
                    "context_usage", "usage" -> handleChatPayload(event.payload)
                    "agent" -> handleAgentPayload(event.payload)
                    "file" -> handleChatPayload(event.payload)
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
                    // This is an abort ACK
                    val isSuccess = obj?.get("ok")?.jsonPrimitive?.booleanOrNull != false
                    _state.value = _state.value.copy(isStoppingRun = false)
                    if (!isSuccess && _state.value.isStreaming) {
                        val errorMsg = obj?.get("error")?.jsonObject?.get("message")?.jsonPrimitive?.content
                            ?: obj?.string("message")
                            ?: choose("Stop failed. Please try again later.", "停止失败，请稍后重试。")
                        _state.value = _state.value.copy(errorMessage = errorMsg)
                    }
                } else if (obj?.get("ok")?.jsonPrimitive?.booleanOrNull == false) {
                    handleError(obj)
                }
            }
            "error" -> handleError(event.payload)
        }
    }

    private fun handleChatPayload(payload: JsonElement?) {
        val obj = payload as? JsonObject ?: return
        val payloadObj = obj["payload"]?.jsonObject ?: obj
        _state.value = _state.value.withContextUsageFromPayload(obj, payloadObj)

        ChatPayloadTool.extract(payloadObj)?.let { toolPayload ->
            handleToolPayload(obj, payloadObj, toolPayload)
            return
        }

        // Determine phase from payload
        val phase = payloadObj["state"]?.jsonPrimitive?.content
            ?: payloadObj["phase"]?.jsonPrimitive?.content
            ?: ""

        when (phase) {
            "streaming", "delta", "in_progress" -> handleDelta(obj, payloadObj)
            "completed", "complete", "done", "final" -> {
                _state.value = _state.value.copy(isStoppingRun = false)
                handleFinal(obj, payloadObj)
            }
            "error", "failed", "fail", "aborted" -> {
                _state.value = _state.value.copy(isStoppingRun = false)
                handleError(obj, payloadObj)
            }
        }
    }

    private fun handleAgentPayload(payload: JsonElement?) {
        val obj = payload as? JsonObject ?: return
        val payloadObj = obj["payload"]?.jsonObject ?: obj

        ChatPayloadTool.extract(payloadObj)?.let { toolPayload ->
            handleToolPayload(obj, payloadObj, toolPayload)
            return
        }

        // Not a tool stream — handle as regular assistant event
        handleChatPayload(payload)
    }

    private fun handleToolPayload(envelope: JsonObject, payload: JsonObject, toolPayload: ChatToolPayload) {
        val plan = ChatToolMessagePlanner.plan(toolPayload) ?: return
        val scope = resolveChatEventScope(envelope, payload, plan.toolCallId)
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
        val ordered = orderedMessages(reduction.messages)
        _state.value = _state.value.copy(
            messages = ordered,
            isStreaming = plan.state == MessageState.streaming || hasPendingAssistantPlaceholder(ordered),
            isStoppingRun = false
        )
    }

    private fun handleOfficePayload(payload: JsonElement?) {
        // Office events are surfaced through GatewayStore presence updates on Android.
        // They do not create chat messages here.
        return
    }

    private fun handleDelta(envelope: JsonObject, payload: JsonElement?) {
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
                messages = orderedMessages(_state.value.messages + msg),
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
            _state.value = _state.value.copy(messages = orderedMessages(messages))
            streamingContent.setLength(0)
            streamingContent.append(updatedContent)
        }
    }

    private fun handleFinal(envelope: JsonObject, payload: JsonElement?) {
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
        val content = if (role == MessageRole.user) {
            extractedContent
        } else {
            extractedContent.ifBlank { streamingContent.toString() }
        }

        val finalContentBlocks = contentBlocks

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
            pendingAssistantMessageForFinal(scope)
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
                    messages = orderedMessages(messages),
                    isStreaming = false
                )
            }
            cancelChatFinalSync(scope.runScope)
            forgetRunScope(runId, scope.runScope)
        } else {
            val eventSortTimestamp = eventTimestampMillis(obj)?.toDouble()?.div(1000.0)
            val msg = ChatMessage(
                id = UUID.randomUUID().toString(),
                role = finalRole,
                state = MessageState.completed,
                content = content,
                contentBlocks = finalContentBlocks,
                createdAt = eventTimestampIso(obj),
                runId = runId,
                sortTimestamp = eventSortTimestamp ?: (System.currentTimeMillis() / 1000.0)
            )
            val anchoredMessage = anchorAssistantFileMessageToSourceRun(msg, _state.value.messages)
            val fileIds = contentBlocks.mapNotNull { it.fileId?.trim()?.takeIf { id -> id.isNotEmpty() } }
            if (fileIds.isNotEmpty()) {
                val messages = _state.value.messages.toMutableList()
                val existingIndex = messages.indexOfFirst { existing ->
                    sameFileMessage(existing, anchoredMessage)
                }
                if (existingIndex >= 0) {
                    val mergedMessage = mergeCompletedFileMessage(
                        existing = messages[existingIndex],
                        completed = anchoredMessage.copy(
                            id = messages[existingIndex].id,
                            sortTimestamp = messages[existingIndex].sortTimestamp ?: anchoredMessage.sortTimestamp
                        )
                    )
                    messages[existingIndex] = mergedMessage
                    _state.value = _state.value.copy(messages = orderedMessages(messages), isStreaming = false)
                    removeDuplicateFileMessages(mergedMessage)
                    streamingContent.clear()
                    streamingMessageId = null
                    cancelChatFinalSync(scope.runScope)
                    forgetRunScope(runId, scope.runScope)
                    return
                }
            }
            _state.value = _state.value.copy(
                messages = orderedMessages(_state.value.messages + anchoredMessage),
                isStreaming = false
            )
            cancelChatFinalSync(scope.runScope)
            forgetRunScope(runId, scope.runScope)
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

    private fun appendOrMergeRemoteUserMessage(
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
        _state.value = _state.value.copy(messages = orderedMessages(messages))
    }

    private fun pendingAssistantMessageForFinal(scope: ChatEventScope): ChatMessage? {
        val messages = _state.value.messages
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

    private fun markAssistantFinalSyncingFromHistory(
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
                messages = orderedMessages(messages),
                isStreaming = true,
                isStoppingRun = false
            )
        } else {
            _state.value = _state.value.copy(isStreaming = true, isStoppingRun = false)
        }

        if (resolvedRunId.isNotBlank() && runScope != null) {
            rememberRunScope(resolvedRunId, runScope)
            scheduleChatFinalSync(resolvedRunId, runScope)
        }
    }

    private fun orderedMessages(messages: List<ChatMessage>): List<ChatMessage> {
        return orderMessagesWithSourceRunAnchors(messages)
    }

    private fun scheduleChatFinalSync(runId: String, runScope: ChatRunScope?, attempt: Int = 0) {
        val assistantMessageId = runScope?.assistantMessageId?.trim()?.takeIf { it.isNotEmpty() } ?: return
        if (chatFinalSyncJobs[assistantMessageId]?.isActive == true) return
        val gatewayId = runScope.gatewayId.trim()
        val sessionKey = normalizeSessionKey(runScope.sessionKey)
        if (gatewayId.isBlank() || sessionKey.isBlank()) return

        val job = scope.launch {
            delay(chatFinalSyncDelayMs(attempt))
            chatFinalSyncJobs.remove(assistantMessageId)
            if (!needsChatFinalSync(runId, runScope)) return@launch

            android.util.Log.d("ChatStore", "Silent chat final sync attempt=${attempt + 1} runId=$runId session=$sessionKey")
            resolvePendingFinalFromHistory(gatewayId = gatewayId, sessionKey = sessionKey, runId = runId, runScope = runScope)
            if (needsChatFinalSync(runId, runScope) && attempt + 1 < chatFinalSyncMaxAttempts) {
                scheduleChatFinalSync(runId, runScope, attempt + 1)
            }
        }
        chatFinalSyncJobs[assistantMessageId] = job
    }

    private fun schedulePendingFinalSyncsForCurrentSession() {
        val current = _state.value
        val gatewayId = current.currentGatewayId?.trim().orEmpty()
        val sessionKey = normalizeSessionKey(current.currentSessionKey)
        if (gatewayId.isBlank() || sessionKey.isBlank()) return
        current.messages
            .filter { message ->
                message.role == MessageRole.assistant &&
                    (message.state == MessageState.streaming || isTransientAssistantPlaceholder(message))
            }
            .forEach { message ->
                val runScope = ChatRunScope(
                    gatewayId = gatewayId,
                    sessionKey = sessionKey,
                    assistantMessageId = message.id,
                    triggeringUserMessageId = triggeringUserMessageIdBefore(message, current.messages)
                )
                val runId = message.runId.trim().takeIf { it.isNotEmpty() } ?: message.id
                rememberRunScope(runId, runScope)
                scheduleChatFinalSync(runId, runScope)
            }
    }

    private fun chatFinalSyncDelayMs(attempt: Int): Long {
        return when {
            attempt <= 0 -> chatFinalSyncInitialDelayMs
            attempt < 6 -> chatFinalSyncFastRetryDelayMs
            else -> chatFinalSyncSlowRetryDelayMs
        }
    }

    private fun cancelChatFinalSync(runScope: ChatRunScope?) {
        val assistantMessageId = runScope?.assistantMessageId?.trim()?.takeIf { it.isNotEmpty() } ?: return
        chatFinalSyncJobs.remove(assistantMessageId)?.cancel()
    }

    private fun needsChatFinalSync(runId: String, runScope: ChatRunScope): Boolean {
        val messages = orderedMessages(_state.value.messages)
        val assistantMessageId = runScope.assistantMessageId?.trim().orEmpty()
        val directMessage = messages.firstOrNull { assistantMessageId.isNotBlank() && it.id == assistantMessageId }
            ?: messages.firstOrNull { runId.isNotBlank() && it.role == MessageRole.assistant && it.runId == runId }
        if (directMessage != null) {
            return directMessage.role == MessageRole.assistant &&
                (directMessage.state == MessageState.streaming || isTransientAssistantPlaceholder(directMessage))
        }

        val triggeringUserId = runScope.triggeringUserMessageId?.trim().orEmpty()
        if (triggeringUserId.isBlank()) return false
        val triggerIndex = messages.indexOfLast { it.id == triggeringUserId }
        if (triggerIndex < 0) return false
        return messages
            .drop(triggerIndex + 1)
            .takeWhile { it.role != MessageRole.user }
            .any { it.role == MessageRole.assistant && (it.state == MessageState.streaming || isTransientAssistantPlaceholder(it)) }
    }

    private fun triggeringUserMessageIdBefore(message: ChatMessage, messages: List<ChatMessage>): String? {
        val ordered = orderedMessages(messages)
        val index = ordered.indexOfFirst { it.id == message.id }
        if (index <= 0) return null
        return ordered
            .take(index)
            .lastOrNull { it.role == MessageRole.user }
            ?.id
    }

    private fun hasPendingAssistantPlaceholder(messages: List<ChatMessage>): Boolean {
        return messages.any { message ->
            message.role == MessageRole.assistant &&
                (message.state == MessageState.streaming || isTransientAssistantPlaceholder(message))
        }
    }

    private fun hasActiveStreamingMessage(messages: List<ChatMessage>): Boolean {
        return messages.any { message ->
            message.state == MessageState.streaming &&
                (message.role == MessageRole.assistant || message.role == MessageRole.tool)
        }
    }

    private fun clearStreamingPointersIfResolved(messages: List<ChatMessage>) {
        val currentStreamingMessageId = streamingMessageId?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val stillStreaming = messages.any { message ->
            message.id == currentStreamingMessageId && message.state == MessageState.streaming
        }
        if (!stillStreaming) {
            streamingMessageId = null
            streamingContent.clear()
        }
    }

    private fun trimToNewestHistoryWindow(messages: List<ChatMessage>): List<ChatMessage> {
        return newestBoundedHistoryWindowMessages(
            messages = messages,
            maxMessages = chatHistoryWindowMaxMessages
        )
    }

    private fun trimToOlderHistoryWindow(messages: List<ChatMessage>): List<ChatMessage> {
        return olderBoundedHistoryWindowMessages(
            messages = messages,
            maxMessages = chatHistoryWindowMaxMessages,
            shouldPreserveActiveMessage = ::shouldPreserveDuringOlderWindowTrim
        )
    }

    private fun shouldPreserveDuringOlderWindowTrim(message: ChatMessage): Boolean {
        return message.state == MessageState.streaming ||
            isTransientAssistantPlaceholder(message) ||
            streamingMessageId == message.id ||
            isTrackedPendingAssistantMessageId(message.id)
    }

    private fun matchesRequestedChatScope(
        state: ChatState,
        gatewayId: String,
        sessionKey: String
    ): Boolean {
        return state.currentGatewayId == gatewayId && sameSessionKey(state.currentSessionKey, sessionKey)
    }

    private fun isTrackedPendingAssistantMessageId(messageId: String): Boolean {
        return chatRunScopes.values.any { it.assistantMessageId == messageId }
    }

    private suspend fun fetchChatHistoryPage(
        gatewayId: String,
        sessionKey: String,
        limit: Int,
        cursor: String? = null,
        direction: String = "older"
    ): ChatHistoryResponse {
        return chatHistoryPageFetcher?.invoke(gatewayId, sessionKey, limit, cursor, direction)
            ?: apiClient.fetchChatHistoryPage(gatewayId, sessionKey, limit, cursor, direction)
    }

    private suspend fun resolvePendingFinalFromHistory(
        gatewayId: String,
        sessionKey: String,
        runId: String,
        runScope: ChatRunScope
    ) {
        val normalizedGatewayId = gatewayId.trim()
        val normalizedSessionKey = normalizeSessionKey(sessionKey)
        if (normalizedGatewayId.isBlank() || normalizedSessionKey.isBlank()) return
        var cursor: String? = null
        var pageCount = 0
        try {
            val startingState = _state.value
            if (startingState.currentGatewayId == normalizedGatewayId &&
                sameSessionKey(startingState.currentSessionKey, normalizedSessionKey)
            ) {
                _state.value = startingState.copy(
                    historyWindow = startingState.historyWindow.copy(isCatchingUp = true)
                )
            }

            while (pageCount < chatHistoryPendingResolveMaxPages && needsChatFinalSync(runId, runScope)) {
                val response = retryOnceOnTransientFailure(
                    operationName = "silent chat history page for $normalizedGatewayId/$normalizedSessionKey"
                ) {
                    fetchChatHistoryPage(
                        normalizedGatewayId,
                        normalizedSessionKey,
                        chatHistoryPageSize,
                        cursor,
                        "older"
                    )
                }
                val current = _state.value
                if (current.currentGatewayId != normalizedGatewayId ||
                    !sameSessionKey(current.currentSessionKey, normalizedSessionKey)
                ) {
                    return
                }
                val messages = mergeHistoryWithCurrentMessages(
                    historyMessages = buildHistoryMessagesFromItems(response.items),
                    currentMessages = current.messages,
                    currentStreamingMessageId = streamingMessageId,
                    isTrackedPendingAssistantMessageId = ::isTrackedPendingAssistantMessageId
                )
                val ordered = trimToNewestHistoryWindow(messages)
                _state.value = current.copy(
                    messages = ordered,
                    historyWindow = current.historyWindow.copy(
                        isCatchingUp = true,
                        hasOlder = response.hasMore,
                        olderCursor = response.nextCursor,
                        newestCursor = response.newestCursor ?: current.historyWindow.newestCursor,
                        loadedMessageCount = ordered.size
                    ),
                    isStreaming = hasActiveStreamingMessage(ordered),
                    isStoppingRun = if (hasActiveStreamingMessage(ordered)) current.isStoppingRun else false
                )
                clearStreamingPointersIfResolved(ordered)
                android.util.Log.d(
                    "ChatStore",
                    "Silent chat final sync merged page ${pageCount + 1} with ${response.items.size} history items for $normalizedGatewayId/$normalizedSessionKey"
                )
                pageCount += 1
                cursor = response.nextCursor
                if (!response.hasMore || cursor.isNullOrBlank()) {
                    return
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("ChatStore", "Silent chat final sync failed for $normalizedGatewayId/$normalizedSessionKey", e)
        } finally {
            val current = _state.value
            if (current.currentGatewayId == normalizedGatewayId &&
                sameSessionKey(current.currentSessionKey, normalizedSessionKey)
            ) {
                _state.value = current.copy(
                    historyWindow = current.historyWindow.copy(isCatchingUp = false)
                )
            }
        }
    }

    private fun handleError(payload: JsonElement?) {
        val obj = payload as? JsonObject
        if (obj == null) {
            _state.value = _state.value.copy(errorMessage = choose("Unknown error", "未知错误"), isStreaming = false)
            return
        }
        handleError(obj, obj["payload"] as? JsonObject ?: obj)
    }

    private fun handleError(envelope: JsonObject, payload: JsonElement?) {
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
        cancelChatFinalSync(scope?.runScope)
        forgetRunScope(runId.orEmpty(), scope?.runScope)
    }

    private fun bindResolvedRunScope(responseId: String?, response: JsonObject?) {
        val normalizedResponseId = responseId?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val scope = chatRunScopes[normalizedResponseId] ?: return
        val payload = response?.get("payload") as? JsonObject
        val result = response?.get("result") as? JsonObject
        val resolvedRunId = payload?.deepString("runId", "run_id")
            ?: payload?.string("id")
            ?: result?.deepString("runId", "run_id")
            ?: result?.string("id")
            ?: response?.string("runId", "run_id")
        if (!resolvedRunId.isNullOrBlank()) {
            rememberRunScope(resolvedRunId, scope)
            scheduleChatFinalSync(resolvedRunId, scope)
        }
    }

    private fun resolveChatEventScope(envelope: JsonObject, payload: JsonObject, runId: String): ChatEventScope {
        val normalizedRunId = runId.trim()
        val explicitGatewayId = envelope.deepString("gatewayId", "gateway_id")
            ?: payload.deepString("gatewayId", "gateway_id")
        val explicitSessionKey = payload.deepString("sessionKey", "session_key")
            ?: envelope.deepString("sessionKey", "session_key")
        val provisionalGatewayId = explicitGatewayId ?: _state.value.currentGatewayId
        val provisionalSessionKey = normalizeSessionKey(explicitSessionKey ?: _state.value.currentSessionKey)
        val directRunScope = normalizedRunId.takeIf { it.isNotEmpty() }?.let { chatRunScopes[it] }
        val pendingRunScope = directRunScope ?: singlePendingRunScope(provisionalGatewayId, provisionalSessionKey)
        if (directRunScope == null && pendingRunScope != null && normalizedRunId.isNotBlank()) {
            rememberRunScope(normalizedRunId, pendingRunScope)
        }
        val gatewayId = explicitGatewayId
            ?: pendingRunScope?.gatewayId
            ?: _state.value.currentGatewayId
        val sessionKey = explicitSessionKey ?: pendingRunScope?.sessionKey

        return ChatEventScope(
            gatewayId = gatewayId?.trim()?.takeIf { it.isNotEmpty() },
            sessionKey = normalizeSessionKey(sessionKey),
            hasSessionKey = !sessionKey.isNullOrBlank(),
            runScope = pendingRunScope
        )
    }

    private fun isCurrentChatScope(scope: ChatEventScope): Boolean {
        if (!scope.hasSessionKey) return false
        val current = _state.value
        val currentGatewayId = current.currentGatewayId?.trim().orEmpty()
        val eventGatewayId = scope.gatewayId?.trim().orEmpty()
        val gatewayMatches = eventGatewayId.isBlank() ||
            currentGatewayId.isBlank() ||
            eventGatewayId == currentGatewayId
        return gatewayMatches && sameSessionKey(current.currentSessionKey, scope.sessionKey)
    }

    private fun rememberRunScope(runId: String, scope: ChatRunScope) {
        val normalizedRunId = runId.trim()
        if (normalizedRunId.isBlank()) return
        chatRunScopes[normalizedRunId] = scope.copy(
            gatewayId = scope.gatewayId.trim(),
            sessionKey = normalizeSessionKey(scope.sessionKey)
        )
        while (chatRunScopes.size > maxChatRunScopes) {
            val oldestKey = chatRunScopes.keys.firstOrNull() ?: break
            chatRunScopes.remove(oldestKey)
        }
    }

    private fun forgetRunScope(runId: String, runScope: ChatRunScope? = null) {
        val normalizedRunId = runId.trim()
        val scope = normalizedRunId.takeIf { it.isNotEmpty() }?.let { chatRunScopes[it] }
            ?: runScope
            ?: return
        val iterator = chatRunScopes.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value == scope) {
                iterator.remove()
            }
        }
    }

    private fun singlePendingRunScope(gatewayId: String?, sessionKey: String): ChatRunScope? {
        val normalizedGatewayId = gatewayId?.trim().orEmpty()
        val normalizedSessionKey = normalizeSessionKey(sessionKey)
        val currentMessages = _state.value.messages
        val pendingScopes = chatRunScopes.values
            .distinctBy { it.assistantMessageId }
            .filter { runScope ->
                val assistantMessageId = runScope.assistantMessageId ?: return@filter false
                val gatewayMatches = normalizedGatewayId.isBlank() || runScope.gatewayId == normalizedGatewayId
                val sessionMatches = sameSessionKey(runScope.sessionKey, normalizedSessionKey)
                val hasStreamingMessage = currentMessages.any { message ->
                    message.id == assistantMessageId &&
                        message.role == MessageRole.assistant &&
                        message.state == MessageState.streaming
                }
                gatewayMatches && sessionMatches && hasStreamingMessage
            }
        return pendingScopes.singleOrNull()
    }

    private fun shouldUseStreamingMessage(runId: String, scope: ChatEventScope): Boolean {
        val currentStreamingMessageId = streamingMessageId ?: return false
        scope.runScope?.assistantMessageId?.let { scopedAssistantMessageId ->
            return scopedAssistantMessageId == currentStreamingMessageId
        }
        val normalizedRunId = runId.trim()
        if (normalizedRunId.isBlank()) return true
        val streamingMessage = _state.value.messages.firstOrNull { it.id == currentStreamingMessageId } ?: return false
        return streamingMessage.runId.isNotBlank() && streamingMessage.runId == normalizedRunId
    }

    private fun completeHiddenRunIfNeeded(runId: String, runScope: ChatRunScope?) {
        val assistantMessageId = runScope?.assistantMessageId ?: return
        if (assistantMessageId != streamingMessageId) return
        cancelChatFinalSync(runScope)
        streamingMessageId = null
        streamingContent.clear()
        _state.value = _state.value.copy(
            isStreaming = false,
            isStoppingRun = false
        )
    }

    private fun noteSessionActivity(scope: ChatEventScope, lastActivityAt: String? = null) {
        if (!scope.hasSessionKey) return
        noteSessionActivity(scope.gatewayId, scope.sessionKey, lastActivityAt)
    }

    private fun noteSessionActivity(gatewayId: String?, sessionKey: String, lastActivityAt: String? = null) {
        val normalizedSessionKey = normalizeSessionKey(sessionKey)
        val current = _state.value
        val currentGatewayId = current.currentGatewayId?.trim().orEmpty()
        val normalizedGatewayId = gatewayId?.trim().orEmpty()
        if (normalizedGatewayId.isNotBlank() && currentGatewayId.isNotBlank() && normalizedGatewayId != currentGatewayId) {
            return
        }

        val activityAt = lastActivityAt?.trim()?.takeIf { it.isNotEmpty() } ?: Instant.now().toString()
        val existingIndex = current.sessions.indexOfFirst { sameSessionKey(it.sessionKey, normalizedSessionKey) }
        val updatedSessions = if (existingIndex >= 0) {
            current.sessions.mapIndexed { index, item ->
                if (index == existingIndex) item.copy(lastActivityAt = activityAt) else item
            }
        } else {
            listOf(ChatSessionItem(sessionKey = normalizedSessionKey, lastActivityAt = activityAt)) + current.sessions
        }
        _state.value = current.copy(
            sessions = updatedSessions.distinctBy { normalizeSessionKey(it.sessionKey).lowercase() }
        )
    }

    private fun persistSelectedSession(gatewayId: String, sessionKey: String) {
        val normalizedGatewayId = gatewayId.trim()
        if (normalizedGatewayId.isBlank()) return
        sessionSelectionStore?.save(normalizedGatewayId, normalizeSessionKey(sessionKey))
    }

    fun beginComposerAttachmentUploadMessages(
        attachments: List<ComposerAttachmentDraft>,
        gatewayId: String,
        sessionKey: String,
        senderDisplayName: String?,
        messageSortBaseTimestamp: Double
    ) {
        if (attachments.isEmpty()) return

        val messages = _state.value.messages.toMutableList()
        attachments.forEachIndexed { index, attachment ->
            val sortTimestamp = messageSortBaseTimestamp + (index * 0.001)
            val statusText = ComposerAttachmentUploadItem(
                gatewayId = gatewayId,
                attachment = attachment,
                progress = 0.0,
                phase = AttachmentUploadPhase.uploading,
                failureMessage = null
            ).statusText
            val message = ChatMessage(
                id = attachment.id,
                role = MessageRole.user,
                state = MessageState.streaming,
                content = sanitizeChatDisplayText(attachment.fileName),
                contentBlocks = listOf(
                    makeComposerAttachmentUploadContentBlock(
                        attachment = attachment,
                        gatewayId = gatewayId,
                        sessionKey = sessionKey,
                        senderDisplayName = senderDisplayName,
                        statusText = statusText,
                        downloadUrlString = attachment.fileUri
                    )
                ),
                createdAt = java.time.Instant.ofEpochMilli((sortTimestamp * 1000).toLong()).toString(),
                runId = composerAttachmentUploadRunId(attachment),
                sortTimestamp = sortTimestamp
            )

            val existingIndex = messages.indexOfFirst { it.id == attachment.id }
            if (existingIndex >= 0) {
                messages[existingIndex] = message
            } else {
                messages.add(message)
            }
        }

        _state.value = _state.value.copy(messages = orderedMessages(messages))
    }

    fun updateComposerAttachmentUploadMessage(
        attachment: ComposerAttachmentDraft,
        gatewayId: String,
        sessionKey: String,
        progress: Double,
        phase: AttachmentUploadPhase,
        failureMessage: String? = null,
        senderDisplayName: String? = null
    ) {
        val messages = _state.value.messages.toMutableList()
        val index = messages.indexOfFirst { it.id == attachment.id }
        if (index < 0) return

        val existing = messages[index]
        if (existing.transferContentBlocks().any { !it.fileId.isNullOrBlank() }) {
            return
        }
        val uploadPlaceholder = existing.copy(
            contentBlocks = listOf(
                makeComposerAttachmentUploadContentBlock(
                    attachment = attachment,
                    gatewayId = gatewayId,
                    sessionKey = sessionKey,
                    senderDisplayName = senderDisplayName ?: existing.transferContentBlocks().firstOrNull()?.senderDisplayName,
                    statusText = null,
                    downloadUrlString = attachment.fileUri
                )
            )
        )
        val completedDuplicateIndex = messages.indexOfFirst { message ->
            message.id != existing.id && samePendingUploadMessage(uploadPlaceholder, message)
        }
        if (completedDuplicateIndex >= 0) {
            val completedDuplicate = messages[completedDuplicateIndex]
            messages[index] = mergeCompletedFileMessage(
                existing = existing,
                completed = completedDuplicate.copy(
                    id = existing.id,
                    sortTimestamp = existing.sortTimestamp ?: completedDuplicate.sortTimestamp
                )
            )
            messages.removeAt(completedDuplicateIndex)
            _state.value = _state.value.copy(messages = orderedMessages(messages))
            return
        }
        val uploadItem = ComposerAttachmentUploadItem(
            gatewayId = gatewayId,
            attachment = attachment,
            progress = progress,
            phase = phase,
            failureMessage = failureMessage
        )
        messages[index] = ChatMessage(
            id = existing.id,
            role = existing.role,
            state = phase.toMessageState(),
            content = sanitizeChatDisplayText(attachment.fileName),
            contentBlocks = listOf(
                makeComposerAttachmentUploadContentBlock(
                    attachment = attachment,
                    gatewayId = gatewayId,
                    sessionKey = sessionKey,
                    senderDisplayName = senderDisplayName ?: existing.fileContentBlocks.firstOrNull()?.senderDisplayName,
                    statusText = uploadItem.statusText,
                    downloadUrlString = attachment.fileUri
                )
            ),
            createdAt = existing.createdAt,
            runId = existing.runId.ifBlank { composerAttachmentUploadRunId(attachment) },
            sortTimestamp = existing.sortTimestamp
        )

        _state.value = _state.value.copy(messages = orderedMessages(messages))
    }

    @Suppress("ReturnCount")
    fun completeComposerAttachmentUploadMessage(
        attachment: ComposerAttachmentDraft,
        record: RelayFileTransferItem,
        gatewayId: String,
        sessionKey: String,
        completionSortTimestamp: Double
    ): Boolean {
        val messages = _state.value.messages.toMutableList()
        val fileRunId = record.fileId.trim().takeIf { it.isNotEmpty() }?.let { fileMessageRunId(it) }
        val index = messages.indexOfFirst { message ->
            message.id == attachment.id ||
                (fileRunId != null && message.runId == fileRunId) ||
                (fileRunId != null && message.fileContentBlocks.any { it.fileId == record.fileId })
        }
        if (index < 0) return false

        val existing = messages[index]
        val finalBlock = makeFileContentBlock(record)
        val attachmentFile = File(attachment.fileUri)
        val attachmentCacheKey = finalBlock.chatAttachmentCacheKey()
        if (attachmentCacheKey != null) {
            runCatching {
                if (attachmentFile.exists()) {
                    RemoteAttachmentCache.put(
                        key = attachmentCacheKey,
                        fileName = attachment.fileName,
                        bytes = attachmentFile.readBytes()
                    )
                }
            }
        }
        if (finalBlock.isImageFileBlock) {
            val cacheKey = finalBlock.chatImageCacheKey()
            if (cacheKey != null) {
                runCatching {
                    BitmapFactory.decodeFile(attachmentFile.absolutePath)
                        ?.let { bitmap ->
                            RemoteImageCache.put(cacheKey, bitmap)
                            RemoteImageSizeCache.put(cacheKey, bitmap.width.toFloat() to bitmap.height.toFloat())
                        }
                }
            }
        }
        val completedMessage = ChatMessage(
            id = existing.id,
            role = if (record.origin.equals("mobile", ignoreCase = true)) MessageRole.user else MessageRole.assistant,
            state = MessageState.completed,
            content = sanitizeChatDisplayText(record.fileName),
            contentBlocks = listOf(finalBlock),
            createdAt = java.time.Instant.ofEpochMilli((completionSortTimestamp * 1000).toLong()).toString(),
            runId = if (record.fileId.isNotBlank()) fileMessageRunId(record.fileId) else existing.runId,
            sortTimestamp = existing.sortTimestamp ?: completionSortTimestamp
        )
        val finalMessage = mergeCompletedFileMessage(existing = existing, completed = completedMessage)
        messages[index] = finalMessage
        val dedupedMessages = messages.filterIndexed { messageIndex, message ->
            val isSameUploadPlaceholder = message.id == attachment.id ||
                message.runId == composerAttachmentUploadRunId(attachment)
            messageIndex == index || (!isSameUploadPlaceholder && !sameFileMessage(message, finalMessage))
        }
        _state.value = _state.value.copy(messages = orderedMessages(dedupedMessages))
        return true
    }

    private fun removeDuplicateFileMessages(fileMessage: ChatMessage) {
        val currentMessages = _state.value.messages
        val canonicalIndex = currentMessages.indexOfFirst { it.id == fileMessage.id }
        if (canonicalIndex < 0) return
        val deduped = currentMessages.filterIndexed { index, message ->
            index == canonicalIndex || !sameFileMessage(message, fileMessage)
        }
        if (deduped.size != currentMessages.size) {
            _state.value = _state.value.copy(messages = orderedMessages(deduped))
        }
    }

    fun sendMessage(
        content: String,
        gatewayId: String,
        attachmentIds: List<String> = emptyList(),
        attachmentBlocks: List<RelayChatContentBlock> = emptyList(),
        commandAttachments: List<RelayChatSendAttachmentPayload> = emptyList()
    ) {
        val sessionKey = _state.value.currentSessionKey
        if (sessionKey.isBlank()) return

        val clientRunId = UUID.randomUUID().toString()
        val requestId = UUID.randomUUID().toString()
        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.user,
            state = MessageState.completed,
            content = content.trim().takeIf { it.isNotEmpty() && it != " " } ?: "",
            contentBlocks = attachmentBlocks,
            createdAt = "",
            runId = "local-user-$clientRunId",
            sortTimestamp = System.currentTimeMillis() / 1000.0
        )
        
        val assistantMsgId = UUID.randomUUID().toString()
        val assistantMsg = ChatMessage(
            id = assistantMsgId,
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = choose("Connecting...", "正在连接..."),
            createdAt = "",
            runId = clientRunId,
            sortTimestamp = System.currentTimeMillis() / 1000.0 + 0.001
        )
        
        streamingMessageId = assistantMsgId
        streamingContent.setLength(0)
        streamingContent.append(assistantMsg.content)
        val runScope = ChatRunScope(
            gatewayId = gatewayId,
            sessionKey = sessionKey,
            assistantMessageId = assistantMsgId,
            triggeringUserMessageId = userMsg.id
        )
        rememberRunScope(clientRunId, runScope)
        rememberRunScope(requestId, runScope)
        persistSelectedSession(gatewayId, sessionKey)

        _state.value = _state.value.copy(
            messages = orderedMessages(_state.value.messages + userMsg + assistantMsg),
            isStreaming = true
        )
        scheduleChatFinalSync(clientRunId, runScope)
        wsClient.sendChatMessage(
            gatewayId = gatewayId,
            sessionKey = sessionKey,
            content = content,
            attachments = commandAttachments,
            idempotencyKey = clientRunId,
            requestId = requestId
        )
    }

    fun sendVoiceMessage(
        gatewayId: String,
        audio: VoiceSendAudioPayload,
        message: String? = null,
        languageHint: String? = null
    ) {
        val sessionKey = _state.value.currentSessionKey
        if (sessionKey.isBlank()) return

        val clientRunId = UUID.randomUUID().toString()
        val requestId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis() / 1000.0
        val userMsg = buildLocalVoiceUserMessage(
            audio = audio,
            gatewayId = gatewayId,
            sessionKey = sessionKey,
            clientRunId = clientRunId,
            sortTimestamp = now
        )
        val assistantMsgId = UUID.randomUUID().toString()
        val assistantMsg = ChatMessage(
            id = assistantMsgId,
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = choose("Waiting for host transcription...", "等待宿主机识别语音..."),
            createdAt = "",
            runId = clientRunId,
            sortTimestamp = now + 0.001
        )

        streamingMessageId = assistantMsgId
        streamingContent.setLength(0)
        streamingContent.append(assistantMsg.content)
        val runScope = ChatRunScope(
            gatewayId = gatewayId,
            sessionKey = sessionKey,
            assistantMessageId = assistantMsgId,
            triggeringUserMessageId = userMsg.id
        )
        rememberRunScope(clientRunId, runScope)
        rememberRunScope(requestId, runScope)
        persistSelectedSession(gatewayId, sessionKey)

        _state.value = _state.value.copy(
            messages = orderedMessages(_state.value.messages + userMsg + assistantMsg),
            isStreaming = true
        )
        scheduleChatFinalSync(clientRunId, runScope)
        wsClient.sendVoiceMessage(
            gatewayId = gatewayId,
            sessionKey = sessionKey,
            audio = audio,
            message = message,
            languageHint = languageHint,
            idempotencyKey = clientRunId,
            requestId = requestId
        )
    }

    suspend fun uploadAttachment(
        gatewayId: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        sha256: String,
        durationMs: Int? = null,
        imageWidth: Int? = null,
        imageHeight: Int? = null,
        senderDisplayName: String? = null,
        clientCreatedAt: String? = null,
        onProgress: ((Double) -> Unit)? = null
    ): RelayFileTransferItem {
        val sessionKey = _state.value.currentSessionKey
        if (sessionKey.isBlank()) throw IllegalStateException("No active chat session")
        val init = apiClient.initMobileFileUpload(
            gatewayId = gatewayId,
            sessionKey = sessionKey,
            fileName = fileName,
            mimeType = mimeType,
            sizeBytes = bytes.size.toLong(),
            sha256 = sha256,
            durationMs = durationMs,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            senderDisplayName = senderDisplayName,
            clientCreatedAt = clientCreatedAt
        )
        val chunkSize = init.chunkSize.coerceAtLeast(1)
        var offset = 0
        var chunkIndex = 0
        while (offset < bytes.size) {
            val end = minOf(offset + chunkSize, bytes.size)
            apiClient.uploadMobileFileChunk(init.uploadId, chunkIndex, bytes.copyOfRange(offset, end))
            offset = end
            chunkIndex += 1
            onProgress?.invoke((offset.toDouble() / bytes.size.toDouble()).coerceIn(0.0, 1.0))
        }
        if (bytes.isNotEmpty()) {
            onProgress?.invoke(1.0)
        }
        return apiClient.completeMobileFileUpload(init.uploadId, chunkIndex).payload
    }

    fun sendCommand(gatewayId: String, command: String) {
        val sessionKey = _state.value.currentSessionKey
        if (sessionKey.isNotBlank()) {
            val requestId = UUID.randomUUID().toString()
            rememberRunScope(requestId, ChatRunScope(gatewayId = gatewayId, sessionKey = sessionKey))
            persistSelectedSession(gatewayId, sessionKey)
            wsClient.sendCommand(gatewayId, sessionKey, command, requestId)
        }
    }

    fun abortRun() {
        if (!_state.value.isStreaming) return
        if (_state.value.isStoppingRun) return

        val gatewayId = _state.value.currentGatewayId
        val sessionKey = _state.value.currentSessionKey

        if (gatewayId.isNullOrBlank()) {
            _state.value = _state.value.copy(errorMessage = choose("No gateway selected. Please pair again.", "网关未选择，请重新配对"))
            return
        }
        if (sessionKey.isBlank()) {
            _state.value = _state.value.copy(errorMessage = choose("Session expired. Please pair again.", "会话已失效，请重新配对"))
            return
        }

        val activeRunId = _state.value.messages.lastOrNull { it.state == MessageState.streaming }?.runId

        val requestId = UUID.randomUUID().toString()
        abortRequestIds.add(requestId)
        _state.value = _state.value.copy(isStoppingRun = true)

        android.util.Log.d("ChatStore", "Stopping run: $activeRunId for gateway: $gatewayId, session: $sessionKey")

        wsClient.abortChatRun(gatewayId, sessionKey, activeRunId, requestId)
        completeCurrentStreamingMessageLocally(activeRunId)
    }

    private fun completeCurrentStreamingMessageLocally(runId: String?) {
        val result = completeStreamingMessageLocallyAfterStop(_state.value.messages, runId)
        if (!result.stoppedRunId.isNullOrBlank()) {
            locallyStoppedRunIds.add(result.stoppedRunId)
        }

        ignoreRunlessStoppedEventsUntilMs = System.currentTimeMillis() + stoppedRunlessEventIgnoreWindowMs
        val runScope = runId?.let { chatRunScopes[it] }
        cancelChatFinalSync(runScope)
        streamingMessageId = null
        streamingContent.clear()
        _state.value = _state.value.copy(
            messages = result.messages,
            isStreaming = false,
            isStoppingRun = false
        )
    }

    suspend fun loadHistory(gatewayId: String, sessionKey: String, limit: Int = chatHistoryPageSize) {
        val normalizedGatewayId = gatewayId.trim()
        val normalizedSessionKey = normalizeSessionKey(sessionKey)
        if (normalizedGatewayId.isBlank()) {
            _state.value = _state.value.copy(isLoading = false, isSwitchingSession = false)
            return
        }
            _state.value = _state.value.copy(
                currentGatewayId = normalizedGatewayId,
                currentSessionKey = normalizedSessionKey,
                isLoading = true,
                errorMessage = null,
                historyWindow = ChatHistoryWindowState()
            )
        persistSelectedSession(normalizedGatewayId, normalizedSessionKey)
        try {
            val response = retryOnceOnTransientFailure(
                operationName = "chat history for $normalizedGatewayId/$normalizedSessionKey"
            ) {
                fetchChatHistoryPage(normalizedGatewayId, normalizedSessionKey, limit)
            }
            val rawHistoryMessages = buildHistoryMessagesFromItems(response.items)
            val historyMessages = rawHistoryMessages
            val current = _state.value
            val messages = if (matchesRequestedChatScope(current, normalizedGatewayId, normalizedSessionKey)) {
                mergeHistoryWithCurrentMessages(
                    historyMessages = historyMessages,
                    currentMessages = current.messages,
                    currentStreamingMessageId = streamingMessageId,
                    isTrackedPendingAssistantMessageId = ::isTrackedPendingAssistantMessageId
                )
            } else {
                historyMessages
            }
            val ordered = trimToNewestHistoryWindow(messages)
            if (matchesRequestedChatScope(current, normalizedGatewayId, normalizedSessionKey)) {
                _state.value = current.copy(
                    messages = ordered,
                    isLoading = false,
                    isSwitchingSession = false,
                    isStreaming = hasActiveStreamingMessage(ordered),
                    isStoppingRun = if (hasActiveStreamingMessage(ordered)) current.isStoppingRun else false,
                    errorMessage = null,
                    historyWindow = current.historyWindow.copy(
                        isLoadingOlder = false,
                        isCatchingUp = false,
                        hasOlder = response.hasMore,
                        olderCursor = response.nextCursor,
                        newestCursor = response.newestCursor,
                        loadedMessageCount = ordered.size
                    )
                )
                clearStreamingPointersIfResolved(ordered)
            }
        } catch (e: CancellationException) {
            val currentState = _state.value
            if (matchesRequestedChatScope(currentState, normalizedGatewayId, normalizedSessionKey)) {
                _state.value = currentState.copy(
                    isLoading = false,
                    isSwitchingSession = false,
                    historyWindow = currentState.historyWindow.copy(isLoadingOlder = false, isCatchingUp = false)
                )
            }
            throw e
        } catch (e: Exception) {
            val currentState = _state.value
            if (!matchesRequestedChatScope(currentState, normalizedGatewayId, normalizedSessionKey)) {
                return
            }
            val shouldSuppressError = isTransientLoadFailure(e)
            if (shouldSuppressError) {
                android.util.Log.w("ChatStore", "Transient timeout while refreshing chat history for $normalizedGatewayId/$normalizedSessionKey", e)
            }
            _state.value = currentState.copy(
                isLoading = false,
                isSwitchingSession = false,
                historyWindow = currentState.historyWindow.copy(isLoadingOlder = false, isCatchingUp = false),
                errorMessage = visibleGatewayLoadErrorMessage(
                    isTransientLoadFailure = shouldSuppressError,
                    rawMessage = e.message
                )
            )
        }
    }

    suspend fun loadOlderHistory(gatewayId: String, sessionKey: String) {
        val normalizedGatewayId = gatewayId.trim()
        val requestedSessionKey = sessionKey.trim()
        val normalizedSessionKey = normalizeSessionKey(requestedSessionKey)
        val current = _state.value
        val window = current.historyWindow
        val cursor = window.olderCursor?.trim()?.takeIf { it.isNotEmpty() }
        if (normalizedGatewayId.isBlank() ||
            requestedSessionKey.isBlank() ||
            current.currentGatewayId != normalizedGatewayId ||
            !sameSessionKey(current.currentSessionKey, normalizedSessionKey) ||
            !window.hasOlder ||
            cursor == null ||
            window.isLoadingOlder
        ) {
            return
        }

        _state.value = current.copy(
            historyWindow = window.copy(isLoadingOlder = true)
        )
        try {
            val response = retryOnceOnTransientFailure(
                operationName = "older chat history for $normalizedGatewayId/$normalizedSessionKey"
            ) {
                fetchChatHistoryPage(
                    normalizedGatewayId,
                    normalizedSessionKey,
                    chatHistoryPageSize,
                    cursor,
                    "older"
                )
            }
            val latest = _state.value
            if (latest.currentGatewayId != normalizedGatewayId ||
                !sameSessionKey(latest.currentSessionKey, normalizedSessionKey)
            ) {
                return
            }
            val messages = mergeHistoryWithCurrentMessages(
                historyMessages = buildHistoryMessagesFromItems(response.items),
                currentMessages = latest.messages,
                currentStreamingMessageId = streamingMessageId,
                isTrackedPendingAssistantMessageId = ::isTrackedPendingAssistantMessageId
            )
            val ordered = trimToOlderHistoryWindow(messages)
            _state.value = latest.copy(
                messages = ordered,
                historyWindow = latest.historyWindow.copy(
                    isLoadingOlder = false,
                    hasOlder = response.hasMore,
                    olderCursor = response.nextCursor,
                    newestCursor = response.newestCursor ?: latest.historyWindow.newestCursor,
                    loadedMessageCount = ordered.size
                ),
                isStreaming = hasActiveStreamingMessage(ordered),
                isStoppingRun = if (hasActiveStreamingMessage(ordered)) latest.isStoppingRun else false
            )
            clearStreamingPointersIfResolved(ordered)
        } catch (e: CancellationException) {
            val latest = _state.value
            if (latest.currentGatewayId == normalizedGatewayId &&
                sameSessionKey(latest.currentSessionKey, normalizedSessionKey)
            ) {
                _state.value = latest.copy(
                    historyWindow = latest.historyWindow.copy(isLoadingOlder = false)
                )
            }
            throw e
        } catch (e: Exception) {
            val latest = _state.value
            if (latest.currentGatewayId == normalizedGatewayId &&
                sameSessionKey(latest.currentSessionKey, normalizedSessionKey)
            ) {
                _state.value = latest.copy(
                    historyWindow = latest.historyWindow.copy(isLoadingOlder = false)
                )
            }
            logWarning("Older chat history load failed for $normalizedGatewayId/$normalizedSessionKey", e)
        }
    }

    fun connectWebSocket() {
        val url = apiClient.baseUrl
        val token = apiClient.accessToken
        if (url.isNotBlank() && token.isNotBlank()) {
            wsClient.connect(url, token)
            schedulePendingFinalSyncsForCurrentSession()
        }
    }

    fun suspendWebSocket() {
        wsClient.suspendConnection()
    }

    fun resumeWebSocket() {
        wsClient.resumeConnection()
        schedulePendingFinalSyncsForCurrentSession()
    }

    suspend fun loadSessions(gatewayId: String): Boolean {
        val normalizedGatewayId = gatewayId.trim()
        if (normalizedGatewayId.isBlank()) {
            _state.value = _state.value.copy(isSwitchingSession = false)
            return false
        }
        try {
            val sessions = retryOnceOnTransientFailure(
                operationName = "chat sessions for $normalizedGatewayId"
            ) {
                apiClient.fetchChatSessions(normalizedGatewayId)
            }
            val currentState = _state.value
            val current = currentState.currentSessionKey
            val isNewGateway = currentState.currentGatewayId != normalizedGatewayId
            val shouldKeepCurrent = shouldKeepCurrentSessionAfterLoad(
                sessions = sessions,
                currentSessionKey = current,
                hasCurrentMessages = currentState.messages.isNotEmpty(),
                isSwitchingSession = currentState.isSwitchingSession,
                isNewGateway = isNewGateway
            )
            val persisted = sessionSelectionStore?.load(normalizedGatewayId)
            val selected = selectSessionKeyAfterLoad(
                sessions = sessions,
                currentSessionKey = current,
                persistedSessionKey = persisted,
                shouldKeepCurrent = shouldKeepCurrent
            )
            persistSelectedSession(normalizedGatewayId, selected)
            
            _state.value = currentState.copy(
                sessions = sessions,
                currentGatewayId = normalizedGatewayId,
                currentSessionKey = selected,
                messages = if (isNewGateway || selected != current) emptyList() else currentState.messages,
                isSwitchingSession = currentState.isSwitchingSession || isNewGateway || selected != current,
                historyWindow = if (isNewGateway || selected != current) ChatHistoryWindowState() else currentState.historyWindow,
                errorMessage = null
            )
            return true
        } catch (e: CancellationException) {
            val currentState = _state.value
            val isNewGateway = currentState.currentGatewayId != normalizedGatewayId
            _state.value = currentState.copy(
                currentGatewayId = normalizedGatewayId,
                currentSessionKey = currentState.currentSessionKey.ifBlank { defaultSessionKey },
                isSwitchingSession = false,
                historyWindow = if (isNewGateway) ChatHistoryWindowState() else currentState.historyWindow
            )
            throw e
        } catch (e: Exception) {
            android.util.Log.w("ChatStore", "Failed to load chat sessions for $normalizedGatewayId", e)
            val currentState = _state.value
            val selected = currentState.currentSessionKey.ifBlank { defaultSessionKey }
            val isTransientLoadFailure = isTransientLoadFailure(e)
            val isNewGateway = currentState.currentGatewayId != normalizedGatewayId

            _state.value = currentState.copy(
                currentGatewayId = normalizedGatewayId,
                currentSessionKey = selected,
                isSwitchingSession = false,
                historyWindow = if (isNewGateway) ChatHistoryWindowState() else currentState.historyWindow,
                errorMessage = visibleGatewayLoadErrorMessage(
                    isTransientLoadFailure = isTransientLoadFailure,
                    rawMessage = e.message
                )
            )
            return false
        }
    }

    private suspend fun <T> retryOnceOnTransientFailure(
        operationName: String,
        block: suspend () -> T
    ): T {
        return try {
            block()
        } catch (e: Exception) {
            if (!isTransientLoadFailure(e)) {
                throw e
            }
            android.util.Log.w("ChatStore", "Transient timeout while loading $operationName, retrying once", e)
            delay(350)
            block()
        }
    }

    private fun logWarning(message: String, throwable: Throwable? = null) {
        runCatching {
            if (throwable == null) {
                android.util.Log.w("ChatStore", message)
            } else {
                android.util.Log.w("ChatStore", message, throwable)
            }
        }
    }

    private fun isTransientLoadFailure(throwable: Throwable?): Boolean {
        var current: Throwable? = throwable
        while (current != null) {
            when (current) {
                is HttpRequestTimeoutException,
                is SocketTimeoutException -> return true
            }
            val message = current.message.orEmpty()
            if (isTransientGatewayLoadFailureMessage(message)) {
                return true
            }
            current = current.cause
        }
        return false
    }

    fun beginGatewaySwitch(gatewayId: String) {
        val normalizedGatewayId = gatewayId.trim().takeIf { it.isNotEmpty() } ?: return
        val current = _state.value
        if (current.currentGatewayId == normalizedGatewayId) return
        val selectedSessionKey = sessionSelectionStore?.load(normalizedGatewayId) ?: defaultSessionKey
        _state.value = current.copy(
            currentGatewayId = normalizedGatewayId,
            currentSessionKey = selectedSessionKey,
            sessions = listOf(ChatSessionItem(sessionKey = selectedSessionKey, lastActivityAt = null)),
            messages = emptyList(),
            isSwitchingSession = true,
            isStreaming = false,
            isStoppingRun = false,
            historyWindow = ChatHistoryWindowState(),
            errorMessage = null
        )
        streamingMessageId = null
        streamingContent.clear()
        abortRequestIds.clear()
        locallyStoppedRunIds.clear()
        chatRunScopes.clear()
        ignoreRunlessStoppedEventsUntilMs = 0
    }

    fun selectSession(sessionKey: String) {
        val normalized = sessionKey.trim().ifBlank { "main" }
        if (_state.value.currentSessionKey == normalized) return
        _state.value.currentGatewayId?.let { gatewayId ->
            persistSelectedSession(gatewayId, normalized)
        }
        _state.value = _state.value.copy(
            currentSessionKey = normalized,
            messages = emptyList(),
            isSwitchingSession = true,
            isStreaming = false,
            isStoppingRun = false,
            historyWindow = ChatHistoryWindowState(),
            errorMessage = null
        )
        streamingMessageId = null
        streamingContent.clear()
    }

    fun newSession() {
        val key = "session_${System.currentTimeMillis()}"
        val current = _state.value
        val session = ChatSessionItem(sessionKey = key, lastActivityAt = null)
        val sessions = (listOf(session) + current.sessions)
            .distinctBy { it.sessionKey.trim().lowercase().ifBlank { defaultSessionKey } }
        _state.value = current.copy(
            currentSessionKey = key,
            sessions = sessions,
            messages = emptyList(),
            isSwitchingSession = false,
            isStreaming = false,
            isStoppingRun = false,
            historyWindow = ChatHistoryWindowState(),
            errorMessage = null
        )
        current.currentGatewayId?.let { gatewayId ->
            persistSelectedSession(gatewayId, key)
        }
        streamingMessageId = null
        streamingContent.clear()
    }

    fun setShowInvocationProcess(enabled: Boolean) {
        _state.value = _state.value.copy(showInvocationProcess = enabled)
    }

    fun toggleShowInvocation() {
        setShowInvocationProcess(!_state.value.showInvocationProcess)
    }

    private fun Set<String>.takeLastSet(limit: Int): Set<String> {
        if (size <= limit) return this
        return toList().takeLast(limit).toSet()
    }

    fun clearMessages() {
        _state.value = _state.value.copy(
            messages = emptyList(),
            isSwitchingSession = false,
            isStoppingRun = false,
            isStreaming = false,
            historyWindow = ChatHistoryWindowState()
        )
        streamingMessageId = null
        streamingContent.clear()
        abortRequestIds.clear()
        locallyStoppedRunIds.clear()
        chatRunScopes.clear()
        ignoreRunlessStoppedEventsUntilMs = 0
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    fun clearSessionImageCaches(gatewayId: String, sessionKey: String) {
        val normalizedGatewayId = gatewayId.trim()
        val normalizedSessionKey = sessionKey.trim().ifBlank { defaultSessionKey }
        if (normalizedGatewayId.isBlank()) return
        RemoteImageCache.clearSession(normalizedGatewayId, normalizedSessionKey)
        RemoteImageSizeCache.clearSession(normalizedGatewayId, normalizedSessionKey)
        RemoteAttachmentCache.clearSession(normalizedGatewayId, normalizedSessionKey)
    }

    fun markVoicePlaybackIdentifierRead(identifier: String, gatewayId: String?, sessionKey: String?) {
        val storageKey = voicePlaybackReadStorageKey(identifier, gatewayId, sessionKey)
        if (storageKey.isBlank()) return
        
        VoicePlaybackReadStore.markRead(storageKey)
        _state.value = _state.value.copy(
            readVoicePlaybackIdentifiers = _state.value.readVoicePlaybackIdentifiers + storageKey
        )
    }

    private fun voicePlaybackReadStorageKey(identifier: String, gatewayId: String?, sessionKey: String?): String {
        val normalizedIdentifier = identifier.trim()
        if (normalizedIdentifier.isEmpty()) return ""
        
        val resolvedGatewayId = (gatewayId ?: _state.value.currentGatewayId ?: "gateway").trim()
        val resolvedSessionKey = (sessionKey ?: _state.value.currentSessionKey.ifBlank { "main" }).trim()
        
        return "$resolvedGatewayId|$resolvedSessionKey|$normalizedIdentifier"
    }

    suspend fun deleteSession(
        gatewayId: String,
        sessionKey: String,
        deleteTranscript: Boolean = true,
        gatewayType: GatewayType = GatewayType.openclaw
    ): Boolean {
        val normalizedGatewayId = gatewayId.trim()
        val normalizedSessionKey = sessionKey.trim().ifBlank { defaultSessionKey }
        if (normalizedGatewayId.isBlank() || normalizedSessionKey.isBlank()) return false

        val apiFailure = try {
            if (apiClient.deleteChatSession(normalizedGatewayId, normalizedSessionKey, deleteTranscript)) {
                applyDeletedSessionLocally(normalizedGatewayId, normalizedSessionKey)
                return true
            }
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!shouldFallbackToRelayCommandForSessionDelete(e)) {
                throw e
            }
            e
        }

        if (confirmDeletedSession(normalizedGatewayId, normalizedSessionKey)) {
            applyDeletedSessionLocally(normalizedGatewayId, normalizedSessionKey)
            return true
        }

        android.util.Log.w(
            "ChatStore",
            "Falling back to relay command for chat session delete",
            apiFailure
        )
        connectWebSocket()
        wsClient.executeCommand(
            gatewayId = normalizedGatewayId,
            method = chatSessionDeleteRelayMethod(gatewayType),
            params = buildChatSessionDeleteCommandParams(normalizedSessionKey, deleteTranscript)
        )
        delay(150)

        if (confirmDeletedSession(normalizedGatewayId, normalizedSessionKey)) {
            applyDeletedSessionLocally(normalizedGatewayId, normalizedSessionKey)
            return true
        }
        return false
    }

    private suspend fun confirmDeletedSession(gatewayId: String, sessionKey: String): Boolean {
        repeat(4) { attempt ->
            val sessions = try {
                retryOnceOnTransientFailure(operationName = "chat sessions after delete for $gatewayId") {
                    apiClient.fetchChatSessions(gatewayId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("ChatStore", "Failed to confirm deleted chat session for $gatewayId/$sessionKey", e)
                null
            }

            if (sessions != null) {
                val current = _state.value
                _state.value = current.copy(
                    sessions = sessions,
                    currentGatewayId = gatewayId,
                    errorMessage = null
                )
                if (sessions.none { sameSessionKey(it.sessionKey, sessionKey) }) {
                    return true
                }
            }

            if (attempt < 3) {
                delay(200)
            }
        }
        return false
    }

    private fun applyDeletedSessionLocally(gatewayId: String, sessionKey: String) {
        val current = _state.value
        val remainingSessions = current.sessions.filterNot { sameSessionKey(it.sessionKey, sessionKey) }
        val isActiveDeleted = current.currentGatewayId == gatewayId && sameSessionKey(current.currentSessionKey, sessionKey)
        val nextSessionKey = if (isActiveDeleted) {
            remainingSessions.firstOrNull()?.sessionKey?.trim()?.ifBlank { defaultSessionKey } ?: defaultSessionKey
        } else {
            current.currentSessionKey
        }
        _state.value = current.copy(
            sessions = remainingSessions,
            currentSessionKey = nextSessionKey,
            messages = if (isActiveDeleted) emptyList() else current.messages,
            isSwitchingSession = current.isSwitchingSession || isActiveDeleted,
            historyWindow = if (isActiveDeleted) ChatHistoryWindowState() else current.historyWindow,
            contextUsageLinesByGatewayAndSession = current.contextUsageLinesByGatewayAndSession.toMutableMap().also { byGateway ->
                val usageBySession = byGateway[gatewayId]?.toMutableMap() ?: return@also
                usageBySession.keys
                    .filter { sameSessionKey(it, sessionKey) }
                    .forEach { usageBySession.remove(it) }
                byGateway[gatewayId] = usageBySession
            }
        )
        clearSessionImageCaches(gatewayId, sessionKey)
        sessionSelectionStore?.clear(gatewayId, sessionKey)
        if (isActiveDeleted) {
            persistSelectedSession(gatewayId, nextSessionKey)
        }
    }

    private fun shouldIgnoreLocallyStoppedEvent(runId: String): Boolean {
        pruneLocallyStoppedRuns()
        val normalizedRunId = runId.trim()
        if (normalizedRunId.isNotEmpty() && locallyStoppedRunIds.contains(normalizedRunId)) {
            return true
        }
        return normalizedRunId.isEmpty()
            && streamingMessageId == null
            && System.currentTimeMillis() < ignoreRunlessStoppedEventsUntilMs
    }

    private fun pruneLocallyStoppedRuns() {
        if (System.currentTimeMillis() >= ignoreRunlessStoppedEventsUntilMs) {
            ignoreRunlessStoppedEventsUntilMs = 0
        }
        if (locallyStoppedRunIds.size > maxLocallyStoppedRunIds) {
            locallyStoppedRunIds.clear()
        }
    }

    private companion object {
        const val stoppedRunlessEventIgnoreWindowMs = 15_000L
        const val maxLocallyStoppedRunIds = 64
        const val maxChatRunScopes = 256
        const val chatFinalSyncInitialDelayMs = 2_500L
        const val chatFinalSyncFastRetryDelayMs = 4_000L
        const val chatFinalSyncSlowRetryDelayMs = 8_000L
        const val chatFinalSyncMaxAttempts = 60
        const val chatHistoryPageSize = 100
        const val chatHistoryWindowMaxMessages = 500
        const val chatHistoryPendingResolveMaxPages = 5
    }
}

internal fun newestBoundedHistoryWindowMessages(
    messages: List<ChatMessage>,
    maxMessages: Int
): List<ChatMessage> {
    if (maxMessages <= 0) return emptyList()
    return orderMessagesWithSourceRunAnchors(messages).takeLast(maxMessages)
}

internal fun olderBoundedHistoryWindowMessages(
    messages: List<ChatMessage>,
    maxMessages: Int,
    shouldPreserveActiveMessage: (ChatMessage) -> Boolean
): List<ChatMessage> {
    if (maxMessages <= 0) return emptyList()
    val ordered = orderMessagesWithSourceRunAnchors(messages)
    if (ordered.size <= maxMessages) return ordered

    val oldestWindow = ordered.take(maxMessages)
    val oldestWindowIds = oldestWindow.mapTo(mutableSetOf()) { it.id }
    val activeMessagesOutsideOldestWindow = ordered.filter { message ->
        message.id !in oldestWindowIds && shouldPreserveActiveMessage(message)
    }
    if (activeMessagesOutsideOldestWindow.isEmpty()) {
        return oldestWindow
    }

    val retainedOldestCount = (maxMessages - activeMessagesOutsideOldestWindow.size).coerceAtLeast(0)
    val retainedOldestWindow = oldestWindow.take(retainedOldestCount)
    return orderMessagesWithSourceRunAnchors(retainedOldestWindow + activeMessagesOutsideOldestWindow)
        .take(maxMessages)
}
