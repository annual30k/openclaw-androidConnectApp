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
    val conversationSeqState: String? = null,
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
    val conversationSeqState: String = "",
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
        conversationSeqState = conversationSeqState.clean()
            ?: if (source.equals("local", ignoreCase = true)) "provisional" else "committed",
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
        conversationSeq = conversationSeq,
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
        conversationSeqState = conversationSeqState,
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
        conversationSeq = conversationSeq,
        conversationSeqState = conversationSeqState,
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
    if (leftOrderKey != null && rightOrderKey != null) {
        val orderCompare = compareCanonicalTimelineOrderKeys(leftOrderKey, rightOrderKey)
        if (orderCompare != 0) return orderCompare
        val identityCompare = left.timelineIdentityKey.compareTo(right.timelineIdentityKey)
        if (identityCompare != 0) return identityCompare
        return left.timelineItemKind.compareTo(right.timelineItemKind)
    }
    if (leftOrderKey != null && rightOrderKey == null) return -1
    if (leftOrderKey == null && rightOrderKey != null) return 1

    // Pair-dependent turn rules do not belong in a global comparator: combining
    // them with relay order keys can form A < B < C < A and makes TimSort throw.
    // Local turn grouping is applied after this total order by the projection
    // helpers below; unconfirmed overlays retain their stable input order here.
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
    snapshot: TimelineSnapshotPage,
    localOrderSources: List<ChatMessage> = emptyList()
): TimelineReconcileResult {
    val (existingConfirmed, existingPending) = TimelinePendingOverlay.splitPending(existing)
    val pendingEntries = (existingPending + pending).mapIndexedNotNull { index, message ->
        message.toEntry(snapshot.sessionKey, index)
    }
    val localOrderSourceEntries = localOrderSources.mapIndexedNotNull { index, message ->
        message.toEntry(snapshot.sessionKey, index)
    }
    val incoming = snapshot.messages.mapIndexedNotNull { index, message ->
        message.toEntryOrNull(snapshot.sessionKey, index)
    }
    return reconcileCanonicalTimeline(
        existingConfirmed = existingConfirmed,
        pendingEntries = pendingEntries,
        localOrderSourceEntries = localOrderSourceEntries,
        incomingCanonical = incoming,
        snapshot = snapshot
    )
}

