package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.network.RelayAPIClient
import com.rethinkingstudio.clawlink.core.network.dto.ChatHistoryResponse
import kotlinx.coroutines.CancellationException

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
    private val needsChatFinalSync: (String, ChatRunScope) -> Boolean,
    private val pageSize: Int,
    private val windowMaxMessages: Int,
    private val pendingResolveMaxPages: Int
) {
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
            val currentBeforeHistory = getState()
            val isCanonicalTimelineSnapshot = isCanonicalTimelineV3(response.timelineSnapshot)
            val shouldMergeCurrentLocalUsers = hasLocalUserMessagesNeedingHistoryMerge(currentBeforeHistory.messages)
            val shouldReplaceTimelineState = isCanonicalTimelineSnapshot ||
                (currentStreamingMessageId() == null && !shouldMergeCurrentLocalUsers)
            val snapshotReduction = reduceTimelineHistorySnapshot(
                response = response,
                currentMessages = currentBeforeHistory.messages,
                currentSessionKey = getState().currentSessionKey,
                timelineState = getTimelineState(),
                replaceExistingTimelineState = shouldReplaceTimelineState
            )
            applySnapshotReduction(snapshotReduction)
            val historyMessages = snapshotReduction?.messages ?: buildHistoryMessagesFromItems(response.items)
            val current = getState()
            val shouldUseAuthoritativeSnapshot = (shouldReplaceTimelineState && response.timelineSnapshot != null) ||
                isCanonicalTimelineV3(response.timelineSnapshot)
            val messages = if (shouldUseAuthoritativeSnapshot) {
                historyMessages
            } else if (matchesRequestedChatScope(current, normalizedGatewayId, normalizedSessionKey)) {
                mergeHistoryWithCurrentMessages(
                    historyMessages = historyMessages,
                    currentMessages = current.messages,
                    currentStreamingMessageId = currentStreamingMessageId(),
                    isTrackedPendingAssistantMessageId = isTrackedPendingAssistantMessageId
                )
            } else {
                historyMessages
            }
            val ordered = trimToNewestHistoryWindow(messages)
            if (matchesRequestedChatScope(current, normalizedGatewayId, normalizedSessionKey)) {
                val hasActiveStreaming = hasActiveStreamingMessage(ordered)
                setState(
                    current.copy(
                        messages = ordered,
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
                            loadedMessageCount = ordered.size
                        )
                    )
                )
                clearStreamingPointersIfResolved(ordered)
                if (!hasActiveStreaming) {
                    TimelinePersistenceMiddleware.clearSnapshot()
                }
            }
        } catch (e: CancellationException) {
            val currentState = getState()
            if (matchesRequestedChatScope(currentState, normalizedGatewayId, normalizedSessionKey)) {
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
            if (!matchesRequestedChatScope(currentState, normalizedGatewayId, normalizedSessionKey)) {
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
            val latest = getState()
            if (latest.currentGatewayId != normalizedGatewayId ||
                !sameSessionKey(latest.currentSessionKey, normalizedSessionKey)
            ) {
                return
            }
            val snapshotReduction = reduceTimelineHistorySnapshot(
                response = response,
                currentMessages = latest.messages,
                currentSessionKey = getState().currentSessionKey,
                timelineState = getTimelineState()
            )
            applySnapshotReduction(snapshotReduction)
            val historyMessages = snapshotReduction?.messages
            val messages = if (historyMessages != null && isCanonicalTimelineV3(response.timelineSnapshot)) {
                historyMessages
            } else {
                val baseHistory = historyMessages ?: buildHistoryMessagesFromItems(response.items)
                mergeHistoryWithCurrentMessages(
                    historyMessages = baseHistory,
                    currentMessages = latest.messages,
                    currentStreamingMessageId = currentStreamingMessageId(),
                    isTrackedPendingAssistantMessageId = isTrackedPendingAssistantMessageId
                )
            }
            val ordered = trimToOlderHistoryWindow(messages)
            val hasActiveStreaming = hasActiveStreamingMessage(ordered)
            setState(
                latest.copy(
                    messages = ordered,
                    historyWindow = latest.historyWindow.copy(
                        isLoadingOlder = false,
                        hasOlder = response.hasMore,
                        olderCursor = response.nextCursor,
                        newestCursor = response.newestCursor ?: latest.historyWindow.newestCursor,
                        loadedMessageCount = ordered.size
                    ),
                    isStreaming = hasActiveStreaming,
                    isStoppingRun = if (hasActiveStreaming) latest.isStoppingRun else false
                )
            )
            clearStreamingPointersIfResolved(ordered)
        } catch (e: CancellationException) {
            val latest = getState()
            if (latest.currentGatewayId == normalizedGatewayId &&
                sameSessionKey(latest.currentSessionKey, normalizedSessionKey)
            ) {
                setState(latest.copy(historyWindow = latest.historyWindow.copy(isLoadingOlder = false)))
            }
            throw e
        } catch (e: Exception) {
            val latest = getState()
            if (latest.currentGatewayId == normalizedGatewayId &&
                sameSessionKey(latest.currentSessionKey, normalizedSessionKey)
            ) {
                setState(latest.copy(historyWindow = latest.historyWindow.copy(isLoadingOlder = false)))
            }
            logWarning("Older chat history load failed for $normalizedGatewayId/$normalizedSessionKey", e)
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
                if (current.currentGatewayId != normalizedGatewayId ||
                    !sameSessionKey(current.currentSessionKey, normalizedSessionKey)
                ) {
                    return
                }
                val snapshotReduction = reduceTimelineHistorySnapshot(
                    response = response,
                    currentMessages = current.messages,
                    currentSessionKey = getState().currentSessionKey,
                    timelineState = getTimelineState()
                )
                applySnapshotReduction(snapshotReduction)
                val historyMessages = snapshotReduction?.messages
                val messages = if (historyMessages != null && isCanonicalTimelineV3(response.timelineSnapshot)) {
                    historyMessages
                } else {
                    val baseHistory = historyMessages ?: buildHistoryMessagesFromItems(response.items)
                    mergeHistoryWithCurrentMessages(
                        historyMessages = baseHistory,
                        currentMessages = current.messages,
                        currentStreamingMessageId = currentStreamingMessageId(),
                        isTrackedPendingAssistantMessageId = isTrackedPendingAssistantMessageId
                    )
                }
                val ordered = trimToNewestHistoryWindow(messages)
                val hasActiveStreaming = hasActiveStreamingMessage(ordered)
                setState(
                    current.copy(
                        messages = ordered,
                        historyWindow = current.historyWindow.copy(
                            isCatchingUp = true,
                            hasOlder = response.hasMore,
                            olderCursor = response.nextCursor,
                            newestCursor = response.newestCursor ?: current.historyWindow.newestCursor,
                            loadedMessageCount = ordered.size
                        ),
                        isStreaming = hasActiveStreaming,
                        isStoppingRun = if (hasActiveStreaming) current.isStoppingRun else false
                    )
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

    private fun trimToNewestHistoryWindow(messages: List<ChatMessage>): List<ChatMessage> {
        val sessionKey = getState().currentSessionKey
        if (v3Sessions.any { sameSessionKey(it, sessionKey) }) {
            return sortTimelineMessagesV3(messages, sessionKey).takeLast(windowMaxMessages)
        }
        return newestBoundedHistoryWindowMessages(
            messages = messages,
            maxMessages = windowMaxMessages
        )
    }

    private fun trimToOlderHistoryWindow(messages: List<ChatMessage>): List<ChatMessage> {
        val sessionKey = getState().currentSessionKey
        if (v3Sessions.any { sameSessionKey(it, sessionKey) }) {
            val ordered = sortTimelineMessagesV3(messages, sessionKey)
            if (ordered.size <= windowMaxMessages) return ordered
            val oldestWindow = ordered.take(windowMaxMessages)
            val oldestWindowIds = oldestWindow.mapTo(mutableSetOf()) { it.id }
            val activeMessagesOutsideOldestWindow = ordered.filter { message ->
                message.id !in oldestWindowIds && shouldPreserveDuringOlderWindowTrim(message)
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
            shouldPreserveActiveMessage = ::shouldPreserveDuringOlderWindowTrim
        )
    }

    private fun shouldPreserveDuringOlderWindowTrim(message: ChatMessage): Boolean {
        return message.state == MessageState.streaming ||
            currentStreamingMessageId() == message.id ||
            isTrackedPendingAssistantMessageId(message.id)
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
