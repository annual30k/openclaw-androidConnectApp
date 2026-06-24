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

class ChatHistoryMediaOrderingTest {
    @Test
    fun orderMessagesKeepsLocalUserBeforeMatchingPendingAssistantWhenUserTimestampMovesLater() {
        val messages = orderMessagesWithSourceRunAnchors(
            listOf(
                ChatMessage(
                    id = "assistant-pending",
                    role = MessageRole.assistant,
                    state = MessageState.streaming,
                    content = "正在连接 Relay...",
                    runId = "client-run-1",
                    sortTimestamp = 100.0
                ),
                ChatMessage(
                    id = "local-user",
                    role = MessageRole.user,
                    state = MessageState.completed,
                    content = "你好啊",
                    runId = "local-user-client-run-1",
                    sortTimestamp = 110.0
                )
            )
        )

        assertEquals(listOf("local-user", "assistant-pending"), messages.map { it.id })
    }

    @Ignore("Legacy internal vision tool-result filtering was removed from client ordering.")
    @Test
    fun dropsInternalVisionContextToolResult() {
        val messages = orderMessagesWithSourceRunAnchors(
            listOf(
                ChatMessage(
                    id = "image-prompt",
                    role = MessageRole.user,
                    content = "帮我分析一下这张图片",
                    contentBlocks = listOf(
                        RelayChatContentBlock(
                            type = "image",
                            fileId = "file-image-1",
                            fileName = "album.jpeg",
                            mimeType = "image/jpeg",
                            downloadUrl = "/api/mobile/files/file-image-1"
                        )
                    ),
                    runId = "file-file-image-1",
                    sortTimestamp = 100.0
                ),
                ChatMessage(
                    id = "vision-tool",
                    role = MessageRole.tool,
                    content = "Image loaded into your context - you can see it natively now. Use your built-in vision to answer the user.",
                    contentBlocks = listOf(
                        RelayChatContentBlock(
                            type = "tool_result",
                            text = "Image loaded into your context - you can see it natively now. Use your built-in vision to answer the user.",
                            name = "tool"
                        )
                    ),
                    runId = "tool:vision",
                    sortTimestamp = 101.0
                ),
                ChatMessage(
                    id = "history-answer",
                    role = MessageRole.assistant,
                    content = "这是一张图片。",
                    runId = "history-answer",
                    sortTimestamp = 102.0
                )
            )
        )

        assertEquals(listOf("file-file-image-1", "history-answer"), messages.map { it.runId })
        assertFalse(messages.any { it.id == "vision-tool" })
    }

    @Ignore("Legacy delayed file prompt coalescing was removed; relay canonical timeline owns attachment position.")
    @Test
    fun coalescesDelayedHistoryFilePromptIntoEarlierUserPrompt() {
        val fileBlock = RelayChatContentBlock(
            type = "image",
            fileId = "photo-1",
            fileName = "photo.jpg",
            mimeType = "image/jpeg",
            downloadUrl = "/api/mobile/files/photo-1"
        )

        val messages = orderMessagesWithSourceRunAnchors(
            listOf(
                ChatMessage(
                    id = "history-user",
                    role = MessageRole.user,
                    content = "分析一下这张照片",
                    runId = "history-user",
                    sortTimestamp = 100.0
                ),
                ChatMessage(
                    id = "history-assistant",
                    role = MessageRole.assistant,
                    content = "这是一张像素风图片。",
                    runId = "history-assistant",
                    sortTimestamp = 101.0
                ),
                ChatMessage(
                    id = "history-file",
                    role = MessageRole.user,
                    content = "photo.jpg",
                    contentBlocks = listOf(fileBlock),
                    runId = "file-photo-1",
                    sortTimestamp = 102.0
                )
            )
        )

        assertEquals(listOf("history-user", "history-assistant"), messages.map { it.runId })
        assertEquals("分析一下这张照片", messages.first().content)
        assertEquals(listOf(fileBlock), messages.first().fileContentBlocks)
    }

