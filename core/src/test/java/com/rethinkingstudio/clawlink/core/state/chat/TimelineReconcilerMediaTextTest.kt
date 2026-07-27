package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineReconcilerMediaTextTest {
    @Test
    fun preservesEitherRelayContentOrderWithoutTypePreference() {
        val runId = "relay-output-order"
        val user = ChatMessage(
            id = "turn-user",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "发送输出",
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = "发送输出")),
            createdAt = "2026-07-27T16:56:00.000Z",
            runId = runId,
            timelineOrderKey = "v1|00000000000000000010|10|000000|turn-user",
            timelineIdentityKey = "v1|main|message|user|turn-user",
            timelineItemKind = "message:user"
        )
        val text = ChatMessage(
            id = "text-output",
            role = MessageRole.assistant,
            state = MessageState.completed,
            content = "输出完成",
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = "输出完成")),
            createdAt = "2026-07-27T16:56:03.000Z",
            runId = runId,
            timelineOrderKey = "v1|00000000000000000013|50|000000|text-output",
            timelineIdentityKey = "v1|main|message|assistant|text-output",
            timelineItemKind = "message:assistant"
        )
        val attachment = ChatMessage(
            id = "attachment-output",
            role = MessageRole.assistant,
            state = MessageState.completed,
            content = "output.png",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    fileId = "file-output",
                    fileName = "output.png",
                    sourceRunId = runId
                )
            ),
            createdAt = "2026-07-27T16:56:02.000Z",
            runId = "file-output",
            timelineOrderKey = "local:$runId:attachment",
            timelineIdentityKey = "local:$runId:attachment:file-output",
            timelineItemKind = "attachment"
        )

        listOf(
            listOf(user, attachment, text),
            listOf(user, text, attachment)
        ).forEach { input ->
            assertEquals(input.map { it.id }, sortTimelineMessagesV3(input, "main").map { it.id })
        }
    }

    @Test
    fun confirmedImageUserKeepsPromptWhenTimelineIsResorted() {
        val prompt = "please describe this image"
        val user = ChatMessage(
            id = "history-user-image-text",
            role = MessageRole.user,
            state = MessageState.completed,
            content = prompt,
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    text = "photo.png",
                    fileId = "file-1",
                    fileName = "photo.png",
                    mimeType = "image/png",
                    downloadUrl = "/api/mobile/files/file-1",
                    sourceRunId = "client-run-image-text"
                )
            ),
            createdAt = "2026-07-07T10:00:00.000Z",
            runId = "client-run-image-text",
            sortTimestamp = 1_783_419_600.0,
            timelineOrderKey = "v1|00000000000000000011|10|0000000000000011:message:user:history-user-image-text",
            timelineIdentityKey = "v1|main|message|user|history-user-image-text",
            timelineItemKind = "message:user"
        )

        val ordered = sortTimelineMessagesV3(listOf(user), "main")
        val sortedUser = ordered.single()

        assertEquals(prompt, sortedUser.content)
        assertTrue(sortedUser.contentBlocks.first().isTextBlock)
        assertEquals("file-1", sortedUser.fileContentBlocks.single().fileId)
    }

    @Test
    fun pendingLocalImageUserKeepsPromptWhenAssistantSnapshotArrives() {
        val clientRunId = "client-run-image-text"
        val prompt = "please describe this image"
        val localUser = ChatMessage(
            id = "user-$clientRunId",
            role = MessageRole.user,
            state = MessageState.completed,
            content = prompt,
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    text = "photo.png",
                    attachmentId = "attachment-1",
                    fileId = "file-1",
                    fileName = "photo.png",
                    mimeType = "image/png",
                    downloadUrl = "/api/mobile/files/file-1",
                    sourceRunId = clientRunId
                )
            ),
            createdAt = "2026-07-07T10:00:00.000Z",
            runId = "local-user-$clientRunId",
            sortTimestamp = 1_783_419_600.0,
            timelineOrderKey = "local:$clientRunId|10|user-$clientRunId",
            timelineIdentityKey = "local:message:user:$clientRunId",
            timelineItemKind = "message:user"
        )
        val waiting = ChatMessage(
            id = "assistant-$clientRunId",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = protocolTypingMarkerText,
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = protocolTypingMarkerText)),
            createdAt = "2026-07-07T10:00:00.001Z",
            runId = clientRunId,
            sortTimestamp = 1_783_419_600.001,
            timelineOrderKey = "local:$clientRunId|20|assistant-$clientRunId",
            timelineIdentityKey = "local:waiting:$clientRunId",
            timelineItemKind = "waiting"
        )

        val result = reconcileTimeline(
            existing = listOf(localUser, waiting),
            snapshot = TimelineSnapshotPage(
                sessionKey = "main",
                messages = listOf(
                    TimelineSnapshotMessage(
                        messageId = "assistant-canonical",
                        seq = 12,
                        turnSeq = 12,
                        turnId = "$clientRunId:assistant",
                        runId = "$clientRunId:assistant",
                        role = "assistant",
                        messageState = "streaming",
                        createdAt = "2026-07-07T10:00:01.000Z",
                        content = listOf(RelayChatContentBlock(type = "text", text = protocolTypingMarkerText)),
                        timelineOrderKey = "v1|00000000000000000012|20|0000000000000012:part-text-1:assistant-canonical",
                        timelineIdentityKey = "v1|main|message|assistant|assistant-canonical",
                        timelineItemKind = "message:assistant"
                    )
                )
            )
        )

        val visible = sortTimelineMessagesV3(result.messages + result.pending, "main")
        val user = visible.first { it.role == MessageRole.user }

        assertEquals(prompt, user.content)
        assertTrue(user.contentBlocks.first().isTextBlock)
        assertEquals("file-1", user.fileContentBlocks.single().fileId)
    }
}
