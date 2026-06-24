package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import kotlinx.serialization.json.JsonObject

internal fun ChatEventScope.withTrackedTimelineRunScope(
    events: List<TimelineEvent>,
    chatRunScopes: Map<String, ChatRunScope>
): ChatEventScope {
    if (hasSessionKey) return this
    val runScope = events.firstNotNullOfOrNull { event ->
        event.timelineRunId()?.trim()?.takeIf { it.isNotEmpty() }?.let { chatRunScopes[it] }
    } ?: return this
    return ChatEventScope(
        gatewayId = runScope.gatewayId,
        sessionKey = normalizeSessionKey(runScope.sessionKey),
        hasSessionKey = true,
        runScope = runScope
    )
}

internal fun TimelineEvent.timelineRunId(): String? {
    return when (this) {
        is TimelineEvent.MessagePartDelta -> runId
        is TimelineEvent.MessageCompleted -> runId
        is TimelineEvent.RunTerminal -> runId
        is TimelineEvent.ToolInvocationUpdated -> runId
        is TimelineEvent.HistorySnapshotPage -> items.firstNotNullOfOrNull { item ->
            item.runId?.trim()?.takeIf { it.isNotEmpty() }
        }
        is TimelineEvent.TurnUserCreated,
        is TimelineEvent.AttachmentStateChanged -> null
    }
}

internal fun resolvedRunIdFromCommandResponse(response: JsonObject?): String? {
    val payload = response?.get("payload") as? JsonObject
    val result = response?.get("result") as? JsonObject
    return payload?.deepString("runId", "run_id")
        ?: payload?.string("id")
        ?: result?.deepString("runId", "run_id")
        ?: result?.string("id")
        ?: response?.string("runId", "run_id")
}

internal fun resolveChatEventScope(
    envelope: JsonObject,
    payload: JsonObject,
    runId: String,
    currentGatewayId: String?,
    currentSessionKey: String,
    messages: List<ChatMessage>,
    chatRunScopes: Map<String, ChatRunScope>,
    bindRunScope: (String, ChatRunScope) -> Unit
): ChatEventScope {
    val normalizedRunId = runId.trim()
    val explicitGatewayId = envelope.deepString("gatewayId", "gateway_id")
        ?: payload.deepString("gatewayId", "gateway_id")
    val explicitSessionKey = payload.deepString("sessionKey", "session_key")
        ?: envelope.deepString("sessionKey", "session_key")
    val provisionalGatewayId = explicitGatewayId ?: currentGatewayId
    val provisionalSessionKey = normalizeSessionKey(explicitSessionKey ?: currentSessionKey)
    val directRunScope = normalizedRunId.takeIf { it.isNotEmpty() }?.let { chatRunScopes[it] }
    val pendingRunScope = directRunScope ?: singlePendingRunScope(
        chatRunScopes = chatRunScopes,
        gatewayId = provisionalGatewayId,
        sessionKey = provisionalSessionKey,
        currentMessages = messages
    )
    if (directRunScope == null && pendingRunScope != null && normalizedRunId.isNotBlank()) {
        bindRunScope(normalizedRunId, pendingRunScope)
    }
    val gatewayId = explicitGatewayId
        ?: pendingRunScope?.gatewayId
        ?: currentGatewayId
    val sessionKey = explicitSessionKey ?: pendingRunScope?.sessionKey

    return ChatEventScope(
        gatewayId = gatewayId?.trim()?.takeIf { it.isNotEmpty() },
        sessionKey = normalizeSessionKey(sessionKey),
        hasSessionKey = !sessionKey.isNullOrBlank(),
        runScope = pendingRunScope
    )
}

internal fun isCurrentChatScope(scope: ChatEventScope, currentState: ChatState): Boolean {
    if (!scope.hasSessionKey) return false
    val currentGatewayId = currentState.currentGatewayId?.trim().orEmpty()
    val eventGatewayId = scope.gatewayId?.trim().orEmpty()
    val gatewayMatches = eventGatewayId.isBlank() ||
        currentGatewayId.isBlank() ||
        eventGatewayId == currentGatewayId
    return gatewayMatches && sameSessionKey(currentState.currentSessionKey, scope.sessionKey)
}

internal fun rememberRunScope(
    chatRunScopes: LinkedHashMap<String, ChatRunScope>,
    runId: String,
    scope: ChatRunScope,
    maxScopes: Int
) {
    val normalizedRunId = runId.trim()
    if (normalizedRunId.isBlank()) return
    chatRunScopes[normalizedRunId] = scope.copy(
        gatewayId = scope.gatewayId.trim(),
        sessionKey = normalizeSessionKey(scope.sessionKey)
    )
    while (chatRunScopes.size > maxScopes) {
        val oldestKey = chatRunScopes.keys.firstOrNull() ?: break
        chatRunScopes.remove(oldestKey)
    }
}

internal fun forgetRunScope(
    chatRunScopes: LinkedHashMap<String, ChatRunScope>,
    runId: String,
    runScope: ChatRunScope? = null
) {
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

private fun singlePendingRunScope(
    chatRunScopes: Map<String, ChatRunScope>,
    gatewayId: String?,
    sessionKey: String,
    currentMessages: List<ChatMessage>
): ChatRunScope? {
    val normalizedGatewayId = gatewayId?.trim().orEmpty()
    val normalizedSessionKey = normalizeSessionKey(sessionKey)
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
    // 没有显式 runId 时，只允许唯一的 pending scope 接管事件；多候选时不猜测，避免跨会话串消息。
    return pendingScopes.singleOrNull()
}
