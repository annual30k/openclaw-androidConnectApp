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
    fun decodesWithUnknownKeysAndNullOptionals() {
        val event = TimelineEventLog.decodeEvent(
            """
            {
              "protocolVersion": 2,
              "eventId": "event-1",
              "eventType": "turn.user.created",
              "turnId": "turn-1",
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
                  "turnId": "relay-turn-1",
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

        assertEquals(listOf("server-user-message"), state.messages.map { it.id })
        assertEquals("local-user-client-run-1", state.messages.single().runId)
        assertEquals("file-photo-1", state.messages.single().fileContentBlocks.first().fileId)
        assertEquals("file:///tmp/album-D1.jpeg", state.messages.single().fileContentBlocks.first().downloadUrl)
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
        val ordered = orderMessagesWithSourceRunAnchors(state.messages)

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
            runId = "local-user-mobile-run",
            sortTimestamp = 210.0
        )
        val localAssistant = ChatMessage(
            id = "assistant-mobile-run",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = protocolTypingMarkerText,
            runId = "mobile-run",
            sortTimestamp = 210.001
        )

        val state = ChatTimelineReducer.reduce(
            ChatTimelineState(
                messages = listOf(localUser, localAssistant),
                activeRunId = "mobile-run",
                activeRunsByTurnId = mapOf("mobile-run" to "mobile-run"),
                activeTurnByRunId = mapOf("mobile-run" to "mobile-run")
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
        val ordered = orderMessagesWithSourceRunAnchors(state.messages)

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
        val ordered = orderMessagesWithSourceRunAnchors(state.messages)

        assertEquals(listOf("local-user-new", "assistant-server"), ordered.map { it.id })
        assertEquals("OK", ordered.last().content)
        assertEquals(MessageState.completed, ordered.last().state)
        assertEquals(200.001, ordered.last().sortTimestamp ?: -1.0, 0.0001)
        assertFalse(hasActiveVisibleTimelineRun(state, ordered))
    }

    @Test
    fun emptyMessageCompletedKeepsOptimisticAssistantPlaceholderWaitingForFirstReplyText() {
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
        val ordered = orderMessagesWithSourceRunAnchors(state.messages)

        assertEquals(listOf("local-user-new", "assistant-local"), ordered.map { it.id })
        assertEquals(protocolTypingMarkerText, ordered.last().content)
        assertEquals(MessageState.streaming, ordered.last().state)
        assertTrue(hasActiveVisibleTimelineRun(state, ordered))
        assertTrue(state.hasActiveRun)
        assertTrue("final-empty" in state.seenEventIds)
    }

    @Test
    fun messageCompletedReplacesOnlyOptimisticAssistantPlaceholderWhenGatewayUsesRequestRunId() {
        val localUser = ChatMessage(
            id = "local-user-new",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "Reply QA908 only",
            runId = "local-user-mobile-run",
            sortTimestamp = 220.0
        )
        val localAssistant = ChatMessage(
            id = "assistant-mobile-run",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = protocolTypingMarkerText,
            runId = "mobile-run",
            sortTimestamp = 220.001
        )

        val state = ChatTimelineReducer.reduce(
            ChatTimelineState(
                messages = listOf(localUser, localAssistant),
                activeRunId = "mobile-run",
                activeRunsByTurnId = mapOf("mobile-run" to "mobile-run"),
                activeTurnByRunId = mapOf("mobile-run" to "mobile-run")
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
        val ordered = orderMessagesWithSourceRunAnchors(state.messages)

        assertEquals(listOf("local-user-new", "assistant-relay-request-run"), ordered.map { it.id })
        assertEquals("QA908", ordered.last().content)
        assertEquals(MessageState.completed, ordered.last().state)
        assertEquals("relay-request-run", ordered.last().runId)
        assertEquals(220.001, ordered.last().sortTimestamp ?: -1.0, 0.0001)
        assertFalse(hasActiveVisibleTimelineRun(state, ordered))
        assertFalse(state.hasActiveRun)
    }

    @Test
    fun messageCompletedDoesNotStealLaterOptimisticPlaceholderForEarlierCommandTurn() {
        val commandUser = ChatMessage(
            id = "user-command-run",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "/new",
            runId = "local-user-command-run",
            sortTimestamp = 230.0
        )
        val nextUser = ChatMessage(
            id = "user-next-run",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "Reply next only",
            runId = "local-user-next-run",
            sortTimestamp = 240.0
        )
        val nextAssistantPlaceholder = ChatMessage(
            id = "assistant-next-run",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = protocolTypingMarkerText,
            runId = "next-run",
            sortTimestamp = 240.001
        )

        val state = ChatTimelineReducer.reduce(
            ChatTimelineState(
                messages = listOf(commandUser, nextUser, nextAssistantPlaceholder),
                activeRunId = "next-run",
                activeRunsByTurnId = mapOf("next-run" to "next-run"),
                activeTurnByRunId = mapOf("next-run" to "next-run")
            ),
            event(
                """
                {
                  "protocolVersion": 2,
                  "eventId": "final-command",
                  "eventType": "message.completed",
                  "turnId": "command-run",
                  "runId": "command-run",
                  "messageId": "assistant-command-run",
                  "role": "assistant",
                  "content": [{ "type": "text", "text": "New session started" }],
                  "createdAt": "1970-01-01T00:03:50.001Z"
                }
                """.trimIndent()
            )
        )
        val ordered = orderMessagesWithSourceRunAnchors(state.messages)

        assertEquals(
            listOf("user-command-run", "assistant-command-run", "user-next-run", "assistant-next-run"),
            ordered.map { it.id }
        )
        assertEquals("New session started", ordered[1].content)
        assertEquals(MessageState.completed, ordered[1].state)
        assertEquals(protocolTypingMarkerText, ordered.last().content)
        assertEquals(MessageState.streaming, ordered.last().state)
        assertTrue(hasActiveVisibleTimelineRun(state, ordered))
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
            sortTimestamp = 200.001
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
        val ordered = orderMessagesWithSourceRunAnchors(state.messages)

        assertEquals(listOf("local-user-new", "assistant-server"), ordered.map { it.id })
        assertEquals("OK", ordered.last().content)
        assertFalse(hasActiveVisibleTimelineRun(state, ordered))
        assertEquals(setOf("final-server"), state.seenEventIds)
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
            id = "user-old",
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
                      "runId": "run-old",
	                      "messageId": "assistant-old-server",
	                      "role": "assistant",
	                      "messageState": "completed",
	                      "content": [{ "type": "text", "text": "old reply" }]
	                    }
	                  ]
                }
                """.trimIndent()
            )
        )
        val ordered = orderMessagesWithSourceRunAnchors(state.messages)

        assertEquals(
            listOf("user-old", "assistant-old-server", "user-new", "assistant-new-local"),
            ordered.map { it.id }
	        )
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
                      "turnId": "relay-turn-1",
                      "runId": "history-user-message",
                      "messageId": "server-user-message",
                      "role": "user",
                      "messageState": "completed",
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

        assertEquals(listOf("server-user-message"), state.messages.map { it.id })
        assertEquals("local-user-client-run-1", state.messages.single().runId)
        assertEquals("file-photo-1", state.messages.single().fileContentBlocks.first().fileId)
        assertEquals("file:///tmp/album-D1.jpeg", state.messages.single().fileContentBlocks.first().downloadUrl)
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
    fun runCompletedKeepsTransientStreamingPlaceholderWaitingForFinalContent() {
        val streaming = ChatTimelineReducer.reduce(
            ChatTimelineState(),
            event("""{"protocolVersion":2,"eventId":"d1","eventType":"message.part.delta","messageId":"assistant-1","turnId":"turn-1","runId":"run-1","role":"assistant","partId":"text","seq":1,"content":[{"type":"text","text":"[[clawlink:typing]]"}]}""")
        )

        val stillWaiting = ChatTimelineReducer.reduce(
            streaming,
            event("""{"protocolVersion":2,"eventId":"r1","eventType":"run.completed","turnId":"turn-1","runId":"run-1"}""")
        )

        assertEquals(MessageState.streaming, stillWaiting.messages.single().state)
        assertTrue(stillWaiting.hasActiveRun)
        assertTrue(hasActiveVisibleTimelineRun(stillWaiting, stillWaiting.messages))
    }

    @Test
    fun runFailedWithoutRunIdsFinalizesVisibleAssistantStreams() {
        val state = ChatTimelineState(
            messages = listOf(
                ChatMessage(
                    id = "user-1",
                    role = MessageRole.user,
                    state = MessageState.completed,
                    content = "trigger failure",
                    runId = "local-user-run-1",
                    sortTimestamp = 300.0
                ),
                ChatMessage(
                    id = "assistant-placeholder",
                    role = MessageRole.assistant,
                    state = MessageState.streaming,
                    content = protocolTypingMarkerText,
                    runId = "run-1",
                    sortTimestamp = 300.001
                ),
                ChatMessage(
                    id = "assistant-error",
                    role = MessageRole.assistant,
                    state = MessageState.streaming,
                    content = "API call failed after 3 retries: HTTP 429",
                    runId = "",
                    sortTimestamp = 301.0
                )
            ),
            activeRunId = "run-1",
            activeRunsByTurnId = mapOf("turn-1" to "run-1"),
            activeTurnByRunId = mapOf("run-1" to "turn-1")
        )

        val failed = ChatTimelineReducer.reduce(
            state,
            event("""{"protocolVersion":2,"eventId":"r1","eventType":"run.failed"}""")
        )

        assertEquals(
            listOf(MessageState.completed, MessageState.failed, MessageState.failed),
            failed.messages.map { it.state }
        )
        assertFalse(failed.hasActiveRun)
        assertFalse(hasActiveVisibleTimelineRun(failed, failed.messages))
    }

    @Test
    fun terminalRunEventsClearActiveRun() {
        val completed = ChatTimelineReducer.reduce(
            ChatTimelineState(activeRunId = "run-1"),
            event("""{"protocolVersion":2,"eventId":"r1","eventType":"run.completed","runId":"run-1"}""")
        )
        val failed = ChatTimelineReducer.reduce(
            ChatTimelineState(activeRunId = "run-2"),
            event("""{"protocolVersion":2,"eventId":"r2","eventType":"run.failed","runId":"run-2"}""")
        )
        val aborted = ChatTimelineReducer.reduce(
            ChatTimelineState(activeRunId = "run-3"),
            event("""{"protocolVersion":2,"eventId":"r3","eventType":"run.aborted","runId":"run-3"}""")
        )

        assertNull(completed.activeRunId)
        assertNull(failed.activeRunId)
        assertNull(aborted.activeRunId)
    }

    @Test
    fun runAbortedRemovesEmptyAssistantCompletionForStoppedRun() {
        val user = ChatMessage(
            id = "user-stop",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "stop me",
            runId = "local-user-run-stop",
            sortTimestamp = 500.0
        )
        val emptyAssistant = ChatMessage(
            id = "assistant-stop",
            role = MessageRole.assistant,
            state = MessageState.completed,
            content = "",
            contentBlocks = listOf(com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock(type = "text", text = "")),
            runId = "run-stop",
            sortTimestamp = 500.001
        )

        val state = ChatTimelineReducer.reduce(
            ChatTimelineState(
                messages = listOf(user, emptyAssistant),
                activeRunId = "run-stop",
                activeRunsByTurnId = mapOf("turn-stop" to "run-stop"),
                activeTurnByRunId = mapOf("run-stop" to "turn-stop")
            ),
            event("""{"protocolVersion":2,"eventId":"abort-stop","eventType":"run.aborted","turnId":"turn-stop","runId":"run-stop"}""")
        )

        assertEquals(listOf("user-stop"), state.messages.map { it.id })
        assertFalse(state.hasActiveRun)
    }

    @Test
    fun eventIdDedupPreventsReapplyingEvents() {
        val created = event("""{"protocolVersion":2,"eventId":"same-event","eventType":"turn.user.created","turnId":"turn-1","messageId":"user-1","content":[{"type":"text","text":"hello"}]}""")

        val state = ChatTimelineReducer.reduceAll(ChatTimelineState(), listOf(created, created))

        assertEquals(1, state.messages.size)
        assertEquals(setOf("same-event"), state.seenEventIds)
    }

    @Test
    fun messagePartSeqDedupIgnoresSameMessagePartAndSeqAcrossEventIds() {
        val state = ChatTimelineReducer.reduceAll(
            ChatTimelineState(),
            listOf(
                event("""{"protocolVersion":2,"eventId":"d1","eventType":"message.part.delta","messageId":"assistant-1","turnId":"turn-1","role":"assistant","partId":"text","seq":7,"content":[{"type":"text","text":"first"}]}"""),
                event("""{"protocolVersion":2,"eventId":"d2","eventType":"message.part.delta","messageId":"assistant-1","turnId":"turn-1","role":"assistant","partId":"text","seq":7,"content":[{"type":"text","text":"second"}]}""")
            )
        )

        assertEquals("first", state.messages.single().content)
        assertTrue("assistant-1|text|7" in state.seenPartSeqKeys)
    }

    @Test
    fun attachmentStateChangedUpsertsAttachmentState() {
        val state = AttachmentTimelineReducer.reduce(
            ChatTimelineState(),
            event("""{"protocolVersion":2,"eventId":"a1","eventType":"attachment.state.changed","attachmentId":"att-1","messageId":"user-1","state":"uploaded","url":"file://a.png"}""")
        )

        val attachment = state.attachmentsById.getValue("att-1")
        assertEquals("user-1", attachment.messageId)
        assertEquals("uploaded", attachment.state)
        assertEquals("file://a.png", attachment.url)
    }

    @Test
    fun toolInvocationUpdatedUpsertsToolState() {
        val state = ChatTimelineReducer.reduce(
            ChatTimelineState(),
            event("""{"protocolVersion":2,"eventId":"t1","eventType":"tool.invocation.updated","toolInvocationId":"tool-1","messageId":"assistant-1","name":"shell","toolState":"running","content":[{"type":"tool_call","text":"pwd"}]}""")
        )

        val tool = state.toolsById.getValue("tool-1")
        assertEquals("assistant-1", tool.messageId)
        assertEquals("shell", tool.name)
        assertEquals("running", tool.state)
        assertEquals("pwd", tool.text)
    }

    @Test
    fun historySnapshotPageKeepsDistinctMessagesInSameTurn() {
        val state = ChatTimelineReducer.reduceAll(
            ChatTimelineState(),
            listOf(
                event("""{"protocolVersion":2,"eventId":"h1","eventType":"history.snapshot.page","messages":[{"turnId":"turn-1","messageId":"user-1","role":"user","text":"hello"}]}"""),
                event("""{"protocolVersion":2,"eventId":"h2","eventType":"history.snapshot.page","messages":[{"turnId":"turn-1","messageId":"assistant-1","role":"assistant","content":[{"type":"text","text":"reply"}]}]}""")
            )
        )

        assertEquals(listOf("user-1", "assistant-1"), state.messages.map { it.id })
        assertEquals(listOf("hello", "reply"), state.messages.map { it.content })
        assertEquals(setOf("turn-1"), state.historySnapshotTurnIds)
        assertEquals(setOf("user-1", "assistant-1"), state.historySnapshotMessageIds)
    }

    @Test
    fun historySnapshotSortsBeforeOptimisticLocalSendByCreatedAt() {
        val localUser = ChatMessage(
            id = "local-user-new",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "new prompt",
            runId = "local-user-run-new",
            sortTimestamp = 200.0
        )
        val localAssistant = ChatMessage(
            id = "assistant-new",
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
                  "eventId": "history-older",
                  "eventType": "history.snapshot.page",
                  "messages": [
                    {
                      "turnId": "turn-old",
                      "messageId": "user-old",
                      "role": "user",
                      "text": "old prompt",
                      "createdAt": "1970-01-01T00:01:40.000Z"
                    },
                    {
                      "turnId": "turn-old",
                      "messageId": "assistant-old",
                      "role": "assistant",
                      "text": "old reply",
                      "createdAt": "1970-01-01T00:01:41.000Z"
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        val ordered = orderMessagesWithSourceRunAnchors(state.messages)

        assertEquals(
            listOf("user-old", "assistant-old", "local-user-new", "assistant-new"),
            ordered.map { it.id }
        )
    }

    private fun event(raw: String): TimelineEvent {
        return requireNotNull(TimelineEventLog.decodeEvent(raw))
    }
}
