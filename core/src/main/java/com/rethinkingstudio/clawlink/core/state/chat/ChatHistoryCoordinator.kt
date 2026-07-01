package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.network.RelayAPIClient
import com.rethinkingstudio.clawlink.core.network.dto.ChatHistoryResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
            val prepared = prepareHistoryMerge(
                response = response,
                currentState = currentBeforeHistory,
                timelineState = getTimelineState(),
                knownV3Sessions = v3Sessions.toSet(),
                activeStreamingMessageId = currentStreamingMessageId(),
                trackedPendingAssistantMessageIds = trackedPendingAssistantMessageIds(currentBeforeHistory.messages),
                replaceExistingTimelineState = shouldReplaceTimelineState(
                    response = response,
                    currentMessages = currentBeforeHistory.messages
                ),
                windowMode = HistoryWindowMode.NEWEST
            )
            val current = getState()
            applySnapshotReduction(prepared.snapshotReduction)
            if (matchesRequestedChatScope(current, normalizedGatewayId, normalizedSessionKey)) {
                setState(
                    current.copy(
                        messages = prepared.orderedMessages,
                        isLoading = false,
                        isSwitchingSession = false,
                        isStreaming = prepared.hasActiveStreaming,
                        isStoppingRun = if (prepared.hasActiveStreaming) current.isStoppingRun else false,
                        errorMessage = null,
                        historyWindow = current.historyWindow.copy(
                            isLoadingOlder = false,
                            isCatchingUp = false,
                            hasOlder = response.hasMore,
                            olderCursor = response.nextCursor,
                            newestCursor = response.newestCursor,
                            loadedMessageCount = prepared.orderedMessages.size
                        )
                    )
                )
                clearStreamingPointersIfResolved(prepared.orderedMessages)
                val updatedTimelineState = getTimelineState().copy(messages = prepared.orderedMessages)
                setTimelineState(updatedTimelineState)
                persistOrClearTimelineSnapshot(updatedTimelineState, prepared.orderedMessages)
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
            val prepared = prepareHistoryMerge(
                response = response,
                currentState = latest,
                timelineState = getTimelineState(),
                knownV3Sessions = v3Sessions.toSet(),
                activeStreamingMessageId = currentStreamingMessageId(),
                trackedPendingAssistantMessageIds = trackedPendingAssistantMessageIds(latest.messages),
                windowMode = HistoryWindowMode.OLDER
            )
            applySnapshotReduction(prepared.snapshotReduction)
            setState(
                latest.copy(
                    messages = prepared.orderedMessages,
                    historyWindow = latest.historyWindow.copy(
                        isLoadingOlder = false,
                        hasOlder = response.hasMore,
                        olderCursor = response.nextCursor,
                        newestCursor = response.newestCursor ?: latest.historyWindow.newestCursor,
                        loadedMessageCount = prepared.orderedMessages.size
                    ),
                    isStreaming = prepared.hasActiveStreaming,
                    isStoppingRun = if (prepared.hasActiveStreaming) latest.isStoppingRun else false
                )
            )
            clearStreamingPointersIfResolved(prepared.orderedMessages)
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
                val prepared = prepareHistoryMerge(
                    response = response,
                    currentState = current,
                    timelineState = getTimelineState(),
                    knownV3Sessions = v3Sessions.toSet(),
                    activeStreamingMessageId = currentStreamingMessageId(),
                    trackedPendingAssistantMessageIds = trackedPendingAssistantMessageIds(current.messages),
                    windowMode = HistoryWindowMode.NEWEST
                )
                applySnapshotReduction(prepared.snapshotReduction)
                setState(
                    current.copy(
                        messages = prepared.orderedMessages,
                        historyWindow = current.historyWindow.copy(
                            isCatchingUp = true,
                            hasOlder = response.hasMore,
                            olderCursor = response.nextCursor,
                            newestCursor = response.newestCursor ?: current.historyWindow.newestCursor,
                            loadedMessageCount = prepared.orderedMessages.size
                        ),
                        isStreaming = prepared.hasActiveStreaming,
                        isStoppingRun = if (prepared.hasActiveStreaming) current.isStoppingRun else false
                    )
                )
                clearStreamingPointersIfResolved(prepared.orderedMessages)
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
            replaceExistingTimelineState = replaceExistingTimelineState
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
        val isCanonicalTimelineSnapshot = isCanonicalTimelineV3(response.timelineSnapshot)
        val shouldMergeCurrentLocalUsers = hasLocalUserMessagesNeedingHistoryMerge(currentMessages)
        return isCanonicalTimelineSnapshot ||
            (currentStreamingMessageId() == null && !shouldMergeCurrentLocalUsers)
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
