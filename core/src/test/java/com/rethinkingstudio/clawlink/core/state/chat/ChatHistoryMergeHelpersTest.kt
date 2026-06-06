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
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
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
import org.junit.Test

class ChatHistoryMergeHelpersTest {
    @Test
    fun chatHistoryWindowStateDefaultsToIdleEmptyWindow() {
        val state = ChatHistoryWindowState()

        assertFalse(state.isLoadingOlder)
        assertFalse(state.isCatchingUp)
        assertFalse(state.hasOlder)
        assertEquals(null, state.olderCursor)
        assertEquals(null, state.newestCursor)
        assertEquals(0, state.loadedMessageCount)
        assertEquals(state, ChatState().historyWindow)
    }

    @Test
    fun abortRunCompletesStreamingPlaceholderLocally() {
        val user = ChatMessage(
            id = "user-1",
            role = MessageRole.user,
            content = "stop me",
            runId = "local-user-run-1",
            sortTimestamp = 10.0
        )
        val placeholder = ChatMessage(
            id = "assistant-1",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "正在连接...",
            runId = "run-stop-1",
            sortTimestamp = 11.0
        )

        val result = completeStreamingMessageLocallyAfterStop(
            messages = listOf(user, placeholder),
            runId = "run-stop-1"
        )

        assertEquals("run-stop-1", result.stoppedRunId)
        assertEquals(listOf(user.id), result.messages.map { it.id })
        assertFalse(result.messages.any { it.state == MessageState.streaming })
    }

    @Test
    fun abortRunCompletesEnglishStreamingPlaceholderLocally() {
        val placeholder = ChatMessage(
            id = "assistant-1",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "Connecting...",
            runId = "run-stop-en",
            sortTimestamp = 11.0
        )

        val result = completeStreamingMessageLocallyAfterStop(
            messages = listOf(placeholder),
            runId = "run-stop-en"
        )

        assertEquals("run-stop-en", result.stoppedRunId)
        assertTrue(result.messages.isEmpty())
    }

    @Test
    fun abortRunCompletesPartialStreamingTextLocally() {
        val partial = ChatMessage(
            id = "assistant-1",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "已经输出的内容",
            runId = "run-stop-2",
            sortTimestamp = 11.0
        )

        val result = completeStreamingMessageLocallyAfterStop(
            messages = listOf(partial),
            runId = "run-stop-2"
        )

        assertEquals("run-stop-2", result.stoppedRunId)
        assertEquals(1, result.messages.size)
        assertEquals(MessageState.completed, result.messages.single().state)
        assertEquals("已经输出的内容", result.messages.single().content)
    }

    @Test
    fun refreshKeepsCompletedAssistantWhenHistoryHasNotCaughtUp() {
        val historyUser = ChatMessage(
            id = "history-user-completed-lag",
            role = MessageRole.user,
            content = "hello",
            runId = "history-user-completed-lag",
            sortTimestamp = 1_000.0
        )
        val localUser = ChatMessage(
            id = "local-user-completed-lag",
            role = MessageRole.user,
            content = "hello",
            runId = "local-user-completed-lag",
            sortTimestamp = 1_000.0
        )
        val completedAssistant = ChatMessage(
            id = "assistant-completed-lag",
            role = MessageRole.assistant,
            state = MessageState.completed,
            content = "final answer",
            runId = "run-completed-lag",
            sortTimestamp = 1_001.0
        )

        val merged = mergeHistoryWithCurrentMessages(
            historyMessages = listOf(historyUser),
            currentMessages = listOf(localUser, completedAssistant),
            currentStreamingMessageId = null,
            isTrackedPendingAssistantMessageId = { false }
        )

        assertEquals(listOf("local-user-completed-lag", "run-completed-lag"), merged.map { it.runId })
    }

    @Test
    fun refreshKeepsNewRepeatedCompletedAssistantWhenOnlyOldHistoryMatches() {
        val historyUser = ChatMessage(
            id = "history-old-user",
            role = MessageRole.user,
            content = "hello",
            runId = "history-old-user",
            sortTimestamp = 100.0
        )
        val historyAssistant = ChatMessage(
            id = "history-old-assistant",
            role = MessageRole.assistant,
            state = MessageState.completed,
            content = "final answer",
            runId = "history-old-assistant",
            sortTimestamp = 101.0
        )
        val localUser = ChatMessage(
            id = "local-user-new-turn",
            role = MessageRole.user,
            content = "hello",
            runId = "local-user-new-turn",
            sortTimestamp = 1_000.0
        )
        val completedAssistant = ChatMessage(
            id = "assistant-new-turn",
            role = MessageRole.assistant,
            state = MessageState.completed,
            content = "final answer",
            runId = "run-new-turn",
            sortTimestamp = 1_001.0
        )

        val merged = mergeHistoryWithCurrentMessages(
            historyMessages = listOf(historyUser, historyAssistant),
            currentMessages = listOf(localUser, completedAssistant),
            currentStreamingMessageId = null,
            isTrackedPendingAssistantMessageId = { false }
        )

        assertEquals(
            listOf("history-old-user", "history-old-assistant", "local-user-new-turn", "run-new-turn"),
            merged.map { it.runId }
        )
    }

    @Test
    fun refreshDropsCompletedAssistantWhenHistoryAlreadyContainsIt() {
        val historyUser = ChatMessage(
            id = "history-user-completed-present",
            role = MessageRole.user,
            content = "hello",
            runId = "history-user-completed-present",
            sortTimestamp = 2_000.0
        )
        val historyAssistant = ChatMessage(
            id = "history-assistant-completed-present",
            role = MessageRole.assistant,
            state = MessageState.completed,
            content = "final answer",
            runId = "history-assistant-completed-present",
            sortTimestamp = 2_001.0
        )
        val localUser = ChatMessage(
            id = "local-user-completed-present",
            role = MessageRole.user,
            content = "hello",
            runId = "local-user-completed-present",
            sortTimestamp = 2_000.0
        )
        val completedAssistant = ChatMessage(
            id = "assistant-completed-present",
            role = MessageRole.assistant,
            state = MessageState.completed,
            content = "final answer",
            runId = "run-completed-present",
            sortTimestamp = 2_001.0
        )

        val merged = mergeHistoryWithCurrentMessages(
            historyMessages = listOf(historyUser, historyAssistant),
            currentMessages = listOf(localUser, completedAssistant),
            currentStreamingMessageId = null,
            isTrackedPendingAssistantMessageId = { false }
        )

        assertEquals(
            listOf("local-user-completed-present", "history-assistant-completed-present"),
            merged.map { it.runId }
        )
    }

