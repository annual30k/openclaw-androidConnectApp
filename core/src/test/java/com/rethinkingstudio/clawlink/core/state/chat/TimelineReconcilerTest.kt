package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TimelineReconcilerTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    }

    @Test
    fun sharedFixturesProduceExpectedStableKeysAndPendingOverlay() {
        fixtureFiles().forEach { file ->
            val fixture = parseFixture(file)
            var messages = fixture.initialLocal
            var pending = emptyList<ChatMessage>()
            fixture.events.forEach { event ->
                val result = reconcileTimeline(
                    existing = messages,
                    pending = pending,
                    snapshot = event
                )
                messages = result.messages
                pending = result.pending
            }

            val actualStableKeys = debugTimelineDump(messages, fixture.sessionKey)
                .map { it.getValue("stableKey") }
            assertEquals(file.name, fixture.expectedStableKeys, actualStableKeys)
            assertEquals(file.name, fixture.expectedPendingCount, pending.size)
        }
    }

    @Test
    fun repeatedSameTextFixtureKeepsBothTurns() {
        val result = applyFixture("repeated_same_text_two_real_turns.json")
        assertEquals(listOf("user-repeat-1", "user-repeat-2"), result.messages.map { it.id })
    }

    @Test
    fun localEchoFixtureReplacesPendingWithServerAck() {
        val result = applyFixture("local_echo_then_history_snapshot.json")
        assertEquals(listOf("user-client-run-1"), result.messages.map { it.id })
        assertTrue(result.pending.isEmpty())
    }

    @Test
    fun userEchoDoesNotRemoveSameRunWaitingAssistantPlaceholder() {
        val waitingAssistant = ChatMessage(
            id = "assistant-waiting",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "[[clawlink:typing]]",
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = "[[clawlink:typing]]")),
            runId = "run-hi",
            sortTimestamp = 100.001
        )

        val result = reconcileTimeline(
            existing = listOf(waitingAssistant),
            snapshot = TimelineSnapshotPage(
                sessionKey = "main",
                messages = listOf(
                    TimelineSnapshotMessage(
                        messageId = "server-user-hi",
                        role = "user",
                        messageState = "completed",
                        runId = "run-hi",
                        turnId = "run-hi",
                        createdAt = "2026-06-09T17:00:00.000Z",
                        content = listOf(RelayChatContentBlock(type = "text", text = "Hi"))
                    )
                )
            )
        )

        assertEquals(listOf("server-user-hi"), result.messages.map { it.id })
        assertEquals(listOf("assistant-waiting"), result.pending.map { it.id })
    }

    @Test
    fun fileAttachmentFixtureKeepsTextBeforeImageAndMetadata() {
        val result = applyFixture("image_user_then_file_result.json")
        val assistant = result.messages.single { it.id == "assistant-screenshot-result-1" }
        assertEquals(listOf("text", "image"), assistant.contentBlocks.map { it.type })
        val image = assistant.contentBlocks[1]
        assertEquals("att-screenshot-1", image.attachmentId)
        assertEquals(1440, image.imageWidth)
        assertEquals(900, image.imageHeight)
        assertEquals("https://relay.example/thumb/att-screenshot-1", image.thumbnailUrl)
    }

    @Test
    fun attachmentRefreshFixtureUpdatesOneBubbleByAttachmentId() {
        val result = applyFixture("attachment_url_refresh.json")
        assertEquals(1, result.messages.size)
        assertEquals("https://relay.example/fresh", result.messages.single().contentBlocks.single().downloadUrl)
    }

    @Test
    fun clockSkewFixtureOrdersByServerSeq() {
        val result = applyFixture("client_clock_skew_ordering.json")
        assertEquals(listOf("clock-user", "clock-assistant"), result.messages.map { it.id })
    }

    @Test
    fun turnSeqKeepsTranscriptOrderWhenHistorySeqIsMissingAndTimestampsTie() {
        val result = reconcileTimeline(
            existing = emptyList(),
            snapshot = TimelineSnapshotPage(
                sessionKey = "main",
                messages = listOf(
                    TimelineSnapshotMessage(messageId = "u1", turnSeq = 1, role = "user", messageState = "completed", createdAt = "2026-06-07T12:00:00.000Z", content = listOf(RelayChatContentBlock(type = "text", text = "first question"))),
                    TimelineSnapshotMessage(messageId = "a1", turnSeq = 2, role = "assistant", messageState = "completed", createdAt = "2026-06-07T12:00:00.000Z", content = listOf(RelayChatContentBlock(type = "text", text = "first answer"))),
                    TimelineSnapshotMessage(messageId = "u2", turnSeq = 3, role = "user", messageState = "completed", createdAt = "2026-06-07T12:00:00.000Z", content = listOf(RelayChatContentBlock(type = "text", text = "second question"))),
                    TimelineSnapshotMessage(messageId = "a2", turnSeq = 4, role = "assistant", messageState = "completed", createdAt = "2026-06-07T12:00:00.000Z", content = listOf(RelayChatContentBlock(type = "text", text = "second answer")))
                )
            )
        )

        assertEquals(listOf("u1", "a1", "u2", "a2"), result.messages.map { it.id })
    }

    @Test
    fun mixedSeqDomainsUseCreatedAtOrder() {
        val result = reconcileTimeline(
            existing = emptyList(),
            snapshot = TimelineSnapshotPage(
                sessionKey = "main",
                messages = listOf(
                    TimelineSnapshotMessage(messageId = "user-1", seq = 1_780_769_608_135, role = "user", messageState = "completed", createdAt = "2026-06-06T18:13:28.135Z", content = listOf(RelayChatContentBlock(type = "text", text = "send screenshot"))),
                    TimelineSnapshotMessage(messageId = "user-2", seq = 1_780_769_715_297, role = "user", messageState = "completed", createdAt = "2026-06-06T18:15:15.297Z", content = listOf(RelayChatContentBlock(type = "text", text = "send file"))),
                    TimelineSnapshotMessage(messageId = "assistant-1", seq = 1_780_769_635_618_000, role = "assistant", messageState = "completed", createdAt = "2026-06-06T18:13:55.618Z", content = listOf(RelayChatContentBlock(type = "text", text = "done"))),
                    TimelineSnapshotMessage(messageId = "assistant-2", seq = 1_780_769_773_636_000, role = "assistant", messageState = "completed", createdAt = "2026-06-06T18:16:13.636Z", content = listOf(RelayChatContentBlock(type = "text", text = "sent")))
                )
            )
        )

        assertEquals(listOf("user-1", "assistant-1", "user-2", "assistant-2"), result.messages.map { it.id })
    }

    @Test
    fun serverMessageIdAndConversationSeqAreAuthoritative() {
        val result = reconcileTimeline(
            existing = emptyList(),
            snapshot = TimelineSnapshotPage(
                sessionKey = "main",
                messages = listOf(
                    TimelineSnapshotMessage(
                        serverMessageId = "srv_user_1",
                        conversationSeq = 1,
                        messageId = "history-user-1",
                        seq = 1_781_000_000_000_000,
                        turnSeq = 1,
                        role = "user",
                        messageState = "completed",
                        runId = "client-run-1",
                        turnId = "client-run-1",
                        partId = "part-text-1",
                        clientMessageId = "client-run-1",
                        idempotencyKey = "client-run-1",
                        createdAt = "2026-06-09T08:00:10.000Z",
                        content = listOf(RelayChatContentBlock(type = "text", text = "把桌面图片发过来"))
                    ),
                    TimelineSnapshotMessage(
                        serverMessageId = "srv_assistant_1",
                        conversationSeq = 2,
                        messageId = "history-assistant-1",
                        seq = 10,
                        turnSeq = 2,
                        role = "assistant",
                        messageState = "completed",
                        runId = "client-run-1",
                        turnId = "client-run-1",
                        partId = "part-text-1",
                        createdAt = "2026-06-09T08:00:00.000Z",
                        content = listOf(RelayChatContentBlock(type = "text", text = "收到"))
                    )
                )
            )
        )

        assertEquals(listOf("srv_user_1", "srv_assistant_1"), result.messages.map { it.id })
        assertEquals(listOf(1L, 2L), result.messages.map { it.seq })
    }

    @Test
    fun sameRunUserToolAssistantOrderWinsOverRegressedConversationSeq() {
        val runId = "23F791B4-97CF-4CF5-BD00-21D947671505"
        val result = reconcileTimeline(
            existing = emptyList(),
            snapshot = TimelineSnapshotPage(
                sessionKey = "main",
                messages = listOf(
                    TimelineSnapshotMessage(
                        messageId = "user-run",
                        conversationSeq = 6,
                        role = "user",
                        messageState = "completed",
                        runId = runId,
                        turnId = runId,
                        createdAt = "2026-06-11T06:45:18.574Z",
                        content = listOf(RelayChatContentBlock(type = "text", text = "你好啊"))
                    ),
                    TimelineSnapshotMessage(
                        messageId = "tool-run",
                        conversationSeq = 4,
                        role = "tool",
                        messageState = "completed",
                        runId = runId,
                        turnId = runId,
                        createdAt = "2026-06-11T06:45:32.709Z",
                        content = listOf(RelayChatContentBlock(type = "tool_result", text = "memory_search", name = "memory_search", toolCallId = "call_1"))
                    ),
                    TimelineSnapshotMessage(
                        messageId = "assistant-run",
                        conversationSeq = 5,
                        role = "assistant",
                        messageState = "completed",
                        runId = runId,
                        turnId = runId,
                        createdAt = "2026-06-11T06:45:39.080Z",
                        content = listOf(RelayChatContentBlock(type = "text", text = "你好 Alex！👋 有什么需要帮忙的吗？"))
                    )
                )
            )
        )

        assertEquals(listOf("user-run", "tool-run", "assistant-run"), result.messages.map { it.id })
    }

    @Test
    fun resetOrdinalSeqAcrossDistantTimestampsUsesCreatedAtOrder() {
        val result = reconcileTimeline(
            existing = emptyList(),
            snapshot = TimelineSnapshotPage(
                sessionKey = "main",
                messages = listOf(
                    TimelineSnapshotMessage(
                        messageId = "new-user",
                        seq = 1,
                        role = "user",
                        messageState = "completed",
                        createdAt = "2026-06-09T08:00:00.000Z",
                        content = listOf(RelayChatContentBlock(type = "text", text = "new question"))
                    ),
                    TimelineSnapshotMessage(
                        messageId = "old-assistant",
                        seq = 2,
                        role = "assistant",
                        messageState = "completed",
                        createdAt = "2026-06-08T08:00:00.000Z",
                        content = listOf(RelayChatContentBlock(type = "text", text = "old answer"))
                    )
                )
            )
        )

        assertEquals(listOf("old-assistant", "new-user"), result.messages.map { it.id })
    }

    @Test
    fun convertedSnapshotMessagesKeepTurnSeqForLaterResort() {
        val createdAt = "2026-06-09T08:00:00.000Z"
        val first = timelineSnapshotMessageToChatMessage(
            TimelineSnapshotMessage(
                messageId = "z-first-assistant",
                turnSeq = 1,
                role = "assistant",
                messageState = "completed",
                createdAt = createdAt,
                content = listOf(RelayChatContentBlock(type = "text", text = "first"))
            )
        )
        val second = timelineSnapshotMessageToChatMessage(
            TimelineSnapshotMessage(
                messageId = "a-second-assistant",
                turnSeq = 2,
                role = "assistant",
                messageState = "completed",
                createdAt = createdAt,
                content = listOf(RelayChatContentBlock(type = "text", text = "second"))
            )
        )

        val ordered = sortTimelineMessagesV3(listOf(second, first))

        assertEquals(listOf("z-first-assistant", "a-second-assistant"), ordered.map { it.id })
    }

    @Test
    fun historySnapshotCollapsesLiveAssistantDuplicateWithDifferentId() {
        val existing = listOf(
            ChatMessage(
                id = "assistant-DA69CD14-756A-4114-9B81-E43686555BD4",
                role = MessageRole.assistant,
                state = MessageState.completed,
                content = "pong 1407",
                contentBlocks = listOf(RelayChatContentBlock(type = "text", text = "pong 1407")),
                createdAt = "2026-06-09T08:00:00.000Z",
                sortTimestamp = 1780992000.0
            )
        )
        val result = reconcileTimeline(
            existing = existing,
            snapshot = TimelineSnapshotPage(
                sessionKey = "main",
                messages = listOf(
                    TimelineSnapshotMessage(
                        messageId = "history:assistant-1407",
                        role = "assistant",
                        messageState = "completed",
                        createdAt = "2026-06-09T08:00:02.000Z",
                        content = listOf(RelayChatContentBlock(type = "text", text = "pong 1407"))
                    )
                )
            )
        )

        assertEquals(listOf("history:assistant-1407"), result.messages.map { it.id })
    }

    @Test
    fun canonicalTimelineKeepsLocalUserBeforeMatchingPendingAssistantWhenUserTimestampMovesLater() {
        val messages = sortTimelineMessagesV3(
            listOf(
                ChatMessage(
                    id = "assistant-pending",
                    role = MessageRole.assistant,
                    state = MessageState.streaming,
                    content = "正在连接 Relay...",
                    contentBlocks = listOf(RelayChatContentBlock(type = "text", text = "正在连接 Relay...")),
                    createdAt = "2026-06-07T12:00:00.000Z",
                    runId = "client-run-1",
                    sortTimestamp = 100.0
                ),
                ChatMessage(
                    id = "local-user",
                    role = MessageRole.user,
                    state = MessageState.completed,
                    content = "你好啊",
                    contentBlocks = listOf(RelayChatContentBlock(type = "text", text = "你好啊")),
                    createdAt = "2026-06-07T12:00:00.100Z",
                    runId = "local-user-client-run-1",
                    sortTimestamp = 110.0
                )
            )
        )

        assertEquals(listOf("local-user", "assistant-pending"), messages.map { it.id })
    }

    @Test
    fun canonicalTimelineKeepsLocalUserBeforeMatchingStreamingAssistantTextWhenUserTimestampMovesLater() {
        val messages = sortTimelineMessagesV3(
            listOf(
                ChatMessage(
                    id = "assistant-streaming-text",
                    role = MessageRole.assistant,
                    state = MessageState.streaming,
                    content = "好的，有需要随时找我",
                    contentBlocks = listOf(RelayChatContentBlock(type = "text", text = "好的，有需要随时找我")),
                    createdAt = "2026-06-07T12:00:00.000Z",
                    runId = "client-run-streaming-text",
                    sortTimestamp = 100.0
                ),
                ChatMessage(
                    id = "local-user-streaming-text",
                    role = MessageRole.user,
                    state = MessageState.completed,
                    content = "No",
                    contentBlocks = listOf(RelayChatContentBlock(type = "text", text = "No")),
                    createdAt = "2026-06-07T12:00:00.100Z",
                    runId = "local-user-client-run-streaming-text",
                    sortTimestamp = 110.0
                )
            )
        )

        assertEquals(listOf("local-user-streaming-text", "assistant-streaming-text"), messages.map { it.id })
    }

    @Test
    fun realtimeSeqKeepsInterleavedSendOrderWhenTimestampsTie() {
        val createdAt = "2026-06-08T12:00:00.000Z"
        val reduced = ChatTimelineReducer.reduceAll(
            ChatTimelineState(),
            listOf(
                TimelineEvent.TurnUserCreated(
                    eventId = "evt-user-1",
                    turnId = "turn-1",
                    runId = "turn-1",
                    messageId = "user-1",
                    content = listOf(RelayChatContentBlock(type = "text", text = "第一问")),
                    createdAt = createdAt,
                    seq = 1,
                    turnSeq = 1
                ),
                TimelineEvent.MessageCompleted(
                    eventId = "evt-assistant-1",
                    turnId = "turn-1",
                    messageId = "assistant-1",
                    role = "assistant",
                    runId = "turn-1",
                    content = listOf(RelayChatContentBlock(type = "text", text = "第一答")),
                    createdAt = createdAt,
                    seq = 2,
                    turnSeq = 2
                ),
                TimelineEvent.TurnUserCreated(
                    eventId = "evt-user-2",
                    turnId = "turn-2",
                    runId = "turn-2",
                    messageId = "user-2",
                    content = listOf(RelayChatContentBlock(type = "text", text = "第二问")),
                    createdAt = createdAt,
                    seq = 3,
                    turnSeq = 3
                ),
                TimelineEvent.MessageCompleted(
                    eventId = "evt-assistant-2",
                    turnId = "turn-2",
                    messageId = "assistant-2",
                    role = "assistant",
                    runId = "turn-2",
                    content = listOf(RelayChatContentBlock(type = "text", text = "第二答")),
                    createdAt = createdAt,
                    seq = 4,
                    turnSeq = 4
                )
            )
        )
        val ordered = sortTimelineMessagesV3(reduced.messages)

        assertEquals(listOf(1L, 2L, 3L, 4L), ordered.map { it.seq })
        assertEquals(listOf(MessageRole.user, MessageRole.assistant, MessageRole.user, MessageRole.assistant), ordered.map { it.role })
        assertEquals(listOf("第一问", "第一答", "第二问", "第二答"), ordered.map { it.content })
    }

    @Test
    fun sameRunAndPartWithDifferentRolesRemainSeparateMessages() {
        val result = reconcileTimeline(
            existing = emptyList(),
            snapshot = TimelineSnapshotPage(
                sessionKey = "main",
                messages = listOf(
                    TimelineSnapshotMessage(
                        messageId = "user-1",
                        seq = 1,
                        runId = "run-1",
                        partId = "part-text-1",
                        role = "user",
                        messageState = "completed",
                        createdAt = "2026-06-07T12:00:00.000Z",
                        content = listOf(RelayChatContentBlock(type = "text", text = "look"))
                    ),
                    TimelineSnapshotMessage(
                        messageId = "assistant-1",
                        seq = 2,
                        runId = "run-1",
                        partId = "part-text-1",
                        role = "assistant",
                        messageState = "completed",
                        createdAt = "2026-06-07T12:00:01.000Z",
                        content = listOf(RelayChatContentBlock(type = "text", text = "ok"))
                    )
                )
            )
        )

        assertEquals(listOf("user-1", "assistant-1"), result.messages.map { it.id })
    }

    @Test
    fun historyAttachmentReplacesLocalAttachmentWithSameAttachmentId() {
        val existing = listOf(
            ChatMessage(
                id = "local-file",
                role = MessageRole.user,
                content = "photo.jpg",
                contentBlocks = listOf(
                    RelayChatContentBlock(
                        type = "image",
                        attachmentId = "att-local",
                        fileName = "photo.jpg",
                        downloadUrl = "file:///tmp/photo.jpg"
                    )
                ),
                createdAt = "2026-06-07T12:00:00.000Z",
                runId = "file-att-local",
                sortTimestamp = 100.0
            )
        )
        val result = reconcileTimeline(
            existing = existing,
            snapshot = TimelineSnapshotPage(
                sessionKey = "main",
                rangeStartCursor = "seq:1",
                rangeEndCursor = "seq:4",
                messages = listOf(
                    TimelineSnapshotMessage(
                        messageId = "history-file",
                        seq = 3,
                        role = "user",
                        messageState = "completed",
                        createdAt = "2026-06-07T12:00:03.000Z",
                        content = listOf(
                            RelayChatContentBlock(
                                type = "image",
                                attachmentId = "att-local",
                                fileName = "photo.jpg",
                                downloadUrl = "/api/mobile/files/att-local"
                            )
                        ),
                        attachmentIds = listOf("att-local")
                    )
                )
            )
        )

        assertEquals(listOf("history-file"), result.messages.map { it.id })
        assertEquals("/api/mobile/files/att-local", result.messages.single().contentBlocks.single().downloadUrl)
    }

    private fun applyFixture(name: String): TimelineReconcileResult {
        val fixture = parseFixture(File(fixtureDir(), name))
        var messages = fixture.initialLocal
        var pending = emptyList<ChatMessage>()
        fixture.events.forEach { event ->
            val result = reconcileTimeline(messages, pending, event)
            messages = result.messages
            pending = result.pending
        }
        return TimelineReconcileResult(messages, pending)
    }

    private fun parseFixture(file: File): FixtureCase {
        val root = json.parseToJsonElement(file.readText()).jsonObject
        val sessionKey = root["events"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("sessionKey")
            ?.jsonPrimitive
            ?.contentOrNull
            ?: "main"
        val initialLocal = (root["initialLocal"] as? JsonArray)
            ?.map { element ->
                timelineSnapshotMessageToChatMessage(
                    json.decodeFromJsonElement<TimelineSnapshotMessage>(element),
                    sessionKey
                )
            }
            ?: emptyList()
        val events = root["events"]!!.jsonArray.map { element ->
            json.decodeFromJsonElement<TimelineSnapshotPage>(element)
        }
        val expectedMessages = root["expectedMessages"]!!.jsonArray.map { it.jsonObject }
        val expectedStableKeys = expectedMessages.mapNotNull { expected ->
            expected["stableKey"]?.jsonPrimitive?.contentOrNull
        }
        val expectedPendingCount = root["expectedPending"]?.jsonArray?.size ?: 0
        return FixtureCase(
            sessionKey = sessionKey,
            initialLocal = initialLocal,
            events = events,
            expectedStableKeys = expectedStableKeys,
            expectedPendingCount = expectedPendingCount
        )
    }

    private fun fixtureFiles(): List<File> {
        return fixtureDir()
            .listFiles { file -> file.extension == "json" }
            .orEmpty()
            .sortedBy { it.name }
    }

    private fun fixtureDir(): File {
        var current: File? = File(System.getProperty("user.dir") ?: "").canonicalFile
        while (current != null) {
            val candidate = File(current, "docs/superpowers/fixtures/timeline")
            if (candidate.isDirectory) return candidate
            current = current.parentFile
        }
        error("Unable to locate docs/superpowers/fixtures/timeline from ${System.getProperty("user.dir")}")
    }

    private data class FixtureCase(
        val sessionKey: String,
        val initialLocal: List<ChatMessage>,
        val events: List<TimelineSnapshotPage>,
        val expectedStableKeys: List<String>,
        val expectedPendingCount: Int
    )
}
