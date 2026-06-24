package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.ChatSessionItem
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import com.rethinkingstudio.clawlink.core.domain.NotificationPort
import com.rethinkingstudio.clawlink.core.network.RelayAPIClient
import com.rethinkingstudio.clawlink.core.network.dto.ChatHistoryResponse
import com.rethinkingstudio.clawlink.core.network.dto.ChatHistoryItem
import com.rethinkingstudio.clawlink.core.network.transport.RelayWebSocketClient
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

class ChatHistoryVoiceAndPendingTest {
    @Ignore("Legacy pending resolution by transcript/content matching was removed; Relay canonical order is required.")
    @Test
    fun suppressesVoiceStreamingPendingAssistantWhenHistoryContainsTranscriptAndAssistantReply() {
        val transcript = "你可以做什么"
        val historyTranscript = ChatMessage(
            id = "voice-run-1",
            role = MessageRole.user,
            content = transcript,
            runId = "voice-run-1",
            sortTimestamp = 100.0
        )
        val historyAssistant = ChatMessage(
            id = "history-assistant-voice",
            role = MessageRole.assistant,
            content = "我可以帮你处理本机任务。",
            runId = "history-assistant-voice",
            sortTimestamp = 104.0
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
                    downloadUrl = "file:///tmp/voice-input.m4a",
                    transcript = transcript
                )
            ),
            runId = "local-user-voice-run-1",
            sortTimestamp = 99.0
        )
        val pendingAssistant = ChatMessage(
            id = "pending-assistant",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = protocolTypingMarkerText,
            runId = "voice-run-1",
            sortTimestamp = 101.0
        )

        val merged = mergeHistoryWithCurrentMessages(
            historyMessages = listOf(historyTranscript, historyAssistant),
            currentMessages = listOf(localVoice, pendingAssistant),
            currentStreamingMessageId = pendingAssistant.id,
            isTrackedPendingAssistantMessageId = { it == pendingAssistant.id }
        )

        assertEquals(listOf("local-user-voice-run-1", "history-assistant-voice"), merged.map { it.runId })
        assertTrue(merged.first().hasVoiceContent)
        assertEquals(transcript, merged.first().voiceTranscriptText)
        assertFalse(merged.any { it.id == pendingAssistant.id })
        assertFalse(merged.any { it.role == MessageRole.user && !it.hasVoiceContent && it.content == transcript })
    }

    @Ignore("Legacy pending resolution by transcript/content matching was removed; Relay canonical order is required.")
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

    @Ignore("Legacy delayed Hermes image prompt echo matching was removed; Relay canonical order is required.")
    @Test
    fun coalescesDelayedHermesImagePromptEcho() {
        val historyUser = ChatMessage(
            id = "history-hermes-delayed-image-user",
            role = MessageRole.user,
            content = "帮我分析一下这张图片",
            runId = "history-hermes-delayed-image-user",
            sortTimestamp = 1_780_215_120.0
        )
        val historyAssistant = ChatMessage(
            id = "history-hermes-delayed-image-answer",
            role = MessageRole.assistant,
            content = "这是一张城市夜景。",
            runId = "history-hermes-delayed-image-answer",
            sortTimestamp = 1_780_215_123.0
        )
        val localUser = ChatMessage(
            id = "local-user",
            role = MessageRole.user,
            content = "帮我分析一下这张图片",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "file",
                    text = "album.jpeg",
                    fileName = "album.jpeg",
                    mimeType = "image/jpeg",
                    downloadUrl = "file:///tmp/album.jpeg"
                )
            ),
            runId = "local-user-hermes-delayed-image",
            sortTimestamp = 1_780_214_917.0
        )
        val pendingAssistant = ChatMessage(
            id = "pending-assistant",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = protocolTypingMarkerText,
            runId = "client-run-hermes-delayed-image",
            sortTimestamp = 1_780_214_917.001
        )

        val merged = mergeHistoryWithCurrentMessages(
            historyMessages = listOf(historyUser, historyAssistant),
            currentMessages = listOf(localUser, pendingAssistant),
            currentStreamingMessageId = pendingAssistant.id,
            isTrackedPendingAssistantMessageId = { it == pendingAssistant.id }
        )

        assertEquals(
            listOf("local-user", "history-hermes-delayed-image-answer"),
            merged.map { it.id }
        )
        assertEquals("帮我分析一下这张图片", merged.first().content)
        assertEquals("file:///tmp/album.jpeg", merged.first().fileContentBlocks.first().downloadUrl)
        assertFalse(merged.any { it.id == pendingAssistant.id })
        assertFalse(merged.any { it.id == historyUser.id })
    }

    @Ignore("Legacy completed assistant content matching was removed; Relay canonical order is required.")
    @Test
    fun keepsCompletedLiveAssistantWhenOnlyContentMatchesOldSyntheticHistory() {
        val historyUser = ChatMessage(
            id = "history-user",
            role = MessageRole.user,
            content = "Android smoke 104037",
            runId = "history-user",
            sortTimestamp = 0.001
        )
        val historyAssistant = ChatMessage(
            id = "history-assistant",
            role = MessageRole.assistant,
            content = "看起来像是一个测试用例编号或 Bug 工单号？",
            runId = "history-assistant",
            sortTimestamp = 0.002
        )
        val localUser = ChatMessage(
            id = "local-user",
            role = MessageRole.user,
            content = "Android smoke 104037",
            runId = "local-user-client-run",
            sortTimestamp = 1_780_000_000.0
        )
        val liveAssistant = ChatMessage(
            id = "live-assistant",
            role = MessageRole.assistant,
            state = MessageState.completed,
            content = "看起来像是一个测试用例编号或 Bug 工单号？",
            runId = "client-run",
            sortTimestamp = 1_780_000_002.0
        )

        val merged = mergeHistoryWithCurrentMessages(
            historyMessages = listOf(historyUser, historyAssistant),
            currentMessages = listOf(localUser, liveAssistant),
            currentStreamingMessageId = null,
            isTrackedPendingAssistantMessageId = { false }
        )

        assertEquals(
            listOf("history-user", "history-assistant", "local-user", "live-assistant"),
            merged.map { it.id }
        )
    }

    @Ignore("Legacy pending resolution by tool-heavy history windows was removed; Relay canonical order is required.")
    @Test
    fun suppressesStreamingPendingAssistantWhenToolHeavyHistoryWindowStartsAfterTriggeringUser() {
        val toolMessages = (0 until 54).map { index ->
            ChatMessage(
                id = "history-tool-$index",
                role = MessageRole.tool,
                content = "tool output $index",
                runId = "history-tool-$index",
                sortTimestamp = 10.1 + index * 0.001
            )
        }
        val historyAssistant = ChatMessage(
            id = "history-assistant",
            role = MessageRole.assistant,
            content = "final answer",
            runId = "history-assistant",
            sortTimestamp = 10.9
        )
        val localUser = ChatMessage(
            id = "local-user",
            role = MessageRole.user,
            content = "analyze this project",
            runId = "local-user-client-run",
            sortTimestamp = 10.0
        )
        val pendingAssistant = ChatMessage(
            id = "pending-assistant",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "正在同步最终内容...",
            runId = "client-run",
            sortTimestamp = 10.001
        )

        val merged = mergeHistoryWithCurrentMessages(
            historyMessages = toolMessages.takeLast(49) + historyAssistant,
            currentMessages = listOf(localUser, pendingAssistant),
            currentStreamingMessageId = pendingAssistant.id,
            isTrackedPendingAssistantMessageId = { it == pendingAssistant.id }
        )

        assertEquals(localUser.id, merged.first().id)
        assertTrue(merged.any { it.id == historyAssistant.id })
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

    @Ignore("Legacy media attachment echo matching was removed; Relay canonical order is required.")
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

    @Ignore("Legacy mobile file/history echo coalescing was removed; relay canonical timeline owns attachment position.")
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

        assertEquals(listOf("file-photo-1", "history-assistant"), merged.map { it.runId })
        assertEquals(100.0, merged.first().sortTimestamp ?: 0.0, 0.000001)
        assertEquals("分析一下这张照片", merged.first().content)
        assertEquals("file:///tmp/photo.jpg", merged.first().fileContentBlocks.first().downloadUrl)
    }

    @Ignore("Legacy failed upload placeholder matching was removed; Relay canonical order is required.")
    @Test
    fun dropsFailedUploadPlaceholderWhenCompletedImageExistsInHistory() {
        val historyImage = ChatMessage(
            id = "history-image",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "photo.jpg",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    fileId = "file-photo",
                    fileName = "photo.jpg",
                    mimeType = "image/jpeg",
                    sizeBytes = 12345,
                    downloadUrl = "/api/mobile/files/file-photo",
                    gatewayId = "gw-hermes",
                    sessionKey = "android-e2e-hermes"
                )
            ),
            runId = "file-file-photo",
            sortTimestamp = 101.0
        )
        val failedUpload = ChatMessage(
            id = "upload-photo",
            role = MessageRole.user,
            state = MessageState.failed,
            content = "photo.jpg",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    fileName = "photo.jpg",
                    mimeType = "image/jpeg",
                    sizeBytes = 12345,
                    downloadUrl = "file:///tmp/photo.jpg",
                    gatewayId = "gw-hermes",
                    sessionKey = "android-e2e-hermes"
                )
            ),
            runId = "upload-photo",
            sortTimestamp = 100.0
        )

        val merged = mergeHistoryWithCurrentMessages(
            historyMessages = listOf(historyImage),
            currentMessages = listOf(failedUpload),
            currentStreamingMessageId = null,
            isTrackedPendingAssistantMessageId = { false }
        )

        assertEquals(listOf("file-file-photo"), merged.map { it.runId })
        assertEquals(MessageState.completed, merged.single().state)
        assertEquals("file-photo", merged.single().fileContentBlocks.single().fileId)
    }

    @Ignore("Legacy local file cleanup by desktop text matching was removed; Relay canonical order is required.")
    @Test
    fun dropsCompletedLocalFileWhenDesktopHistoryDoesNotReferenceIt() {
        val fileBlock = RelayChatContentBlock(
            type = "image",
            fileId = "relay-only-photo",
            fileName = "photo.jpg",
            mimeType = "image/jpeg",
            downloadUrl = "file:///tmp/photo.jpg"
        )
        val historyAssistant = ChatMessage(
            id = "history-assistant",
            role = MessageRole.assistant,
            content = "DONE",
            runId = "history-assistant",
            sortTimestamp = 102.0
        )
        val completedLocalFile = ChatMessage(
            id = "local-file",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "photo.jpg",
            contentBlocks = listOf(fileBlock),
            runId = "file-relay-only-photo",
            sortTimestamp = 103.0
        )

        val merged = mergeHistoryWithCurrentMessages(
            historyMessages = listOf(historyAssistant),
            currentMessages = listOf(completedLocalFile),
            currentStreamingMessageId = null,
            isTrackedPendingAssistantMessageId = { false }
        )

        assertEquals(listOf("history-assistant"), merged.map { it.id })
    }

    @Ignore("Legacy media-reference anchoring was removed; relay canonical timeline owns attachment position.")
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

        assertEquals(listOf("history-file", "history-assistant"), ordered.map { it.runId })
        assertTrue((ordered[0].sortTimestamp ?: 0.0) < (ordered[1].sortTimestamp ?: 0.0))
        assertEquals("分析一下这张照片", ordered[0].content)
        assertEquals("/api/mobile/files/photo-1", ordered[0].fileContentBlocks.first().downloadUrl)
    }

    @Ignore("Legacy internal continuation duplicate collapse was removed.")
    @Test
    fun dropsOpenClawInternalContinuationDuplicateUserPrompt() {
        val messages = orderMessagesWithSourceRunAnchors(
            listOf(
                ChatMessage(
                    id = "history-voice-prompt",
                    role = MessageRole.user,
                    content = "测试语音功能不需要回复。",
                    runId = "history-voice-prompt",
                    sortTimestamp = 100.0
                ),
                ChatMessage(
                    id = "history-internal-continuation",
                    role = MessageRole.user,
                    content = """
                        测试语音功能不需要回复。

                        The previous attempt did not produce a user-visible answer. Continue from the current state and produce the visible answer now. Do not restart from scratch.
                    """.trimIndent(),
                    runId = "history-internal-continuation",
                    sortTimestamp = 148.0
                ),
                ChatMessage(
                    id = "history-answer",
                    role = MessageRole.assistant,
                    content = "已回复。",
                    runId = "history-answer",
                    sortTimestamp = 149.0
                )
            )
        )

        assertEquals(listOf("history-voice-prompt", "history-answer"), messages.map { it.runId })
        assertEquals("测试语音功能不需要回复。", messages.first().content)
        assertFalse(messages.any { it.content.contains("previous attempt", ignoreCase = true) })
    }
}
