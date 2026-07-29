package com.rethinkingstudio.clawlink.core.state.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentAvailabilityTest {
    @Test
    fun localOriginalWinsAndIgnoresExplicitExpiryAnd410() {
        val result = resolveAttachmentAvailability(
            hasLocalOriginal = true,
            hasLocalCachedCopy = false,
            hasLocalThumbnail = false,
            hasRemoteReference = true,
            expiresAt = "2020-01-01T00:00:00Z",
            serverReportedExpired = true,
            nowEpochMs = 1_800_000_000_000L
        )

        assertEquals(AttachmentSource.LOCAL_ORIGINAL, result.source)
        assertTrue(result.canOpenOriginal)
        assertFalse(result.shouldAttemptRemoteDownload)
    }

    @Test
    fun thumbnailRemainsDisplayableAfterServerCleanupWithoutRemoteRetry() {
        val result = resolveAttachmentAvailability(
            hasLocalOriginal = false,
            hasLocalCachedCopy = false,
            hasLocalThumbnail = true,
            hasRemoteReference = true,
            expiresAt = null,
            serverReportedExpired = true
        )

        assertEquals(AttachmentSource.LOCAL_THUMBNAIL, result.source)
        assertTrue(result.canDisplayThumbnail)
        assertFalse(result.canOpenOriginal)
        assertFalse(result.shouldAttemptRemoteDownload)
    }

    @Test
    fun previouslyDownloadedStableLocalCopyWinsAfterRestartAndServerExpiry() {
        val result = resolveAttachmentAvailability(
            hasLocalOriginal = false,
            hasLocalCachedCopy = true,
            hasLocalThumbnail = false,
            hasRemoteReference = true,
            expiresAt = "2020-01-01T00:00:00Z",
            serverReportedExpired = true,
            nowEpochMs = 1_800_000_000_000L
        )

        assertEquals(AttachmentSource.LOCAL_CACHED_COPY, result.source)
        assertTrue(result.canOpenOriginal)
        assertFalse(result.shouldAttemptRemoteDownload)
    }

    @Test
    fun pastExpiresAtStillAttemptsRemoteBecauseDeviceClockIsNotAuthoritative() {
        val available = resolveAttachmentAvailability(
            hasLocalOriginal = false,
            hasLocalCachedCopy = false,
            hasLocalThumbnail = false,
            hasRemoteReference = true,
            expiresAt = "2030-01-01T00:00:00Z",
            serverReportedExpired = false,
            nowEpochMs = 1_800_000_000_000L
        )
        val pastMetadata = resolveAttachmentAvailability(
            hasLocalOriginal = false,
            hasLocalCachedCopy = false,
            hasLocalThumbnail = false,
            hasRemoteReference = true,
            expiresAt = "2020-01-01T00:00:00Z",
            serverReportedExpired = false,
            nowEpochMs = 1_800_000_000_000L
        )

        assertEquals(AttachmentSource.REMOTE, available.source)
        assertTrue(available.shouldAttemptRemoteDownload)
        assertEquals(AttachmentSource.REMOTE, pastMetadata.source)
        assertTrue(pastMetadata.shouldAttemptRemoteDownload)
    }

    @Test
    fun explicitServerExpiredStateOr410CacheMarkerStopsRemoteAttempt() {
        assertTrue(isExplicitAttachmentExpiredState("expired", null))
        assertTrue(isExplicitAttachmentExpiredState(null, "file_expired"))
        assertFalse(isExplicitAttachmentExpiredState("completed", "available"))

        val expired = resolveAttachmentAvailability(
            hasLocalOriginal = false,
            hasLocalCachedCopy = false,
            hasLocalThumbnail = false,
            hasRemoteReference = true,
            expiresAt = "2030-01-01T00:00:00Z",
            serverReportedExpired = true,
            nowEpochMs = 1_700_000_000_000L
        )

        assertEquals(AttachmentSource.SERVER_CLEANED, expired.source)
        assertFalse(expired.shouldAttemptRemoteDownload)
    }
}
