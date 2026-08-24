package com.rethinkingstudio.clawlink.core.state.chat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatContentBlockParserTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parsesSourceRunIdFromFileContentBlocks() {
        val payload = json.parseToJsonElement(
            """
            {
              "state": "final",
              "message": {
                "role": "assistant",
                "content": [
                  {
                    "type": "file",
                    "fileId": "file_reply_image",
                    "fileName": "reply.jpg",
                    "mimeType": "image/jpeg",
                    "sourceRunId": "run-voice-1"
                  }
                ]
              }
            }
            """.trimIndent()
        ) as JsonObject

        val block = parseContentBlocks(payload).single()

        assertEquals("run-voice-1", block.sourceRunId)
    }

    @Test
    fun parsesAttachmentIdFromFileContentBlocks() {
        val payload = json.parseToJsonElement(
            """
            {
              "state": "final",
              "message": {
                "role": "assistant",
                "content": [
                  {
                    "type": "file",
                    "attachmentId": "att_source_run_sha",
                    "fileId": "file_reply_image",
                    "fileName": "reply.jpg",
                    "mimeType": "image/jpeg"
                  }
                ]
              }
            }
            """.trimIndent()
        ) as JsonObject

        val block = parseContentBlocks(payload).single()

        assertEquals("att_source_run_sha", block.attachmentId)
    }

    @Test
    fun parsesDurableLocalPathFromVoiceContentBlocks() {
        val payload = json.parseToJsonElement(
            """
            {
              "message": {
                "role": "user",
                "content": [
                  {
                    "type": "voice",
                    "fileId": "file_voice",
                    "downloadUrl": "/api/mobile/files/file_voice",
                    "local_path": "file:///data/user/0/clawlink/cache/voice.m4a"
                  }
                ]
              }
            }
            """.trimIndent()
        ) as JsonObject

        val block = parseContentBlocks(payload).single()

        assertEquals("file:///data/user/0/clawlink/cache/voice.m4a", block.localPath)
        assertEquals("/api/mobile/files/file_voice", block.downloadUrl)
    }
}
