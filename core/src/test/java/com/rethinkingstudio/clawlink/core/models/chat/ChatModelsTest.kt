package com.rethinkingstudio.clawlink.core.models.chat

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
