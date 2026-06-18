package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import java.time.Instant

@Serializable
internal data class TimelineSnapshotPage(
    val sessionKey: String = "main",
    val snapshotRevision: String? = null,
    val rangeStartCursor: String? = null,
    val rangeEndCursor: String? = null,
    val newestCursor: String? = null,
    val oldestCursor: String? = null,
    val deletedMessageIds: List<String> = emptyList(),
    val messages: List<TimelineSnapshotMessage> = emptyList()
) {
    val range: TimelineSnapshotRange
        get() = TimelineSnapshotRange.fromCursors(rangeStartCursor, rangeEndCursor)

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            isLenient = true
        }

        fun fromJsonElement(element: JsonElement): TimelineSnapshotPage? {
            return try {
                json.decodeFromJsonElement<TimelineSnapshotPage>(element)
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}

@Serializable
internal data class TimelineSnapshotMessage @OptIn(ExperimentalSerializationApi::class) constructor(
    val sessionKey: String? = null,
    val stableKey: String? = null,
    val serverMessageId: String? = null,
    val conversationSeq: Long? = null,
    val messageId: String? = null,
    val seq: Long? = null,
    val turnSeq: Long? = null,
    val role: String = "assistant",
    val messageState: String = "completed",
    val runId: String? = null,
    val turnId: String? = null,
    val partId: String? = null,
    val clientMessageId: String? = null,
    val idempotencyKey: String? = null,
    val createdAt: String = "",
    val content: List<RelayChatContentBlock> = emptyList(),
    val attachmentIds: List<String> = emptyList(),
    val timelineOrderKey: String? = null,
    val timelineIdentityKey: String? = null,
    val timelineItemKind: String? = null,
    val timelineResolvesWaiting: Boolean? = null
)

internal data class TimelineReconcileResult(
    val messages: List<ChatMessage>,
    val pending: List<ChatMessage>
)

private data class CanonicalTimelineEntry(
    val originalIndex: Int = 0,
    val sessionKey: String,
    val stableKey: String,
    val messageId: String,
    val conversationSeq: Long?,
    val seq: Long?,
    val sortTimestamp: Double?,
    val turnSeq: Long?,
    val role: MessageRole,
    val state: MessageState,
    val runId: String?,
    val turnId: String?,
    val partId: String?,
    val clientMessageId: String?,
    val idempotencyKey: String?,
    val createdAt: String,
    val content: List<RelayChatContentBlock>,
    val attachmentIds: List<String>,
    val source: String,
    val timelineOrderKey: String,
    val timelineIdentityKey: String,
    val timelineItemKind: String,
    val timelineResolvesWaiting: Boolean?
)

private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

private fun String.toRole(): MessageRole = when (trim().lowercase()) {
    "user" -> MessageRole.user
    "system" -> MessageRole.system
    "tool" -> MessageRole.tool
    else -> MessageRole.assistant
}

private fun String.toState(): MessageState = when (trim().lowercase()) {
    "pending", "local_created", "pending_upload", "pending_send", "sent_to_relay" -> MessageState.pending
    "streaming", "server_confirmed" -> MessageState.streaming
    "failed", "aborted" -> MessageState.failed
    "deleted" -> MessageState.deleted
    "recalled" -> MessageState.recalled
    else -> MessageState.completed
}

private fun displayText(blocks: List<RelayChatContentBlock>): String {
    return blocks
        .filter { it.type == "text" || it.text?.isNotBlank() == true }
        .mapNotNull { it.text?.trim()?.takeIf(String::isNotEmpty) }
        .joinToString("\n\n")
}

private fun attachmentIds(blocks: List<RelayChatContentBlock>, explicit: List<String>): List<String> {
    return (explicit + blocks.mapNotNull { it.stableAttachmentId })
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
}

private fun TimelineSnapshotMessage.toEntryOrNull(defaultSessionKey: String, originalIndex: Int = 0): CanonicalTimelineEntry? {
    val session = sessionKey.clean() ?: defaultSessionKey
    val sanitizedContent = sanitizeChatContentBlocks(content)
    val authoritativeMessageId = serverMessageId.clean()
    val authoritativeSeq = conversationSeq ?: seq
    val canonicalIdentityKey = timelineIdentityKey.clean() ?: return null
    val canonicalOrderKey = timelineOrderKey.clean() ?: return null
    val canonicalItemKind = timelineItemKind.clean() ?: return null
    return CanonicalTimelineEntry(
        originalIndex = originalIndex,
        sessionKey = session,
        stableKey = canonicalIdentityKey,
        messageId = authoritativeMessageId ?: messageId.clean() ?: clientMessageId.clean() ?: idempotencyKey.clean() ?: runId.clean() ?: canonicalIdentityKey,
        conversationSeq = conversationSeq,
        seq = authoritativeSeq,
        // Always use epoch-seconds for sortTimestamp so it's comparable with local user
        // messages (which also use epoch-seconds). Seq is preserved separately for
        // tie-breaking between history messages that share the same createdAt.
        sortTimestamp = createdAt.toEpochSeconds() ?: seq?.toDouble(),
        turnSeq = turnSeq,
        role = role.toRole(),
        state = messageState.toState(),
        runId = runId,
        turnId = turnId,
        partId = partId,
        clientMessageId = clientMessageId,
        idempotencyKey = idempotencyKey,
        createdAt = createdAt,
        content = sanitizedContent,
        attachmentIds = attachmentIds(sanitizedContent, attachmentIds),
        source = if (messageState.toState() == MessageState.pending) "local" else "history",
        timelineOrderKey = canonicalOrderKey,
        timelineIdentityKey = canonicalIdentityKey,
        timelineItemKind = canonicalItemKind,
        timelineResolvesWaiting = timelineResolvesWaiting
    )
}

internal fun timelineSnapshotMessageToChatMessage(
    message: TimelineSnapshotMessage,
    sessionKey: String = message.sessionKey ?: "main"
): ChatMessage {
    return requireNotNull(message.toEntryOrNull(sessionKey)) {
        "Timeline snapshot message is missing canonical timeline keys"
    }.toChatMessage()
}

private fun ChatMessage.toEntry(sessionKey: String, originalIndex: Int = 0): CanonicalTimelineEntry {
    val canonicalIdentityKey = timelineIdentityKey.clean()
    val canonicalOrderKey = timelineOrderKey.clean()
    val identity = canonicalIdentityKey?.let { TimelineStableIdentity(it, TimelineIdentitySource.MessageId) }
        ?: stableTimelineKey(sessionKey, this)
    val canonicalContent = sanitizeChatContentBlocks(contentBlocks).ifEmpty {
        sanitizeChatMessageText(content).takeIf { it.isNotEmpty() }?.let {
            listOf(RelayChatContentBlock(type = "text", text = it))
        } ?: emptyList()
    }
    return CanonicalTimelineEntry(
        originalIndex = originalIndex,
        sessionKey = sessionKey,
        stableKey = identity.stableKey,
        messageId = timelineMessageId.clean() ?: id,
        conversationSeq = null,
        seq = seq,
        sortTimestamp = sortTimestamp,
        turnSeq = turnSeq,
        role = role,
        state = state,
        runId = runId.clean(),
        turnId = null,
        partId = timelinePartId.clean(),
        clientMessageId = localClientId() ?: id.takeIf { state == MessageState.pending || state == MessageState.streaming },
        idempotencyKey = null,
        createdAt = createdAt,
        content = canonicalContent,
        attachmentIds = attachmentIds(canonicalContent, emptyList()),
        source = if (state == MessageState.pending || state == MessageState.streaming || isLocalTimelineMessageId(id)) "local" else "history",
        timelineOrderKey = canonicalOrderKey.orEmpty(),
        timelineIdentityKey = canonicalIdentityKey.orEmpty(),
        timelineItemKind = timelineItemKind.clean().orEmpty(),
        timelineResolvesWaiting = timelineResolvesWaiting
    )
}

private fun CanonicalTimelineEntry.toChatMessage(): ChatMessage {
    return ChatMessage(
        id = messageId,
        role = role,
        state = state,
        content = displayText(content),
        contentBlocks = content,
        createdAt = createdAt,
        runId = runId.clean() ?: clientMessageId.clean() ?: messageId,
        sortTimestamp = sortTimestamp ?: createdAt.toEpochSeconds() ?: seq?.toDouble(),
        seq = seq,
        turnSeq = turnSeq,
        timelineStableKey = stableKey,
        timelineMessageId = messageId,
        timelinePartId = partId.orEmpty(),
        timelineOrderKey = timelineOrderKey,
        timelineIdentityKey = timelineIdentityKey,
        timelineItemKind = timelineItemKind,
        timelineResolvesWaiting = timelineResolvesWaiting
    )
}

private fun ChatMessage.localClientId(): String? {
    return runId.removePrefix("local-user-").takeIf { it != runId && it.isNotBlank() }
}

private fun String.toEpochSeconds(): Double? {
    return runCatching { Instant.parse(this).toEpochMilli().toDouble() / 1000.0 }.getOrNull()
}

private fun identitiesMatch(left: CanonicalTimelineEntry, right: CanonicalTimelineEntry): Boolean {
    val leftHasCanonicalIdentity = left.timelineIdentityKey.isNotBlank()
    val rightHasCanonicalIdentity = right.timelineIdentityKey.isNotBlank()
    if (leftHasCanonicalIdentity || rightHasCanonicalIdentity) {
        return leftHasCanonicalIdentity &&
            rightHasCanonicalIdentity &&
            left.timelineIdentityKey == right.timelineIdentityKey
    }
    if (left.stableKey == right.stableKey) return true
    if (left.messageId.isNotBlank() && left.messageId == right.messageId) return true
    if (left.clientMessageId != null && left.clientMessageId == right.clientMessageId) return true
    if (left.idempotencyKey != null && left.idempotencyKey == right.idempotencyKey) return true
    if (left.runId != null &&
        left.runId == right.runId &&
        left.partId != null &&
        left.partId == right.partId &&
        left.role == right.role
    ) {
        return true
    }
    val leftCleanRun = left.runId?.replace("local-user-", "")
    val rightCleanRun = right.runId?.replace("local-user-", "")
    if (!leftCleanRun.isNullOrEmpty() && leftCleanRun == rightCleanRun && left.role == right.role) return true
    if (left.attachmentIds.isNotEmpty() &&
        right.attachmentIds.isNotEmpty() &&
        left.attachmentIds.any { it in right.attachmentIds }
    ) {
        return true
    }
    if (left.runId != null &&
        left.runId == right.runId &&
        left.role == MessageRole.assistant &&
        right.role == MessageRole.assistant &&
        left.state == MessageState.streaming
    ) {
        return true
    }
    return false
}

private fun compareEntries(left: CanonicalTimelineEntry, right: CanonicalTimelineEntry): Int {
    val leftOrderKey = relayTimelineOrderKey(left)
    val rightOrderKey = relayTimelineOrderKey(right)
    if (localPendingTimelineOrder(left, right)) return -1
    if (localPendingTimelineOrder(right, left)) return 1
    if (leftOrderKey != null && rightOrderKey != null) {
        val orderCompare = leftOrderKey.compareTo(rightOrderKey)
        if (orderCompare != 0) return orderCompare
        val identityCompare = left.timelineIdentityKey.compareTo(right.timelineIdentityKey)
        if (identityCompare != 0) return identityCompare
        return left.timelineItemKind.compareTo(right.timelineItemKind)
    }
    if (pendingWaitingOverlayOrder(left, right)) return 1
    if (pendingWaitingOverlayOrder(right, left)) return -1
    if (sameTurnToolBeforeAssistant(left, right)) return -1
    if (sameTurnToolBeforeAssistant(right, left)) return 1

    if (leftOrderKey != null && rightOrderKey == null) return -1
    if (leftOrderKey == null && rightOrderKey != null) return 1

    val inputCompare = left.originalIndex.compareTo(right.originalIndex)
    if (inputCompare != 0) return inputCompare
    return left.stableKey.compareTo(right.stableKey)
}

private fun relayTimelineOrderKey(entry: CanonicalTimelineEntry): String? {
    val orderKey = entry.timelineOrderKey.trim()
    if (orderKey.isEmpty() ||
        orderKey.startsWith("local:") ||
        entry.timelineIdentityKey.isBlank() ||
        entry.timelineItemKind.isBlank()
    ) {
        return null
    }
    return orderKey
}

private fun pendingWaitingOverlayOrder(left: CanonicalTimelineEntry, right: CanonicalTimelineEntry): Boolean {
    if (!isTransientAssistantTimelinePlaceholder(left)) return false
    if (normalizedTurnIdentity(left) != normalizedTurnIdentity(right)) return false
    return right.role == MessageRole.tool || right.role == MessageRole.assistant
}

private fun sameTurnToolBeforeAssistant(left: CanonicalTimelineEntry, right: CanonicalTimelineEntry): Boolean {
    if (left.role != MessageRole.tool || right.role != MessageRole.assistant) return false
    if (isTransientAssistantTimelinePlaceholder(right)) return false
    val leftTurn = normalizedTurnIdentity(left)
    return leftTurn.isNotEmpty() && leftTurn == normalizedTurnIdentity(right)
}

private fun normalizedTurnIdentity(entry: CanonicalTimelineEntry): String {
    val turnId = entry.turnId?.trim().orEmpty()
    if (turnId.isNotEmpty()) return turnId
    val runId = entry.runId?.trim().orEmpty()
    return runId
        .removePrefix("local-user-")
        .removePrefix("user-")
        .replace(Regex(":(user|assistant|tool|system|waiting)$", RegexOption.IGNORE_CASE), "")
        .trim()
}

private fun isLocalTimelineMessageId(id: String): Boolean {
    return id.startsWith("local:") ||
        Regex("^assistant-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE).matches(id) ||
        Regex("^tool-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE).matches(id)
}

private fun localPendingTimelineOrder(left: CanonicalTimelineEntry, right: CanonicalTimelineEntry): Boolean {
    val leftRunId = left.runId?.trim().orEmpty()
    if (left.role != MessageRole.user) return false
    val clientRunId = leftRunId.removePrefix("local-user-").trim()
    if (clientRunId.isNotEmpty() && right.role in setOf(MessageRole.assistant, MessageRole.tool)) {
        val rightTurn = normalizedTurnIdentity(right)
        if (rightTurn.isEmpty() || rightTurn == clientRunId || right.runId?.trim() == clientRunId) {
            return true
        }
    }
    if (clientRunId.isNotEmpty() &&
        relayTimelineOrderKey(left) == null &&
        right.role == MessageRole.tool
    ) {
        return true
    }
    if (relayTimelineOrderKey(left) == null &&
        relayTimelineOrderKey(right) == null &&
        (right.role == MessageRole.assistant || right.role == MessageRole.tool)
    ) {
        return true
    }
    return clientRunId.isNotEmpty() &&
        right.role == MessageRole.assistant &&
        right.runId?.trim() == clientRunId
}

private fun isTransientAssistantTimelinePlaceholder(entry: CanonicalTimelineEntry): Boolean {
    if (entry.role != MessageRole.assistant || entry.state !in setOf(MessageState.streaming, MessageState.pending)) return false
    if (entry.content.any { it.type in setOf("file", "image", "voice", "audio", "tool", "tool_result") }) return false
    val text = displayText(entry.content)
    return text.isBlank() || isTransientAssistantPlaceholderContent(text)
}

private fun deletedTombstone(entry: CanonicalTimelineEntry): CanonicalTimelineEntry {
    return entry.copy(state = MessageState.deleted, content = emptyList(), attachmentIds = emptyList())
}

internal fun reconcileTimeline(
    existing: List<ChatMessage>,
    pending: List<ChatMessage> = emptyList(),
    snapshot: TimelineSnapshotPage
): TimelineReconcileResult {
    val (existingConfirmed, existingPending) = TimelinePendingOverlay.splitPending(existing)
    val pendingEntries = (existingPending + pending).mapIndexed { index, message ->
        message.toEntry(snapshot.sessionKey, index)
    }
    val incoming = snapshot.messages.mapIndexedNotNull { index, message ->
        message.toEntryOrNull(snapshot.sessionKey, index)
    }
    return reconcileCanonicalTimeline(existingConfirmed, pendingEntries, incoming, snapshot)
}

private fun reconcileCanonicalTimeline(
    existingConfirmed: List<ChatMessage>,
    pendingEntries: List<CanonicalTimelineEntry>,
    incomingCanonical: List<CanonicalTimelineEntry>,
    snapshot: TimelineSnapshotPage
): TimelineReconcileResult {
    val incomingIdentities = incomingCanonical.mapTo(mutableSetOf()) { it.timelineIdentityKey }
    val deletedIds = snapshot.deletedMessageIds.toSet()
    val byIdentity = linkedMapOf<String, CanonicalTimelineEntry>()
    val range = snapshot.range

    existingConfirmed
        .mapIndexed { index, message -> message.toEntry(snapshot.sessionKey, index) }
        .filter { it.timelineIdentityKey.isNotBlank() && it.timelineItemKind.isNotBlank() && relayTimelineOrderKey(it) != null }
        .filter { range.isBounded && !range.contains(it.seq ?: it.conversationSeq) }
        .forEach { entry ->
            if (entry.messageId in deletedIds || entry.timelineIdentityKey in deletedIds) {
                byIdentity[entry.timelineIdentityKey] = deletedTombstone(entry)
            } else if (entry.timelineIdentityKey !in incomingIdentities) {
                byIdentity[entry.timelineIdentityKey] = entry
            }
        }

    incomingCanonical.forEach { entry ->
        byIdentity[entry.timelineIdentityKey] = entry
    }

    val pendingTurnIds = pendingEntries
        .filter { it.role == MessageRole.assistant && it.state in setOf(MessageState.pending, MessageState.streaming) }
        .map { normalizedTurnIdentity(it) }
        .filter { it.isNotBlank() }
        .toSet()
    val remainingPending = pendingEntries
        .filter { pendingEntry ->
            incomingCanonical.none { incomingEntry -> pendingResolvedByCanonical(pendingEntry, incomingEntry) }
        }
        .filter { pendingEntry ->
            if (pendingEntry.role != MessageRole.user || pendingEntry.runId?.trim()?.startsWith("local-user-") != true) {
                return@filter true
            }
            val turnIdentity = normalizedTurnIdentity(pendingEntry)
            turnIdentity.isNotBlank() && turnIdentity in pendingTurnIds
        }

    return TimelineReconcileResult(
        messages = byIdentity.values.sortedWith(::compareEntries).map { it.toChatMessage() },
        pending = remainingPending.sortedWith(::compareEntries).map { it.toChatMessage() }
    )
}

private fun pendingResolvedByCanonical(
    pending: CanonicalTimelineEntry,
    canonical: CanonicalTimelineEntry
): Boolean {
    if (pending.role != canonical.role) return false
    if (pending.role == MessageRole.assistant && !canonical.clearsWaitingAssistantTimelineItem()) return false
    val pendingClientId = pending.clientMessageId?.trim().orEmpty()
    if (pendingClientId.isNotEmpty() &&
        (pendingClientId == canonical.clientMessageId || pendingClientId == canonical.idempotencyKey)
    ) {
        return true
    }
    val pendingRunId = pending.runId?.removePrefix("local-user-")?.trim().orEmpty()
    val canonicalRunId = canonical.runId?.trim().orEmpty()
    if (pendingRunId.isNotEmpty() && pendingRunId == canonicalRunId) return true
    if (pending.attachmentIds.isNotEmpty() &&
        canonical.attachmentIds.isNotEmpty() &&
        pending.attachmentIds.any { it in canonical.attachmentIds }
    ) {
        return true
    }
    return false
}

private fun CanonicalTimelineEntry.isToolTimelineItem(): Boolean {
    if (role == MessageRole.tool) return true
    val kind = timelineItemKind.trim().lowercase()
    if (kind.contains("tool")) return true
    return content.any { it.isToolCallBlock || it.isToolResultBlock }
}

private fun CanonicalTimelineEntry.clearsWaitingAssistantTimelineItem(): Boolean {
    if (isAttachmentTimelineItem()) return true
    return isAssistantAnswerTimelineItem()
}

private fun CanonicalTimelineEntry.isAttachmentTimelineItem(): Boolean {
    return timelineItemKind.trim().equals("attachment", ignoreCase = true)
}

private fun CanonicalTimelineEntry.isAssistantAnswerTimelineItem(): Boolean {
    if (role != MessageRole.assistant) return false
    if (isToolTimelineItem()) return false
    if (content.any { it.isFileBlock || it.isVoiceMessageBlock }) return false
    val text = displayText(content)
    return text.isNotBlank() && !isTransientAssistantPlaceholderContent(text)
}

internal fun sortTimelineMessagesV3(messages: List<ChatMessage>, sessionKey: String = "main"): List<ChatMessage> {
    return messages
        .mapIndexed { index, message -> message.toEntry(sessionKey, index) }
        .sortedWith(::compareEntries)
        .map { it.toChatMessage() }
}
