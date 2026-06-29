package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock

internal object ChatTimelineReducer {
    fun reduceAll(state: ChatTimelineState, events: List<TimelineEvent>): ChatTimelineState {
        return events.fold(state) { current, event -> reduce(current, event) }
    }

    fun reduce(state: ChatTimelineState, event: TimelineEvent): ChatTimelineState {
        if (event.eventId != null && event.eventId in state.seenEventIds) {
            return when (event) {
                is TimelineEvent.MessageCompleted -> if (state.shouldApplyMessageCompleted(event)) state.applyMessageCompleted(event) else state
                else -> state
            }
        }
        val markedState = state.rememberEvent(event)
        return when (event) {
            is TimelineEvent.TurnUserCreated -> if (event.hasCanonicalTimelineKeys()) markedState.applyUserTurn(event) else markedState
            is TimelineEvent.MessagePartDelta -> if (event.hasCanonicalTimelineKeys()) markedState.applyPartDelta(event) else markedState
            is TimelineEvent.MessageCompleted -> if (markedState.shouldApplyMessageCompleted(event)) markedState.applyMessageCompleted(event) else markedState
            is TimelineEvent.RunTerminal -> markedState.applyRunTerminal(event)
            is TimelineEvent.AttachmentStateChanged -> AttachmentTimelineReducer.reduce(markedState, event, rememberEvent = false)
            is TimelineEvent.ToolInvocationUpdated -> if (event.hasToolInvocationIdentity()) markedState.applyToolInvocation(event) else markedState
            is TimelineEvent.HistorySnapshotPage -> markedState.applyHistorySnapshot(event)
        }
    }

    private fun ChatTimelineState.shouldApplyMessageCompleted(event: TimelineEvent.MessageCompleted): Boolean {
        if (event.hasCanonicalTimelineKeys()) return true
        if (messages.any { it.id == event.messageId && it.timelineIdentityKey.isNotBlank() }) return true
        // 缺 canonical 字段的 completion 只能在 turn/run 稳定身份能锚到现有 user 或等待占位时应用。
        return event.clearsWaitingAssistant() &&
            (matchingAssistantMessageForCompletedEvent(event) != null || matchingTurnUserMessage(event) != null)
    }

    private fun ChatTimelineState.applyUserTurn(event: TimelineEvent.TurnUserCreated): ChatTimelineState {
        if (messages.any { it.id == event.messageId }) return this
        val incomingTurnIdentities = listOfNotNull(
            event.turnId.takeIf { it.isNotBlank() },
            event.runId?.takeIf { it.isNotBlank() }
        ).mapNotNull { normalizedTurnIdentity(it) }.toSet()
        val localIndex = messages.indexOfFirst { message ->
            message.role == MessageRole.user &&
                normalizedTurnIdentity(message.runId) in incomingTurnIdentities
        }
            .takeIf { it >= 0 }
        val existing = localIndex?.let(messages::getOrNull)
        val message = ChatMessage(
            id = event.messageId,
            role = MessageRole.user,
            state = MessageState.completed,
            content = event.content.timelineText(),
            contentBlocks = event.content,
            createdAt = event.createdAt.orEmpty().ifBlank { existing?.createdAt.orEmpty() },
            runId = existing?.runId?.takeIf { it.startsWith("local-user-") }
                ?: if (event.source.isLocalTimelineSource()) {
                    "local-user-${event.turnId}"
                } else {
                    event.runId?.takeIf { it.isNotBlank() } ?: event.turnId
                },
            sortTimestamp = existing?.sortTimestamp ?: timelineSortTimestamp(event.createdAt),
            seq = event.seq,
            turnSeq = event.turnSeq,
            timelineOrderKey = event.timelineOrderKey.orEmpty(),
            timelineIdentityKey = event.timelineIdentityKey.orEmpty(),
            timelineItemKind = event.timelineItemKind.orEmpty(),
            timelineResolvesWaiting = event.timelineResolvesWaiting,
            source = event.source.orEmpty()
        )
        if (localIndex == null || localIndex < 0) return copy(messages = messages + message)
        val mergedMessage = existing?.let { mergeLocalUserMessage(local = it, incoming = message) } ?: message
        return copy(messages = messages.toMutableList().also { it[localIndex] = mergedMessage })
    }

