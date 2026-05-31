package com.rethinkingstudio.clawlink.ui.screens.chat.components

import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
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
    fun assistantStreamingTypingMarkerUsesWaitingPresentationUntilRealReplyArrives() {
        assertTrue(shouldShowStreamingWaitState(MessageRole.assistant, MessageState.streaming))
        assertTrue(shouldUseStandaloneStreamingIndicator("[[clawlink:typing]]", hasFileBlocks = false, hasVoiceBlocks = false))
        assertFalse(shouldUseStandaloneStreamingIndicator("partial reply", hasFileBlocks = false, hasVoiceBlocks = false))
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
    fun mixedMediaMessagesUseExpandedBubbleWidth() {
        assertTrue(
            shouldUseExpandedMixedMediaBubble(
                displayText = "这张图里有什么",
                hasFileBlocks = true,
                hasVoiceBlocks = false
            )
        )
        assertFalse(
            shouldUseExpandedMixedMediaBubble(
                displayText = "",
                hasFileBlocks = true,
                hasVoiceBlocks = false
            )
        )
        assertFalse(
            shouldUseExpandedMixedMediaBubble(
                displayText = "plain text",
                hasFileBlocks = false,
                hasVoiceBlocks = false
            )
        )
    }

    @Test
    fun mixedMediaBubbleWidthUsesAvailableRowSpace() {
        assertEquals(336.dp, mixedMediaBubbleWidth(390.dp))
        assertEquals(560.dp, mixedMediaBubbleWidth(728.dp))
        assertEquals(0.dp, mixedMediaBubbleWidth(44.dp))
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
