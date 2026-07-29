package com.rethinkingstudio.clawlink.core.state.chat

internal data class TimelineSnapshotVersion(
    val revision: String?,
    val highWatermark: Long?
)

internal fun shouldAcceptTimelineSnapshotVersion(
    current: TimelineSnapshotVersion,
    incoming: TimelineSnapshotVersion
): Boolean {
    val currentHigh = current.highWatermark
    val incomingHigh = incoming.highWatermark
    if (currentHigh != null && incomingHigh != null && incomingHigh < currentHigh) return false

    val currentNumericRevision = current.revision?.trim()?.toLongOrNull()
    val incomingNumericRevision = incoming.revision?.trim()?.toLongOrNull()
    if (currentNumericRevision != null && incomingNumericRevision != null && incomingNumericRevision < currentNumericRevision) {
        return false
    }
    return true
}

internal fun timelineSnapshotVersion(page: TimelineSnapshotPage?): TimelineSnapshotVersion {
    return TimelineSnapshotVersion(
        revision = page?.snapshotRevision?.trim()?.takeIf { it.isNotEmpty() },
        highWatermark = page?.messages
            // seq 可能只是分页或旧协议排序字段；只有服务端明确给出的
            // conversationSeq 才能证明 conversation-wide watermark。
            ?.mapNotNull { message -> message.conversationSeq }
            ?.maxOrNull()
    )
}
