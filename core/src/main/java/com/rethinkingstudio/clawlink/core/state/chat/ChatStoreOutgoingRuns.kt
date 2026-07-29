package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import com.rethinkingstudio.clawlink.core.network.transport.RelayChatSendAttachmentPayload
import com.rethinkingstudio.clawlink.core.network.transport.VoiceSendAudioPayload
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
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
    timelineOutbox[resolvedClientRunId] = TimelineOutboxEntry(
        kind = TimelineOutboxKind.TEXT,
        clientMessageId = resolvedClientRunId,
        idempotencyKey = resolvedClientRunId,
        requestId = requestId,
        content = content,
        attachments = commandAttachments.map { attachment ->
            TimelineOutboxAttachment(
                fileId = attachment.fileId,
                fileName = attachment.fileName,
                mimeType = attachment.mimeType,
                sizeBytes = attachment.sizeBytes,
                sha256 = attachment.sha256,
                sourceRunId = attachment.sourceRunId
            )
        },
        createdAtEpochMs = System.currentTimeMillis()
    )
    noteCanonicalTimelineMutation()
    // WebSocket 命令发出前必须完成持久写；重放复用完全相同的 client/idempotency/request 标识，
    // 由服务端进行确定性去重。
    if (!persistCurrentTimelineSnapshot(
            timelineState,
            _state.value.messages,
            durablePendingOverlay = true
        )
    ) {
        _state.value = _state.value.copy(
            errorMessage = choose(
                "Message was not sent because local recovery storage is unavailable.",
                "本地恢复存储暂不可用，消息尚未发送。"
            )
        )
        return
    }
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
    timelineOutbox[clientRunId] = TimelineOutboxEntry(
        kind = TimelineOutboxKind.VOICE,
        clientMessageId = clientRunId,
        idempotencyKey = clientRunId,
        requestId = requestId,
        voice = TimelineOutboxVoice(
            fileName = audio.fileName,
            mimeType = audio.mimeType,
            sizeBytes = audio.sizeBytes,
            contentBase64 = audio.contentBase64,
            message = message,
            languageHint = languageHint
        ),
        createdAtEpochMs = System.currentTimeMillis()
    )
    noteCanonicalTimelineMutation()
    if (!persistCurrentTimelineSnapshot(
            timelineState,
            _state.value.messages,
            durablePendingOverlay = true
        )
    ) {
        _state.value = _state.value.copy(
            errorMessage = choose(
                "Voice message was not sent because local recovery storage is unavailable.",
                "本地恢复存储暂不可用，语音消息尚未发送。"
            )
        )
        return
    }
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

internal fun ChatStore.replayPendingTimelineOutbox() {
    val persistenceScope = activeTimelinePersistenceScope() ?: return
    if (!isCurrentTimelineScope(persistenceScope)) return
    if (!TimelinePersistenceMiddleware.persistOutbox(
            persistenceScope,
            timelineOutbox.values.toList(),
            timelineState.messages
        )
    ) return
    timelineOutbox.values.toList().forEach { entry ->
        when (entry.kind) {
            TimelineOutboxKind.TEXT -> wsClient.sendChatMessage(
                gatewayId = persistenceScope.gatewayId,
                sessionKey = persistenceScope.sessionKey,
                content = entry.content,
                attachments = entry.attachments.map { attachment ->
                    RelayChatSendAttachmentPayload(
                        fileId = attachment.fileId,
                        fileName = attachment.fileName,
                        mimeType = attachment.mimeType,
                        sizeBytes = attachment.sizeBytes,
                        sha256 = attachment.sha256,
                        sourceRunId = attachment.sourceRunId
                    )
                },
                idempotencyKey = entry.idempotencyKey,
                requestId = entry.requestId
            )
            TimelineOutboxKind.VOICE -> entry.voice?.let { voice ->
                val contentBase64 = TimelinePersistenceMiddleware.resolveVoiceContentBase64(
                    persistenceScope,
                    voice
                ) ?: return@let
                wsClient.sendVoiceMessage(
                    gatewayId = persistenceScope.gatewayId,
                    sessionKey = persistenceScope.sessionKey,
                    audio = VoiceSendAudioPayload(
                        fileName = voice.fileName,
                        mimeType = voice.mimeType,
                        sizeBytes = voice.sizeBytes,
                        contentBase64 = contentBase64
                    ),
                    message = voice.message,
                    languageHint = voice.languageHint,
                    idempotencyKey = entry.idempotencyKey,
                    requestId = entry.requestId
                )
            }
        }
    }
}

internal fun ChatStore.reconcileTimelineOutbox(messages: List<com.rethinkingstudio.clawlink.core.models.chat.ChatMessage>) {
    if (timelineOutbox.isEmpty()) return
    val acknowledgedKeys = timelineOutbox.keys.filter { idempotencyKey ->
        messages.any { message ->
            val isCanonical = !message.timelineOrderKey.startsWith("local:") &&
                !message.timelineIdentityKey.startsWith("local:")
            isCanonical && listOf(
                message.runId,
                message.timelineIdentityKey,
                message.timelineStableKey,
                message.timelineMessageId
            ).any { candidate -> candidate.contains(idempotencyKey) }
        }
    }
    acknowledgedKeys.forEach(timelineOutbox::remove)
}

internal fun ChatStore.sendSlashCommand(gatewayId: String, command: String) {
    // Slash 指令也需要走完整本地 turn，这样用户能看到已发送内容，后续事件也能按稳定 run scope 归位。
    sendTextOutgoingRun(
        content = command,
        gatewayId = gatewayId,
        attachmentIds = emptyList(),
        attachmentBlocks = emptyList(),
        commandAttachments = emptyList()
    )
}
