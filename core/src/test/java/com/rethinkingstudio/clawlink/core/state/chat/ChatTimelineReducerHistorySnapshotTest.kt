package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

class ChatTimelineReducerHistorySnapshotTest {
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
        val ordered = orderMessagesWithSourceRunAnchors(state.messages)

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
        val ordered = orderMessagesWithSourceRunAnchors(state.messages)

        assertEquals(listOf("local-user-new", "assistant-relay-request-run"), ordered.map { it.id })
        assertEquals("QA908", ordered.last().content)
        assertEquals(MessageState.completed, ordered.last().state)
        assertEquals("relay-request-run", ordered.last().runId)
        assertEquals(220.001, ordered.last().sortTimestamp ?: -1.0, 0.0001)
        assertFalse(hasActiveVisibleTimelineRun(state, ordered))
        assertFalse(state.hasActiveRun)
    }

    @Ignore("Legacy placeholder stealing guard without canonical order was removed.")
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
        val ordered = orderMessagesWithSourceRunAnchors(state.messages)

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
}
