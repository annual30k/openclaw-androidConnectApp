package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import com.rethinkingstudio.clawlink.core.network.transport.RelayChatSendAttachmentPayload
import com.rethinkingstudio.clawlink.core.network.transport.VoiceSendAudioPayload
import java.util.UUID

internal fun ChatStore.sendTextOutgoingRun(
    content: String,
    gatewayId: String,
    attachmentIds: List<String>,
    attachmentBlocks: List<RelayChatContentBlock>,
    commandAttachments: List<RelayChatSendAttachmentPayload>,
    clientRunId: String? = null
) {
    val sessionKey = _state.value.currentSessionKey
    if (sessionKey.isBlank()) return

    val resolvedClientRunId = clientRunId?.trim()?.takeIf { it.isNotEmpty() } ?: UUID.randomUUID().toString()
    val requestId = resolvedClientRunId
    val draft = buildLocalTextOutgoingRun(
        currentMessages = _state.value.messages,
        content = content,
        gatewayId = gatewayId,
        sessionKey = sessionKey,
        clientRunId = resolvedClientRunId,
        attachmentIds = attachmentIds,
        attachmentBlocks = attachmentBlocks
    )

    streamingMessageId = draft.runScope.assistantMessageId
    streamingContent.setLength(0)
    streamingContent.append(draft.assistantMessage.content)
    rememberRunScope(resolvedClientRunId, draft.runScope)
    rememberRunScope(requestId, draft.runScope)
    persistSelectedSession(gatewayId, sessionKey)

    _state.value = _state.value.copy(
        messages = orderedMessages(draft.messages),
        isStreaming = true
    )
    timelineState = timelineState.copy(
        messages = _state.value.messages,
        activeRunId = resolvedClientRunId,
        activeRunsByTurnId = timelineState.activeRunsByTurnId + (resolvedClientRunId to resolvedClientRunId),
        activeTurnByRunId = timelineState.activeTurnByRunId + (resolvedClientRunId to resolvedClientRunId)
    )
    TimelinePersistenceMiddleware.persistSnapshot(timelineState)
    scheduleChatFinalSync(resolvedClientRunId, draft.runScope)
    wsClient.sendChatMessage(
        gatewayId = gatewayId,
        sessionKey = sessionKey,
        content = content,
        attachments = commandAttachments,
        idempotencyKey = resolvedClientRunId,
        requestId = requestId
    )
}

internal fun ChatStore.sendVoiceOutgoingRun(
    gatewayId: String,
    audio: VoiceSendAudioPayload,
    message: String?,
    languageHint: String?
) {
    val sessionKey = _state.value.currentSessionKey
    if (sessionKey.isBlank()) return

    val clientRunId = UUID.randomUUID().toString()
    val requestId = clientRunId
    val draft = buildLocalVoiceOutgoingRun(
        currentMessages = _state.value.messages,
        gatewayId = gatewayId,
        sessionKey = sessionKey,
        clientRunId = clientRunId,
        audio = audio
    )

    streamingMessageId = draft.runScope.assistantMessageId
    streamingContent.setLength(0)
    streamingContent.append(draft.assistantMessage.content)
    rememberRunScope(clientRunId, draft.runScope)
    rememberRunScope(requestId, draft.runScope)
    persistSelectedSession(gatewayId, sessionKey)

    _state.value = _state.value.copy(
        messages = orderedMessages(draft.messages),
        isStreaming = true
    )
    timelineState = timelineState.copy(
        messages = _state.value.messages,
        activeRunId = clientRunId,
        activeRunsByTurnId = timelineState.activeRunsByTurnId + (clientRunId to clientRunId),
        activeTurnByRunId = timelineState.activeTurnByRunId + (clientRunId to clientRunId)
    )
    TimelinePersistenceMiddleware.persistSnapshot(timelineState)
    scheduleChatFinalSync(clientRunId, draft.runScope)
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

internal fun ChatStore.sendSlashCommand(gatewayId: String, command: String) {
    val sessionKey = _state.value.currentSessionKey
    if (sessionKey.isBlank()) return

    val requestId = UUID.randomUUID().toString()
    rememberRunScope(requestId, ChatRunScope(gatewayId = gatewayId, sessionKey = sessionKey))
    persistSelectedSession(gatewayId, sessionKey)
    wsClient.sendCommand(gatewayId, sessionKey, command, requestId)
}
