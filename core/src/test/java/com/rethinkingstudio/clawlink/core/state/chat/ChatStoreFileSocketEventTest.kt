package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.domain.NotificationPort
import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.network.RelayAPIClient
import com.rethinkingstudio.clawlink.core.network.transport.RelayWebSocketClient
import com.rethinkingstudio.clawlink.core.network.transport.WsEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatStoreFileSocketEventTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun relayFileSocketEventWithTopLevelFieldsMaterializesFileBlock() {
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

            invokeHandleWsEvent(
                store,
                WsEvent(
                    type = "event",
                    event = "file",
                    payload = json.parseToJsonElement(
                        """
                        {
                          "state": "final",
                          "role": "assistant",
                          "sessionKey": "main",
                          "runId": "file-report-1",
                          "createdAt": "2026-06-22T08:30:00.000Z",
                          "fileId": "file-report-1",
                          "fileName": "report.pdf",
                          "mimeType": "application/pdf",
                          "sizeBytes": 4096,
                          "downloadUrl": "/api/mobile/files/file-report-1"
                        }
                        """.trimIndent()
                    )
                )
            )

            val message = store.state.value.messages.single()
            assertEquals(MessageRole.assistant, message.role)
            assertEquals(MessageState.completed, message.state)
            assertEquals("report.pdf", message.content)
            assertEquals("file-report-1", message.contentBlocks.single().fileId)
            assertEquals("/api/mobile/files/file-report-1", message.contentBlocks.single().downloadUrl)
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun relayFileSocketEventWithTypingMarkerTextUsesFileNameAsDisplayContent() {
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

            invokeHandleWsEvent(
                store,
                WsEvent(
                    type = "event",
                    event = "file",
                    payload = json.parseToJsonElement(
                        """
                        {
                          "state": "final",
                          "role": "assistant",
                          "text": "[[clawlink:typing]]",
                          "sessionKey": "main",
                          "runId": "file-report-typing-marker",
                          "createdAt": "2026-07-01T04:00:00.000Z",
                          "fileId": "file-report-typing-marker",
                          "fileName": "report.pdf",
                          "mimeType": "application/pdf",
                          "sizeBytes": 4096,
                          "downloadUrl": "/api/mobile/files/file-report-typing-marker"
                        }
                        """.trimIndent()
                    )
                )
            )

            val message = store.state.value.messages.single()
            assertEquals(MessageRole.assistant, message.role)
            assertEquals(MessageState.completed, message.state)
            assertEquals("report.pdf", message.content)
            assertEquals("file-report-typing-marker", message.contentBlocks.single().fileId)
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun relayFileSocketEventWithoutSourceRunAnchorsToCurrentPendingRun() {
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

            val localRunId = "client-run-spider"
            val userMessageId = "user-$localRunId"
            val assistantMessageId = "assistant-$localRunId"
            val userMessage = ChatMessage(
                id = userMessageId,
                role = MessageRole.user,
                state = MessageState.completed,
                content = "把桌面的蜘蛛侠图片发给我",
                runId = "local-user-$localRunId",
                sortTimestamp = 1_780_000_120.0
            )
            val assistantPlaceholder = buildLocalTextAssistantPlaceholderMessage(
                id = assistantMessageId,
                clientRunId = localRunId,
                sortTimestamp = 1_780_000_120.001
            )
            setChatState(
                store,
                store.state.value.copy(
                    messages = listOf(userMessage, assistantPlaceholder),
                    isStreaming = true
                )
            )
            rememberRunScope(
                store,
                localRunId,
                ChatRunScope(
                    gatewayId = "gateway-1",
                    sessionKey = "main",
                    assistantMessageId = assistantMessageId,
                    triggeringUserMessageId = userMessageId
                )
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
                          "role": "assistant",
                          "gatewayId": "gateway-1",
                          "sessionKey": "main",
                          "runId": "file-file-spiderman",
                          "createdAt": "2026-06-22T08:30:00.000Z",
                          "contentBlocks": [
                            {
                              "type": "file",
                              "fileId": "file-spiderman",
                              "fileName": "spiderman.png",
                              "mimeType": "image/png",
                              "downloadUrl": "/api/mobile/files/file-spiderman"
                            }
                          ]
                        }
                        """.trimIndent()
                    )
                )
            )

            val messages = store.state.value.messages
            assertEquals(
                listOf("local-user-$localRunId", "file-file-spiderman"),
                messages.map { it.runId }
            )
            val fileMessage = messages.last()
            assertTrue(
                "timelineOrderKey=${fileMessage.timelineOrderKey}",
                fileMessage.timelineOrderKey.startsWith("local:$localRunId|20|")
            )
            assertEquals(localRunId, fileMessage.fileContentBlocks.single().sourceRunId)
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun relayFileSocketEventsKeepDistinctCompletedImagesWithoutStableIdentity() {
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

            fun imageEvent(fileId: String, createdAt: String): WsEvent {
                return WsEvent(
                    type = "event",
                    event = "file",
                    payload = json.parseToJsonElement(
                        """
                        {
                          "state": "final",
                          "role": "assistant",
                          "sessionKey": "main",
                          "createdAt": "$createdAt",
                          "contentBlocks": [
                            {
                              "type": "file",
                              "text": "codex-shot-2026-06-22_23-35-07.png",
                              "name": "codex-shot-2026-06-22_23-35-07.png",
                              "fileId": "$fileId",
                              "fileName": "codex-shot-2026-06-22_23-35-07.png",
                              "mimeType": "image/png",
                              "sizeBytes": 6878188,
                              "imageWidth": 3024,
                              "imageHeight": 1964,
                              "downloadUrl": "/api/mobile/files/$fileId",
                              "gatewayId": "gw-file-without-stable-identity",
                              "sessionKey": "main",
                              "senderDisplayName": "OpenClaw"
                            }
                          ]
                        }
                        """.trimIndent()
                    )
                )
            }

            invokeHandleWsEvent(store, imageEvent("file_first", "2026-06-22T15:35:42.612Z"))
            invokeHandleWsEvent(store, imageEvent("file_second", "2026-06-22T15:35:42.634Z"))

            val messages = store.state.value.messages
            assertEquals(2, messages.size)
            assertEquals(listOf("file_first", "file_second"), messages.map { it.fileContentBlocks.single().fileId })
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun relayFileSocketEventsMergeCompletedImagesWithSameAttachmentId() {
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

            fun imageEvent(fileId: String, fileName: String, sizeBytes: Int): WsEvent {
                return WsEvent(
                    type = "event",
                    event = "file",
                    payload = json.parseToJsonElement(
                        """
                        {
                          "state": "final",
                          "role": "assistant",
                          "sessionKey": "main",
                          "runId": "run-image-reply",
                          "sourceRunId": "run-image-reply",
                          "createdAt": "2026-06-22T15:35:42.634Z",
                          "contentBlocks": [
                            {
                              "type": "file",
                              "attachmentId": "att_source_run_sha",
                              "fileId": "$fileId",
                              "fileName": "$fileName",
                              "mimeType": "image/png",
                              "sizeBytes": $sizeBytes,
                              "downloadUrl": "/api/mobile/files/$fileId",
                              "gatewayId": "gw-file-attachment-identity",
                              "sessionKey": "main",
                              "sourceRunId": "run-image-reply"
                            }
                          ]
                        }
                        """.trimIndent()
                    )
                )
            }

            invokeHandleWsEvent(store, imageEvent("file_first", "draft-name.png", 100))
            invokeHandleWsEvent(store, imageEvent("file_second", "final-name.png", 200))

            val messages = store.state.value.messages
            assertEquals(1, messages.size)
            assertEquals(MessageRole.assistant, messages.single().role)
            assertEquals("run-image-reply", messages.single().runId)
            assertTrue(messages.single().timelineOrderKey.startsWith("local:run-image-reply|30|"))
            assertEquals("att_source_run_sha", messages.single().fileContentBlocks.single().attachmentId)
            assertEquals("file_second", messages.single().fileContentBlocks.single().fileId)
            assertEquals("final-name.png", messages.single().fileContentBlocks.single().fileDisplayName)
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun legacyRelayFileSocketEventKeepsExistingAssistantTextAndAppendsSeparateAttachmentMessage() {
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

            val runId = "run-legacy-file-after-text"
            val userMessageId = "user-$runId"
            val assistantMessageId = "assistant-$runId"
            setChatState(
                store,
                store.state.value.copy(
                    messages = listOf(
                        ChatMessage(
                            id = userMessageId,
                            role = MessageRole.user,
                            state = MessageState.completed,
                            content = "截图电脑当前屏幕发过来",
                            runId = "local-user-$runId",
                            sortTimestamp = 1_780_000_100.0
                        ),
                        ChatMessage(
                            id = assistantMessageId,
                            role = MessageRole.assistant,
                            state = MessageState.streaming,
                            content = "截图好了，发给你：",
                            runId = runId,
                            sortTimestamp = 1_780_000_100.001
                        )
                    ),
                    currentGatewayId = "gateway-1",
                    currentSessionKey = "main",
                    isStreaming = true
                )
            )
            rememberRunScope(
                store,
                runId,
                ChatRunScope(
                    gatewayId = "gateway-1",
                    sessionKey = "main",
                    assistantMessageId = assistantMessageId,
                    triggeringUserMessageId = userMessageId
                )
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
                          "role": "assistant",
                          "gatewayId": "gateway-1",
                          "sessionKey": "main",
                          "runId": "file-file-legacy-screenshot",
                          "createdAt": "2026-07-01T03:40:00.000Z",
                          "contentBlocks": [
                            {
                              "type": "image",
                              "attachmentId": "att-legacy-screenshot",
                              "fileId": "file-legacy-screenshot",
                              "fileName": "desktop_screenshot.png",
                              "mimeType": "image/png",
                              "downloadUrl": "/api/mobile/files/file-legacy-screenshot"
                            }
                          ]
                        }
                        """.trimIndent()
                    )
                )
            )

            val messages = store.state.value.messages
            assertEquals(
                listOf(MessageRole.user, MessageRole.assistant, MessageRole.assistant),
                messages.map { it.role }
            )
            val assistantText = messages.single { it.id == assistantMessageId }
            assertEquals(MessageState.completed, assistantText.state)
            assertEquals("截图好了，发给你：", assistantText.content)
            assertTrue("contentBlocks=${assistantText.contentBlocks}", assistantText.transferContentBlocks().isEmpty())
            val fileMessage = messages.single { it.id != userMessageId && it.id != assistantMessageId }
            assertEquals("file-file-legacy-screenshot", fileMessage.runId)
            assertEquals("file-legacy-screenshot", fileMessage.fileContentBlocks.single().fileId)
            assertEquals(false, store.state.value.isStreaming)
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun legacyRelayFileSocketEventIgnoresTypingMarkerTextAndStillAppendsSeparateAttachmentMessage() {
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

            val runId = "run-legacy-file-typing-marker"
            val userMessageId = "user-$runId"
            val assistantMessageId = "assistant-$runId"
            setChatState(
                store,
                store.state.value.copy(
                    messages = listOf(
                        ChatMessage(
                            id = userMessageId,
                            role = MessageRole.user,
                            state = MessageState.completed,
                            content = "截图电脑当前屏幕发过来",
                            runId = "local-user-$runId",
                            sortTimestamp = 1_780_000_200.0
                        ),
                        ChatMessage(
                            id = assistantMessageId,
                            role = MessageRole.assistant,
                            state = MessageState.streaming,
                            content = "截图好了，发给你：",
                            runId = runId,
                            sortTimestamp = 1_780_000_200.001
                        )
                    ),
                    currentGatewayId = "gateway-1",
                    currentSessionKey = "main",
                    isStreaming = true
                )
            )
            rememberRunScope(
                store,
                runId,
                ChatRunScope(
                    gatewayId = "gateway-1",
                    sessionKey = "main",
                    assistantMessageId = assistantMessageId,
                    triggeringUserMessageId = userMessageId
                )
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
                          "role": "assistant",
                          "text": "[[clawlink:typing]]",
                          "gatewayId": "gateway-1",
                          "sessionKey": "main",
                          "runId": "file-file-typing-marker-screenshot",
                          "createdAt": "2026-07-01T03:45:00.000Z",
                          "contentBlocks": [
                            {
                              "type": "image",
                              "attachmentId": "att-typing-marker-screenshot",
                              "fileId": "file-typing-marker-screenshot",
                              "fileName": "desktop_screenshot.png",
                              "mimeType": "image/png",
                              "downloadUrl": "/api/mobile/files/file-typing-marker-screenshot"
                            }
                          ]
                        }
                        """.trimIndent()
                    )
                )
            )

            val messages = store.state.value.messages
            assertEquals(
                listOf(MessageRole.user, MessageRole.assistant, MessageRole.assistant),
                messages.map { it.role }
            )
            val assistantText = messages.single { it.id == assistantMessageId }
            assertEquals(MessageState.completed, assistantText.state)
            assertEquals("截图好了，发给你：", assistantText.content)
            assertTrue("contentBlocks=${assistantText.contentBlocks}", assistantText.transferContentBlocks().isEmpty())
            val fileMessage = messages.single { it.id != userMessageId && it.id != assistantMessageId }
            assertEquals("file-file-typing-marker-screenshot", fileMessage.runId)
            assertEquals("file-typing-marker-screenshot", fileMessage.fileContentBlocks.single().fileId)
            assertEquals(false, store.state.value.isStreaming)
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun relayFileSocketEventWithTimelineEventsMaterializesImageWithoutReplacingTextReply() {
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

            val runId = "run-screenshot"
            setChatState(
                store,
                store.state.value.copy(
                    messages = listOf(
                        ChatMessage(
                            id = "user-$runId",
                            role = MessageRole.user,
                            state = MessageState.completed,
                            content = "截图电脑当前屏幕发过来",
                            runId = "local-user-$runId",
                            sortTimestamp = 1_780_000_100.0
                        ),
                        ChatMessage(
                            id = "assistant-$runId",
                            role = MessageRole.assistant,
                            state = MessageState.streaming,
                            content = "截图好了，发给你：",
                            runId = runId,
                            sortTimestamp = 1_780_000_100.001
                        )
                    ),
                    isStreaming = true
                )
            )

            invokeHandleWsEvent(
                store,
                WsEvent(
                    type = "event",
                    event = "file",
                    payload = json.parseToJsonElement(
                        """
                        {
                          "gatewayId": "gateway-1",
                          "sessionKey": "main",
                          "timelineEvents": [
                            {
                              "protocolVersion": 2,
                              "eventId": "evt-file-message",
                              "eventType": "message.completed",
                              "gatewayId": "gateway-1",
                              "sessionKey": "main",
                              "turnId": "$runId",
                              "runId": "$runId",
                              "messageId": "file-file-screenshot",
                              "partId": "part-attachment-1",
                              "seq": 1780000100000,
                              "turnSeq": 1,
                              "role": "assistant",
                              "messageState": "completed",
                              "runState": "completed",
                              "createdAt": "2026-06-25T09:28:44.000Z",
                              "source": "relay-legacy",
                              "attachmentIds": ["att-screenshot", "file-screenshot"],
                              "content": [
                                {
                                  "type": "image",
                                  "attachmentId": "att-screenshot",
                                  "fileId": "file-screenshot",
                                  "fileName": "desktop_screenshot.png",
                                  "mimeType": "image/png",
                                  "downloadUrl": "/api/mobile/files/file-screenshot",
                                  "sourceRunId": "$runId"
                                }
                              ],
                              "timelineOrderKey": "local:$runId:030-attachment:file-file-screenshot",
                              "timelineIdentityKey": "local:$runId:attachment:att-screenshot",
                              "timelineItemKind": "attachment",
                              "timelineResolvesWaiting": false
                            },
                            {
                              "protocolVersion": 2,
                              "eventId": "evt-file-run",
                              "eventType": "run.completed",
                              "gatewayId": "gateway-1",
                              "sessionKey": "main",
                              "turnId": "$runId",
                              "runId": "$runId",
                              "messageId": "file-file-screenshot",
                              "partId": "run-state",
                              "seq": 1780000100001,
                              "turnSeq": 2,
                              "role": "assistant",
                              "messageState": "completed",
                              "runState": "completed",
                              "createdAt": "2026-06-25T09:28:44.000Z",
                              "source": "relay-legacy",
                              "content": []
                            }
                          ]
                        }
                        """.trimIndent()
                    )
                )
            )

            val messages = store.state.value.messages
            assertTrue(messages.any { it.id == "assistant-$runId" && it.content == "截图好了，发给你：" })
            val imageMessage = messages.single { it.id == "file-file-screenshot" }
            assertEquals(MessageRole.assistant, imageMessage.role)
            assertEquals(MessageState.completed, imageMessage.state)
            assertEquals("file-screenshot", imageMessage.fileContentBlocks.single().fileId)
            assertEquals("image", imageMessage.fileContentBlocks.single().type)
            assertEquals("att-screenshot", imageMessage.fileContentBlocks.single().attachmentId)
            assertEquals(false, store.state.value.isStreaming)
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun relayAssistantFileSocketEventDoesNotReplaceLocalUserUploadPlaceholderWhenAttachmentIdMatches() {
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
            store.newSession("main")

            val localUploadPlaceholder = ChatMessage(
                id = "local-upload-placeholder",
                role = MessageRole.user,
                state = MessageState.streaming,
                content = "shared.png",
                contentBlocks = listOf(
                    com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock(
                        type = "image",
                        attachmentId = "att-shared",
                        fileName = "shared.png",
                        mimeType = "image/png",
                        downloadUrl = "file:///tmp/shared.png",
                        sourceRunId = "turn-shared"
                    )
                ),
                runId = "upload-att-shared",
                sortTimestamp = 100.0
            )
            setChatState(
                store,
                store.state.value.copy(
                    currentSessionKey = "main",
                    messages = listOf(localUploadPlaceholder)
                )
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
                          "role": "assistant",
                          "sessionKey": "main",
                          "runId": "assistant-file-run",
                          "createdAt": "2026-07-01T03:30:00.000Z",
                          "contentBlocks": [
                            {
                              "type": "image",
                              "attachmentId": "att-shared",
                              "fileId": "file-shared",
                              "fileName": "shared.png",
                              "mimeType": "image/png",
                              "downloadUrl": "/api/mobile/files/file-shared",
                              "sourceRunId": "turn-shared"
                            }
                          ]
                        }
                        """.trimIndent()
                    )
                )
            )

            val messages = store.state.value.messages
            assertEquals(listOf(MessageRole.user, MessageRole.assistant), messages.map { it.role })
            assertEquals("local-upload-placeholder", messages.first().id)
            assertEquals("assistant-file-run", messages.last().runId)
            assertEquals("file-shared", messages.last().fileContentBlocks.single().fileId)
        } finally {
            wsClient.destroy()
        }
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

    private fun rememberRunScope(store: ChatStore, runId: String, scope: ChatRunScope) {
        store.rememberRunScope(runId, scope)
    }
}
