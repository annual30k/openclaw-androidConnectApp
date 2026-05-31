package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
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
                is TimelineEvent.MessageCompleted -> state.applyMessageCompleted(event)
                else -> state
            }
        }
        val markedState = state.rememberEvent(event)
        return when (event) {
            is TimelineEvent.TurnUserCreated -> markedState.applyUserTurn(event)
            is TimelineEvent.MessagePartDelta -> markedState.applyPartDelta(event)
            is TimelineEvent.MessageCompleted -> markedState.applyMessageCompleted(event)
            is TimelineEvent.RunTerminal -> markedState.applyRunTerminal(event)
            is TimelineEvent.AttachmentStateChanged -> AttachmentTimelineReducer.reduce(markedState, event, rememberEvent = false)
            is TimelineEvent.ToolInvocationUpdated -> markedState.applyToolInvocation(event)
            is TimelineEvent.HistorySnapshotPage -> markedState.applyHistorySnapshot(event)
        }
    }

    private fun ChatTimelineState.applyUserTurn(event: TimelineEvent.TurnUserCreated): ChatTimelineState {
        if (messages.any { it.id == event.messageId }) return this
        val localIndex = messages.indexOfFirst { it.runId == "local-user-${event.turnId}" }
        val existing = messages.getOrNull(localIndex)
        val message = ChatMessage(
            id = event.messageId,
            role = MessageRole.user,
            state = MessageState.completed,
            content = event.content.timelineText(),
            contentBlocks = event.content,
            createdAt = event.createdAt.orEmpty().ifBlank { existing?.createdAt.orEmpty() },
            runId = "local-user-${event.turnId}",
            sortTimestamp = existing?.sortTimestamp ?: timelineSortTimestamp(event.createdAt)
        )
        if (localIndex < 0) return copy(messages = messages + message)
        return copy(messages = messages.toMutableList().also { it[localIndex] = message })
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
        val localPlaceholder = if (existingMessage == null && role == MessageRole.assistant && !event.runId.isNullOrBlank()) {
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
                sortTimestamp = timelineSortTimestamp(event.createdAt, fallbackSortTimestamp)
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
        val localPlaceholder = if (eventRole == MessageRole.assistant) {
            messages.firstOrNull { candidate ->
                candidate.id != event.messageId &&
                candidate.role == MessageRole.assistant &&
                candidate.state == MessageState.streaming &&
                    completedEventMatchesPlaceholder(candidate, event) &&
                    isTransientAssistantPlaceholder(candidate)
            } ?: singleUnresolvedTransientAssistantPlaceholder(turnId = event.turnId, runId = event.runId)
        } else {
            null
        }
        val matchedMessage = existing ?: localPlaceholder
        val content = event.content.timelineText().ifBlank { matchedMessage?.content.orEmpty() }
        val role = matchedMessage?.role ?: eventRole
        val completedTurnId = event.turnId ?: matchedMessage?.runId?.let { activeTurnByRunId[it] }
        val completedRunId = completedTurnId?.let { activeRunsByTurnId[it] }
            ?: event.runId?.takeIf { it.isNotBlank() }
            ?: matchedMessage?.runId?.takeIf { it.isNotBlank() }
        val fallbackSortTimestamp = matchedMessage?.sortTimestamp
            ?: completedTurnId?.let { turnId ->
                messages.lastOrNull { candidate ->
                    candidate.role == MessageRole.user &&
                        (candidate.runId == "local-user-$turnId" || candidate.id == "user-$turnId")
                }
                    ?.sortTimestamp
                    ?.plus(timelineMessageOrderEpsilon)
            }
            ?: latestKnownSortTimestamp()?.plus(timelineMessageOrderEpsilon)
        val message = ChatMessage(
            id = event.messageId,
            role = role,
            state = MessageState.completed,
            content = content,
            contentBlocks = event.content.ifEmpty { matchedMessage?.contentBlocks.orEmpty() },
            createdAt = event.createdAt.orEmpty().ifBlank { matchedMessage?.createdAt.orEmpty() },
            runId = event.runId.orEmpty().ifBlank { matchedMessage?.runId.orEmpty() },
            sortTimestamp = matchedMessage?.sortTimestamp ?: timelineSortTimestamp(event.createdAt, fallbackSortTimestamp)
        )
        val nextParts = messagePartsById + (event.messageId to TimelineMessageParts(event.turnId, mapOf("text" to content)))
        val upsertedMessages = upsertMessage(
            message,
            replaceMessageId = if (existing == null) localPlaceholder?.id else null
        )
        val nextMessages = if (existing != null && localPlaceholder != null) {
            upsertedMessages.filterNot { it.id == localPlaceholder.id }
        } else {
            upsertedMessages
        }
        val placeholderRunIdToClear = localPlaceholder?.runId
            ?.takeIf { it.isNotBlank() && it != completedRunId }
        val runIdsToClear = listOfNotNull(completedRunId, placeholderRunIdToClear).toSet()
        val clearedRunsByTurn = activeRunsByTurnId.filterValues { activeRun -> activeRun !in runIdsToClear }
        return copy(
            messages = nextMessages,
            activeRunId = if (activeRunId != null && activeRunId in runIdsToClear) null else activeRunId,
            activeRunsByTurnId = completedTurnId?.let { clearedRunsByTurn - it } ?: clearedRunsByTurn,
            activeTurnByRunId = activeTurnByRunId.filterKeys { activeRun -> activeRun !in runIdsToClear },
            messagePartsById = nextParts
        )
    }

    private fun ChatTimelineState.applyRunTerminal(event: TimelineEvent.RunTerminal): ChatTimelineState {
        val turnId = event.turnId ?: event.runId?.let { activeTurnByRunId[it] }
        val runId = event.runId ?: turnId?.let { activeRunsByTurnId[it] }
        val hasExplicitScope = !turnId.isNullOrBlank() || !runId.isNullOrBlank()
        if (event.status == "completed" && messages.any { message ->
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
        val nextMessages = if (event.status == "aborted") {
            terminalMessages.filterNot { message ->
                message.role == MessageRole.assistant &&
                    matchesTerminalEvent(message, turnId, runId, hasExplicitScope) &&
                    isTransientAssistantPlaceholder(message)
            }
        } else {
            terminalMessages
        }
        return copy(
            messages = nextMessages,
            activeRunId = if (shouldClearActiveRunId) null else activeRunId,
            activeRunsByTurnId = nextRunsByTurn,
            activeTurnByRunId = nextTurnByRun
        )
    }

    private fun ChatTimelineState.applyToolInvocation(event: TimelineEvent.ToolInvocationUpdated): ChatTimelineState {
        val existing = toolsById[event.toolCallId]
        val tool = TimelineToolInvocationState(
            toolCallId = event.toolCallId,
            messageId = event.messageId ?: existing?.messageId,
            name = event.name ?: existing?.name,
            state = event.state,
            text = event.text ?: existing?.text
        )
        return copy(toolsById = toolsById + (event.toolCallId to tool))
    }

    private fun ChatTimelineState.applyHistorySnapshot(event: TimelineEvent.HistorySnapshotPage): ChatTimelineState {
        var nextState = this
        var fallbackSortTimestamp = 0.0
        event.items.forEach { item ->
            if (item.messageId !in nextState.historySnapshotMessageIds) {
                val sortTimestamp = timelineSortTimestamp(
                    createdAt = item.createdAt,
                    fallback = fallbackSortTimestamp
                )
                fallbackSortTimestamp = (sortTimestamp ?: fallbackSortTimestamp) + timelineMessageOrderEpsilon
                val message = ChatMessage(
                    id = item.messageId,
                    role = item.role.toMessageRole(default = MessageRole.assistant),
                    state = MessageState.completed,
                    content = item.displayText,
                    contentBlocks = item.content,
                    createdAt = item.createdAt.orEmpty(),
                    runId = item.runId?.takeIf { it.isNotBlank() } ?: item.messageId,
                    sortTimestamp = sortTimestamp
                )
                nextState = nextState.copy(
                    messages = nextState.upsertMessage(message),
                    historySnapshotTurnIds = nextState.historySnapshotTurnIds + item.turnId,
                    historySnapshotMessageIds = nextState.historySnapshotMessageIds + item.messageId
                )
            }
        }
        return nextState
    }

    private fun ChatTimelineState.upsertMessage(message: ChatMessage, replaceMessageId: String? = null): List<ChatMessage> {
        val index = messages.indexOfFirst { current ->
            current.id == message.id || (replaceMessageId != null && current.id == replaceMessageId)
        }
        if (index < 0) return messages + message
        return messages.toMutableList().also { current ->
            val existing = current[index]
            current[index] = message.copy(
                createdAt = message.createdAt.ifBlank { existing.createdAt },
                runId = message.runId.ifBlank { existing.runId },
                sortTimestamp = message.sortTimestamp ?: existing.sortTimestamp
            )
        }
    }

    private fun ChatTimelineState.latestKnownSortTimestamp(): Double? {
        return messages.maxOfOrNull { it.sortTimestamp ?: Double.NEGATIVE_INFINITY }
            ?.takeIf { it != Double.NEGATIVE_INFINITY }
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

private fun String?.toMessageRole(default: MessageRole): MessageRole {
    return when (this?.trim()?.lowercase()) {
        "user" -> MessageRole.user
        "assistant" -> MessageRole.assistant
        "system" -> MessageRole.system
        "tool" -> MessageRole.tool
        else -> default
    }
}

private fun partKey(messageId: String, partId: String): String = "$messageId|$partId"

private fun partSeqKey(messageId: String, partId: String, seq: Long): String = "$messageId|$partId|$seq"

private fun List<com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock>.timelineText(): String {
    return mapNotNull { it.text?.takeIf { value -> value.isNotBlank() } }.joinToString("\n\n")
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
