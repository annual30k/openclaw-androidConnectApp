package com.rethinkingstudio.clawlink.core.state.chat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatPayloadTextTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun extractsDeltaText() {
        val payload = json.parseToJsonElement("""{"state":"delta","delta":"hello"}""") as JsonObject

        assertEquals("hello", ChatPayloadText.extract(payload))
    }

    @Test
    fun extractsTextFromMessageContentBlocks() {
        val payload = json.parseToJsonElement(
            """
            {
              "state": "final",
              "message": {
                "role": "assistant",
                "content": [
                  {"type": "text", "text": "hello "},
                  {"type": "text", "text": "world"}
                ]
              }
            }
            """.trimIndent()
        ) as JsonObject

        assertEquals("hello world", ChatPayloadText.extract(payload))
    }
}
