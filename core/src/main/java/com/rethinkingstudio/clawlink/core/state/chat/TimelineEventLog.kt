package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

internal sealed interface TimelineEvent {
    val eventId: String?

    data class TurnUserCreated(
        override val eventId: String?,
        val turnId: String,
        val runId: String?,
        val messageId: String,
        val content: List<RelayChatContentBlock>,
        val createdAt: String?,
        val seq: Long? = null,
        val turnSeq: Long? = null,
        val source: String? = null,
        val timelineOrderKey: String? = null,
        val timelineIdentityKey: String? = null,
        val timelineItemKind: String? = null,
        val timelineResolvesWaiting: Boolean? = null
    ) : TimelineEvent

    data class MessagePartDelta(
        override val eventId: String?,
        val turnId: String?,
        val messageId: String,
        val role: String?,
        val partId: String,
        val seq: Long,
        val content: List<RelayChatContentBlock>,
        val runId: String?,
        val createdAt: String?,
        val turnSeq: Long? = null,
        val timelineOrderKey: String? = null,
        val timelineIdentityKey: String? = null,
        val timelineItemKind: String? = null,
        val timelineResolvesWaiting: Boolean? = null
    ) : TimelineEvent

    data class MessageCompleted(
        override val eventId: String?,
        val turnId: String?,
        val messageId: String,
        val role: String?,
        val runId: String?,
        val content: List<RelayChatContentBlock>,
        val createdAt: String?,
        val seq: Long? = null,
        val turnSeq: Long? = null,
        val timelineOrderKey: String? = null,
        val timelineIdentityKey: String? = null,
        val timelineItemKind: String? = null,
        val timelineResolvesWaiting: Boolean? = null
    ) : TimelineEvent

    data class RunTerminal(
        override val eventId: String?,
        val turnId: String?,
        val runId: String?,
        val status: String
    ) : TimelineEvent

    data class AttachmentStateChanged(
        override val eventId: String?,
        val attachmentId: String,
        val messageId: String?,
        val state: String,
        val url: String?
    ) : TimelineEvent

    data class ToolInvocationUpdated(
        override val eventId: String?,
        val turnId: String?,
        val runId: String?,
        val toolCallId: String,
        val messageId: String?,
        val role: String?,
        val messageState: String?,
        val name: String?,
        val state: String,
        val text: String?,
        val content: List<RelayChatContentBlock>,
        val createdAt: String?,
        val seq: Long? = null,
        val turnSeq: Long? = null,
        val timelineOrderKey: String? = null,
        val timelineIdentityKey: String? = null,
        val timelineItemKind: String? = null,
        val timelineResolvesWaiting: Boolean? = null
    ) : TimelineEvent

    data class HistorySnapshotPage(
        override val eventId: String?,
        val items: List<HistorySnapshotItem>
    ) : TimelineEvent
}

@Serializable
internal data class ChatTimelineState(
    val messages: List<ChatMessage> = emptyList(),
    val activeRunId: String? = null,
    val activeRunsByTurnId: Map<String, String> = emptyMap(),
    val activeTurnByRunId: Map<String, String> = emptyMap(),
    val seenEventIds: Set<String> = emptySet(),
    val seenPartSeqKeys: Set<String> = emptySet(),
    val messagePartSeqByKey: Map<String, Long> = emptyMap(),
    val messagePartsById: Map<String, TimelineMessageParts> = emptyMap(),
    val attachmentsById: Map<String, TimelineAttachmentState> = emptyMap(),
    val toolsById: Map<String, TimelineToolInvocationState> = emptyMap(),
    val historySnapshotTurnIds: Set<String> = emptySet(),
    val historySnapshotMessageIds: Set<String> = emptySet()
) {
    val hasActiveRun: Boolean get() = activeRunId != null || activeRunsByTurnId.isNotEmpty() || activeTurnByRunId.isNotEmpty()
}

@Serializable
internal data class TimelineMessageParts(
    val turnId: String? = null,
    val parts: Map<String, String> = emptyMap()
) {
    val content: String get() = parts.values.joinToString("")
}

@Serializable
internal data class TimelineAttachmentState(
    val attachmentId: String,
    val messageId: String? = null,
    val state: String,
    val url: String? = null
)

