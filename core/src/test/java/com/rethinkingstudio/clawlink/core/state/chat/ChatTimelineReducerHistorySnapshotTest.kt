package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import com.rethinkingstudio.clawlink.core.network.dto.ChatHistoryResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTimelineReducerHistorySnapshotTest {
    @Test
    fun authoritativeHistoryReplacementKeepsCompletedLocalTurnOrderAsMetadataOnly() {
        fun localUser(id: String, runId: String, order: Long) = ChatMessage(
            id = id,
            role = MessageRole.user,
            state = MessageState.completed,
            content = id,
            runId = "local-user-$runId",
            turnId = runId,
            clientMessageId = runId,
            idempotencyKey = runId,
            timelineOrderKey = "local:$runId|10|$id",
            timelineIdentityKey = "local:message:user:$runId",
            timelineItemKind = "message:user",
            source = "local",
            localTurnOrder = order
        )
        val currentMessages = listOf(
            localUser("local-new", "run-new", 0),
            localUser("local-ping", "run-ping", 1)
        )
        val response = ChatHistoryResponse(
            items = emptyList(),
            timelineSnapshot = Json.parseToJsonElement(
                """
                {
                  "timelineProtocolVersion": 3,
                  "sessionKey": "mobile-hermes",
                  "snapshotRevision": "rev-local-order",
                  "messages": [
                    {
                      "messageId": "server-new-user",
                      "role": "user",
                      "messageState": "completed",
                      "turnId": "run-new",
                      "runId": "run-new",
                      "clientMessageId": "run-new",
                      "idempotencyKey": "run-new",
                      "timelineOrderKey": "v5|0|00000001786421720969|00000000000000000000|10|server-new-user",
                      "timelineIdentityKey": "v1|mobile-hermes|message|user|server-new-user",
                      "timelineItemKind": "message:user",
                      "source": "history",
                      "content": [{ "type": "text", "text": "/new" }]
                    },
                    {
                      "messageId": "server-new-answer",
                      "role": "assistant",
                      "messageState": "completed",
                      "turnId": "run-new",
                      "runId": "run-new",
                      "timelineOrderKey": "v5|0|00001786421722525000|00000000000000000000|50|server-new-answer",
                      "timelineIdentityKey": "v1|mobile-hermes|message|assistant|server-new-answer",
                      "timelineItemKind": "message:assistant",
                      "source": "history",
                      "content": [{ "type": "text", "text": "New session started!" }]
                    },
                    {
                      "messageId": "server-ping-user",
                      "role": "user",
                      "messageState": "completed",
                      "turnId": "run-ping",
                      "runId": "run-ping",
                      "clientMessageId": "run-ping",
                      "idempotencyKey": "run-ping",
                      "timelineOrderKey": "v5|0|00000000000000004355|00000000000000000000|10|server-ping-user",
                      "timelineIdentityKey": "v1|mobile-hermes|message|user|server-ping-user",
                      "timelineItemKind": "message:user",
                      "source": "history",
                      "content": [{ "type": "text", "text": "ping" }]
                    },
                    {
                      "messageId": "server-ping-answer",
                      "role": "assistant",
                      "messageState": "completed",
                      "turnId": "run-ping",
                      "runId": "run-ping",
                      "timelineOrderKey": "v5|0|00000000000000004356|00000000000000000000|50|server-ping-answer",
                      "timelineIdentityKey": "v1|mobile-hermes|message|assistant|server-ping-answer",
                      "timelineItemKind": "message:assistant",
                      "source": "history",
                      "content": [{ "type": "text", "text": "pong" }]
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        val reduction = requireNotNull(
            reduceTimelineHistorySnapshot(
                response = response,
                currentMessages = currentMessages,
                currentSessionKey = "mobile-hermes",
                timelineState = ChatTimelineState(messages = currentMessages),
                replaceExistingTimelineState = true
            )
        )
        val ordered = sortTimelineMessagesV3(reduction.messages, "mobile-hermes")

        assertEquals(
            listOf("server-new-user", "server-new-answer", "server-ping-user", "server-ping-answer"),
            ordered.map(ChatMessage::id)
        )
        assertEquals(
            listOf(0L, 1L),
            ordered.filter { it.role == MessageRole.user }.mapNotNull(ChatMessage::localTurnOrder)
        )
        assertTrue(reduction.messages.none { it.id.startsWith("local-") })
    }

    @Test
    fun historySnapshotReplayRemovesPersistedLocalUserDuplicateWhenCanonicalRowAlreadyExists() {
        val turnId = "android-client-run-relogin"
        val canonicalIdentity = "v1|main|message|user|server-user"
        val localDuplicate = ChatMessage(
            id = "local-server-user",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "企业的注册资本是多少",
            runId = turnId,
            timelineOrderKey = "v4|1|00000000000000000014|10|local-user",
            timelineIdentityKey = "v1|main|message|user|local-server-user",
            timelineItemKind = "message:user",
            source = "local"
        )
        val canonical = ChatMessage(
            id = "server-user",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "企业的注册资本是多少",
            runId = "$turnId:user",
            timelineOrderKey = "v4|0|00000000000000000014|10|server-user",
            timelineIdentityKey = canonicalIdentity,
            timelineItemKind = "message:user",
            source = "history"
        )

        val state = ChatTimelineReducer.reduce(
            ChatTimelineState(messages = listOf(localDuplicate, canonical)),
            event(
                """
                {
                  "protocolVersion": 2,
                  "eventId": "history-relogin-replay",
                  "eventType": "history.snapshot.page",
                  "messages": [
                    {
                      "turnId": "$turnId:user",
                      "runId": "$turnId:user",
                      "messageId": "server-user",
                      "role": "user",
                      "messageState": "completed",
                      "timelineOrderKey": "v4|0|00000000000000000014|10|server-user",
                      "timelineIdentityKey": "$canonicalIdentity",
                      "timelineItemKind": "message:user",
                      "source": "history",
                      "content": [{ "type": "text", "text": "企业的注册资本是多少" }]
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        assertEquals(listOf("local-server-user"), state.messages.map { it.id })
        assertEquals(listOf(canonicalIdentity), state.messages.map { it.timelineIdentityKey })
        assertEquals("$turnId:user", state.messages.single().runId)
    }

    @Test
    fun emptyAssistantMessageCompletedClearsOptimisticAssistantPlaceholder() {
        val localUser = ChatMessage(
            id = "local-user-new",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "new prompt",
            runId = "local-user-run-new",
            sortTimestamp = 200.0
        )
        val localAssistant = ChatMessage(
            id = "assistant-local",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = protocolTypingMarkerText,
            runId = "run-new",
            sortTimestamp = 200.001
        )

        val state = ChatTimelineReducer.reduce(
            ChatTimelineState(
                messages = listOf(localUser, localAssistant),
                activeRunId = "run-new",
                activeRunsByTurnId = mapOf("turn-new" to "run-new"),
                activeTurnByRunId = mapOf("run-new" to "turn-new")
            ),
            event(
                """
                {
                  "protocolVersion": 2,
                  "eventId": "final-empty",
                  "eventType": "message.completed",
                  "turnId": "turn-new",
                  "runId": "run-new",
                  "messageId": "assistant-server",
                  "role": "assistant",
                  "content": [{ "type": "text", "text": "" }],
                  "createdAt": "1970-01-01T00:03:20.001Z"
                }
                """.trimIndent()
            )
        )
        val ordered = orderTimelineMessages(state.messages)

        assertEquals(listOf("local-user-new", "assistant-server"), ordered.map { it.id })
        assertEquals("", ordered.last().content)
        assertEquals(MessageState.completed, ordered.last().state)
        assertFalse(hasActiveVisibleTimelineRun(state, ordered))
        assertFalse(state.hasActiveRun)
        assertTrue("final-empty" in state.seenEventIds)
    }

    @Test
    fun messageCompletedReplacesOnlyOptimisticAssistantPlaceholderWhenGatewayUsesRequestRunId() {
        val localUser = ChatMessage(
            id = "local-user-new",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "Reply QA908 only",
            runId = "local-user-relay-request-run",
            sortTimestamp = 220.0
        )
        val localAssistant = ChatMessage(
            id = "assistant-mobile-run",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = protocolTypingMarkerText,
            runId = "relay-request-run",
            sortTimestamp = 220.001
        )

        val state = ChatTimelineReducer.reduce(
            ChatTimelineState(
                messages = listOf(localUser, localAssistant),
                activeRunId = "relay-request-run",
                activeRunsByTurnId = mapOf("relay-request-run" to "relay-request-run"),
                activeTurnByRunId = mapOf("relay-request-run" to "relay-request-run")
            ),
            event(
                """
                {
                  "protocolVersion": 2,
                  "eventId": "final-request",
                  "eventType": "message.completed",
                  "turnId": "relay-request-run",
                  "runId": "relay-request-run",
                  "messageId": "assistant-relay-request-run",
                  "role": "assistant",
                  "content": [{ "type": "text", "text": "QA908" }],
                  "createdAt": "1970-01-01T00:03:40.001Z"
                }
                """.trimIndent()
            )
        )
        val ordered = orderTimelineMessages(state.messages)

        assertEquals(listOf("local-user-new", "assistant-relay-request-run"), ordered.map { it.id })
        assertEquals("QA908", ordered.last().content)
        assertEquals(MessageState.completed, ordered.last().state)
        assertEquals("relay-request-run", ordered.last().runId)
        assertEquals(220.001, ordered.last().sortTimestamp ?: -1.0, 0.0001)
        assertFalse(hasActiveVisibleTimelineRun(state, ordered))
        assertFalse(state.hasActiveRun)
    }

    @Test
    fun repeatedMessageCompletedReconcilesPreviouslyDuplicatedPlaceholder() {
        val localUser = ChatMessage(
            id = "local-user-new",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "new prompt",
            runId = "local-user-run-new",
            sortTimestamp = 200.0
        )
        val localAssistant = ChatMessage(
            id = "assistant-local",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "正在连接...",
            runId = "run-new",
            sortTimestamp = 200.001
        )
        val serverAssistant = ChatMessage(
            id = "assistant-server",
            role = MessageRole.assistant,
            state = MessageState.completed,
            content = "OK",
            runId = "run-new",
            sortTimestamp = 200.001,
            timelineOrderKey = "v1|00000000000000000001|50|000000|assistant-server",
            timelineIdentityKey = "message:assistant:assistant-server",
            timelineItemKind = "message:assistant"
        )
        val finalEvent = event(
            """
            {
              "protocolVersion": 2,
              "eventId": "final-server",
              "eventType": "message.completed",
              "turnId": "turn-new",
              "runId": "run-new",
              "messageId": "assistant-server",
              "role": "assistant",
              "content": [{ "type": "text", "text": "OK" }]
            }
            """.trimIndent()
        )

        val state = ChatTimelineReducer.reduce(
            ChatTimelineState(
                messages = listOf(localUser, localAssistant, serverAssistant),
                activeRunId = "run-new",
                activeRunsByTurnId = mapOf("turn-new" to "run-new"),
                activeTurnByRunId = mapOf("run-new" to "turn-new"),
                seenEventIds = setOf("final-server")
            ),
            finalEvent
        )
        val ordered = orderTimelineMessages(state.messages)

        assertEquals(listOf("local-user-new", "assistant-server"), ordered.map { it.id })
        assertEquals("OK", ordered.last().content)
        assertFalse(hasActiveVisibleTimelineRun(state, ordered))
        assertEquals(setOf("final-server"), state.seenEventIds)
    }

    @Test
    fun legacyFileReplayCannotDuplicateOrReorderCanonicalAttachments() {
        val runId = "run-five-images"
        val events = (1..5).flatMap { index ->
            val canonical = event(
                """
                {
                  "protocolVersion": 2,
                  "eventId": "canonical-attachment-$index",
                  "eventType": "message.completed",
                  "turnId": "$runId",
                  "runId": "$runId",
                  "messageId": "attachment-message-$index",
                  "role": "assistant",
                  "content": [{
                    "type": "image",
                    "attachmentId": "attachment-$index",
                    "fileId": "file-$index",
                    "fileName": "image-$index.jpg",
                    "mimeType": "image/jpeg"
                  }],
                  "timelineOrderKey": "v4|0|0000000000000000001$index|30|attachment-$index",
                  "timelineIdentityKey": "v1|main|attachment|assistant|attachment-$index",
                  "timelineItemKind": "attachment",
                  "timelineResolvesWaiting": false
                }
                """.trimIndent()
            )
            val legacyReplay = event(
                """
                {
                  "protocolVersion": 2,
                  "eventId": "legacy-attachment-$index",
                  "eventType": "message.completed",
                  "turnId": "$runId",
                  "runId": "$runId",
                  "messageId": "attachment-message-$index",
                  "role": "assistant",
                  "content": [{
                    "type": "image",
                    "attachmentId": "attachment-$index",
                    "fileId": "file-$index",
                    "fileName": "image-$index.jpg",
                    "mimeType": "image/jpeg"
                  }],
                  "timelineOrderKey": "local:$runId:030-attachment:file-$index",
                  "timelineIdentityKey": "local:$runId:attachment:attachment-$index",
                  "timelineItemKind": "attachment",
                  "timelineResolvesWaiting": false
                }
                """.trimIndent()
            )
            listOf(canonical, legacyReplay)
        }

        val state = ChatTimelineReducer.reduceAll(ChatTimelineState(), events)
        val ordered = orderTimelineMessages(state.messages)

        assertEquals(5, ordered.size)
        assertEquals((1..5).map { "attachment-message-$it" }, ordered.map { it.id })
        assertEquals(
            (1..5).map { "v1|main|attachment|assistant|attachment-$it" },
            ordered.map { it.timelineIdentityKey }
        )
        assertTrue(ordered.all { !it.timelineOrderKey.startsWith("local:") })
    }

    @Test
    fun staleActiveRunWithoutStreamingMessageIsNotAVisibleRun() {
        val state = ChatTimelineState(
            activeRunId = "stale-run",
            activeRunsByTurnId = mapOf("turn-1" to "stale-run"),
            activeTurnByRunId = mapOf("stale-run" to "turn-1")
        )
        val messages = listOf(
            ChatMessage(
                id = "assistant-1",
                role = MessageRole.assistant,
                state = MessageState.completed,
                content = "final"
            )
        )

        assertTrue(state.hasActiveRun)
        assertFalse(hasActiveVisibleTimelineRun(state, messages))
    }

    @Test
    fun historySnapshotReplacesOldWaitingPlaceholderWithoutStealingNewPlaceholder() {
        val oldUser = ChatMessage(
            id = "local-user-old",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "old prompt",
            runId = "local-user-turn-old",
            sortTimestamp = 200.0
        )
        val oldAssistantPlaceholder = ChatMessage(
            id = "assistant-old-local",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = protocolTypingMarkerText,
            runId = "run-old",
            sortTimestamp = 200.001
        )
        val newUser = ChatMessage(
            id = "user-new",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "new prompt",
            runId = "local-user-turn-new",
            sortTimestamp = 201.0
        )
        val newAssistantPlaceholder = ChatMessage(
            id = "assistant-new-local",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = protocolTypingMarkerText,
            runId = "run-new",
            sortTimestamp = 201.001
        )

        val state = ChatTimelineReducer.reduce(
            ChatTimelineState(
                messages = listOf(oldUser, oldAssistantPlaceholder, newUser, newAssistantPlaceholder),
                activeRunId = "run-new",
                activeRunsByTurnId = mapOf("turn-new" to "run-new"),
                activeTurnByRunId = mapOf("run-new" to "turn-new")
            ),
            event(
                """
                {
                  "protocolVersion": 2,
                  "eventId": "history-old",
                  "eventType": "history.snapshot.page",
                  "messages": [
                    {
                      "turnId": "turn-old",
                      "runId": "turn-old",
                      "messageId": "user-old",
                      "role": "user",
                      "messageState": "completed",
                      "timelineOrderKey": "v1|00000000000000000001|10|000000|user-old",
                      "timelineIdentityKey": "message:user:user-old",
                      "timelineItemKind": "message:user",
                      "content": [{ "type": "text", "text": "old prompt" }]
                    },
                    {
                      "turnId": "turn-old",
                      "runId": "run-old",
                      "messageId": "assistant-old-server",
                      "role": "assistant",
                      "messageState": "completed",
                      "timelineOrderKey": "v1|00000000000000000001|50|000000|assistant-old-server",
                      "timelineIdentityKey": "message:assistant:assistant-old-server",
                      "timelineItemKind": "message:assistant",
                      "content": [{ "type": "text", "text": "old reply" }]
	                    }
	                  ]
                }
                """.trimIndent()
            )
        )
        val ordered = orderTimelineMessages(state.messages)

        assertEquals(
            listOf("local-user-old", "assistant-old-server", "user-new", "assistant-new-local"),
            ordered.map { it.id }
        )
        assertEquals("user-old", ordered.first().timelineMessageId)
        assertEquals("old reply", ordered[1].content)
        assertEquals(200.001, ordered[1].sortTimestamp ?: -1.0, 0.0001)
        assertEquals(MessageState.streaming, ordered.last().state)
        assertEquals("run-new", state.activeRunId)
        assertTrue(hasActiveVisibleTimelineRun(state, ordered))
    }

    @Test
    fun historySnapshotReplacesLocalImageUserMessageForSameTurn() {
        val localImageBlock = RelayChatContentBlock(
            type = "file",
            text = "album-D1.jpeg",
            fileName = "album-D1.jpeg",
            mimeType = "image/jpeg",
            sizeBytes = 12_345,
            imageWidth = 1200,
            imageHeight = 900,
            downloadUrl = "file:///tmp/album-D1.jpeg"
        )
        val localUser = ChatMessage(
            id = "local-user-message",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "帮我分析一下这个图片",
            contentBlocks = listOf(localImageBlock),
            runId = "local-user-client-run-1",
            sortTimestamp = 200.0
        )

        val state = ChatTimelineReducer.reduce(
            ChatTimelineState(messages = listOf(localUser)),
            event(
                """
                {
                  "protocolVersion": 2,
                  "eventId": "history-user",
                  "eventType": "history.snapshot.page",
                  "messages": [
                    {
                      "turnId": "client-run-1",
                      "runId": "client-run-1",
                      "messageId": "server-user-message",
                      "role": "user",
                      "messageState": "completed",
                      "timelineOrderKey": "v1|00000000000000000001|10|000000|server-user-message",
                      "timelineIdentityKey": "message:user:server-user-message",
                      "createdAt": "1970-01-01T00:03:20.500Z",
                      "content": [
                        { "type": "text", "text": "帮我分析一下这个图片" },
                        {
                          "type": "file",
                          "text": "album-D1.jpeg",
                          "fileId": "file-photo-1",
                          "fileName": "album-D1.jpeg",
                          "mimeType": "image/jpeg",
                          "sizeBytes": 12345,
                          "imageWidth": 1200,
                          "imageHeight": 900,
                          "downloadUrl": "/api/mobile/files/file-photo-1"
                        }
                      ]
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        assertEquals(listOf("local-user-message"), state.messages.map { it.id })
        assertEquals("server-user-message", state.messages.single().timelineMessageId)
        assertEquals("local-user-client-run-1", state.messages.single().runId)
        assertEquals("file-photo-1", state.messages.single().fileContentBlocks.first().fileId)
        assertEquals("file:///tmp/album-D1.jpeg", state.messages.single().fileContentBlocks.first().downloadUrl)
    }

    @Test
    fun historySnapshotReplacesLocalPlainUserEchoWithRoleSuffixedRunIdentity() {
        val initial = ChatTimelineState(
            messages = listOf(
                ChatMessage(
                    id = "local-user-message",
                    role = MessageRole.user,
                    state = MessageState.completed,
                    content = "那你现在可以做什么",
                    runId = "local-user-client-run-1",
                    sortTimestamp = 200.0
                ),
                ChatMessage(
                    id = "assistant-local",
                    role = MessageRole.assistant,
                    state = MessageState.streaming,
                    content = protocolTypingMarkerText,
                    runId = "client-run-1",
                    sortTimestamp = 200.001
                )
            )
        )

        val state = ChatTimelineReducer.reduce(
            initial,
            event(
                """
                {
                  "protocolVersion": 2,
                  "eventId": "history-user-suffixed",
                  "eventType": "history.snapshot.page",
                  "messages": [
                    {
                      "turnId": "client-run-1:user",
                      "runId": "client-run-1:user",
                      "messageId": "server-user-message",
                      "role": "user",
                      "messageState": "completed",
                      "timelineOrderKey": "v1|00000000000000000001|10|000000|server-user-message",
                      "timelineIdentityKey": "message:user:server-user-message",
                      "timelineItemKind": "message:user",
                      "content": [{ "type": "text", "text": "那你现在可以做什么" }]
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        assertEquals(listOf("local-user-message", "assistant-local"), state.messages.map { it.id })
        assertEquals(1, state.messages.count { it.role == MessageRole.user })
        assertEquals("local-user-client-run-1", state.messages.first().runId)
        assertEquals("server-user-message", state.messages.first().timelineMessageId)
    }

    @Test
    fun historySnapshotReplacesPlainUserEchoWhenRunIdentityMatches() {
        val state = ChatTimelineReducer.reduceAll(
            ChatTimelineState(),
            listOf(
                TimelineEvent.TurnUserCreated(
                    eventId = "plain-user-local",
                    turnId = "turn-hermes-plain",
                    runId = null,
                    messageId = "local-user-plain",
                    source = "local",
                    content = listOf(
                        RelayChatContentBlock(
                            type = "text",
                            text = "帮我查看一下 codex 的任务完成了吗，结论是什么"
                        )
                    ),
                    createdAt = "2026-05-31T08:08:37.000Z",
                    timelineOrderKey = "v1|00000000000000000001|10|000000|local-user-plain",
                    timelineIdentityKey = "message:user:local-user-plain",
                    timelineItemKind = "message:user"
                ),
                TimelineEvent.MessageCompleted(
                    eventId = "plain-assistant-local",
                    turnId = "turn-hermes-plain",
                    runId = "run-hermes-plain",
                    messageId = "assistant-hermes-plain",
                    role = "assistant",
                    content = listOf(
                        RelayChatContentBlock(
                            type = "text",
                            text = "我看了 Codex 的任务日志和当前仓库状态，结论是："
                        )
                    ),
	                    createdAt = "2026-05-31T08:12:03.000Z",
                        timelineOrderKey = "v1|00000000000000000001|50|000000|assistant-hermes-plain",
                        timelineIdentityKey = "message:assistant:assistant-hermes-plain",
                        timelineItemKind = "message:assistant"
	                ),
                TimelineEvent.HistorySnapshotPage(
                    eventId = "plain-history-page",
                    items = listOf(
                        HistorySnapshotItem(
                            turnId = "",
                            runId = "turn-hermes-plain",
                            messageId = "history-hermes-plain-user",
                            role = "user",
                            content = listOf(
                                RelayChatContentBlock(
                                    type = "text",
                                    text = "帮我查看一下 codex 的任务完成了吗，结论是什么"
                                )
                            ),
                            createdAt = "2026-05-31T08:12:00.000Z",
	                            timelineOrderKey = "v1|00000000000000000001|10|000000|history-hermes-plain-user",
	                            timelineIdentityKey = "message:user:history-hermes-plain-user",
                                timelineItemKind = "message:user"
	                        ),
                        HistorySnapshotItem(
                            turnId = "",
                            runId = "run-hermes-plain",
                            messageId = "assistant-hermes-plain",
                            role = "assistant",
                            content = listOf(
                                RelayChatContentBlock(
                                    type = "text",
                                    text = "我看了 Codex 的任务日志和当前仓库状态，结论是："
                                )
                            ),
                            createdAt = "2026-05-31T08:12:03.000Z",
	                            timelineOrderKey = "v1|00000000000000000001|50|000000|assistant-hermes-plain",
	                            timelineIdentityKey = "message:assistant:assistant-hermes-plain",
                                timelineItemKind = "message:assistant"
	                        )
                    )
                )
            )
        )

        assertEquals(
            listOf("local-user-plain", "assistant-hermes-plain"),
            state.messages.map { it.id }
        )
        assertEquals("history-hermes-plain-user", state.messages.first().timelineMessageId)
        assertEquals(listOf(MessageRole.user, MessageRole.assistant), state.messages.map { it.role })
    }

    @Test
    fun historySnapshotReplacesPlainUserEchoWhenRunIdentityMatchesWithoutTextGuessing() {
        val localUser = ChatMessage(
            id = "local-user-screenshot",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "再发一个当前屏幕截图",
            runId = "local-user-client-screenshot-run",
            createdAt = "2026-06-07T12:26:00.000Z",
            sortTimestamp = 1_780_835_160.0
        )

        val state = ChatTimelineReducer.reduce(
            ChatTimelineState(messages = listOf(localUser)),
            TimelineEvent.HistorySnapshotPage(
                eventId = "nearby-runless-user-history",
                items = listOf(
                    HistorySnapshotItem(
                        turnId = "",
                        runId = "client-screenshot-run",
                        messageId = "history-user-screenshot",
                        role = "user",
                        content = listOf(RelayChatContentBlock(type = "text", text = "再发一个当前屏幕截图")),
                        createdAt = "2026-06-07T12:26:00.500Z",
                        timelineOrderKey = "v1|00000000000000000001|10|000000|history-user-screenshot",
                        timelineIdentityKey = "message:user:history-user-screenshot",
                        timelineItemKind = "message:user"
                    )
                )
            )
        )

        assertEquals(listOf("local-user-screenshot"), state.messages.map { it.id })
        assertEquals("history-user-screenshot", state.messages.single().timelineMessageId)
        assertEquals("local-user-client-screenshot-run", state.messages.single().runId)
    }

    @Test
    fun historySnapshotKeepsRepeatedPlainPromptInsideEchoWindowWhenRunsDiffer() {
        val state = ChatTimelineReducer.reduceAll(
            ChatTimelineState(),
            listOf(
                TimelineEvent.TurnUserCreated(
                    eventId = "first-user-local",
                    turnId = "turn-first",
                    runId = null,
                    messageId = "local-user-first",
                    source = "local",
                    content = listOf(RelayChatContentBlock(type = "text", text = "重新总结一下")),
                    createdAt = "2026-05-31T08:08:37.000Z",
                    timelineOrderKey = "v1|00000000000000000001|10|000000|local-user-first",
                    timelineIdentityKey = "message:user:local-user-first",
                    timelineItemKind = "message:user"
                ),
                TimelineEvent.MessageCompleted(
                    eventId = "first-assistant-local",
                    turnId = "turn-first",
                    runId = "run-first",
                    messageId = "assistant-first",
                    role = "assistant",
                    content = listOf(RelayChatContentBlock(type = "text", text = "第一次总结。")),
                    createdAt = "2026-05-31T08:08:40.000Z",
                    timelineOrderKey = "v1|00000000000000000001|50|000000|assistant-first",
                    timelineIdentityKey = "message:assistant:assistant-first",
                    timelineItemKind = "message:assistant"
                ),
                TimelineEvent.HistorySnapshotPage(
                    eventId = "second-history-page",
                    items = listOf(
                        HistorySnapshotItem(
                            turnId = "",
                            runId = "history-second-user",
                            messageId = "history-second-user",
                            role = "user",
                            content = listOf(RelayChatContentBlock(type = "text", text = "重新总结一下")),
                            createdAt = "2026-05-31T08:09:57.000Z",
                            timelineOrderKey = "v1|00000000000000000002|10|000000|history-second-user",
                            timelineIdentityKey = "message:user:history-second-user",
                            timelineItemKind = "message:user"
                        ),
                        HistorySnapshotItem(
                            turnId = "",
                            runId = "run-second",
                            messageId = "assistant-second",
                            role = "assistant",
                            content = listOf(RelayChatContentBlock(type = "text", text = "第二次总结。")),
                            createdAt = "2026-05-31T08:10:00.000Z",
                            timelineOrderKey = "v1|00000000000000000002|50|000000|assistant-second",
                            timelineIdentityKey = "message:assistant:assistant-second",
                            timelineItemKind = "message:assistant"
                        )
                    )
                )
            )
        )

        assertEquals(
            listOf("local-user-first", "assistant-first", "history-second-user", "assistant-second"),
            state.messages.map { it.id }
        )
        assertEquals(
            listOf(MessageRole.user, MessageRole.assistant, MessageRole.user, MessageRole.assistant),
            state.messages.map { it.role }
        )
    }

    @Test
    fun streamingToolMessageAloneDoesNotLockChatComposer() {
        val state = ChatTimelineState(
            activeRunId = "run-1",
            activeRunsByTurnId = mapOf("turn-1" to "run-1"),
            activeTurnByRunId = mapOf("run-1" to "turn-1")
        )
        val messages = listOf(
            ChatMessage(
                id = "tool-1",
                role = MessageRole.tool,
                state = MessageState.streaming,
                content = "Tool result"
            )
        )

        assertFalse(hasActiveVisibleTimelineRun(state, messages))
    }

    @Test
    fun messageCompletedFinalizesMessage() {
        val state = ChatTimelineReducer.reduceAll(
            ChatTimelineState(),
            listOf(
                event("""{"protocolVersion":2,"eventId":"d1","eventType":"message.part.delta","messageId":"assistant-1","turnId":"turn-1","role":"assistant","partId":"text","seq":1,"content":[{"type":"text","text":"draft"}]}"""),
                event("""{"protocolVersion":2,"eventId":"c1","eventType":"message.completed","messageId":"assistant-1","turnId":"turn-1","content":[{"type":"text","text":"final"}]}""")
            )
        )

        assertEquals("final", state.messages.single().content)
        assertEquals(MessageState.completed, state.messages.single().state)
        assertFalse(hasActiveVisibleTimelineRun(state, state.messages))
    }

    @Test
    fun messageCompletedClearsRunlessTurnActiveRun() {
        val streaming = ChatTimelineReducer.reduce(
            ChatTimelineState(),
            event("""{"protocolVersion":2,"eventId":"d1","eventType":"message.part.delta","messageId":"assistant-1","turnId":"turn-1","role":"assistant","partId":"text","seq":1,"content":[{"type":"text","text":"draft"}]}""")
        )
        assertTrue(hasActiveVisibleTimelineRun(streaming, streaming.messages))

        val completed = ChatTimelineReducer.reduce(
            streaming,
            event("""{"protocolVersion":2,"eventId":"c1","eventType":"message.completed","messageId":"assistant-1","turnId":"turn-1","content":[{"type":"text","text":"final"}]}""")
        )

        assertEquals(MessageState.completed, completed.messages.single().state)
        assertFalse(completed.hasActiveRun)
        assertFalse(hasActiveVisibleTimelineRun(completed, completed.messages))
    }

    @Test
    fun runCompletedFinalizesStreamingMessageWhenMessageCompletedIsMissing() {
        val streaming = ChatTimelineReducer.reduce(
            ChatTimelineState(),
            event("""{"protocolVersion":2,"eventId":"d1","eventType":"message.part.delta","messageId":"assistant-1","turnId":"turn-1","runId":"run-1","role":"assistant","partId":"text","seq":1,"content":[{"type":"text","text":"final text"}]}""")
        )
        assertTrue(hasActiveVisibleTimelineRun(streaming, streaming.messages))

        val completed = ChatTimelineReducer.reduce(
            streaming,
            event("""{"protocolVersion":2,"eventId":"r1","eventType":"run.completed","turnId":"turn-1","runId":"run-1"}""")
        )

        assertEquals("final text", completed.messages.single().content)
        assertEquals(MessageState.completed, completed.messages.single().state)
        assertFalse(completed.hasActiveRun)
        assertFalse(hasActiveVisibleTimelineRun(completed, completed.messages))
    }

    @Test
    fun runCompletedRemovesTransientStreamingPlaceholderAndAcceptsLateFinalContent() {
        val streaming = ChatTimelineReducer.reduce(
            ChatTimelineState(),
            event("""{"protocolVersion":2,"eventId":"d1","eventType":"message.part.delta","messageId":"assistant-1","turnId":"turn-1","runId":"run-1","role":"assistant","partId":"text","seq":1,"content":[{"type":"text","text":"[[clawlink:typing]]"}]}""")
        )

        val completed = ChatTimelineReducer.reduce(
            streaming,
            event("""{"protocolVersion":2,"eventId":"r1","eventType":"run.completed","turnId":"turn-1","runId":"run-1"}""")
        )

        assertTrue(completed.messages.isEmpty())
        assertFalse(completed.hasActiveRun)
        assertFalse(hasActiveVisibleTimelineRun(completed, completed.messages))

        val lateFinal = ChatTimelineReducer.reduce(
            completed,
            event("""{"protocolVersion":2,"eventId":"f1","eventType":"message.completed","messageId":"assistant-final","turnId":"turn-1","runId":"run-1","role":"assistant","content":[{"type":"text","text":"OK"}]}""")
        )
        assertEquals("OK", lateFinal.messages.single().content)
        assertEquals(MessageState.completed, lateFinal.messages.single().state)
        assertFalse(lateFinal.hasActiveRun)
    }

}