    private fun ChatTimelineState.applyPartDelta(event: TimelineEvent.MessagePartDelta): ChatTimelineState {
        val exactSeqKey = partSeqKey(event.messageId, event.partId, event.seq)
        if (exactSeqKey in seenPartSeqKeys) return this

        val partKey = partKey(event.messageId, event.partId)
        val previousSeq = messagePartSeqByKey[partKey]
        if (previousSeq != null && event.seq <= previousSeq) {
            return copy(seenPartSeqKeys = seenPartSeqKeys + exactSeqKey)
        }

        val existingParts = messagePartsById[event.messageId] ?: TimelineMessageParts(turnId = event.turnId)
        val nextParts = existingParts.copy(
            turnId = existingParts.turnId ?: event.turnId,
            parts = existingParts.parts + (event.partId to event.content.timelineText())
        )
        val role = event.role.toMessageRole(default = MessageRole.assistant)
        val existingMessage = messages.firstOrNull { it.id == event.messageId }
        val localPlaceholder = if (
            existingMessage == null &&
            role == MessageRole.assistant &&
            !event.runId.isNullOrBlank() &&
            event.content.canReplaceAssistantDeltaPlaceholder()
        ) {
            messages.firstOrNull { candidate ->
                candidate.role == MessageRole.assistant &&
                    candidate.state == MessageState.streaming &&
                    candidate.runId == event.runId &&
                    isTransientAssistantPlaceholder(candidate)
            } ?: singleUnresolvedTransientAssistantPlaceholder(turnId = event.turnId, runId = event.runId)
        } else {
            null
        }
        val matchedMessage = existingMessage ?: localPlaceholder
        val fallbackSortTimestamp = matchedMessage?.sortTimestamp
            ?: event.turnId?.let { turnId ->
                messages.lastOrNull { candidate -> candidate.role == MessageRole.user && candidate.runId == "local-user-$turnId" }
                    ?.sortTimestamp
                    ?.plus(timelineMessageOrderEpsilon)
            }
            ?: latestKnownSortTimestamp()?.plus(timelineMessageOrderEpsilon)
        val message = upsertMessage(
            message = ChatMessage(
                id = event.messageId,
                role = role,
                state = MessageState.streaming,
                content = nextParts.content,
                contentBlocks = event.content,
                createdAt = event.createdAt.orEmpty().ifBlank { matchedMessage?.createdAt.orEmpty() },
                runId = event.runId.orEmpty(),
                sortTimestamp = timelineSortTimestamp(event.createdAt, fallbackSortTimestamp),
                seq = event.seq,
                turnSeq = matchedMessage?.turnSeq ?: event.turnSeq,
                timelineOrderKey = event.timelineOrderKey.orEmpty().ifBlank { matchedMessage?.timelineOrderKey.orEmpty() },
                timelineIdentityKey = event.timelineIdentityKey.orEmpty().ifBlank { matchedMessage?.timelineIdentityKey.orEmpty() },
                timelineItemKind = event.timelineItemKind.orEmpty().ifBlank { matchedMessage?.timelineItemKind.orEmpty() },
                timelineResolvesWaiting = event.timelineResolvesWaiting ?: matchedMessage?.timelineResolvesWaiting
            ),
            replaceMessageId = localPlaceholder?.id
        )
        val replacedPlaceholderRunId = localPlaceholder?.runId
            ?.takeIf { it.isNotBlank() && it != event.runId }
        return copy(
            messages = message,
            activeRunId = event.runId ?: activeRunId,
            activeRunsByTurnId = event.turnId?.let {
                activeRunsByTurnId
                    .filterValues { activeRun -> activeRun != replacedPlaceholderRunId }
                    .plus(it to (event.runId ?: activeRunsByTurnId[it].orEmpty()))
            } ?: activeRunsByTurnId.filterValues { activeRun -> activeRun != replacedPlaceholderRunId },
            activeTurnByRunId = event.runId?.let { runId ->
                event.turnId?.let {
                    activeTurnByRunId
                        .filterKeys { activeRun -> activeRun != replacedPlaceholderRunId }
                        .plus(runId to it)
                }
            } ?: activeTurnByRunId.filterKeys { activeRun -> activeRun != replacedPlaceholderRunId },
            seenPartSeqKeys = seenPartSeqKeys + exactSeqKey,
            messagePartSeqByKey = messagePartSeqByKey + (partKey to event.seq),
            messagePartsById = messagePartsById + (event.messageId to nextParts)
        )
    }

