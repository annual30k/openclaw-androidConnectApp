package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.domain.NotificationPort
import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.ComposerAttachmentDraft
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import com.rethinkingstudio.clawlink.core.network.RelayAPIClient
import com.rethinkingstudio.clawlink.core.network.dto.RelayFileTransferItem
import com.rethinkingstudio.clawlink.core.network.transport.RelayWebSocketClient
import com.rethinkingstudio.clawlink.core.network.transport.WsEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

class ChatStoreSessionTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun historyPageSizeBoundsInitialRefreshWork() {
        assertEquals(50, ChatStore.chatHistoryPageSize)
        assertTrue(ChatStore.chatHistoryWindowMaxMessages > ChatStore.chatHistoryPageSize)
    }

    @Test
    fun newSessionCanUseExplicitMobileDraftKey() {
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

            val createdSessionKey = store.newSession("mobile-7980942a-fbb6-4868-b674-74caa7a6b1f6")

            assertEquals("mobile-7980942a-fbb6-4868-b674-74caa7a6b1f6", createdSessionKey)
            assertEquals("mobile-7980942a-fbb6-4868-b674-74caa7a6b1f6", store.state.value.currentSessionKey)
            assertTrue(store.state.value.sessions.any { it.sessionKey == "mobile-7980942a-fbb6-4868-b674-74caa7a6b1f6" })
            assertEquals(emptyList<Any>(), store.state.value.messages)
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun malformedChatErrorDoesNotSurfaceGenericUnknownError() {
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

            val method = ChatStore::class.java.getDeclaredMethod("handleError", JsonElement::class.java)
            method.isAccessible = true
            method.invoke(store, null)

            assertNull(store.state.value.errorMessage)
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun canonicalAgentToolEventUsesTimelineIdentityInsteadOfLegacyDuplicateProjection() {
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
            val legacyPayload = json.parseToJsonElement(
                """
                {
                  "stream": "tool",
                  "sessionKey": "main",
                  "runId": "turn-canonical-1",
                  "data": {
                    "phase": "result",
                    "toolCallId": "call-canonical-1",
                    "name": "exec",
                    "result": "done"
                  }
                }
                """.trimIndent()
            )
            val payload = json.parseToJsonElement(
                """
                {
                  "sessionKey": "main",
                  "runId": "turn-canonical-1",
                  "timelineEvents": [{
                    "protocolVersion": 2,
                    "eventId": "event-tool-canonical-1",
                    "eventType": "tool.invocation.updated",
                    "sessionKey": "main",
                    "turnId": "turn-canonical-1",
                    "runId": "turn-canonical-1",
                    "messageId": "tool-call-canonical-1",
                    "toolInvocationId": "call-canonical-1",
                    "toolState": "success",
                    "role": "tool",
                    "content": [{"type":"tool_result","toolCallId":"call-canonical-1","text":"done"}],
                    "timelineOrderKey": "v4|1|00000000000000000001|30|tool",
                    "timelineIdentityKey": "v1|main|tool|call-canonical-1",
                    "timelineItemKind": "tool"
                  }]
                }
                """.trimIndent()
            )

            invokeHandleWsEvent(store, WsEvent(type = "event", event = "agent", payload = legacyPayload))
            invokeHandleWsEvent(store, WsEvent(type = "event", event = "agent", payload = payload))

            val tools = store.state.value.messages.filter { it.role == MessageRole.tool }
            assertEquals(1, tools.size)
            assertEquals("v1|main|tool|call-canonical-1", tools.single().timelineIdentityKey)
            assertEquals("tool-call-canonical-1", tools.single().id)
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun repeatedLegacyAssistantFinalUsesStableRunMessageIdentity() {
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
            val payload = json.parseToJsonElement(
                """
                {
                  "state": "final",
                  "role": "assistant",
                  "sessionKey": "main",
                  "runId": "mobile-run-stable-final",
                  "text": "stable reply"
                }
                """.trimIndent()
            )

            repeat(2) {
                invokeHandleWsEvent(store, WsEvent(type = "event", event = "chat", payload = payload))
            }

            val messages = store.state.value.messages
            assertEquals(1, messages.count { it.role == MessageRole.assistant })
            assertEquals("assistant-mobile-run-stable-final", messages.single().id)
            assertEquals("assistant-mobile-run-stable-final", messages.single().timelineMessageId)
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun realtimeUserEchoWithIdempotencyKeyMergesLocalImagePromptAfterPendingFinished() {
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
            val clientRunId = "client-run-android-hermes-late-idempotency"
            val prompt = "帮我分析一下"
            val localUser = ChatMessage(
                id = "local-user-image-prompt",
                role = MessageRole.user,
                state = MessageState.completed,
                content = prompt,
                contentBlocks = listOf(
                    RelayChatContentBlock(
                        type = "image",
                        fileId = "local-dinner",
                        fileName = "dinner.png",
                        mimeType = "image/png",
                        downloadUrl = "file:///tmp/dinner.png",
                        sourceRunId = clientRunId
                    )
                ),
                runId = "local-user-$clientRunId",
                sortTimestamp = 100.0
            )
            val completedAssistant = ChatMessage(
                id = "assistant-completed",
                role = MessageRole.assistant,
                state = MessageState.completed,
                content = "已经完成",
                runId = clientRunId,
                sortTimestamp = 100.001
            )
            setChatState(
                store,
                store.state.value.copy(
                    messages = listOf(localUser, completedAssistant),
                    currentSessionKey = "main",
                    isStreaming = false
                )
            )

            invokeHandleWsEvent(
                store,
                WsEvent(
                    type = "event",
                    event = "chat",
                    payload = json.parseToJsonElement(
                        """
                        {
                          "state": "final",
                          "role": "user",
                          "sessionKey": "main",
                          "idempotencyKey": "$clientRunId",
                          "text": "$prompt",
                          "ts": 1779383003000
                        }
                        """.trimIndent()
                    )
                )
            )

            val messages = store.state.value.messages
            assertEquals(listOf(MessageRole.user, MessageRole.assistant), messages.map { it.role })
            assertEquals(1, messages.count { it.role == MessageRole.user })
            assertEquals("local-user-$clientRunId", messages.first().runId)
            assertEquals(listOf("local-dinner"), messages.first().contentBlocks.mapNotNull { it.fileId })
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun realtimeUserEchoPrefersSourceRunIdOverLegacyRunIdForLocalImagePrompt() {
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
            val clientRunId = "client-run-android-hermes-top-level-source"
            val prompt = "帮我分析一下这张图"
            val localUser = ChatMessage(
                id = "local-user-image-prompt",
                role = MessageRole.user,
                state = MessageState.completed,
                content = prompt,
                contentBlocks = listOf(
                    RelayChatContentBlock(
                        type = "image",
                        fileId = "local-screenshot",
                        fileName = "screenshot.png",
                        mimeType = "image/png",
                        downloadUrl = "file:///tmp/screenshot.png",
                        sourceRunId = clientRunId
                    )
                ),
                runId = "local-user-$clientRunId",
                sortTimestamp = 100.0
            )
            val completedAssistant = ChatMessage(
                id = "assistant-completed",
                role = MessageRole.assistant,
                state = MessageState.completed,
                content = "你好，我在。",
                runId = clientRunId,
                sortTimestamp = 100.001
            )
            setChatState(
                store,
                store.state.value.copy(
                    messages = listOf(localUser, completedAssistant),
                    currentSessionKey = "main",
                    isStreaming = false
                )
            )

            invokeHandleWsEvent(
                store,
                WsEvent(
                    type = "event",
                    event = "chat",
                    payload = json.parseToJsonElement(
                        """
                        {
                          "state": "final",
                          "role": "user",
                          "sessionKey": "main",
                          "runId": "legacy-hermes-user-echo",
                          "sourceRunId": "$clientRunId",
                          "text": "$prompt",
                          "ts": 1779383003000
                        }
                        """.trimIndent()
                    )
                )
            )

            val messages = store.state.value.messages
            assertEquals(listOf(MessageRole.user, MessageRole.assistant), messages.map { it.role })
            assertEquals(1, messages.count { it.role == MessageRole.user })
            assertEquals("local-user-$clientRunId", messages.first().runId)
            assertEquals(listOf("local-screenshot"), messages.first().contentBlocks.mapNotNull { it.fileId })
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun selectingSessionClearsCurrentTimelineRunState() {
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

            store.beginGatewaySwitch("gateway-1")
            store.newSession("session-a")
            setCurrentTimelineState(
                store,
                ChatTimelineState(
                    messages = listOf(
                        ChatMessage(
                            id = "assistant-run-a",
                            role = MessageRole.assistant,
                            state = MessageState.streaming,
                            content = "partial",
                            runId = "run-a"
                        )
                    ),
                    activeRunId = "run-a",
                    activeRunsByTurnId = mapOf("turn-a" to "run-a"),
                    activeTurnByRunId = mapOf("run-a" to "turn-a")
                )
            )

            store.selectSession("session-b")

            val timelineState = currentTimelineState(store)
            assertEquals(emptyList<Any>(), timelineState.messages)
            assertNull(timelineState.activeRunId)
            assertTrue(timelineState.activeRunsByTurnId.isEmpty())
            assertTrue(timelineState.activeTurnByRunId.isEmpty())
            assertEquals("session-b", store.state.value.currentSessionKey)
            assertEquals(emptyList<Any>(), store.state.value.messages)
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun releaseSessionSwitchOverlayOnlyClearsSwitchingFlag() {
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
            val message = ChatMessage(
                id = "message-1",
                role = MessageRole.user,
                state = MessageState.completed,
                content = "hello"
            )
            setChatState(
                store,
                store.state.value.copy(
                    currentGatewayId = "gateway-1",
                    currentSessionKey = "session-a",
                    messages = listOf(message),
                    isLoading = true,
                    isSwitchingSession = true
                )
            )

            store.releaseSessionSwitchOverlay()

            assertFalse(store.state.value.isSwitchingSession)
            assertTrue(store.state.value.isLoading)
            assertEquals("session-a", store.state.value.currentSessionKey)
            assertEquals(listOf(message), store.state.value.messages)
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun preparedTimelineRehydrationRestoresOrderedMessagesAndVisibleRunState() {
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
                id = "user-1",
                role = MessageRole.user,
                state = MessageState.completed,
                content = "发我截图",
                runId = "local-user-run-1",
                sortTimestamp = 200.0,
                timelineOrderKey = localTimelineOrderKey("turn-1", 10, "user-1"),
                timelineIdentityKey = localTimelineIdentityKey("message:user", "turn-1"),
                timelineItemKind = "message:user"
            )
            val waiting = buildLocalTextAssistantPlaceholderMessage(
                id = "assistant-local",
                clientRunId = "run-1",
                sortTimestamp = 200.001
            )
            setChatState(
                store,
                store.state.value.copy(
                    currentSessionKey = "main",
                    isStoppingRun = true
                )
            )

            val prepared = store.prepareTimelineRehydration(
                restored = ChatTimelineState(
                    messages = listOf(waiting, user),
                    activeRunId = "run-1",
                    activeRunsByTurnId = mapOf("turn-1" to "run-1"),
                    activeTurnByRunId = mapOf("run-1" to "turn-1")
                ),
                sessionKey = "main"
            )
            store.applyPreparedTimelineRehydration(prepared)

            assertEquals(listOf("user-1", "assistant-local"), store.state.value.messages.map { it.id })
            assertTrue(store.state.value.isStreaming)
            assertTrue(store.state.value.isStoppingRun)
            assertEquals(listOf("user-1", "assistant-local"), currentTimelineState(store).messages.map { it.id })
            assertEquals("run-1", currentTimelineState(store).activeRunId)
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun canonicalTimelineWithoutSessionKeyUsesTrackedCurrentRunScope() {
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

            store.beginGatewaySwitch("gateway-1")
            store.newSession("session-a")
            val localRunId = "local-run-session-a"
            val assistantMessageId = "assistant-local-session-a"
            setChatState(
                store,
                store.state.value.copy(
                    messages = listOf(
                        ChatMessage(
                            id = "user-session-a",
                            role = MessageRole.user,
                            state = MessageState.completed,
                            content = "hello",
                            runId = "local-user-$localRunId"
                        ),
                        ChatMessage(
                            id = assistantMessageId,
                            role = MessageRole.assistant,
                            state = MessageState.streaming,
                            content = protocolTypingMarkerText,
                            runId = localRunId
                        )
                    ),
                    isStreaming = true
                )
            )
            rememberRunScope(
                store,
                localRunId,
                ChatRunScope(
                    gatewayId = "gateway-1",
                    sessionKey = "session-a",
                    assistantMessageId = assistantMessageId,
                    triggeringUserMessageId = "user-session-a"
                )
            )

            invokeHandleChatPayload(
                store,
                """
                {
                  "timelineEvents": [
                    {
                      "protocolVersion": 2,
                      "eventId": "delta-session-a",
                      "eventType": "message.part.delta",
                      "turnId": "turn-session-a",
                      "runId": "$localRunId",
	                      "messageId": "assistant-session-a",
	                      "role": "assistant",
	                      "partId": "text",
	                      "seq": 1,
	                      "timelineOrderKey": "v1|00000000000000000001|50|000000|assistant-session-a",
	                      "timelineIdentityKey": "message:assistant:assistant-session-a",
	                      "timelineItemKind": "message:assistant",
	                      "content": [{ "type": "text", "text": "session A reply" }]
                    }
                  ]
                }
                """.trimIndent()
            )

            assertEquals("session-a", store.state.value.currentSessionKey)
            assertEquals(
                "session A reply",
                store.state.value.messages.last { it.role == MessageRole.assistant }.content
            )
            assertTrue(store.state.value.isStreaming)
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun sendSlashCommandAddsVisibleLocalTurnInCurrentSession() {
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

            store.beginGatewaySwitch("gateway-1")
            store.newSession("session-a")

            store.sendCommand(gatewayId = "gateway-1", command = "/status")

            val messages = store.state.value.messages
            assertEquals("session-a", store.state.value.currentSessionKey)
            assertEquals(listOf(MessageRole.user, MessageRole.assistant), messages.map { it.role })
            assertEquals("/status", messages.first().content)
            assertEquals(MessageState.completed, messages.first().state)
            assertEquals(MessageState.streaming, messages.last().state)
            assertTrue(messages.first().runId.startsWith("local-user-"))
            assertTrue(store.state.value.isStreaming)
            assertEquals(messages, currentTimelineState(store).messages)
        } finally {
            wsClient.destroy()
        }
    }

    @Ignore("Legacy attachment upload placeholder coalescing by local identity was removed from display ordering.")
    @Test
    fun sendMessageReplacesAttachmentUploadPlaceholderWithCombinedPrompt() {
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
            store.beginGatewaySwitch("gateway-1")
            store.newSession("main")
            val attachment = ComposerAttachmentDraft(
                id = "attachment-1",
                fileUri = "/tmp/photo.png",
                fileName = "photo.png",
                mimeType = "image/png",
                sizeBytes = 42,
                imageWidth = 320,
                imageHeight = 240
            )
            val imageBlock = RelayChatContentBlock(
                type = "image",
                fileId = "file-1",
                fileName = "photo.png",
                mimeType = "image/png",
                sizeBytes = 42,
                imageWidth = 320,
                imageHeight = 240,
                downloadUrl = "/tmp/photo.png",
                downloadPath = "/api/mobile/files/file-1"
            )

            store.beginComposerAttachmentUploadMessages(
                attachments = listOf(attachment),
                gatewayId = "gateway-1",
                sessionKey = "main",
                senderDisplayName = "Mac",
                messageSortBaseTimestamp = 100.0
            )
            store.sendMessage(
                content = "分析一下这张图",
                gatewayId = "gateway-1",
                attachmentIds = listOf(attachment.id),
                attachmentBlocks = listOf(imageBlock)
            )

            val userMessages = store.state.value.messages.filter { it.role == MessageRole.user }
            assertEquals(1, userMessages.size)
            assertEquals("分析一下这张图", userMessages.single().content)
            assertEquals(listOf(imageBlock), userMessages.single().contentBlocks)
            assertEquals(100.0, userMessages.single().sortTimestamp ?: 0.0, 0.0001)
            assertTrue(store.state.value.messages.none { it.id == attachment.id || it.runId == "upload-${attachment.id}" })
            assertEquals(1, store.state.value.messages.count { it.role == MessageRole.assistant && it.state == MessageState.streaming })
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun attachmentOnlyUploadSyncsTimelineSnapshotWithoutAssistantRun() {
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
            val attachment = ComposerAttachmentDraft(
                id = "attachment-only-1",
                fileUri = "/tmp/android-hidden-attachment-only.txt",
                fileName = "android-hidden-attachment-only.txt",
                mimeType = "text/plain",
                sizeBytes = 36
            )

            store.beginComposerAttachmentUploadMessages(
                attachments = listOf(attachment),
                gatewayId = "gateway-1",
                sessionKey = "main",
                senderDisplayName = "Mac",
                sourceRunId = "client-run-attachment-only",
                messageSortBaseTimestamp = 100.0
            )

            val placeholderTimeline = currentTimelineState(store)
            assertEquals(1, placeholderTimeline.messages.size)
            assertEquals("attachment", placeholderTimeline.messages.single().timelineItemKind)
            assertFalse(hasActiveVisibleTimelineRun(placeholderTimeline, placeholderTimeline.messages))
            assertTrue(shouldPersistTimelineSnapshot(placeholderTimeline, placeholderTimeline.messages))

            store.completeComposerAttachmentUploadMessage(
                attachment = attachment,
                record = RelayFileTransferItem(
                    fileId = "file-attachment-only-1",
                    gatewayId = "gateway-1",
                    sessionKey = "main",
                    fileName = "android-hidden-attachment-only.txt",
                    mimeType = "text/plain",
                    sizeBytes = 36,
                    sha256 = "sha",
                    origin = "mobile",
                    senderDisplayName = "Mac",
                    createdAt = "2026-07-01T01:05:51.000Z",
                    sortTimestampMs = 100000,
                    updatedAt = "2026-07-01T01:05:52.000Z",
                    expiresAt = "2026-07-07T17:05:51.000Z",
                    status = "completed",
                    storagePath = "/tmp/android-hidden-attachment-only.txt",
                    downloadPath = "/api/mobile/files/file-attachment-only-1",
                    chunkSize = 1,
                    totalChunks = 1,
                    sourceRunId = "client-run-attachment-only"
                ),
                gatewayId = "gateway-1",
                sessionKey = "main",
                sourceRunId = "client-run-attachment-only",
                completionSortTimestamp = 100.0
            )

            val completedTimeline = currentTimelineState(store)
            assertEquals(1, completedTimeline.messages.size)
            assertEquals("android-hidden-attachment-only.txt", completedTimeline.messages.single().content)
            assertTrue(completedTimeline.messages.single().timelineOrderKey.startsWith("local:client-run-attachment-only|30|"))
            assertFalse(hasActiveVisibleTimelineRun(completedTimeline, completedTimeline.messages))
            assertTrue(shouldPersistTimelineSnapshot(completedTimeline, completedTimeline.messages))
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun relayLegacyAttachmentOnlyEchoSyncsRestorableTimelineSnapshot() {
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

            store.appendOrMergeRemoteUserMessage(
                content = "android-hidden-attachment-only-fix.txt",
                contentBlocks = listOf(
                    RelayChatContentBlock(
                        type = "file",
                        attachmentId = "att-attachment-only-echo",
                        fileId = "file-attachment-only-echo",
                        fileName = "android-hidden-attachment-only-fix.txt",
                        mimeType = "text/plain",
                        downloadUrl = "/api/mobile/files/file-attachment-only-echo",
                        sourceRunId = "client-run-attachment-only-echo"
                    )
                ),
                runId = "client-run-attachment-only-echo",
                sortTimestamp = 100.0
            )

            val timeline = currentTimelineState(store)
            assertEquals(1, timeline.messages.size)
            val message = timeline.messages.single()
            assertTrue(message.timelineOrderKey.startsWith("local:client-run-attachment-only-echo|30|"))
            assertEquals(
                "local:attachment:att-attachment-only-echo",
                message.timelineIdentityKey
            )
            assertEquals("attachment", message.timelineItemKind)
            assertTrue(shouldPersistTimelineSnapshot(timeline, timeline.messages))
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun userFileEventWithoutSourceRunIdKeepsLocalAttachmentTimelineIdentityForLaterSnapshotSync() {
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
            store.beginGatewaySwitch("gateway-1")
            store.newSession("main")

            val attachment = ComposerAttachmentDraft(
                id = "attachment-only-no-source",
                fileUri = "/tmp/android-hidden-attachment-no-source.txt",
                fileName = "android-hidden-attachment-no-source.txt",
                mimeType = "text/plain",
                sizeBytes = 40
            )

            store.beginComposerAttachmentUploadMessages(
                attachments = listOf(attachment),
                gatewayId = "gateway-1",
                sessionKey = "main",
                senderDisplayName = "Mac",
                sourceRunId = "client-run-attachment-no-source",
                messageSortBaseTimestamp = 100.0
            )
            store.completeComposerAttachmentUploadMessage(
                attachment = attachment,
                record = RelayFileTransferItem(
                    fileId = "file-attachment-no-source-1",
                    gatewayId = "gateway-1",
                    sessionKey = "main",
                    fileName = "android-hidden-attachment-no-source.txt",
                    mimeType = "text/plain",
                    sizeBytes = 40,
                    sha256 = "sha",
                    origin = "mobile",
                    senderDisplayName = "Mac",
                    createdAt = "2026-07-01T01:47:28.300Z",
                    sortTimestampMs = 100000,
                    updatedAt = "2026-07-01T01:47:28.301Z",
                    expiresAt = "2026-07-07T17:47:28.000Z",
                    status = "completed",
                    storagePath = "/tmp/android-hidden-attachment-no-source.txt",
                    downloadPath = "/api/mobile/files/file-attachment-no-source-1",
                    chunkSize = 1,
                    totalChunks = 1,
                    sourceRunId = "client-run-attachment-no-source"
                ),
                gatewayId = "gateway-1",
                sessionKey = "main",
                sourceRunId = "client-run-attachment-no-source",
                completionSortTimestamp = 100.0
            )

            invokeHandleWsEvent(
                store,
                WsEvent(
                    type = "event",
                    event = "file",
                    payload = json.parseToJsonElement(
                        """
                        {
                          "state": "final",
                          "role": "user",
                          "gatewayId": "gateway-1",
                          "sessionKey": "main",
                          "runId": "file-file-attachment-no-source-1",
                          "createdAt": "2026-07-01T01:47:28.658Z",
                          "contentBlocks": [
                            {
                              "type": "file",
                              "attachmentId": "attachment-only-no-source",
                              "fileId": "file-attachment-no-source-1",
                              "fileName": "android-hidden-attachment-no-source.txt",
                              "mimeType": "text/plain",
                              "downloadUrl": "/api/mobile/files/file-attachment-no-source-1"
                            }
                          ]
                        }
                        """.trimIndent()
                    )
                )
            )

            val realtimeMessage = store.state.value.messages.single()
            assertTrue(realtimeMessage.timelineOrderKey.startsWith("local:client-run-attachment-no-source|30|"))
            assertEquals(
                "local:attachment:attachment-only-no-source",
                realtimeMessage.timelineIdentityKey
            )

            store.syncTimelineMessagesSnapshot(store.state.value.messages)

            val timeline = currentTimelineState(store)
            assertTrue(shouldPersistTimelineSnapshot(timeline, timeline.messages))
        } finally {
            wsClient.destroy()
        }
    }

    @Ignore("Legacy duplicate message-id coalescing before rendering was removed; canonical identity is authoritative.")
    @Test
    fun orderedMessagesCoalescesDuplicateMessageIdsBeforeRendering() {
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
            val localBlock = RelayChatContentBlock(
                type = "image",
                fileId = "file-1",
                fileName = "photo.png",
                mimeType = "image/png",
                downloadUrl = "/tmp/photo.png",
                downloadPath = "/api/mobile/files/file-1"
            )
            val remoteBlock = localBlock.copy(downloadUrl = "/api/mobile/files/file-1")
            val localMessage = ChatMessage(
                id = "user-duplicate",
                role = MessageRole.user,
                state = MessageState.completed,
                content = "20260606",
                contentBlocks = listOf(localBlock),
                runId = "local-user-duplicate",
                sortTimestamp = 100.0
            )
            val echoedMessage = localMessage.copy(
                contentBlocks = listOf(remoteBlock),
                createdAt = "2030-01-01T00:00:00.000Z",
                sortTimestamp = 101.0
            )

            val ordered = invokeOrderedMessages(store, listOf(localMessage, echoedMessage))

            assertEquals(1, ordered.size)
            assertEquals("user-duplicate", ordered.single().id)
            assertEquals("20260606", ordered.single().content)
            assertEquals(listOf(localBlock), ordered.single().contentBlocks)
            assertEquals(100.0, ordered.single().sortTimestamp ?: 0.0, 0.0001)
        } finally {
            wsClient.destroy()
        }
    }

    @Ignore("Legacy local/server user echo coalescing before rendering was removed; canonical identity is authoritative.")
    @Test
    fun orderedMessagesCoalescesLocalUserMessageWithServerEchoBeforeRendering() {
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
            val localUser = ChatMessage(
                id = "user-client-run",
                role = MessageRole.user,
                state = MessageState.completed,
                content = "你好",
                runId = "local-user-client-run",
                sortTimestamp = 100.0
            )
            val assistantPlaceholder = ChatMessage(
                id = "assistant-client-run",
                role = MessageRole.assistant,
                state = MessageState.streaming,
                content = protocolTypingMarkerText,
                runId = "client-run",
                sortTimestamp = 100.001
            )
            val serverEcho = ChatMessage(
                id = "user-client-run",
                role = MessageRole.user,
                state = MessageState.completed,
                content = "你好",
                runId = "client-run",
                createdAt = "2030-01-01T00:00:00.000Z",
                sortTimestamp = 100.002
            )

            val ordered = invokeOrderedMessages(store, listOf(localUser, assistantPlaceholder, serverEcho))

            assertEquals(1, ordered.count { it.role == MessageRole.user && it.content == "你好" })
            assertEquals("user-client-run", ordered.first { it.role == MessageRole.user }.id)
            assertEquals("local-user-client-run", ordered.first { it.role == MessageRole.user }.runId)
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun orderedMessagesKeepsSameLocalUserTextAcrossAssistantBoundary() {
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
            val firstLocalUser = ChatMessage(
                id = "user-client-run-1",
                role = MessageRole.user,
                state = MessageState.completed,
                content = "你好",
                runId = "local-user-client-run-1",
                sortTimestamp = 100.0
            )
            val secondLocalUser = ChatMessage(
                id = "user-client-run-2",
                role = MessageRole.user,
                state = MessageState.completed,
                content = "你好",
                runId = "local-user-client-run-2",
                sortTimestamp = 101.0
            )
            val assistantReply = ChatMessage(
                id = "assistant-client-run-1",
                role = MessageRole.assistant,
                state = MessageState.completed,
                content = "你好，有什么可以帮你？",
                runId = "client-run-1",
                sortTimestamp = 100.5
            )

            val ordered = invokeOrderedMessages(store, listOf(firstLocalUser, assistantReply, secondLocalUser))

            assertEquals(
                listOf("user-client-run-1", "user-client-run-2"),
                ordered.filter { it.role == MessageRole.user }.map { it.id }
            )
        } finally {
            wsClient.destroy()
        }
    }

    @Ignore("Legacy remote user echo matching without canonical identity was removed.")
    @Test
    fun orderedMessagesKeepsRemoteUserEchoWhenStableRunDoesNotMatchLocalUser() {
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
            val localUser = ChatMessage(
                id = "user-client-run",
                role = MessageRole.user,
                state = MessageState.completed,
                content = "你好",
                runId = "local-user-client-run",
                sortTimestamp = 100.0
            )
            val serverEcho = ChatMessage(
                id = "server-user-message",
                role = MessageRole.user,
                state = MessageState.completed,
                content = "你好",
                runId = "server-run",
                sortTimestamp = 100.1
            )

            val ordered = invokeOrderedMessages(store, listOf(localUser, serverEcho))

            assertEquals(listOf("user-client-run", "server-user-message"), ordered.map { it.id })
        } finally {
            wsClient.destroy()
        }
    }

    private fun currentTimelineState(store: ChatStore): ChatTimelineState {
        val field = ChatStore::class.java.getDeclaredField("timelineState")
        field.isAccessible = true
        return field.get(store) as ChatTimelineState
    }

    private fun setCurrentTimelineState(store: ChatStore, state: ChatTimelineState) {
        val field = ChatStore::class.java.getDeclaredField("timelineState")
        field.isAccessible = true
        field.set(store, state)
    }

    private fun invokeHandleChatPayload(store: ChatStore, rawPayload: String) {
        val method = ChatStore::class.java.getDeclaredMethod("handleChatPayload", JsonElement::class.java)
        method.isAccessible = true
        method.invoke(store, json.parseToJsonElement(rawPayload) as JsonObject)
    }

    private fun invokeHandleWsEvent(store: ChatStore, event: WsEvent) {
        val method = ChatStore::class.java.getDeclaredMethod("handleWsEvent", WsEvent::class.java)
        method.isAccessible = true
        method.invoke(store, event)
    }

    private fun setChatState(store: ChatStore, state: ChatState) {
        val field = ChatStore::class.java.getDeclaredField("_state")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = field.get(store) as MutableStateFlow<ChatState>
        stateFlow.value = state
    }

    private fun invokeOrderedMessages(store: ChatStore, messages: List<ChatMessage>): List<ChatMessage> {
        return store.orderedMessages(messages)
    }

    private fun rememberRunScope(store: ChatStore, runId: String, scope: ChatRunScope) {
        store.rememberRunScope(runId, scope)
    }
}
