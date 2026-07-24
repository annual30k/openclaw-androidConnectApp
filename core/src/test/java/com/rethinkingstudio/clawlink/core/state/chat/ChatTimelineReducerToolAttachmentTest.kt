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

class ChatTimelineReducerToolAttachmentTest {
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
    fun runAbortedRemovesWaitingAssistantPlaceholderForRemoteTerminalEvent() {
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
            state = MessageState.streaming,
            content = "正在同步回复...",
            contentBlocks = listOf(com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock(type = "text", text = "正在同步回复...")),
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
    fun attachmentMessageCompletedWithoutTimelineResolvesWaitingKeepsWaitingPlaceholder() {
        val user = ChatMessage(
            id = "user-1",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "发我截图",
            runId = "local-user-turn-1",
            sortTimestamp = 200.0,
            timelineOrderKey = localTimelineOrderKey("turn-1", 10, "user-1"),
            timelineIdentityKey = localTimelineIdentityKey("message:user", "user-1"),
            timelineItemKind = "message:user"
        )
        val assistant = buildLocalTextAssistantPlaceholderMessage(
            id = "assistant-local",
            clientRunId = "run-1",
            sortTimestamp = 200.001
        )

        val state = ChatTimelineReducer.reduce(
            ChatTimelineState(
                messages = listOf(user, assistant),
                activeRunId = "run-1",
                activeRunsByTurnId = mapOf("turn-1" to "run-1"),
                activeTurnByRunId = mapOf("run-1" to "turn-1")
            ),
            event(
                """
                {
                  "protocolVersion": 2,
                  "eventId": "attachment-no-resolve-flag",
                  "eventType": "message.completed",
                  "turnId": "turn-1",
                  "runId": "run-1",
                  "messageId": "attachment-1",
                  "role": "assistant",
                  "createdAt": "1970-01-01T00:03:20.002Z",
                  "content": [
                    {
                      "type": "image",
                      "attachmentId": "att-1",
                      "fileId": "file-1",
                      "fileName": "shot.png",
                      "mimeType": "image/png",
                      "downloadUrl": "/api/mobile/files/file-1"
                    }
                  ],
                  "timelineOrderKey": "v1|00000000000000000001|30|000000|attachment-1",
                  "timelineIdentityKey": "local:turn-1:attachment:att-1",
                  "timelineItemKind": "attachment"
                }
                """.trimIndent()
            )
        )

        assertEquals(listOf("user-1", "attachment-1", "assistant-local"), state.messages.map { it.id })
        assertEquals(MessageState.streaming, state.messages.last().state)
        assertTrue(hasActiveVisibleTimelineRun(state, state.messages))
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
    fun toolInvocationUpdatedMaterializesToolMessageAndKeepsWaitingBubble() {
        val user = ChatMessage(
            id = "user-1",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "Read the file",
            runId = "local-user-turn-1",
            sortTimestamp = 200.0
        )
        val assistant = ChatMessage(
            id = "assistant-local",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = protocolTypingMarkerText,
            runId = "run-1",
            sortTimestamp = 200.001
        )

        val state = ChatTimelineReducer.reduce(
            ChatTimelineState(
                messages = listOf(user, assistant),
                activeRunId = "run-1",
                activeRunsByTurnId = mapOf("turn-1" to "run-1"),
                activeTurnByRunId = mapOf("run-1" to "turn-1")
            ),
            event(
                """
                {
                  "protocolVersion": 2,
                  "eventId": "tool-running",
                  "eventType": "tool.invocation.updated",
                  "turnId": "turn-1",
                  "runId": "run-1",
                  "messageId": "tool-turn-1",
                  "role": "tool",
                  "messageState": "streaming",
                  "createdAt": "1970-01-01T00:03:20.002Z",
                  "content": [
                    { "type": "tool_call", "text": "Reading config", "name": "read_file", "toolCallId": "tool-1" }
                  ],
                  "toolInvocationId": "tool-1",
                  "name": "read_file",
                  "toolState": "running"
                }
                """.trimIndent()
            )
        )

        assertEquals(listOf("user-1", "tool-turn-1", "assistant-local"), state.messages.map { it.id })
        val toolMessage = state.messages.single { it.role == MessageRole.tool }
        assertEquals("tool-turn-1", toolMessage.id)
        assertEquals(MessageState.streaming, toolMessage.state)
        assertEquals("Reading config", toolMessage.content)
        assertEquals("tool-1", toolMessage.contentBlocks.first().toolCallId)
        assertTrue(toolMessage.shouldDisplayInChat(showInvocationProcess = true))
        assertEquals(MessageState.streaming, state.messages.first { it.id == "assistant-local" }.state)
        assertEquals(protocolTypingMarkerText, state.messages.first { it.id == "assistant-local" }.content)
        assertTrue(hasActiveVisibleTimelineRun(state, state.messages))
        assertEquals("running", state.toolsById.getValue("tool-1").state)
    }

    @Test
    fun statusOnlyToolInvocationMaterializesToolMessageAndKeepsWaitingBubble() {
        val user = ChatMessage(
            id = "user-1",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "Search the weather",
            runId = "local-user-turn-1",
            sortTimestamp = 200.0
        )
        val assistant = ChatMessage(
            id = "assistant-local",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = protocolTypingMarkerText,
            runId = "run-1",
            sortTimestamp = 200.001
        )

        val state = ChatTimelineReducer.reduce(
            ChatTimelineState(
                messages = listOf(user, assistant),
                activeRunId = "run-1",
                activeRunsByTurnId = mapOf("turn-1" to "run-1"),
                activeTurnByRunId = mapOf("run-1" to "turn-1")
            ),
            event(
                """
                {
                  "protocolVersion": 2,
                  "eventId": "tool-status",
                  "eventType": "tool.invocation.updated",
                  "turnId": "turn-1",
                  "runId": "run-1",
                  "messageId": "tool-turn-1",
                  "role": "tool",
                  "messageState": "streaming",
                  "createdAt": "1970-01-01T00:03:20.002Z",
                  "toolInvocationId": "tool-1",
                  "name": "web_search",
                  "toolState": "running"
                }
                """.trimIndent()
            )
        )

        assertEquals(listOf("user-1", "tool-turn-1", "assistant-local"), state.messages.map { it.id })
        val toolMessage = state.messages.single { it.role == MessageRole.tool }
        assertEquals(MessageState.streaming, toolMessage.state)
        assertEquals("web_search", toolMessage.content)
        assertTrue(toolMessage.shouldDisplayInChat(showInvocationProcess = true))
        assertEquals(protocolTypingMarkerText, state.messages.first { it.id == "assistant-local" }.content)
        assertEquals("running", state.toolsById.getValue("tool-1").state)
    }

    @Test
    fun toolInvocationStatusOnlyUpdatePreservesMaterializedToolContent() {
        val running = ChatTimelineReducer.reduce(
            ChatTimelineState(),
            event(
                """
                {
                  "protocolVersion": 2,
                  "eventId": "tool-running",
                  "eventType": "tool.invocation.updated",
                  "turnId": "turn-1",
                  "runId": "run-1",
                  "messageId": "tool-turn-1",
                  "role": "tool",
                  "messageState": "streaming",
                  "createdAt": "1970-01-01T00:03:20.002Z",
                  "content": [
                    { "type": "tool_call", "text": "Reading config", "name": "read_file", "toolCallId": "tool-1" }
                  ],
                  "toolInvocationId": "tool-1",
                  "name": "read_file",
                  "toolState": "running"
                }
                """.trimIndent()
            )
        )

        val completed = ChatTimelineReducer.reduce(
            running,
            event(
                """
                {
                  "protocolVersion": 2,
                  "eventId": "tool-success",
                  "eventType": "tool.invocation.updated",
                  "turnId": "turn-1",
                  "runId": "run-1",
                  "messageId": "tool-turn-1",
                  "role": "tool",
                  "messageState": "completed",
                  "createdAt": "1970-01-01T00:03:20.003Z",
                  "toolInvocationId": "tool-1",
                  "name": "read_file",
                  "toolState": "success"
                }
                """.trimIndent()
            )
        )

        val toolMessage = completed.messages.single { it.role == MessageRole.tool }
        assertEquals(MessageState.completed, toolMessage.state)
        assertEquals("Reading config", toolMessage.content)
        assertEquals("tool-1", toolMessage.contentBlocks.first().toolCallId)
        assertEquals("success", completed.toolsById.getValue("tool-1").state)
        assertEquals("run-1", completed.activeRunId)
    }

    @Test
    fun lateCompletedToolDoesNotReactivateTerminalRun() {
        val completed = ChatTimelineReducer.reduce(
            ChatTimelineState(),
            event(
                """
                {
                  "protocolVersion": 2,
                  "eventId": "late-tool-success",
                  "eventType": "tool.invocation.updated",
                  "turnId": "turn-1",
                  "runId": "run-1",
                  "messageId": "tool-turn-1",
                  "role": "tool",
                  "messageState": "completed",
                  "createdAt": "1970-01-01T00:03:20.003Z",
                  "content": [
                    { "type": "tool_result", "text": "Done", "name": "read_file", "toolCallId": "tool-1" }
                  ],
                  "toolInvocationId": "tool-1",
                  "name": "read_file",
                  "toolState": "success"
                }
                """.trimIndent()
            )
        )

        assertNull(completed.activeRunId)
        assertTrue(completed.activeRunsByTurnId.isEmpty())
        assertTrue(completed.activeTurnByRunId.isEmpty())
    }

    @Test
    fun runCompletedAfterToolEventClosesWaitingAndStillAcceptsLateFinalContent() {
        val user = ChatMessage(
            id = "user-1",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "Use a tool",
            runId = "local-user-turn-1",
            sortTimestamp = 200.0
        )
        val assistant = ChatMessage(
            id = "assistant-local",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "正在同步回复...",
            runId = "run-1",
            sortTimestamp = 200.001
        )
        val toolRunning = ChatTimelineReducer.reduce(
            ChatTimelineState(
                messages = listOf(user, assistant),
                activeRunId = "run-1",
                activeRunsByTurnId = mapOf("turn-1" to "run-1"),
                activeTurnByRunId = mapOf("run-1" to "turn-1")
            ),
            event(
                """
                {
                  "protocolVersion": 2,
                  "eventId": "tool-running",
                  "eventType": "tool.invocation.updated",
                  "turnId": "turn-1",
                  "runId": "run-1",
                  "messageId": "tool-turn-1",
                  "role": "tool",
                  "messageState": "streaming",
                  "content": [
                    { "type": "tool_call", "text": "Reading file", "name": "read_file", "toolCallId": "tool-1" }
                  ],
                  "toolInvocationId": "tool-1",
                  "name": "read_file",
                  "toolState": "running"
                }
                """.trimIndent()
            )
        )

        val completed = ChatTimelineReducer.reduce(
            toolRunning,
            event("""{"protocolVersion":2,"eventId":"run-done","eventType":"run.completed","turnId":"turn-1","runId":"run-1"}""")
        )

        assertEquals("Reading file", completed.messages.single { it.role == MessageRole.tool }.content)
        assertEquals(MessageState.completed, completed.messages.single { it.role == MessageRole.tool }.state)
        assertEquals(listOf("user-1", "tool-turn-1"), completed.messages.map { it.id })
        assertFalse(hasActiveVisibleTimelineRun(completed, completed.messages))

        val final = ChatTimelineReducer.reduce(
            completed,
            event(
                """
                {
                  "protocolVersion": 2,
                  "eventId": "final",
                  "eventType": "message.completed",
                  "turnId": "turn-1",
                  "runId": "run-1",
                  "messageId": "assistant-final",
                  "role": "assistant",
                  "content": [{ "type": "text", "text": "Tool result is ready." }]
                }
                """.trimIndent()
            )
        )

        assertEquals(listOf("user-1", "tool-turn-1", "assistant-final"), final.messages.map { it.id })
        assertEquals(MessageState.completed, final.messages.first { it.id == "assistant-final" }.state)
        assertEquals("Tool result is ready.", final.messages.first { it.id == "assistant-final" }.content)
        assertEquals("Reading file", final.messages.single { it.role == MessageRole.tool }.content)
        assertFalse(hasActiveVisibleTimelineRun(final, final.messages))
    }

    @Test
    fun assistantAttachmentCompletedWithTimelineResolvesWaitingReplacesWaitingBubble() {
        val waitingAssistant = ChatMessage(
            id = "assistant-local",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "正在同步回复...",
            runId = "run-1",
            sortTimestamp = 200.001
        )

        val state = ChatTimelineReducer.reduce(
            ChatTimelineState(
                messages = listOf(waitingAssistant),
                activeRunId = "run-1",
                activeRunsByTurnId = mapOf("turn-1" to "run-1"),
                activeTurnByRunId = mapOf("run-1" to "turn-1")
            ),
            event(
                """
                {
                  "protocolVersion": 2,
                  "eventId": "image-result",
                  "eventType": "message.completed",
                  "turnId": "turn-1",
                  "runId": "run-1",
                  "messageId": "assistant-image",
                  "role": "assistant",
                  "timelineItemKind": "attachment",
                  "timelineResolvesWaiting": true,
                  "content": [
                    {
                      "type": "image",
                      "attachmentId": "att-image-1",
                      "fileName": "result.png",
                      "mimeType": "image/png",
                      "downloadUrl": "https://relay.example/files/att-image-1"
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        assertEquals(listOf("assistant-image"), state.messages.map { it.id })
        assertEquals(MessageState.completed, state.messages.first { it.id == "assistant-image" }.state)
        assertFalse(state.hasActiveRun)
        assertFalse(hasActiveVisibleTimelineRun(state, state.messages))
    }

    @Test
    fun attachmentStateChangedDoesNotReplaceWaitingBubble() {
        val waitingAssistant = ChatMessage(
            id = "assistant-local",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "正在同步回复...",
            runId = "run-1",
            sortTimestamp = 200.001
        )

        val state = ChatTimelineReducer.reduce(
            ChatTimelineState(
                messages = listOf(waitingAssistant),
                activeRunId = "run-1",
                activeRunsByTurnId = mapOf("turn-1" to "run-1"),
                activeTurnByRunId = mapOf("run-1" to "turn-1")
            ),
            event(
                """
                {
                  "protocolVersion": 2,
                  "eventId": "attachment-ready",
                  "eventType": "attachment.state.changed",
                  "attachmentId": "att-image-1",
                  "messageId": "assistant-image",
                  "state": "ready",
                  "url": "https://relay.example/files/att-image-1"
                }
                """.trimIndent()
            )
        )

        assertEquals(listOf("assistant-local"), state.messages.map { it.id })
        assertEquals(MessageState.streaming, state.messages.single().state)
        assertTrue(state.hasActiveRun)
        assertTrue(hasActiveVisibleTimelineRun(state, state.messages))
    }

    @Test
    fun messageCompletedReplacesStreamingAssistantForSameRunWhenMessageIdChanges() {
        val user = ChatMessage(
            id = "user-1",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "Use a tool",
            runId = "local-user-turn-1",
            sortTimestamp = 200.0
        )
        val tool = ChatMessage(
            id = "tool-turn-1",
            role = MessageRole.tool,
            state = MessageState.streaming,
            content = "Reading file",
            contentBlocks = listOf(RelayChatContentBlock(type = "tool_call", text = "Reading file", name = "read_file", toolCallId = "tool-1")),
            runId = "run-1",
            sortTimestamp = 200.0009
        )
        val streamingAssistant = ChatMessage(
            id = "assistant-delta",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "Partial answer",
            runId = "run-1",
            sortTimestamp = 200.001
        )

        val state = ChatTimelineReducer.reduce(
            ChatTimelineState(
                messages = listOf(user, tool, streamingAssistant),
                activeRunId = "run-1",
                activeRunsByTurnId = mapOf("turn-1" to "run-1"),
                activeTurnByRunId = mapOf("run-1" to "turn-1")
            ),
            event(
                """
                {
                  "protocolVersion": 2,
                  "eventId": "final",
                  "eventType": "message.completed",
                  "turnId": "turn-1",
                  "runId": "run-1",
                  "messageId": "assistant-final",
                  "role": "assistant",
                  "content": [{ "type": "text", "text": "Final answer" }]
                }
                """.trimIndent()
            )
        )

        assertEquals(listOf("user-1", "tool-turn-1", "assistant-final"), state.messages.map { it.id })
        assertEquals(1, state.messages.count { it.role == MessageRole.assistant })
        assertEquals("Final answer", state.messages.last().content)
        assertFalse(state.hasActiveRun)
    }

    @Test
    fun historySnapshotPageKeepsDistinctMessagesInSameTurn() {
        val state = ChatTimelineReducer.reduceAll(
            ChatTimelineState(),
            listOf(
                event("""{"protocolVersion":2,"eventId":"h1","eventType":"history.snapshot.page","messages":[{"turnId":"turn-1","messageId":"user-1","role":"user","text":"hello","timelineOrderKey":"v1|00000000000000000001|10|000000|user-1","timelineIdentityKey":"message:user:user-1"}]}"""),
                event("""{"protocolVersion":2,"eventId":"h2","eventType":"history.snapshot.page","messages":[{"turnId":"turn-1","messageId":"assistant-1","role":"assistant","content":[{"type":"text","text":"reply"}],"timelineOrderKey":"v1|00000000000000000001|50|000000|assistant-1","timelineIdentityKey":"message:assistant:assistant-1"}]}""")
            )
        )

        assertEquals(listOf("user-1", "assistant-1"), state.messages.map { it.id })
        assertEquals(listOf("hello", "reply"), state.messages.map { it.content })
        assertEquals(setOf("turn-1"), state.historySnapshotTurnIds)
        assertEquals(setOf("user-1", "assistant-1"), state.historySnapshotMessageIds)
    }

    @Ignore("Legacy assistant fragment collapse by contained text was removed; relay canonical identity is authoritative.")
    @Test
    fun historySnapshotPageCollapsesContainedAssistantTextFragments() {
        val state = ChatTimelineReducer.reduceAll(
            ChatTimelineState(),
            listOf(
                event(
                    """
                    {
                      "protocolVersion": 2,
                      "eventId": "history-page",
                      "eventType": "history.snapshot.page",
                      "messages": [
                        {
                          "turnId": "turn-1",
                          "messageId": "assistant-fragment",
                          "role": "assistant",
                          "createdAt": "1970-01-01T00:03:20.000Z",
                          "content": [{ "type": "text", "text": "我可以帮你直接动手做事，不只是聊天。" }]
                        },
                        {
                          "turnId": "turn-1",
                          "messageId": "assistant-full",
                          "role": "assistant",
                          "createdAt": "1970-01-01T00:03:20.500Z",
                          "content": [{ "type": "text", "text": "我可以帮你直接动手做事，不只是聊天。主要能做这些：\n\n1. 操作你的 Mac" }]
                        }
                      ]
                    }
                    """.trimIndent()
                )
            )
        )
        assertEquals(listOf("assistant-full"), state.messages.map { it.id })
        assertEquals("我可以帮你直接动手做事，不只是聊天。主要能做这些：\n\n1. 操作你的 Mac", state.messages.single().content)
        assertEquals(setOf("assistant-fragment", "assistant-full"), state.historySnapshotMessageIds)
    }

    @Ignore("Legacy createdAt ordering against optimistic local sends was removed.")
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
}