    private fun ChatTimelineState.applyMessageCompleted(event: TimelineEvent.MessageCompleted): ChatTimelineState {
        val existing = messages.firstOrNull { it.id == event.messageId }
        val eventRole = event.role.toMessageRole(default = existing?.role ?: MessageRole.assistant)
        val clearsWaitingAssistant = event.clearsWaitingAssistant()
        val sameRunAssistant = if (clearsWaitingAssistant) {
            matchingAssistantMessageForCompletedEvent(event)
        } else {
            null
        }
        val matchedMessage = existing ?: sameRunAssistant
        val content = event.content.timelineText().ifBlank { matchedMessage?.content.orEmpty() }
        val role = matchedMessage?.role ?: eventRole
        if (role == MessageRole.assistant &&
            matchedMessage?.state == MessageState.streaming &&
            isTransientAssistantPlaceholder(matchedMessage) &&
            !clearsWaitingAssistant
        ) {
            return this
        }
        val completedTurnId = event.turnId ?: matchedMessage?.runId?.let { activeTurnByRunId[it] }
        val completedRunId = completedTurnId?.let { activeRunsByTurnId[it] }
            ?: event.runId?.takeIf { it.isNotBlank() }
            ?: matchedMessage?.runId?.takeIf { it.isNotBlank() }
        val anchoredOrderKey = completedAssistantOrderKey(event, matchedMessage)
        val anchoredIdentityKey = completedAssistantIdentityKey(event, matchedMessage)
        val anchoredItemKind = completedAssistantItemKind(event, matchedMessage, anchoredOrderKey, anchoredIdentityKey)
        val message = ChatMessage(
            id = event.messageId,
            role = role,
            state = MessageState.completed,
            content = content,
            contentBlocks = event.content.ifEmpty { matchedMessage?.contentBlocks.orEmpty() },
            createdAt = event.createdAt.orEmpty().ifBlank { matchedMessage?.createdAt.orEmpty() },
            runId = event.runId.orEmpty().ifBlank { matchedMessage?.runId.orEmpty() },
            sortTimestamp = matchedMessage?.sortTimestamp ?: timelineSortTimestamp(event.createdAt),
            seq = event.seq ?: matchedMessage?.seq,
            turnSeq = matchedMessage?.turnSeq ?: event.turnSeq,
            timelineMessageId = event.messageId,
            timelineOrderKey = anchoredOrderKey.orEmpty().ifBlank { event.timelineOrderKey.orEmpty().ifBlank { matchedMessage?.timelineOrderKey.orEmpty() } },
            timelineIdentityKey = anchoredIdentityKey.orEmpty().ifBlank { event.timelineIdentityKey.orEmpty().ifBlank { matchedMessage?.timelineIdentityKey.orEmpty() } },
            timelineItemKind = anchoredItemKind,
            timelineResolvesWaiting = event.timelineResolvesWaiting ?: matchedMessage?.timelineResolvesWaiting
        )
        val messageForUpsert = message
        val nextParts = messagePartsById + (event.messageId to TimelineMessageParts(event.turnId, mapOf("text" to content)))
        val upsertedMessages = upsertMessage(
            messageForUpsert,
            replaceMessageId = if (existing == null && sameRunAssistant?.id != messageForUpsert.id) sameRunAssistant?.id else null
        )
        val nextMessages = if (clearsWaitingAssistant) {
            upsertedMessages.filterNot { candidate ->
                candidate.id != messageForUpsert.id &&
                    candidate.role == MessageRole.assistant &&
                    candidate.state == MessageState.streaming &&
                    isTransientAssistantPlaceholder(candidate) &&
                    completedEventMatchesPlaceholder(candidate, event)
            }
        } else {
            upsertedMessages
        }
        val placeholderRunIdToClear = sameRunAssistant?.runId
            ?.takeIf { it.isNotBlank() && it != completedRunId }
        val runIdsToClear = if (clearsWaitingAssistant) {
            listOfNotNull(completedRunId, placeholderRunIdToClear).toSet()
        } else {
            emptySet()
        }
        val clearedRunsByTurn = activeRunsByTurnId.filterValues { activeRun -> activeRun !in runIdsToClear }
        return copy(
            messages = orderMessagesWithSourceRunAnchors(anchoredMessagesForCompletedTurn(nextMessages, event)),
            activeRunId = if (activeRunId != null && activeRunId in runIdsToClear) null else activeRunId,
            activeRunsByTurnId = completedTurnId?.let { clearedRunsByTurn - it } ?: clearedRunsByTurn,
            activeTurnByRunId = activeTurnByRunId.filterKeys { activeRun -> activeRun !in runIdsToClear },
            messagePartsById = nextParts
        )
    }

