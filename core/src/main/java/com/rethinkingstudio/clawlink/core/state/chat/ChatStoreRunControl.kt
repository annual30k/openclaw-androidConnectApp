package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
import java.util.UUID

internal fun ChatStore.abortActiveRun() {
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

    val activeRunId = timelineState.activeRunId
        ?: timelineState.activeTurnByRunId.keys.lastOrNull()
        ?: _state.value.messages.lastOrNull { it.state == MessageState.streaming }?.runId

    val requestId = UUID.randomUUID().toString()
    abortRequestIds.add(requestId)
    _state.value = _state.value.copy(isStoppingRun = true)

    android.util.Log.d("ChatStore", "Stopping run: $activeRunId for gateway: $gatewayId, session: $sessionKey")

    wsClient.abortChatRun(gatewayId, sessionKey, activeRunId, requestId)
    completeCurrentStreamingMessageLocally(activeRunId)
}

internal fun ChatStore.shouldIgnoreLocallyStoppedRunEvent(runId: String): Boolean {
    pruneLocallyStoppedRunIds()
    val normalizedRunId = runId.trim()
    if (normalizedRunId.isNotEmpty() && locallyStoppedRunIds.contains(normalizedRunId)) {
        return true
    }
    return normalizedRunId.isEmpty()
        && streamingMessageId == null
        && System.currentTimeMillis() < ignoreRunlessStoppedEventsUntilMs
}

internal fun ChatStore.pruneLocallyStoppedRunIds() {
    if (System.currentTimeMillis() >= ignoreRunlessStoppedEventsUntilMs) {
        ignoreRunlessStoppedEventsUntilMs = 0
    }
    if (locallyStoppedRunIds.size > maxLocallyStoppedRunIds) {
        locallyStoppedRunIds.clear()
    }
}

private fun ChatStore.completeCurrentStreamingMessageLocally(runId: String?) {
    val result = completeStreamingMessageLocallyAfterStop(_state.value.messages, runId)
    if (!result.stoppedRunId.isNullOrBlank()) {
        locallyStoppedRunIds.add(result.stoppedRunId)
    }

    ignoreRunlessStoppedEventsUntilMs = System.currentTimeMillis() + stoppedRunlessEventIgnoreWindowMs
    val runScope = runId?.let { chatRunScopes[it] }
    streamingMessageId = null
    streamingContent.clear()
    _state.value = _state.value.copy(
        messages = orderedMessages(result.messages),
        isStreaming = false,
        isStoppingRun = false
    )
    completeCurrentRun(runId.orEmpty(), runScope)
}

private const val stoppedRunlessEventIgnoreWindowMs = 15_000L
private const val maxLocallyStoppedRunIds = 64