private fun reconcileCanonicalTimeline(
    existingConfirmed: List<ChatMessage>,
    pendingEntries: List<CanonicalTimelineEntry>,
    localOrderSourceEntries: List<CanonicalTimelineEntry>,
    incomingCanonical: List<CanonicalTimelineEntry>,
    snapshot: TimelineSnapshotPage
): TimelineReconcileResult {
    val existingConfirmedEntries = existingConfirmed.mapIndexedNotNull { index, message ->
        message.toEntry(snapshot.sessionKey, index)
    }
    val locallyOrderedUsers = (existingConfirmedEntries + pendingEntries + localOrderSourceEntries).filter { entry ->
        entry.role == MessageRole.user && entry.localTurnOrder != null
    }
    val incomingCanonicalWithLocalOrder = incomingCanonical.map { incoming ->
        incoming.inheritingStableLocalTurnOrder(locallyOrderedUsers)
    }
    val incomingCanonicalWithLocalDisplayId = incomingCanonicalWithLocalOrder.map { incoming ->
        val matchingLocalPending = pendingEntries
            .filter(CanonicalTimelineEntry::isUnconfirmedLocalProjection)
            .filter { pending -> pendingResolvedByCanonical(pending, incoming) }
            .singleOrNull()
        val previouslyAdoptedDisplay = if (matchingLocalPending == null) {
            (existingConfirmedEntries + pendingEntries)
                .filter { existing -> existing.displayId != existing.messageId }
                .filter { existing -> previouslyAdoptedDisplayMatches(existing, incoming) }
                .singleOrNull()
        } else {
            null
        }
        val displaySource = matchingLocalPending ?: previouslyAdoptedDisplay
        if (displaySource == null) incoming else incoming.copy(
            // Relay messageId 仍保留在 messageId/timelineMessageId；只复用 UI display id，
            // 使本地 completed 回答在权威行到达时原地升级，不产生删除再插入的闪烁。
            // 首次接管后，后续 delta/history snapshot 也必须沿用这个 display id；否则
            // Compose 会在长回复中途把同一行当成删除 + 新增，导致视口跳到列表顶部。
            displayId = displaySource.displayId,
            localTurnOrder = displaySource.localTurnOrder ?: incoming.localTurnOrder
        )
    }
    val incomingIdentities = incomingCanonicalWithLocalDisplayId.mapTo(mutableSetOf()) { it.timelineIdentityKey }
    val deletedIds = snapshot.deletedMessageIds.toSet()
    val byIdentity = linkedMapOf<String, CanonicalTimelineEntry>()
    val range = snapshot.range

    existingConfirmedEntries
        .filter { it.timelineIdentityKey.isNotBlank() && it.timelineItemKind.isNotBlank() && relayTimelineOrderKey(it) != null }
        .filter { range.isBounded && !range.contains(it.seq ?: it.conversationSeq) }
        .forEach { entry ->
            if (entry.messageId in deletedIds || entry.timelineIdentityKey in deletedIds) {
                byIdentity[entry.timelineIdentityKey] = deletedTombstone(entry)
            } else if (entry.timelineIdentityKey !in incomingIdentities) {
                byIdentity[entry.timelineIdentityKey] = entry
            }
        }

    existingConfirmedEntries
        .filter(CanonicalTimelineEntry::isDurableStableFileProjection)
        .filter { existingEntry ->
            incomingCanonicalWithLocalDisplayId.none { incomingEntry ->
                existingEntry.sameStableAttachmentProjection(incomingEntry)
            }
        }
        .forEach { entry ->
            if (entry.messageId in deletedIds || entry.timelineIdentityKey in deletedIds) {
                byIdentity[entry.timelineIdentityKey] = deletedTombstone(entry)
            } else if (entry.timelineIdentityKey !in incomingIdentities) {
                // provider history 可能暂时不包含 Relay 文件通道独立落库的附件；已有稳定附件身份
                // 的 canonical 行必须跨完整刷新保留，直到新投影接管或显式 tombstone 删除。
                byIdentity[entry.timelineIdentityKey] = entry
            }
        }

    incomingCanonicalWithLocalDisplayId.forEach { entry ->
        byIdentity[entry.timelineIdentityKey] = entry
    }

    val legacyPendingTurnIds = pendingEntries
        .filter { it.role == MessageRole.assistant && it.state in setOf(MessageState.pending, MessageState.streaming) }
        .map(::normalizedTurnIdentity)
        .filter(String::isNotBlank)
        .toSet()
    val remainingPending = pendingEntries
        .filter { pendingEntry ->
            // 本地回显只由 Relay 返回的同角色、同稳定身份行确认；不得因为 history
            // page 尚未覆盖该回合，或 waiting 占位已结束，而擅自从界面移除。
            incomingCanonicalWithLocalDisplayId.none { incomingEntry -> pendingResolvedByCanonical(pendingEntry, incomingEntry) }
        }
        .filter { pendingEntry ->
            if (pendingEntry.role != MessageRole.user || pendingEntry.runId?.trim()?.startsWith("local-user-") != true) {
                return@filter true
            }
            if (pendingEntry.timelineOrderKey.trim().startsWith("local:") ||
                pendingEntry.timelineIdentityKey.trim().startsWith("local:")
            ) {
                return@filter true
            }
            // 仅兼容早期没有 local order/identity 的旧 user echo：它没有可用于跨页确认
            // 的本地稳定键，所以继续沿用“仅在同回合 waiting 存在时保留”的旧清理规则。
            val turnIdentity = normalizedTurnIdentity(pendingEntry)
            pendingEntry.isDurableStableFileProjection() ||
                (turnIdentity.isNotBlank() && turnIdentity in legacyPendingTurnIds)
        }

    return TimelineReconcileResult(
        messages = byIdentity.values.sortedWith(::compareEntries).map { it.toChatMessage() },
        pending = remainingPending.sortedWith(::compareEntries).map { it.toChatMessage() }
    )
}

