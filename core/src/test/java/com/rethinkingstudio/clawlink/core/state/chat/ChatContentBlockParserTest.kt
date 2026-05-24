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
}
