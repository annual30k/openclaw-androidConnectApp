package com.rethinkingstudio.clawlink.core.state.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineSnapshotVersionTest {
    @Test
    fun lowerHighWatermarkCannotReplaceNewerRealtimeTimeline() {
        assertFalse(
            shouldAcceptTimelineSnapshotVersion(
                current = TimelineSnapshotVersion("10", 100),
                incoming = TimelineSnapshotVersion("11", 99)
            )
        )
    }

    @Test
    fun lowerNumericRevisionIsRejectedEvenWithSameWatermark() {
        assertFalse(
            shouldAcceptTimelineSnapshotVersion(
                current = TimelineSnapshotVersion("10", 100),
                incoming = TimelineSnapshotVersion("9", 100)
            )
        )
    }

    @Test
    fun newerWatermarkAndOpaqueRevisionAreAccepted() {
        assertTrue(
            shouldAcceptTimelineSnapshotVersion(
                current = TimelineSnapshotVersion("etag-a", 100),
                incoming = TimelineSnapshotVersion("etag-b", 101)
            )
        )
    }

    @Test
    fun pageLocalSeqDoesNotBecomeConversationWatermark() {
        val version = timelineSnapshotVersion(
            TimelineSnapshotPage(
                snapshotRevision = "etag-a",
                messages = listOf(TimelineSnapshotMessage(seq = 9_999L))
            )
        )

        assertEquals("etag-a", version.revision)
        assertNull(version.highWatermark)
    }

    @Test
    fun explicitConversationSeqDefinesWatermark() {
        val version = timelineSnapshotVersion(
            TimelineSnapshotPage(
                messages = listOf(
                    TimelineSnapshotMessage(seq = 99_999L, conversationSeq = 40L),
                    TimelineSnapshotMessage(seq = 1L, conversationSeq = 41L)
                )
            )
        )

        assertEquals(41L, version.highWatermark)
    }
}