private fun previouslyAdoptedDisplayMatches(
    existing: CanonicalTimelineEntry,
    incoming: CanonicalTimelineEntry
): Boolean {
    if (existing.role != incoming.role) return false
    if (existing.timelineIdentityKey.isNotBlank() && existing.timelineIdentityKey == incoming.timelineIdentityKey) {
        return true
    }
    if (existing.messageId.isNotBlank() && existing.messageId == incoming.messageId) return true

    val existingIdentities = turnIdentitySet(existing)
    val incomingIdentities = turnIdentitySet(incoming)
    if (existingIdentities.isEmpty() || incomingIdentities.isEmpty() ||
        existingIdentities.intersect(incomingIdentities).isEmpty()
    ) {
        return false
    }
    if (existing.role != MessageRole.user) return true

    // 当前版本的本地 user 都携带 clientMessageId/idempotencyKey。canonical user
    // 必须显式回传其中一个才能继承本地展示 ID；只共享 provider run/turn 不足以确认。
    val localAcknowledgementIds = listOf(existing.clientMessageId, existing.idempotencyKey)
        .map(::normalizedTurnIdentityValue)
        .filter(String::isNotEmpty)
        .toSet()
    if (localAcknowledgementIds.isEmpty()) return true
    val incomingAcknowledgementIds = listOf(incoming.clientMessageId, incoming.idempotencyKey)
        .map(::normalizedTurnIdentityValue)
        .filter(String::isNotEmpty)
        .toSet()
    return localAcknowledgementIds.intersect(incomingAcknowledgementIds).isNotEmpty()
}

private fun CanonicalTimelineEntry.isUnconfirmedLocalProjection(): Boolean {
    // A Relay row can keep the original local display ID/run ID to avoid Compose
    // churn, but its canonical order key means it is already confirmed. It must
    // never be moved again as a pending local turn.
    if (relayTimelineOrderKey(this) != null &&
        !(source.equals("local", ignoreCase = true) && state in setOf(MessageState.pending, MessageState.streaming))
    ) {
        return false
    }
    val hasProvisionalConversationLifecycle = role == MessageRole.user && (
        conversationSeqState.equals("provisional", ignoreCase = true) ||
            (conversationSeqState.isBlank() && source.equals("local", ignoreCase = true))
        )
    return hasProvisionalConversationLifecycle ||
        timelineOrderKey.trim().startsWith("local:") ||
        timelineIdentityKey.trim().startsWith("local:") ||
        runId?.trim()?.startsWith("local-user-") == true
}

private fun CanonicalTimelineEntry.inheritingStableLocalTurnOrder(
    locallyOrderedUsers: List<CanonicalTimelineEntry>
): CanonicalTimelineEntry {
    if (role != MessageRole.user || localTurnOrder != null) return this
    val identities = turnIdentitySet(this)
    if (identities.isEmpty()) return this
    val matchingOrders = locallyOrderedUsers.asSequence()
        .filter { candidate -> turnIdentitySet(candidate).any(identities::contains) }
        .mapNotNull(CanonicalTimelineEntry::localTurnOrder)
        .distinct()
        .toList()
    // 只在稳定 turn 身份唯一对应一个本地提交序号时继承；冲突时保留服务端投影。
    return matchingOrders.singleOrNull()?.let { order -> copy(localTurnOrder = order) } ?: this
}

private fun pendingResolvedByCanonical(
    pending: CanonicalTimelineEntry,
    canonical: CanonicalTimelineEntry
): Boolean {
    if (pending.role != canonical.role) return false
    if (pending.role == MessageRole.user && pending.content.any { it.isFileBlock && !it.isVoiceMessageBlock }) {
        val pendingAttachmentIdentities = pending.stableTransferIdentitySet()
        val canonicalAttachmentIdentities = canonical.stableTransferIdentitySet()
        // 图片/文件 user echo 不能被同 turn 的纯文字历史行提前确认；否则部分快照会先删掉
        // 本地媒体，直到附件行稍后到达才重新出现。只允许稳定附件身份完成接管。
        if (pendingAttachmentIdentities.isEmpty() ||
            pendingAttachmentIdentities.intersect(canonicalAttachmentIdentities).isEmpty()
        ) {
            return false
        }
    }
    if (pending.role == MessageRole.assistant &&
        !canonical.replacesStreamingAssistantWaiting(pending) &&
        !canonical.clearsWaitingAssistantTimelineItem()
    ) {
        return false
    }
    val pendingIdentities = turnIdentitySet(pending)
    val canonicalIdentities = turnIdentitySet(canonical)
    if (pendingIdentities.isNotEmpty() &&
        canonicalIdentities.isNotEmpty() &&
        pendingIdentities.intersect(canonicalIdentities).isNotEmpty()
    ) return true
    if (pending.attachmentIds.isNotEmpty() &&
        canonical.attachmentIds.isNotEmpty() &&
        pending.attachmentIds.any { it in canonical.attachmentIds }
    ) {
        return true
    }
    return false
}