    private fun ChatTimelineState.matchingAssistantMessageForCompletedEvent(
        event: TimelineEvent.MessageCompleted
    ): ChatMessage? {
        messages.lastOrNull { candidate ->
            candidate.id != event.messageId &&
                candidate.role == MessageRole.assistant &&
                candidate.state == MessageState.streaming &&
                completedEventMatchesPlaceholder(candidate, event)
        }?.let { return it }

        if (!event.clearsWaitingAssistant()) return null
        return singleUnresolvedTransientAssistantPlaceholder(turnId = event.turnId, runId = event.runId)
            ?: oldestUnresolvedTransientAssistantPlaceholder(turnId = event.turnId, runId = event.runId)
    }

    private fun ChatTimelineState.applyRunTerminal(event: TimelineEvent.RunTerminal): ChatTimelineState {
        val turnId = event.turnId ?: event.runId?.let { activeTurnByRunId[it] }
        val runId = event.runId ?: turnId?.let { activeRunsByTurnId[it] }
        val hasExplicitScope = !turnId.isNullOrBlank() || !runId.isNullOrBlank()
        if ((event.status == "completed" || event.status == "aborted") && messages.any { message ->
                message.state == MessageState.streaming &&
                    matchesTerminalEvent(message, turnId, runId, hasExplicitScope) &&
                    isWaitingOnlyStreamingContent(message.content)
            }
        ) {
            return this
        }
        val shouldClearActiveRunId = !hasExplicitScope || activeRunId == null || activeRunId == runId
        val nextRunsByTurn = when {
            !hasExplicitScope -> emptyMap()
            turnId != null -> activeRunsByTurnId - turnId
            runId != null -> activeRunsByTurnId.filterValues { activeRunId -> activeRunId != runId }
            else -> activeRunsByTurnId
        }
        val nextTurnByRun = when {
            !hasExplicitScope -> emptyMap()
            runId != null -> activeTurnByRunId - runId
            turnId != null -> activeTurnByRunId.filterValues { activeTurnId -> activeTurnId != turnId }
            else -> activeTurnByRunId
        }
        val terminalMessageState = if (event.status == "failed") MessageState.failed else MessageState.completed
        val terminalMessages = messages.map { message ->
            if (message.state == MessageState.streaming && matchesTerminalEvent(message, turnId, runId, hasExplicitScope)) {
                message.copy(state = terminalMessageState)
            } else {
                message
            }
        }
        return copy(
            messages = terminalMessages,
            activeRunId = if (shouldClearActiveRunId) null else activeRunId,
            activeRunsByTurnId = nextRunsByTurn,
            activeTurnByRunId = nextTurnByRun
        )
    }

