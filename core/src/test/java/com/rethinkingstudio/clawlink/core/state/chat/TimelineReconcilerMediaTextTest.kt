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
        listOf(true, false).forEach { imageFirst ->
            val suffix = if (imageFirst) "image-first" else "text-first"
            val runId = "relay-output-$suffix"
            val firstOutputOrder = "v4|0|00000000000000000017|50|1785145891100100:output|$suffix"
            val secondOutputOrder = "v4|0|00000000000000000017|50|1785145893776024:output|$suffix"
            val imageOrder = if (imageFirst) firstOutputOrder else secondOutputOrder
            val textOrder = if (imageFirst) secondOutputOrder else firstOutputOrder
            val user = TimelineSnapshotMessage(
                messageId = "user-$suffix",
                conversationSeq = 17,
                seq = 17,
                turnSeq = 1,
                turnId = "turn-$suffix",
                runId = runId,
                role = "user",
                messageState = "completed",
                createdAt = "2026-07-27T09:51:30.000Z",
                content = listOf(RelayChatContentBlock(type = "text", text = "发送截图")),
                timelineOrderKey = "v4|0|00000000000000000017|10|1785145890000000:user|$suffix",
                timelineIdentityKey = "v4|main|message|user|$suffix",
                timelineItemKind = "message:user"
            )
            val image = TimelineSnapshotMessage(
                messageId = "image-$suffix",
                conversationSeq = 17,
                seq = 17,
                turnSeq = 2,
                turnId = "turn-$suffix",
                runId = runId,
                role = "assistant",
                messageState = "completed",
                createdAt = "2026-07-27T09:51:31.100Z",
                content = listOf(
                    RelayChatContentBlock(
                        type = "image",
                        text = "desktop.png",
                        fileId = "file-$suffix",
                        fileName = "desktop.png",
                        mimeType = "image/png",
                        downloadUrl = "/api/mobile/files/file-$suffix"
                    )
                ),
                timelineOrderKey = imageOrder,
                timelineIdentityKey = "v4|main|attachment|assistant|$suffix",
                timelineItemKind = "attachment"
            )
            val text = TimelineSnapshotMessage(
                messageId = "text-$suffix",
                conversationSeq = 17,
                seq = 17,
                turnSeq = 3,
                turnId = "turn-$suffix",
                runId = runId,
                role = "assistant",
                messageState = "completed",
                createdAt = "2026-07-27T09:51:33.776Z",
                content = listOf(RelayChatContentBlock(type = "text", text = "截图发送完成")),
                timelineOrderKey = textOrder,
                timelineIdentityKey = "v4|main|message|assistant|$suffix",
                timelineItemKind = "message:assistant"
            )

            val result = reconcileTimeline(
                existing = emptyList(),
                snapshot = TimelineSnapshotPage(
                    sessionKey = "main",
                    messages = listOf(text, user, image)
                )
            )
            val ordered = sortTimelineMessagesV3(result.messages + result.pending, "main")
            val expected = if (imageFirst) {
                listOf("user-$suffix", "image-$suffix", "text-$suffix")
            } else {
                listOf("user-$suffix", "text-$suffix", "image-$suffix")
            }

            assertEquals(suffix, expected, ordered.map { it.timelineMessageId })
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

    @Test
    fun canonicalMixedMediaMessageRemainsIdempotentAcrossRepeatedSorting() {
        val prompt = "分析一下这张图"
        val fileName = "album-B4358473-17EA-46AB-9319-B041A422E3C9.jpg"
        val message = ChatMessage(
            id = "user-ios-image",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "$prompt\n\n$fileName",
            contentBlocks = listOf(
                RelayChatContentBlock(type = "text", contentBlockId = "blk_prompt", text = prompt),
                RelayChatContentBlock(
                    type = "image",
                    contentBlockId = "blk_image",
                    attachmentId = "att_image",
                    fileId = "file_image",
                    fileName = fileName,
                    text = fileName,
                    sourceRunId = "attachment-ios-image"
                )
            ),
            runId = "local-user-attachment-ios-image",
            timelineOrderKey = "local:attachment-ios-image:010-user",
            timelineIdentityKey = "v1|main|message|user|srv_ios_image",
            timelineItemKind = "message:user",
            source = "local"
        )

        val sorted = (1..20).fold(listOf(message)) { messages, _ ->
            sortTimelineMessagesV3(messages, "main")
        }.single()

        assertEquals(prompt, sorted.content)
        assertEquals(listOf("blk_prompt", "blk_image"), sorted.contentBlocks.map { it.contentBlockId })
        assertEquals(listOf(prompt), sorted.contentBlocks.filter { it.isTextBlock }.map { it.text })
        assertEquals("file_image", sorted.fileContentBlocks.single().fileId)
    }
}
