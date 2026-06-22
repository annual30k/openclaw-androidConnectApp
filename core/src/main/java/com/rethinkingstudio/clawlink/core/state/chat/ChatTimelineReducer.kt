package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import java.time.Instant
import java.time.format.DateTimeParseException

private const val timelineMessageOrderEpsilon = 0.001

internal object ChatTimelineReducer {
    fun reduceAll(state: ChatTimelineState, events: List<TimelineEvent>): ChatTimelineState {
        return events.fold(state) { current, event -> reduce(current, event) }
    }

    fun reduce(state: ChatTimelineState, event: TimelineEvent): ChatTimelineState {
        if (event.eventId != null && event.eventId in state.seenEventIds) {
            return when (event) {
                is TimelineEvent.MessageCompleted -> if (event.hasCanonicalTimelineKeys()) state.applyMessageCompleted(event) else state
                else -> state
            }
        }
        val markedState = state.rememberEvent(event)
        return when (event) {
            is TimelineEvent.TurnUserCreated -> if (event.hasCanonicalTimelineKeys()) markedState.applyUserTurn(event) else markedState
            is TimelineEvent.MessagePartDelta -> if (event.hasCanonicalTimelineKeys()) markedState.applyPartDelta(event) else markedState
            is TimelineEvent.MessageCompleted -> if (event.hasCanonicalTimelineKeys()) markedState.applyMessageCompleted(event) else markedState
            is TimelineEvent.RunTerminal -> markedState.applyRunTerminal(event)
            is TimelineEvent.AttachmentStateChanged -> AttachmentTimelineReducer.reduce(markedState, event, rememberEvent = false)
            is TimelineEvent.ToolInvocationUpdated -> if (event.hasToolInvocationIdentity()) markedState.applyToolInvocation(event) else markedState
            is TimelineEvent.HistorySnapshotPage -> markedState.applyHistorySnapshot(event)
        }
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
            runId = existing?.runId?.takeIf { it.startsWith("local-user-") } ?: "local-user-${event.turnId}",
            sortTimestamp = existing?.sortTimestamp ?: timelineSortTimestamp(event.createdAt),
            seq = event.seq,
            turnSeq = event.turnSeq,
            timelineOrderKey = event.timelineOrderKey.orEmpty(),
            timelineIdentityKey = event.timelineIdentityKey.orEmpty(),
            timelineItemKind = event.timelineItemKind.orEmpty(),
            timelineResolvesWaiting = event.timelineResolvesWaiting
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
            timelineItemKind = event.timelineItemKind.orEmpty().ifBlank { matchedMessage?.timelineItemKind.orEmpty() },
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
                    timelineResolvesWaiting = item.timelineResolvesWaiting
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

    private fun ChatTimelineState.completedAssistantOrderKey(
        event: TimelineEvent.MessageCompleted,
        existing: ChatMessage?
    ): String? {
        if (!event.clearsWaitingAssistant()) return null
        existing?.timelineOrderKey
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.startsWith("local:") }
            ?.let { return it }
        val user = matchingTurnUserMessage(event) ?: return null
        val turnBase = anchoredTurnBase(user) ?: return null
        val identity = completedAssistantIdentityKey(event, existing) ?: event.messageId
        return listOf(
            "v1",
            turnBase,
            "50",
            "${padded(1, 16)}:part-text-1:${event.messageId}",
            shortStableHash(identity)
        ).joinToString("|")
    }

    private fun completedAssistantIdentityKey(
        event: TimelineEvent.MessageCompleted,
        existing: ChatMessage?
    ): String? {
        return event.timelineIdentityKey?.trim()?.takeIf { it.isNotEmpty() }
            ?: existing?.timelineIdentityKey?.trim()?.takeIf { it.isNotEmpty() }
            ?: event.runId?.trim()?.takeIf { it.isNotEmpty() }?.let { "local:$it:message:assistant:030-assistant" }
            ?: event.turnId?.trim()?.takeIf { it.isNotEmpty() }?.let { "local:$it:message:assistant:030-assistant" }
    }