    private fun ChatTimelineState.applyToolInvocation(event: TimelineEvent.ToolInvocationUpdated): ChatTimelineState {
        val existing = toolsById[event.toolCallId]
        val messageId = toolInvocationMessageId(event, existing)
        val tool = TimelineToolInvocationState(
            toolCallId = event.toolCallId,
            messageId = messageId,
            name = event.name ?: existing?.name,
            state = event.state,
            text = event.text ?: existing?.text
        )
        val existingMessage = messages.firstOrNull { it.id == messageId }
        val contentBlocks = event.content.ifEmpty { existingMessage?.contentBlocks.orEmpty() }
        val fallbackName = event.name ?: existing?.name ?: event.toolCallId
        val content = if (event.content.isEmpty()) {
            existingMessage?.content?.takeIf { it.isNotBlank() }
                ?: toolMessageContent(event, contentBlocks, fallbackName)
        } else {
            toolMessageContent(event, contentBlocks, fallbackName)
        }
        if (existingMessage == null && contentBlocks.isEmpty() && content.isBlank()) {
            return copy(toolsById = toolsById + (event.toolCallId to tool))
        }
        val fallbackSortTimestamp = event.turnId?.let { turnId ->
                messages.lastOrNull { candidate ->
                    candidate.role == MessageRole.user &&
                        (candidate.runId == "local-user-$turnId" || candidate.id == "user-$turnId")
                }?.sortTimestamp?.plus(timelineMessageOrderEpsilon)
            }
            ?: latestKnownSortTimestamp()?.plus(timelineMessageOrderEpsilon)
        val message = ChatMessage(
            id = messageId,
            role = MessageRole.tool,
            state = toolMessageState(event),
            content = content,
            contentBlocks = contentBlocks,
            createdAt = event.createdAt.orEmpty().ifBlank { existingMessage?.createdAt.orEmpty() },
            runId = event.runId.orEmpty().ifBlank { existingMessage?.runId.orEmpty() },
            sortTimestamp = existingMessage?.sortTimestamp
                ?: timelineSortTimestamp(event.createdAt, fallbackSortTimestamp),
            seq = event.seq ?: existingMessage?.seq,
            turnSeq = event.turnSeq ?: existingMessage?.turnSeq,
            timelineOrderKey = event.timelineOrderKey.orEmpty().ifBlank { existingMessage?.timelineOrderKey.orEmpty() },
            timelineIdentityKey = event.timelineIdentityKey.orEmpty().ifBlank { existingMessage?.timelineIdentityKey.orEmpty() },
            timelineItemKind = event.timelineItemKind.orEmpty().ifBlank { existingMessage?.timelineItemKind.orEmpty() },
            timelineResolvesWaiting = event.timelineResolvesWaiting ?: existingMessage?.timelineResolvesWaiting
        )
        return copy(
            messages = orderMessagesWithSourceRunAnchors(upsertToolMessage(message)),
            activeRunId = event.runId ?: activeRunId,
            activeRunsByTurnId = if (!event.turnId.isNullOrBlank() && !event.runId.isNullOrBlank()) {
                activeRunsByTurnId + (event.turnId to event.runId)
            } else {
                activeRunsByTurnId
            },
            activeTurnByRunId = if (!event.turnId.isNullOrBlank() && !event.runId.isNullOrBlank()) {
                activeTurnByRunId + (event.runId to event.turnId)
            } else {
                activeTurnByRunId
            },
            toolsById = toolsById + (event.toolCallId to tool)
        )
    }