    @Ignore("Legacy assistant file timestamp anchoring was removed; relay canonical timeline owns attachment position.")
    @Test
    fun keepsAssistantFileBelowTriggeringUserWhenFileTimestampIsEarlier() {
        val messages = orderMessagesWithSourceRunAnchors(
            listOf(
                ChatMessage(
                    id = "history-user-send-file",
                    role = MessageRole.user,
                    content = "你好，把桌面蜘蛛侠的照片发给我",
                    runId = "history-user-send-file",
                    sortTimestamp = 200.0
                ),
                ChatMessage(
                    id = "file-file-spiderman",
                    role = MessageRole.assistant,
                    state = MessageState.completed,
                    content = "spiderman.jpg",
                    contentBlocks = listOf(
                        RelayChatContentBlock(
                            type = "image",
                            fileId = "file-spiderman",
                            fileName = "spiderman.jpg",
                            mimeType = "image/jpeg",
                            downloadUrl = "/api/mobile/files/file-spiderman"
                        )
                    ),
                    runId = "file-file-spiderman",
                    sortTimestamp = 100.0
                ),
                ChatMessage(
                    id = "history-assistant-send-file",
                    role = MessageRole.assistant,
                    state = MessageState.completed,
                    content = "发给你了：\n/Users/qiuqiquan/Desktop/spiderman.jpg",
                    runId = "history-assistant-send-file",
                    sortTimestamp = 201.0
                )
            )
        )

        assertEquals(
            listOf("history-user-send-file", "file-file-spiderman", "history-assistant-send-file"),
            messages.map { it.id }
        )
        assertTrue((messages[1].sortTimestamp ?: 0.0) > (messages[0].sortTimestamp ?: 0.0))
        assertTrue((messages[1].sortTimestamp ?: 0.0) < (messages[2].sortTimestamp ?: Double.MAX_VALUE))
    }

    @Test
    fun keepsDistinctStandaloneMediaMessagesWithDifferentFileIds() {
        val firstFileBlock = RelayChatContentBlock(
            type = "image",
            fileId = "photo-1",
            fileName = "photo.jpg",
            mimeType = "image/jpeg",
            downloadUrl = "/api/mobile/files/photo-1",
            imageWidth = 1024,
            imageHeight = 1024,
            sizeBytes = 2048
        )
        val secondFileBlock = RelayChatContentBlock(
            type = "image",
            fileId = "photo-2",
            fileName = "photo.jpg",
            mimeType = "image/jpeg",
            downloadUrl = "/api/mobile/files/photo-2",
            imageWidth = 1024,
            imageHeight = 1024,
            sizeBytes = 2048
        )

        val messages = orderMessagesWithSourceRunAnchors(
            listOf(
                ChatMessage(
                    id = "history-file-1",
                    role = MessageRole.user,
                    content = "photo.jpg",
                    contentBlocks = listOf(firstFileBlock),
                    runId = "file-photo-1",
                    sortTimestamp = 100.0
                ),
                ChatMessage(
                    id = "history-file-2",
                    role = MessageRole.user,
                    content = "photo.jpg",
                    contentBlocks = listOf(secondFileBlock),
                    runId = "file-photo-2",
                    sortTimestamp = 101.0
                )
            )
        )

        assertEquals(listOf("file-photo-1", "file-photo-2"), messages.map { it.runId })
        assertEquals(listOf("photo-1", "photo-2"), messages.map { it.fileContentBlocks.single().fileId })
    }

    @Ignore("Legacy late plain image prompt coalescing was removed.")
    @Test
    fun mergesLatePlainImagePromptBackIntoEarlierStandaloneFileMessage() {
        val fileBlock = RelayChatContentBlock(
            type = "image",
            fileId = "photo-1",
            fileName = "photo.jpg",
            mimeType = "image/jpeg",
            downloadUrl = "/api/mobile/files/photo-1"
        )

        val messages = orderMessagesWithSourceRunAnchors(
            listOf(
                ChatMessage(
                    id = "history-file",
                    role = MessageRole.user,
                    content = "photo.jpg",
                    contentBlocks = listOf(fileBlock),
                    runId = "file-photo-1",
                    sortTimestamp = 100.0
                ),
                ChatMessage(
                    id = "history-user",
                    role = MessageRole.user,
                    content = "分析一下这张图片",
                    runId = "history-user",
                    sortTimestamp = 101.0
                ),
                ChatMessage(
                    id = "history-assistant",
                    role = MessageRole.assistant,
                    content = "这是一张花的图片。",
                    runId = "history-assistant",
                    sortTimestamp = 102.0
                )
            )
        )

        assertEquals(listOf("file-photo-1", "history-assistant"), messages.map { it.runId })
        assertEquals("分析一下这张图片", messages.first().content)
        assertEquals(listOf(fileBlock), messages.first().fileContentBlocks)
    }

