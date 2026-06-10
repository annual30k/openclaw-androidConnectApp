package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlin.math.floor

internal enum class TimelineIdentitySource {
    MessageId, Seq, RunPart, TurnPart, Client, Fallback
}

internal data class TimelineStableIdentity(
    val stableKey: String,
    val source: TimelineIdentitySource
)

private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

private fun keyPart(value: String): String {
    return URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}

internal fun timelineContentHash(content: List<RelayChatContentBlock>): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(content.joinToString(separator = "|") {
            listOf(
                it.type,
                it.text.orEmpty(),
                it.attachmentId.orEmpty(),
                it.fileId.orEmpty(),
                it.fileName.orEmpty(),
                it.mimeType.orEmpty(),
                it.downloadUrl.orEmpty(),
                it.thumbnailUrl.orEmpty(),
                it.transferState.orEmpty()
            ).joinToString(separator = "\u0000")
        }.toByteArray(StandardCharsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

internal fun stableTimelineKey(
    sessionKey: String,
    messageId: String? = null,
    seq: Long? = null,
    runId: String? = null,
    turnId: String? = null,
    role: String? = null,
    partId: String? = null,
    clientMessageId: String? = null,
    idempotencyKey: String? = null,
    createdAt: String? = null,
    contentHash: String? = null
): TimelineStableIdentity {
    val session = sessionKey.clean() ?: "main"
    messageId.clean()?.let {
        return TimelineStableIdentity("$session:message:${keyPart(it)}", TimelineIdentitySource.MessageId)
    }
    seq?.let {
        return TimelineStableIdentity("$session:seq:$it", TimelineIdentitySource.Seq)
    }
    val run = runId.clean()
    val part = partId.clean()
    if (run != null && part != null) {
        return TimelineStableIdentity("$session:run-part:${keyPart(run)}:${keyPart(part)}", TimelineIdentitySource.RunPart)
    }
    val turn = turnId.clean()
    val normalizedRole = role.clean()
    if (turn != null && normalizedRole != null && part != null) {
        return TimelineStableIdentity(
            "$session:turn-part:${keyPart(turn)}:${keyPart(normalizedRole)}:${keyPart(part)}",
            TimelineIdentitySource.TurnPart
        )
    }
    val clientKey = clientMessageId.clean() ?: idempotencyKey.clean()
    if (clientKey != null) {
        return TimelineStableIdentity("$session:client:${keyPart(clientKey)}", TimelineIdentitySource.Client)
    }
    val bucket = createdAt.clean()
        ?.let { runCatching { java.time.Instant.parse(it).epochSecond }.getOrNull() }
        ?.let { floor(it.toDouble()).toLong() }
        ?: 0L
    val hash = (contentHash.clean() ?: timelineContentHash(emptyList())).take(16)
    return TimelineStableIdentity(
        "$session:fallback:${keyPart(normalizedRole ?: "assistant")}:$bucket:$hash",
        TimelineIdentitySource.Fallback
    )
}

internal fun stableTimelineKey(sessionKey: String, message: ChatMessage): TimelineStableIdentity {
    val session = sessionKey.clean() ?: "main"
    message.timelineStableKey.clean()?.let {
        return TimelineStableIdentity(it, TimelineIdentitySource.MessageId)
    }
    message.timelineMessageId.clean()?.let {
        return TimelineStableIdentity("$session:message:${keyPart(it)}", TimelineIdentitySource.MessageId)
    }
    message.id.clean()?.let {
        return TimelineStableIdentity("$session:message:${keyPart(it)}", TimelineIdentitySource.MessageId)
    }
    message.seq?.let {
        return TimelineStableIdentity("$session:seq:$it", TimelineIdentitySource.Seq)
    }
    return stableTimelineKey(
        sessionKey = session,
        runId = message.runId,
        role = message.role.name,
        partId = message.timelinePartId,
        createdAt = message.createdAt,
        contentHash = timelineContentHash(message.contentBlocks)
    )
}
