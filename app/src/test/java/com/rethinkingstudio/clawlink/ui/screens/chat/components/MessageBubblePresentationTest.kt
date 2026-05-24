package com.rethinkingstudio.clawlink.ui.screens.chat.components

import org.junit.Assert.assertTrue
import org.junit.Test

class MessageBubblePresentationTest {
    @Test
    fun voiceTranscriptionWaitTextUsesStreamingIndicatorPresentation() {
        assertTrue(isStreamingIndicatorDisplayText("等待宿主机识别语音..."))
        assertTrue(isStreamingIndicatorDisplayText("Waiting for host transcription..."))
    }
}