private fun CanonicalTimelineEntry.stableTransferIdentitySet(): Set<String> {
    return buildSet {
        attachmentIds.forEach { identity ->
            identity.trim().takeIf { it.isNotEmpty() }?.let(::add)
        }
        content.forEach { block ->
            block.attachmentId?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
            block.fileId?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
        }
    }
}

private fun CanonicalTimelineEntry.isDurableStableFileProjection(): Boolean {
    return content.any { it.isFileBlock && !it.isVoiceMessageBlock } &&
        stableTransferIdentitySet().isNotEmpty()
}

private fun CanonicalTimelineEntry.sameStableAttachmentProjection(
    candidate: CanonicalTimelineEntry
): Boolean {
    if (!isDurableStableFileProjection() || !candidate.isDurableStableFileProjection()) return false
    if (role != candidate.role) return false
    return stableTransferIdentitySet().intersect(candidate.stableTransferIdentitySet()).isNotEmpty()
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
    // Before the Relay assigns canonical keys, the explicitly supplied event
    // sort timestamp is the only ordering source. Preserve input order only
    // when that authoritative metadata is absent or tied.
    if (inputEntries.none { relayTimelineOrderKey(it) != null }) {
        return projectUnconfirmedLocalTurns(inputEntries.sortedWith(::compareUnconfirmedRealtimeEntries))
            .map { it.toChatMessage() }
    }
    val canonicalEntries = inputEntries
        .filter { relayTimelineOrderKey(it) != null }
        .sortedWith(::compareEntries)
        .iterator()
    val canonicalSlotted = inputEntries.map { entry ->
        if (relayTimelineOrderKey(entry) == null) entry else canonicalEntries.next()
    }
    // Relay canonical key 是已确认消息的唯一顺序来源。只有真正未确认的本地 user
    // 及其唯一身份命中的输出可以作为尾部 overlay；确认消息禁止二次按 turn 重排。
    return projectUnconfirmedLocalTurns(orderCanonicalTurns(canonicalSlotted)).map { it.toChatMessage() }
}

private fun compareUnconfirmedRealtimeEntries(
    left: CanonicalTimelineEntry,
    right: CanonicalTimelineEntry
): Int {
    val leftTimestamp = left.sortTimestamp
    val rightTimestamp = right.sortTimestamp
    if (leftTimestamp != null && rightTimestamp != null) {
        val timestampCompare = leftTimestamp.compareTo(rightTimestamp)
        if (timestampCompare != 0) return timestampCompare
    }
    val inputCompare = left.originalIndex.compareTo(right.originalIndex)
    if (inputCompare != 0) return inputCompare
    return left.stableKey.compareTo(right.stableKey)
}

