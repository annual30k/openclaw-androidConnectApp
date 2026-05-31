package com.rethinkingstudio.clawlink.ui.screens.chat.components

import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageBubblePresentationTest {
    @Test
    fun voiceTranscriptionWaitTextUsesStreamingIndicatorPresentation() {
        assertTrue(isStreamingIndicatorDisplayText("等待宿主机识别语音..."))
        assertTrue(isStreamingIndicatorDisplayText("Waiting for host transcription..."))
    }

    @Test
    fun protocolTypingMarkerUsesStreamingIndicatorPresentation() {
        assertTrue(isStreamingIndicatorDisplayText("[[clawlink:typing]]"))
    }

    @Test
    fun connectingPlaceholdersUseStreamingIndicatorPresentation() {
        assertTrue(isStreamingIndicatorDisplayText("正在连接..."))
        assertTrue(isStreamingIndicatorDisplayText("连接中"))
        assertTrue(isStreamingIndicatorDisplayText("Connecting..."))
    }

    @Test
    fun assistantStreamingPartialReplyKeepsWaitingPresentation() {
        assertTrue(shouldShowStreamingWaitState(MessageRole.assistant, MessageState.streaming))
        assertTrue(shouldUseStandaloneStreamingIndicator("partial reply", hasFileBlocks = false, hasVoiceBlocks = false))
        assertFalse(
            shouldShowInlineStreamingIndicator(
                role = MessageRole.assistant,
                state = MessageState.streaming,
                displayText = "partial reply",
                hasFileBlocks = false,
                hasVoiceBlocks = false
            )
        )
    }

    @Test
    fun standaloneStreamingIndicatorDoesNotShowFooterText() {
        assertFalse(
            shouldShowMessageFooter(
                role = MessageRole.assistant,
                state = MessageState.streaming,
                displayText = "正在同步回复...",
                hasFileBlocks = false,
                hasVoiceBlocks = false,
                isToolMessage = false
            )
        )
        assertFalse(
            shouldShowMessageFooter(
                role = MessageRole.assistant,
                state = MessageState.streaming,
                displayText = "[[clawlink:typing]]",
                hasFileBlocks = false,
                hasVoiceBlocks = false,
                isToolMessage = false
            )
        )
    }

    @Test
    fun assistantCompletedReplyClearsWaitingPresentation() {
        assertFalse(shouldShowStreamingWaitState(MessageRole.assistant, MessageState.completed))
        assertFalse(
            shouldShowInlineStreamingIndicator(
                role = MessageRole.assistant,
                state = MessageState.completed,
                displayText = "final reply",
                hasFileBlocks = false,
                hasVoiceBlocks = false
            )
        )
    }
}
