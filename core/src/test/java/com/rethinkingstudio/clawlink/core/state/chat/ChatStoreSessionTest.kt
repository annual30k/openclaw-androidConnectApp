package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.domain.NotificationPort
import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.ComposerAttachmentDraft
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import com.rethinkingstudio.clawlink.core.models.gateway.GatewayType
import com.rethinkingstudio.clawlink.core.network.RelayAPIClient
import com.rethinkingstudio.clawlink.core.network.dto.RelayFileTransferItem
import com.rethinkingstudio.clawlink.core.network.transport.RelayWebSocketClient
import com.rethinkingstudio.clawlink.core.network.transport.WsConnectionState
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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
    fun preparedTimelineRehydrationKeepsMixedVersionQuestionBeforeAnswer() {
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
            val question = ChatMessage(
                id = "user-mixed-version-restore",
                role = MessageRole.user,
                state = MessageState.completed,
                content = "帮我分析一下这个图片",
                runId = "mixed-version-restore:user",
                timelineOrderKey = "v5|0|00000000000000000040|00000000000000000005|10|0000000000000005:00000000000000000005:user|eeee",
                timelineIdentityKey = "message:user:mixed-version-restore",
                timelineItemKind = "message:user"
            )
            val answer = ChatMessage(
                id = "assistant-mixed-version-restore",
                role = MessageRole.assistant,
                state = MessageState.completed,
                content = "这是一张自然风景照。",
                runId = "mixed-version-restore:assistant",
                timelineOrderKey = "v4|0|00000000000000000040|50|0000000000000006:00000000000000000006:assistant|ffff",
                timelineIdentityKey = "message:assistant:mixed-version-restore",
                timelineItemKind = "message:assistant"
            )

            val prepared = store.prepareTimelineRehydration(
                restored = ChatTimelineState(messages = listOf(answer, question)),
                sessionKey = "main"
            )

            assertEquals(listOf(question.id, answer.id), prepared.orderedMessages.map { it.id })
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

    @Test
    fun offlineFollowUpStaysQueuedUntilWebSocketReconnects() {
        val wsClient = RelayWebSocketClient()
        try {
            val store = ChatStore(
                apiClient = RelayAPIClient(),
                wsClient = wsClient,
                gatewayTypeFor = { GatewayType.hermes },
                notificationPort = object : NotificationPort {
                    override fun showReplyNotification(sessionKey: String, title: String, body: String) = Unit
                    override fun cancelNotification(id: Int) = Unit
                    override fun cancelAll() = Unit
                }
            )
            setChatState(
                store,
                ChatState(currentGatewayId = "gateway-1", currentSessionKey = "main")
            )

            store.sendTextOutgoingRun("active", "gateway-1", emptyList(), emptyList(), emptyList(), "run-active")
            store.sendTextOutgoingRun("111", "gateway-1", emptyList(), emptyList(), emptyList(), "run-offline-queued")
            setChatState(store, store.state.value.copy(isStreaming = false))
            setCurrentTimelineState(
                store,
                currentTimelineState(store).copy(
                    activeRunId = null,
                    activeRunsByTurnId = emptyMap(),
                    activeTurnByRunId = emptyMap()
                )
            )

            store.drainQueuedTimelineOutbox(WsConnectionState.disconnected)
            store.handleWebSocketConnectionState(WsConnectionState.reconnecting)
            // 回复元数据已经结束但旧队列尚未发送时，新消息也必须排在旧消息后面，不能插队。
            store.sendTextOutgoingRun(
                "newer",
                "gateway-1",
                emptyList(),
                emptyList(),
                emptyList(),
                "run-newer-queued"
            )

            assertTrue(store.timelineOutbox.getValue("run-offline-queued").queued)
            assertTrue(store.timelineOutbox.getValue("run-newer-queued").queued)
            assertEquals(
                listOf("run-offline-queued", "run-newer-queued"),
                store.timelineOutbox.values
                    .filter { it.queued }
                    .sortedBy { it.queuePosition }
                    .map { it.clientMessageId }
            )
            assertEquals(
                "queued",
                store.state.value.messages.single { it.id == "user-run-offline-queued" }.deliveryState
            )
            assertFalse(store.state.value.messages.any { it.id == "assistant-run-offline-queued" })
            assertFalse(store.state.value.isStreaming)

            store.handleWebSocketConnectionState(WsConnectionState.connected)

            assertFalse(store.timelineOutbox.getValue("run-offline-queued").queued)
            assertTrue(store.timelineOutbox.getValue("run-newer-queued").queued)
            assertEquals(
                "",
                store.state.value.messages.single { it.id == "user-run-offline-queued" }.deliveryState
            )
            assertTrue(store.state.value.messages.any { it.id == "assistant-run-offline-queued" })
            assertTrue(store.state.value.isStreaming)
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun configuredOfflineTransportQueuesFirstMessageInsteadOfStartingVolatileRun() {
        assertTrue(
            shouldQueueOutgoingTextRun(
                hasActiveReply = false,
                hasQueuedEntries = false,
                relayConfigured = true,
                connectionState = WsConnectionState.disconnected
            )
        )
        assertFalse(
            shouldQueueOutgoingTextRun(
                hasActiveReply = false,
                hasQueuedEntries = false,
                relayConfigured = true,
                connectionState = WsConnectionState.connected
            )
        )
        assertTrue(
            shouldQueueOutgoingTextRun(
                hasActiveReply = false,
                hasQueuedEntries = true,
                relayConfigured = true,
                connectionState = WsConnectionState.connected
            )
        )
    }

    @Test
    fun hermesFollowUpWaitsForPersistedHistoryFinalBeforeQueueActivation() {
        val wsClient = RelayWebSocketClient()
        try {
            val store = ChatStore(
                apiClient = RelayAPIClient(),
                wsClient = wsClient,
                gatewayTypeFor = { GatewayType.hermes },
                notificationPort = object : NotificationPort {
                    override fun showReplyNotification(sessionKey: String, title: String, body: String) = Unit
                    override fun cancelNotification(id: Int) = Unit
                    override fun cancelAll() = Unit
                }
            )
            setChatState(
                store,
                ChatState(currentGatewayId = "gateway-hermes", currentSessionKey = "mobile-session")
            )
            store.sendTextOutgoingRun(
                "A question",
                "gateway-hermes",
                emptyList(),
                emptyList(),
                emptyList(),
                "run-a"
            )
            store.sendTextOutgoingRun(
                "B follow-up",
                "gateway-hermes",
                emptyList(),
                emptyList(),
                emptyList(),
                "run-b"
            )

            invokeHandleChatPayload(
                store,
                """
                {
                  "state": "final",
                  "sessionKey": "mobile-session",
                  "runId": "run-a",
                  "timelineEvents": [
                    {
                      "protocolVersion": 2,
                      "eventId": "evt-live-final-a",
                      "eventType": "message.completed",
                      "turnId": "run-a",
                      "runId": "run-a",
                      "messageId": "assistant-run-a",
                      "role": "assistant",
                      "runState": "active",
                      "source": "live",
                      "content": [{ "type": "text", "text": "A answer" }],
                      "timelineOrderKey": "v5|1|00000000000000000001|00000000000000000000|50|assistant-run-a",
                      "timelineIdentityKey": "v1|mobile-session|message|assistant|assistant-run-a",
                      "timelineItemKind": "message:assistant"
                    },
                    {
                      "protocolVersion": 2,
                      "eventId": "evt-live-terminal-a",
                      "eventType": "run.completed",
                      "turnId": "run-a",
                      "runId": "run-a"
                    }
                  ]
                }
                """.trimIndent()
            )
            store.drainQueuedTimelineOutbox(WsConnectionState.connected)

            val liveAnswer = store.state.value.messages.single { it.id == "assistant-run-a" }
            assertEquals("A answer", liveAnswer.content)
            assertEquals("live", liveAnswer.source)
            assertEquals(MessageState.streaming, liveAnswer.state)
            assertTrue(store.state.value.isStreaming)
            assertTrue(store.timelineOutbox.getValue("run-b").queued)
            assertEquals("queued", store.state.value.messages.single { it.id == "user-run-b" }.deliveryState)

            invokeHandleChatPayload(
                store,
                """
                {
                  "state": "history_sync",
                  "sessionKey": "mobile-session",
                  "runId": "run-a",
                  "timelineEvents": [
                    {
                      "protocolVersion": 2,
                      "eventId": "evt-history-final-a",
                      "eventType": "message.completed",
                      "turnId": "run-a",
                      "runId": "run-a",
                      "messageId": "assistant-run-a",
                      "role": "assistant",
                      "runState": "active",
                      "source": "history",
                      "content": [{ "type": "text", "text": "A answer" }],
                      "timelineOrderKey": "v5|0|00000000000000000002|00000000000000000000|50|assistant-run-a",
                      "timelineIdentityKey": "v1|mobile-session|message|assistant|assistant-run-a",
                      "timelineItemKind": "message:assistant"
                    },
                    {
                      "protocolVersion": 2,
                      "eventId": "evt-history-terminal-a",
                      "eventType": "run.completed",
                      "turnId": "run-a",
                      "runId": "run-a"
                    }
                  ]
                }
                """.trimIndent()
            )
            store.drainQueuedTimelineOutbox(WsConnectionState.connected)

            val persistedAnswer = store.state.value.messages.single { it.id == "assistant-run-a" }
            assertEquals("history", persistedAnswer.source)
            assertEquals(MessageState.completed, persistedAnswer.state)
            assertFalse(store.timelineOutbox.getValue("run-b").queued)
            assertEquals(
                listOf("user-run-a", "assistant-run-a", "user-run-b", "assistant-run-b"),
                store.state.value.messages
                    .filter { it.role == MessageRole.user || it.role == MessageRole.assistant }
                    .map { it.id }
            )
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun followUpQueueSupportsReorderRemoveAndDrainsExactlyOnePerTerminalRun() {
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
            setChatState(
                store,
                ChatState(currentGatewayId = "gateway-1", currentSessionKey = "main")
            )

            store.sendTextOutgoingRun("first", "gateway-1", emptyList(), emptyList(), emptyList(), "run-1")
            store.sendTextOutgoingRun("second", "gateway-1", emptyList(), emptyList(), emptyList(), "run-2")
            store.sendTextOutgoingRun("third", "gateway-1", emptyList(), emptyList(), emptyList(), "run-3")
            store.sendTextOutgoingRun("fourth", "gateway-1", emptyList(), emptyList(), emptyList(), "run-4")

            assertEquals(listOf(false, true, true, true), store.timelineOutbox.values.map { it.queued })
            assertEquals(
                listOf("", "queued", "queued", "queued"),
                store.state.value.messages.filter { it.role == MessageRole.user }.map { it.deliveryState }
            )
            assertFalse(store.state.value.messages.single { it.id == "user-run-2" }.shouldDisplayInChat(false))

            store.moveQueuedMessage("user-run-3", -1)
            store.removeQueuedMessage("user-run-2")

            assertEquals(listOf("run-3", "run-4"), store.timelineOutbox.values
                .filter { it.queued }
                .sortedBy { it.queuePosition }
                .map { it.clientMessageId })
            assertEquals(listOf("third", "fourth"), store.state.value.messages
                .filter { it.deliveryState == "queued" }
                .sortedBy { it.queuePosition }
                .map { it.clientMessageText })
            assertFalse(store.state.value.messages.any { it.id == "user-run-2" })

            setChatState(store, store.state.value.copy(isStreaming = false))
            setCurrentTimelineState(
                store,
                currentTimelineState(store).copy(
                    activeRunId = null,
                    activeRunsByTurnId = emptyMap(),
                    activeTurnByRunId = emptyMap()
                )
            )
            store.drainQueuedTimelineOutbox(WsConnectionState.connected)

            assertEquals(listOf(false, false, true), store.timelineOutbox.values.map { it.queued })
            assertTrue(store.state.value.isStreaming)
            assertEquals("", store.state.value.messages.single { it.id == "user-run-3" }.deliveryState)
            assertEquals("queued", store.state.value.messages.single { it.id == "user-run-4" }.deliveryState)
            assertEquals("user-run-3", store.state.value.messages.filter { it.role == MessageRole.user }.last().id)

            setChatState(store, store.state.value.copy(isStreaming = false))
            setCurrentTimelineState(
                store,
                currentTimelineState(store).copy(
                    activeRunId = null,
                    activeRunsByTurnId = emptyMap(),
                    activeTurnByRunId = emptyMap()
                )
            )
            store.drainQueuedTimelineOutbox(WsConnectionState.connected)

            assertEquals(listOf(false, false, false), store.timelineOutbox.values.map { it.queued })
            assertEquals("", store.state.value.messages.single { it.id == "user-run-4" }.deliveryState)
            assertEquals("user-run-4", store.state.value.messages.filter { it.role == MessageRole.user }.last().id)
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun followUpQueueKeepsRepeatedTextAsDistinctStableTurns() {
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
            setChatState(
                store,
                ChatState(currentGatewayId = "gateway-1", currentSessionKey = "main")
            )

            store.sendTextOutgoingRun("hold", "gateway-1", emptyList(), emptyList(), emptyList(), "run-hold")
            store.sendTextOutgoingRun("same follow-up", "gateway-1", emptyList(), emptyList(), emptyList(), "run-same-1")
            store.sendTextOutgoingRun("same follow-up", "gateway-1", emptyList(), emptyList(), emptyList(), "run-same-2")

            val queuedEntries = store.timelineOutbox.values
                .filter { it.queued }
                .sortedBy { it.queuePosition }
            assertEquals(listOf("same follow-up", "same follow-up"), queuedEntries.map { it.content })
            assertEquals(listOf("run-same-1", "run-same-2"), queuedEntries.map { it.idempotencyKey })
            assertEquals(2, queuedEntries.map { it.idempotencyKey }.distinct().size)
            assertEquals(
                listOf("user-run-same-1", "user-run-same-2"),
                store.state.value.messages
                    .filter { it.deliveryState == "queued" }
                    .sortedBy { it.queuePosition }
                    .map { it.id }
            )

            setChatState(store, store.state.value.copy(isStreaming = false))
            setCurrentTimelineState(
                store,
                currentTimelineState(store).copy(
                    activeRunId = null,
                    activeRunsByTurnId = emptyMap(),
                    activeTurnByRunId = emptyMap()
                )
            )
            store.drainQueuedTimelineOutbox(WsConnectionState.connected)
            assertEquals(false, store.timelineOutbox.getValue("run-same-1").queued)
            assertEquals(true, store.timelineOutbox.getValue("run-same-2").queued)

            setChatState(store, store.state.value.copy(isStreaming = false))
            setCurrentTimelineState(
                store,
                currentTimelineState(store).copy(
                    activeRunId = null,
                    activeRunsByTurnId = emptyMap(),
                    activeTurnByRunId = emptyMap()
                )
            )
            store.drainQueuedTimelineOutbox(WsConnectionState.connected)
            assertEquals(false, store.timelineOutbox.getValue("run-same-2").queued)
            assertEquals(
                listOf("same follow-up", "same follow-up"),
                store.state.value.messages
                    .filter { it.role == MessageRole.user && it.content == "same follow-up" }
                    .map { it.content }
            )
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun staleActiveRunMetadataDoesNotQueueOrBlockMessagesAfterVisibleReplyEnds() {
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
            setChatState(
                store,
                ChatState(currentGatewayId = "gateway-1", currentSessionKey = "main")
            )

            store.sendTextOutgoingRun("active", "gateway-1", emptyList(), emptyList(), emptyList(), "run-active")
            store.sendTextOutgoingRun("queued", "gateway-1", emptyList(), emptyList(), emptyList(), "run-queued")

            val completedMessages = store.state.value.messages.mapNotNull { message ->
                when {
                    message.id == "assistant-run-active" -> null
                    message.id == "user-run-active" -> message
                    else -> message
                }
            }
            // 模拟终态已结束可见回复，但旧协议映射尚未带齐 client runId，留下 stale active-run 元数据。
            setChatState(store, store.state.value.copy(messages = completedMessages, isStreaming = false))

            store.drainQueuedTimelineOutbox(WsConnectionState.connected)

            assertFalse(store.timelineOutbox.getValue("run-queued").queued)
            assertEquals("", store.state.value.messages.single { it.id == "user-run-queued" }.deliveryState)

            setChatState(
                store,
                store.state.value.copy(
                    messages = store.state.value.messages.filterNot { it.id == "assistant-run-queued" },
                    isStreaming = false
                )
            )
            store.sendTextOutgoingRun("direct", "gateway-1", emptyList(), emptyList(), emptyList(), "run-direct")

            assertFalse(store.timelineOutbox.getValue("run-direct").queued)
            assertEquals("", store.state.value.messages.single { it.id == "user-run-direct" }.deliveryState)
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun missingQueuedOverlayIsRecoveredFromDurableOutboxBeforeFifoDrain() {
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
            setChatState(
                store,
                ChatState(currentGatewayId = "gateway-1", currentSessionKey = "main")
            )

            store.sendTextOutgoingRun("active", "gateway-1", emptyList(), emptyList(), emptyList(), "run-active")
            store.sendTextOutgoingRun("queue-1", "gateway-1", emptyList(), emptyList(), emptyList(), "run-queue-1")
            store.sendTextOutgoingRun("queue-2", "gateway-1", emptyList(), emptyList(), emptyList(), "run-queue-2")
            store.sendTextOutgoingRun("queue-3", "gateway-1", emptyList(), emptyList(), emptyList(), "run-queue-3")

            // 模拟旧实现的历史刷新：第一条 UI overlay 被覆盖，但 durable outbox 仍完整。
            setChatState(
                store,
                store.state.value.copy(
                    messages = store.state.value.messages.filterNot { it.id == "user-run-queue-1" },
                    isStreaming = false
                )
            )
            setCurrentTimelineState(
                store,
                currentTimelineState(store).copy(
                    activeRunId = null,
                    activeRunsByTurnId = emptyMap(),
                    activeTurnByRunId = emptyMap()
                )
            )

            store.drainQueuedTimelineOutbox(WsConnectionState.connected)

            assertTrue(store.timelineOutbox.containsKey("run-queue-1"))
            assertFalse(store.timelineOutbox.getValue("run-queue-1").queued)
            assertTrue(store.timelineOutbox.getValue("run-queue-2").queued)
            assertTrue(store.timelineOutbox.getValue("run-queue-3").queued)
            assertEquals("", store.state.value.messages.single { it.id == "user-run-queue-1" }.deliveryState)
            assertEquals(
                listOf("queue-2", "queue-3"),
                store.state.value.messages
                    .filter { it.deliveryState == "queued" }
                    .sortedBy { it.queuePosition }
                    .map { it.clientMessageText }
            )
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun concurrentQueueDrainActivatesExactlyOneTurn() {
        val wsClient = RelayWebSocketClient()
        val executor = Executors.newFixedThreadPool(8)
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
            val queuedMessages = (1..8).map { index ->
                val runId = "concurrent-$index"
                store.timelineOutbox[runId] = TimelineOutboxEntry(
                    kind = TimelineOutboxKind.TEXT,
                    clientMessageId = runId,
                    idempotencyKey = runId,
                    requestId = runId,
                    content = "queued-$index",
                    createdAtEpochMs = index.toLong(),
                    queued = true,
                    queuePosition = index.toLong()
                )
                buildQueuedTimelineOutboxUserMessage(store.timelineOutbox.getValue(runId))
            }
            setChatState(
                store,
                ChatState(
                    currentGatewayId = "gateway-1",
                    currentSessionKey = "main",
                    messages = queuedMessages,
                    isStreaming = false
                )
            )

            val ready = CountDownLatch(8)
            val start = CountDownLatch(1)
            val finished = CountDownLatch(8)
            repeat(8) {
                executor.execute {
                    ready.countDown()
                    start.await()
                    store.drainQueuedTimelineOutbox(WsConnectionState.connected)
                    finished.countDown()
                }
            }
            assertTrue(ready.await(2, TimeUnit.SECONDS))
            start.countDown()
            assertTrue(finished.await(5, TimeUnit.SECONDS))

            assertEquals(1, store.timelineOutbox.values.count { !it.queued })
            assertEquals(7, store.timelineOutbox.values.count { it.queued })
            assertTrue(store.state.value.isStreaming)
        } finally {
            executor.shutdownNow()
            wsClient.destroy()
        }
    }

    @Test
    fun invalidQueuedHeadIsMarkedFailedAndDoesNotBlockNextTurn() {
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
            val invalidEntry = TimelineOutboxEntry(
                kind = TimelineOutboxKind.TEXT,
                clientMessageId = "   ",
                idempotencyKey = "invalid-head",
                requestId = "invalid-head",
                content = "recover me",
                createdAtEpochMs = 1L,
                queued = true,
                queuePosition = 1L
            )
            val validEntry = TimelineOutboxEntry(
                kind = TimelineOutboxKind.TEXT,
                clientMessageId = "valid-next",
                idempotencyKey = "valid-next",
                requestId = "valid-next",
                content = "send next",
                createdAtEpochMs = 2L,
                queued = true,
                queuePosition = 2L
            )
            store.timelineOutbox[invalidEntry.idempotencyKey] = invalidEntry
            store.timelineOutbox[validEntry.idempotencyKey] = validEntry
            setChatState(
                store,
                ChatState(
                    currentGatewayId = "gateway-1",
                    currentSessionKey = "main",
                    messages = listOf(buildQueuedTimelineOutboxUserMessage(validEntry)),
                    isStreaming = false
                )
            )

            store.drainQueuedTimelineOutbox(WsConnectionState.connected)

            assertFalse(store.timelineOutbox.containsKey(invalidEntry.idempotencyKey))
            assertFalse(store.timelineOutbox.getValue(validEntry.idempotencyKey).queued)
            assertTrue(store.state.value.messages.any { message ->
                message.deliveryState == "failed" && message.content == "recover me"
            })
            assertEquals("", store.state.value.messages.single { it.id == "user-valid-next" }.deliveryState)
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
