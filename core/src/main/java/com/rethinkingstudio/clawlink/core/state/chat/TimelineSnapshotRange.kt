package com.rethinkingstudio.clawlink.core.state.chat

internal data class TimelineSnapshotRange(
    val startSeq: Long?,
    val endSeqExclusive: Long?
) {
    val isBounded: Boolean
        get() = startSeq != null || endSeqExclusive != null

    fun contains(seq: Long?): Boolean {
        if (seq == null) return false
        if (startSeq != null && seq < startSeq) return false
        if (endSeqExclusive != null && seq >= endSeqExclusive) return false
        return true
    }

    companion object {
        fun fromCursors(startCursor: String?, endCursor: String?): TimelineSnapshotRange {
            return TimelineSnapshotRange(
                startSeq = cursorSeq(startCursor),
                endSeqExclusive = cursorSeq(endCursor)
            )
        }

        private fun cursorSeq(cursor: String?): Long? {
            val value = cursor?.trim() ?: return null
            if (!value.startsWith("seq:")) return null
            return value.removePrefix("seq:").toLongOrNull()
        }
    }
}
