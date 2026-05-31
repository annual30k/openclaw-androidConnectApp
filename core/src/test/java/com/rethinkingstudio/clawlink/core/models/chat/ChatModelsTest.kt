package com.rethinkingstudio.clawlink.core.models.chat

import com.rethinkingstudio.clawlink.core.network.transport.VoiceSendAudioPayload
import com.rethinkingstudio.clawlink.core.state.chat.buildLocalVoiceUserMessage
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