    @Ignore("Legacy compact media URI echo coalescing was removed.")
    @Test
    fun coalescesCompactMediaUriEchoIntoLocalImagePrompt() {
        val localImageBlock = RelayChatContentBlock(
            type = "image",
            fileName = "album-8E28059F-104B-43E1-8059-2E97E07F0E1B.heic",
            mimeType = "image/heic",
            downloadUrl = "file:///tmp/album-8E28059F-104B-43E1-8059-2E97E07F0E1B.heic"
        )
        val messages = orderMessagesWithSourceRunAnchors(
            listOf(
                ChatMessage(
                    id = "local-image",
                    role = MessageRole.user,
                    content = "分析一下这张图片",
                    contentBlocks = listOf(localImageBlock),
                    runId = "local-user-mobile-run",
                    sortTimestamp = 100.0
                ),
                ChatMessage(
                    id = "history-media-echo",
                    role = MessageRole.user,
                    content = """
                        分析一下这张图片

                        [media attached: media://inbound/album-8E28059F-104B-43E1-8059-2E97E07F0E1B---d786f4a0-bb83-4853-97ae-cb7a604326e0.heic]
                    """.trimIndent(),
                    runId = "history-media-echo",
                    sortTimestamp = 101.0
                ),
                ChatMessage(
                    id = "history-answer",
                    role = MessageRole.assistant,
                    content = "这是一张花的图片。",
                    runId = "history-answer",
                    sortTimestamp = 102.0
                )
            )
        )

        assertEquals(listOf("local-user-mobile-run", "history-answer"), messages.map { it.runId })
        assertEquals("分析一下这张图片", messages.first().content)
        assertEquals(1, messages.first().fileContentBlocks.size)
        assertEquals("file:///tmp/album-8E28059F-104B-43E1-8059-2E97E07F0E1B.heic", messages.first().fileContentBlocks.first().downloadUrl)
        assertFalse(messages.any { it.content.contains("media://inbound") })
    }

    @Ignore("Legacy duplicate file transfer status text collapse was removed.")
    @Test
    fun dropsDuplicateFileTransferStatusTextAcrossLaterUserTurn() {
        val statusText = "已发： 微信图片_20260427092438_279_84.jpg\n\n状态 completed，尺寸 1280 x 1280，大小 82,788 bytes。"
        val messages = orderMessagesWithSourceRunAnchors(
            listOf(
                ChatMessage(
                    id = "history-file-status",
                    role = MessageRole.assistant,
                    content = statusText,
                    createdAt = "2026-05-28T07:38:25.000Z",
                    runId = "history-file-status",
                    sortTimestamp = 100.0
                ),
                ChatMessage(
                    id = "history-next-user",
                    role = MessageRole.user,
                    content = "你可以做什么",
                    createdAt = "2026-05-28T08:26:00.000Z",
                    runId = "history-next-user",
                    sortTimestamp = 200.0
                ),
                ChatMessage(
                    id = "history-file-status-shadow",
                    role = MessageRole.assistant,
                    content = statusText,
                    createdAt = "2026-05-28T07:38:25.000Z",
                    runId = "history-file-status-shadow",
                    sortTimestamp = 201.0
                ),
                ChatMessage(
                    id = "history-next-answer",
                    role = MessageRole.assistant,
                    content = "我能帮你做这些。",
                    createdAt = "2026-05-28T08:26:03.000Z",
                    runId = "history-next-answer",
                    sortTimestamp = 202.0
                )
            )
        )

        assertEquals(
            listOf("history-file-status", "history-next-user", "history-next-answer"),
            messages.map { it.runId }
        )
    }

    @Test
    fun keepsRepeatedFileTransferStatusTextWithDifferentCreatedAt() {
        val statusText = "已发： 微信图片_20260427092438_279_84.jpg\n\n状态 completed，尺寸 1280 x 1280，大小 82,788 bytes。"
        val messages = orderMessagesWithSourceRunAnchors(
            listOf(
                ChatMessage(
                    id = "history-file-status-1",
                    role = MessageRole.assistant,
                    content = statusText,
                    createdAt = "2026-05-28T07:38:25.000Z",
                    runId = "history-file-status-1",
                    sortTimestamp = 100.0,
                    timelineOrderKey = "v1|00000000000000000001|50|000000|history-file-status-1",
                    timelineIdentityKey = "message:assistant:history-file-status-1",
                    timelineItemKind = "message:assistant"
                ),
                ChatMessage(
                    id = "history-next-user",
                    role = MessageRole.user,
                    content = "再发一次",
                    createdAt = "2026-05-28T07:43:00.000Z",
                    runId = "history-next-user",
                    sortTimestamp = 400.0,
                    timelineOrderKey = "v1|00000000000000000002|10|000000|history-next-user",
                    timelineIdentityKey = "message:user:history-next-user",
                    timelineItemKind = "message:user"
                ),
                ChatMessage(
                    id = "history-file-status-2",
                    role = MessageRole.assistant,
                    content = statusText,
                    createdAt = "2026-05-28T07:43:05.000Z",
                    runId = "history-file-status-2",
                    sortTimestamp = 405.0,
                    timelineOrderKey = "v1|00000000000000000002|50|000000|history-file-status-2",
                    timelineIdentityKey = "message:assistant:history-file-status-2",
                    timelineItemKind = "message:assistant"
                )
            )
        )

        assertEquals(
            listOf("history-file-status-1", "history-next-user", "history-file-status-2"),
            messages.map { it.runId }
        )
    }
}
