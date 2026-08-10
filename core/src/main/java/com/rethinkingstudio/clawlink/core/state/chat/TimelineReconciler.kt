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
    val source: String? = null,
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
    val displayId: String,
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
    val timelineResolvesWaiting: Boolean?,
    val deliveryState: String = "",
    val clientMessageText: String? = null,
    val queuePosition: Long? = null,
    val localTurnOrder: Long? = null
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
    val textBlockContent = blocks
        .filter { it.isTextBlock }
        .mapNotNull { it.text?.trim()?.takeIf(String::isNotEmpty) }
        .joinToString("\n\n")
    if (textBlockContent.isNotEmpty()) return textBlockContent
    return blocks
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
        displayId = authoritativeMessageId ?: messageId.clean() ?: clientMessageId.clean() ?: idempotencyKey.clean() ?: runId.clean() ?: canonicalIdentityKey,
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
        source = source.clean() ?: if (messageState.toState() == MessageState.pending) "local" else "history",
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

private fun ChatMessage.toEntry(sessionKey: String, originalIndex: Int = 0): CanonicalTimelineEntry? {
    val canonicalIdentityKey = timelineIdentityKey.clean()
    val canonicalOrderKey = timelineOrderKey.clean()
    val identity = canonicalIdentityKey?.let { TimelineStableIdentity(it, TimelineIdentitySource.MessageId) }
        ?: stableTimelineKey(sessionKey, this)
        ?: return null
    val canonicalContent = canonicalContentForTimelineEntry()
    return CanonicalTimelineEntry(
        originalIndex = originalIndex,
        sessionKey = sessionKey,
        stableKey = identity.stableKey,
        messageId = timelineMessageId.clean() ?: id,
        displayId = id,
        conversationSeq = null,
        seq = seq,
        sortTimestamp = sortTimestamp,
        turnSeq = turnSeq,
        role = role,
        state = state,
        runId = runId.clean(),
        turnId = turnId.clean(),
        partId = timelinePartId.clean(),
        clientMessageId = clientMessageId.clean() ?: localClientId() ?: id.takeIf { state == MessageState.pending || state == MessageState.streaming },
        idempotencyKey = idempotencyKey.clean(),
        createdAt = createdAt,
        content = canonicalContent,
        attachmentIds = attachmentIds(canonicalContent, emptyList()),
        source = source.clean()
            ?: if (state == MessageState.pending || state == MessageState.streaming || isLocalTimelineMessageId(id)) "local" else "history",
        timelineOrderKey = canonicalOrderKey.orEmpty(),
        timelineIdentityKey = canonicalIdentityKey.orEmpty(),
        timelineItemKind = timelineItemKind.clean().orEmpty(),
        timelineResolvesWaiting = timelineResolvesWaiting,
        deliveryState = deliveryState,
        clientMessageText = clientMessageText,
        queuePosition = queuePosition,
        localTurnOrder = localTurnOrder
    )
}

private fun CanonicalTimelineEntry.toChatMessage(): ChatMessage {
    return ChatMessage(
        id = displayId,
        role = role,
        state = state,
        content = displayText(content),
        contentBlocks = content,
        createdAt = createdAt,
        runId = runId.clean() ?: clientMessageId.clean() ?: messageId,
        turnId = turnId.orEmpty(),
        clientMessageId = clientMessageId.orEmpty(),
        idempotencyKey = idempotencyKey.orEmpty(),
        sortTimestamp = sortTimestamp ?: createdAt.toEpochSeconds() ?: seq?.toDouble(),
        seq = seq,
        turnSeq = turnSeq,
        timelineStableKey = stableKey,
        timelineMessageId = messageId,
        timelinePartId = partId.orEmpty(),
        timelineOrderKey = timelineOrderKey,
        timelineIdentityKey = timelineIdentityKey,
        timelineItemKind = timelineItemKind,
        timelineResolvesWaiting = timelineResolvesWaiting,
        source = source,
        deliveryState = deliveryState,
        clientMessageText = clientMessageText,
        queuePosition = queuePosition,
        localTurnOrder = localTurnOrder
    )
}

