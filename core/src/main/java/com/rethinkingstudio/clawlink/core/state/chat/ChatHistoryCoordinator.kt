package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.network.RelayAPIClient
import com.rethinkingstudio.clawlink.core.network.dto.ChatHistoryResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

internal class ChatHistoryCoordinator(
    private val apiClient: RelayAPIClient,
    private val chatHistoryPageFetcher: ChatHistoryPageFetcher?,
    private val getState: () -> ChatState,
    private val setState: (ChatState) -> Unit,
    private val getTimelineState: () -> ChatTimelineState,
    private val setTimelineState: (ChatTimelineState) -> Unit,
    private val v3Sessions: MutableSet<String>,
    private val currentStreamingMessageId: () -> String?,
    private val isTrackedPendingAssistantMessageId: (String) -> Boolean,
    private val clearStreamingPointersIfResolved: (List<ChatMessage>) -> Unit,
    private val orderedMessages: (List<ChatMessage>) -> List<ChatMessage>,
    private val persistSelectedSession: (String, String) -> Unit,
    private val currentScopeGeneration: () -> Long,
    private val currentMutationRevision: () -> Long,
    private val noteCanonicalMutation: () -> Unit,
    private val persistTimelineState: (ChatTimelineState, List<ChatMessage>) -> Unit,
    private val reconcileOutbox: (List<ChatMessage>) -> Unit,
    private val canAcceptSnapshotVersion: (TimelineSnapshotVersion) -> Boolean,
    private val recordSnapshotVersion: (TimelineSnapshotVersion) -> Unit,
    private val awaitHistoryPrepareReady: suspend (Long, Long) -> Boolean,
    private val historyPrepareAttemptHook: (() -> Unit)?,
    private val needsChatFinalSync: (String, ChatRunScope) -> Boolean,
    private val pageSize: Int,
    private val windowMaxMessages: Int,
    private val pendingResolveMaxPages: Int
) {
    private val requestSequence = AtomicLong(0L)
    private val latestNewestRequestByScope = mutableMapOf<String, Long>()
    private val latestOlderRequestByScope = mutableMapOf<String, Long>()

    private enum class HistoryWindowMode {
        NEWEST,
        OLDER
    }

    private data class PreparedHistoryMerge(
        val snapshotReduction: ChatHistorySnapshotReduction?,
        val orderedMessages: List<ChatMessage>,
        val hasActiveStreaming: Boolean
    )

    suspend fun loadHistory(
        gatewayId: String,
        sessionKey: String,
        limit: Int,
        keepSwitchingOverlay: Boolean
    ) {
        val normalizedGatewayId = gatewayId.trim()
        val normalizedSessionKey = normalizeSessionKey(sessionKey)
        if (normalizedGatewayId.isBlank()) {
            setState(getState().copy(isLoading = false, isSwitchingSession = false))
            return
        }
        val initialState = getState()
        val hasActiveScope = !initialState.currentGatewayId.isNullOrBlank()
        if (hasActiveScope && !matchesRequestedChatScope(initialState, normalizedGatewayId, normalizedSessionKey)) {
            return
        }
        val requestGeneration = currentScopeGeneration()
        val requestId = requestSequence.incrementAndGet()
        val requestScopeKey = "$normalizedGatewayId\u0000$normalizedSessionKey"
        synchronized(latestNewestRequestByScope) {
            latestNewestRequestByScope[requestScopeKey] = requestId
        }
        setState(
            initialState.copy(
                currentGatewayId = normalizedGatewayId,
                currentSessionKey = normalizedSessionKey,
                isLoading = true,
                isSwitchingSession = initialState.isSwitchingSession && keepSwitchingOverlay,
                errorMessage = null,
                historyWindow = ChatHistoryWindowState()
            )
        )
        persistSelectedSession(normalizedGatewayId, normalizedSessionKey)
        try {
            val response = retryOnceOnTransientFailure(
                operationName = "chat history for $normalizedGatewayId/$normalizedSessionKey"
            ) {
                fetchChatHistoryPage(normalizedGatewayId, normalizedSessionKey, limit)
            }
            if (!canCommitNewestRequest(
                    gatewayId = normalizedGatewayId,
                    sessionKey = normalizedSessionKey,
                    generation = requestGeneration,
                    requestScopeKey = requestScopeKey,
                    requestId = requestId
                )
            ) return
            val snapshotPage = response.timelineSnapshot?.let(TimelineSnapshotPage::fromJsonElement)
            if (snapshotPage != null && !sameSessionKey(snapshotPage.sessionKey, normalizedSessionKey)) return
            val incomingSnapshotVersion = timelineSnapshotVersion(snapshotPage)
            if (!canAcceptSnapshotVersion(incomingSnapshotVersion)) return
            while (canCommitNewestRequest(
                    gatewayId = normalizedGatewayId,
                    sessionKey = normalizedSessionKey,
                    generation = requestGeneration,
                    requestScopeKey = requestScopeKey,
                    requestId = requestId
                )
            ) {
                val (prepared, preparedMutationRevision) = prepareStableHistoryMerge(
                    response = response,
                    windowMode = HistoryWindowMode.NEWEST,
                    expectedGeneration = requestGeneration
                ) ?: return
                val current = getState()
                val requestStillCurrent = canCommitNewestRequest(
                    gatewayId = normalizedGatewayId,
                    sessionKey = normalizedSessionKey,
                    generation = requestGeneration,
                    requestScopeKey = requestScopeKey,
                    requestId = requestId
                )
                if (!requestStillCurrent) return
                if (currentMutationRevision() != preparedMutationRevision) {
                    if (!awaitHistoryPrepareReady(requestGeneration, currentMutationRevision())) return
                    continue
                }
                // reducer 接触 timelineState 前必须完成 scope/generation/request/revision 四重校验，
                // 防止迟到响应污染下一个会话的 reducer 初始状态。
                applySnapshotReduction(prepared.snapshotReduction)
                val committedMessages = orderedMessages(prepared.orderedMessages)
                val hasActiveStreaming = hasActiveStreamingMessage(committedMessages)
                setState(
                    current.copy(
                        messages = committedMessages,
                        isLoading = false,
                        isSwitchingSession = false,
                        isStreaming = hasActiveStreaming,
                        isStoppingRun = if (hasActiveStreaming) current.isStoppingRun else false,
                        errorMessage = null,
                        historyWindow = current.historyWindow.copy(
                            isLoadingOlder = false,
                            isCatchingUp = false,
                            hasOlder = response.hasMore,
                            olderCursor = response.nextCursor,
                            newestCursor = response.newestCursor,
                            loadedMessageCount = committedMessages.size
                        )
                    )
                )
                clearStreamingPointersIfResolved(committedMessages)
                val updatedTimelineState = getTimelineState().copy(messages = committedMessages)
                setTimelineState(updatedTimelineState)
                recordSnapshotVersion(incomingSnapshotVersion)
                reconcileOutbox(committedMessages)
                noteCanonicalMutation()
                persistTimelineState(updatedTimelineState, committedMessages)
                break
            }
        } catch (e: CancellationException) {
            val currentState = getState()
            if (canCommitNewestRequest(
                    gatewayId = normalizedGatewayId,
                    sessionKey = normalizedSessionKey,
                    generation = requestGeneration,
                    requestScopeKey = requestScopeKey,
                    requestId = requestId
                )
            ) {
                setState(
                    currentState.copy(
                        isLoading = false,
                        isSwitchingSession = false,
                        historyWindow = currentState.historyWindow.copy(isLoadingOlder = false, isCatchingUp = false)
                    )
                )
            }
            throw e
        } catch (e: Exception) {
            val currentState = getState()
            if (!canCommitNewestRequest(
                    gatewayId = normalizedGatewayId,
                    sessionKey = normalizedSessionKey,
                    generation = requestGeneration,
                    requestScopeKey = requestScopeKey,
                    requestId = requestId
                )
            ) {
                return
            }
            val shouldSuppressError = isTransientLoadFailure(e)
            if (shouldSuppressError) {
                android.util.Log.w("ChatStore", "Transient timeout while refreshing chat history for $normalizedGatewayId/$normalizedSessionKey", e)
            }
            setState(
                currentState.copy(
                    isLoading = false,
                    isSwitchingSession = false,
                    historyWindow = currentState.historyWindow.copy(isLoadingOlder = false, isCatchingUp = false),
                    errorMessage = visibleGatewayLoadErrorMessage(
                        isTransientLoadFailure = shouldSuppressError,
                        rawMessage = e.message
                    )
                )
            )
        } finally {
            // revision 过期、prepare 冲突或 scope 校验失败等提前返回路径，
            // 都必须关闭由当前请求持有的 loading 状态。
            if (canCommitNewestRequest(
                    gatewayId = normalizedGatewayId,
                    sessionKey = normalizedSessionKey,
                    generation = requestGeneration,
                    requestScopeKey = requestScopeKey,
                    requestId = requestId
                )
            ) {
                val currentState = getState()
                if (currentState.isLoading || currentState.isSwitchingSession) {
                    setState(
                        currentState.copy(
                            isLoading = false,
                            isSwitchingSession = false,
                            historyWindow = currentState.historyWindow.copy(
                                isLoadingOlder = false,
                                isCatchingUp = false
                            )
                        )
                    )
                }
            }
        }
    }

    suspend fun loadOlderHistory(gatewayId: String, sessionKey: String) {
        val normalizedGatewayId = gatewayId.trim()
        val requestedSessionKey = sessionKey.trim()
        val normalizedSessionKey = normalizeSessionKey(requestedSessionKey)
        val current = getState()
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
        val requestGeneration = currentScopeGeneration()
        val requestId = requestSequence.incrementAndGet()
        val requestScopeKey = "$normalizedGatewayId\u0000$normalizedSessionKey"
        synchronized(latestOlderRequestByScope) {
            latestOlderRequestByScope[requestScopeKey] = requestId
        }

        setState(current.copy(historyWindow = window.copy(isLoadingOlder = true)))
        try {
            val response = retryOnceOnTransientFailure(
                operationName = "older chat history for $normalizedGatewayId/$normalizedSessionKey"
            ) {
                fetchChatHistoryPage(
                    normalizedGatewayId,
                    normalizedSessionKey,
                    pageSize,
                    cursor,
                    "older"
                )
            }
            if (currentScopeGeneration() != requestGeneration ||
                !matchesRequestedChatScope(getState(), normalizedGatewayId, normalizedSessionKey)
            ) {
                return
            }
            while (currentScopeGeneration() == requestGeneration &&
                matchesRequestedChatScope(getState(), normalizedGatewayId, normalizedSessionKey)
            ) {
                val (prepared, preparedMutationRevision) = prepareStableHistoryMerge(
                    response = response,
                    windowMode = HistoryWindowMode.OLDER,
                    replaceExistingTimelineState = false,
                    expectedGeneration = requestGeneration
                ) ?: return
                val latest = getState()
                if (currentScopeGeneration() != requestGeneration ||
                    !matchesRequestedChatScope(latest, normalizedGatewayId, normalizedSessionKey)
                ) return
                if (currentMutationRevision() != preparedMutationRevision) {
                    if (!awaitHistoryPrepareReady(requestGeneration, currentMutationRevision())) return
                    continue
                }
                applySnapshotReduction(prepared.snapshotReduction)
                val committedMessages = orderedMessages(prepared.orderedMessages)
                val hasActiveStreaming = hasActiveStreamingMessage(committedMessages)
                setState(
                    latest.copy(
                        messages = committedMessages,
                        historyWindow = latest.historyWindow.copy(
                            isLoadingOlder = false,
                            hasOlder = response.hasMore,
                            olderCursor = response.nextCursor,
                            newestCursor = response.newestCursor ?: latest.historyWindow.newestCursor,
                            loadedMessageCount = committedMessages.size
                        ),
                        isStreaming = hasActiveStreaming,
                        isStoppingRun = if (hasActiveStreaming) latest.isStoppingRun else false
                    )
                )
                clearStreamingPointersIfResolved(committedMessages)
                val updatedTimelineState = getTimelineState().copy(messages = committedMessages)
                setTimelineState(updatedTimelineState)
                reconcileOutbox(committedMessages)
                noteCanonicalMutation()
                persistTimelineState(updatedTimelineState, committedMessages)
                break
            }
        } catch (e: CancellationException) {
            closeOlderLoadingIfOwned(normalizedGatewayId, normalizedSessionKey, requestScopeKey, requestId)
            throw e
        } catch (e: Exception) {
            closeOlderLoadingIfOwned(normalizedGatewayId, normalizedSessionKey, requestScopeKey, requestId)
            logWarning("Older chat history load failed for $normalizedGatewayId/$normalizedSessionKey", e)
        } finally {
            // generation 变化会让 prepare/await 提前退出，但 loading owner 仍然
            // 必须释放；只按 request owner + 当前显示 scope 收口，避免旧请求
            // 误清一个已经接管同 scope 的新翻页请求。
            closeOlderLoadingIfOwned(normalizedGatewayId, normalizedSessionKey, requestScopeKey, requestId)
        }
    }

    suspend fun resolvePendingFinalFromHistory(
        gatewayId: String,
        sessionKey: String,
        runId: String,
        runScope: ChatRunScope
    ) {
        val normalizedGatewayId = gatewayId.trim()
        val normalizedSessionKey = normalizeSessionKey(sessionKey)
        if (normalizedGatewayId.isBlank() || normalizedSessionKey.isBlank()) return
        val requestGeneration = currentScopeGeneration()
        var cursor: String? = null
        var pageCount = 0
        try {
            val startingState = getState()
            if (startingState.currentGatewayId == normalizedGatewayId &&
                sameSessionKey(startingState.currentSessionKey, normalizedSessionKey)
            ) {
                setState(startingState.copy(historyWindow = startingState.historyWindow.copy(isCatchingUp = true)))
            }

            while (pageCount < pendingResolveMaxPages && needsChatFinalSync(runId, runScope)) {
                val response = retryOnceOnTransientFailure(
                    operationName = "silent chat history page for $normalizedGatewayId/$normalizedSessionKey"
                ) {
                    fetchChatHistoryPage(
                        normalizedGatewayId,
                        normalizedSessionKey,
                        pageSize,
                        cursor,
                        "older"
                    )
                }
                val current = getState()
                if (currentScopeGeneration() != requestGeneration ||
                    current.currentGatewayId != normalizedGatewayId ||
                    !sameSessionKey(current.currentSessionKey, normalizedSessionKey)
                ) {
                    return
                }
                var preparedAtRevision = prepareStableHistoryMerge(
                    response = response,
                    windowMode = HistoryWindowMode.NEWEST,
                    replaceExistingTimelineState = false,
                    expectedGeneration = requestGeneration
                ) ?: return
                while (currentMutationRevision() != preparedAtRevision.second) {
                    if (!awaitHistoryPrepareReady(requestGeneration, currentMutationRevision())) return
                    if (currentScopeGeneration() != requestGeneration ||
                        !matchesRequestedChatScope(getState(), normalizedGatewayId, normalizedSessionKey)
                    ) return
                    preparedAtRevision = prepareStableHistoryMerge(
                        response = response,
                        windowMode = HistoryWindowMode.NEWEST,
                        replaceExistingTimelineState = false,
                        expectedGeneration = requestGeneration
                    ) ?: return
                }
                val (prepared, _) = preparedAtRevision
                if (currentScopeGeneration() != requestGeneration ||
                    !matchesRequestedChatScope(getState(), normalizedGatewayId, normalizedSessionKey)
                ) return
                val latest = getState()
                applySnapshotReduction(prepared.snapshotReduction)
                val committedMessages = orderedMessages(prepared.orderedMessages)
                val hasActiveStreaming = hasActiveStreamingMessage(committedMessages)
                setState(
                    latest.copy(
                        messages = committedMessages,
                        historyWindow = latest.historyWindow.copy(
                            isCatchingUp = true,
                            hasOlder = response.hasMore,
                            olderCursor = response.nextCursor,
                            newestCursor = response.newestCursor ?: current.historyWindow.newestCursor,
                            loadedMessageCount = committedMessages.size
                        ),
                        isStreaming = hasActiveStreaming,
                        isStoppingRun = if (hasActiveStreaming) latest.isStoppingRun else false
                    )
                )
                clearStreamingPointersIfResolved(committedMessages)
                val updatedTimelineState = getTimelineState().copy(messages = committedMessages)
                setTimelineState(updatedTimelineState)
                reconcileOutbox(committedMessages)
                noteCanonicalMutation()
                persistTimelineState(updatedTimelineState, committedMessages)
                logDebug("Silent chat final sync merged page ${pageCount + 1} with ${response.items.size} history items")
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
            val current = getState()
            if (current.currentGatewayId == normalizedGatewayId &&
                sameSessionKey(current.currentSessionKey, normalizedSessionKey)
            ) {
                setState(current.copy(historyWindow = current.historyWindow.copy(isCatchingUp = false)))
            }
        }
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

    private fun applySnapshotReduction(reduction: ChatHistorySnapshotReduction?) {
        if (reduction == null) return
        setTimelineState(reduction.timelineState)
        v3Sessions.addAll(reduction.v3SessionKeys)
    }

    private fun canCommitNewestRequest(
        gatewayId: String,
        sessionKey: String,
        generation: Long,
        requestScopeKey: String,
        requestId: Long
    ): Boolean {
        val isLatest = synchronized(latestNewestRequestByScope) {
            latestNewestRequestByScope[requestScopeKey] == requestId
        }
        return isLatest &&
            currentScopeGeneration() == generation &&
            matchesRequestedChatScope(getState(), gatewayId, sessionKey)
    }

    private fun closeOlderLoadingIfOwned(
        gatewayId: String,
        sessionKey: String,
        requestScopeKey: String,
        requestId: Long
    ) {
        synchronized(latestOlderRequestByScope) {
            if (latestOlderRequestByScope[requestScopeKey] != requestId) return
            val latest = getState()
            if (matchesRequestedChatScope(latest, gatewayId, sessionKey) &&
                latest.historyWindow.isLoadingOlder
            ) {
                setState(latest.copy(historyWindow = latest.historyWindow.copy(isLoadingOlder = false)))
            }
            latestOlderRequestByScope.remove(requestScopeKey)
        }
    }

    private suspend fun prepareStableHistoryMerge(
        response: ChatHistoryResponse,
        windowMode: HistoryWindowMode,
        replaceExistingTimelineState: Boolean? = null,
        expectedGeneration: Long
    ): Pair<PreparedHistoryMerge, Long>? {
        while (currentScopeGeneration() == expectedGeneration) {
            val revisionBefore = currentMutationRevision()
            val current = getState()
            val prepared = prepareHistoryMerge(
                response = response,
                currentState = current,
                timelineState = getTimelineState(),
                knownV3Sessions = v3Sessions.toSet(),
                activeStreamingMessageId = currentStreamingMessageId(),
                trackedPendingAssistantMessageIds = trackedPendingAssistantMessageIds(current.messages),
                replaceExistingTimelineState = replaceExistingTimelineState
                    ?: shouldReplaceTimelineState(
                        response = response,
                        currentMessages = current.messages
                    ),
                windowMode = windowMode
            )
            historyPrepareAttemptHook?.invoke()
            if (currentMutationRevision() == revisionBefore) {
                return prepared to revisionBefore
            }
            val conflictedRevision = currentMutationRevision()
            // 契约：HTTP response 已经返回后绝不因本地流式 mutation 丢弃，也
            // 不重新请求网络。等待 StateFlow 明确报告 streaming 结束（或 scope
            // generation 变化），再用同一 response 和新的 revision 重新 prepare。
            if (!awaitHistoryPrepareReady(expectedGeneration, conflictedRevision)) return null
        }
        return null
    }

    private suspend fun prepareHistoryMerge(
        response: ChatHistoryResponse,
        currentState: ChatState,
        timelineState: ChatTimelineState,
        knownV3Sessions: Set<String>,
        activeStreamingMessageId: String?,
        trackedPendingAssistantMessageIds: Set<String>,
        replaceExistingTimelineState: Boolean = false,
        windowMode: HistoryWindowMode
    ): PreparedHistoryMerge = withContext(Dispatchers.Default) {
        val snapshotReduction = reduceTimelineHistorySnapshot(
            response = response,
            currentMessages = currentState.messages,
            currentSessionKey = currentState.currentSessionKey,
            timelineState = timelineState,
            replaceExistingTimelineState = replaceExistingTimelineState,
            activeStreamingMessageId = activeStreamingMessageId
        )
        val historyMessages = snapshotReduction?.messages ?: buildHistoryMessagesFromItems(response.items)
        val shouldUseAuthoritativeSnapshot = (replaceExistingTimelineState && response.timelineSnapshot != null) ||
            isCanonicalTimelineV3(response.timelineSnapshot)
        val mergedMessages = if (shouldUseAuthoritativeSnapshot) {
            historyMessages
        } else {
            mergeHistoryWithCurrentMessages(
                historyMessages = historyMessages,
                currentMessages = currentState.messages,
                currentStreamingMessageId = activeStreamingMessageId,
                isTrackedPendingAssistantMessageId = { messageId ->
                    messageId in trackedPendingAssistantMessageIds
                }
            )
        }
        val effectiveV3Sessions = if (snapshotReduction == null) {
            knownV3Sessions
        } else {
            knownV3Sessions + snapshotReduction.v3SessionKeys
        }
        val orderedMessages = when (windowMode) {
            HistoryWindowMode.NEWEST -> trimToNewestHistoryWindow(
                messages = mergedMessages,
                sessionKey = currentState.currentSessionKey,
                knownV3Sessions = effectiveV3Sessions
            )
            HistoryWindowMode.OLDER -> trimToOlderHistoryWindow(
                messages = mergedMessages,
                sessionKey = currentState.currentSessionKey,
                knownV3Sessions = effectiveV3Sessions,
                activeStreamingMessageId = activeStreamingMessageId,
                trackedPendingAssistantMessageIds = trackedPendingAssistantMessageIds
            )
        }
        PreparedHistoryMerge(
            snapshotReduction = snapshotReduction,
            orderedMessages = orderedMessages,
            hasActiveStreaming = hasActiveStreamingMessage(orderedMessages)
        )
    }

    private fun shouldReplaceTimelineState(
        response: ChatHistoryResponse,
        currentMessages: List<ChatMessage>
    ): Boolean {
        // HTTP chat.history snapshots are authoritative for the requested newest window.
        // Only the explicitly active streaming turn is overlaid by the snapshot reducer;
        // completed local cache entries must not survive merely because the server no
        // longer returns them (for example, filtered OpenClaw heartbeat artifacts).
        if (response.timelineSnapshot != null) return true
        val shouldMergeCurrentLocalUsers = hasLocalUserMessagesNeedingHistoryMerge(currentMessages)
        return currentStreamingMessageId() == null && !shouldMergeCurrentLocalUsers
    }

    private fun trimToNewestHistoryWindow(
        messages: List<ChatMessage>,
        sessionKey: String,
        knownV3Sessions: Set<String>
    ): List<ChatMessage> {
        if (knownV3Sessions.any { sameSessionKey(it, sessionKey) }) {
            return sortTimelineMessagesV3(messages, sessionKey).takeLast(windowMaxMessages)
        }
        return newestBoundedHistoryWindowMessages(
            messages = messages,
            maxMessages = windowMaxMessages
        )
    }

    private fun trimToOlderHistoryWindow(
        messages: List<ChatMessage>,
        sessionKey: String,
        knownV3Sessions: Set<String>,
        activeStreamingMessageId: String?,
        trackedPendingAssistantMessageIds: Set<String>
    ): List<ChatMessage> {
        if (knownV3Sessions.any { sameSessionKey(it, sessionKey) }) {
            val ordered = sortTimelineMessagesV3(messages, sessionKey)
            if (ordered.size <= windowMaxMessages) return ordered
            val oldestWindow = ordered.take(windowMaxMessages)
            val oldestWindowIds = oldestWindow.mapTo(mutableSetOf()) { it.id }
            val activeMessagesOutsideOldestWindow = ordered.filter { message ->
                message.id !in oldestWindowIds &&
                    shouldPreserveDuringOlderWindowTrim(
                        message = message,
                        activeStreamingMessageId = activeStreamingMessageId,
                        trackedPendingAssistantMessageIds = trackedPendingAssistantMessageIds
                    )
            }
            if (activeMessagesOutsideOldestWindow.isEmpty()) {
                return oldestWindow
            }
            val retainedOldestCount = (windowMaxMessages - activeMessagesOutsideOldestWindow.size).coerceAtLeast(0)
            val retainedOldestWindow = oldestWindow.take(retainedOldestCount)
            return sortTimelineMessagesV3(retainedOldestWindow + activeMessagesOutsideOldestWindow, sessionKey)
                .take(windowMaxMessages)
        }
        return olderBoundedHistoryWindowMessages(
            messages = messages,
            maxMessages = windowMaxMessages,
            shouldPreserveActiveMessage = { message ->
                shouldPreserveDuringOlderWindowTrim(
                    message = message,
                    activeStreamingMessageId = activeStreamingMessageId,
                    trackedPendingAssistantMessageIds = trackedPendingAssistantMessageIds
                )
            }
        )
    }

    private fun shouldPreserveDuringOlderWindowTrim(
        message: ChatMessage,
        activeStreamingMessageId: String?,
        trackedPendingAssistantMessageIds: Set<String>
    ): Boolean {
        return message.state == MessageState.streaming ||
            activeStreamingMessageId == message.id ||
            message.id in trackedPendingAssistantMessageIds
    }

    private fun trackedPendingAssistantMessageIds(messages: List<ChatMessage>): Set<String> {
        return messages
            .mapNotNull { message ->
                message.id.takeIf { it.isNotBlank() && isTrackedPendingAssistantMessageId(it) }
            }
            .toSet()
    }

    private fun hasActiveStreamingMessage(messages: List<ChatMessage>): Boolean {
        return messages.any { message ->
            message.role == MessageRole.assistant &&
                message.state == MessageState.streaming
        }
    }

    private fun hasLocalUserMessagesNeedingHistoryMerge(messages: List<ChatMessage>): Boolean {
        return messages.any { message ->
            message.role == MessageRole.user &&
                message.runId.startsWith("local-user-")
        }
    }

    private fun matchesRequestedChatScope(state: ChatState, gatewayId: String, sessionKey: String): Boolean {
        return state.currentGatewayId == gatewayId && sameSessionKey(state.currentSessionKey, sessionKey)
    }
}