private fun orderCanonicalTurns(entries: List<CanonicalTimelineEntry>): List<CanonicalTimelineEntry> {
    data class TurnStats(var userCount: Int = 0, var userOrderKey: String? = null)
    val statsByTurn = mutableMapOf<String, TurnStats>()
    entries.forEach { entry ->
        val orderKey = relayTimelineOrderKey(entry) ?: return@forEach
        val identity = primaryPresentationTurnIdentity(entry) ?: return@forEach
        val stats = statsByTurn.getOrPut(identity, ::TurnStats)
        if (entry.role == MessageRole.user) {
            stats.userCount += 1
            val previousUserOrderKey = stats.userOrderKey
            if (previousUserOrderKey == null || compareCanonicalTimelineOrderKeys(orderKey, previousUserOrderKey) < 0) {
                stats.userOrderKey = orderKey
            }
        }
    }

    data class OrderedEntry(
        val originalIndex: Int,
        val entry: CanonicalTimelineEntry,
        val orderKey: String,
        val pairedTurn: String,
        val anchorOrderKey: String
    )
    val orderedCanonical = entries.mapIndexedNotNull { index, entry ->
        val orderKey = relayTimelineOrderKey(entry) ?: return@mapIndexedNotNull null
        val identity = primaryPresentationTurnIdentity(entry).orEmpty()
        val stats = statsByTurn[identity]
        val userOrderKey = stats?.takeIf { it.userCount == 1 }?.userOrderKey
        val malformedOutput = entry.role in setOf(MessageRole.assistant, MessageRole.tool) &&
            userOrderKey != null && compareCanonicalTimelineOrderKeys(orderKey, userOrderKey) < 0
        val repairedTurn = identity.takeIf { userOrderKey != null && (entry.role == MessageRole.user || malformedOutput) }.orEmpty()
        OrderedEntry(index, entry, orderKey, repairedTurn, userOrderKey.takeIf { repairedTurn.isNotEmpty() } ?: orderKey)
    }.sortedWith { left, right ->
        val anchorCompare = compareCanonicalTimelineOrderKeys(left.anchorOrderKey, right.anchorOrderKey)
        if (anchorCompare != 0) return@sortedWith anchorCompare
        if (left.pairedTurn.isNotEmpty() && left.pairedTurn == right.pairedTurn) {
            val roleCompare = canonicalTurnRoleRank(left.entry).compareTo(canonicalTurnRoleRank(right.entry))
            if (roleCompare != 0) return@sortedWith roleCompare
        }
        val keyCompare = compareCanonicalTimelineOrderKeys(left.orderKey, right.orderKey)
        if (keyCompare != 0) keyCompare else left.originalIndex.compareTo(right.originalIndex)
    }.iterator()
    return entries.map { entry -> if (relayTimelineOrderKey(entry) == null) entry else orderedCanonical.next().entry }
}

private fun canonicalTurnRoleRank(entry: CanonicalTimelineEntry): Int = when (entry.role) {
    MessageRole.user -> 0
    MessageRole.tool -> 1
    MessageRole.assistant -> 2
    else -> 3
}

private fun primaryPresentationTurnIdentity(entry: CanonicalTimelineEntry): String? {
    entry.content.forEach { block ->
        normalizedTurnIdentityValue(block.sourceRunId).takeIf(String::isNotEmpty)?.let { return it }
    }
    return listOf(entry.idempotencyKey, entry.clientMessageId, entry.turnId, entry.runId)
        .firstNotNullOfOrNull { value -> normalizedTurnIdentityValue(value).takeIf(String::isNotEmpty) }
}

private fun projectUnconfirmedLocalTurns(
    entries: List<CanonicalTimelineEntry>
): List<CanonicalTimelineEntry> {
    val localUsers = entries.filter(::isActiveLocalOverlayUser)
    if (localUsers.isEmpty()) return anchorLocalOutputsAfterConfirmedUsers(entries)

    val identitiesByUser = localUsers.associateWith { turnIdentitySet(it).toMutableSet() }
    val userForOutput = mutableMapOf<CanonicalTimelineEntry, CanonicalTimelineEntry>()
    var identitiesExpanded: Boolean
    do {
        identitiesExpanded = false
        entries.forEach { output ->
            if (output in userForOutput || output.role !in setOf(MessageRole.assistant, MessageRole.tool)) {
                return@forEach
            }
            val outputIdentities = turnIdentitySet(output)
            if (outputIdentities.isEmpty()) return@forEach
            val matches = localUsers.filter { user ->
                identitiesByUser.getValue(user).any(outputIdentities::contains)
            }
            val user = matches.singleOrNull() ?: return@forEach
            userForOutput[output] = user
            if (identitiesByUser.getValue(user).addAll(outputIdentities)) {
                identitiesExpanded = true
            }
        }
    } while (identitiesExpanded)
    val usersWithOutputs = userForOutput.values.toSet()
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
        if (user !in usersWithOutputs) return@mapIndexedNotNull null
        LocalTurnProjection(
            entry = entry,
            user = user,
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
            return@sortedWith compareCanonicalTimelineOrderKeys(leftOrder, rightOrder)
        }
        left.physicalIndex.compareTo(right.physicalIndex)
    }

    if (entries.any { relayTimelineOrderKey(it) != null }) {
        val projectedEntries = projected.map(LocalTurnProjection::entry).toSet()
        return anchorLocalOutputsAfterConfirmedUsers(entries.filter { it !in projectedEntries } + projected.map(LocalTurnProjection::entry))
    }

    val projectionsByEntry = projected.associateBy(LocalTurnProjection::entry)
    val projectionsByUser = projected.groupBy(LocalTurnProjection::user)
    val emittedUsers = mutableSetOf<CanonicalTimelineEntry>()
    val grouped = buildList {
        entries.forEach { entry ->
            val projection = projectionsByEntry[entry]
            if (projection == null) {
                add(entry)
            } else if (emittedUsers.add(projection.user)) {
                addAll(projectionsByUser.getValue(projection.user).map(LocalTurnProjection::entry))
            }
        }
    }
    return anchorLocalOutputsAfterConfirmedUsers(grouped)
}

