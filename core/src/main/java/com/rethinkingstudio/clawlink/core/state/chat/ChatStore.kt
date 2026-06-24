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
import com.rethinkingstudio.clawlink.core.state.chat.RemoteImageCache
import com.rethinkingstudio.clawlink.core.state.chat.RemoteImageSizeCache
import com.rethinkingstudio.clawlink.core.state.chat.RemoteAttachmentCache
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal typealias ChatHistoryPageFetcher = suspend (
    gatewayId: String,
    sessionKey: String,
    limit: Int,
    cursor: String?,
    direction: String
) -> ChatHistoryResponse

class ChatStore(
    internal val apiClient: RelayAPIClient,
    internal val wsClient: RelayWebSocketClient,
    internal val notificationPort: NotificationPort,
    internal val sessionSelectionStore: ChatSessionSelectionStore? = null,
    private val chatHistoryPageFetcher: ChatHistoryPageFetcher? = null
) {
    val relayBaseUrl: String get() = apiClient.baseUrl
    val accessToken: String get() = apiClient.accessToken

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    internal val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    internal var streamingMessageId: String? = null
    internal var streamingContent = StringBuilder()
    internal val chatRunScopes = linkedMapOf<String, ChatRunScope>()
    internal val abortRequestIds = mutableSetOf<String>()
    internal val locallyStoppedRunIds = mutableSetOf<String>()
    private val chatFinalSyncJobs = mutableMapOf<String, Job>()
    internal var ignoreRunlessStoppedEventsUntilMs: Long = 0
    internal var timelineState = ChatTimelineState()
    private val v3Sessions = mutableSetOf<String>()
    private val historyCoordinator = ChatHistoryCoordinator(
        apiClient = apiClient,
        chatHistoryPageFetcher = chatHistoryPageFetcher,
        getState = { _state.value },
        setState = { nextState -> _state.value = nextState },
        getTimelineState = { timelineState },
        setTimelineState = { nextTimelineState -> timelineState = nextTimelineState },
        v3Sessions = v3Sessions,
        currentStreamingMessageId = { streamingMessageId },
        isTrackedPendingAssistantMessageId = ::isTrackedPendingAssistantMessageId,
        clearStreamingPointersIfResolved = ::clearStreamingPointersIfResolved,
        orderedMessages = ::orderedMessages,
        persistSelectedSession = ::persistSelectedSession,
        needsChatFinalSync = ::needsChatFinalSync,
        pageSize = chatHistoryPageSize,
        windowMaxMessages = chatHistoryWindowMaxMessages,
        pendingResolveMaxPages = chatHistoryPendingResolveMaxPages
    )
    private val sessionDeletionCoordinator = ChatSessionDeletionCoordinator(
        apiClient = apiClient,
        wsClient = wsClient,
        sessionSelectionStore = sessionSelectionStore,
        getState = { _state.value },
        setState = { nextState -> _state.value = nextState },
        connectWebSocket = ::connectWebSocket,
        clearSessionCaches = ::clearSessionImageCaches,
        persistSelectedSession = ::persistSelectedSession
    )

    init {
        _state.value = _state.value.copy(
            readVoicePlaybackIdentifiers = VoicePlaybackReadStore.getReadIdentifiers()
        )
        wsClient.events
            .onEach { event -> handleWsEvent(event) }
            .launchIn(scope)
    }

    fun rehydrateTimelineState() {
        val restored = TimelinePersistenceMiddleware.restoreSnapshot() ?: return
        timelineState = restored
        val ordered = orderedMessages(restored.messages)
        val hasActiveVisibleRun = hasActiveVisibleTimelineRun(restored, ordered)
        _state.value = _state.value.copy(
            messages = ordered,
            isStreaming = hasActiveVisibleRun,
            isStoppingRun = if (hasActiveVisibleRun) _state.value.isStoppingRun else false
        )
    }

    private fun handleWsEvent(event: WsEvent) {
        handleRealtimeWsEvent(event)
    }

    private fun handleChatPayload(payload: JsonElement?) {
        handleRealtimeChatPayload(payload)
    }

    private fun handleFinal(envelope: JsonObject, payload: JsonElement?) {
        handleRealtimeFinal(envelope, payload)
    }

    private fun handleError(payload: JsonElement?) {
        handleRealtimeError(payload)
    }

    internal fun orderedMessages(messages: List<ChatMessage>): List<ChatMessage> {
        val sessionKey = _state.value.currentSessionKey
        return sortTimelineMessagesV3(removeResolvedTransientAssistantPlaceholders(messages), sessionKey)
    }

    internal fun orderMessagesForRealtime(messages: List<ChatMessage>): List<ChatMessage> {
        return orderedMessages(messages)
    }

    internal fun completeCurrentRun(runId: String, runScope: ChatRunScope?) {
        cancelChatFinalSync(runScope)
        markTimelineRunResolved(runId, runScope)
        forgetRunScope(runId, runScope)
    }

    private fun markTimelineRunResolved(runId: String, runScope: ChatRunScope?) {
        val ordered = orderedMessages(_state.value.messages)
        val runIdsToClear = buildSet {
            runId.trim().takeIf { it.isNotEmpty() }?.let(::add)
            runScope?.assistantMessageId
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { assistantMessageId ->
                    ordered.firstOrNull { message -> message.id == assistantMessageId }
                        ?.runId
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?.let(::add)
                }
        }
        val turnIdsToClear = buildSet {
            runIdsToClear.forEach { resolvedRunId ->
                timelineState.activeTurnByRunId[resolvedRunId]?.let(::add)
            }
            timelineState.activeRunsByTurnId
                .filterValues { activeRunId -> activeRunId in runIdsToClear }
                .keys
                .forEach(::add)
        }
        timelineState = timelineState.copy(
            messages = ordered,
            activeRunId = timelineState.activeRunId?.takeUnless { activeRunId -> activeRunId in runIdsToClear },
            activeRunsByTurnId = timelineState.activeRunsByTurnId
                .filterKeys { turnId -> turnId !in turnIdsToClear }
                .filterValues { activeRunId -> activeRunId !in runIdsToClear },
            activeTurnByRunId = timelineState.activeTurnByRunId
                .filterKeys { activeRunId -> activeRunId !in runIdsToClear }
                .filterValues { turnId -> turnId !in turnIdsToClear }
        )
        if (hasActiveVisibleTimelineRun(timelineState, ordered)) {
            TimelinePersistenceMiddleware.persistSnapshot(timelineState.copy(messages = ordered))
        } else {
            TimelinePersistenceMiddleware.clearSnapshot()
        }
    }

    internal fun scheduleChatFinalSync(runId: String, runScope: ChatRunScope?, attempt: Int = 0) {
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
            historyCoordinator.resolvePendingFinalFromHistory(
                gatewayId = gatewayId,
                sessionKey = sessionKey,
                runId = runId,
                runScope = runScope
            )
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
                    message.state == MessageState.streaming
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
                directMessage.state == MessageState.streaming
        }

        val triggeringUserId = runScope.triggeringUserMessageId?.trim().orEmpty()
        if (triggeringUserId.isBlank()) return false
        val triggerIndex = messages.indexOfLast { it.id == triggeringUserId }
        if (triggerIndex < 0) return false
        return messages
            .drop(triggerIndex + 1)
            .takeWhile { it.role != MessageRole.user }
            .any { it.role == MessageRole.assistant && it.state == MessageState.streaming }
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

    internal fun hasPendingAssistantPlaceholder(messages: List<ChatMessage>): Boolean {
        return messages.any { message ->
            message.role == MessageRole.assistant &&
                message.state == MessageState.streaming
        }
    }

    internal fun resetCurrentTimelineScope() {
        timelineState = ChatTimelineState()
        TimelinePersistenceMiddleware.clearSnapshot()
    }

    internal fun clearStreamingPointersIfResolved(messages: List<ChatMessage>) {
        val currentStreamingMessageId = streamingMessageId?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val stillStreaming = messages.any { message ->
            message.id == currentStreamingMessageId && message.state == MessageState.streaming
        }
        if (!stillStreaming) {
            streamingMessageId = null
            streamingContent.clear()
        }
    }

    private fun isTrackedPendingAssistantMessageId(messageId: String): Boolean {
        return chatRunScopes.values.any { it.assistantMessageId == messageId }
    }

    internal fun bindResolvedRunScope(responseId: String?, response: JsonObject?) {
        val normalizedResponseId = responseId?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val scope = chatRunScopes[normalizedResponseId] ?: return
        val resolvedRunId = resolvedRunIdFromCommandResponse(response)
        if (!resolvedRunId.isNullOrBlank()) {
            rememberRunScope(resolvedRunId, scope)
            scheduleChatFinalSync(resolvedRunId, scope)
        }
    }

    internal fun resolveChatEventScope(envelope: JsonObject, payload: JsonObject, runId: String): ChatEventScope {
        return resolveChatEventScope(
            envelope = envelope,
            payload = payload,
            runId = runId,
            currentGatewayId = _state.value.currentGatewayId,
            currentSessionKey = _state.value.currentSessionKey,
            messages = _state.value.messages,
            chatRunScopes = chatRunScopes,
            bindRunScope = ::rememberRunScope
        )
    }

    internal fun isCurrentChatScope(scope: ChatEventScope): Boolean {
        return isCurrentChatScope(scope, _state.value)
    }

    internal fun rememberRunScope(runId: String, scope: ChatRunScope) {
        rememberRunScope(chatRunScopes, runId, scope, maxChatRunScopes)
    }

    internal fun rememberRunScopeForRealtime(runId: String, scope: ChatRunScope) {
        rememberRunScope(runId, scope)
    }

    internal fun forgetRunScope(runId: String, runScope: ChatRunScope? = null) {
        forgetRunScope(chatRunScopes, runId, runScope)
    }

    internal fun shouldUseStreamingMessage(runId: String, scope: ChatEventScope): Boolean {
        val currentStreamingMessageId = streamingMessageId ?: return false
        scope.runScope?.assistantMessageId?.let { scopedAssistantMessageId ->
            return scopedAssistantMessageId == currentStreamingMessageId
        }
        val normalizedRunId = runId.trim()
        if (normalizedRunId.isBlank()) return true
        val streamingMessage = _state.value.messages.firstOrNull { it.id == currentStreamingMessageId } ?: return false
        return streamingMessage.runId.isNotBlank() && streamingMessage.runId == normalizedRunId
    }

    internal fun completeHiddenRunIfNeeded(runId: String, runScope: ChatRunScope?) {
        val assistantMessageId = runScope?.assistantMessageId ?: return
        if (assistantMessageId != streamingMessageId) return
        cancelChatFinalSync(runScope)
        streamingMessageId = null
        streamingContent.clear()
        _state.value = _state.value.copy(
            isStreaming = false,
            isStoppingRun = false
        )
        markTimelineRunResolved(runId, runScope)
    }

    internal fun noteSessionActivity(scope: ChatEventScope, lastActivityAt: String? = null) {
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

        _state.value = current.copy(
            sessions = sessionsWithActivity(
                sessions = current.sessions,
                sessionKey = normalizedSessionKey,
                lastActivityAt = lastActivityAt
            )
        )
    }

    internal fun persistSelectedSession(gatewayId: String, sessionKey: String) {
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
        val messages = ComposerAttachmentMessageUpdater.begin(
            currentMessages = _state.value.messages,
            attachments = attachments,
            gatewayId = gatewayId,
            sessionKey = sessionKey,
            senderDisplayName = senderDisplayName,
            messageSortBaseTimestamp = messageSortBaseTimestamp,
            orderMessages = ::orderedMessages
        )
        _state.value = _state.value.copy(messages = messages)
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
        val messages = ComposerAttachmentMessageUpdater.update(
            currentMessages = _state.value.messages,
            attachment = attachment,
            gatewayId = gatewayId,
            sessionKey = sessionKey,
            progress = progress,
            phase = phase,
            failureMessage = failureMessage,
            senderDisplayName = senderDisplayName,
            orderMessages = ::orderedMessages
        ) ?: return
        _state.value = _state.value.copy(messages = messages)
    }

    @Suppress("ReturnCount")
    fun completeComposerAttachmentUploadMessage(
        attachment: ComposerAttachmentDraft,
        record: RelayFileTransferItem,
        gatewayId: String,
        sessionKey: String,
        completionSortTimestamp: Double
    ): Boolean {
        val result = ComposerAttachmentMessageUpdater.complete(
            currentMessages = _state.value.messages,
            attachment = attachment,
            record = record,
            completionSortTimestamp = completionSortTimestamp,
            orderMessages = ::orderedMessages
        )
        if (!result.completed) return false
        _state.value = _state.value.copy(messages = result.messages)
        return true
    }

    internal fun removeDuplicateFileMessages(fileMessage: ChatMessage) {
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
        sendTextOutgoingRun(
            content = content,
            gatewayId = gatewayId,
            attachmentIds = attachmentIds,
            attachmentBlocks = attachmentBlocks,
            commandAttachments = commandAttachments
        )
    }

    fun sendVoiceMessage(
        gatewayId: String,
        audio: VoiceSendAudioPayload,
        message: String? = null,
        languageHint: String? = null
    ) {
        sendVoiceOutgoingRun(
            gatewayId = gatewayId,
            audio = audio,
            message = message,
            languageHint = languageHint
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
        return uploadMobileAttachment(
            apiClient = apiClient,
            gatewayId = gatewayId,
            sessionKey = sessionKey,
            fileName = fileName,
            mimeType = mimeType,
            bytes = bytes,
            sha256 = sha256,
            durationMs = durationMs,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            senderDisplayName = senderDisplayName,
            clientCreatedAt = clientCreatedAt,
            onProgress = onProgress
        )
    }

    fun sendCommand(gatewayId: String, command: String) {
        sendSlashCommand(gatewayId, command)
    }

    fun abortRun() {
        abortActiveRun()
    }

    suspend fun loadHistory(
        gatewayId: String,
        sessionKey: String,
        limit: Int = chatHistoryPageSize,
        keepSwitchingOverlay: Boolean = true
    ) {
        historyCoordinator.loadHistory(
            gatewayId = gatewayId,
            sessionKey = sessionKey,
            limit = limit,
            keepSwitchingOverlay = keepSwitchingOverlay
        )
    }

    suspend fun loadOlderHistory(gatewayId: String, sessionKey: String) {
        historyCoordinator.loadOlderHistory(gatewayId, sessionKey)
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
        return loadSessionsForGateway(gatewayId)
    }

    fun beginGatewaySwitch(gatewayId: String) {
        beginGatewaySwitchSelection(gatewayId)
    }

    fun selectSession(sessionKey: String) {
        selectChatSession(sessionKey)
    }

    fun newSession(sessionKey: String? = null): String {
        return createChatSession(sessionKey)
    }

    fun setShowInvocationProcess(enabled: Boolean) {
        _state.value = _state.value.copy(showInvocationProcess = enabled)
    }

    fun toggleShowInvocation() {
        setShowInvocationProcess(!_state.value.showInvocationProcess)
    }

    fun clearMessages() {
        clearChatMessages()
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

    suspend fun loadToolDetail(
        gatewayId: String,
        sessionKey: String,
        toolCallId: String,
        cursor: String? = null,
        limit: Int = 20_000
    ): Boolean = loadChatToolDetail(
        apiClient = apiClient,
        gatewayId = gatewayId,
        sessionKey = sessionKey,
        toolCallId = toolCallId,
        cursor = cursor,
        limit = limit,
        getState = { _state.value },
        setState = { _state.value = it }
    )

    fun markVoicePlaybackIdentifierRead(identifier: String, gatewayId: String?, sessionKey: String?) {
        val storageKey = voicePlaybackReadStorageKey(
            identifier = identifier,
            gatewayId = gatewayId,
            sessionKey = sessionKey,
            currentGatewayId = _state.value.currentGatewayId,
            currentSessionKey = _state.value.currentSessionKey
        )
        if (storageKey.isBlank()) return
        
        VoicePlaybackReadStore.markRead(storageKey)
        _state.value = _state.value.copy(
            readVoicePlaybackIdentifiers = _state.value.readVoicePlaybackIdentifiers + storageKey
        )
    }

    suspend fun deleteSession(
        gatewayId: String,
        sessionKey: String,
        deleteTranscript: Boolean = true,
        gatewayType: GatewayType = GatewayType.openclaw
    ): Boolean {
        return sessionDeletionCoordinator.deleteSession(
            gatewayId = gatewayId,
            sessionKey = sessionKey,
            deleteTranscript = deleteTranscript,
            gatewayType = gatewayType
        )
    }

    internal fun shouldIgnoreLocallyStoppedEvent(runId: String): Boolean {
        return shouldIgnoreLocallyStoppedRunEvent(runId)
    }

    internal fun pruneLocallyStoppedRuns() {
        pruneLocallyStoppedRunIds()
    }

    private companion object {
        const val maxChatRunScopes = 256
        const val chatFinalSyncInitialDelayMs = 2_500L
        const val chatFinalSyncFastRetryDelayMs = 4_000L
        const val chatFinalSyncSlowRetryDelayMs = 8_000L
        const val chatFinalSyncMaxAttempts = 60
        const val chatHistoryPageSize = 500
        const val chatHistoryWindowMaxMessages = 500
        const val chatHistoryPendingResolveMaxPages = 5
        const val localUserEchoMergeWindowSeconds = 600.0
    }
}
