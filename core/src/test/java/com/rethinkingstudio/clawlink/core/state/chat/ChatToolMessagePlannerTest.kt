package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ChatToolMessagePlannerTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun buildsSyntheticToolBlocksForCompactToolPayload() {
        val payload = json.parseToJsonElement(
            """
            {
              "stream": "tool",
              "runId": "run-1",
              "tool": "exec",
              "status": "error",
              "args": { "command": "pwd" },
              "error": "spawn /bin/zsh EAGAIN"
            }
            """.trimIndent()
        ) as JsonObject

        val toolPayload = ChatToolPayloadParser.parse(payload)
        val plan = ChatToolMessagePlanner.plan(requireNotNull(toolPayload))

        assertNotNull(plan)
        requireNotNull(plan)
        assertEquals("tool:run-1", plan.toolRunId)
        assertEquals("run-1", plan.toolCallId)
        assertEquals("exec", plan.toolName)
        assertEquals(MessageRole.tool, plan.role)
        assertEquals(MessageState.failed, plan.state)
        assertEquals("spawn /bin/zsh EAGAIN", plan.content)
        assertEquals(listOf("tool_use", "tool_result"), plan.contentBlocks.map { it.type })
        assertEquals(true, plan.contentBlocks.last().isError)
    }

    @Test
    fun preservesExplicitToolBlocksFromContentPayload() {
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

        val toolPayload = ChatToolPayloadParser.parse(payload)
        val plan = ChatToolMessagePlanner.plan(requireNotNull(toolPayload))

        assertNotNull(plan)
        requireNotNull(plan)
        assertEquals("tool:call-4", plan.toolRunId)
        assertEquals("call-4", plan.toolCallId)
        assertEquals("list", plan.toolName)
        assertEquals(MessageRole.tool, plan.role)
        assertEquals(MessageState.completed, plan.state)
        assertEquals("a.txt\nb.txt", plan.content)
        assertEquals(listOf("tool_use", "tool_result"), plan.contentBlocks.map { it.type })
    }

    @Test
    fun runScopedToolPayloadsWithoutCallIdUseDistinctFallbackIds() {
        val firstPayload = json.parseToJsonElement(
            """
            {
              "stream": "tool",
              "runId": "assistant-run-1",
              "seq": 11,
              "data": {
                "phase": "streaming",
                "tool_name": "web_search",
                "text": "searching weather"
              }
            }
            """.trimIndent()
        ) as JsonObject
        val secondPayload = json.parseToJsonElement(
            """
            {
              "stream": "tool",
              "runId": "assistant-run-1",
              "seq": 12,
              "data": {
                "phase": "streaming",
                "tool_name": "web_search",
                "text": "searching bank news"
              }
            }
            """.trimIndent()
        ) as JsonObject

        val firstPlan = ChatToolMessagePlanner.plan(requireNotNull(ChatToolPayloadParser.parse(firstPayload)))
        val secondPlan = ChatToolMessagePlanner.plan(requireNotNull(ChatToolPayloadParser.parse(secondPayload)))

        assertNotNull(firstPlan)
        assertNotNull(secondPlan)
        requireNotNull(firstPlan)
        requireNotNull(secondPlan)
        assert(firstPlan.toolRunId != secondPlan.toolRunId)
    }
}