    private fun ChatTimelineState.matchingTurnUserMessage(event: TimelineEvent.MessageCompleted): ChatMessage? {
        val identities = listOfNotNull(event.turnId, event.runId)
            .mapNotNull { normalizedTurnIdentity(it) }
            .toSet()
        if (identities.isEmpty()) return null
        return messages.lastOrNull { message ->
            message.role == MessageRole.user &&
                listOfNotNull(
                    normalizedTurnIdentity(message.timelineMessageId),
                    normalizedTurnIdentity(message.runId)
                ).any { it in identities }
        }
    }

    private fun anchoredMessagesForCompletedTurn(
        messages: List<ChatMessage>,
        event: TimelineEvent.MessageCompleted
    ): List<ChatMessage> {
        val identities = listOfNotNull(event.turnId, event.runId)
            .mapNotNull { normalizedTurnIdentity(it) }
            .toSet()
        if (identities.isEmpty()) return messages
        val index = messages.indexOfLast { message ->
            message.role == MessageRole.user &&
                listOfNotNull(
                    normalizedTurnIdentity(message.timelineMessageId),
                    normalizedTurnIdentity(message.runId)
                ).any { it in identities }
        }
        if (index < 0) return messages
        val user = messages[index]
        if (user.timelineOrderKey.trim().isNotEmpty() && !user.timelineOrderKey.trim().startsWith("local:")) {
            return messages
        }
        val turnBase = anchoredTurnBase(user) ?: return messages
        val identity = user.timelineIdentityKey.trim().ifEmpty {
            "local:${user.runId.ifBlank { event.runId ?: event.turnId.orEmpty() }}:message:user:010-user"
        }
        val anchoredUser = user.copy(
            timelineOrderKey = listOf(
                "v1",
                turnBase,
                "10",
                "${padded(1, 16)}:part-text-1:${user.id}",
                shortStableHash(identity)
            ).joinToString("|"),
            timelineIdentityKey = identity,
            timelineItemKind = user.timelineItemKind.ifBlank { "message:user" }
        )
        return messages.toMutableList().also { it[index] = anchoredUser }
    }

    private fun anchoredTurnBase(user: ChatMessage): String? {
        val order = user.timelineOrderKey.trim()
        if (order.isNotEmpty() && !order.startsWith("local:")) {
            val parts = order.split("|")
            if (parts.size >= 2 && parts[1].isNotBlank()) return parts[1]
        }
        return createdAtMillis(user.createdAt)?.let { padded(it) }
    }

