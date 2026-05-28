package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatPayloadToolTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun detectsCompactToolErrorPayload() {
        val payload = json.parseToJsonElement(
            """
            {
              "stream": "tool",
              "runId": "run-1",
              "tool": "exec",
              "status": "error",
              "error": "spawn /bin/zsh EAGAIN"
            }
            """.trimIndent()
        ) as JsonObject

        val tool = ChatPayloadTool.extract(payload)

        assertEquals("run-1", tool?.toolCallId)
        assertEquals("exec", tool?.toolName)
        assertEquals("spawn /bin/zsh EAGAIN", tool?.displayText)
        assertEquals(MessageState.failed, tool?.state)
    }

    @Test
    fun detectsNestedToolDataPayload() {
        val payload = json.parseToJsonElement(
            """
            {
              "state": "final",
              "data": {
                "phase": "final",
                "tool_name": "shell",
                "tool_call_id": "call-1",
                "result": { "content": "done" }
              }
            }
            """.trimIndent()
        ) as JsonObject

        val tool = ChatPayloadTool.extract(payload)

        assertEquals("call-1", tool?.toolCallId)
        assertEquals("shell", tool?.toolName)
        assertEquals("done", tool?.displayText)
        assertEquals(MessageState.completed, tool?.state)
    }

    @Test
    fun detectsToolPayloadUsingDataNameLikeIos() {
        val payload = json.parseToJsonElement(
            """
            {
              "stream": "tool",
              "runId": "run-1",
              "data": {
                "phase": "update",
                "name": "call-from-name",
                "args": { "command": "pwd" },
                "partial_result": { "output": "/tmp" }
              }
            }
            """.trimIndent()
        ) as JsonObject

        val tool = ChatPayloadTool.extract(payload)

        assertEquals("call-from-name", tool?.toolCallId)
        assertEquals("call-from-name", tool?.toolName)
        assertEquals("/tmp", tool?.displayText)
        assertEquals(MessageState.streaming, tool?.state)
    }

    @Test
    fun ignoresNormalAssistantPayloads() {
        val payload = json.parseToJsonElement("""{"state":"final","text":"hello"}""") as JsonObject

        assertNull(ChatPayloadTool.extract(payload))
    }

    @Test
    fun ignoresAssistantPayloadWithResultFieldUnlessToolMarked() {
        val payload = json.parseToJsonElement(
            """{"state":"final","role":"assistant","result":{"content":"hello"}}"""
        ) as JsonObject

        assertNull(ChatPayloadTool.extract(payload))
    }

    @Test
    fun detectsToolPayloadSentOnChatEvent() {
        val payload = json.parseToJsonElement(
            """
            {
              "state": "streaming",
              "role": "tool",
              "runId": "call-3",
              "data": {
                "tool_name": "read",
                "tool_call_id": "call-3",
                "args": { "path": "/tmp/a.txt" },
                "partial_result": { "content": "reading" }
              }
            }
            """.trimIndent()
        ) as JsonObject

        val tool = ChatPayloadTool.extract(payload)

        assertEquals("call-3", tool?.toolCallId)
        assertEquals("read", tool?.toolName)
        assertEquals("reading", tool?.displayText)
        assertEquals(MessageState.streaming, tool?.state)
    }

    @Test
    fun detectsToolPayloadFromContentBlocks() {
        val payload = json.parseToJsonElement(
            """
            {
              "state": "final",
              "role": "assistant",
              "message": {
                "content": [
                  {
                    "type": "tool_use",
                    "name": "list",
                    "tool_call_id": "call-4",
                    "args": { "path": "/Users/me/Desktop" }
                  },
                  {
                    "type": "tool_result",
                    "name": "list",
                    "tool_call_id": "call-4",
                    "result": { "content": "a.txt\nb.txt" }
                  }
                ]
              }
            }
            """.trimIndent()
        ) as JsonObject

        val tool = ChatPayloadTool.extract(payload)

        assertEquals("call-4", tool?.toolCallId)
        assertEquals("list", tool?.toolName)
        assertEquals("a.txt\nb.txt", tool?.displayText)
        assertEquals(MessageState.completed, tool?.state)
    }
}
