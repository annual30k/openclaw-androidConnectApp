package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSourceRunOrderingTest {
    @Test
    fun anchorsAssistantFileHistoryAfterSourceRun() {
        val localVoice = ChatMessage(
            id = "local-voice",
            role = MessageRole.user,
            content = "voice-input.m4a",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "voice",
                    fileName = "voice-input.m4a",
                    mimeType = "audio/mp4",
                    downloadUrl = "file:///tmp/voice-input.m4a"
                )
            ),
            runId = "local-user-run-voice",
            sortTimestamp = 100.0
        )
        val pendingAssistant = ChatMessage(
            id = "assistant-run-voice",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "正在同步回复...",
            runId = "run-voice",
            sortTimestamp = 100.001
        )
        val imageReply = ChatMessage(
            id = "file-reply-image",
            role = MessageRole.assistant,
            content = "reply.jpg",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "file",
                    fileId = "reply-image",
                    fileName = "reply.jpg",
                    mimeType = "image/jpeg",
                    sourceRunId = "run-voice"
                )
            ),
            runId = "file-reply-image",
            sortTimestamp = 99.0
        )

        val merged = mergeHistoryWithCurrentMessages(
            historyMessages = listOf(imageReply),
            currentMessages = listOf(localVoice, pendingAssistant),
            currentStreamingMessageId = pendingAssistant.id,
            isTrackedPendingAssistantMessageId = { it == pendingAssistant.id }
        )

        assertEquals(listOf("local-user-run-voice", "run-voice", "file-reply-image"), merged.map { it.runId })
        assertTrue((merged[2].sortTimestamp ?: 0.0) > (pendingAssistant.sortTimestamp ?: 0.0))
    }
}
