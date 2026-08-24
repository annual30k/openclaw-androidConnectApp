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
import com.rethinkingstudio.clawlink.core.network.dto.ChatSyncResponse
import com.rethinkingstudio.clawlink.core.network.transport.RelayWebSocketClient
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
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
import org.junit.Test

class ChatHistoryMergeHelpersTest {
    @Test
    fun successfulCursorReplayReleasesSessionSwitchOverlayWithoutHistoryFallback() = runBlocking {
        val operations = mutableListOf<String>()
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
                    operations += "history"
                    ChatHistoryResponse(items = emptyList())
                },
                chatTimelineSyncPageFetcher = { _, _, cursor ->
                    operations += "sync:$cursor"
                    ChatSyncResponse(
                        nextCursor = cursor,
                        latestCursor = cursor,
                        hasMore = false
                    )
                },
                chatTimelineSyncCursorLoader = { _, _ -> "ts1.current" }
            )
            store.setStateForTest(
                ChatState(
                    currentGatewayId = "gw_1",
                    currentSessionKey = "session-b",
                    isSwitchingSession = true
                )
            )

            store.reconcileTimelineAfterLocalRestore("gw_1", "session-b")
            withTimeout(3_000) {
                while (store.state.value.isSwitchingSession) yield()
            }

            assertEquals(listOf("sync:ts1.current"), operations)
            assertFalse(store.state.value.isSwitchingSession)
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun reconnectBootstrapReplacesExpiredCursorBeforeHistoryAndReplaysConcurrentEvent() = runBlocking {
        val operations = mutableListOf<String>()
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
                    operations += "history"
                    ChatHistoryResponse(
                        items = emptyList(),
                        timelineSnapshot = Json.parseToJsonElement(
                            """
                            {
                              "timelineProtocolVersion": 4,
                              "sessionKey": "main",
                              "snapshotRevision": "checkpoint-first",
                              "messages": [{
                                "messageId": "assistant-history",
                                "conversationSeq": 1,
                                "role": "assistant",
                                "messageState": "completed",
                                "timelineOrderKey": "v4|0|00000000000000000001|50|assistant-history",
                                "timelineIdentityKey": "v1|main|message|assistant|assistant-history",
                                "timelineItemKind": "message:assistant",
                                "content": [{"type":"text","text":"from history"}]
                              }]
                            }
                            """.trimIndent()
                        )
                    )
                },
                chatTimelineSyncPageFetcher = { _, _, cursor ->
                    operations += "sync:${cursor ?: "nil"}"
                    if (cursor == "ts1.expired") {
                        throw IllegalStateException("cursor_expired")
                    } else if (cursor == null) {
                        ChatSyncResponse(
                            nextCursor = "ts1.checkpoint",
                            latestCursor = "ts1.checkpoint"
                        )
                    } else {
                        ChatSyncResponse(
                            events = listOf(Json.parseToJsonElement(
                                """
                                {
                                  "protocolVersion": 2,
                                  "eventId": "evt-android-concurrent",
                                  "eventType": "message.completed",
                                  "gatewayId": "gw_1",
                                  "sessionKey": "main",
                                  "conversationSeq": 2,
                                  "turnId": "turn-android-concurrent",
                                  "runId": "run-android-concurrent",
                                  "messageId": "assistant-concurrent",
                                  "role": "assistant",
                                  "messageState": "completed",
                                  "timelineOrderKey": "v4|0|00000000000000000002|50|assistant-concurrent",
                                  "timelineIdentityKey": "v1|main|message|assistant|assistant-concurrent",
                                  "timelineItemKind": "message:assistant",
                                  "content": [{"type":"text","text":"arrived during history"}]
                                }
                                """.trimIndent()
                            )),
                            nextCursor = "ts1.after",
                            latestCursor = "ts1.after"
                        )
                    }
                },
                chatTimelineSyncCursorLoader = { _, _ -> "ts1.expired" }
            )
            store.setStateForTest(ChatState(currentGatewayId = "gw_1", currentSessionKey = "main"))

            store.reconcileTimelineAfterLocalRestore("gw_1", "main")
            withTimeout(3_000) {
                while (store.state.value.messages.size < 2) yield()
            }

            assertEquals(listOf("sync:ts1.expired", "sync:nil", "history", "sync:ts1.checkpoint"), operations)
            assertEquals(listOf("from history", "arrived during history"), store.state.value.messages.map { it.content })
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun canonicalAssistantTextWithTrailingToolCallKeepsAssistantRole() {
        val messages = buildHistoryMessagesFromItems(
            listOf(
                ChatHistoryItem(
                    id = "assistant-analysis-and-search",
                    role = "assistant",
                    content = JsonPrimitive("这是一张标准证件照。关于蜘蛛侠图片，我来找找："),
                    contentBlocks = listOf(
                        RelayChatContentBlock(type = "text", text = "这是一张标准证件照。关于蜘蛛侠图片，我来找找："),
                        RelayChatContentBlock(type = "tool_call", name = "exec", toolCallId = "call-find-spider")
                    ),
                    createdAt = "2026-07-27T02:23:00Z",
                    timelineOrderKey = "v4|0|00000000000000000010|50|assistant-analysis-and-search",
                    timelineIdentityKey = "v1|main|message|assistant|assistant-analysis-and-search",
                    timelineItemKind = "message:assistant"
                )
            )
        )

        assertEquals(1, messages.size)
        assertEquals(MessageRole.assistant, messages.single().role)
        assertFalse(messages.single().hasToolContent)
        assertTrue(messages.single().shouldDisplayInChat(showInvocationProcess = false))
    }

    @Test
    fun staleCanonicalAssistantToolCallOnlyHistoryBecomesToolInsteadOfBlankBubble() {
        val messages = buildHistoryMessagesFromItems(
            listOf(
                ChatHistoryItem(
                    id = "assistant-tool-only",
                    role = "assistant",
                    content = JsonPrimitive("{ \"command\": \"find spiderman.jpg\" }"),
                    contentBlocks = listOf(
                        RelayChatContentBlock(type = "thinking"),
                        RelayChatContentBlock(type = "tool_call", name = "exec", toolCallId = "call-find-spider")
                    ),
                    createdAt = "2026-07-27T02:23:01Z",
                    timelineOrderKey = "v4|0|00000000000000000012|30|assistant-tool-only",
                    timelineIdentityKey = "v1|main|tool|call-find-spider",
                    timelineItemKind = "message:assistant"
                )
            )
        )

        assertEquals(1, messages.size)
        assertEquals(MessageRole.tool, messages.single().role)
        assertFalse(messages.single().shouldDisplayInChat(showInvocationProcess = false))
        assertTrue(messages.single().shouldDisplayInChat(showInvocationProcess = true))
    }

    @Test
    fun samePendingUploadMessageDoesNotGuessByFileMetadataAlone() {
        val pending = ChatMessage(
            id = "attachment-1",
            role = MessageRole.user,
            state = MessageState.streaming,
            content = "same-name.png",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    fileName = "same-name.png",
                    mimeType = "image/png",
                    sizeBytes = 42,
                    gatewayId = "gateway-1",
                    sessionKey = "main"
                )
            ),
            runId = "upload-attachment-1",
            sortTimestamp = 10.0
        )
        val completed = ChatMessage(
            id = "file-file-2",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "same-name.png",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    fileId = "file-2",
                    fileName = "same-name.png",
                    mimeType = "image/png",
                    sizeBytes = 42,
                    downloadUrl = "/api/mobile/files/file-2",
                    gatewayId = "gateway-1",
                    sessionKey = "main"
                )
            ),
            runId = "file-file-2",
            sortTimestamp = 11.0
        )

        assertFalse(samePendingUploadMessage(pending, completed))
    }

    @Test
    fun samePendingUploadMessageMatchesByAttachmentId() {
        val pending = ChatMessage(
            id = "attachment-1",
            role = MessageRole.user,
            state = MessageState.streaming,
            content = "same-name.png",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    attachmentId = "attachment-1",
                    fileName = "same-name.png",
                    mimeType = "image/png",
                    sizeBytes = 42
                )
            ),
            runId = "upload-attachment-1",
            sortTimestamp = 10.0
        )
        val completed = ChatMessage(
            id = "file-file-2",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "renamed-final.png",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    attachmentId = "attachment-1",
                    fileId = "file-2",
                    fileName = "renamed-final.png",
                    mimeType = "image/png",
                    sizeBytes = 84,
                    downloadUrl = "/api/mobile/files/file-2"
                )
            ),
            runId = "file-file-2",
            sortTimestamp = 11.0
        )

        assertTrue(samePendingUploadMessage(pending, completed))
    }

    @Test
    fun samePendingUploadMessageDoesNotMergeAcrossRolesWhenAttachmentIdMatches() {
        val pending = ChatMessage(
            id = "attachment-1",
            role = MessageRole.user,
            state = MessageState.streaming,
            content = "same-name.png",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    attachmentId = "attachment-1",
                    fileName = "same-name.png",
                    mimeType = "image/png",
                    sizeBytes = 42
                )
            ),
            runId = "upload-attachment-1",
            sortTimestamp = 10.0
        )
        val assistantCompleted = ChatMessage(
            id = "file-file-2",
            role = MessageRole.assistant,
            state = MessageState.completed,
            content = "same-name.png",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    attachmentId = "attachment-1",
                    fileId = "file-2",
                    fileName = "same-name.png",
                    mimeType = "image/png",
                    sizeBytes = 84,
                    downloadUrl = "/api/mobile/files/file-2"
                )
            ),
            runId = "file-file-2",
            sortTimestamp = 11.0
        )

        assertFalse(samePendingUploadMessage(pending, assistantCompleted))
    }

    @Test
    fun sameFileMessageDoesNotMergeByFileMetadataWithoutStableIdentity() {
        val localPlaceholder = ChatMessage(
            id = "attachment-1",
            role = MessageRole.user,
            state = MessageState.streaming,
            content = "duplicate-name.png",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    fileName = "duplicate-name.png",
                    mimeType = "image/png",
                    sizeBytes = 42,
                    gatewayId = "gateway-1",
                    sessionKey = "main"
                )
            ),
            runId = "upload-attachment-1",
            sortTimestamp = 10.0
        )
        val completedOtherUpload = ChatMessage(
            id = "file-file-2",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "duplicate-name.png",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    fileId = "file-2",
                    fileName = "duplicate-name.png",
                    mimeType = "image/png",
                    sizeBytes = 42,
                    downloadUrl = "/api/mobile/files/file-2",
                    gatewayId = "gateway-1",
                    sessionKey = "main"
                )
            ),
            runId = "file-file-2",
            sortTimestamp = 11.0
        )

        assertFalse(sameFileMessage(localPlaceholder, completedOtherUpload))
    }

    @Test
    fun sameFileMessageDoesNotMergeAcrossRolesWhenAttachmentIdMatches() {
        val localPlaceholder = ChatMessage(
            id = "attachment-1",
            role = MessageRole.user,
            state = MessageState.streaming,
            content = "shared.png",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    attachmentId = "att-shared",
                    fileName = "shared.png",
                    mimeType = "image/png"
                )
            ),
            runId = "upload-att-shared",
            sortTimestamp = 10.0
        )
        val assistantFile = ChatMessage(
            id = "assistant-file",
            role = MessageRole.assistant,
            state = MessageState.completed,
            content = "shared.png",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    attachmentId = "att-shared",
                    fileId = "file-shared",
                    fileName = "shared.png",
                    mimeType = "image/png",
                    downloadUrl = "/api/mobile/files/file-shared"
                )
            ),
            runId = "file-file-shared",
            sortTimestamp = 11.0
        )

        assertFalse(sameFileMessage(localPlaceholder, assistantFile))
    }

    @Test
    fun sameFileMessageMatchesByAttachmentIdEvenWhenFileNameChanges() {
        val initial = ChatMessage(
            id = "message-1",
            role = MessageRole.assistant,
            state = MessageState.completed,
            content = "draft.png",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    attachmentId = "att-1",
                    fileName = "draft.png",
                    mimeType = "image/png"
                )
            ),
            runId = "attachment-run",
            sortTimestamp = 10.0
        )
        val renamed = ChatMessage(
            id = "message-2",
            role = MessageRole.assistant,
            state = MessageState.completed,
            content = "final.png",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    attachmentId = "att-1",
                    fileId = "file-2",
                    fileName = "final.png",
                    mimeType = "image/png",
                    downloadUrl = "/api/mobile/files/file-2"
                )
            ),
            runId = "file-file-2",
            sortTimestamp = 11.0
        )

        assertTrue(sameFileMessage(initial, renamed))
    }

    @Test
    fun sameFileMessageMatchesHistoricalAliasesOnlyWithExplicitProjection() {
        val first = ChatMessage(
            id = "first",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "look",
            contentBlocks = listOf(RelayChatContentBlock(
                type = "image",
                attachmentId = "attachment-old-a",
                fileId = "file-old-a",
                fileName = "photo.png",
                mimeType = "image/png",
                sha256 = "same-digest",
                sourceRunId = "run-1"
            )),
            runId = "file-file-old-a"
        )
        val duplicateAlias = ChatMessage(
            id = "second",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "look",
            contentBlocks = listOf(RelayChatContentBlock(
                type = "image",
                attachmentId = "attachment-old-b",
                projectionOf = "attachment-old-a",
                fileId = "file-old-b",
                fileName = "photo.png",
                mimeType = "image/png",
                contentHash = "same-digest",
                sourceRunId = "run-1"
            )),
            runId = "file-file-old-b"
        )

        assertTrue(sameFileMessage(first, duplicateAlias))
        assertFalse(sameFileMessage(first, duplicateAlias.copy(
            contentBlocks = duplicateAlias.contentBlocks.map {
                it.copy(projectionOf = null, sourceRunId = "run-1")
            }
        )))
    }

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
    fun canonicalHistoryRefreshDropsStaleCompletedCacheButKeepsPendingTurnOverlay() {
        val staleCompleted = canonicalMessage(
            id = "stale-assistant",
            role = MessageRole.assistant,
            content = "old answer",
            order = "0001|50|stale",
            identity = "main:message:stale-assistant",
            runId = "old-run"
        )
        val localUser = ChatMessage(
            id = "local-user-run-1",
            role = MessageRole.user,
            content = "new question",
            runId = "local-user-run-1",
            sortTimestamp = 100.0,
            timelineOrderKey = "local:run-1:10:local-user-run-1",
            timelineIdentityKey = "local:message:user:run-1",
            timelineItemKind = "message:user"
        )
        val waiting = ChatMessage(
            id = "assistant-waiting",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "[[clawlink:typing]]",
            runId = "run-1",
            sortTimestamp = 100.001,
            timelineOrderKey = "local:run-1:20:assistant-waiting",
            timelineIdentityKey = "local:waiting:run-1",
            timelineItemKind = "waiting"
        )

        val merged = mergeHistoryWithCurrentMessages(
            historyMessages = emptyList(),
            currentMessages = listOf(staleCompleted, localUser, waiting),
            currentStreamingMessageId = waiting.id,
            isTrackedPendingAssistantMessageId = { it == waiting.id }
        )

        assertEquals(listOf(localUser.id, waiting.id), merged.map { it.id })
    }

    @Test
    fun canonicalHistoryRefreshKeepsEveryQueuedFutureTurnWhileCurrentTurnIsActive() {
        val activeUser = ChatMessage(
            id = "user-active-run",
            role = MessageRole.user,
            content = "active",
            runId = "local-user-active-run",
            sortTimestamp = 100.0,
            timelineOrderKey = "local:active-run|10|user-active-run",
            timelineIdentityKey = "local:message:user:active-run",
            timelineItemKind = "message:user"
        )
        val waiting = ChatMessage(
            id = "assistant-active-run",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = protocolTypingMarkerText,
            runId = "active-run",
            sortTimestamp = 100.001,
            timelineOrderKey = "local:active-run|20|assistant-active-run",
            timelineIdentityKey = "local:waiting:active-run",
            timelineItemKind = "waiting"
        )
        val queued = (1L..3L).map { position ->
            val runId = "queued-run-$position"
            ChatMessage(
                id = "user-$runId",
                role = MessageRole.user,
                content = "queued-$position",
                runId = "local-user-$runId",
                sortTimestamp = 100.0 + position,
                timelineOrderKey = "local:$runId|10|user-$runId",
                timelineIdentityKey = "local:message:user:$runId",
                timelineItemKind = "message:user",
                deliveryState = "queued",
                clientMessageText = "queued-$position",
                queuePosition = position
            )
        }

        val merged = mergeHistoryWithCurrentMessages(
            historyMessages = emptyList(),
            currentMessages = listOf(activeUser, waiting) + queued,
            currentStreamingMessageId = waiting.id,
            isTrackedPendingAssistantMessageId = { it == waiting.id }
        )

        assertEquals(
            listOf(activeUser.id, waiting.id) + queued.map { it.id },
            merged.map { it.id }
        )
        assertEquals(listOf(1L, 2L, 3L), merged.filter { it.deliveryState == "queued" }.map { it.queuePosition })
    }

    @Test
    fun canonicalHistoryRefreshUsesRelayAsOnlyCompletedSource() {
        val staleCompleted = canonicalMessage(
            id = "stale-assistant",
            role = MessageRole.assistant,
            content = "old answer",
            order = "0001|50|stale",
            identity = "main:message:stale-assistant",
            runId = "old-run"
        )
        val relayUser = canonicalMessage(
            id = "server-user",
            role = MessageRole.user,
            content = "relay question",
            order = "0002|10|server-user",
            identity = "main:message:server-user",
            runId = "relay-run"
        )
        val relayAssistant = canonicalMessage(
            id = "server-assistant",
            role = MessageRole.assistant,
            content = "relay answer",
            order = "0003|50|server-assistant",
            identity = "main:message:server-assistant",
            runId = "relay-run"
        )

        val merged = mergeHistoryWithCurrentMessages(
            historyMessages = listOf(relayAssistant, relayUser),
            currentMessages = listOf(staleCompleted),
            currentStreamingMessageId = null,
            isTrackedPendingAssistantMessageId = { false }
        )

        assertEquals(listOf("server-user", "server-assistant"), merged.map { it.id })
    }

    @Test
    fun loadOlderPrepareExitClearsOwnedLoadingAndAllowsNextPageAttempt() = runBlocking {
        var fetchCount = 0
        var responseReturned = false
        var prepareAttemptCount = 0
        lateinit var store: ChatStore
        val wsClient = RelayWebSocketClient()
        try {
            store = ChatStore(
                apiClient = RelayAPIClient(),
                wsClient = wsClient,
                notificationPort = object : NotificationPort {
                    override fun showReplyNotification(sessionKey: String, title: String, body: String) = Unit
                    override fun cancelNotification(id: Int) = Unit
                    override fun cancelAll() = Unit
                },
                chatHistoryPageFetcher = { _, _, _, _, _ ->
                    fetchCount += 1
                    responseReturned = true
                    ChatHistoryResponse(
                        items = chatHistoryItems(1..1).map { item ->
                            item.copy(
                                timelineOrderKey = "main:0001",
                                timelineIdentityKey = "main:assistant:history-1",
                                timelineItemKind = "message:assistant"
                            )
                        },
                        hasMore = true,
                        nextCursor = "older:next"
                    )
                }
            )
            store.setStateForTest(
                ChatState(
                    currentGatewayId = "gw_1",
                    currentSessionKey = "main",
                    historyWindow = ChatHistoryWindowState(
                        hasOlder = true,
                        olderCursor = "older:start"
                    )
                )
            )
            store.historyPrepareAttemptHookForTest = {
                prepareAttemptCount += 1
                if (prepareAttemptCount == 1) {
                    assertTrue("prepare 必须发生在 HTTP response 返回之后", responseReturned)
                    // 精确模拟 response 返回后 websocket mutation 抢先提交，随后
                    // scope generation 变化使 awaitHistoryPrepareReady 返回 false。
                    store.noteCanonicalTimelineMutation()
                    store.advanceTimelineScopeGeneration()
                }
            }

            store.loadOlderHistory("gw_1", "main")

            assertEquals(1, fetchCount)
            assertFalse(store.state.value.historyWindow.isLoadingOlder)
            assertEquals("older:start", store.state.value.historyWindow.olderCursor)

            // 第一次请求的 finally 已释放 owner；同一游标可以立即再次翻页，
            // 不会被遗留的 isLoadingOlder=true 永久冻结。
            store.historyPrepareAttemptHookForTest = null
            responseReturned = false
            store.loadOlderHistory("gw_1", "main")

            assertEquals(2, fetchCount)
            assertFalse(store.state.value.historyWindow.isLoadingOlder)
            assertEquals("older:next", store.state.value.historyWindow.olderCursor)
            assertEquals(listOf("history-1"), store.state.value.messages.map { it.id })
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
    fun historyResponseThatFinishesAfterSessionSwitchCannotPoisonTimelineReducerState() = runBlocking {
        val requestStarted = CompletableDeferred<Unit>()
        val releaseResponse = CompletableDeferred<Unit>()
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
                    requestStarted.complete(Unit)
                    releaseResponse.await()
                    ChatHistoryResponse(
                        items = emptyList(),
                        timelineSnapshot = Json.parseToJsonElement(
                            """
                            {
                              "timelineProtocolVersion": 3,
                              "sessionKey": "session-a",
                              "snapshotRevision": "7",
                              "messages": [
                                {
                                  "messageId": "assistant-session-a",
                                  "seq": 7,
                                  "role": "assistant",
                                  "messageState": "completed",
                                  "timelineOrderKey": "session-a:0007",
                                  "timelineIdentityKey": "session-a:assistant:7",
                                  "timelineItemKind": "message:assistant",
                                  "content": [{"type":"text","text":"stale"}]
                                }
                              ]
                            }
                            """.trimIndent()
                        )
                    )
                }
            )
            store.setStateForTest(
                ChatState(
                    currentGatewayId = "gw_1",
                    currentSessionKey = "session-a",
                    sessions = listOf(
                        ChatSessionItem("session-a", null),
                        ChatSessionItem("session-b", null)
                    )
                )
            )

            val historyJob = async { store.loadHistory("gw_1", "session-a", limit = 100) }
            requestStarted.await()
            store.selectSession("session-b")
            releaseResponse.complete(Unit)
            historyJob.await()

            assertEquals("session-b", store.state.value.currentSessionKey)
            assertTrue(store.state.value.messages.isEmpty())
            assertTrue(store.timelineState.messages.isEmpty())
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun prepareConflictWaitsForStreamingSignalAndReusesReturnedResponse() = runBlocking {
        val prepareConflictObserved = CompletableDeferred<Unit>()
        var fetchCount = 0
        var prepareAttemptCount = 0
        lateinit var store: ChatStore
        val wsClient = RelayWebSocketClient()
        try {
            store = ChatStore(
                apiClient = RelayAPIClient(),
                wsClient = wsClient,
                notificationPort = object : NotificationPort {
                    override fun showReplyNotification(sessionKey: String, title: String, body: String) = Unit
                    override fun cancelNotification(id: Int) = Unit
                    override fun cancelAll() = Unit
                },
                chatHistoryPageFetcher = { _, _, _, _, _ ->
                    fetchCount += 1
                    ChatHistoryResponse(
                        items = chatHistoryItems(1..1).map { item ->
                            item.copy(
                                timelineOrderKey = "main:0001",
                                timelineIdentityKey = "main:assistant:history-1",
                                timelineItemKind = "message:assistant"
                            )
                        }
                    )
                }
            )
            store.historyPrepareAttemptHookForTest = {
                prepareAttemptCount += 1
                if (prepareAttemptCount == 1) {
                    store.setStateForTest(store.state.value.copy(isStreaming = true))
                    store.noteCanonicalTimelineMutation()
                    prepareConflictObserved.complete(Unit)
                }
            }
            store.setStateForTest(ChatState(currentGatewayId = "gw_1", currentSessionKey = "main"))

            val historyJob = async { store.loadHistory("gw_1", "main", limit = 100) }
            withTimeout(3_000L) { prepareConflictObserved.await() }

            // response 已经在内存中；streaming 未结束前不重算、不重复 HTTP。
            assertEquals(1, fetchCount)
            assertTrue(store.state.value.isLoading)
            store.setStateForTest(store.state.value.copy(isStreaming = false))
            withTimeout(3_000L) { historyJob.await() }

            assertFalse(store.state.value.isLoading)
            assertFalse(store.state.value.isSwitchingSession)
            assertEquals(1, fetchCount)
            assertEquals(2, prepareAttemptCount)
            assertEquals(
                "state=${store.state.value}",
                listOf("history-1"),
                store.state.value.messages.map { it.id }
            )
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun helloDuringInFlightReconcileQueuesExactlyOneFollowUpWithoutTimer() = runBlocking {
        val firstFetchStarted = CompletableDeferred<Unit>()
        val releaseFirstFetch = CompletableDeferred<Unit>()
        val secondFetchStarted = CompletableDeferred<Unit>()
        var fetchCount = 0
        var prepareCount = 0
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
                    fetchCount += 1
                    if (fetchCount == 1) {
                        firstFetchStarted.complete(Unit)
                        releaseFirstFetch.await()
                    } else if (fetchCount == 2) {
                        secondFetchStarted.complete(Unit)
                    }
                    ChatHistoryResponse(
                        items = chatHistoryItems(1..1).map { item ->
                            item.copy(
                                timelineOrderKey = "main:0001",
                                timelineIdentityKey = "main:assistant:history-1",
                                timelineItemKind = "message:assistant"
                            )
                        }
                    )
                }
            )
            store.historyPrepareAttemptHookForTest = { prepareCount += 1 }
            store.setStateForTest(ChatState(currentGatewayId = "gw_1", currentSessionKey = "main"))

            store.requestCanonicalHistoryReconcileAfterHello()
            withTimeout(3_000L) { firstFetchStarted.await() }

            // 对账进行中收到多个真实 hello/ready，pending 状态只保留一次补跑。
            repeat(3) { store.requestCanonicalHistoryReconcileAfterHello() }
            releaseFirstFetch.complete(Unit)

            withTimeout(3_000L) { secondFetchStarted.await() }
            withTimeout(3_000L) {
                while (prepareCount < 2 || store.state.value.messages.map { it.id } != listOf("history-1")) {
                    yield()
                }
            }
            assertEquals(2, fetchCount)
            assertEquals(2, prepareCount)
            assertEquals(
                "prepareCount=$prepareCount state=${store.state.value}",
                listOf("history-1"),
                store.state.value.messages.map { it.id }
            )
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
	                                  "timelineOrderKey": "v1|00000000000000000001|10|000000|user-1",
	                                  "timelineIdentityKey": "message:user:user-1",
	                                  "timelineItemKind": "message:user",
	                                  "content": [{ "type": "text", "text": "hello" }]
                                },
                                {
                                  "turnId": "turn-1",
                                  "runId": "run-1",
                                  "messageId": "assistant-1",
                                  "role": "assistant",
                                  "messageState": "completed",
	                                  "createdAt": "2026-05-29T09:18:05.000Z",
	                                  "timelineOrderKey": "v1|00000000000000000001|50|000000|assistant-1",
	                                  "timelineIdentityKey": "message:assistant:assistant-1",
	                                  "timelineItemKind": "message:assistant",
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
    fun loadHistoryTimelineSnapshotDropsCompletedLocalEntriesMissingFromAuthoritativeSnapshot() = runBlocking {
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
                                  "turnId": "server-turn",
                                  "runId": "server-turn",
                                  "messageId": "server-user",
                                  "role": "user",
                                  "messageState": "completed",
                                  "timelineOrderKey": "v1|00000000000000000002|10|000000|server-user",
                                  "timelineIdentityKey": "message:user:server-user",
                                  "timelineItemKind": "message:user",
                                  "content": [{ "type": "text", "text": "kept" }]
                                }
                              ]
                            }
                            """.trimIndent()
                        )
                    )
                }
            )
            val staleUser = ChatMessage(
                id = "stale-local-user",
                role = MessageRole.user,
                state = MessageState.completed,
                content = "internal cached turn",
                runId = "local-user-stale-run",
                timelineOrderKey = "local:stale-run:10",
                timelineIdentityKey = "local:message:user:stale-run",
                timelineItemKind = "message:user"
            )
            val staleAssistant = ChatMessage(
                id = "stale-local-assistant",
                role = MessageRole.assistant,
                state = MessageState.completed,
                content = "internal cached reply",
                runId = "stale-run",
                timelineOrderKey = "local:stale-run:50",
                timelineIdentityKey = "local:message:assistant:stale-run",
                timelineItemKind = "message:assistant"
            )
            store.setStateForTest(
                ChatState(
                    messages = listOf(staleUser, staleAssistant),
                    currentGatewayId = "gw_1",
                    currentSessionKey = "main"
                )
            )
            store.setTimelineStateForTest(ChatTimelineState(messages = listOf(staleUser, staleAssistant)))

            store.loadHistory("gw_1", "main", limit = 50)

            assertEquals(listOf("server-user"), store.state.value.messages.map { it.id })
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun loadHistoryTimelineSnapshotPreservesOnlyExplicitlyActiveStreamingTurn() = runBlocking {
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
                        timelineSnapshot = Json.parseToJsonElement(
                            """
                            {
                              "protocolVersion": 2,
                              "eventType": "history.snapshot.page",
                              "gatewayId": "gw_1",
                              "sessionKey": "main",
                              "source": "history",
                              "messages": []
                            }
                            """.trimIndent()
                        )
                    )
                }
            )
            val localUser = ChatMessage(
                id = "local-user-active",
                role = MessageRole.user,
                state = MessageState.completed,
                content = "active prompt",
                runId = "local-user-active-run"
            )
            val activeAssistant = buildLocalTextAssistantPlaceholderMessage(
                id = "assistant-active-run",
                clientRunId = "active-run",
                sortTimestamp = 10.001
            )
            store.setStateForTest(
                ChatState(
                    messages = listOf(localUser, activeAssistant),
                    currentGatewayId = "gw_1",
                    currentSessionKey = "main",
                    isStreaming = true
                )
            )
            store.setTimelineStateForTest(ChatTimelineState(messages = listOf(localUser, activeAssistant)))
            store.setStreamingMessageIdForTest(activeAssistant.id)

            store.loadHistory("gw_1", "main", limit = 50)

            assertEquals(listOf(localUser.id, activeAssistant.id), store.state.value.messages.map { it.id })
            assertTrue(store.state.value.isStreaming)
        } finally {
            wsClient.destroy()
        }
    }
}