    @Test
    fun olderWindowRetainsFetchedOlderMessagesWhenCurrentWindowIsFull() {
        val olderMessages = (0 until 50).map { index ->
            ChatMessage(
                id = "older-$index",
                role = MessageRole.assistant,
                content = "older $index",
                runId = "older-$index",
                sortTimestamp = index.toDouble()
            )
        }
        val currentMessages = (50 until 550).map { index ->
            ChatMessage(
                id = "current-$index",
                role = MessageRole.assistant,
                content = "current $index",
                runId = "current-$index",
                sortTimestamp = index.toDouble()
            )
        }

        val merged = mergeHistoryWithCurrentMessages(
            historyMessages = olderMessages,
            currentMessages = currentMessages,
            currentStreamingMessageId = null,
            isTrackedPendingAssistantMessageId = { false }
        )
        val bounded = olderBoundedHistoryWindowMessages(
            messages = merged,
            maxMessages = 500,
            shouldPreserveActiveMessage = { false }
        )

        assertEquals(500, bounded.size)
        assertEquals("older-0", bounded.first().id)
        assertTrue(bounded.any { it.id == "older-49" })
        assertFalse(bounded.any { it.id == "current-549" })
    }

    @Test
    fun olderWindowRetainsFetchedOlderMessagesAndActivePendingAssistant() {
        val olderMessages = (0 until 50).map { index ->
            ChatMessage(
                id = "older-$index",
                role = MessageRole.assistant,
                content = "older $index",
                runId = "older-$index",
                sortTimestamp = index.toDouble()
            )
        }
        val currentMessages = (50 until 549).map { index ->
            ChatMessage(
                id = "current-$index",
                role = MessageRole.assistant,
                content = "current $index",
                runId = "current-$index",
                sortTimestamp = index.toDouble()
            )
        } + ChatMessage(
            id = "pending-assistant",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "正在连接...",
            runId = "pending-run",
            sortTimestamp = 549.0
        )

        val merged = mergeHistoryWithCurrentMessages(
            historyMessages = olderMessages,
            currentMessages = currentMessages,
            currentStreamingMessageId = "pending-assistant",
            isTrackedPendingAssistantMessageId = { it == "pending-assistant" }
        )
        val bounded = olderBoundedHistoryWindowMessages(
            messages = merged,
            maxMessages = 500,
            shouldPreserveActiveMessage = { it.id == "pending-assistant" }
        )

        assertEquals(500, bounded.size)
        assertEquals("older-0", bounded.first().id)
        assertTrue(bounded.any { it.id == "older-49" })
        assertTrue(bounded.any { it.id == "pending-assistant" })
    }

    @Test
    fun loadOlderHistoryRetainsFetchedOlderPageAtFullStoreWindow() = runBlocking {
        val requests = mutableListOf<HistoryPageRequest>()
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
                chatHistoryPageFetcher = { gatewayId, sessionKey, limit, cursor, direction ->
                    requests += HistoryPageRequest(gatewayId, sessionKey, limit, cursor, direction)
                    if (cursor == "seq:50") {
                        ChatHistoryResponse(
                            items = chatHistoryItems(0 until 50),
                            hasMore = true,
                            nextCursor = "seq:0",
                            newestCursor = "seq:49"
                        )
                    } else {
                        ChatHistoryResponse(
                            items = chatHistoryItems(50 until 550),
                            hasMore = true,
                            nextCursor = "seq:50",
                            newestCursor = "seq:549"
                        )
                    }
                }
            )

            store.loadHistory("gw_1", "main", limit = 500)
            assertEquals(500, store.state.value.messages.size)
            assertEquals("seq:50", store.state.value.historyWindow.olderCursor)
            assertEquals(null, store.state.value.errorMessage)

            store.loadOlderHistory("gw_1", "main")

            val state = store.state.value
            assertEquals(500, state.messages.size)
            assertTrue(state.messages.any { it.id == "history-0" })
            assertTrue(state.messages.any { it.id == "history-49" })
            assertFalse(state.messages.any { it.id == "history-549" })
            assertEquals("seq:0", state.historyWindow.olderCursor)
            assertEquals(500, state.historyWindow.loadedMessageCount)
            assertTrue(requests.any { it.limit == 500 && it.cursor == null && it.direction == "older" })
            assertTrue(requests.any { it.limit == 100 && it.cursor == "seq:50" && it.direction == "older" })
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun loadHistoryFailureKeepsExistingMessagesAndClearsLoading() = runBlocking {
        var shouldFail = false
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
                chatHistoryPageFetcher = { _, _, _, _, _ ->
                    if (shouldFail) error("history unavailable")
                    ChatHistoryResponse(
                        items = chatHistoryItems(1..2),
                        hasMore = true,
                        nextCursor = "seq:1",
                        newestCursor = "seq:2"
                    )
                }
            )

            store.loadHistory("gw_1", "main", limit = 100)
            assertEquals(listOf("history-1", "history-2"), store.state.value.messages.map { it.id })

            shouldFail = true
            store.loadHistory("gw_1", "main", limit = 100)

            val state = store.state.value
            assertEquals(listOf("history-1", "history-2"), state.messages.map { it.id })
            assertFalse(state.isLoading)
            assertFalse(state.isSwitchingSession)
            assertTrue(state.errorMessage?.isNotBlank() == true)
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun staleHistoryLoadDoesNotSwitchBackAfterSessionSelection() = runBlocking {
        val requests = mutableListOf<HistoryPageRequest>()
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
                chatHistoryPageFetcher = { gatewayId, sessionKey, limit, cursor, direction ->
                    requests += HistoryPageRequest(gatewayId, sessionKey, limit, cursor, direction)
                    ChatHistoryResponse(items = emptyList())
                }
            )
            store.setStateForTest(
                ChatState(
                    currentGatewayId = "gw_1",
                    currentSessionKey = "session-b",
                    sessions = listOf(
                        ChatSessionItem(sessionKey = "session-a", lastActivityAt = null),
                        ChatSessionItem(sessionKey = "session-b", lastActivityAt = null)
                    ),
                    isSwitchingSession = true
                )
            )

