package com.rethinkingstudio.clawlink.ui.screens.chat.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentDownloadSemanticsTest {
    @Test
    fun http410IsServerCleanup() {
        assertTrue(isRemoteAttachmentExpiredResponse(410, ""))
    }

    @Test
    fun explicitFileExpiredPayloadIsServerCleanup() {
        assertTrue(isRemoteAttachmentExpiredResponse(404, "{\"code\":\"file_expired\"}"))
    }

    @Test
    fun ordinaryTransientFailureIsNotServerCleanup() {
        assertFalse(isRemoteAttachmentExpiredResponse(503, "temporarily unavailable"))
    }
}
