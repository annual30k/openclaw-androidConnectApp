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
import kotlin.math.abs

private const val ordinalSeqTrustWindowSeconds = 10 * 60.0
private const val assistantDuplicateWindowSeconds = 15.0
private const val sameRunTranscriptOrderWindowSeconds = 15 * 60.0

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
    val attachmentIds: List<String> = emptyList()
)

internal data class TimelineReconcileResult(
    val messages: List<ChatMessage>,
    val pending: List<ChatMessage>
)

private data class CanonicalTimelineEntry(
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
    val source: String
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

private fun TimelineSnapshotMessage.toEntry(defaultSessionKey: String): CanonicalTimelineEntry {
    val session = sessionKey.clean() ?: defaultSessionKey
    val hash = timelineContentHash(content)
    val authoritativeMessageId = serverMessageId.clean()
    val authoritativeSeq = conversationSeq ?: seq
    val identity = stableKey?.clean()?.let {
        TimelineStableIdentity(it, TimelineIdentitySource.MessageId)
    } ?: authoritativeMessageId?.let {
        TimelineStableIdentity("$session:server:$it", TimelineIdentitySource.MessageId)
    } ?: stableTimelineKey(
        sessionKey = session,
        messageId = authoritativeMessageId ?: messageId,
        seq = authoritativeSeq,
        runId = runId,
        turnId = turnId,
        role = role,
        partId = partId,
        clientMessageId = clientMessageId,
        idempotencyKey = idempotencyKey,
        createdAt = createdAt,
        contentHash = hash
    )
    return CanonicalTimelineEntry(
        sessionKey = session,
        stableKey = identity.stableKey,
        messageId = authoritativeMessageId ?: messageId.clean() ?: clientMessageId.clean() ?: idempotencyKey.clean() ?: runId.clean() ?: identity.stableKey,
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
        content = content,
        attachmentIds = attachmentIds(content, attachmentIds),
        source = if (messageState.toState() == MessageState.pending) "local" else "history"
    )
}

internal fun timelineSnapshotMessageToChatMessage(
    message: TimelineSnapshotMessage,
    sessionKey: String = message.sessionKey ?: "main"
): ChatMessage {
    return message.toEntry(sessionKey).toChatMessage()
}

private fun ChatMessage.toEntry(sessionKey: String): CanonicalTimelineEntry {
    val identity = stableTimelineKey(sessionKey, this)
    val canonicalContent = contentBlocks.ifEmpty {
        content.trim().takeIf { it.isNotEmpty() }?.let {
            listOf(RelayChatContentBlock(type = "text", text = it))
        } ?: emptyList()
    }
    return CanonicalTimelineEntry(
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
        source = if (state == MessageState.pending || state == MessageState.streaming || isLocalTimelineMessageId(id)) "local" else "history"
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
        timelinePartId = partId.orEmpty()
    )
}

private fun ChatMessage.localClientId(): String? {
    return runId.removePrefix("local-user-").takeIf { it != runId && it.isNotBlank() }
}

private fun String.toEpochSeconds(): Double? {
    return runCatching { Instant.parse(this).toEpochMilli().toDouble() / 1000.0 }.getOrNull()
}

private enum class TimelineSeqDomain {
    Ordinal,
    Millis,
    Micros,
    Other
}

private fun timelineSeqDomain(seq: Long?): TimelineSeqDomain? {
    if (seq == null) return null
    val value = kotlin.math.abs(seq.toDouble())
    return when {
        value < 1_000_000_000.0 -> TimelineSeqDomain.Ordinal
        value >= 100_000_000_000.0 && value < 100_000_000_000_000.0 -> TimelineSeqDomain.Millis
        value >= 100_000_000_000_000.0 -> TimelineSeqDomain.Micros
        else -> TimelineSeqDomain.Other
    }
}

private fun identitiesMatch(left: CanonicalTimelineEntry, right: CanonicalTimelineEntry): Boolean {
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
    if (sameLiveHistoryAssistantDuplicate(left, right)) return true
    return false
}

private fun pureText(entry: CanonicalTimelineEntry): String? {
    if (entry.content.size != 1) return null
    val block = entry.content.single()
    if (block.type != "text") return null
    return block.text?.trim()?.takeIf { it.isNotEmpty() }
}

private fun shouldCollapseAssistantFragment(left: CanonicalTimelineEntry, right: CanonicalTimelineEntry): Boolean {
    if (left.role != MessageRole.assistant || right.role != MessageRole.assistant) return false
    if (left.state != MessageState.completed || right.state != MessageState.completed) return false
    val leftText = pureText(left) ?: return false
    val rightText = pureText(right) ?: return false
    if (leftText == rightText) return false
    if (!leftText.contains(rightText) && !rightText.contains(leftText)) return false
    val leftTime = left.createdAt.toEpochSeconds() ?: return false
    val rightTime = right.createdAt.toEpochSeconds() ?: return false
    return abs(leftTime - rightTime) <= 180.0
}

private fun collapseAssistantFragments(entries: List<CanonicalTimelineEntry>): List<CanonicalTimelineEntry> {
    val result = mutableListOf<CanonicalTimelineEntry>()
    entries.forEach { entry ->
        val index = result.indexOfFirst { shouldCollapseAssistantFragment(it, entry) }
        if (index < 0) {
            result += entry
        } else {
            val existing = result[index]
            result[index] = if ((pureText(entry)?.length ?: 0) > (pureText(existing)?.length ?: 0)) entry else existing
        }
    }
    return result
}

internal fun compareSameRunTranscriptOrderFields(
    leftRunId: String?,
    rightRunId: String?,
    leftRole: MessageRole,
    rightRole: MessageRole,
    leftSortTimestamp: Double?,
    rightSortTimestamp: Double?
): Int {
    val leftNormalizedRunId = normalizedTranscriptRunId(leftRunId) ?: return 0
    val rightNormalizedRunId = normalizedTranscriptRunId(rightRunId) ?: return 0
    if (leftNormalizedRunId != rightNormalizedRunId) return 0
    if (leftSortTimestamp != null &&
        rightSortTimestamp != null &&
        abs(leftSortTimestamp - rightSortTimestamp) > sameRunTranscriptOrderWindowSeconds
    ) {
        return 0
    }

    val leftRank = sameRunTranscriptRoleRank(leftRole) ?: return 0
    val rightRank = sameRunTranscriptRoleRank(rightRole) ?: return 0
    if (leftRank == rightRank) return 0
    return leftRank.compareTo(rightRank)
}

private fun normalizedTranscriptRunId(rawRunId: String?): String? {
    val trimmed = rawRunId?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    return if (trimmed.startsWith("local-user-")) {
        trimmed.removePrefix("local-user-").trim().takeIf { it.isNotEmpty() }
    } else {
        trimmed
    }
}

private fun sameRunTranscriptRoleRank(role: MessageRole): Int? {
    return when (role) {
        MessageRole.user -> 0
        MessageRole.tool -> 1
        MessageRole.assistant -> 2
        MessageRole.system -> null
    }
}

private fun compareEntries(left: CanonicalTimelineEntry, right: CanonicalTimelineEntry): Int {
    if (localPendingTimelineOrder(left, right)) return -1
    if (localPendingTimelineOrder(right, left)) return 1
    val sameRunOrder = compareSameRunTranscriptOrderFields(
        leftRunId = left.runId,
        rightRunId = right.runId,
        leftRole = left.role,
        rightRole = right.role,
        leftSortTimestamp = left.sortTimestamp,
        rightSortTimestamp = right.sortTimestamp
    )
    if (sameRunOrder != 0) return sameRunOrder
    if (left.conversationSeq != null && right.conversationSeq != null && left.conversationSeq != right.conversationSeq) {
        return left.conversationSeq.compareTo(right.conversationSeq)
    }

    val leftSeqDomain = timelineSeqDomain(left.seq)
    val rightSeqDomain = timelineSeqDomain(right.seq)
    val sameSeqDomain = leftSeqDomain != null && leftSeqDomain == rightSeqDomain
    val leftTime = left.sortTimestamp ?: left.createdAt.toEpochSeconds()
    val rightTime = right.sortTimestamp ?: right.createdAt.toEpochSeconds()
    if (shouldPreferSeqOrder(leftSeqDomain, sameSeqDomain, left.seq, right.seq, leftTime, rightTime)) {
        return left.seq!!.compareTo(right.seq!!)
    }
    if (leftTime != null && rightTime != null && leftTime != rightTime) {
        return leftTime.compareTo(rightTime)
    }
    if (leftTime != null && rightTime == null) return -1
    if (leftTime == null && rightTime != null) return 1
    if (!sameSeqDomain && left.seq != null && right.seq != null && left.seq != right.seq) {
        return left.seq.compareTo(right.seq)
    }
    if (!sameSeqDomain && left.seq != null && right.seq == null) return -1
    if (!sameSeqDomain && left.seq == null && right.seq != null) return 1
    if (shouldPreferTurnSeqOrder(left.turnSeq, right.turnSeq, leftTime, rightTime)) return left.turnSeq!!.compareTo(right.turnSeq!!)
    val timeCompare = (left.createdAt.toEpochSeconds() ?: 0.0).compareTo(right.createdAt.toEpochSeconds() ?: 0.0)
    if (timeCompare != 0) return timeCompare
    val roleCompare = timelineRoleOrder(left.role).compareTo(timelineRoleOrder(right.role))
    if (roleCompare != 0) return roleCompare
    val partCompare = (left.partId ?: "").compareTo(right.partId ?: "")
    if (partCompare != 0) return partCompare
    return left.stableKey.compareTo(right.stableKey)
}

private fun shouldPreferSeqOrder(
    domain: TimelineSeqDomain?,
    sameSeqDomain: Boolean,
    leftSeq: Long?,
    rightSeq: Long?,
    leftTime: Double?,
    rightTime: Double?
): Boolean {
    if (!sameSeqDomain || leftSeq == null || rightSeq == null || leftSeq == rightSeq) return false
    if (domain == TimelineSeqDomain.Millis || domain == TimelineSeqDomain.Micros) return true
    if (leftTime == null || rightTime == null) return true
    return abs(leftTime - rightTime) <= ordinalSeqTrustWindowSeconds
}

private fun shouldPreferTurnSeqOrder(
    leftTurnSeq: Long?,
    rightTurnSeq: Long?,
    leftTime: Double?,
    rightTime: Double?
): Boolean {
    if (leftTurnSeq == null || rightTurnSeq == null || leftTurnSeq == rightTurnSeq) return false
    if (leftTime == null || rightTime == null) return true
    return abs(leftTime - rightTime) <= ordinalSeqTrustWindowSeconds
}

private fun sameLiveHistoryAssistantDuplicate(left: CanonicalTimelineEntry, right: CanonicalTimelineEntry): Boolean {
    if (left.role != MessageRole.assistant || right.role != MessageRole.assistant) return false
    if (left.state != MessageState.completed || right.state != MessageState.completed) return false
    val leftText = pureText(left) ?: return false
    val rightText = pureText(right) ?: return false
    if (leftText != rightText) return false
    val oneLocal = isLocalTimelineMessage(left) || isLocalTimelineMessage(right)
    val oneHistory = isHistoryTimelineMessage(left) || isHistoryTimelineMessage(right)
    if (!oneLocal || !oneHistory) return false
    val leftTime = left.sortTimestamp ?: left.createdAt.toEpochSeconds() ?: return false
    val rightTime = right.sortTimestamp ?: right.createdAt.toEpochSeconds() ?: return false
    return abs(leftTime - rightTime) <= assistantDuplicateWindowSeconds
}

private fun isLocalTimelineMessage(entry: CanonicalTimelineEntry): Boolean {
    return entry.source == "local" || isLocalTimelineMessageId(entry.messageId)
}

private fun isHistoryTimelineMessage(entry: CanonicalTimelineEntry): Boolean {
    return entry.source == "history" || entry.messageId.startsWith("history:")
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

private fun timelineRoleOrder(role: MessageRole): Int {
    return when (role) {
        MessageRole.user -> 0
        MessageRole.assistant -> 1
        MessageRole.tool -> 2
        MessageRole.system -> 3
    }
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
    val pendingEntries = (existingPending + pending).map { it.toEntry(snapshot.sessionKey) }
    val incoming = snapshot.messages.map { it.toEntry(snapshot.sessionKey) }
    val incomingKeys = incoming.mapTo(mutableSetOf()) { it.stableKey }
    val deletedIds = snapshot.deletedMessageIds.toSet()
    val byKey = linkedMapOf<String, CanonicalTimelineEntry>()

    existingConfirmed.map { it.toEntry(snapshot.sessionKey) }.forEach { entry ->
        if (snapshot.range.contains(entry.seq) && entry.stableKey !in incomingKeys) {
            if (entry.messageId in deletedIds) {
                byKey[entry.stableKey] = deletedTombstone(entry)
            }
        } else {
            byKey[entry.stableKey] = entry
        }
    }

    incoming.forEach { entry ->
        val matchingKey = byKey.entries.firstOrNull { identitiesMatch(it.value, entry) }?.key
        if (matchingKey != null && matchingKey != entry.stableKey) byKey.remove(matchingKey)
        byKey[entry.stableKey] = entry
    }

    val remainingPending = pendingEntries.filter { pendingEntry ->
        incoming.none { incomingEntry -> identitiesMatch(pendingEntry, incomingEntry) }
    }

    val confirmed = collapseAssistantFragments(byKey.values.toList())
        .sortedWith(::compareEntries)
        .map { it.toChatMessage() }
    return TimelineReconcileResult(
        messages = confirmed,
        pending = remainingPending.sortedWith(::compareEntries).map { it.toChatMessage() }
    )
}

internal fun sortTimelineMessagesV3(messages: List<ChatMessage>, sessionKey: String = "main"): List<ChatMessage> {
    return messages
        .map { it.toEntry(sessionKey) }
        .sortedWith(::compareEntries)
        .map { it.toChatMessage() }
}
