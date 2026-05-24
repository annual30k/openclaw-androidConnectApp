package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import com.rethinkingstudio.clawlink.core.network.dto.ChatHistoryItem
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHistoryMergeHelpersTest {
    @Test
    fun buildsHistoryMessagesWithMonotonicSortTimestampsFromTranscriptOrder() {
        val messages = buildHistoryMessagesFromItems(
            listOf(
                ChatHistoryItem(
                    id = "history-user",
                    role = "user",
                    content = JsonPrimitive("把微信图片发过来"),
                    createdAt = "2026-05-22T08:06:02.287Z"
                ),
                ChatHistoryItem(
                    id = "history-tool",
                    role = "tool",
                    content = JsonPrimitive("""{"output":"sent"}"""),
                    createdAt = "2026-05-22T08:05:48.577Z"
                ),
                ChatHistoryItem(
                    id = "history-assistant",
                    role = "assistant",
                    content = JsonPrimitive("发过去了"),
                    createdAt = "2026-05-22T08:06:03.326Z"
                )
            )
        )

        val ordered = orderMessagesWithSourceRunAnchors(messages)

        assertEquals(listOf("history-user", "history-tool", "history-assistant"), ordered.map { it.runId })
        assertTrue((messages[1].sortTimestamp ?: 0.0) > (messages[0].sortTimestamp ?: 0.0))
    }

    @Test
    fun doesNotLiftOldFileHistoryItemAfterNewerTurn() {
        val messages = buildHistoryMessagesFromItems(
            listOf(
                ChatHistoryItem(
                    id = "today-user",
                    role = "user",
                    content = JsonPrimitive("你好"),
                    createdAt = "2026-05-24T07:09:00.000Z"
                ),
                ChatHistoryItem(
                    id = "today-assistant",
                    role = "assistant",
                    content = JsonPrimitive("Alex，我在。"),
                    createdAt = "2026-05-24T07:09:03.000Z"
                ),
                ChatHistoryItem(
                    id = "old-image",
                    role = "assistant",
                    content = JsonPrimitive("微信图片.jpg"),
                    contentBlocks = listOf(
                        RelayChatContentBlock(
                            type = "image",
                            fileId = "old-image-file",
                            fileName = "微信图片.jpg",
                            mimeType = "image/jpeg"
                        )
                    ),
                    createdAt = "2026-05-23T11:14:00.000Z"
                )
            )
        )

        val ordered = orderMessagesWithSourceRunAnchors(messages)

        assertEquals(listOf("old-image", "today-user", "today-assistant"), ordered.map { it.runId })
        assertTrue((messages[2].sortTimestamp ?: 0.0) < (messages[0].sortTimestamp ?: 0.0))
    }

    @Test
    fun filtersProtocolTypingMarkersFromHistoryMessages() {
        val messages = buildHistoryMessagesFromItems(
            listOf(
                ChatHistoryItem(
                    id = "typing",
                    role = "assistant",
                    content = JsonPrimitive("[[clawlink:typing]][[clawlink:typing]]"),
                    createdAt = "2026-05-24T08:00:00.000Z"
                ),
                ChatHistoryItem(
                    id = "answer",
                    role = "assistant",
                    content = JsonPrimitive("final answer"),
                    createdAt = "2026-05-24T08:00:03.000Z"
                )
            )
        )

        assertEquals(listOf("answer"), messages.map { it.id })
    }

    @Test
    fun treatsProtocolTypingMarkersAsTransientAssistantPlaceholders() {
        val message = ChatMessage(
            id = "typing",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "[[clawlink:typing]][[clawlink:typing]]",
            runId = "run",
            sortTimestamp = 10.0
        )

        assertTrue(isTransientAssistantPlaceholder(message))
    }

    @Test
    fun treatsVoiceTranscriptionWaitTextAsTransientAssistantPlaceholder() {
        val zhMessage = ChatMessage(
            id = "voice-wait-zh",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "等待宿主机识别语音...",
            runId = "run",
            sortTimestamp = 10.0
        )
        val enMessage = zhMessage.copy(
            id = "voice-wait-en",
            content = "Waiting for host transcription..."
        )

        assertTrue(isTransientAssistantPlaceholder(zhMessage))
        assertTrue(isTransientAssistantPlaceholder(enMessage))
    }

    @Test
    fun replacesLateVoiceTranscriptHistoryTextWithLocalVoiceMessage() {
        val historyTranscript = ChatMessage(
            id = "history-transcript",
            role = MessageRole.user,
            content = "你可以做什么？",
            runId = "voice-client-run-1",
            sortTimestamp = 1400.0
        )
        val historyAssistant = ChatMessage(
            id = "history-assistant",
            role = MessageRole.assistant,
            content = "我可以帮你处理本机任务。",
            runId = "assistant-client-run-1",
            sortTimestamp = 1401.0
        )
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
            runId = "local-user-voice-client-run-1",
            sortTimestamp = 1000.0
        )

        val merged = mergeHistoryWithCurrentMessages(
            historyMessages = listOf(historyTranscript, historyAssistant),
            currentMessages = listOf(localVoice),
            currentStreamingMessageId = null,
            isTrackedPendingAssistantMessageId = { false }
        )

        assertEquals(listOf("local-user-voice-client-run-1", "assistant-client-run-1"), merged.map { it.runId })
        assertTrue(merged.first().hasVoiceContent)
        assertEquals("你可以做什么？", merged.first().voiceTranscriptText)
        assertFalse(merged.any { it.id == historyTranscript.id })
    }

    @Test
    fun suppressesStreamingPendingAssistantWhenHistoryResolvesTurn() {
        val historyUser = ChatMessage(
            id = "history-user",
            role = MessageRole.user,
            content = "hello",
            runId = "history-user",
            sortTimestamp = 10.0
        )
        val historyAssistant = ChatMessage(
            id = "history-assistant",
            role = MessageRole.assistant,
            content = "final answer",
            runId = "history-assistant",
            sortTimestamp = 14.0
        )
        val localUser = ChatMessage(
            id = "local-user",
            role = MessageRole.user,
            content = "hello",
            runId = "local-user-client-run",
            sortTimestamp = 10.0
        )
        val pendingAssistant = ChatMessage(
            id = "pending-assistant",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "正在连接...",
            runId = "client-run",
            sortTimestamp = 11.0
        )

        val merged = mergeHistoryWithCurrentMessages(
            historyMessages = listOf(historyUser, historyAssistant),
            currentMessages = listOf(localUser, pendingAssistant),
            currentStreamingMessageId = pendingAssistant.id,
            isTrackedPendingAssistantMessageId = { it == pendingAssistant.id }
        )

        assertEquals(listOf("local-user", "history-assistant"), merged.map { it.id })
        assertFalse(merged.any { it.id == pendingAssistant.id })
    }

    @Test
    fun keepsStreamingPendingAssistantWhenHistoryHasOnlyUserEcho() {
        val historyUser = ChatMessage(
            id = "history-user",
            role = MessageRole.user,
            content = "hello",
            runId = "history-user",
            sortTimestamp = 10.0
        )
        val localUser = ChatMessage(
            id = "local-user",
            role = MessageRole.user,
            content = "hello",
            runId = "local-user-client-run",
            sortTimestamp = 10.0
        )
        val pendingAssistant = ChatMessage(
            id = "pending-assistant",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "正在连接...",
            runId = "client-run",
            sortTimestamp = 11.0
        )

        val merged = mergeHistoryWithCurrentMessages(
            historyMessages = listOf(historyUser),
            currentMessages = listOf(localUser, pendingAssistant),
            currentStreamingMessageId = pendingAssistant.id,
            isTrackedPendingAssistantMessageId = { it == pendingAssistant.id }
        )

        assertEquals(listOf("local-user", "pending-assistant"), merged.map { it.id })
        assertTrue(merged.last().state == MessageState.streaming)
    }

    @Test
    fun mergesHistoryMediaAttachmentEchoWithMatchingLocalUserBubble() {
        val historyUser = ChatMessage(
            id = "history-user",
            role = MessageRole.user,
            content = """
                分析一下这张照片

                [media attached: /Users/example/photo.jpg (image/jpeg) | /Users/example/photo.jpg]
            """.trimIndent(),
            runId = "history-user",
            sortTimestamp = 10.0
        )
        val historyAssistant = ChatMessage(
            id = "history-assistant",
            role = MessageRole.assistant,
            content = "这是一张像素风图片。",
            runId = "history-assistant",
            sortTimestamp = 12.0
        )
        val localUser = ChatMessage(
            id = "local-user",
            role = MessageRole.user,
            content = "分析一下这张照片",
            runId = "local-user-run-1",
            sortTimestamp = 10.0
        )
        val pendingAssistant = ChatMessage(
            id = "pending-assistant",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "正在连接...",
            runId = "run-1",
            sortTimestamp = 10.001
        )

        val merged = mergeHistoryWithCurrentMessages(
            historyMessages = listOf(historyUser, historyAssistant),
            currentMessages = listOf(localUser, pendingAssistant),
            currentStreamingMessageId = pendingAssistant.id,
            isTrackedPendingAssistantMessageId = { it == pendingAssistant.id }
        )

        assertEquals(listOf("local-user", "history-assistant"), merged.map { it.id })
        assertEquals("分析一下这张照片", merged.first().content)
        assertFalse(merged.any { it.content.contains("[media attached:") })
    }

    @Test
    fun keepsLocalMobileFileSortWhenHistoryEchoArrivesAfterAssistant() {
        val fileBlock = RelayChatContentBlock(
            type = "image",
            fileId = "photo-1",
            fileName = "photo.jpg",
            mimeType = "image/jpeg",
            downloadUrl = "file:///tmp/photo.jpg"
        )
        val historyUser = ChatMessage(
            id = "history-user",
            role = MessageRole.user,
            content = "分析一下这张照片",
            runId = "history-user",
            sortTimestamp = 100.001
        )
        val historyAssistant = ChatMessage(
            id = "history-assistant",
            role = MessageRole.assistant,
            content = "这是一张像素风图片。",
            runId = "history-assistant",
            sortTimestamp = 102.0
        )
        val lateHistoryFile = ChatMessage(
            id = "history-file",
            role = MessageRole.user,
            content = "photo.jpg",
            contentBlocks = listOf(fileBlock.copy(downloadUrl = "/api/mobile/files/photo-1")),
            runId = "file-photo-1",
            sortTimestamp = 103.0
        )
        val localFile = ChatMessage(
            id = "local-file",
            role = MessageRole.user,
            content = "photo.jpg",
            contentBlocks = listOf(fileBlock),
            runId = "file-photo-1",
            sortTimestamp = 100.0
        )
        val localUser = ChatMessage(
            id = "local-user",
            role = MessageRole.user,
            content = "分析一下这张照片",
            runId = "local-user-run-1",
            sortTimestamp = 100.001
        )

        val merged = mergeHistoryWithCurrentMessages(
            historyMessages = listOf(historyUser, historyAssistant, lateHistoryFile),
            currentMessages = listOf(localFile, localUser),
            currentStreamingMessageId = null,
            isTrackedPendingAssistantMessageId = { false }
        )

        assertEquals(listOf("file-photo-1", "local-user-run-1", "history-assistant"), merged.map { it.runId })
        assertEquals(100.0, merged.first().sortTimestamp ?: 0.0, 0.000001)
        assertEquals("file:///tmp/photo.jpg", merged.first().fileContentBlocks.first().downloadUrl)
    }

    @Test
    fun anchorsHistoryMobileFileBeforeUserTextWhenTextContainsMediaReference() {
        val messages = buildHistoryMessagesFromItems(
            listOf(
                ChatHistoryItem(
                    id = "history-user",
                    role = "user",
                    content = JsonPrimitive(
                        """
                            分析一下这张照片

                            [media attached: /Users/example/photo.jpg (image/jpeg) | /Users/example/photo.jpg]
                        """.trimIndent()
                    ),
                    createdAt = "2026-05-24T10:00:00.000Z"
                ),
                ChatHistoryItem(
                    id = "history-assistant",
                    role = "assistant",
                    content = JsonPrimitive("这是一张像素风图片。"),
                    createdAt = "2026-05-24T10:00:02.000Z"
                ),
                ChatHistoryItem(
                    id = "history-file",
                    role = "user",
                    content = JsonPrimitive("photo.jpg"),
                    contentBlocks = listOf(
                        RelayChatContentBlock(
                            type = "image",
                            fileId = "photo-1",
                            fileName = "photo.jpg",
                            mimeType = "image/jpeg",
                            downloadUrl = "/api/mobile/files/photo-1"
                        )
                    ),
                    createdAt = "2026-05-24T10:00:03.000Z"
                )
            )
        )

        val ordered = orderMessagesWithSourceRunAnchors(messages)

        assertEquals(listOf("history-file", "history-user", "history-assistant"), ordered.map { it.runId })
        assertTrue((ordered[0].sortTimestamp ?: 0.0) < (ordered[1].sortTimestamp ?: 0.0))
        assertEquals("分析一下这张照片", ordered[1].content)
    }
}
