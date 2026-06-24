package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatSessionItem
import kotlinx.coroutines.CancellationException

internal suspend fun ChatStore.loadSessionsForGateway(gatewayId: String): Boolean {
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

internal fun ChatStore.beginGatewaySwitchSelection(gatewayId: String) {
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
    clearActiveRunState(clearStoppedRuns = true)
}

internal fun ChatStore.selectChatSession(sessionKey: String) {
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
    clearActiveRunState(clearStoppedRuns = false)
}

internal fun ChatStore.createChatSession(sessionKey: String? = null): String {
    val key = normalizeSessionKey(
        sessionKey
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "session_${System.currentTimeMillis()}"
    )
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
    clearActiveRunState(clearStoppedRuns = false)
    return key
}

internal fun ChatStore.clearChatMessages() {
    _state.value = _state.value.copy(
        messages = emptyList(),
        isSwitchingSession = false,
        isStoppingRun = false,
        isStreaming = false,
        historyWindow = ChatHistoryWindowState()
    )
    clearActiveRunState(clearStoppedRuns = true)
}

private fun ChatStore.clearActiveRunState(clearStoppedRuns: Boolean) {
    streamingMessageId = null
    streamingContent.clear()
    resetCurrentTimelineScope()
    abortRequestIds.clear()
    chatRunScopes.clear()
    if (clearStoppedRuns) {
        locallyStoppedRunIds.clear()
        ignoreRunlessStoppedEventsUntilMs = 0
    }
}
