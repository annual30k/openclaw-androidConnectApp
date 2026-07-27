package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatHistoryMediaOrderingTest {
    @Test
    fun orderMessagesKeepsLocalUserBeforeMatchingPendingAssistantWhenUserTimestampMovesLater() {
        val messages = orderTimelineMessages(
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

        val messages = orderTimelineMessages(
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

    @Test
    fun keepsRepeatedFileTransferStatusTextWithDifferentCreatedAt() {
        val statusText = "已发： 微信图片_20260427092438_279_84.jpg\n\n状态 completed，尺寸 1280 x 1280，大小 82,788 bytes。"
        val messages = orderTimelineMessages(
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