    private fun ChatTimelineState.toolInvocationMessageId(
        event: TimelineEvent.ToolInvocationUpdated,
        existing: TimelineToolInvocationState?
    ): String {
        val eventMessageId = event.messageId?.trim()?.takeIf { it.isNotEmpty() }
        val eventMessage = eventMessageId?.let { messageId -> messages.firstOrNull { it.id == messageId } }
        if (eventMessageId != null && (eventMessage == null || eventMessage.role == MessageRole.tool || eventMessage.hasToolContent)) {
            return eventMessageId
        }
        return existing?.messageId?.trim()?.takeIf { it.isNotEmpty() }
            ?: "tool:${event.toolCallId}"
    }

    private fun ChatTimelineState.matchingAssistantAnchorForTool(event: TimelineEvent.ToolInvocationUpdated): ChatMessage? {
        return messages.lastOrNull { candidate ->
            candidate.role == MessageRole.assistant &&
                toolEventMatchesAssistant(candidate, event)
        }
    }

    private fun ChatTimelineState.toolEventMatchesAssistant(
        message: ChatMessage,
        event: TimelineEvent.ToolInvocationUpdated
    ): Boolean {
        val runId = event.runId?.takeIf { it.isNotBlank() }
        if (runId != null && message.runId == runId) return true

        val turnId = event.turnId?.takeIf { it.isNotBlank() } ?: return false
        if (activeRunsByTurnId[turnId] == message.runId) return true
        return runId != null && activeTurnByRunId[runId] == turnId
    }

    private fun anchoredToolSortTimestamp(
        event: TimelineEvent.ToolInvocationUpdated,
        anchorAssistant: ChatMessage?
    ): Double? {
        val anchorTimestamp = anchorAssistant?.sortTimestamp ?: return null
        val upperBound = anchorTimestamp - (timelineMessageOrderEpsilon / 10.0)
        val eventTimestamp = timelineSortTimestamp(event.createdAt)
        return minOf(eventTimestamp ?: upperBound, upperBound)
    }

    private fun toolMessageState(event: TimelineEvent.ToolInvocationUpdated): MessageState {
        event.messageState.toMessageState()?.let { return it }
        return when (event.state.trim().lowercase()) {
            "success", "completed", "complete", "done", "final", "result" -> MessageState.completed
            "failed", "fail", "error", "cancelled", "canceled", "denied" -> MessageState.failed
            else -> MessageState.streaming
        }
    }

    private fun toolMessageContent(
        event: TimelineEvent.ToolInvocationUpdated,
        contentBlocks: List<RelayChatContentBlock>,
        fallbackName: String?
    ): String {
        event.text?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        contentBlocks.renderToolBlocksDisplayText().trim().takeIf { it.isNotEmpty() }?.let { return it }
        return fallbackName?.trim().orEmpty()
    }