private fun ChatMessage.localClientId(): String? {
    return runId.removePrefix("local-user-").takeIf { it != runId && it.isNotBlank() }
}

private fun ChatMessage.canonicalContentForTimelineEntry(): List<RelayChatContentBlock> {
    val sanitizedBlocks = sanitizeChatContentBlocks(contentBlocks)
    val sanitizedText = sanitizeChatMessageText(content).takeIf { it.isNotEmpty() }
    if (sanitizedText == null) return sanitizedBlocks
    if (sanitizedBlocks.isEmpty()) return listOf(RelayChatContentBlock(type = "text", text = sanitizedText))
    if (role != MessageRole.user) return sanitizedBlocks
    // Structured text blocks are authoritative. `content` is only their display projection and,
    // for mixed text/media messages, may also contain the attachment label. Promoting that
    // projection back into a new text block on every reconciliation makes the message grow
    // exponentially across refreshes.
    if (sanitizedBlocks.any { it.isTextBlock && !it.text.isNullOrBlank() }) {
        return sanitizedBlocks
    }
    if (sanitizedText.isAttachmentLabelFor(sanitizedBlocks)) return sanitizedBlocks

    // 图片/文件 user 消息在本地回显、实时回显、历史 snapshot 间切换时，content 可能保存用户提示词，
    // 但 contentBlocks 只剩文件块。v3 对账必须把提示词提升为 text block，避免重排后正文降级成文件名或空白。
    return listOf(RelayChatContentBlock(type = "text", text = sanitizedText)) + sanitizedBlocks
}

private fun String.isAttachmentLabelFor(blocks: List<RelayChatContentBlock>): Boolean {
    val normalizedText = normalizedAttachmentLabel()
    if (normalizedText.isEmpty()) return false
    return blocks
        .filter { it.isFileBlock || it.isVoiceMessageBlock }
        .flatMap { block -> listOf(block.fileDisplayName, block.name, block.text, block.fileStatusText, block.voiceStatusText) }
        .any { label -> label?.normalizedAttachmentLabel() == normalizedText }
}