@Serializable
internal data class TimelineToolInvocationState(
    val toolCallId: String,
    val messageId: String? = null,
    val name: String? = null,
    val state: String,
    val text: String? = null
)

@Serializable
internal data class HistorySnapshotItem(
    val turnId: String,
    val messageId: String,
    val role: String,
    val text: String = "",
    val content: List<RelayChatContentBlock> = emptyList(),
    val createdAt: String? = null,
    val runId: String? = null,
    val seq: Long? = null,
    val turnSeq: Long? = null,
    val timelineOrderKey: String? = null,
    val timelineIdentityKey: String? = null,
    val timelineItemKind: String? = null,
    val timelineResolvesWaiting: Boolean? = null,
    val source: String? = null
) {
    val displayText: String
        get() = if (content.isNotEmpty()) {
            content.mapNotNull { it.text?.takeIf { value -> value.isNotBlank() } }.joinToString("\n\n")
        } else {
            text
        }
}

internal object TimelineEventLog {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    }

    fun decodeEvent(raw: String): TimelineEvent? {
        return try {
            val event = json.decodeFromString(RawTimelineEvent.serializer(), raw)
            if (event.protocolVersion != 2) return null
            event.toTimelineEvent()
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun decodePayload(element: JsonElement?): List<TimelineEvent> {
        val obj = element as? JsonObject ?: return emptyList()
        val timelineEvents = obj["timelineEvents"] as? JsonArray
        if (timelineEvents != null) {
            return timelineEvents.mapNotNull { decodeEvent(it.toString()) }
        }
        val snapshot = obj["timelineSnapshot"] as? JsonObject
        if (snapshot != null) {
            return listOfNotNull(decodeEvent(snapshot.toString()))
        }
        if (obj["protocolVersion"]?.jsonPrimitive?.intOrNull == 2) {
            return listOfNotNull(decodeEvent(obj.toString()))
        }
        return emptyList()
    }
}

@Serializable
private data class RawTimelineEvent(
    val protocolVersion: Int? = null,
    val eventId: String? = null,
    val eventType: String? = null,
    val type: String? = null,
    val turnId: String? = null,
    val runId: String? = null,
    val messageId: String? = null,
    val text: String? = null,
    val content: List<RelayChatContentBlock> = emptyList(),
    val createdAt: String? = null,
    val role: String? = null,
    val partId: String? = null,
    val seq: Long? = null,
    val turnSeq: Long? = null,
    val source: String? = null,
    val timelineItemKind: String? = null,
    val timelineOrderKey: String? = null,
    val timelineIdentityKey: String? = null,
    val timelineResolvesWaiting: Boolean? = null,
    val messageState: String? = null,
    val runState: String? = null,
    val status: String? = null,
    val attachmentId: String? = null,
    val attachment: RawTimelineAttachment? = null,
    val state: String? = null,
    val error: RawTimelineError? = null,
    val url: String? = null,
    val toolInvocationId: String? = null,
    val toolCallId: String? = null,
    val toolState: String? = null,
    val name: String? = null,
    @SerialName("items")
    val items: List<HistorySnapshotItem> = emptyList(),
    val messages: List<HistorySnapshotItem> = emptyList()
) {
    fun toTimelineEvent(): TimelineEvent? {
        val canonicalType = eventType ?: type ?: return null
        return when (canonicalType) {
            "turn.user.created" -> TimelineEvent.TurnUserCreated(
                eventId = eventId,
                turnId = turnId?.takeIf { it.isNotBlank() } ?: return null,
                runId = runId,
                messageId = messageId?.takeIf { it.isNotBlank() } ?: return null,
                content = canonicalContent(),
                createdAt = createdAt,
                seq = seq,
                turnSeq = turnSeq,
                source = source,
                timelineOrderKey = timelineOrderKey,
                timelineIdentityKey = timelineIdentityKey,
                timelineItemKind = timelineItemKind,
                timelineResolvesWaiting = timelineResolvesWaiting
            )
            "message.part.delta" -> TimelineEvent.MessagePartDelta(
                eventId = eventId,
                turnId = turnId,
                messageId = messageId?.takeIf { it.isNotBlank() } ?: return null,
                role = role,
                partId = partId?.takeIf { it.isNotBlank() } ?: return null,
                seq = seq ?: return null,
                content = canonicalContent(),
                runId = runId,
                createdAt = createdAt,
                turnSeq = turnSeq,
                timelineOrderKey = timelineOrderKey,
                timelineIdentityKey = timelineIdentityKey,
                timelineItemKind = timelineItemKind,
                timelineResolvesWaiting = timelineResolvesWaiting
            )
            "message.completed" -> TimelineEvent.MessageCompleted(
                eventId = eventId,
                turnId = turnId,
                messageId = messageId?.takeIf { it.isNotBlank() } ?: return null,
                role = role,
                runId = runId,
                content = canonicalContent(),
                createdAt = createdAt,
                seq = seq,
                turnSeq = turnSeq,
                timelineOrderKey = timelineOrderKey,
                timelineIdentityKey = timelineIdentityKey,
                timelineItemKind = timelineItemKind,
                timelineResolvesWaiting = timelineResolvesWaiting
            )
            "run.completed", "run.failed", "run.aborted" -> TimelineEvent.RunTerminal(
                eventId = eventId,
                turnId = turnId,
                runId = runId,
                status = canonicalType.removePrefix("run.")
            )
            "attachment.state.changed" -> TimelineEvent.AttachmentStateChanged(
                eventId = eventId,
                attachmentId = attachmentId?.takeIf { it.isNotBlank() }
                    ?: attachment?.attachmentId?.takeIf { it.isNotBlank() }
                    ?: return null,
                messageId = messageId,
                state = state?.takeIf { it.isNotBlank() }
                    ?: attachment?.state?.takeIf { it.isNotBlank() }
                    ?: return null,
                url = url ?: attachment?.url
            )
            "tool.invocation.updated" -> TimelineEvent.ToolInvocationUpdated(
                eventId = eventId,
                turnId = turnId,
                runId = runId,
                toolCallId = toolInvocationId?.takeIf { it.isNotBlank() }
                    ?: toolCallId?.takeIf { it.isNotBlank() }
                    ?: return null,
                messageId = messageId,
                role = role,
                messageState = messageState,
                name = name,
                state = toolState?.takeIf { it.isNotBlank() }
                    ?: state?.takeIf { it.isNotBlank() }
                    ?: return null,
                text = text?.takeIf { it.isNotBlank() } ?: canonicalToolText(),
                content = canonicalToolContent(),
                createdAt = createdAt,
                seq = seq,
                turnSeq = turnSeq,
                timelineOrderKey = timelineOrderKey,
                timelineIdentityKey = timelineIdentityKey,
                timelineItemKind = timelineItemKind,
                timelineResolvesWaiting = timelineResolvesWaiting
            )
            "history.snapshot.page" -> TimelineEvent.HistorySnapshotPage(
                eventId = eventId,
                items = if (messages.isNotEmpty()) messages else items
            )
            else -> null
        }
    }

    private fun canonicalContent(): List<RelayChatContentBlock> {
        return if (content.isNotEmpty()) content else listOf(RelayChatContentBlock(type = "text", text = text.orEmpty()))
    }

    private fun canonicalText(): String {
        return canonicalContent().mapNotNull { it.text?.takeIf { value -> value.isNotBlank() } }.joinToString("\n\n")
    }

    private fun canonicalToolContent(): List<RelayChatContentBlock> {
        if (content.isNotEmpty()) return content
        val textValue = text?.takeIf { it.isNotBlank() } ?: return emptyList()
        return listOf(RelayChatContentBlock(type = "text", text = textValue))
    }

    private fun canonicalToolText(): String? {
        return canonicalToolContent()
            .mapNotNull { it.text?.takeIf { value -> value.isNotBlank() } }
            .joinToString("\n\n")
            .takeIf { it.isNotBlank() }
    }
}

@Serializable
private data class RawTimelineAttachment(
    val attachmentId: String? = null,
    val state: String? = null,
    val url: String? = null
)

@Serializable
private data class RawTimelineError(
    val userMessage: String? = null,
    val code: String? = null
)
