package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTimelineReducerTest {
    @Test
    fun activeMessageCompletionKeepsRunLockedUntilTerminalEvent() {
        val initial = ChatTimelineState(
            messages = listOf(
                ChatMessage(
                    id = "waiting-client-run",
                    role = MessageRole.assistant,
                    state = MessageState.streaming,
                    content = "[[clawlink:typing]]",
                    contentBlocks = listOf(RelayChatContentBlock(type = "text", text = "[[clawlink:typing]]")),
                    runId = "client-run"
                )
            ),
            activeRunId = "client-run",
            activeRunsByTurnId = mapOf("client-run" to "client-run"),
            activeTurnByRunId = mapOf("client-run" to "client-run")
        )
        val completion = event(
            """
            {
              "protocolVersion": 2,
              "eventId": "evt-active-completion",
              "eventType": "message.completed",
              "turnId": "client-run",
              "runId": "host-run",
              "runState": "active",
              "messageId": "assistant-active",
              "role": "assistant",
              "content": [{ "type": "text", "text": "阶段结果" }]
            }
            """.trimIndent()
        )

        val active = ChatTimelineReducer.reduce(initial, completion)

        assertTrue(active.hasActiveRun)
        assertEquals("host-run", active.activeRunId)
        assertEquals(mapOf("client-run" to "host-run"), active.activeRunsByTurnId)
        assertEquals("阶段结果", active.messages.last().content)

        val terminal = ChatTimelineReducer.reduce(
            active,
            TimelineEvent.RunTerminal(
                eventId = "evt-active-terminal",
                turnId = "client-run",
                runId = "host-run",
                status = "completed"
            )
        )

        assertFalse(terminal.hasActiveRun)
    }

    @Test
    fun providerRunCompletionKeepsLocalQuestionAheadByClientAlias() {
        val clientRunId = "client-run-alias"
        val initial = ChatTimelineState(
            messages = listOf(
                ChatMessage(
                    id = "user-$clientRunId",
                    role = MessageRole.user,
                    content = "question",
                    runId = "local-user-$clientRunId",
                    turnId = clientRunId,
                    clientMessageId = clientRunId,
                    idempotencyKey = clientRunId,
                    timelineOrderKey = "local:$clientRunId|10|user",
                    timelineIdentityKey = "local:message:user:$clientRunId",
                    timelineItemKind = "message:user",
                    localTurnOrder = 1
                ),
                ChatMessage(
                    id = "assistant-$clientRunId",
                    role = MessageRole.assistant,
                    state = MessageState.streaming,
                    content = "[[clawlink:typing]]",
                    runId = clientRunId,
                    turnId = clientRunId,
                    clientMessageId = clientRunId,
                    idempotencyKey = clientRunId,
                    timelineOrderKey = "local:$clientRunId|20|waiting",
                    timelineIdentityKey = "local:waiting:$clientRunId",
                    timelineItemKind = "waiting",
                    localTurnOrder = 1
                )
            )
        )
        val completion = requireNotNull(
            TimelineEventLog.decodeEvent(
                """
                {
                  "protocolVersion": 2,
                  "eventId": "evt-provider-answer",
                  "eventType": "message.completed",
                  "turnId": "server-turn-alias",
                  "runId": "provider-run-alias",
                  "clientMessageId": "$clientRunId",
                  "idempotencyKey": "$clientRunId",
                  "messageId": "assistant-provider",
                  "role": "assistant",
                  "content": [{ "type": "text", "text": "answer" }],
                  "timelineOrderKey": "v1|00000000000000000001|50|000000|assistant-provider",
                  "timelineIdentityKey": "message:assistant:assistant-provider",
                  "timelineItemKind": "message:assistant"
                }
                """.trimIndent()
            )
        )

        val state = ChatTimelineReducer.reduce(initial, completion)

        assertEquals(listOf("user-$clientRunId", "assistant-provider"), state.messages.map { it.id })
        assertEquals(clientRunId, state.messages.last().clientMessageId)
        assertEquals(clientRunId, state.messages.last().idempotencyKey)
    }

    @Test
    fun liveUserTurnKeepsRemoteRunIdentityAndSource() {
        val event = requireNotNull(
            TimelineEventLog.decodeEvent(
                """
                {
                  "protocolVersion": 2,
                  "eventId": "pc-user-live",
                  "eventType": "turn.user.created",
                  "turnId": "turn-pc-user",
                  "runId": "run-pc-user",
                  "messageId": "message-pc-user",
                  "source": "live",
                  "createdAt": "2026-06-23T11:13:00.000Z",
                  "timelineOrderKey": "v1|00000000000000000001|10|000000|message-pc-user",
                  "timelineIdentityKey": "message:user:message-pc-user",
                  "timelineItemKind": "message:user",
                  "content": [{ "type": "text", "text": "全部都装完了吗" }]
                }
                """.trimIndent()
            )
        )

        val state = ChatTimelineReducer.reduce(
            ChatTimelineState(),
            event
        )

        assertEquals("run-pc-user", state.messages.single().runId)
        assertEquals("live", state.messages.single().source)
    }

    @Test
    fun canonicalEventPreservesConversationSequenceWithoutReplacingProducerSequence() {
        val event = requireNotNull(
            TimelineEventLog.decodeEvent(
                """
                {
                  "protocolVersion": 2,
                  "eventId": "pc-conversation-seq",
                  "eventType": "message.completed",
                  "turnId": "turn-conversation-seq",
                  "runId": "run-conversation-seq",
                  "messageId": "message-conversation-seq",
                  "role": "assistant",
                  "conversationSeq": 34,
                  "seq": 7,
                  "content": [{ "type": "text", "text": "ordered reply" }],
                  "timelineOrderKey": "v1|00000000000000000034|50|000000|message-conversation-seq",
                  "timelineIdentityKey": "message:assistant:message-conversation-seq",
                  "timelineItemKind": "message:assistant"
                }
                """.trimIndent()
            )
        )

        assertEquals(34L, (event as TimelineEvent.MessageCompleted).conversationSeq)
        val state = ChatTimelineReducer.reduce(ChatTimelineState(), event)

        assertEquals(34L, state.messages.single().conversationSeq)
        assertEquals(7L, state.messages.single().seq)
    }

    @Test
    fun staleActiveCompletionDoesNotReactivateFinalizedPersistedMessage() {
        val initial = ChatTimelineState(
            messages = listOf(
                ChatMessage(
                    id = "message-finished",
                    role = MessageRole.assistant,
                    state = MessageState.completed,
                    content = "completed answer",
                    runId = "run-finished",
                    turnId = "turn-finished",
                    timelineOrderKey = "v1|00000000000000000001|50|000000|message-finished",
                    timelineIdentityKey = "message:assistant:message-finished",
                    timelineItemKind = "message:assistant"
                )
            )
        )
        val staleActiveCompletion = requireNotNull(
            TimelineEventLog.decodeEvent(
                """
                {
                  "protocolVersion": 2,
                  "eventId": "event-stale-active-completion",
                  "eventType": "message.completed",
                  "turnId": "turn-finished",
                  "runId": "run-finished",
                  "messageId": "message-finished",
                  "role": "assistant",
                  "messageState": "completed",
                  "runState": "active",
                  "content": [{ "type": "text", "text": "completed answer" }],
                  "timelineOrderKey": "v1|00000000000000000001|50|000000|message-finished",
                  "timelineIdentityKey": "message:assistant:message-finished",
                  "timelineItemKind": "message:assistant"
                }
                """.trimIndent()
            )
        )

        val result = ChatTimelineReducer.reduce(initial, staleActiveCompletion)

        assertFalse(result.hasActiveRun)
        assertEquals(MessageState.completed, result.messages.single().state)
        assertEquals("completed answer", result.messages.single().content)
    }

    @Test
    fun canonicalUserEchoWithSameMessageIdPromotesLocalBubbleWithoutLosingLocalOrder() {
        val runId = "1502fed0-7966-4629-9e88-5857815d0a2b"
        val local = ChatMessage(
            id = "user-$runId",
            role = MessageRole.user,
            content = "/new",
            runId = "local-user-$runId",
            turnId = runId,
            clientMessageId = runId,
            idempotencyKey = runId,
            timelineOrderKey = "local:$runId|10|user-$runId",
            timelineIdentityKey = "local:message:user:$runId",
            timelineItemKind = "message:user",
            source = "local",
            localTurnOrder = 7
        )
        val canonical = requireNotNull(
            TimelineEventLog.decodeEvent(
                """
                {
                  "protocolVersion": 2,
                  "eventId": "evt-user-$runId",
                  "eventType": "turn.user.created",
                  "turnId": "$runId",
                  "runId": "$runId",
                  "clientMessageId": "$runId",
                  "idempotencyKey": "$runId",
                  "messageId": "user-$runId",
                  "source": "local",
                  "content": [{ "type": "text", "text": "/new" }],
                  "timelineOrderKey": "v5|1|00000000000000000001|00000000000000000000|10|user-$runId",
                  "timelineIdentityKey": "v1|mobile-session|message|user|srv-user-$runId",
                  "timelineItemKind": "message:user"
                }
                """.trimIndent()
            )
        )

        val state = ChatTimelineReducer.reduce(ChatTimelineState(messages = listOf(local)), canonical)

        assertEquals(1, state.messages.size)
        assertEquals("user-$runId", state.messages.single().id)
        assertEquals(7L, state.messages.single().localTurnOrder)
        assertEquals("v5|1|00000000000000000001|00000000000000000000|10|user-$runId", state.messages.single().timelineOrderKey)
        assertEquals("v1|mobile-session|message|user|srv-user-$runId", state.messages.single().timelineIdentityKey)
    }

    @Test
    fun decodesWithUnknownKeysAndNullOptionals() {
        val event = TimelineEventLog.decodeEvent(
            """
            {
              "protocolVersion": 2,
              "eventId": "event-1",
              "eventType": "turn.user.created",
              "turnId": "turn-1",
              "runId": "run-1",
              "messageId": "message-1",
              "content": [{ "type": "text", "text": "hello" }],
              "createdAt": null,
              "ignored_future_field": { "nested": true }
            }
            """.trimIndent()
        )

        val created = event as TimelineEvent.TurnUserCreated
        assertEquals("event-1", created.eventId)
        assertEquals("turn-1", created.turnId)
        assertEquals("run-1", created.runId)
        assertEquals("message-1", created.messageId)
        assertEquals("hello", created.content.first().text)
        assertNull(created.createdAt)
    }

    @Test
    fun rejectsRawV1Events() {
        val event = TimelineEventLog.decodeEvent(
            """
            {
              "protocolVersion": 1,
              "eventId": "event-1",
              "eventType": "turn.user.created",
              "turnId": "turn-1",
              "messageId": "message-1",
              "text": "legacy"
            }
            """.trimIndent()
        )

        assertNull(event)
    }

    @Test
    fun appliesUserTurnsWithoutMergingRepeatedText() {
        val state = ChatTimelineReducer.reduceAll(
            ChatTimelineState(),
            listOf(
                event("""{"protocolVersion":2,"eventId":"u1","eventType":"turn.user.created","turnId":"turn-1","messageId":"user-1","content":[{"type":"text","text":"same"}]}"""),
                event("""{"protocolVersion":2,"eventId":"u2","eventType":"turn.user.created","turnId":"turn-2","messageId":"user-2","content":[{"type":"text","text":"same"}]}""")
            )
        )

        assertEquals(listOf("user-1", "user-2"), state.messages.map { it.id })
        assertEquals(listOf("same", "same"), state.messages.map { it.content })
        assertEquals(listOf(MessageRole.user, MessageRole.user), state.messages.map { it.role })
    }

    @Test
    fun turnUserCreatedDoesNotReplaceDifferentLocalRunWithSameText() {
        val initial = ChatTimelineState(
            messages = listOf(
                ChatMessage(
                    id = "local-user-a",
                    role = MessageRole.user,
                    state = MessageState.completed,
                    content = "Hi",
                    contentBlocks = listOf(RelayChatContentBlock(type = "text", text = "Hi")),
                    runId = "local-user-run-a",
                    sortTimestamp = 100.0
                ),
                ChatMessage(
                    id = "assistant-a",
                    role = MessageRole.assistant,
                    state = MessageState.streaming,
                    content = "[[clawlink:typing]]",
                    contentBlocks = listOf(RelayChatContentBlock(type = "text", text = "[[clawlink:typing]]")),
                    runId = "run-a",
                    sortTimestamp = 100.001
                )
            )
        )

        val state = ChatTimelineReducer.reduce(
            initial,
            event("""{"protocolVersion":2,"eventId":"server-user-b","eventType":"turn.user.created","turnId":"run-b","runId":"run-b","messageId":"server-user-b","source":"local","createdAt":"2026-06-09T17:00:05.000Z","content":[{"type":"text","text":"Hi"}]}""")
        )

        assertEquals(listOf("local-user-a", "assistant-a", "server-user-b"), state.messages.map { it.id })
        assertEquals(listOf("local-user-run-a", "run-a", "local-user-run-b"), state.messages.map { it.runId })
    }

    @Test
    fun historyUserWinsOverReplayedRelayProjectionForSameStableTurn() {
        val stableRunId = "wx_1785218318879_xwwecjao"
        val initial = ChatTimelineState(
            messages = listOf(
                ChatMessage(
                    id = "6781ef37",
                    role = MessageRole.user,
                    state = MessageState.completed,
                    content = "福建省农业融资担保有限公司的注册资金是多少",
                    contentBlocks = listOf(
                        RelayChatContentBlock(type = "text", text = "福建省农业融资担保有限公司的注册资金是多少")
                    ),
                    runId = "$stableRunId:user",
                    sortTimestamp = 1.0,
                    seq = 14,
                    turnSeq = 14,
                    timelineMessageId = "6781ef37",
                    timelineOrderKey = "v1|00000000000000000014|10|000000|6781ef37",
                    timelineIdentityKey = "v1|main|message|user|srv-history-user",
                    timelineItemKind = "message:user",
                    source = "history"
                )
            )
        )

        val state = ChatTimelineReducer.reduce(
            initial,
            event(
                """
                {
                  "protocolVersion": 2,
                  "eventId": "evt-old-relay-replay",
                  "eventType": "turn.user.created",
                  "turnId": "$stableRunId",
                  "runId": "$stableRunId",
                  "messageId": "user-$stableRunId",
                  "source": "local",
                  "createdAt": "2026-07-28T05:58:38.000Z",
                  "timelineOrderKey": "v1|00000000000000000028|10|000000|user-$stableRunId",
                  "timelineIdentityKey": "v1|main|message|user|srv-old-replay-user",
                  "timelineItemKind": "message:user",
                  "content": [{ "type": "text", "text": "福建省农业融资担保有限公司的注册资金是多少" }]
                }
                """.trimIndent()
            )
        )

        assertEquals(1, state.messages.size)
        assertEquals("6781ef37", state.messages.single().id)
        assertEquals("history", state.messages.single().source)
        assertEquals("v1|main|message|user|srv-history-user", state.messages.single().timelineIdentityKey)
        assertEquals(14L, state.messages.single().turnSeq)
    }

    @Test
    fun turnUserCreatedReplacesLocalImageUserMessageForSameTurn() {
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
                  "eventId": "user-server",
                  "eventType": "turn.user.created",
                  "turnId": "client-run-1",
                  "runId": "client-run-1",
                  "messageId": "server-user-message",
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
    fun turnUserCreatedReplacesLocalImageUserMessageWithAttachmentIdAndKeepsPreview() {
        val localImageBlock = RelayChatContentBlock(
            type = "file",
            text = "album-D1.jpeg",
            attachmentId = "attachment-local-1",
            fileId = "file-photo-1",
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
                  "eventId": "user-server",
                  "eventType": "turn.user.created",
                  "turnId": "client-run-1",
                  "runId": "client-run-1",
                  "messageId": "server-user-message",
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
                """.trimIndent()
            )
        )

        assertEquals(listOf("local-user-message"), state.messages.map { it.id })
        assertEquals("server-user-message", state.messages.single().timelineMessageId)
        assertEquals("file:///tmp/album-D1.jpeg", state.messages.single().fileContentBlocks.first().downloadUrl)
    }

    @Test
    fun turnUserCreatedReplacesLocalUserMessageWhenRunIdMatchesClientRun() {
        val localUser = ChatMessage(
            id = "user-client-run-1",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "本地原始提示",
            runId = "local-user-client-run-1",
            sortTimestamp = 200.0
        )

        val state = ChatTimelineReducer.reduce(
            ChatTimelineState(messages = listOf(localUser)),
            event(
                """
                {
                  "protocolVersion": 2,
                  "eventId": "user-server",
                  "eventType": "turn.user.created",
                  "turnId": "relay-request-1",
                  "runId": "client-run-1",
                  "messageId": "server-user-message",
                  "createdAt": "1970-01-01T00:03:20.500Z",
                  "content": [{ "type": "text", "text": "服务端规范提示" }]
                }
                """.trimIndent()
            )
        )

        assertEquals(listOf("user-client-run-1"), state.messages.map { it.id })
        assertEquals("server-user-message", state.messages.single().timelineMessageId)
        assertEquals("local-user-client-run-1", state.messages.single().runId)
        assertEquals("本地原始提示", state.messages.single().content)
    }

    @Test
    fun turnUserCreatedReplacesLocalPlainUserEchoWithPrefixedRunIdentity() {
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
                  "eventId": "user-server-prefixed",
                  "eventType": "turn.user.created",
                  "turnId": "user-client-run-1",
                  "runId": "user-client-run-1",
                  "messageId": "server-user-message",
                  "content": [{ "type": "text", "text": "那你现在可以做什么" }]
                }
                """.trimIndent()
            )
        )

        assertEquals(listOf("local-user-message", "assistant-local"), state.messages.map { it.id })
        assertEquals("server-user-message", state.messages.first().timelineMessageId)
        assertEquals(1, state.messages.count { it.role == MessageRole.user })
        assertEquals("local-user-client-run-1", state.messages.first().runId)
    }

    @Test
    fun prefixedUserConfirmationKeepsAssistantPlaceholderResolvable() {
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

        val state = ChatTimelineReducer.reduceAll(
            initial,
            listOf(
                event(
                    """
                    {
                      "protocolVersion": 2,
                      "eventId": "user-server-prefixed",
                      "eventType": "turn.user.created",
                      "turnId": "user-client-run-1",
                      "runId": "user-client-run-1",
                      "messageId": "server-user-message",
                      "content": [{ "type": "text", "text": "那你现在可以做什么" }]
                    }
                    """.trimIndent()
                ),
                event(
                    """
                    {
                      "protocolVersion": 2,
                      "eventId": "assistant-server-completed",
                      "eventType": "message.completed",
                      "turnId": "client-run-1",
                      "runId": "client-run-1",
                      "messageId": "assistant-server",
                      "role": "assistant",
                      "content": [{ "type": "text", "text": "我可以帮你处理本地任务。" }]
                    }
                    """.trimIndent()
                )
            )
        )

        assertEquals(listOf("local-user-message", "assistant-server"), state.messages.map { it.id })
        assertEquals("server-user-message", state.messages.first().timelineMessageId)
        assertEquals("local-user-client-run-1", state.messages.first().runId)
        assertEquals("client-run-1", state.messages.last().runId)
        assertEquals("我可以帮你处理本地任务。", state.messages.last().content)
    }

    @Test
    fun turnUserCreatedMergesTranscriptIntoLocalVoiceUserMessage() {
        val localVoice = ChatMessage(
            id = "local-user-message",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "voice-input.m4a",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "voice",
                    fileName = "voice-input.m4a",
                    mimeType = "audio/mp4",
                    downloadUrl = "file:///tmp/voice-input.m4a"
                )
            ),
            createdAt = "1970-01-01T00:03:20.000Z",
            runId = "local-user-client-run-voice",
            sortTimestamp = 200.0
        )

        val state = ChatTimelineReducer.reduce(
            ChatTimelineState(messages = listOf(localVoice)),
            event(
                """
                {
                  "protocolVersion": 2,
                  "eventId": "user-voice-transcript",
                  "eventType": "turn.user.created",
                  "turnId": "client-run-voice",
                  "runId": "client-run-voice",
                  "messageId": "server-user-voice",
                  "createdAt": "1970-01-01T00:03:20.500Z",
                  "content": [{ "type": "text", "text": "我。" }]
                }
                """.trimIndent()
            )
        )

        assertEquals(listOf("local-user-message"), state.messages.map { it.id })
        assertEquals("server-user-voice", state.messages.single().timelineMessageId)
        assertEquals("local-user-client-run-voice", state.messages.single().runId)
        assertEquals("voice-input.m4a", state.messages.single().content)
        assertEquals("我。", state.messages.single().voiceTranscriptText)
    }

    @Test
    fun messageCompletedKeepsDistinctCanonicalImageItemsEvenWhenFileIdMatches() {
        val state = ChatTimelineReducer.reduceAll(
            ChatTimelineState(),
            listOf(
                event(
                    """
                    {
                      "protocolVersion": 2,
                      "eventId": "assistant-final",
                      "eventType": "message.completed",
                      "messageId": "assistant-run-1",
                      "turnId": "turn-1",
                      "runId": "run-1",
                      "role": "assistant",
                      "createdAt": "1970-01-01T00:03:20.000Z",
                      "content": [
                        {
                          "type": "image",
                          "text": "chatgpt image.png",
                          "fileId": "file-img-1",
                          "fileName": "chatgpt image.png",
                          "mimeType": "image/png",
                          "sizeBytes": 2048,
                          "imageWidth": 1024,
                          "imageHeight": 1024,
                          "downloadUrl": "/api/mobile/files/file-img-1"
                        }
                      ]
                    }
                    """.trimIndent()
                ),
                event(
                    """
                    {
                      "protocolVersion": 2,
                      "eventId": "assistant-file-echo",
                      "eventType": "message.completed",
                      "messageId": "file-file-img-1",
                      "turnId": "turn-1",
                      "runId": "run-1",
                      "role": "assistant",
                      "createdAt": "1970-01-01T00:03:21.000Z",
                      "content": [
                        {
                          "type": "image",
                          "text": "chatgpt image.png",
                          "fileId": "file-img-1",
                          "fileName": "chatgpt image.png",
                          "mimeType": "image/png",
                          "sizeBytes": 2048,
                          "imageWidth": 1024,
                          "imageHeight": 1024,
                          "downloadUrl": "/api/mobile/files/file-img-1"
                        }
                      ]
                    }
                    """.trimIndent()
                )
            )
        )

        assertEquals(2, state.messages.size)
        assertEquals(listOf("assistant-run-1", "file-file-img-1"), state.messages.map { it.id })
        assertEquals(listOf("file-img-1", "file-img-1"), state.messages.map { it.fileContentBlocks.single().fileId })
    }

    @Test
    fun messagePartDeltaIsAbsoluteAndOlderSeqIsIgnored() {
        val state = ChatTimelineReducer.reduceAll(
            ChatTimelineState(),
            listOf(
                event("""{"protocolVersion":2,"eventId":"d1","eventType":"message.part.delta","messageId":"assistant-1","turnId":"turn-1","role":"assistant","partId":"text","seq":1,"content":[{"type":"text","text":"hello"}]}"""),
                event("""{"protocolVersion":2,"eventId":"d2","eventType":"message.part.delta","messageId":"assistant-1","turnId":"turn-1","role":"assistant","partId":"text","seq":2,"content":[{"type":"text","text":"hello world"}]}"""),
                event("""{"protocolVersion":2,"eventId":"d3","eventType":"message.part.delta","messageId":"assistant-1","turnId":"turn-1","role":"assistant","partId":"text","seq":1,"content":[{"type":"text","text":"stale"}]}""")
            )
        )

        assertEquals(1, state.messages.size)
        assertEquals("hello world", state.messages.single().content)
        assertEquals(MessageState.streaming, state.messages.single().state)
    }

    @Test
    fun runlessPartDeltaStillRepresentsActiveVisibleRun() {
        val state = ChatTimelineReducer.reduceAll(
            ChatTimelineState(),
            listOf(
                event("""{"protocolVersion":2,"eventId":"d1","eventType":"message.part.delta","messageId":"assistant-1","role":"assistant","partId":"text","seq":1,"content":[{"type":"text","text":"hello"}]}""")
            )
        )

        assertEquals(MessageState.streaming, state.messages.single().state)
        assertTrue(hasActiveVisibleTimelineRun(state, state.messages))
    }

    @Test
    fun messagePartDeltaReplacesOptimisticAssistantPlaceholderForSameRun() {
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
            content = "[[clawlink:typing]]",
            runId = "run-new",
            sortTimestamp = 200.001
        )

        val state = ChatTimelineReducer.reduce(
            ChatTimelineState(messages = listOf(localUser, localAssistant)),
            event(
                """
                {
                  "protocolVersion": 2,
                  "eventId": "delta-server",
                  "eventType": "message.part.delta",
                  "turnId": "turn-new",
                  "runId": "run-new",
                  "messageId": "assistant-server",
                  "role": "assistant",
                  "partId": "text",
                  "seq": 1,
                  "content": [{ "type": "text", "text": "[[clawlink:typing]]" }],
                  "createdAt": "1970-01-01T00:03:20.001Z"
                }
                """.trimIndent()
            )
        )
        val ordered = orderTimelineMessages(state.messages)

        assertEquals(listOf("local-user-new", "assistant-server"), ordered.map { it.id })
        assertEquals(200.001, ordered.last().sortTimestamp ?: -1.0, 0.0001)
        assertTrue(hasActiveVisibleTimelineRun(state, ordered))
    }

    @Test
    fun messagePartDeltaReplacesOnlyOptimisticAssistantPlaceholderWhenGatewayUsesRequestRunId() {
        val localUser = ChatMessage(
            id = "local-user-new",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "Reply QA907 only",
            runId = "local-user-relay-request-run",
            sortTimestamp = 210.0
        )
        val localAssistant = ChatMessage(
            id = "assistant-mobile-run",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = protocolTypingMarkerText,
            runId = "relay-request-run",
            sortTimestamp = 210.001
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
                  "eventId": "delta-request",
                  "eventType": "message.part.delta",
                  "turnId": "relay-request-run",
                  "runId": "relay-request-run",
                  "messageId": "assistant-relay-request-run",
                  "role": "assistant",
                  "partId": "text",
                  "seq": 1,
                  "content": [{ "type": "text", "text": "QA907" }],
                  "createdAt": "1970-01-01T00:03:30.001Z"
                }
                """.trimIndent()
            )
        )
        val ordered = orderTimelineMessages(state.messages)

        assertEquals(listOf("local-user-new", "assistant-relay-request-run"), ordered.map { it.id })
        assertEquals("QA907", ordered.last().content)
        assertEquals("relay-request-run", ordered.last().runId)
        assertEquals(210.001, ordered.last().sortTimestamp ?: -1.0, 0.0001)
        assertTrue(hasActiveVisibleTimelineRun(state, ordered))
    }

    @Test
    fun messageCompletedReplacesOptimisticAssistantPlaceholderForSameRun() {
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
                  "eventId": "final-server",
                  "eventType": "message.completed",
                  "turnId": "turn-new",
                  "runId": "run-new",
                  "messageId": "assistant-server",
                  "role": "assistant",
                  "content": [{ "type": "text", "text": "OK" }],
                  "createdAt": "1970-01-01T00:03:20.001Z"
                }
                """.trimIndent()
            )
        )
        val ordered = orderTimelineMessages(state.messages)

        assertEquals(listOf("local-user-new", "assistant-server"), ordered.map { it.id })
        assertEquals("OK", ordered.last().content)
        assertEquals(MessageState.completed, ordered.last().state)
        assertEquals(200.001, ordered.last().sortTimestamp ?: -1.0, 0.0001)
        assertFalse(hasActiveVisibleTimelineRun(state, ordered))
    }

}
