package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.ChatSessionItem
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import com.rethinkingstudio.clawlink.core.domain.NotificationPort
import com.rethinkingstudio.clawlink.core.network.RelayAPIClient
import com.rethinkingstudio.clawlink.core.network.dto.ChatHistoryResponse
import com.rethinkingstudio.clawlink.core.network.dto.ChatHistoryItem
import com.rethinkingstudio.clawlink.core.network.transport.RelayWebSocketClient
import com.rethinkingstudio.clawlink.core.network.transport.WsConnectionState
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

class ChatHistoryCanonicalSnapshotTest {
    @Test
    fun authoritativeHistoryCompletionWakesQueuedFollowUpWithoutReconnect() = runBlocking {
        val wsClient = RelayWebSocketClient()
        try {
            val connectionStateField = RelayWebSocketClient::class.java.getDeclaredField("_connectionState")
            connectionStateField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            (connectionStateField.get(wsClient) as MutableStateFlow<WsConnectionState>).value =
                WsConnectionState.connected
            val store = ChatStore(
                apiClient = RelayAPIClient(),
                wsClient = wsClient,
                notificationPort = object : NotificationPort {
                    override fun showReplyNotification(sessionKey: String, title: String, body: String) = Unit
                    override fun cancelNotification(id: Int) = Unit
                    override fun cancelAll() = Unit
                },
                chatHistoryPageFetcher = { _, _, _, _, _ ->
                    ChatHistoryResponse(
                        items = emptyList(),
                        timelineSnapshot = Json.parseToJsonElement(
                            """
                            {
                              "timelineProtocolVersion": 3,
                              "sessionKey": "main",
                              "messages": [
                                {
                                  "messageId": "server-user-active",
                                  "role": "user",
                                  "messageState": "completed",
                                  "turnId": "run-active",
                                  "runId": "run-active",
                                  "clientMessageId": "run-active",
                                  "idempotencyKey": "run-active",
                                  "timelineOrderKey": "v1|00000000000000000001|10|server-user-active",
                                  "timelineIdentityKey": "message:user:server-user-active",
                                  "timelineItemKind": "message:user",
                                  "content": [{ "type": "text", "text": "first" }]
                                },
                                {
                                  "messageId": "server-assistant-active",
                                  "role": "assistant",
                                  "messageState": "completed",
                                  "turnId": "run-active",
                                  "runId": "run-active",
                                  "timelineOrderKey": "v1|00000000000000000001|50|server-assistant-active",
                                  "timelineIdentityKey": "message:assistant:server-assistant-active",
                                  "timelineItemKind": "message:assistant",
                                  "content": [{ "type": "text", "text": "done" }]
                                }
                              ]
                            }
                            """.trimIndent()
                        )
                    )
                }
            )
            store.setStateForTest(ChatState(currentGatewayId = "gw_1", currentSessionKey = "main"))
            store.sendTextOutgoingRun("first", "gw_1", emptyList(), emptyList(), emptyList(), "run-active")
            store.sendTextOutgoingRun("follow-up", "gw_1", emptyList(), emptyList(), emptyList(), "run-queued")
            assertTrue(store.timelineOutbox.getValue("run-queued").queued)

            store.loadHistory("gw_1", "main", limit = 50)
            withTimeout(2_000L) {
                while (
                    store.timelineOutbox.getValue("run-queued").queued ||
                    store.state.value.messages
                        .singleOrNull { it.id == "user-run-queued" }
                        ?.deliveryState == "queued"
                ) {
                    yield()
                }
            }

            assertFalse(store.timelineOutbox.getValue("run-queued").queued)
            assertEquals(
                "",
                store.state.value.messages.single { it.id == "user-run-queued" }.deliveryState
            )
            assertTrue(store.state.value.isStreaming)
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun loadHistoryCanonicalTimelineSnapshotDropsStaleCompletedCacheWhenLocalUserExists() = runBlocking {
        val wsClient = RelayWebSocketClient()
        try {
            val staleSortTimestamp = Instant.parse("2026-06-09T01:35:15.000Z").toEpochMilli() / 1000.0
            val store = ChatStore(
                apiClient = RelayAPIClient(),
                wsClient = wsClient,
                notificationPort = object : NotificationPort {
                    override fun showReplyNotification(sessionKey: String, title: String, body: String) = Unit
                    override fun cancelNotification(id: Int) = Unit
                    override fun cancelAll() = Unit
                },
                chatHistoryPageFetcher = { _, _, _, _, _ ->
                    ChatHistoryResponse(
                        items = emptyList(),
                        hasMore = false,
                        timelineSnapshot = Json.parseToJsonElement(
                            """
                            {
                              "timelineProtocolVersion": 3,
                              "sessionKey": "main",
                              "rangeStartCursor": "seq:1",
                              "rangeEndCursor": "seq:3",
                              "messages": [
                                {
                                  "messageId": "user-canonical",
                                  "seq": 1,
                                  "turnSeq": 1,
                                  "role": "user",
                                  "messageState": "completed",
                                  "runId": "client-run",
                                  "turnId": "client-run",
                                  "partId": "user",
                                  "clientMessageId": "client-run",
                                  "timelineOrderKey": "main:000000000001:010-message-user:user-canonical",
                                  "timelineIdentityKey": "main:message:user-canonical",
                                  "timelineItemKind": "message:user",
                                  "createdAt": "2026-06-08T00:36:34.684Z",
                                  "content": [{ "type": "text", "text": "你好阿" }]
                                },
                                {
                                  "messageId": "assistant-canonical",
                                  "seq": 2,
                                  "turnSeq": 2,
                                  "role": "assistant",
                                  "messageState": "completed",
                                  "runId": "client-run",
                                  "turnId": "client-run",
                                  "partId": "assistant",
                                  "timelineOrderKey": "main:000000000002:050-message-assistant:assistant-canonical",
                                  "timelineIdentityKey": "main:message:assistant-canonical",
                                  "timelineItemKind": "message:assistant",
                                  "createdAt": "2026-06-08T00:36:55.548Z",
                                  "content": [{ "type": "text", "text": "你好，我在。" }]
                                }
                              ],
                              "deletedMessageIds": []
                            }
                            """.trimIndent()
                        )
                    )
                }
            )
            val localUser = ChatMessage(
                id = "local-user",
                role = MessageRole.user,
                state = MessageState.completed,
                content = "你好阿",
                runId = "local-user-client-run",
                sortTimestamp = Instant.parse("2026-06-09T01:35:10.000Z").toEpochMilli() / 1000.0
            )
            val staleAssistant = ChatMessage(
                id = "stale-assistant",
                role = MessageRole.assistant,
                state = MessageState.completed,
                content = "你好，我在。",
                createdAt = "01:35",
                runId = "client-run",
                sortTimestamp = staleSortTimestamp
            )
            store.setStateForTest(
                ChatState(
                    messages = listOf(localUser, staleAssistant),
                    currentGatewayId = "gw_1",
                    currentSessionKey = "main"
                )
            )
            store.setTimelineStateForTest(ChatTimelineState(messages = listOf(localUser, staleAssistant)))

            store.loadHistory("gw_1", "main", limit = 100)

            val messages = store.state.value.messages
            assertEquals(listOf("user-canonical", "assistant-canonical"), messages.map { it.id })
            assertEquals(
                listOf(
                    Instant.parse("2026-06-08T00:36:34.684Z").toEpochMilli() / 1000.0,
                    Instant.parse("2026-06-08T00:36:55.548Z").toEpochMilli() / 1000.0
                ),
                messages.map { it.sortTimestamp }
            )
            assertFalse(messages.any { it.sortTimestamp == staleSortTimestamp })
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun localTextAssistantPlaceholderUsesProtocolTypingMarker() {
        val assistant = buildLocalTextAssistantPlaceholderMessage(
            id = "assistant-run-1",
            clientRunId = "run-1",
            sortTimestamp = 10.001
        )

        assertEquals(MessageRole.assistant, assistant.role)
        assertEquals(MessageState.streaming, assistant.state)
        assertTrue(isProtocolTypingMarkerText(assistant.content))
        assertFalse(assistant.content.contains("正在连接"))
        assertFalse(assistant.content.contains("Connecting"))
    }

    @Test
    fun legacyAssistantFinalClearsPersistedTimelineRun() {
        val wsClient = RelayWebSocketClient()
        try {
            val store = ChatStore(
                apiClient = RelayAPIClient(),
                wsClient = wsClient,
                notificationPort = object : NotificationPort {
                    override fun showReplyNotification(sessionKey: String, title: String, body: String) = Unit
                    override fun cancelNotification(id: Int) = Unit
                    override fun cancelAll() = Unit
                }
            )
            val user = ChatMessage(
                id = "user-run-1",
                role = MessageRole.user,
                state = MessageState.completed,
                content = "reply OK",
                runId = "local-user-run-1",
                sortTimestamp = 10.0
            )
            val placeholder = buildLocalTextAssistantPlaceholderMessage(
                id = "assistant-run-1",
                clientRunId = "run-1",
                sortTimestamp = 10.001
            )
            val runScope = ChatRunScope(
                gatewayId = "gw_1",
                sessionKey = "main",
                assistantMessageId = placeholder.id,
                triggeringUserMessageId = user.id
            )
            store.setStateForTest(
                ChatState(
                    messages = listOf(user, placeholder),
                    currentGatewayId = "gw_1",
                    currentSessionKey = "main",
                    isStreaming = true
                )
            )
            store.setTimelineStateForTest(
                ChatTimelineState(
                    messages = listOf(user, placeholder),
                    activeRunId = "run-1",
                    activeRunsByTurnId = mapOf("run-1" to "run-1"),
                    activeTurnByRunId = mapOf("run-1" to "run-1")
                )
            )
            store.setRunScopesForTest(linkedMapOf("run-1" to runScope))

            store.invokeHandleFinalForTest(
                envelope = buildJsonObject {
                    put("gatewayId", "gw_1")
                    put("sessionKey", "main")
                },
                payload = buildJsonObject {
                    put("runId", "run-1")
                    put("role", "assistant")
                    put("content", "OK805")
                }
            )

            val state = store.state.value
            assertEquals(listOf("user-run-1", "assistant-run-1"), state.messages.map { it.id })
            assertEquals(MessageState.completed, state.messages.last().state)
            assertEquals("OK805", state.messages.last().content)
            assertFalse(state.isStreaming)

            val timelineState = store.timelineStateForTest()
            assertEquals(null, timelineState.activeRunId)
            assertTrue(timelineState.activeRunsByTurnId.isEmpty())
            assertTrue(timelineState.activeTurnByRunId.isEmpty())
            assertEquals(listOf(MessageState.completed, MessageState.completed), timelineState.messages.map { it.state })
        } finally {
            wsClient.destroy()
        }
    }

    @Ignore("Legacy non-canonical history failure cache behavior was removed; Relay canonical order is required.")
    @Test
    fun loadOlderHistoryFailureKeepsExistingWindowAndCursor() = runBlocking {
        var shouldFailOlder = false
        val wsClient = RelayWebSocketClient()
        try {
            val store = ChatStore(
                apiClient = RelayAPIClient(),
                wsClient = wsClient,
                notificationPort = object : NotificationPort {
                    override fun showReplyNotification(sessionKey: String, title: String, body: String) = Unit
                    override fun cancelNotification(id: Int) = Unit
                    override fun cancelAll() = Unit
                },
                chatHistoryPageFetcher = { _, _, _, cursor, _ ->
                    if (cursor == "seq:10" && shouldFailOlder) error("older history unavailable")
                    ChatHistoryResponse(
                        items = chatHistoryItems(10..12),
                        hasMore = true,
                        nextCursor = "seq:10",
                        newestCursor = "seq:12"
                    )
                }
            )

            store.loadHistory("gw_1", "main", limit = 100)
            shouldFailOlder = true
            store.loadOlderHistory("gw_1", "main")

            val state = store.state.value
            assertEquals(listOf("history-10", "history-11", "history-12"), state.messages.map { it.id })
            assertFalse(state.historyWindow.isLoadingOlder)
            assertTrue(state.historyWindow.hasOlder)
            assertEquals("seq:10", state.historyWindow.olderCursor)
            assertEquals("seq:12", state.historyWindow.newestCursor)
        } finally {
            wsClient.destroy()
        }
    }
    @Ignore("Legacy protocol-marker filtering for non-canonical history was removed; Relay canonical order is required.")
    @Test
    fun filtersProtocolTypingMarkersFromHistoryMessages() {
        val messages = buildHistoryMessagesFromItems(
            listOf(
                ChatHistoryItem(
                    id = "typing",
                    role = "assistant",
                    content = JsonPrimitive("[[clawlink:typing]][[clawlink:typing]]"),
                    createdAt = "2026-05-24T08:00:00.000Z"
                ),
                ChatHistoryItem(
                    id = "answer",
                    role = "assistant",
                    content = JsonPrimitive("final answer"),
                    createdAt = "2026-05-24T08:00:03.000Z"
                )
            )
        )

        assertEquals(listOf("answer"), messages.map { it.id })
    }

    @Test
    fun treatsProtocolTypingMarkersAsTransientAssistantPlaceholders() {
        val message = ChatMessage(
            id = "typing",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "[[clawlink:typing]][[clawlink:typing]]",
            runId = "run",
            sortTimestamp = 10.0
        )

        assertTrue(isTransientAssistantPlaceholder(message))
    }

    @Test
    fun treatsVoiceTranscriptionWaitTextAsTransientAssistantPlaceholder() {
        val zhMessage = ChatMessage(
            id = "voice-wait-zh",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "等待宿主机识别语音...",
            runId = "run",
            sortTimestamp = 10.0
        )
        val enMessage = zhMessage.copy(
            id = "voice-wait-en",
            content = "Waiting for host transcription..."
        )

        assertTrue(isTransientAssistantPlaceholder(zhMessage))
        assertTrue(isTransientAssistantPlaceholder(enMessage))
    }

    @Ignore("Legacy voice transcript replacement by local matching was removed; Relay canonical order is required.")
    @Test
    fun replacesLateVoiceTranscriptHistoryTextWithLocalVoiceMessage() {
        val historyTranscript = ChatMessage(
            id = "history-transcript",
            role = MessageRole.user,
            content = "你可以做什么？",
            runId = "voice-client-run-1",
            sortTimestamp = 1400.0
        )
        val historyAssistant = ChatMessage(
            id = "history-assistant",
            role = MessageRole.assistant,
            content = "我可以帮你处理本机任务。",
            runId = "assistant-client-run-1",
            sortTimestamp = 1401.0
        )
        val localVoice = ChatMessage(
            id = "local-voice",
            role = MessageRole.user,
            content = "voice-input.m4a",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "voice",
                    fileName = "voice-input.m4a",
                    mimeType = "audio/mp4",
                    downloadUrl = "file:///tmp/voice-input.m4a"
                )
            ),
            runId = "local-user-voice-client-run-1",
            sortTimestamp = 1000.0
        )

        val merged = mergeHistoryWithCurrentMessages(
            historyMessages = listOf(historyTranscript, historyAssistant),
            currentMessages = listOf(localVoice),
            currentStreamingMessageId = null,
            isTrackedPendingAssistantMessageId = { false }
        )

        assertEquals(listOf("local-user-voice-client-run-1", "assistant-client-run-1"), merged.map { it.runId })
        assertTrue(merged.first().hasVoiceContent)
        assertEquals("你可以做什么？", merged.first().voiceTranscriptText)
        assertFalse(merged.any { it.id == historyTranscript.id })
    }
}