    private fun ChatTimelineState.applyHistorySnapshot(event: TimelineEvent.HistorySnapshotPage): ChatTimelineState {
        var nextState = this
        var fallbackSortTimestamp = 0.0
        event.items.forEach { item ->
            if (item.messageId !in nextState.historySnapshotMessageIds) {
                if (item.timelineOrderKey.isNullOrBlank() ||
                    item.timelineIdentityKey.isNullOrBlank() ||
                    item.timelineItemKind.isNullOrBlank()
                ) {
                    return@forEach
                }
                val sortTimestamp = timelineSortTimestamp(
                    createdAt = item.createdAt,
                    fallback = fallbackSortTimestamp
                )
                fallbackSortTimestamp = (sortTimestamp ?: fallbackSortTimestamp) + timelineMessageOrderEpsilon
                val content = item.displayText.ifBlank { item.content.timelineText() }
                val message = ChatMessage(
                    id = item.messageId,
                    role = item.role.toMessageRole(default = MessageRole.assistant),
                    state = MessageState.completed,
                    content = content,
                    contentBlocks = item.content,
                    createdAt = item.createdAt.orEmpty(),
                    runId = item.runId?.takeIf { it.isNotBlank() } ?: item.messageId,
                    sortTimestamp = sortTimestamp,
                    seq = item.seq,
                    turnSeq = item.turnSeq,
                    timelineMessageId = item.messageId,
                    timelineOrderKey = item.timelineOrderKey.orEmpty(),
                    timelineIdentityKey = item.timelineIdentityKey.orEmpty(),
                    timelineItemKind = item.timelineItemKind.orEmpty(),
                    timelineResolvesWaiting = item.timelineResolvesWaiting,
                    source = item.source.orEmpty()
                )
                val localUser = if (message.role == MessageRole.user) {
                    nextState.matchingLocalUser(
                        excludingMessageId = item.messageId,
                        turnId = item.turnId,
                        runId = item.runId
                    ) ?: nextState.matchingLocalUserByContent(
                        excludingMessageId = item.messageId,
                        turnId = item.turnId,
                        runId = item.runId,
                        content = message.content
                    )
                } else {
                    null
                }
                val localPlaceholder = if (
                    message.role == MessageRole.assistant &&
                    message.state == MessageState.completed &&
                    message.hasAssistantAnswerTimelineContent()
                ) {
                    nextState.messages.firstOrNull { candidate ->
                        candidate.id != item.messageId &&
                            candidate.role == MessageRole.assistant &&
                            candidate.state == MessageState.streaming &&
                            isTransientAssistantPlaceholder(candidate) &&
                            nextState.historyItemMatchesPlaceholder(item, candidate)
                    }
                } else {
                    null
                }
                val placeholderRunId = localPlaceholder?.runId?.takeIf { it.isNotBlank() }
                val messageForUpsert = if (localPlaceholder != null && (message.sortTimestamp == null || item.createdAt.isNullOrBlank())) {
                    message.copy(
                        createdAt = message.createdAt.ifBlank { localPlaceholder.createdAt },
                        sortTimestamp = localPlaceholder.sortTimestamp
                    )
                } else {
                    message
                }
                val turnIdsToClear = buildSet {
                    item.turnId.takeIf { it.isNotBlank() }?.let { turnId ->
                        if (nextState.activeRunsByTurnId[turnId] == placeholderRunId) add(turnId)
                    }
                    placeholderRunId?.let { runId ->
                        nextState.activeTurnByRunId[runId]?.let(::add)
                    }
                }
                nextState = nextState.copy(
                    messages = nextState.upsertMessage(
                        message = localUser?.let { mergeLocalUserMessage(local = it, incoming = messageForUpsert) } ?: messageForUpsert,
                        replaceMessageId = localUser?.id ?: localPlaceholder?.id
                    ),
                    activeRunId = if (nextState.activeRunId != null && nextState.activeRunId == placeholderRunId) {
                        null
                    } else {
                        nextState.activeRunId
                    },
                    activeRunsByTurnId = nextState.activeRunsByTurnId
                        .filterKeys { turnId -> turnId !in turnIdsToClear }
                        .filterValues { activeRunId -> activeRunId != placeholderRunId },
                    activeTurnByRunId = nextState.activeTurnByRunId
                        .filterKeys { activeRunId -> activeRunId != placeholderRunId }
                        .filterValues { turnId -> turnId !in turnIdsToClear },
                    historySnapshotTurnIds = nextState.historySnapshotTurnIds + item.turnId,
                    historySnapshotMessageIds = nextState.historySnapshotMessageIds + item.messageId
                )
            }
        }
        return nextState.copy(messages = orderMessagesWithSourceRunAnchors(nextState.messages))
    }

    private fun ChatTimelineState.rememberEvent(event: TimelineEvent): ChatTimelineState {
        val eventId = event.eventId?.takeIf { it.isNotBlank() } ?: return this
        return copy(seenEventIds = seenEventIds + eventId)
    }
}
