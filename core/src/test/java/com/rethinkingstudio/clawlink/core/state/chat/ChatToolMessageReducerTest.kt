package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import com.rethinkingstudio.clawlink.core.models.chat.RelayJSONValue
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatToolMessageReducerTest {
    @Test
    fun upsertsToolMessageAndPreservesExistingOrderFields() {
        val existing = ChatMessage(
            id = "tool:call-1",
            role = MessageRole.tool,
            state = MessageState.streaming,
            content = "running",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "tool_use",
                    name = "exec",
                    toolCallId = "call-1",
                    args = RelayJSONValue.StringVal("pwd")
                )
            ),
            createdAt = "created",
            runId = "call-1",
            sortTimestamp = 12.0
        )
        val plan = ChatToolMessagePlan(
            toolRunId = "tool:call-1",
            toolCallId = "call-1",
            toolName = "exec",
            role = MessageRole.tool,
            state = MessageState.completed,
            content = "done",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "tool_result",
                    name = "exec",
                    toolCallId = "call-1",
                    result = RelayJSONValue.StringVal("done")
                )
            )
        )

        val reduced = ChatToolMessageReducer.upsert(
            messages = listOf(existing),
            plan = plan,
            nowEpochSeconds = 99.0
        )

        assertEquals(1, reduced.messages.size)
        val message = reduced.message
        assertEquals("tool:call-1", message.id)
        assertEquals("created", message.createdAt)
        assertEquals(12.0, message.sortTimestamp)
        assertEquals(MessageState.completed, message.state)
        assertEquals("done", message.content)
        assertEquals(listOf("tool_use", "tool_result"), message.contentBlocks.map { it.type })
    }

    @Test
    fun appendsNewToolMessageWithStableRunIdAndTimestamp() {
        val plan = ChatToolMessagePlan(
            toolRunId = "tool:call-2",
            toolCallId = "call-2",
            toolName = "read",
            role = MessageRole.tool,
            state = MessageState.streaming,
            content = "reading",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "tool_result",
                    name = "read",
                    toolCallId = "call-2",
                    partialResult = RelayJSONValue.StringVal("reading")
                )
            )
        )

        val reduced = ChatToolMessageReducer.upsert(
            messages = emptyList(),
            plan = plan,
            nowEpochSeconds = 42.0
        )

        assertEquals(1, reduced.messages.size)
        assertEquals("tool:call-2", reduced.message.id)
        assertEquals("call-2", reduced.message.runId)
        assertEquals(42.0, reduced.message.sortTimestamp)
        assertEquals(listOf("tool_result"), reduced.message.contentBlocks.map { it.type })
    }

    @Test
    fun mergesWithExistingToolMessageUsingCanonicalToolRunId() {
        val existing = ChatMessage(
            id = "history-tool-message",
            role = MessageRole.tool,
            state = MessageState.streaming,
            content = "running",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "tool_use",
                    name = "exec",
                    toolCallId = "call-legacy"
                )
            ),
            runId = "tool:call-legacy",
            sortTimestamp = 77.0
        )
        val plan = ChatToolMessagePlan(
            toolRunId = "tool:call-legacy",
            toolCallId = "call-legacy",
            toolName = "exec",
            role = MessageRole.tool,
            state = MessageState.completed,
            content = "done",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "tool_result",
                    name = "exec",
                    toolCallId = "call-legacy",
                    result = RelayJSONValue.StringVal("done")
                )
            )
        )

        val reduced = ChatToolMessageReducer.upsert(
            messages = listOf(existing),
            plan = plan,
            nowEpochSeconds = 99.0
        )

        assertEquals(1, reduced.messages.size)
        assertEquals("history-tool-message", reduced.message.id)
        assertEquals("tool:call-legacy", reduced.message.runId)
        assertEquals(77.0, reduced.message.sortTimestamp)
        assertEquals(listOf("tool_use", "tool_result"), reduced.message.contentBlocks.map { it.type })
    }

    @Test
    fun insertsNewToolMessageBeforeAnchoredAssistantPlaceholder() {
        val user = ChatMessage(
            id = "user-1",
            role = MessageRole.user,
            content = "do it",
            sortTimestamp = 10.0
        )
        val assistant = ChatMessage(
            id = "assistant-1",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "正在连接...",
            runId = "run-1",
            sortTimestamp = 10.001
        )
        val plan = ChatToolMessagePlan(
            toolRunId = "tool:call-3",
            toolCallId = "call-3",
            toolName = "exec",
            role = MessageRole.tool,
            state = MessageState.streaming,
            content = "running",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "tool_result",
                    name = "exec",
                    toolCallId = "call-3",
                    partialResult = RelayJSONValue.StringVal("running")
                )
            )
        )

        val reduced = ChatToolMessageReducer.upsert(
            messages = listOf(user, assistant),
            plan = plan,
            nowEpochSeconds = 99.0,
            anchorAssistantMessageId = "assistant-1"
        )

        assertEquals(listOf("user-1", "tool:call-3", "assistant-1"), reduced.messages.map { it.id })
        assertEquals(10.0009, reduced.message.sortTimestamp)
    }
}