            store.loadHistory("gw_1", "session-a", limit = 100)

            val state = store.state.value
            assertEquals("session-b", state.currentSessionKey)
            assertTrue(state.isSwitchingSession)
            assertTrue(requests.isEmpty())
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun loadHistoryCanReleaseGatewaySwitchOverlayBeforeSlowHistoryCompletes() = runBlocking {
        val allowHistoryResponse = CompletableDeferred<Unit>()
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
                chatHistoryPageFetcher = { _, _, _, _, _ ->
                    allowHistoryResponse.await()
                    ChatHistoryResponse(items = emptyList())
                }
            )
            store.setStateForTest(
                ChatState(
                    currentGatewayId = "gw_1",
                    currentSessionKey = "main",
                    isSwitchingSession = true
                )
            )

            val loadJob = async {
                store.loadHistory("gw_1", "main", limit = 100, keepSwitchingOverlay = false)
            }
            yield()

            val loadingState = store.state.value
            assertTrue(loadingState.isLoading)
            assertFalse(loadingState.isSwitchingSession)

            allowHistoryResponse.complete(Unit)
            loadJob.await()
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun loadHistoryTimelineSnapshotReplacesStaleLocalStreamingState() = runBlocking {
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
                chatHistoryPageFetcher = { _, _, _, _, _ ->
                    ChatHistoryResponse(
                        items = emptyList(),
                        hasMore = false,
                        timelineSnapshot = Json.parseToJsonElement(
                            """
                            {
                              "protocolVersion": 2,
                              "eventType": "history.snapshot.page",
                              "gatewayId": "gw_1",
                              "sessionKey": "main",
                              "source": "history",
                              "messages": [
                                {
                                  "turnId": "turn-1",
                                  "runId": "run-1",
                                  "messageId": "user-1",
                                  "role": "user",
                                  "messageState": "completed",
                                  "createdAt": "2026-05-29T09:18:00.000Z",
                                  "content": [{ "type": "text", "text": "hello" }]
                                },
                                {
                                  "turnId": "turn-1",
                                  "runId": "run-1",
                                  "messageId": "assistant-1",
                                  "role": "assistant",
                                  "messageState": "completed",
                                  "createdAt": "2026-05-29T09:18:05.000Z",
                                  "content": [{ "type": "text", "text": "reply" }]
                                }
                              ],
                              "attachments": []
                            }
                            """.trimIndent()
                        )
                    )
                }
            )
            val stale = ChatMessage(
                id = "assistant-stale",
                role = MessageRole.assistant,
                state = MessageState.streaming,
                content = "Connecting...",
                runId = "stale-run"
            )
            store.setStateForTest(
                ChatState(
                    messages = listOf(stale),
                    currentGatewayId = "gw_1",
                    currentSessionKey = "main",
                    isStreaming = true
                )
            )
            store.setTimelineStateForTest(
                ChatTimelineState(
                    messages = listOf(stale),
                    activeRunId = "stale-run",
                    activeRunsByTurnId = mapOf("stale-turn" to "stale-run"),
                    activeTurnByRunId = mapOf("stale-run" to "stale-turn")
                )
            )
            store.setRunScopesForTest(
                linkedMapOf(
                    "stale-run" to ChatRunScope(
                        gatewayId = "gw_1",
                        sessionKey = "main",
                        assistantMessageId = "assistant-stale",
                        triggeringUserMessageId = null
                    )
                )
            )

            store.loadHistory("gw_1", "main", limit = 100)