private fun anchorLocalOutputsAfterConfirmedUsers(
    entries: List<CanonicalTimelineEntry>
): List<CanonicalTimelineEntry> {
    val confirmedUsers = entries.filter { entry ->
        entry.role == MessageRole.user && relayTimelineOrderKey(entry) != null
    }
    if (confirmedUsers.isEmpty()) return entries

    val identitiesByUser = confirmedUsers.associateWith(::turnIdentitySet)
    val outputsByAnchor = mutableMapOf<CanonicalTimelineEntry, MutableList<CanonicalTimelineEntry>>()
    entries.filter(::isLocalOutputOverlay).forEach { output ->
        val outputIdentities = turnIdentitySet(output)
        if (outputIdentities.isEmpty()) return@forEach
        val matchingUser = confirmedUsers.singleOrNull { user ->
            identitiesByUser.getValue(user).any(outputIdentities::contains)
        } ?: return@forEach
        val userIdentities = identitiesByUser.getValue(matchingUser)
        val anchor = entries.lastOrNull { candidate ->
            candidate == matchingUser || (
                candidate.role in setOf(MessageRole.assistant, MessageRole.tool) &&
                    !isLocalOutputOverlay(candidate) &&
                    turnIdentitySet(candidate).any(userIdentities::contains)
                )
        } ?: matchingUser
        outputsByAnchor.getOrPut(anchor, ::mutableListOf).add(output)
    }
    if (outputsByAnchor.isEmpty()) return entries

    val physicalIndex = entries.withIndex().associate { (index, entry) -> entry to index }
    val movedOutputs = outputsByAnchor.values.flatten().toSet()
    return buildList {
        entries.forEach { entry ->
            if (entry in movedOutputs) return@forEach
            add(entry)
            outputsByAnchor[entry]
                ?.sortedWith(
                    compareBy<CanonicalTimelineEntry>(::localTurnProjectionPhase)
                        .thenBy { output -> physicalIndex.getValue(output) }
                )
                ?.let(::addAll)
        }
    }
}

private fun isLocalOutputOverlay(entry: CanonicalTimelineEntry): Boolean {
    if (entry.role !in setOf(MessageRole.assistant, MessageRole.tool)) return false
    if (relayTimelineOrderKey(entry) != null) return false
    if (entry.timelineItemKind.equals("waiting", ignoreCase = true)) return true
    if (entry.state != MessageState.streaming) return false
    val rawOrderKey = entry.timelineOrderKey.trim()
    return rawOrderKey.isEmpty() || rawOrderKey.startsWith("local:")
}

private data class LocalTurnProjection(
    val entry: CanonicalTimelineEntry,
    val user: CanonicalTimelineEntry,
    val physicalIndex: Int,
    val userOrder: Int,
    val phase: Int
)

private fun isActiveLocalOverlayUser(entry: CanonicalTimelineEntry): Boolean {
    if (entry.role != MessageRole.user || !entry.isUnconfirmedLocalProjection()) return false
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
