package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

@Ignore("Legacy sourceRun/timestamp anchoring was removed; relay canonical timeline owns tool and attachment order.")
class ChatSourceRunOrderingTest {
    @Test
    fun ordersMixedToolAssistantTextAndImageAroundSourceRun() {
        val user = ChatMessage(
            id = "user-run-1",
            role = MessageRole.user,
            content = "把图片发过来",
            runId = "local-user-run-1",
            sortTimestamp = 10.0
        )
        val tool = ChatMessage(
            id = "tool-call-1",
            role = MessageRole.tool,
            content = "sending image",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "tool_result",
                    name = "send_image",
                    toolCallId = "call-1",
                    text = "sending image"
                )
            ),
            runId = "call-1",
            sortTimestamp = 10.0009
        )
        val assistantText = ChatMessage(
            id = "assistant-run-1",
            role = MessageRole.assistant,
            state = MessageState.completed,
            content = "图片发过去了。",
            runId = "run-1",
            sortTimestamp = 10.001
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
                    sourceRunId = "run-1"
                )
            ),
            runId = "file-reply-image",
            sortTimestamp = 9.0
        )

        val ordered = orderMessagesWithSourceRunAnchors(
            listOf(imageReply, assistantText, tool, user)
        )

        assertEquals(
            listOf("local-user-run-1", "call-1", "run-1", "file-reply-image"),
            ordered.map { it.runId }
        )
        assertTrue((ordered[3].sortTimestamp ?: 0.0) > (ordered[2].sortTimestamp ?: 0.0))
    }

    @Test
    fun ordersToolBeforeTransientAssistantPlaceholderEvenWhenToolTimestampIsLater() {
        val user = ChatMessage(
            id = "user-run-weather",
            role = MessageRole.user,
            content = "帮我看看上杭今天的天气",
            runId = "local-user-run-weather",
            sortTimestamp = 10.0
        )
        val pendingAssistant = ChatMessage(
            id = "assistant-run-weather",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "正在同步回复...",
            runId = "run-weather",
            sortTimestamp = 10.001
        )
        val tool = ChatMessage(
            id = "tool-weather",
            role = MessageRole.tool,
            content = "weather result",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "tool_result",
                    name = "web_search",
                    toolCallId = "call-weather",
                    text = "weather result"
                )
            ),
            runId = "call-weather",
            sortTimestamp = 10.002
        )

        val ordered = orderMessagesWithSourceRunAnchors(
            listOf(user, pendingAssistant, tool)
        )

        assertEquals(
            listOf("local-user-run-weather", "call-weather", "run-weather"),
            ordered.map { it.runId }
        )
    }

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