            val state = store.state.value
            assertEquals(listOf("user-1", "assistant-1"), state.messages.map { it.id })
            assertEquals(listOf(MessageState.completed, MessageState.completed), state.messages.map { it.state })
            assertFalse(state.isStreaming)
            assertFalse(state.isStoppingRun)
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun loadHistoryTimelineSnapshotPreservesLocalVoiceBubbleOverHistoryTranscript() = runBlocking {
        val transcript = "The."
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
                chatHistoryPageFetcher = { _, _, _, _, _ ->
                    ChatHistoryResponse(
                        items = emptyList(),
                        hasMore = false,
                        timelineSnapshot = Json.parseToJsonElement(
                            """
                            {
                              "protocolVersion": 2,
                              "eventType": "history.snapshot.page",
                              "gatewayId": "gw_1",
                              "sessionKey": "main",
                              "source": "history",
                              "messages": [
                                {
                                  "turnId": "voice-run-android",
                                  "runId": "voice-run-android",
                                  "messageId": "voice-run-android",
                                  "role": "user",
                                  "messageState": "completed",
                                  "createdAt": "2026-05-29T09:18:00.000Z",
                                  "content": [{ "type": "text", "text": "$transcript" }]
                                },
                                {
                                  "turnId": "voice-run-android",
                                  "runId": "voice-run-android",
                                  "messageId": "assistant-voice-run-android",
                                  "role": "assistant",
                                  "messageState": "completed",
                                  "createdAt": "2026-05-29T09:18:05.000Z",
                                  "content": [{ "type": "text", "text": "I’m here." }]
                                }
                              ],
                              "attachments": []
                            }
                            """.trimIndent()
                        )
                    )
                }
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
                        downloadUrl = "file:///tmp/voice-input.m4a",
                        transcript = transcript
                    )
                ),
                runId = "local-user-voice-run-android",
                sortTimestamp = 100.0
            )
            store.setStateForTest(
                ChatState(
                    messages = listOf(localVoice),
                    currentGatewayId = "gw_1",
                    currentSessionKey = "main"
                )
            )
            store.setTimelineStateForTest(ChatTimelineState(messages = listOf(localVoice)))

            store.loadHistory("gw_1", "main", limit = 100)

            val state = store.state.value
            assertEquals("local-user-voice-run-android", state.messages.first().runId)
            assertEquals("assistant-voice-run-android", state.messages[1].id)
            assertTrue(state.messages.first().hasVoiceContent)
            assertEquals(transcript, state.messages.first().voiceTranscriptText)
            assertFalse(state.messages.any { it.role == MessageRole.user && !it.hasVoiceContent && it.content == transcript })
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

    private data class HistoryPageRequest(
        val gatewayId: String,
        val sessionKey: String,
        val limit: Int,
        val cursor: String?,
        val direction: String
    )

    @Suppress("UNCHECKED_CAST")
    private fun ChatStore.setStateForTest(state: ChatState) {
        val field = ChatStore::class.java.getDeclaredField("_state")
        field.isAccessible = true
        val flow = field.get(this) as MutableStateFlow<ChatState>
        flow.value = state
    }

    private fun ChatStore.setTimelineStateForTest(state: ChatTimelineState) {
        val field = ChatStore::class.java.getDeclaredField("timelineState")
        field.isAccessible = true
        field.set(this, state)
    }

    private fun ChatStore.setRunScopesForTest(scopes: LinkedHashMap<String, ChatRunScope>) {
        val field = ChatStore::class.java.getDeclaredField("chatRunScopes")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val value = field.get(this) as MutableMap<String, ChatRunScope>
        value.clear()
        value.putAll(scopes)
    }

    private fun ChatStore.timelineStateForTest(): ChatTimelineState {
        val field = ChatStore::class.java.getDeclaredField("timelineState")
        field.isAccessible = true
        return field.get(this) as ChatTimelineState
    }

    private fun ChatStore.invokeHandleFinalForTest(envelope: JsonObject, payload: JsonElement) {
        val method = ChatStore::class.java.getDeclaredMethod(
            "handleFinal",
            JsonObject::class.java,
            JsonElement::class.java
        )
        method.isAccessible = true
        method.invoke(this, envelope, payload)
    }

    private fun chatHistoryItems(indices: IntRange): List<ChatHistoryItem> {
        return indices.map { index ->
            ChatHistoryItem(
                id = "history-$index",
                role = "assistant",
                content = JsonPrimitive("message $index"),
                createdAt = Instant.EPOCH.plusSeconds(index.toLong()).toString()
            )
        }
    }

    @Test
    fun buildsHistoryMessagesWithMonotonicSortTimestampsFromTranscriptOrder() {
        val messages = buildHistoryMessagesFromItems(
            listOf(
                ChatHistoryItem(
                    id = "history-user",
                    role = "user",
                    content = JsonPrimitive("把微信图片发过来"),
                    createdAt = "2026-05-22T08:06:02.287Z"
                ),
                ChatHistoryItem(
                    id = "history-tool",
                    role = "tool",
                    content = JsonPrimitive("""{"output":"sent"}"""),
                    createdAt = "2026-05-22T08:05:48.577Z"
                ),
                ChatHistoryItem(
                    id = "history-assistant",
                    role = "assistant",
                    content = JsonPrimitive("发过去了"),
                    createdAt = "2026-05-22T08:06:03.326Z"
                )
            )
        )

        val ordered = orderMessagesWithSourceRunAnchors(messages)

        assertEquals(listOf("history-user", "history-tool", "history-assistant"), ordered.map { it.runId })
        assertTrue((messages[1].sortTimestamp ?: 0.0) > (messages[0].sortTimestamp ?: 0.0))
    }

    @Test
    fun historyNormalizationCollapsesTimestampPrefixedUserShadowInSameTurn() {
        val messages = buildHistoryMessagesFromItems(
            listOf(
                ChatHistoryItem(
                    id = "mobile-user",
                    role = "user",
                    content = JsonPrimitive("你可以做什么吗"),
                    createdAt = "2026-05-29T08:08:00.000Z"
                ),
                ChatHistoryItem(
                    id = "host-user-shadow",
                    role = "user",
                    content = JsonPrimitive("[Fri 2026-05-29 16:08 GMT+8] 你可以做什么吗"),
                    createdAt = "2026-05-29T08:08:01.000Z"
                ),
                ChatHistoryItem(
                    id = "assistant",
                    role = "assistant",
                    content = JsonPrimitive("可以。简单说，我能当你的本地助理兼工程搭子。"),
                    createdAt = "2026-05-29T08:08:05.000Z"
                )
            )
        )

        val normalized = orderMessagesWithSourceRunAnchors(messages)

        assertEquals(listOf("mobile-user", "assistant"), normalized.map { it.runId })
        assertEquals(
            listOf("你可以做什么吗", "可以。简单说，我能当你的本地助理兼工程搭子。"),
            normalized.map { it.content }
        )
    }

    @Test
    fun doesNotLiftOldFileHistoryItemAfterNewerTurn() {
        val messages = buildHistoryMessagesFromItems(
            listOf(
                ChatHistoryItem(
                    id = "today-user",
                    role = "user",
                    content = JsonPrimitive("你好"),
                    createdAt = "2026-05-24T07:09:00.000Z"
                ),
                ChatHistoryItem(
                    id = "today-assistant",
                    role = "assistant",
                    content = JsonPrimitive("Alex，我在。"),
                    createdAt = "2026-05-24T07:09:03.000Z"
                ),
                ChatHistoryItem(
                    id = "old-image",
                    role = "assistant",
                    content = JsonPrimitive("微信图片.jpg"),
                    contentBlocks = listOf(
                        RelayChatContentBlock(
                            type = "image",
                            fileId = "old-image-file",
                            fileName = "微信图片.jpg",
                            mimeType = "image/jpeg"
                        )
                    ),
                    createdAt = "2026-05-23T11:14:00.000Z"
                )
            )
        )

        val ordered = orderMessagesWithSourceRunAnchors(messages)

        assertEquals(listOf("old-image", "today-user", "today-assistant"), ordered.map { it.runId })
        assertTrue((messages[2].sortTimestamp ?: 0.0) < (messages[0].sortTimestamp ?: 0.0))
    }

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

    @Test
    fun suppressesVoiceStreamingPendingAssistantWhenHistoryContainsTranscriptAndAssistantReply() {
        val transcript = "你可以做什么"
        val historyTranscript = ChatMessage(
            id = "voice-run-1",
            role = MessageRole.user,
            content = transcript,
            runId = "voice-run-1",
            sortTimestamp = 100.0
        )
        val historyAssistant = ChatMessage(
            id = "history-assistant-voice",
            role = MessageRole.assistant,
            content = "我可以帮你处理本机任务。",
            runId = "history-assistant-voice",
            sortTimestamp = 104.0
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
                    downloadUrl = "file:///tmp/voice-input.m4a",
                    transcript = transcript
                )
            ),
            runId = "local-user-voice-run-1",
            sortTimestamp = 99.0
        )
        val pendingAssistant = ChatMessage(
            id = "pending-assistant",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = protocolTypingMarkerText,
            runId = "voice-run-1",
            sortTimestamp = 101.0
        )

        val merged = mergeHistoryWithCurrentMessages(
            historyMessages = listOf(historyTranscript, historyAssistant),
            currentMessages = listOf(localVoice, pendingAssistant),
            currentStreamingMessageId = pendingAssistant.id,
            isTrackedPendingAssistantMessageId = { it == pendingAssistant.id }
        )

        assertEquals(listOf("local-user-voice-run-1", "history-assistant-voice"), merged.map { it.runId })
        assertTrue(merged.first().hasVoiceContent)
        assertEquals(transcript, merged.first().voiceTranscriptText)
        assertFalse(merged.any { it.id == pendingAssistant.id })
        assertFalse(merged.any { it.role == MessageRole.user && !it.hasVoiceContent && it.content == transcript })
    }

    @Test
    fun suppressesStreamingPendingAssistantWhenHistoryResolvesTurn() {
        val historyUser = ChatMessage(
            id = "history-user",
            role = MessageRole.user,
            content = "hello",
            runId = "history-user",
            sortTimestamp = 10.0
        )
        val historyAssistant = ChatMessage(
            id = "history-assistant",
            role = MessageRole.assistant,
            content = "final answer",
            runId = "history-assistant",
            sortTimestamp = 14.0
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
            historyMessages = listOf(historyUser, historyAssistant),
            currentMessages = listOf(localUser, pendingAssistant),
            currentStreamingMessageId = pendingAssistant.id,
            isTrackedPendingAssistantMessageId = { it == pendingAssistant.id }
        )

        assertEquals(listOf("local-user", "history-assistant"), merged.map { it.id })
        assertFalse(merged.any { it.id == pendingAssistant.id })
    }

    @Test
    fun coalescesDelayedHermesImagePromptEcho() {
        val historyUser = ChatMessage(
            id = "history-hermes-delayed-image-user",
            role = MessageRole.user,
            content = "帮我分析一下这张图片",
            runId = "history-hermes-delayed-image-user",
            sortTimestamp = 1_780_215_120.0
        )
        val historyAssistant = ChatMessage(
            id = "history-hermes-delayed-image-answer",
            role = MessageRole.assistant,
            content = "这是一张城市夜景。",
            runId = "history-hermes-delayed-image-answer",
            sortTimestamp = 1_780_215_123.0
        )
        val localUser = ChatMessage(
            id = "local-user",
            role = MessageRole.user,
            content = "帮我分析一下这张图片",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "file",
                    text = "album.jpeg",
                    fileName = "album.jpeg",
                    mimeType = "image/jpeg",
                    downloadUrl = "file:///tmp/album.jpeg"
                )
            ),
            runId = "local-user-hermes-delayed-image",
            sortTimestamp = 1_780_214_917.0
        )
        val pendingAssistant = ChatMessage(
            id = "pending-assistant",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = protocolTypingMarkerText,
            runId = "client-run-hermes-delayed-image",
            sortTimestamp = 1_780_214_917.001
        )

        val merged = mergeHistoryWithCurrentMessages(
            historyMessages = listOf(historyUser, historyAssistant),
            currentMessages = listOf(localUser, pendingAssistant),
            currentStreamingMessageId = pendingAssistant.id,
            isTrackedPendingAssistantMessageId = { it == pendingAssistant.id }
        )

        assertEquals(
            listOf("local-user", "history-hermes-delayed-image-answer"),
            merged.map { it.id }
        )
        assertEquals("帮我分析一下这张图片", merged.first().content)
        assertEquals("file:///tmp/album.jpeg", merged.first().fileContentBlocks.first().downloadUrl)
        assertFalse(merged.any { it.id == pendingAssistant.id })
        assertFalse(merged.any { it.id == historyUser.id })
    }

    @Test
    fun keepsCompletedLiveAssistantWhenOnlyContentMatchesOldSyntheticHistory() {
        val historyUser = ChatMessage(
            id = "history-user",
            role = MessageRole.user,
            content = "Android smoke 104037",
            runId = "history-user",
            sortTimestamp = 0.001
        )
        val historyAssistant = ChatMessage(
            id = "history-assistant",
            role = MessageRole.assistant,
            content = "看起来像是一个测试用例编号或 Bug 工单号？",
            runId = "history-assistant",
            sortTimestamp = 0.002
        )
        val localUser = ChatMessage(
            id = "local-user",
            role = MessageRole.user,
            content = "Android smoke 104037",
            runId = "local-user-client-run",
            sortTimestamp = 1_780_000_000.0
        )
        val liveAssistant = ChatMessage(
            id = "live-assistant",
            role = MessageRole.assistant,
            state = MessageState.completed,
            content = "看起来像是一个测试用例编号或 Bug 工单号？",
            runId = "client-run",
            sortTimestamp = 1_780_000_002.0
        )

        val merged = mergeHistoryWithCurrentMessages(
            historyMessages = listOf(historyUser, historyAssistant),
            currentMessages = listOf(localUser, liveAssistant),
            currentStreamingMessageId = null,
            isTrackedPendingAssistantMessageId = { false }
        )

        assertEquals(
            listOf("history-user", "history-assistant", "local-user", "live-assistant"),
            merged.map { it.id }
        )
    }

    @Test
    fun suppressesStreamingPendingAssistantWhenToolHeavyHistoryWindowStartsAfterTriggeringUser() {
        val toolMessages = (0 until 54).map { index ->
            ChatMessage(
                id = "history-tool-$index",
                role = MessageRole.tool,
                content = "tool output $index",
                runId = "history-tool-$index",
                sortTimestamp = 10.1 + index * 0.001
            )
        }
        val historyAssistant = ChatMessage(
            id = "history-assistant",
            role = MessageRole.assistant,
            content = "final answer",
            runId = "history-assistant",
            sortTimestamp = 10.9
        )
        val localUser = ChatMessage(
            id = "local-user",
            role = MessageRole.user,
            content = "analyze this project",
            runId = "local-user-client-run",
            sortTimestamp = 10.0
        )
        val pendingAssistant = ChatMessage(
            id = "pending-assistant",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "正在同步最终内容...",
            runId = "client-run",
            sortTimestamp = 10.001
        )

        val merged = mergeHistoryWithCurrentMessages(
            historyMessages = toolMessages.takeLast(49) + historyAssistant,
            currentMessages = listOf(localUser, pendingAssistant),
            currentStreamingMessageId = pendingAssistant.id,
            isTrackedPendingAssistantMessageId = { it == pendingAssistant.id }
        )

        assertEquals(localUser.id, merged.first().id)
        assertTrue(merged.any { it.id == historyAssistant.id })
        assertFalse(merged.any { it.id == pendingAssistant.id })
    }

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

    @Test
    fun mergesHistoryMediaAttachmentEchoWithMatchingLocalUserBubble() {
        val historyUser = ChatMessage(
            id = "history-user",
            role = MessageRole.user,
            content = """
                分析一下这张照片

                [media attached: /Users/example/photo.jpg (image/jpeg) | /Users/example/photo.jpg]
            """.trimIndent(),
            runId = "history-user",
            sortTimestamp = 10.0
        )
        val historyAssistant = ChatMessage(
            id = "history-assistant",
            role = MessageRole.assistant,
            content = "这是一张像素风图片。",
            runId = "history-assistant",
            sortTimestamp = 12.0
        )
        val localUser = ChatMessage(
            id = "local-user",
            role = MessageRole.user,
            content = "分析一下这张照片",
            runId = "local-user-run-1",
            sortTimestamp = 10.0
        )
        val pendingAssistant = ChatMessage(
            id = "pending-assistant",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "正在连接...",
            runId = "run-1",
            sortTimestamp = 10.001
        )

        val merged = mergeHistoryWithCurrentMessages(
            historyMessages = listOf(historyUser, historyAssistant),
            currentMessages = listOf(localUser, pendingAssistant),
            currentStreamingMessageId = pendingAssistant.id,
            isTrackedPendingAssistantMessageId = { it == pendingAssistant.id }
        )

        assertEquals(listOf("local-user", "history-assistant"), merged.map { it.id })
        assertEquals("分析一下这张照片", merged.first().content)
        assertFalse(merged.any { it.content.contains("[media attached:") })
    }

    @Test
    fun keepsLocalMobileFileSortWhenHistoryEchoArrivesAfterAssistant() {
        val fileBlock = RelayChatContentBlock(
            type = "image",
            fileId = "photo-1",
            fileName = "photo.jpg",
            mimeType = "image/jpeg",
            downloadUrl = "file:///tmp/photo.jpg"
        )
        val historyUser = ChatMessage(
            id = "history-user",
            role = MessageRole.user,
            content = "分析一下这张照片",
            runId = "history-user",
            sortTimestamp = 100.001
        )
        val historyAssistant = ChatMessage(
            id = "history-assistant",
            role = MessageRole.assistant,
            content = "这是一张像素风图片。",
            runId = "history-assistant",
            sortTimestamp = 102.0
        )
        val lateHistoryFile = ChatMessage(
            id = "history-file",
            role = MessageRole.user,
            content = "photo.jpg",
            contentBlocks = listOf(fileBlock.copy(downloadUrl = "/api/mobile/files/photo-1")),
            runId = "file-photo-1",
            sortTimestamp = 103.0
        )
        val localFile = ChatMessage(
            id = "local-file",
            role = MessageRole.user,
            content = "photo.jpg",
            contentBlocks = listOf(fileBlock),
            runId = "file-photo-1",
            sortTimestamp = 100.0
        )
        val localUser = ChatMessage(
            id = "local-user",
            role = MessageRole.user,
            content = "分析一下这张照片",
            runId = "local-user-run-1",
            sortTimestamp = 100.001
        )

        val merged = mergeHistoryWithCurrentMessages(
            historyMessages = listOf(historyUser, historyAssistant, lateHistoryFile),
            currentMessages = listOf(localFile, localUser),
            currentStreamingMessageId = null,
            isTrackedPendingAssistantMessageId = { false }
        )

        assertEquals(listOf("file-photo-1", "history-assistant"), merged.map { it.runId })
        assertEquals(100.0, merged.first().sortTimestamp ?: 0.0, 0.000001)
        assertEquals("分析一下这张照片", merged.first().content)
        assertEquals("file:///tmp/photo.jpg", merged.first().fileContentBlocks.first().downloadUrl)
    }

    @Test
    fun dropsFailedUploadPlaceholderWhenCompletedImageExistsInHistory() {
        val historyImage = ChatMessage(
            id = "history-image",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "photo.jpg",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    fileId = "file-photo",
                    fileName = "photo.jpg",
                    mimeType = "image/jpeg",
                    sizeBytes = 12345,
                    downloadUrl = "/api/mobile/files/file-photo",
                    gatewayId = "gw-hermes",
                    sessionKey = "android-e2e-hermes"
                )
            ),
            runId = "file-file-photo",
            sortTimestamp = 101.0
        )
        val failedUpload = ChatMessage(
            id = "upload-photo",
            role = MessageRole.user,
            state = MessageState.failed,
            content = "photo.jpg",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    fileName = "photo.jpg",
                    mimeType = "image/jpeg",
                    sizeBytes = 12345,
                    downloadUrl = "file:///tmp/photo.jpg",
                    gatewayId = "gw-hermes",
                    sessionKey = "android-e2e-hermes"
                )
            ),
            runId = "upload-photo",
            sortTimestamp = 100.0
        )

        val merged = mergeHistoryWithCurrentMessages(
            historyMessages = listOf(historyImage),
            currentMessages = listOf(failedUpload),
            currentStreamingMessageId = null,
            isTrackedPendingAssistantMessageId = { false }
        )

        assertEquals(listOf("file-file-photo"), merged.map { it.runId })
        assertEquals(MessageState.completed, merged.single().state)
        assertEquals("file-photo", merged.single().fileContentBlocks.single().fileId)
    }

    @Test
    fun dropsCompletedLocalFileWhenDesktopHistoryDoesNotReferenceIt() {
        val fileBlock = RelayChatContentBlock(
            type = "image",
            fileId = "relay-only-photo",
            fileName = "photo.jpg",
            mimeType = "image/jpeg",
            downloadUrl = "file:///tmp/photo.jpg"
        )
        val historyAssistant = ChatMessage(
            id = "history-assistant",
            role = MessageRole.assistant,
            content = "DONE",
            runId = "history-assistant",
            sortTimestamp = 102.0
        )
        val completedLocalFile = ChatMessage(
            id = "local-file",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "photo.jpg",
            contentBlocks = listOf(fileBlock),
            runId = "file-relay-only-photo",
            sortTimestamp = 103.0
        )

        val merged = mergeHistoryWithCurrentMessages(
            historyMessages = listOf(historyAssistant),
            currentMessages = listOf(completedLocalFile),
            currentStreamingMessageId = null,
            isTrackedPendingAssistantMessageId = { false }
        )

        assertEquals(listOf("history-assistant"), merged.map { it.id })
    }

    @Test
    fun anchorsHistoryMobileFileBeforeUserTextWhenTextContainsMediaReference() {
        val messages = buildHistoryMessagesFromItems(
            listOf(
                ChatHistoryItem(
                    id = "history-user",
                    role = "user",
                    content = JsonPrimitive(
                        """
                            分析一下这张照片

                            [media attached: /Users/example/photo.jpg (image/jpeg) | /Users/example/photo.jpg]
                        """.trimIndent()
                    ),
                    createdAt = "2026-05-24T10:00:00.000Z"
                ),
                ChatHistoryItem(
                    id = "history-assistant",
                    role = "assistant",
                    content = JsonPrimitive("这是一张像素风图片。"),
                    createdAt = "2026-05-24T10:00:02.000Z"
                ),
                ChatHistoryItem(
                    id = "history-file",
                    role = "user",
                    content = JsonPrimitive("photo.jpg"),
                    contentBlocks = listOf(
                        RelayChatContentBlock(
                            type = "image",
                            fileId = "photo-1",
                            fileName = "photo.jpg",
                            mimeType = "image/jpeg",
                            downloadUrl = "/api/mobile/files/photo-1"
                        )
                    ),
                    createdAt = "2026-05-24T10:00:03.000Z"
                )
            )
        )

        val ordered = orderMessagesWithSourceRunAnchors(messages)

        assertEquals(listOf("history-file", "history-assistant"), ordered.map { it.runId })
        assertTrue((ordered[0].sortTimestamp ?: 0.0) < (ordered[1].sortTimestamp ?: 0.0))
        assertEquals("分析一下这张照片", ordered[0].content)
        assertEquals("/api/mobile/files/photo-1", ordered[0].fileContentBlocks.first().downloadUrl)
    }

    @Test
    fun dropsOpenClawInternalContinuationDuplicateUserPrompt() {
        val messages = orderMessagesWithSourceRunAnchors(
            listOf(
                ChatMessage(
                    id = "history-voice-prompt",
                    role = MessageRole.user,
                    content = "测试语音功能不需要回复。",
                    runId = "history-voice-prompt",
                    sortTimestamp = 100.0
                ),
                ChatMessage(
                    id = "history-internal-continuation",
                    role = MessageRole.user,
                    content = """
                        测试语音功能不需要回复。

                        The previous attempt did not produce a user-visible answer. Continue from the current state and produce the visible answer now. Do not restart from scratch.
                    """.trimIndent(),
                    runId = "history-internal-continuation",
                    sortTimestamp = 148.0
                ),
                ChatMessage(
                    id = "history-answer",
                    role = MessageRole.assistant,
                    content = "已回复。",
                    runId = "history-answer",
                    sortTimestamp = 149.0
                )
            )
        )

        assertEquals(listOf("history-voice-prompt", "history-answer"), messages.map { it.runId })
        assertEquals("测试语音功能不需要回复。", messages.first().content)
        assertFalse(messages.any { it.content.contains("previous attempt", ignoreCase = true) })
    }

    @Test
    fun dropsInternalVisionContextToolResult() {
        val messages = orderMessagesWithSourceRunAnchors(
            listOf(
                ChatMessage(
                    id = "image-prompt",
                    role = MessageRole.user,
                    content = "帮我分析一下这张图片",
                    contentBlocks = listOf(
                        RelayChatContentBlock(
                            type = "image",
                            fileId = "file-image-1",
                            fileName = "album.jpeg",
                            mimeType = "image/jpeg",
                            downloadUrl = "/api/mobile/files/file-image-1"
                        )
                    ),
                    runId = "file-file-image-1",
                    sortTimestamp = 100.0
                ),
                ChatMessage(
                    id = "vision-tool",
                    role = MessageRole.tool,
                    content = "Image loaded into your context - you can see it natively now. Use your built-in vision to answer the user.",
                    contentBlocks = listOf(
                        RelayChatContentBlock(
                            type = "tool_result",
                            text = "Image loaded into your context - you can see it natively now. Use your built-in vision to answer the user.",
                            name = "tool"
                        )
                    ),
                    runId = "tool:vision",
                    sortTimestamp = 101.0
                ),
                ChatMessage(
                    id = "history-answer",
                    role = MessageRole.assistant,
                    content = "这是一张图片。",
                    runId = "history-answer",
                    sortTimestamp = 102.0
                )
            )
        )

        assertEquals(listOf("file-file-image-1", "history-answer"), messages.map { it.runId })
        assertFalse(messages.any { it.id == "vision-tool" })
    }

    @Test
    fun coalescesDelayedHistoryFilePromptIntoEarlierUserPrompt() {
        val fileBlock = RelayChatContentBlock(
            type = "image",
            fileId = "photo-1",
            fileName = "photo.jpg",
            mimeType = "image/jpeg",
            downloadUrl = "/api/mobile/files/photo-1"
        )

        val messages = orderMessagesWithSourceRunAnchors(
            listOf(
                ChatMessage(
                    id = "history-user",
                    role = MessageRole.user,
                    content = "分析一下这张照片",
                    runId = "history-user",
                    sortTimestamp = 100.0
                ),
                ChatMessage(
                    id = "history-assistant",
                    role = MessageRole.assistant,
                    content = "这是一张像素风图片。",
                    runId = "history-assistant",
                    sortTimestamp = 101.0
                ),
                ChatMessage(
                    id = "history-file",
                    role = MessageRole.user,
                    content = "photo.jpg",
                    contentBlocks = listOf(fileBlock),
                    runId = "file-photo-1",
                    sortTimestamp = 102.0
                )
            )
        )

        assertEquals(listOf("history-user", "history-assistant"), messages.map { it.runId })
        assertEquals("分析一下这张照片", messages.first().content)
        assertEquals(listOf(fileBlock), messages.first().fileContentBlocks)
    }

    @Test
    fun mergesLatePlainImagePromptBackIntoEarlierStandaloneFileMessage() {
        val fileBlock = RelayChatContentBlock(
            type = "image",
            fileId = "photo-1",
            fileName = "photo.jpg",
            mimeType = "image/jpeg",
            downloadUrl = "/api/mobile/files/photo-1"
        )

        val messages = orderMessagesWithSourceRunAnchors(
            listOf(
                ChatMessage(
                    id = "history-file",
                    role = MessageRole.user,
                    content = "photo.jpg",
                    contentBlocks = listOf(fileBlock),
                    runId = "file-photo-1",
                    sortTimestamp = 100.0
                ),
                ChatMessage(
                    id = "history-user",
                    role = MessageRole.user,
                    content = "分析一下这张图片",
                    runId = "history-user",
                    sortTimestamp = 101.0
                ),
                ChatMessage(
                    id = "history-assistant",
                    role = MessageRole.assistant,
                    content = "这是一张花的图片。",
                    runId = "history-assistant",
                    sortTimestamp = 102.0
                )
            )
        )

        assertEquals(listOf("file-photo-1", "history-assistant"), messages.map { it.runId })
        assertEquals("分析一下这张图片", messages.first().content)
        assertEquals(listOf(fileBlock), messages.first().fileContentBlocks)
    }

    @Test
    fun coalescesCompactMediaUriEchoIntoLocalImagePrompt() {
        val localImageBlock = RelayChatContentBlock(
            type = "image",
            fileName = "album-8E28059F-104B-43E1-8059-2E97E07F0E1B.heic",
            mimeType = "image/heic",
            downloadUrl = "file:///tmp/album-8E28059F-104B-43E1-8059-2E97E07F0E1B.heic"
        )
        val messages = orderMessagesWithSourceRunAnchors(
            listOf(
                ChatMessage(
                    id = "local-image",
                    role = MessageRole.user,
                    content = "分析一下这张图片",
                    contentBlocks = listOf(localImageBlock),
                    runId = "local-user-mobile-run",
                    sortTimestamp = 100.0
                ),
                ChatMessage(
                    id = "history-media-echo",
                    role = MessageRole.user,
                    content = """
                        分析一下这张图片

                        [media attached: media://inbound/album-8E28059F-104B-43E1-8059-2E97E07F0E1B---d786f4a0-bb83-4853-97ae-cb7a604326e0.heic]
                    """.trimIndent(),
                    runId = "history-media-echo",
                    sortTimestamp = 101.0
                ),
                ChatMessage(
                    id = "history-answer",
                    role = MessageRole.assistant,
                    content = "这是一张花的图片。",
                    runId = "history-answer",
                    sortTimestamp = 102.0
                )
            )
        )

        assertEquals(listOf("local-user-mobile-run", "history-answer"), messages.map { it.runId })
        assertEquals("分析一下这张图片", messages.first().content)
        assertEquals(1, messages.first().fileContentBlocks.size)
        assertEquals("file:///tmp/album-8E28059F-104B-43E1-8059-2E97E07F0E1B.heic", messages.first().fileContentBlocks.first().downloadUrl)
        assertFalse(messages.any { it.content.contains("media://inbound") })
    }

    @Test
    fun dropsDuplicateFileTransferStatusTextAcrossLaterUserTurn() {
        val statusText = "已发： 微信图片_20260427092438_279_84.jpg\n\n状态 completed，尺寸 1280 x 1280，大小 82,788 bytes。"
        val messages = orderMessagesWithSourceRunAnchors(
            listOf(
                ChatMessage(
                    id = "history-file-status",
                    role = MessageRole.assistant,
                    content = statusText,
                    createdAt = "2026-05-28T07:38:25.000Z",
                    runId = "history-file-status",
                    sortTimestamp = 100.0
                ),
                ChatMessage(
                    id = "history-next-user",
                    role = MessageRole.user,
                    content = "你可以做什么",
                    createdAt = "2026-05-28T08:26:00.000Z",
                    runId = "history-next-user",
                    sortTimestamp = 200.0
                ),
                ChatMessage(
                    id = "history-file-status-shadow",
                    role = MessageRole.assistant,
                    content = statusText,
                    createdAt = "2026-05-28T07:38:25.000Z",
                    runId = "history-file-status-shadow",
                    sortTimestamp = 201.0
                ),
                ChatMessage(
                    id = "history-next-answer",
                    role = MessageRole.assistant,
                    content = "我能帮你做这些。",
                    createdAt = "2026-05-28T08:26:03.000Z",
                    runId = "history-next-answer",
                    sortTimestamp = 202.0
                )
            )
        )

        assertEquals(
            listOf("history-file-status", "history-next-user", "history-next-answer"),
            messages.map { it.runId }
        )
    }

    @Test
    fun keepsRepeatedFileTransferStatusTextWithDifferentCreatedAt() {
        val statusText = "已发： 微信图片_20260427092438_279_84.jpg\n\n状态 completed，尺寸 1280 x 1280，大小 82,788 bytes。"
        val messages = orderMessagesWithSourceRunAnchors(
            listOf(
                ChatMessage(
                    id = "history-file-status-1",
                    role = MessageRole.assistant,
                    content = statusText,
                    createdAt = "2026-05-28T07:38:25.000Z",
                    runId = "history-file-status-1",
                    sortTimestamp = 100.0
                ),
                ChatMessage(
                    id = "history-next-user",
                    role = MessageRole.user,
                    content = "再发一次",
                    createdAt = "2026-05-28T07:43:00.000Z",
                    runId = "history-next-user",
                    sortTimestamp = 400.0
                ),
                ChatMessage(
                    id = "history-file-status-2",
                    role = MessageRole.assistant,
                    content = statusText,
                    createdAt = "2026-05-28T07:43:05.000Z",
                    runId = "history-file-status-2",
                    sortTimestamp = 405.0
                )
            )
        )

        assertEquals(
            listOf("history-file-status-1", "history-next-user", "history-file-status-2"),
            messages.map { it.runId }
        )
    }

}
