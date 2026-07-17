package com.rethinkingstudio.clawlink.ui.screens.chat.components

import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import com.rethinkingstudio.clawlink.ui.screens.chat.isUserAuthoredMessage
import com.rethinkingstudio.clawlink.ui.screens.chat.isLocalUserMessage
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageBubblePresentationTest {
    @Test
    fun userAuthoredMessageIgnoresSourceForBubbleOwnership() {
        val pcUserMessage = ChatMessage(
            id = "pc-user-1",
            role = MessageRole.user,
            runId = "run-pc-user",
            source = "live"
        )
        val assistantMessage = ChatMessage(
            id = "assistant-1",
            role = MessageRole.assistant,
            runId = "run-assistant",
            source = "live"
        )

        assertTrue(pcUserMessage.isUserAuthoredMessage())
        assertFalse(pcUserMessage.isLocalUserMessage())
        assertFalse(assistantMessage.isUserAuthoredMessage())
    }

    @Test
    fun localUserMessageRequiresLocalSourceOrLocalRunId() {
        assertTrue(
            ChatMessage(
                id = "local-user-1",
                role = MessageRole.user,
                runId = "local-user-client-run",
                source = "local"
            ).isLocalUserMessage()
        )
        assertFalse(
            ChatMessage(
                id = "pc-user-1",
                role = MessageRole.user,
                runId = "run-pc-user",
                source = "live"
            ).isLocalUserMessage()
        )
        assertFalse(
            ChatMessage(
                id = "history-user-1",
                role = MessageRole.user,
                runId = "history-user-1",
                source = "history"
            ).isLocalUserMessage()
        )
    }

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
    fun adaptiveMixedMediaBubbleWidthUsesContentAndCapsAtMaximum() {
        assertEquals(322.dp, adaptiveMixedMediaBubbleWidth(336.dp, listOf(94.dp, 290.dp)))
        assertEquals(210.dp, adaptiveMixedMediaBubbleWidth(336.dp, listOf(178.dp)))
        assertEquals(336.dp, adaptiveMixedMediaBubbleWidth(336.dp, listOf(420.dp)))
    }

    @Test
    fun repeatedLegacyMediaProjectionUsesOneCanonicalPrompt() {
        val blocks = listOf(
            RelayChatContentBlock(type = "text", contentBlockId = "blk_prompt", text = "分析一下这个图片"),
            RelayChatContentBlock(type = "image", contentBlockId = "blk_image", fileId = "file_image")
        )

        assertEquals(
            "分析一下这个图片",
            coalescedMixedMediaDisplayText(
                List(4) { "分析一下这个图片" }.joinToString("\n\n"),
                blocks
            )
        )
    }

    @Test
    fun distinctStableRepeatedTextBlocksRemainVisible() {
        val blocks = listOf(
            RelayChatContentBlock(type = "text", contentBlockId = "blk_first", text = "再说一次"),
            RelayChatContentBlock(type = "text", contentBlockId = "blk_second", text = "再说一次"),
            RelayChatContentBlock(type = "image", contentBlockId = "blk_image", fileId = "file_image")
        )

        assertEquals("再说一次\n\n再说一次", coalescedMixedMediaDisplayText("再说一次\n\n再说一次", blocks))
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

    @Test
    fun streamingToolMessagesStartCollapsed() {
        assertFalse(shouldStartToolMessageExpanded(showInvocationProcess = true, state = MessageState.streaming))
        assertFalse(shouldStartToolMessageExpanded(showInvocationProcess = false, state = MessageState.streaming))
        assertFalse(shouldStartToolMessageExpanded(showInvocationProcess = true, state = MessageState.completed))
    }
}
