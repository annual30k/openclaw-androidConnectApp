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
import org.junit.Ignore
import org.junit.Test

class ChatHistoryCanonicalSnapshotTest {
    @Ignore("Legacy non-canonical snapshot without timelineOrderKey/timelineIdentityKey is no longer supported.")
    @Test
    fun loadHistoryCanonicalTimelineSnapshotKeepsInterleavedOrderAndAttachmentSlot() = runBlocking {
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
                              "timelineProtocolVersion": 3,
                              "sessionKey": "main",
                              "messages": [
                                {
                                  "messageId": "user-1",
                                  "seq": 1,
                                  "turnSeq": 1,
                                  "role": "user",
                                  "messageState": "completed",
                                  "runId": "turn-1",
                                  "turnId": "turn-1",
                                  "createdAt": "2026-06-08T12:00:00.000Z",
                                  "content": [{ "type": "text", "text": "第一问" }]
                                },
                                {
                                  "messageId": "assistant-1",
                                  "seq": 2,
                                  "turnSeq": 2,
                                  "role": "assistant",
                                  "messageState": "completed",
                                  "runId": "turn-1",
                                  "turnId": "turn-1",
                                  "createdAt": "2026-06-08T12:00:00.000Z",
                                  "content": [{ "type": "text", "text": "第一答" }]
                                },
                                {
                                  "messageId": "user-2",
                                  "seq": 3,
                                  "turnSeq": 3,
                                  "role": "user",
                                  "messageState": "completed",
                                  "runId": "turn-2",
                                  "turnId": "turn-2",
                                  "createdAt": "2026-06-08T12:00:00.000Z",
                                  "content": [
                                    {
                                      "type": "image",
                                      "text": "photo.jpg",
                                      "fileId": "file-photo-1",
                                      "fileName": "photo.jpg",
                                      "mimeType": "image/jpeg",
                                      "downloadUrl": "/api/mobile/files/file-photo-1"
                                    }
                                  ],
                                  "attachmentIds": ["file-photo-1"]
                                },
                                {
                                  "messageId": "assistant-2",
                                  "seq": 4,
                                  "turnSeq": 4,
                                  "role": "assistant",
                                  "messageState": "completed",
                                  "runId": "turn-2",
                                  "turnId": "turn-2",
                                  "createdAt": "2026-06-08T12:00:00.000Z",
                                  "content": [{ "type": "text", "text": "第二答" }]
                                }
                              ],
                              "deletedMessageIds": []
                            }
                            """.trimIndent()
                        )
                    )
                }
            )

            store.loadHistory("gw_1", "main", limit = 100)

            val messages = store.state.value.messages
            assertEquals(listOf("user-1", "assistant-1", "user-2", "assistant-2"), messages.map { it.id })
            assertEquals(listOf(MessageRole.user, MessageRole.assistant, MessageRole.user, MessageRole.assistant), messages.map { it.role })
            assertEquals(messages.map { it.id }.distinct(), messages.map { it.id })
            assertEquals("file-photo-1", messages[2].contentBlocks.single().fileId)
            assertTrue(messages[3].contentBlocks.none { it.fileId == "file-photo-1" })
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

    @Ignore("Legacy transcript-order timestamp synthesis was removed; Relay canonical order is required.")
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

    @Ignore("Legacy timestamp-prefixed user shadow collapse was removed; relay canonical identity is required.")
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

    @Ignore("Legacy file anchoring by inferred media references was removed; Relay canonical order is required.")
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

    @Ignore("Legacy structured timestamp ordering was removed; Relay canonical order is required.")
    @Test
    fun usesStructuredSortTimestampWhenHistoryCreatedAtIsDisplayOnly() {
        val messages = buildHistoryMessagesFromItems(
            listOf(
                ChatHistoryItem(
                    id = "assistant-current",
                    role = "assistant",
                    content = JsonPrimitive("我能做很多事情。"),
                    createdAt = "刚刚",
                    sortTimestamp = 1_780_996_300.0,
                    seq = 10
                ),
                ChatHistoryItem(
                    id = "file-file_afcb5884fb474b4bb7c64e44ad545af8",
                    role = "assistant",
                    content = JsonPrimitive("截图好了，发给你：\n/Users/qiuqiquan/.clawconnect/hermes/outbox/desktop_screenshot_20260609_143747.png"),
                    contentBlocks = listOf(
                        RelayChatContentBlock(
                            type = "image",
                            fileId = "file_afcb5884fb474b4bb7c64e44ad545af8",
                            fileName = "desktop_screenshot_20260609_143747.png",
                            mimeType = "image/png"
                        )
                    ),
                    createdAt = "14:37",
                    sortTimestamp = 1_780_987_074.952,
                    seq = 6
                ),
                ChatHistoryItem(
                    id = "user-local",
                    role = "user",
                    content = JsonPrimitive("你可以做什么"),
                    createdAt = "刚刚",
                    sortTimestamp = 1_780_996_299.522,
                    seq = 9
                )
            )
        )

        val ordered = orderMessagesWithSourceRunAnchors(messages)

        assertEquals(
            listOf(
                "file-file_afcb5884fb474b4bb7c64e44ad545af8",
                "user-local",
                "assistant-current"
            ),
            ordered.map { it.runId }
        )
        assertEquals("14:37", messages[1].createdAt)
        assertEquals(1_780_987_074.952, messages[1].sortTimestamp ?: 0.0, 0.0001)
    }

    @Ignore("Legacy wrapped seq ordering was removed; Relay canonical order is required.")
    @Test
    fun doesNotLetWrappedOrdinalTimelineSeqMoveNewerRealtimeTurnAboveHistory() {
        val ordered = orderMessagesWithSourceRunAnchors(
            listOf(
                ChatMessage(
                    id = "newest-ping",
                    role = MessageRole.user,
                    content = "Ping",
                    runId = "local-user-ping",
                    sortTimestamp = 1_781_013_969.0,
                    seq = 3
                ),
                ChatMessage(
                    id = "newer-user",
                    role = MessageRole.user,
                    content = "iOS森的什么科001",
                    runId = "local-user-new",
                    sortTimestamp = 1_780_999_296.0,
                    seq = 1
                ),
                ChatMessage(
                    id = "earlier-user",
                    role = MessageRole.user,
                    content = "你好啊",
                    runId = "history-61",
                    sortTimestamp = 1_780_990_339.0,
                    seq = 61
                ),
                ChatMessage(
                    id = "previous-assistant",
                    role = MessageRole.assistant,
                    content = "找到图片了",
                    runId = "history-60",
                    sortTimestamp = 1_780_990_000.0,
                    seq = 60
                )
            )
        )

        assertEquals(
            listOf("previous-assistant", "earlier-user", "newer-user", "newest-ping"),
            ordered.map { it.id }
        )
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
