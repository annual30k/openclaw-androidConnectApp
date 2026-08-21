package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHistoryVoiceAndPendingTest {
    @Test
    fun keepsStreamingPendingAssistantWhenHistoryHasOnlyUserEcho() {
        val historyUser = ChatMessage(
            id = "history-user",
            role = MessageRole.user,
            content = "hello",
            runId = "history-user",
            sortTimestamp = 10.0
        )
        val localUser = ChatMessage(
            id = "local-user",
            role = MessageRole.user,
            content = "hello",
            runId = "local-user-client-run",
            sortTimestamp = 10.0
        )
        val pendingAssistant = ChatMessage(
            id = "pending-assistant",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "正在连接...",
            runId = "client-run",
            sortTimestamp = 11.0
        )

        val merged = mergeHistoryWithCurrentMessages(
            historyMessages = listOf(historyUser),
            currentMessages = listOf(localUser, pendingAssistant),
            currentStreamingMessageId = pendingAssistant.id,
            isTrackedPendingAssistantMessageId = { it == pendingAssistant.id }
        )

        assertEquals(listOf("local-user", "pending-assistant"), merged.map { it.id })
        assertTrue(merged.last().state == MessageState.streaming)
    }
}
