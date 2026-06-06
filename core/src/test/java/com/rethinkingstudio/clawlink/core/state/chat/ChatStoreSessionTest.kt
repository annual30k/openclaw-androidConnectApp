package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.domain.NotificationPort
import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.ComposerAttachmentDraft
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import com.rethinkingstudio.clawlink.core.network.RelayAPIClient
import com.rethinkingstudio.clawlink.core.network.transport.RelayWebSocketClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatStoreSessionTest {
    private val json = Json { ignoreUnknownKeys = true }

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

    @Test
    fun orderedMessagesCoalescesDuplicateFileIdentitiesBeforeRendering() {
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
            val imageBlock = RelayChatContentBlock(
                type = "image",
                fileId = "file-img-1",
                fileName = "chatgpt image.png",
                mimeType = "image/png",
                sizeBytes = 2048,
                imageWidth = 1024,
                imageHeight = 1024,
                downloadUrl = "/api/mobile/files/file-img-1"
            )
            val assistantImage = ChatMessage(
                id = "assistant-run-1",
                role = MessageRole.assistant,
                state = MessageState.completed,
                content = "chatgpt image.png",
                contentBlocks = listOf(imageBlock),
                runId = "run-1",
                sortTimestamp = 100.0
            )
            val fileEcho = ChatMessage(
                id = "file-file-img-1",
                role = MessageRole.assistant,
                state = MessageState.completed,
                content = "chatgpt image.png",
                contentBlocks = listOf(imageBlock),
                runId = "file-file-img-1",
                sortTimestamp = 101.0
            )

            val ordered = invokeOrderedMessages(store, listOf(assistantImage, fileEcho))

            assertEquals(1, ordered.size)
            assertEquals("assistant-run-1", ordered.single().id)
            assertEquals("file-img-1", ordered.single().fileContentBlocks.single().fileId)
            assertEquals(100.0, ordered.single().sortTimestamp ?: 0.0, 0.0001)
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

    private fun setChatState(store: ChatStore, state: ChatState) {
        val field = ChatStore::class.java.getDeclaredField("_state")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = field.get(store) as MutableStateFlow<ChatState>
        stateFlow.value = state
    }

    private fun invokeOrderedMessages(store: ChatStore, messages: List<ChatMessage>): List<ChatMessage> {
        val method = ChatStore::class.java.getDeclaredMethod("orderedMessages", List::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(store, messages) as List<ChatMessage>
    }

    private fun rememberRunScope(store: ChatStore, runId: String, scope: ChatRunScope) {
        val method = ChatStore::class.java.getDeclaredMethod(
            "rememberRunScope",
            String::class.java,
            ChatRunScope::class.java
        )
        method.isAccessible = true
        method.invoke(store, runId, scope)
    }
}
