package com.rethinkingstudio.clawlink.core.models.chat

import org.junit.Assert.assertEquals
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
}
