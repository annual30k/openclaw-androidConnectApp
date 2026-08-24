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
import com.rethinkingstudio.clawlink.core.network.dto.ChatSyncResponse
import com.rethinkingstudio.clawlink.core.network.dto.RelayFileTransferItem
import com.rethinkingstudio.clawlink.core.network.transport.RelayChatSendAttachmentPayload
import com.rethinkingstudio.clawlink.core.network.transport.RelayWebSocketClient
import com.rethinkingstudio.clawlink.core.network.transport.VoiceSendAudioPayload
import com.rethinkingstudio.clawlink.core.network.transport.WsConnectionState
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

internal typealias ChatTimelineSyncPageFetcher = suspend (
    gatewayId: String,
    sessionKey: String,
    cursor: String?
) -> ChatSyncResponse

private data class TimelineMutationSignal(
    val generation: Long,
    val revision: Long
)

private data class CanonicalHistoryReconcileRequest(
    val gatewayId: String,
    val sessionKey: String,
    val generation: Long
)

class ChatStore(
    internal val apiClient: RelayAPIClient,
    internal val wsClient: RelayWebSocketClient,
    internal val notificationPort: NotificationPort,
    internal val sessionSelectionStore: ChatSessionSelectionStore? = null,
    private val chatHistoryPageFetcher: ChatHistoryPageFetcher? = null,
    private val chatTimelineSyncPageFetcher: ChatTimelineSyncPageFetcher? = null,
    private val chatTimelineSyncCursorLoader: ((String, String) -> String?)? = null,
    private val gatewayTypeFor: (String) -> GatewayType = { GatewayType.openclaw }
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
    private val timelineMutationSignal = MutableStateFlow(TimelineMutationSignal(0L, 0L))
    private val canonicalHistoryReconcileLock = Any()
    private var pendingCanonicalHistoryReconcile: CanonicalHistoryReconcileRequest? = null
    private var canonicalHistoryReconcileInFlight = false
    internal var ignoreRunlessStoppedEventsUntilMs: Long = 0
    internal var timelineState = ChatTimelineState()
    internal var timelineScopeGeneration: Long = 0L
        private set
    internal var timelineMutationRevision: Long = 0L
        private set
    internal var timelineSnapshotRevision: String? = null
    internal var timelineHighWatermark: Long? = null
    internal val timelineOutbox = linkedMapOf<String, TimelineOutboxEntry>()
    private val incrementalSyncScopesInFlight = mutableSetOf<String>()
    // 队列激活是一个跨多个内存状态与持久化快照的原子事务；所有触发入口必须共用同一把锁，
    // 避免终态事件、历史恢复和重连在 IO 线程池上并发激活同一条或相邻两条消息。
    internal val queuedTimelineOutboxDrainLock = Any()
    internal var historyPrepareAttemptHookForTest: (() -> Unit)? = null
    private val v3Sessions = mutableSetOf<String>()
    private val historyCoordinator = ChatHistoryCoordinator(
        apiClient = apiClient,
        chatHistoryPageFetcher = chatHistoryPageFetcher,
        getState = { _state.value },
        setState = { nextState -> _state.value = nextState },
        getTimelineState = { timelineState },
        setTimelineState = { nextTimelineState -> timelineState = nextTimelineState },
        v3Sessions = v3Sessions,
        locallyStoppedRunIds = { locallyStoppedRunIds.toSet() },
        currentStreamingMessageId = { streamingMessageId },
        isTrackedPendingAssistantMessageId = ::isTrackedPendingAssistantMessageId,
        clearStreamingPointersIfResolved = ::clearStreamingPointersIfResolved,
        orderedMessages = ::orderedMessages,
        persistSelectedSession = ::persistSelectedSession,
        currentScopeGeneration = { timelineScopeGeneration },
        currentMutationRevision = { timelineMutationRevision },
        noteCanonicalMutation = ::noteCanonicalTimelineMutation,
        persistTimelineState = { nextTimelineState, messages ->
            persistCurrentTimelineSnapshot(nextTimelineState, messages)
        },
        reconcileOutbox = ::reconcileTimelineOutbox,
        canAcceptSnapshotVersion = { incoming ->
            shouldAcceptTimelineSnapshotVersion(
                current = TimelineSnapshotVersion(timelineSnapshotRevision, timelineHighWatermark),
                incoming = incoming
            )
        },
        recordSnapshotVersion = { incoming ->
            timelineSnapshotRevision = incoming.revision ?: timelineSnapshotRevision
            timelineHighWatermark = maxOf(
                timelineHighWatermark ?: Long.MIN_VALUE,
                incoming.highWatermark ?: Long.MIN_VALUE
            ).takeUnless { it == Long.MIN_VALUE }
        },
        awaitHistoryPrepareReady = ::awaitHistoryPrepareReady,
        historyPrepareAttemptHook = { historyPrepareAttemptHookForTest?.invoke() },
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
        wsClient.connectionState
            .onEach { connectionState -> handleWebSocketConnectionState(connectionState) }
            .launchIn(scope)
    }

    internal data class PreparedTimelineRehydration(
        val timelineState: ChatTimelineState,
        val orderedMessages: List<ChatMessage>,
        val hasActiveVisibleRun: Boolean
    )

    suspend fun rehydrateTimelineState(gatewayId: String, sessionKey: String) {
        val expectedScope = activeTimelinePersistenceScope(gatewayId, sessionKey) ?: return
        val expectedGeneration = timelineScopeGeneration
        val expectedMutationRevision = timelineMutationRevision
        // 启动页会在主线程进入这里；SharedPreferences + JSON 反序列化 + 排序必须放到后台，
        // 否则大历史会话会把首屏聊天页卡在 Compose 主线程上。
        val restoredSnapshot = withContext(Dispatchers.IO) {
            TimelinePersistenceMiddleware.restoreSnapshot(expectedScope)
        } ?: return
        val prepared = withContext(Dispatchers.Default) {
            prepareTimelineRehydration(restoredSnapshot.restoredTimelineState(), expectedScope.sessionKey)
        }
        // 磁盘恢复可能晚于网关/会话切换、WebSocket 事件、本地发送或历史提交；
        // 迟到的旧磁盘快照绝不能覆盖更新的内存时间线。
        if (!isCurrentTimelineScope(expectedScope) ||
            timelineScopeGeneration != expectedGeneration ||
            timelineMutationRevision != expectedMutationRevision
        ) {
            return
        }
        timelineOutbox.clear()
        restoredSnapshot.outbox.forEach { restoredEntry ->
            val entry = restoredEntry.copy(clientMessageId = restoredEntry.clientMessageId.trim())
            timelineOutbox[entry.idempotencyKey] = entry
        }
        timelineSnapshotRevision = restoredSnapshot.snapshotRevision
        timelineHighWatermark = restoredSnapshot.highWatermark
        applyPreparedTimelineRehydration(prepared)
    }

    internal fun prepareTimelineRehydration(
        restored: ChatTimelineState,
        sessionKey: String
    ): PreparedTimelineRehydration {
        val ordered = sortTimelineMessagesV3(
            removeResolvedTransientAssistantPlaceholders(restored.messages),
            sessionKey
        )
        val restoredTimelineState = restored.copy(messages = ordered)
        return PreparedTimelineRehydration(
            timelineState = restoredTimelineState,
            orderedMessages = ordered,
            hasActiveVisibleRun = hasActiveVisibleTimelineRun(restoredTimelineState, ordered)
        )
    }

    internal fun applyPreparedTimelineRehydration(prepared: PreparedTimelineRehydration) {
        val restoredMessages = orderedMessages(prepared.orderedMessages)
        val hasActiveVisibleRun = hasActiveVisibleTimelineRun(prepared.timelineState, restoredMessages)
        timelineState = prepared.timelineState.copy(messages = restoredMessages)
        _state.value = _state.value.copy(
            messages = restoredMessages,
            isStreaming = hasActiveVisibleRun,
            isStoppingRun = if (hasActiveVisibleRun) _state.value.isStoppingRun else false
        )
        val currentGatewayId = _state.value.currentGatewayId?.trim().orEmpty()
        val currentSessionKey = normalizeSessionKey(_state.value.currentSessionKey)
        restoredMessages
            .filter { message -> message.role == MessageRole.assistant && message.state == MessageState.streaming }
            .forEach { message ->
                val restoredRunId = message.runId.trim().takeIf { it.isNotEmpty() } ?: message.id
                val restoredRunScope = ChatRunScope(
                    gatewayId = currentGatewayId,
                    sessionKey = currentSessionKey,
                    assistantMessageId = message.id,
                    triggeringUserMessageId = triggeringUserMessageIdBefore(message, restoredMessages)
                )
                rememberRunScope(restoredRunId, restoredRunScope)
                timelineOutbox[restoredRunId]?.let { outbox -> rememberRunScope(outbox.requestId, restoredRunScope) }
                streamingMessageId = message.id
                streamingContent.clear()
                streamingContent.append(message.content)
            }
        noteCanonicalTimelineMutation()
        scheduleQueuedTimelineOutboxDrain()
    }

    internal fun activeTimelinePersistenceScope(
        gatewayId: String? = _state.value.currentGatewayId,
        sessionKey: String = _state.value.currentSessionKey
    ): TimelinePersistenceScope? {
        val normalizedGatewayId = gatewayId?.trim().orEmpty()
        if (normalizedGatewayId.isBlank() || apiClient.baseUrl.isBlank() || apiClient.accessToken.isBlank()) return null
        return timelinePersistenceScope(
            baseUrl = apiClient.baseUrl,
            accessToken = apiClient.accessToken,
            gatewayId = normalizedGatewayId,
            sessionKey = sessionKey
        )
    }

    internal fun isCurrentTimelineScope(scope: TimelinePersistenceScope): Boolean {
        return activeTimelinePersistenceScope() == scope.normalized()
    }

    internal fun advanceTimelineScopeGeneration() {
        timelineScopeGeneration += 1L
        timelineMutationRevision += 1L
        timelineMutationSignal.value = TimelineMutationSignal(
            generation = timelineScopeGeneration,
            revision = timelineMutationRevision
        )
        timelineSnapshotRevision = null
        timelineHighWatermark = null
        timelineOutbox.clear()
    }

    internal fun noteCanonicalTimelineMutation() {
        timelineMutationRevision += 1L
        timelineMutationSignal.value = TimelineMutationSignal(
            generation = timelineScopeGeneration,
            revision = timelineMutationRevision
        )
    }

    private fun handleWsEvent(event: WsEvent) {
        if (event.type.equals("hello", ignoreCase = true) ||
            event.type.equals("ready", ignoreCase = true) ||
            event.event.equals("hello", ignoreCase = true) ||
            event.event.equals("ready", ignoreCase = true)
        ) {
            requestCanonicalHistoryReconcileAfterHello()
        }
        handleRealtimeWsEvent(event)
    }

    internal fun requestCanonicalHistoryReconcileAfterHello() {
        val current = _state.value
        val gatewayId = current.currentGatewayId?.trim().orEmpty()
        val sessionKey = normalizeSessionKey(current.currentSessionKey)
        if (gatewayId.isBlank() || sessionKey.isBlank()) return
        val request = CanonicalHistoryReconcileRequest(
            gatewayId = gatewayId,
            sessionKey = sessionKey,
            generation = timelineScopeGeneration
        )
        val shouldStart = synchronized(canonicalHistoryReconcileLock) {
            pendingCanonicalHistoryReconcile = request
            if (canonicalHistoryReconcileInFlight) {
                false
            } else {
                canonicalHistoryReconcileInFlight = true
                true
            }
        }
        if (shouldStart) scope.launch { drainCanonicalHistoryReconciles() }
    }

    private suspend fun drainCanonicalHistoryReconciles() {
        try {
            while (true) {
                val request = synchronized(canonicalHistoryReconcileLock) {
                    pendingCanonicalHistoryReconcile?.also {
                        pendingCanonicalHistoryReconcile = null
                    }
                } ?: return
                if (timelineScopeGeneration != request.generation ||
                    !matchesCurrentStoreScope(request.gatewayId, request.sessionKey)
                ) continue
                // Relay 的 hello/ready 是 session 注册完成后的确定性屏障。
                // 只在真实协议事件后读取权威 HTTP 历史，不用 socket open
                // 或定时器猜测注册是否完成，也不猜测连续 seq 区间。
                historyCoordinator.loadHistory(
                    gatewayId = request.gatewayId,
                    sessionKey = request.sessionKey,
                    limit = chatHistoryPageSize,
                    keepSwitchingOverlay = false
                )
                // 权威历史可能已经清除了本地残留的 streaming/active 标记。历史提交完成后
                // 必须重新检查 durable queue；否则入队时恰好遇到旧运行态的消息会永久停在队列里。
                scheduleQueuedTimelineOutboxDrain()
            }
        } finally {
            val shouldRestart = synchronized(canonicalHistoryReconcileLock) {
                canonicalHistoryReconcileInFlight = false
                if (pendingCanonicalHistoryReconcile != null) {
                    canonicalHistoryReconcileInFlight = true
                    true
                } else {
                    false
                }
            }
            if (shouldRestart) scope.launch { drainCanonicalHistoryReconciles() }
        }
    }

    private suspend fun awaitHistoryPrepareReady(
        expectedGeneration: Long,
        conflictedRevision: Long
    ): Boolean {
        val signal = timelineMutationSignal
            .combine(_state) { mutation, state -> mutation to state.isStreaming }
            .first { (mutation, isStreaming) ->
                mutation.generation != expectedGeneration ||
                    (mutation.revision >= conflictedRevision && !isStreaming)
            }
            .first
        return signal.generation == expectedGeneration
    }

    private fun matchesCurrentStoreScope(gatewayId: String, sessionKey: String): Boolean {
        val current = _state.value
        return current.currentGatewayId == gatewayId && sameSessionKey(current.currentSessionKey, sessionKey)
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
        val withQueuedOutbox = restoreQueuedTimelineOutboxMessages(messages, timelineOutbox.values)
        return sortTimelineMessagesV3(removeResolvedTransientAssistantPlaceholders(withQueuedOutbox), sessionKey)
    }

    internal fun orderMessagesForRealtime(messages: List<ChatMessage>): List<ChatMessage> {
        return orderedMessages(messages)
    }

    internal fun currentGatewayType(): GatewayType {
        val gatewayId = _state.value.currentGatewayId?.trim().orEmpty()
        return gatewayTypeFor(gatewayId)
    }

    internal fun completeCurrentRun(runId: String, runScope: ChatRunScope?) {
        cancelChatFinalSync(runScope)
        markTimelineRunResolved(runId, runScope)
        forgetRunScope(runId, runScope)
        scheduleQueuedTimelineOutboxDrain()
    }

    internal fun scheduleQueuedTimelineOutboxDrain() {
        scope.launch {
            kotlinx.coroutines.yield()
            drainQueuedTimelineOutbox()
        }
    }

    internal fun handleWebSocketConnectionState(connectionState: WsConnectionState) {
        if (connectionState != WsConnectionState.connected) return
        // 离线期间 queued entry 始终保留持久队列身份；只有真正重连成功后才激活队首。
        // 锁会合并历史恢复、终态事件和连接事件的并发触发，确保一次只发送一条。
        drainQueuedTimelineOutbox(connectionState)
        schedulePendingFinalSyncsForCurrentSession()
        scheduleIncrementalTimelineSyncForCurrentScope()
    }

    /**
     * Restored chat scopes do not need to wait for a socket callback before
     * reconciling. The same in-flight key also coalesces a near-simultaneous
     * WebSocket connected event, so foreground restore cannot race a full
     * history snapshot against durable cursor replay.
     */
    fun reconcileTimelineAfterLocalRestore(
        gatewayId: String,
        sessionKey: String,
        keepSwitchingOverlay: Boolean = true
    ) {
        if (!matchesCurrentStoreScope(gatewayId, sessionKey)) return
        scheduleIncrementalTimelineSync(gatewayId, sessionKey, keepSwitchingOverlay)
    }

    private fun scheduleIncrementalTimelineSyncForCurrentScope() {
        val current = _state.value
        val gatewayId = current.currentGatewayId?.trim().orEmpty()
        val sessionKey = normalizeSessionKey(current.currentSessionKey)
        scheduleIncrementalTimelineSync(gatewayId, sessionKey, keepSwitchingOverlay = false)
    }

    private fun scheduleIncrementalTimelineSync(
        gatewayId: String,
        sessionKey: String,
        keepSwitchingOverlay: Boolean
    ) {
        if (gatewayId.isBlank()) return
        val scopeKey = "$gatewayId\u0000$sessionKey"
        synchronized(incrementalSyncScopesInFlight) {
            if (!incrementalSyncScopesInFlight.add(scopeKey)) return
        }
        scope.launch {
            try {
                reconcileTimelineAfterReconnect(gatewayId, sessionKey, keepSwitchingOverlay)
            } finally {
                synchronized(incrementalSyncScopesInFlight) {
                    incrementalSyncScopesInFlight.remove(scopeKey)
                }
            }
        }
    }

    private suspend fun reconcileTimelineAfterReconnect(
        gatewayId: String,
        sessionKey: String,
        keepSwitchingOverlay: Boolean
    ) {
        val savedCursor = chatTimelineSyncCursorLoader?.invoke(gatewayId, sessionKey)
            ?: sessionSelectionStore?.loadSyncCursor(gatewayId, sessionKey)
        if (!savedCursor.isNullOrBlank() && replayTimelineSyncChanges(gatewayId, sessionKey, savedCursor)) {
            // A successful cursor replay is just as terminal as a canonical history load.
            // Session selection sets this flag before local rehydration, so returning here
            // without closing it leaves an already-restored conversation permanently covered.
            if (matchesCurrentStoreScope(gatewayId, sessionKey)) {
                releaseSessionSwitchOverlay()
            }
            return
        }

        // Capture a journal boundary before reading the snapshot. Events written
        // while history is in flight are replayed from this exact checkpoint.
        val bootstrapCheckpoint = fetchTimelineSyncCheckpoint(gatewayId, sessionKey)
        loadHistory(gatewayId, sessionKey, keepSwitchingOverlay = keepSwitchingOverlay)
        if (bootstrapCheckpoint != null &&
            matchesCurrentStoreScope(gatewayId, sessionKey) &&
            _state.value.errorMessage == null
        ) {
            replayTimelineSyncChanges(gatewayId, sessionKey, bootstrapCheckpoint)
        }
    }

    /**
     * Reads but does not persist the bootstrap boundary. It becomes durable only after
     * canonical history succeeds and replay advances from it.
     */
    private suspend fun fetchTimelineSyncCheckpoint(gatewayId: String, sessionKey: String): String? {
        // Keep history-only unit test doubles isolated from the real network client.
        if (chatTimelineSyncPageFetcher == null && chatHistoryPageFetcher != null) return null
        val response = try {
            chatTimelineSyncPageFetcher?.invoke(gatewayId, sessionKey, null)
                ?: apiClient.fetchChatSync(gatewayId, sessionKey, null)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return null
        }
        if (!matchesCurrentStoreScope(gatewayId, sessionKey) || response.events.isNotEmpty() || response.hasMore) {
            return null
        }
        return response.latestCursor?.trim()?.takeIf { it.isNotEmpty() }
            ?: response.nextCursor?.trim()?.takeIf { it.isNotEmpty() }
    }

    private suspend fun replayTimelineSyncChanges(
        gatewayId: String,
        sessionKey: String,
        initialCursor: String?
    ): Boolean {
        var cursor = initialCursor?.trim()?.takeIf { it.isNotEmpty() }
        repeat(20) {
            val response = try {
                chatTimelineSyncPageFetcher?.invoke(gatewayId, sessionKey, cursor)
                    ?: apiClient.fetchChatSync(gatewayId, sessionKey, cursor)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Includes 409 cursor_expired and an older Relay's 404. The
                // caller deliberately falls back to canonical chat.history.
                return false
            }
            if (!matchesCurrentStoreScope(gatewayId, sessionKey)) return false
            val events = response.events.flatMap(TimelineEventLog::decodePayload)
            if (events.isNotEmpty()) applyTimelineEvents(events)
            val nextCursor = response.nextCursor?.trim()?.takeIf { it.isNotEmpty() }
                ?: response.latestCursor?.trim()?.takeIf { it.isNotEmpty() }
                ?: cursor
            if (!nextCursor.isNullOrBlank()) {
                sessionSelectionStore?.saveSyncCursor(gatewayId, sessionKey, nextCursor)
            }
            if (!response.hasMore) return !nextCursor.isNullOrBlank()
            if (nextCursor.isNullOrBlank() || nextCursor == cursor) return false
            cursor = nextCursor
        }
        return false
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
        noteCanonicalTimelineMutation()
        persistCurrentTimelineSnapshot(timelineState, ordered)
    }

    internal fun scheduleChatFinalSync(runId: String, runScope: ChatRunScope?, attempt: Int = 0) {
        val assistantMessageId = runScope?.assistantMessageId?.trim()?.takeIf { it.isNotEmpty() } ?: return
        if (chatFinalSyncJobs[assistantMessageId]?.isActive == true) return
        val gatewayId = runScope.gatewayId.trim()
        val sessionKey = normalizeSessionKey(runScope.sessionKey)
        if (gatewayId.isBlank() || sessionKey.isBlank()) return
        val gatewayType = gatewayTypeFor(gatewayId)
        val connectionState = wsClient.connectionState.value

        val job = scope.launch {
            delay(chatFinalSyncDelayMs(attempt, gatewayType, connectionState))
            chatFinalSyncJobs.remove(assistantMessageId)
            if (!needsChatFinalSync(runId, runScope)) return@launch

            logDebug("Silent chat final sync attempt=${attempt + 1}")
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
        return chatFinalSyncDelayMs(
            attempt = attempt,
            gatewayType = GatewayType.openclaw,
            connectionState = WsConnectionState.connected
        )
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
        timelineOutbox.clear()
        noteCanonicalTimelineMutation()
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
        sourceRunId: String? = null,
        messageSortBaseTimestamp: Double
    ) {
        val messages = ComposerAttachmentMessageUpdater.begin(
            currentMessages = _state.value.messages,
            attachments = attachments,
            gatewayId = gatewayId,
            sessionKey = sessionKey,
            senderDisplayName = senderDisplayName,
            sourceRunId = sourceRunId,
            messageSortBaseTimestamp = messageSortBaseTimestamp,
            orderMessages = ::orderedMessages
        )
        _state.value = _state.value.copy(messages = messages)
        syncTimelineMessagesSnapshot(messages)
    }

    fun updateComposerAttachmentUploadMessage(
        attachment: ComposerAttachmentDraft,
        gatewayId: String,
        sessionKey: String,
        progress: Double,
        phase: AttachmentUploadPhase,
        failureMessage: String? = null,
        senderDisplayName: String? = null,
        sourceRunId: String? = null
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
            sourceRunId = sourceRunId,
            orderMessages = ::orderedMessages
        ) ?: return
        _state.value = _state.value.copy(messages = messages)
        syncTimelineMessagesSnapshot(messages)
    }

    @Suppress("ReturnCount")
    fun completeComposerAttachmentUploadMessage(
        attachment: ComposerAttachmentDraft,
        record: RelayFileTransferItem,
        gatewayId: String,
        sessionKey: String,
        sourceRunId: String? = null,
        completionSortTimestamp: Double
    ): Boolean {
        val result = ComposerAttachmentMessageUpdater.complete(
            currentMessages = _state.value.messages,
            attachment = attachment,
            record = record,
            sourceRunId = sourceRunId,
            completionSortTimestamp = completionSortTimestamp,
            orderMessages = ::orderedMessages
        )
        if (!result.completed) return false
        _state.value = _state.value.copy(messages = result.messages)
        syncTimelineMessagesSnapshot(result.messages)
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
            val ordered = orderedMessages(deduped)
            _state.value = _state.value.copy(messages = ordered)
            syncTimelineMessagesSnapshot(ordered)
        }
    }

    internal fun syncTimelineMessagesSnapshot(messages: List<ChatMessage>) {
        val snapshotMessages = canonicalizeMessagesForTimelineSnapshot(messages)
        timelineState = timelineState.copy(messages = snapshotMessages)
        noteCanonicalTimelineMutation()
        persistCurrentTimelineSnapshot(timelineState, snapshotMessages)
    }

    fun sendMessage(
        content: String,
        gatewayId: String,
        attachmentIds: List<String> = emptyList(),
        attachmentBlocks: List<RelayChatContentBlock> = emptyList(),
        commandAttachments: List<RelayChatSendAttachmentPayload> = emptyList(),
        clientRunId: String? = null
    ) {
        sendTextOutgoingRun(
            content = content,
            gatewayId = gatewayId,
            attachmentIds = attachmentIds,
            attachmentBlocks = attachmentBlocks,
            commandAttachments = commandAttachments,
            clientRunId = clientRunId
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
        sourceRunId: String? = null,
        idempotencyKey: String,
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
            sourceRunId = sourceRunId,
            idempotencyKey = idempotencyKey,
            onProgress = onProgress
        )
    }

    fun sendCommand(gatewayId: String, command: String) {
        sendSlashCommand(gatewayId, command)
    }

    fun resetSession(gatewayId: String, sessionKey: String = _state.value.currentSessionKey) {
        val normalizedGatewayId = gatewayId.trim()
        if (normalizedGatewayId.isBlank()) return
        val normalizedSessionKey = normalizeSessionKey(sessionKey)
        persistSelectedSession(normalizedGatewayId, normalizedSessionKey)
        wsClient.resetSession(
            gatewayId = normalizedGatewayId,
            sessionKey = normalizedSessionKey
        )
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
        // 手动刷新、会话进入与启动恢复都经过这里。历史状态收敛为空闲后立即唤醒队首，
        // 不依赖下一次 WebSocket 重连或下一条终态事件碰巧到来。
        scheduleQueuedTimelineOutboxDrain()
    }

    fun releaseSessionSwitchOverlay() {
        val current = _state.value
        if (!current.isSwitchingSession) return
        // 已有同 scope 历史同步继续负责消息合并；这里只撤掉切换遮罩，避免重复触发时阻塞 UI。
        _state.value = current.copy(isSwitchingSession = false)
    }

    suspend fun loadOlderHistory(gatewayId: String, sessionKey: String) {
        historyCoordinator.loadOlderHistory(gatewayId, sessionKey)
    }

    fun connectWebSocket() {
        val url = apiClient.baseUrl
        val token = apiClient.accessToken
        if (url.isNotBlank() && token.isNotBlank()) {
            wsClient.connect(url, token)
            replayPendingTimelineOutbox()
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

    internal companion object {
        const val maxChatRunScopes = 256
        const val chatFinalSyncInitialDelayMs = 2_500L
        const val chatFinalSyncFastRetryDelayMs = 4_000L
        const val chatFinalSyncSlowRetryDelayMs = 8_000L
        const val hermesRealtimeFinalSyncInitialDelayMs = 30_000L
        const val hermesRealtimeFinalSyncRetryDelayMs = 30_000L
        const val hermesRealtimeFinalSyncSlowRetryDelayMs = 60_000L
        const val chatFinalSyncMaxAttempts = 60
        const val chatHistoryPageSize = 50
        const val chatHistoryWindowMaxMessages = 500
        const val chatHistoryPendingResolveMaxPages = 5
        const val localUserEchoMergeWindowSeconds = 600.0
    }
}

internal fun chatFinalSyncDelayMs(
    attempt: Int,
    gatewayType: GatewayType,
    connectionState: WsConnectionState
): Long {
    // Hermes 在 Android 上的 history fallback 会触发 host 侧同步 sessions export。
    // 当移动端 WebSocket 已连通时，过早补拉会反向堵住 Hermes 实时回包，因此这里只把它当成慢兜底。
    if (gatewayType == GatewayType.hermes && connectionState == WsConnectionState.connected) {
        return when {
            attempt <= 0 -> ChatStore.hermesRealtimeFinalSyncInitialDelayMs
            attempt < 6 -> ChatStore.hermesRealtimeFinalSyncRetryDelayMs
            else -> ChatStore.hermesRealtimeFinalSyncSlowRetryDelayMs
        }
    }
    return when {
        attempt <= 0 -> ChatStore.chatFinalSyncInitialDelayMs
        attempt < 6 -> ChatStore.chatFinalSyncFastRetryDelayMs
        else -> ChatStore.chatFinalSyncSlowRetryDelayMs
    }
}
