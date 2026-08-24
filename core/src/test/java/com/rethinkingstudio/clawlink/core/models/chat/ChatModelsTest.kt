package com.rethinkingstudio.clawlink.core.models.chat

import com.rethinkingstudio.clawlink.core.network.transport.VoiceSendAudioPayload
import com.rethinkingstudio.clawlink.core.state.chat.buildLocalVoiceUserMessage
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ChatModelsTest {
    @Test
    fun imageContentBlocksAreFileContentBlocks() {
        val message = ChatMessage(
            id = "upload-1",
            role = MessageRole.user,
            content = "photo.png",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    fileName = "photo.png",
                    mimeType = "image/png",
                    downloadUrl = "/tmp/photo.png",
                    status = "上传中 35%"
                )
            )
        )

        assertEquals(1, message.fileContentBlocks.size)
        assertTrue(message.fileContentBlocks.single().isImageFileBlock)
    }

    @Test
    fun imagePreviewPrefersExistingLocalPathBeforeRelayFallback() {
        val localFile = File.createTempFile("clawlink-image-preview", ".jpg")
        try {
            val block = RelayChatContentBlock(
                type = "image",
                fileId = "file-local-present",
                fileName = "photo.jpg",
                mimeType = "image/jpeg",
                downloadUrl = localFile.absolutePath,
                downloadPath = "/api/mobile/files/file-local-present"
            )

            assertEquals(localFile.absolutePath, block.preferredImagePreviewURLString)
        } finally {
            localFile.delete()
        }
    }

    @Test
    fun imagePreviewFallsBackWhenLocalPathIsMissing() {
        val missingFile = File(System.getProperty("java.io.tmpdir"), "clawlink-missing-${System.nanoTime()}.jpg")
        val block = RelayChatContentBlock(
            type = "image",
            fileId = "file-local-missing",
            fileName = "photo.jpg",
            mimeType = "image/jpeg",
            downloadUrl = missingFile.absolutePath,
            downloadPath = "/api/mobile/files/file-local-missing"
        )

        assertEquals("/api/mobile/files/file-local-missing", block.preferredImagePreviewURLString)
    }

    @Test
    fun voiceContentBlocksRecognizeUploadPlaceholders() {
        val message = ChatMessage(
            id = "upload-voice",
            role = MessageRole.user,
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "voice",
                    fileName = "voice.m4a",
                    mimeType = "audio/mp4",
                    status = "上传中 12%"
                )
            )
        )

        assertEquals(1, message.voiceContentBlocks.size)
    }

    @Test
    fun localVoiceSendCreatesVisibleUserVoiceMessage() {
        val message = buildLocalVoiceUserMessage(
            audio = VoiceSendAudioPayload(
                fileName = "voice-input.m4a",
                mimeType = "audio/mp4",
                sizeBytes = 4,
                contentBase64 = "AQIDBA=="
            ),
            gatewayId = "gateway-1",
            sessionKey = "main",
            clientRunId = "run-1",
            sortTimestamp = 123.0
        )

        assertEquals(MessageRole.user, message.role)
        assertEquals(MessageState.completed, message.state)
        assertEquals("local-user-run-1", message.runId)
        assertTrue(message.hasVoiceContent)
        assertTrue(message.shouldDisplayInChat(showInvocationProcess = false))
        val voice = message.voiceContentBlocks.single()
        assertEquals("voice", voice.type)
        assertEquals("voice-input.m4a", voice.fileName)
        assertEquals("audio/mp4", voice.mimeType)
        assertEquals(4, voice.sizeBytes)
        assertTrue(voice.voiceDownloadURLString!!.startsWith("file://"))
        assertEquals(voice.downloadUrl, voice.localPath)
        assertEquals("run-1", voice.sourceRunId)
        assertEquals("local:run-1|10|user-run-1", message.timelineOrderKey)
        assertEquals("local:message:user:run-1", message.timelineIdentityKey)
        assertEquals("message:user", message.timelineItemKind)
    }

    @Test
    fun streamingAssistantProtocolTypingMarkerRemainsDisplayable() {
        val streaming = ChatMessage(
            id = "assistant-typing",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "[[clawlink:typing]]"
        )
        val completed = streaming.copy(state = MessageState.completed)

        assertTrue(streaming.shouldDisplayInChat(showInvocationProcess = false))
        assertFalse(completed.shouldDisplayInChat(showInvocationProcess = false))
    }

    @Test
    fun completedAssistantProtocolTypingMarkerContentBlockIsHidden() {
        val completed = ChatMessage(
            id = "assistant-typing-block",
            role = MessageRole.assistant,
            state = MessageState.completed,
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = "[[clawlink:typing]]"))
        )

        assertFalse(completed.shouldDisplayInChat(showInvocationProcess = false))
    }

    @Test
    fun completedAssistantLegacyConnectingPlaceholderIsHidden() {
        val placeholders = listOf("正在连接...", "连接中", "Connecting...", "正在同步回复...", "Syncing reply...")

        placeholders.forEach { placeholder ->
            val completed = ChatMessage(
                id = "assistant-legacy-connecting",
                role = MessageRole.assistant,
                state = MessageState.completed,
                content = placeholder
            )

            assertFalse(completed.shouldDisplayInChat(showInvocationProcess = false))
        }
    }

    @Test
    fun completedAssistantBlankTextContentBlockIsHidden() {
        val completed = ChatMessage(
            id = "assistant-empty-block",
            role = MessageRole.assistant,
            state = MessageState.completed,
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = ""))
        )

        assertFalse(completed.shouldDisplayInChat(showInvocationProcess = false))
    }

    @Test
    fun voiceContentBlocksDecodeSnakeCaseDownloadMetadata() {
        val block = Json.decodeFromString<RelayChatContentBlock>(
            """
            {
              "type": "voice",
              "content_block_id": "blk_voice_1",
              "file_id": "file_voice_snake",
              "file_name": "reply.mp3",
              "mime_type": "audio/mpeg",
              "duration_ms": 1200,
              "download_url": "/api/mobile/files/file_voice_snake",
              "sender_display_name": "Mac Book Pro"
            }
            """.trimIndent()
        )

        assertTrue(block.isVoiceMessageBlock)
        assertEquals("blk_voice_1", block.contentBlockId)
        assertEquals("file_voice_snake", block.fileId)
        assertEquals("reply.mp3", block.fileName)
        assertEquals("audio/mpeg", block.mimeType)
        assertEquals(1200, block.durationMs)
        assertEquals("Mac Book Pro", block.senderDisplayName)
        assertEquals("/api/mobile/files/file_voice_snake", block.voiceDownloadURLString)
        assertEquals("file_voice_snake", block.voicePlaybackIdentifier)
    }

    @Test
    fun fileContentBlocksDecodeAssistantSourceRunId() {
        val block = Json.decodeFromString<RelayChatContentBlock>(
            """
            {
              "type": "file",
              "file_id": "file_reply_image",
              "file_name": "reply.jpg",
              "mime_type": "image/jpeg",
              "source_run_id": "run-voice-1"
            }
            """.trimIndent()
        )

        assertEquals("run-voice-1", block.sourceRunId)
    }

    @Test
    fun toolMessagesRespectInvocationVisibilityToggle() {
        val callBlock = RelayChatContentBlock(
            type = "tool_call",
            name = "read",
            toolCallId = "call-1",
            arguments = RelayJSONValue.ObjectVal(
                mapOf("command" to RelayJSONValue.StringVal("cat /tmp/demo.md"))
            )
        )
        val resultBlock = RelayChatContentBlock(
            type = "tool_result",
            name = "read",
            toolCallId = "call-1",
            output = RelayJSONValue.StringVal("done")
        )
        val message = ChatMessage(
            id = "tool-1",
            role = MessageRole.tool,
            content = "done",
            contentBlocks = listOf(callBlock, resultBlock)
        )

        assertTrue(message.shouldDisplayInChat(showInvocationProcess = true))
        assertFalse(message.shouldDisplayInChat(showInvocationProcess = false))
        assertEquals("read", message.toolDisplayName)
        assertEquals("read, read", message.toolDisplaySummary)
    }

    @Test
    fun toolContentBlocksUseIosCompatibleTypeAliases() {
        val message = ChatMessage(
            id = "tool-aliases",
            role = MessageRole.assistant,
            contentBlocks = listOf(
                RelayChatContentBlock(type = "function_call", name = "exec", toolCallId = "call-1"),
                RelayChatContentBlock(type = "tool_out_error", name = "exec", toolCallId = "call-1", isError = true),
                RelayChatContentBlock(type = "tool_output_error", name = "exec", toolCallId = "call-1", isError = true)
            )
        )

        assertTrue(message.hasToolContent)
        assertEquals(3, message.toolContentBlocks.size)
        assertTrue(message.toolContentBlocks[0].isToolCallBlock)
        assertTrue(message.toolContentBlocks[1].isToolResultBlock)
        assertTrue(message.toolContentBlocks[2].isToolResultBlock)
    }

    @Test
    fun canonicalAssistantMessageDoesNotBecomeToolOnlyBecauseItContinuesWithToolCall() {
        val message = ChatMessage(
            id = "assistant-analysis-and-search",
            role = MessageRole.assistant,
            content = "这是一张标准证件照。关于蜘蛛侠图片，我来找找：",
            contentBlocks = listOf(
                RelayChatContentBlock(type = "text", text = "这是一张标准证件照。关于蜘蛛侠图片，我来找找："),
                RelayChatContentBlock(type = "tool_call", name = "exec", toolCallId = "call-find-spider")
            ),
            timelineItemKind = "message:assistant"
        )

        assertFalse(message.hasToolContent)
        assertTrue(message.shouldDisplayInChat(showInvocationProcess = false))
    }

    @Test
    fun toolCallOnlyMessageDoesNotRenderAsBlankAssistantFromStaleCanonicalKind() {
        val message = ChatMessage(
            id = "assistant-tool-only",
            role = MessageRole.assistant,
            content = "{ \"command\": \"find spiderman.jpg\" }",
            contentBlocks = listOf(
                RelayChatContentBlock(type = "thinking"),
                RelayChatContentBlock(type = "tool_call", name = "exec", toolCallId = "call-find-spider")
            ),
            timelineItemKind = "message:assistant"
        )

        assertTrue(message.hasToolContent)
        assertFalse(message.shouldDisplayInChat(showInvocationProcess = false))
        assertTrue(message.shouldDisplayInChat(showInvocationProcess = true))
    }

    @Test
    fun toolSummaryBlocksDecodeDetailMetadata() {
        val block = Json.decodeFromString<RelayChatContentBlock>(
            """
            {
              "type": "tool_result",
              "name": "exec",
              "tool_call_id": "call-1",
              "preview": "short output",
              "tool_state": "completed",
              "has_full_detail": true,
              "detail_truncated": true,
              "detail_expired": false,
              "detail_expires_at": "2026-06-08T00:00:00.000Z",
              "chunked": true
            }
            """.trimIndent()
        )

        assertTrue(block.isToolResultBlock)
        assertEquals("call-1", block.toolCallId)
        assertEquals("short output", block.preview)
        assertEquals("completed", block.toolState)
        assertEquals(true, block.hasFullDetail)
        assertEquals(true, block.detailTruncated)
        assertEquals(false, block.detailExpired)
        assertEquals("2026-06-08T00:00:00.000Z", block.detailExpiresAt)
        assertEquals(true, block.chunked)
    }

    @Test
    fun toolDetailResponseDecodesContentBlocksAndPaging() {
        val response = Json.decodeFromString<ToolDetailResponse>(
            """
            {
              "toolCallId": "call-1",
              "name": "exec",
              "state": "completed",
              "preview": "hello",
              "hasFullDetail": true,
              "truncated": true,
              "expired": false,
              "expiresAt": "2026-06-08T00:00:00.000Z",
              "content": "hello world",
              "contentBlocks": [
                {
                  "type": "tool_result",
                  "tool_call_id": "call-1",
                  "text": "hello world"
                }
              ],
              "offset": 0,
              "limit": 20,
              "hasMore": true,
              "nextCursor": "offset:20",
              "downloadUrl": "/api/mobile/gateways/gw/chat/tools/call-1/detail/download"
            }
            """.trimIndent()
        )

        assertEquals("call-1", response.toolCallId)
        assertTrue(response.hasFullDetail)
        assertTrue(response.truncated)
        assertTrue(response.hasMore)
        assertEquals("offset:20", response.nextCursor)
        assertEquals("hello world", response.contentBlocks.single().text)
        assertEquals("call-1", response.contentBlocks.single().toolCallId)
    }
}