    private fun createdAtMillis(value: String): Long? {
        return try {
            Instant.parse(value).toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun padded(value: Long, size: Int = 20): String {
        val text = value.coerceAtLeast(0).toString()
        return if (text.length >= size) text else "0".repeat(size - text.length) + text
    }

    private fun shortStableHash(value: String): String {
        var hash = -3750763034362895579L
        value.encodeToByteArray().forEach { byte ->
            hash = hash xor (byte.toLong() and 0xff)
            hash *= 1099511628211L
        }
        return hash.toULong().toString(16)
    }

    private fun ChatTimelineState.upsertMessage(
        message: ChatMessage,
        replaceMessageId: String? = null,
        insertBeforeMessageId: String? = null
    ): List<ChatMessage> {
        val identityKey = message.timelineIdentityKey.trim().takeIf { it.isNotEmpty() } ?: return messages
        val index = messages.indexOfFirst { current ->
            current.timelineIdentityKey == identityKey || (replaceMessageId != null && current.id == replaceMessageId)
        }
        if (index < 0 && insertBeforeMessageId != null) {
            val anchorIndex = messages.indexOfFirst { it.id == insertBeforeMessageId }
            if (anchorIndex >= 0) {
                return messages.toMutableList().also { it.add(anchorIndex, message) }
            }
        }
        if (index < 0) return messages + message
        return messages.toMutableList().also { current ->
            val existing = current[index]
            current[index] = message.copy(
                createdAt = message.createdAt.ifBlank { existing.createdAt },
                runId = message.runId.ifBlank { existing.runId },
                sortTimestamp = message.sortTimestamp ?: existing.sortTimestamp,
                seq = message.seq ?: existing.seq,
                turnSeq = message.turnSeq ?: existing.turnSeq,
                timelineStableKey = message.timelineStableKey.ifBlank { existing.timelineStableKey },
                timelineMessageId = message.timelineMessageId.ifBlank { existing.timelineMessageId },
                timelinePartId = message.timelinePartId.ifBlank { existing.timelinePartId },
                timelineOrderKey = message.timelineOrderKey.ifBlank { existing.timelineOrderKey },
                timelineIdentityKey = message.timelineIdentityKey.ifBlank { existing.timelineIdentityKey },
                timelineItemKind = message.timelineItemKind.ifBlank { existing.timelineItemKind },
                timelineResolvesWaiting = message.timelineResolvesWaiting ?: existing.timelineResolvesWaiting
            )
        }
    }

    private fun ChatTimelineState.upsertToolMessage(message: ChatMessage): List<ChatMessage> {
        val identityKey = message.timelineIdentityKey.trim().takeIf { it.isNotEmpty() }
        val index = messages.indexOfFirst { current ->
            if (identityKey != null) {
                current.timelineIdentityKey == identityKey
            } else {
                current.role == MessageRole.tool && current.id == message.id
            }
        }
        if (index < 0) return messages + message
        return messages.toMutableList().also { current ->
            val existing = current[index]
            current[index] = message.copy(
                createdAt = message.createdAt.ifBlank { existing.createdAt },
                runId = message.runId.ifBlank { existing.runId },
                sortTimestamp = message.sortTimestamp ?: existing.sortTimestamp,
                seq = message.seq ?: existing.seq,
                turnSeq = message.turnSeq ?: existing.turnSeq,
                timelineStableKey = message.timelineStableKey.ifBlank { existing.timelineStableKey },
                timelineMessageId = message.timelineMessageId.ifBlank { existing.timelineMessageId },
                timelinePartId = message.timelinePartId.ifBlank { existing.timelinePartId },
                timelineOrderKey = message.timelineOrderKey.ifBlank { existing.timelineOrderKey },
                timelineIdentityKey = message.timelineIdentityKey.ifBlank { existing.timelineIdentityKey },
                timelineItemKind = message.timelineItemKind.ifBlank { existing.timelineItemKind },
                timelineResolvesWaiting = message.timelineResolvesWaiting ?: existing.timelineResolvesWaiting
            )
        }
    }

    private fun ChatTimelineState.latestKnownSortTimestamp(): Double? {
        return messages.maxOfOrNull { it.sortTimestamp ?: Double.NEGATIVE_INFINITY }
            ?.takeIf { it != Double.NEGATIVE_INFINITY }
    }

    private fun ChatTimelineState.historyItemMatchesPlaceholder(
        item: HistorySnapshotItem,
        message: ChatMessage
    ): Boolean {
        val runId = item.runId?.takeIf { it.isNotBlank() }
        if (runId != null && message.runId == runId) return true

        val turnId = item.turnId.takeIf { it.isNotBlank() } ?: return false
        if (messagePartsById[message.id]?.turnId == turnId) return true
        if (activeRunsByTurnId[turnId] == message.runId) return true
        return activeTurnByRunId[message.runId] == turnId
    }

    private fun ChatTimelineState.matchingLocalUser(
        excludingMessageId: String,
        turnId: String?,
        runId: String?
    ): ChatMessage? {
        return matchingLocalUserIndex(
            excludingMessageId = excludingMessageId,
            turnId = turnId,
            runId = runId
        )?.let(messages::get)
    }

    private fun ChatTimelineState.matchingLocalUserByContent(
        excludingMessageId: String,
        turnId: String?,
        runId: String?,
        content: String
    ): ChatMessage? {
        val allowsLegacyTurnRunMatch = !turnId.isNullOrBlank() || runId?.startsWith("turn-", ignoreCase = true) == true
        if (!allowsLegacyTurnRunMatch) return null
        val trimmedContent = content.trim()
        if (trimmedContent.isEmpty()) return null
        val candidates = messages.filter { message ->
            message.role == MessageRole.user &&
                message.id != excludingMessageId &&
                (message.timelineIdentityKey.isBlank() || message.timelineIdentityKey.contains(":local", ignoreCase = true)) &&
                message.content.trim() == trimmedContent
        }
        return candidates.singleOrNull()
    }

    private fun ChatTimelineState.matchingLocalUserIndex(
        excludingMessageId: String,
        turnId: String?,
        runId: String?
    ): Int? {
        val incomingTurnIdentities = listOfNotNull(
            turnId?.takeIf { it.isNotBlank() },
            runId?.takeIf { it.isNotBlank() }
        ).mapNotNull { normalizedTurnIdentity(it) }.toSet()
        if (incomingTurnIdentities.isNotEmpty()) {
            val explicitIndex = messages.indexOfLast { message ->
                message.role == MessageRole.user &&
                    message.id != excludingMessageId &&
                    normalizedTurnIdentity(message.runId) in incomingTurnIdentities
            }
            if (explicitIndex >= 0) return explicitIndex
        }
        return null
    }

    private fun normalizedTurnIdentity(value: String?): String? {
        var normalized = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val prefix = listOf("local-user-", "user-").firstOrNull { normalized.startsWith(it, ignoreCase = true) }
        if (prefix != null) normalized = normalized.substring(prefix.length).trim()
        normalized = normalized.replace(Regex(":(user|assistant|tool|system|waiting)$", RegexOption.IGNORE_CASE), "").trim()
        return normalized.takeIf { it.isNotEmpty() }
    }

    private fun ChatTimelineState.matchesTerminalRun(message: ChatMessage, turnId: String?, runId: String?): Boolean {
        if (!runId.isNullOrBlank() && message.runId == runId) return true
        if (turnId.isNullOrBlank()) return false
        return messagePartsById[message.id]?.turnId == turnId
    }

    private fun ChatTimelineState.matchesTerminalEvent(
        message: ChatMessage,
        turnId: String?,
        runId: String?,
        hasExplicitScope: Boolean
    ): Boolean {
        if (matchesTerminalRun(message, turnId, runId)) return true
        if (hasExplicitScope) return false
        return message.role == MessageRole.assistant &&
            message.state == MessageState.streaming &&
            !message.hasFileContent &&
            !message.hasVoiceContent &&
            !message.hasToolContent
    }

    private fun ChatTimelineState.completedEventMatchesPlaceholder(
        message: ChatMessage,
        event: TimelineEvent.MessageCompleted
    ): Boolean {
        val runId = event.runId?.takeIf { it.isNotBlank() }
        if (runId != null && message.runId == runId) return true

        val turnId = event.turnId?.takeIf { it.isNotBlank() }
        if (turnId == null) return false
        if (messagePartsById[message.id]?.turnId == turnId) return true
        if (activeRunsByTurnId[turnId] == message.runId) return true
        return activeTurnByRunId[message.runId] == turnId
    }

    private fun ChatTimelineState.singleUnresolvedTransientAssistantPlaceholder(
        turnId: String? = null,
        runId: String? = null
    ): ChatMessage? {
        val candidates = messages.filter { candidate ->
            candidate.role == MessageRole.assistant &&
                candidate.state == MessageState.streaming &&
                isTransientAssistantPlaceholder(candidate)
        }
        if (candidates.size != 1) return null
        val candidate = candidates.single()
        val ordered = messages.sortedWith(
            compareBy<ChatMessage> { it.sortTimestamp ?: Double.MAX_VALUE }
                .thenBy { it.createdAt }
                .thenBy { it.id }
        )
        val candidateIndex = ordered.indexOfFirst { it.id == candidate.id }
        if (candidateIndex <= 0) return null
        val triggeringUserIndex = ordered
            .take(candidateIndex)
            .indexOfLast { it.role == MessageRole.user }
        if (triggeringUserIndex < 0) return null
        val explicitUserIndex = explicitEventUserIndex(
            ordered = ordered,
            turnId = turnId,
            runId = runId
        )
        if (explicitUserIndex >= 0 && triggeringUserIndex != explicitUserIndex) {
            return null
        }
        val hasTerminalAssistantAfterTrigger = ordered
            .drop(triggeringUserIndex + 1)
            .any { message ->
                message.id != candidate.id &&
                    message.role == MessageRole.assistant &&
                    (message.state == MessageState.completed || message.state == MessageState.failed) &&
                    !isTransientAssistantPlaceholder(message) &&
                    (message.plainTextContent.trim().isNotEmpty() || message.contentBlocks.isNotEmpty())
        }
        return if (hasTerminalAssistantAfterTrigger) null else candidate
    }

    private fun ChatTimelineState.oldestUnresolvedTransientAssistantPlaceholder(
        turnId: String? = null,
        runId: String? = null
    ): ChatMessage? {
        if (!turnId.isNullOrBlank() || !runId.isNullOrBlank()) return null
        val ordered = messages.sortedWith(
            compareBy<ChatMessage> { it.sortTimestamp ?: Double.MAX_VALUE }
                .thenBy { it.createdAt }
                .thenBy { it.id }
        )
        for (candidateIndex in ordered.indices) {
            val candidate = ordered[candidateIndex]
            if (candidate.role != MessageRole.assistant ||
                candidate.state != MessageState.streaming ||
                !isTransientAssistantPlaceholder(candidate)
            ) {
                continue
            }
            val triggeringUserIndex = ordered
                .take(candidateIndex)
                .indexOfLast { it.role == MessageRole.user }
            if (triggeringUserIndex < 0) continue
            val nextUserIndex = ordered
                .drop(candidateIndex + 1)
                .indexOfFirst { it.role == MessageRole.user }
                .takeIf { it >= 0 }
                ?.let { candidateIndex + 1 + it }
                ?: ordered.size
            val hasTerminalAssistantAfterTrigger = ordered
                .subList(triggeringUserIndex + 1, nextUserIndex)
                .any { message ->
                    message.id != candidate.id &&
                        message.role == MessageRole.assistant &&
                        (message.state == MessageState.completed || message.state == MessageState.failed) &&
                        !isTransientAssistantPlaceholder(message) &&
                        (message.plainTextContent.trim().isNotEmpty() || message.contentBlocks.isNotEmpty())
                }
            if (!hasTerminalAssistantAfterTrigger) return candidate
        }
        return null
    }

    private fun explicitEventUserIndex(
        ordered: List<ChatMessage>,
        turnId: String?,
        runId: String?
    ): Int {
        val normalizedTurnId = turnId?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedRunId = runId?.trim()?.takeIf { it.isNotEmpty() }
        if (normalizedTurnId == null && normalizedRunId == null) return -1

        return ordered.indexOfLast { message ->
            if (message.role != MessageRole.user) return@indexOfLast false
            val messageId = message.id.trim()
            val messageRunId = message.runId.trim()
            (normalizedTurnId != null &&
                (messageId == "user-$normalizedTurnId" ||
                    messageRunId == "local-user-$normalizedTurnId" ||
                    messageRunId == normalizedTurnId)) ||
                (normalizedRunId != null &&
                    (messageId == "user-$normalizedRunId" ||
                        messageRunId == "local-user-$normalizedRunId" ||
                        messageRunId == normalizedRunId))
        }
    }

    private fun ChatTimelineState.rememberEvent(event: TimelineEvent): ChatTimelineState {
        val eventId = event.eventId?.takeIf { it.isNotBlank() } ?: return this
        return copy(seenEventIds = seenEventIds + eventId)
    }
}

private fun ChatMessage.hasRenderableTimelineCompletedContent(): Boolean {
    return content.trim().isNotEmpty() || contentBlocks.hasRenderableTimelineCompletedContent()
}

private fun TimelineEvent.MessageCompleted.clearsWaitingAssistant(): Boolean {
    timelineResolvesWaiting?.let { return it }
    val completedRole = role.toMessageRole(default = MessageRole.assistant)
    val kind = timelineItemKind?.trim()?.lowercase().orEmpty()
    if (kind == "message:assistant") return true
    return completedRole == MessageRole.assistant && !content.hasToolTimelineContent()
}

private fun ChatMessage.hasAssistantAnswerTimelineContent(): Boolean {
    if (role != MessageRole.assistant) return false
    if (contentBlocks.hasNonAnswerResultContent()) return false
    val text = contentBlocks.assistantAnswerText().ifBlank { content.trim() }
    return text.isNotBlank() && !isTransientAssistantPlaceholderContent(text)
}

private fun mergeLocalUserMessage(local: ChatMessage, incoming: ChatMessage): ChatMessage {
    val mergedBlocks = mergedLocalUserContentBlocks(local = local, incoming = incoming)
    return incoming.copy(
        content = local.content.takeIf { it.trim().isNotEmpty() } ?: incoming.content,
        contentBlocks = mergedBlocks,
        createdAt = incoming.createdAt.ifBlank { local.createdAt },
        runId = local.runId.takeIf { it.startsWith("local-user-") } ?: incoming.runId,
        sortTimestamp = local.sortTimestamp ?: incoming.sortTimestamp,
        seq = incoming.seq ?: local.seq,
        turnSeq = incoming.turnSeq ?: local.turnSeq,
        timelineStableKey = incoming.timelineStableKey.ifBlank { local.timelineStableKey },
        timelineMessageId = incoming.timelineMessageId.ifBlank { local.timelineMessageId },
        timelinePartId = incoming.timelinePartId.ifBlank { local.timelinePartId }
    )
}

private fun mergedLocalUserContentBlocks(local: ChatMessage, incoming: ChatMessage): List<RelayChatContentBlock> {
    if (local.hasVoiceContent) {
        val transcript = userPromptText(content = incoming.content, contentBlocks = incoming.contentBlocks).trim()
        if (transcript.isNotBlank()) {
            return local.contentBlocks.map { block ->
                if (block.isVoiceMessageBlock) block.copy(transcript = transcript) else block
            }
        }
    }
    if (local.contentBlocks.isEmpty()) return incoming.contentBlocks
    if (incoming.contentBlocks.isEmpty()) return local.contentBlocks
    if (local.hasFileContent && incoming.hasFileContent) {
        return mergeCompletedFileMessage(existing = local, completed = incoming).contentBlocks
    }
    return local.contentBlocks
}

private fun userPromptText(content: String, contentBlocks: List<RelayChatContentBlock>): String {
    val blockText = contentBlocks.mapNotNull { block ->
        if (block.isFileBlock || block.isVoiceMessageBlock || block.isToolCallBlock || block.isToolResultBlock) {
            null
        } else {
            block.text?.trim()?.takeIf { it.isNotEmpty() }
        }
    }.joinToString("\n\n")
    return blockText.ifBlank { content }
}

private fun TimelineEvent.TurnUserCreated.hasCanonicalTimelineKeys(): Boolean {
    return !timelineOrderKey.isNullOrBlank() &&
        !timelineIdentityKey.isNullOrBlank() &&
        !timelineItemKind.isNullOrBlank()
}

private fun TimelineEvent.MessagePartDelta.hasCanonicalTimelineKeys(): Boolean {
    return !timelineOrderKey.isNullOrBlank() &&
        !timelineIdentityKey.isNullOrBlank() &&
        !timelineItemKind.isNullOrBlank()
}

private fun TimelineEvent.MessageCompleted.hasCanonicalTimelineKeys(): Boolean {
    return !timelineOrderKey.isNullOrBlank() &&
        !timelineIdentityKey.isNullOrBlank() &&
        !timelineItemKind.isNullOrBlank()
}

private fun TimelineEvent.ToolInvocationUpdated.hasCanonicalTimelineKeys(): Boolean {
    return !timelineOrderKey.isNullOrBlank() &&
        !timelineIdentityKey.isNullOrBlank() &&
        !timelineItemKind.isNullOrBlank()
}

private fun TimelineEvent.ToolInvocationUpdated.hasToolInvocationIdentity(): Boolean {
    return toolCallId.isNotBlank() || !messageId.isNullOrBlank()
}

private fun String?.toMessageRole(default: MessageRole): MessageRole {
    return when (this?.trim()?.lowercase()) {
        "user" -> MessageRole.user
        "assistant" -> MessageRole.assistant
        "system" -> MessageRole.system
        "tool" -> MessageRole.tool
        else -> default
    }
}

private fun String?.toMessageState(): MessageState? {
    return when (this?.trim()?.lowercase()) {
        "completed", "complete", "done", "success", "final", "result" -> MessageState.completed
        "streaming", "delta", "in_progress", "running", "active" -> MessageState.streaming
        "failed", "fail", "error", "cancelled", "canceled", "denied" -> MessageState.failed
        else -> null
    }
}

private fun partKey(messageId: String, partId: String): String = "$messageId|$partId"

private fun partSeqKey(messageId: String, partId: String, seq: Long): String = "$messageId|$partId|$seq"

private fun List<com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock>.timelineText(): String {
    return mapNotNull { it.text?.takeIf { value -> value.isNotBlank() } }.joinToString("\n\n")
}

private fun List<RelayChatContentBlock>.hasRenderableTimelineCompletedContent(): Boolean {
    return any { block ->
        block.isToolCallBlock ||
            block.isToolResultBlock ||
            block.isFileBlock ||
            block.isVoiceMessageBlock ||
            !block.text.isNullOrBlank() ||
            !block.transcript.isNullOrBlank() ||
            listOf(block.result, block.partialResult, block.content, block.output, block.error).any { value ->
                !value?.renderedText(listOf("content", "markdown", "text", "body", "message", "value", "result", "output")).isNullOrBlank()
            }
    }
}

private fun List<RelayChatContentBlock>.hasAssistantAnswerTimelineContent(): Boolean {
    if (hasNonAnswerResultContent()) return false
    val text = assistantAnswerText()
    return text.isNotBlank() && !isTransientAssistantPlaceholderContent(text)
}

private fun List<RelayChatContentBlock>.canReplaceAssistantDeltaPlaceholder(): Boolean {
    if (hasNonAnswerResultContent()) return false
    val text = assistantAnswerText()
    return text.isNotBlank()
}

private fun List<RelayChatContentBlock>.hasToolTimelineContent(): Boolean {
    return any { block -> block.isToolCallBlock || block.isToolResultBlock }
}

private fun List<RelayChatContentBlock>.hasNonAnswerResultContent(): Boolean {
    return any { block ->
        block.isToolCallBlock ||
            block.isToolResultBlock ||
            block.isFileBlock ||
            block.isVoiceMessageBlock
    }
}

private fun List<RelayChatContentBlock>.assistantAnswerText(): String {
    return mapNotNull { block ->
        block.text?.trim()?.takeIf { it.isNotEmpty() }
            ?: block.transcript?.trim()?.takeIf { it.isNotEmpty() }
    }.joinToString("\n\n").trim()
}

private fun isWaitingOnlyStreamingContent(content: String): Boolean {
    return isTransientAssistantPlaceholderContent(content)
}

private fun timelineSortTimestamp(createdAt: String?, fallback: Double? = null): Double? {
    val parsed = createdAt
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { value ->
            try {
                Instant.parse(value).toEpochMilli() / 1000.0
            } catch (_: DateTimeParseException) {
                null
            }
        }
    return parsed ?: fallback
}
