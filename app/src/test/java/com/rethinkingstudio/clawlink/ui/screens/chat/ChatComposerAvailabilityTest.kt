package com.rethinkingstudio.clawlink.ui.screens.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatComposerAvailabilityTest {
    @Test
    fun activeReplyAllowsLocalFollowUpQueueWhenPresenceTemporarilyDrops() {
        val availability = resolveChatComposerAvailability(
            hasSelectedGateway = true,
            sessionKey = "main",
            isChatChainReady = false,
            isStreaming = true,
            isStoppingRun = false,
            isUploadingAttachment = false,
            isVoiceInputBusy = false
        )

        assertTrue(availability.hasActiveSession)
        assertTrue(availability.canEditComposer)
        assertTrue(availability.canSendMessage)
    }

    @Test
    fun disconnectedIdleSessionCannotStartANewRun() {
        val availability = resolveChatComposerAvailability(
            hasSelectedGateway = true,
            sessionKey = "main",
            isChatChainReady = false,
            isStreaming = false,
            isStoppingRun = false,
            isUploadingAttachment = false,
            isVoiceInputBusy = false
        )

        assertFalse(availability.hasActiveSession)
        assertFalse(availability.canEditComposer)
        assertFalse(availability.canSendMessage)
    }

    @Test
    fun stoppingRunKeepsComposerLocked() {
        val availability = resolveChatComposerAvailability(
            hasSelectedGateway = true,
            sessionKey = "main",
            isChatChainReady = true,
            isStreaming = true,
            isStoppingRun = true,
            isUploadingAttachment = false,
            isVoiceInputBusy = false
        )

        assertTrue(availability.hasActiveSession)
        assertFalse(availability.canEditComposer)
        assertFalse(availability.canSendMessage)
    }
}