private fun String.normalizedAttachmentLabel(): String {
    return trim().replace(Regex("\\s+"), " ").lowercase()
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
    val leftCleanRun = normalizedTurnIdentityValue(left.runId)
    val rightCleanRun = normalizedTurnIdentityValue(right.runId)
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
    if (localUserBeforeUnconfirmedOutput(left, right, leftOrderKey, rightOrderKey)) return -1
    if (localUserBeforeUnconfirmedOutput(right, left, rightOrderKey, leftOrderKey)) return 1
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

private fun localUserBeforeUnconfirmedOutput(
    left: CanonicalTimelineEntry,
    right: CanonicalTimelineEntry,
    leftOrderKey: String?,
    rightOrderKey: String?
): Boolean {
    if (leftOrderKey != null || rightOrderKey != null) return false
    if (left.role != MessageRole.user || right.role !in setOf(MessageRole.assistant, MessageRole.tool)) return false
    if (left.runId?.trim()?.startsWith("local-user-") != true) return false
    return normalizedTurnIdentity(left).isNotEmpty() &&
        normalizedTurnIdentity(left) == normalizedTurnIdentity(right)
}

private fun CanonicalTimelineEntry.isLocalOverlayEntry(): Boolean {
    return timelineOrderKey.trim().startsWith("local:") ||
        source.trim().equals("local", ignoreCase = true) ||
        runId?.trim()?.startsWith("local-user-") == true ||
        isTransientAssistantTimelinePlaceholder(this)
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
    val turnId = normalizedTurnIdentityValue(entry.turnId)
    if (turnId.isNotEmpty()) return turnId
    val mediaSourceRunId = entry.content.firstNotNullOfOrNull { block ->
        if (block.isFileBlock || block.isVoiceMessageBlock) {
            normalizedTurnIdentityValue(block.sourceRunId).takeIf { it.isNotEmpty() }
        } else {
            null
        }
    }
    if (!mediaSourceRunId.isNullOrEmpty()) return mediaSourceRunId
    val runId = normalizedTurnIdentityValue(entry.runId)
    if (runId.isNotEmpty()) return runId
    return normalizedTurnIdentityValue(entry.clientMessageId).ifEmpty {
        normalizedTurnIdentityValue(entry.idempotencyKey)
    }
}

private fun turnIdentitySet(entry: CanonicalTimelineEntry): Set<String> {
    return buildList {
        entry.content.forEach { block -> add(block.sourceRunId) }
        add(entry.clientMessageId)
        add(entry.idempotencyKey)
        add(entry.turnId)
        add(entry.runId)
    }
        .map(::normalizedTurnIdentityValue)
        .filter(String::isNotEmpty)
        .map(String::lowercase)
        .toSet()
}

private fun normalizedTurnIdentityValue(value: String?): String {
    var normalized = value?.trim().orEmpty()
    if (normalized.isEmpty()) return ""
    normalized = normalized
        .removePrefix("local-user-")
        .removePrefix("user-")
        .trim()
    return normalized
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
    val pendingEntries = (existingPending + pending).mapIndexedNotNull { index, message ->
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
        .mapIndexedNotNull { index, message -> message.toEntry(snapshot.sessionKey, index) }
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
    if (pending.role == MessageRole.assistant &&
        !canonical.replacesStreamingAssistantWaiting(pending) &&
        !canonical.clearsWaitingAssistantTimelineItem()
    ) {
        return false
    }
    val pendingClientId = normalizedTurnIdentityValue(pending.clientMessageId)
    if (pendingClientId.isNotEmpty() &&
        listOf(canonical.clientMessageId, canonical.idempotencyKey, canonical.runId, canonical.turnId)
            .any { normalizedTurnIdentityValue(it) == pendingClientId }
    ) {
        return true
    }
    val pendingRunId = normalizedTurnIdentityValue(pending.runId)
    if (pendingRunId.isNotEmpty() &&
        listOf(canonical.runId, canonical.turnId, canonical.clientMessageId, canonical.idempotencyKey)
            .any { normalizedTurnIdentityValue(it) == pendingRunId }
    ) {
        return true
    }
    if (pending.attachmentIds.isNotEmpty() &&
        canonical.attachmentIds.isNotEmpty() &&
        pending.attachmentIds.any { it in canonical.attachmentIds }
    ) {
        return true
    }
    return false
}

private fun CanonicalTimelineEntry.replacesStreamingAssistantWaiting(
    pending: CanonicalTimelineEntry
): Boolean {
    if (!isTransientAssistantTimelinePlaceholder(pending)) return false
    if (role != MessageRole.assistant || state !in setOf(MessageState.pending, MessageState.streaming)) return false
    if (content.any { it.isFileBlock || it.isVoiceMessageBlock || it.isToolCallBlock || it.isToolResultBlock }) return false
    val pendingTurn = normalizedTurnIdentity(pending)
    // 服务端 streaming assistant 到达后必须替换同一 turn 的本地 waiting，占位合并只按规范化 run/turn 身份，不按文本或时间相似性判断。
    return pendingTurn.isNotEmpty() && pendingTurn == normalizedTurnIdentity(this)
}

private fun CanonicalTimelineEntry.isToolTimelineItem(): Boolean {
    if (role == MessageRole.tool) return true
    val kind = timelineItemKind.trim().lowercase()
    if (kind.contains("tool")) return true
    return content.any { it.isToolCallBlock || it.isToolResultBlock }
}

private fun CanonicalTimelineEntry.clearsWaitingAssistantTimelineItem(): Boolean {
    // 独立附件行和 assistant 文本答案是两个不同 timeline item；只有服务端显式声明
    // resolvesWaiting=true，或真正的 assistant 文本答案，才能清掉等待中的 assistant 占位。
    timelineResolvesWaiting?.let { return it }
    if (isAttachmentTimelineItem()) return false
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
    val inputEntries = messages.mapIndexedNotNull { index, message -> message.toEntry(sessionKey, index) }
    val canonicalEntries = inputEntries
        .filter { relayTimelineOrderKey(it) != null }
        .sortedWith(::compareEntries)
        .iterator()
    val canonicalSlotted = inputEntries.map { entry ->
        if (relayTimelineOrderKey(entry) == null) entry else canonicalEntries.next()
    }
    val projectedTurns = projectActiveLocalTurns(canonicalSlotted)
    val userAnchored = moveLegacyUnconfirmedUsersBeforeMatchingOutputs(projectedTurns)
    return moveWaitingAfterSameTurnOutputs(userAnchored).map { it.toChatMessage() }
}

private fun projectActiveLocalTurns(
    entries: List<CanonicalTimelineEntry>
): List<CanonicalTimelineEntry> {
    val localUsers = entries.filter(::isActiveLocalUser)
    if (localUsers.isEmpty()) return entries

    val identitiesByUser = localUsers.associateWith { turnIdentitySet(it).toMutableSet() }
    val userForOutput = mutableMapOf<CanonicalTimelineEntry, CanonicalTimelineEntry>()
    var identitiesExpanded: Boolean
    do {
        identitiesExpanded = false
        entries.forEachIndexed { outputIndex, output ->
            if (output in userForOutput || output.role !in setOf(MessageRole.assistant, MessageRole.tool)) {
                return@forEachIndexed
            }
            val outputIdentities = turnIdentitySet(output)
            if (outputIdentities.isEmpty()) return@forEachIndexed
            val matches = localUsers.filter { user ->
                identitiesByUser.getValue(user).any(outputIdentities::contains)
            }
            val user = matches.singleOrNull() ?: return@forEachIndexed
            val userIndex = entries.indexOf(user)
            val canonicalAnswer = relayTimelineOrderKey(output) != null && output.role == MessageRole.assistant
            val shouldProject = canonicalAnswer ||
                output.state in setOf(MessageState.pending, MessageState.streaming) ||
                isTransientAssistantTimelinePlaceholder(output) ||
                // Local media/tool rows normally keep their relay input slot. The
                // exception is an output that arrived physically before its own
                // optimistic user row; pull only that inversion into the turn.
                (relayTimelineOrderKey(output) == null && outputIndex < userIndex)
            if (!shouldProject) return@forEachIndexed
            userForOutput[output] = user
            if (identitiesByUser.getValue(user).addAll(outputIdentities)) {
                identitiesExpanded = true
            }
        }
    } while (identitiesExpanded)
    // A lone optimistic user must keep its physical slot. Projection is only
    // justified when at least one output can be tied to an active local turn.
    if (userForOutput.isEmpty()) return entries

    // Older persisted rows did not have localTurnOrder. If any such row is present,
    // keep the physical user order instead of mixing incomparable order domains.
    val orderedUsers = if (localUsers.all { it.localTurnOrder != null }) {
        localUsers.sortedWith(
            compareBy<CanonicalTimelineEntry> { it.localTurnOrder }
                .thenBy { it.originalIndex }
        )
    } else {
        localUsers
    }
    val userOrder = orderedUsers.withIndex().associate { (index, user) -> user to index }
    val projected = entries.mapIndexedNotNull { physicalIndex, entry ->
        val user = when {
            entry in identitiesByUser -> entry
            else -> userForOutput[entry]
        } ?: return@mapIndexedNotNull null
        LocalTurnProjection(
            entry = entry,
            physicalIndex = physicalIndex,
            userOrder = userOrder.getValue(user),
            phase = localTurnProjectionPhase(entry)
        )
    }.sortedWith { left, right ->
        val turnCompare = left.userOrder.compareTo(right.userOrder)
        if (turnCompare != 0) return@sortedWith turnCompare
        val phaseCompare = left.phase.compareTo(right.phase)
        if (phaseCompare != 0) return@sortedWith phaseCompare
        val leftOrder = relayTimelineOrderKey(left.entry)
        val rightOrder = relayTimelineOrderKey(right.entry)
        if (leftOrder != null && rightOrder != null && leftOrder != rightOrder) {
            return@sortedWith leftOrder.compareTo(rightOrder)
        }
        left.physicalIndex.compareTo(right.physicalIndex)
    }.map(LocalTurnProjection::entry)

    val projectedEntries = projected.toSet()
    val base = entries.filterNot(projectedEntries::contains).toMutableList()
    if (base.any { relayTimelineOrderKey(it) != null }) {
        return base + projected
    }
    val firstProjectedIndex = entries.indexOfFirst(projectedEntries::contains).let { index ->
        if (index < 0) entries.size else index
    }
    val insertionIndex = entries.take(firstProjectedIndex).count { it !in projectedEntries }
    base.addAll(insertionIndex, projected)
    return base
}

private fun moveLegacyUnconfirmedUsersBeforeMatchingOutputs(
    entries: List<CanonicalTimelineEntry>
): List<CanonicalTimelineEntry> {
    val result = entries.toMutableList()
    val users = entries.filter { user ->
        user.role == MessageRole.user &&
            relayTimelineOrderKey(user) == null &&
            user.state !in setOf(MessageState.failed, MessageState.deleted, MessageState.recalled) &&
            user.deliveryState.trim().lowercase() !in setOf("failed", "error", "aborted")
    }
    users.forEach { user ->
        val userTurn = normalizedTurnIdentity(user)
        if (userTurn.isEmpty()) return@forEach
        val userIndex = result.indexOf(user)
        val outputIndex = result.indexOfFirst { output ->
            output.role in setOf(MessageRole.assistant, MessageRole.tool) &&
                normalizedTurnIdentity(output) == userTurn
        }
        if (userIndex < 0 || outputIndex < 0 || outputIndex >= userIndex) return@forEach
        result.removeAt(userIndex)
        result.add(outputIndex, user)
    }
    return result
}

private data class LocalTurnProjection(
    val entry: CanonicalTimelineEntry,
    val physicalIndex: Int,
    val userOrder: Int,
    val phase: Int
)

private fun isActiveLocalUser(entry: CanonicalTimelineEntry): Boolean {
    if (entry.role != MessageRole.user || relayTimelineOrderKey(entry) != null) return false
    val isLocal = entry.timelineOrderKey.startsWith("local:") ||
        entry.timelineIdentityKey.startsWith("local:") ||
        entry.source.equals("local", ignoreCase = true) ||
        entry.runId?.startsWith("local-user-") == true
    if (!isLocal) return false
    if (entry.state in setOf(MessageState.failed, MessageState.deleted, MessageState.recalled)) return false
    return entry.deliveryState.trim().lowercase() !in setOf("failed", "error", "aborted")
}

private fun localTurnProjectionPhase(entry: CanonicalTimelineEntry): Int {
    if (entry.role == MessageRole.user) return 0
    if (isTransientAssistantTimelinePlaceholder(entry) || entry.timelineItemKind.equals("waiting", ignoreCase = true)) {
        return 2
    }
    return 1
}

private fun moveWaitingAfterSameTurnOutputs(
    entries: List<CanonicalTimelineEntry>
): List<CanonicalTimelineEntry> {
    val result = entries.toMutableList()
    val waitingEntries = entries.filter(::isTransientAssistantTimelinePlaceholder)
    waitingEntries.forEach { waiting ->
        val waitingIndex = result.indexOfFirst {
            it.stableKey == waiting.stableKey && it.displayId == waiting.displayId
        }
        if (waitingIndex < 0) return@forEach
        val waitingTurn = normalizedTurnIdentity(waiting)
        if (waitingTurn.isEmpty()) return@forEach
        val lastOutputIndex = result.indices.lastOrNull { index ->
            index != waitingIndex &&
                result[index].role in setOf(MessageRole.assistant, MessageRole.tool) &&
                !isTransientAssistantTimelinePlaceholder(result[index]) &&
                normalizedTurnIdentity(result[index]) == waitingTurn
        } ?: return@forEach
        if (lastOutputIndex <= waitingIndex) return@forEach
        result.removeAt(waitingIndex)
        result.add(lastOutputIndex, waiting)
    }
    return result
}
