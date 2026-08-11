package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import com.rethinkingstudio.clawlink.core.network.transport.RelayChatSendAttachmentPayload
import com.rethinkingstudio.clawlink.core.network.transport.VoiceSendAudioPayload
import com.rethinkingstudio.clawlink.core.network.transport.WsConnectionState
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
import java.util.UUID

internal fun ChatStore.drainQueuedTimelineOutbox(
    connectionState: WsConnectionState = wsClient.connectionState.value
) {
    synchronized(queuedTimelineOutboxDrainLock) {
        drainQueuedTimelineOutboxLocked(connectionState)
    }
}

private fun ChatStore.drainQueuedTimelineOutboxLocked(connectionState: WsConnectionState) {
    // durable queue 只有在传输层已经真正连通时才能激活。离线时提前把 queued 改成 false，
    // 会让队列面板消失并把可靠性退化成 WebSocket 的进程内缓冲；进程重建后只剩用户气泡，
    // 无法保证原请求继续获得回复。
    if (connectionState != WsConnectionState.connected) return
    if (hasActiveReplyForOutgoingQueue()) return
    val gatewayId = _state.value.currentGatewayId?.trim().orEmpty()
    val sessionKey = normalizeSessionKey(_state.value.currentSessionKey)
    if (gatewayId.isBlank() || sessionKey.isBlank()) return
    val entry = orderedQueuedTimelineOutboxEntries().firstOrNull() ?: return
    if (entry.kind != TimelineOutboxKind.TEXT) return
    val clientMessageId = entry.clientMessageId.trim()
    if (clientMessageId.isEmpty()) {
        failUnrestorableQueuedTimelineOutboxEntry(entry)
        drainQueuedTimelineOutboxLocked(connectionState)
        return
    }

    // outbox 是排队身份与顺序的持久化权威；即使旧历史刷新曾吞掉 UI overlay，
    // 激活前也必须先按稳定 clientMessageId 恢复，绝不能删除尚未发送的 durable entry。
    val messages = orderedMessages(_state.value.messages).toMutableList()
    val userIndex = messages.indexOfFirst { message ->
        message.id == "user-$clientMessageId" ||
            message.runId == "local-user-$clientMessageId"
    }
    if (userIndex < 0) {
        failUnrestorableQueuedTimelineOutboxEntry(entry, messages)
        drainQueuedTimelineOutboxLocked(connectionState)
        return
    }

    val userMessage = messages.removeAt(userIndex)
    val activatedAtEpochMs = System.currentTimeMillis()
    val activatedLocalTurnOrder = nextLocalTurnOrder(messages)
    val activatedUserMessage = userMessage.copy(
        deliveryState = "",
        clientMessageText = entry.content,
        queuePosition = null,
        sortTimestamp = activatedAtEpochMs / 1000.0,
        localTurnOrder = activatedLocalTurnOrder
    )
    // 激活时把队列项移动到当前时间线尾部；后续激活的旧队列项不能插回已发送消息之前。
    messages += activatedUserMessage
    val assistantMessageId = "assistant-$clientMessageId"
    val assistantMessage = buildLocalTextAssistantPlaceholderMessage(
        id = assistantMessageId,
        clientRunId = entry.idempotencyKey,
        sortTimestamp = maxOf(
            activatedAtEpochMs / 1000.0,
            (activatedUserMessage.sortTimestamp ?: 0.0) + 0.001
        ),
        localTurnOrder = activatedLocalTurnOrder
    )
    messages.removeAll { message -> message.id == assistantMessageId }
    messages += assistantMessage
    val runScope = ChatRunScope(
        gatewayId = gatewayId,
        sessionKey = sessionKey,
        assistantMessageId = assistantMessageId,
        triggeringUserMessageId = activatedUserMessage.id
    )
    streamingMessageId = assistantMessageId
    streamingContent.setLength(0)
    streamingContent.append(assistantMessage.content)
    rememberRunScope(entry.idempotencyKey, runScope)
    rememberRunScope(entry.requestId, runScope)
    timelineOutbox[entry.idempotencyKey] = entry.copy(
        queued = false,
        queuePosition = null,
        localTurnOrder = activatedLocalTurnOrder
    )
    _state.value = _state.value.copy(
        messages = orderedMessages(messages),
        isStreaming = true,
        isStoppingRun = false
    )
    timelineState = timelineState.copy(
        messages = _state.value.messages,
        activeRunId = entry.idempotencyKey,
        activeRunsByTurnId = timelineState.activeRunsByTurnId + (entry.idempotencyKey to entry.idempotencyKey),
        activeTurnByRunId = timelineState.activeTurnByRunId + (entry.idempotencyKey to entry.idempotencyKey)
    )
    noteCanonicalTimelineMutation()
    if (!persistCurrentTimelineSnapshot(timelineState, _state.value.messages, durablePendingOverlay = true)) {
        _state.value = _state.value.copy(
            errorMessage = choose(
                "Queued message recovery state could not be saved.",
                "排队消息的恢复状态暂时无法保存。"
            )
        )
    }
    scheduleChatFinalSync(entry.idempotencyKey, runScope)
    wsClient.sendChatMessage(
        gatewayId = gatewayId,
        sessionKey = sessionKey,
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
}

private fun ChatStore.failUnrestorableQueuedTimelineOutboxEntry(
    entry: TimelineOutboxEntry,
    currentMessages: List<com.rethinkingstudio.clawlink.core.models.chat.ChatMessage> = _state.value.messages
) {
    val clientMessageId = entry.clientMessageId.trim()
    timelineOutbox.remove(entry.idempotencyKey)
    val failedMessage = buildQueuedTimelineOutboxUserMessage(entry).copy(
        deliveryState = "failed",
        queuePosition = null
    )
    val retainedMessages = currentMessages.filterNot { message ->
        message.deliveryState.equals("queued", ignoreCase = true) &&
            (queuedClientMessageId(message) == clientMessageId ||
                message.id == "user-$clientMessageId" ||
                message.runId == "local-user-$clientMessageId")
    } + failedMessage
    val ordered = orderedMessages(retainedMessages)
    _state.value = _state.value.copy(
        messages = ordered,
        errorMessage = choose(
            "A queued message could not be restored. It was marked failed and the remaining queue will continue.",
            "一条排队消息无法恢复，已标记失败，其余队列将继续发送。"
        )
    )
    timelineState = timelineState.copy(messages = ordered)
    noteCanonicalTimelineMutation()
    persistCurrentTimelineSnapshot(timelineState, ordered, durablePendingOverlay = true)
}

internal fun ChatStore.sendTextOutgoingRun(
    content: String,
    gatewayId: String,
    attachmentIds: List<String>,
    attachmentBlocks: List<RelayChatContentBlock>,
    commandAttachments: List<RelayChatSendAttachmentPayload>,
    clientRunId: String? = null
) {
    val queueBehindActiveRun = shouldQueueOutgoingTextRun(
        hasActiveReply = hasActiveReplyForOutgoingQueue(),
        hasQueuedEntries = orderedQueuedTimelineOutboxEntries().isNotEmpty(),
        relayConfigured = apiClient.isConfigured,
        connectionState = wsClient.connectionState.value
    )
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
    val queuePosition = if (queueBehindActiveRun) nextQueuedTimelineOutboxPosition() else null
    val outboxEntry = TimelineOutboxEntry(
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
        createdAtEpochMs = System.currentTimeMillis(),
        queued = queueBehindActiveRun,
        queuePosition = queuePosition,
        localTurnOrder = draft.userMessage.localTurnOrder
    )

    if (queueBehindActiveRun) {
        val queuedMessages = draft.messages
            .filterNot { message -> message.id == draft.assistantMessage.id }
            .map { message ->
                if (message.id == draft.userMessage.id) {
                    message.copy(
                        deliveryState = "queued",
                        clientMessageText = content,
                        queuePosition = queuePosition
                    )
                } else {
                    message
                }
            }
        timelineOutbox[resolvedClientRunId] = outboxEntry
        persistSelectedSession(gatewayId, sessionKey)
        _state.value = _state.value.copy(messages = orderedMessages(queuedMessages))
        timelineState = timelineState.copy(messages = _state.value.messages)
        noteCanonicalTimelineMutation()
        persistCurrentTimelineSnapshot(
            timelineState,
            _state.value.messages,
            durablePendingOverlay = true
        )
        scheduleQueuedTimelineOutboxDrain()
        return
    }

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
    timelineOutbox[resolvedClientRunId] = outboxEntry
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

fun ChatStore.moveQueuedMessage(messageId: String, offset: Int) {
    if (offset == 0) return
    val queuedEntries = orderedQueuedTimelineOutboxEntries().toMutableList()
    val sourceIndex = queuedEntries.indexOfFirst { entry -> queuedEntryMatchesMessage(entry, messageId) }
    if (sourceIndex < 0) return
    val destinationIndex = sourceIndex + offset
    if (destinationIndex !in queuedEntries.indices) return

    val moved = queuedEntries.removeAt(sourceIndex)
    queuedEntries.add(destinationIndex, moved)
    val basePosition = queuedEntries.minOfOrNull(::effectiveQueuedTimelineOutboxPosition) ?: 0L
    queuedEntries.forEachIndexed { index, entry ->
        timelineOutbox[entry.idempotencyKey] = entry.copy(queuePosition = basePosition + index.toLong())
    }
    val positionByClientMessageId = queuedEntries.mapIndexedNotNull { index, entry ->
        normalizedQueuedEntryClientMessageId(entry)?.let { clientMessageId ->
            clientMessageId to (basePosition + index.toLong())
        }
    }.toMap()
    val messages = _state.value.messages.map { message ->
        val clientMessageId = queuedClientMessageId(message)
        val nextPosition = clientMessageId?.let(positionByClientMessageId::get)
        if (message.deliveryState.equals("queued", ignoreCase = true) && nextPosition != null) {
            message.copy(queuePosition = nextPosition)
        } else {
            message
        }
    }
    commitQueuedMessageMutation(messages)
}

fun ChatStore.removeQueuedMessage(messageId: String) {
    val entry = orderedQueuedTimelineOutboxEntries()
        .firstOrNull { queuedEntry -> queuedEntryMatchesMessage(queuedEntry, messageId) }
        ?: return
    val clientMessageId = normalizedQueuedEntryClientMessageId(entry) ?: return
    timelineOutbox.remove(entry.idempotencyKey)
    val messages = _state.value.messages.filterNot { message ->
        message.deliveryState.equals("queued", ignoreCase = true) &&
            queuedClientMessageId(message) == clientMessageId
    }
    commitQueuedMessageMutation(messages)
    // 删除旧队首时当前运行可能早已结束；立即复核剩余队列，避免下一条继续被旧队首的
    // 生命周期卡住。drain 本身会再次校验连接态与可见 active reply。
    scheduleQueuedTimelineOutboxDrain()
}

private fun ChatStore.commitQueuedMessageMutation(messages: List<com.rethinkingstudio.clawlink.core.models.chat.ChatMessage>) {
    val ordered = orderedMessages(messages)
    _state.value = _state.value.copy(messages = ordered)
    timelineState = timelineState.copy(messages = ordered)
    noteCanonicalTimelineMutation()
    if (!persistCurrentTimelineSnapshot(timelineState, ordered, durablePendingOverlay = true)) {
        _state.value = _state.value.copy(
            errorMessage = choose(
                "The updated message queue could not be saved.",
                "消息队列的调整暂时无法保存。"
            )
        )
    }
}

private fun ChatStore.orderedQueuedTimelineOutboxEntries(): List<TimelineOutboxEntry> {
    return timelineOutbox.values
        .filter { entry -> entry.queued && entry.kind == TimelineOutboxKind.TEXT }
        .sortedWith(
            compareBy<TimelineOutboxEntry>(::effectiveQueuedTimelineOutboxPosition)
                .thenBy { entry -> entry.idempotencyKey }
        )
}

private fun ChatStore.nextQueuedTimelineOutboxPosition(): Long {
    val maximum = orderedQueuedTimelineOutboxEntries()
        .maxOfOrNull(::effectiveQueuedTimelineOutboxPosition)
        ?: return 0L
    return if (maximum == Long.MAX_VALUE) maximum else maximum + 1L
}

private fun effectiveQueuedTimelineOutboxPosition(entry: TimelineOutboxEntry): Long {
    return entry.queuePosition ?: entry.createdAtEpochMs
}

private fun queuedEntryMatchesMessage(entry: TimelineOutboxEntry, messageId: String): Boolean {
    val normalizedMessageId = messageId.trim()
    val clientMessageId = normalizedQueuedEntryClientMessageId(entry) ?: return false
    return normalizedMessageId == clientMessageId ||
        normalizedMessageId == "user-$clientMessageId" ||
        normalizedMessageId == "local-user-$clientMessageId"
}

private fun normalizedQueuedEntryClientMessageId(entry: TimelineOutboxEntry): String? {
    return entry.clientMessageId.trim().takeIf { it.isNotEmpty() }
}

private fun queuedClientMessageId(message: com.rethinkingstudio.clawlink.core.models.chat.ChatMessage): String? {
    val runId = message.runId.trim()
    if (runId.startsWith("local-user-")) {
        return runId.removePrefix("local-user-").trim().takeIf { it.isNotEmpty() }
    }
    return message.id.removePrefix("user-").trim().takeIf { it.isNotEmpty() }
}

internal fun ChatStore.sendVoiceOutgoingRun(
    gatewayId: String,
    audio: VoiceSendAudioPayload,
    message: String?,
    languageHint: String?
) {
    if (hasActiveReplyForOutgoingQueue()) return
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
        createdAtEpochMs = System.currentTimeMillis(),
        localTurnOrder = draft.userMessage.localTurnOrder
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

/**
 * 只有当前时间线上确实存在可见的流式回复时，后续消息才进入队列。
 * timeline 的 active-run 映射可能在终态事件缺少旧 client runId 时短暂残留，
 * 不能让这类不可见的恢复元数据永久阻塞用户发送。
 */
private fun ChatStore.hasActiveReplyForOutgoingQueue(): Boolean {
    return _state.value.isStreaming &&
        hasActiveVisibleTimelineRun(timelineState, _state.value.messages)
}

internal fun shouldQueueOutgoingTextRun(
    hasActiveReply: Boolean,
    hasQueuedEntries: Boolean,
    relayConfigured: Boolean,
    connectionState: WsConnectionState
): Boolean {
    return hasActiveReply ||
        hasQueuedEntries ||
        (relayConfigured && connectionState != WsConnectionState.connected)
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
    timelineOutbox.values.filterNot { it.queued }.forEach { entry ->
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
