package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

class TimelineReconcilerTest {
    @Test
    fun localPendingTurnsKeepEachUserAnchoredToItsOwnAssistantPlaceholder() {
        val firstUser = ChatMessage(
            id = "user-first-run",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "1111",
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = "1111")),
            runId = "local-user-first-run",
            sortTimestamp = 100.0,
            timelineOrderKey = "local:first-run|10|user-first-run",
            timelineIdentityKey = "local:message:user:first-run",
            timelineItemKind = "message:user"
        )
        val firstWaiting = ChatMessage(
            id = "assistant-first-run",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = protocolTypingMarkerText,
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = protocolTypingMarkerText)),
            runId = "first-run",
            sortTimestamp = 100.001,
            timelineOrderKey = "local:first-run|20|assistant-first-run",
            timelineIdentityKey = "local:waiting:first-run",
            timelineItemKind = "waiting"
        )
        val secondUser = ChatMessage(
            id = "user-second-run",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "2222",
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = "2222")),
            runId = "local-user-second-run",
            sortTimestamp = 101.0,
            timelineOrderKey = "local:second-run|10|user-second-run",
            timelineIdentityKey = "local:message:user:second-run",
            timelineItemKind = "message:user"
        )
        val secondWaiting = ChatMessage(
            id = "assistant-second-run",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = protocolTypingMarkerText,
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = protocolTypingMarkerText)),
            runId = "second-run",
            sortTimestamp = 101.001,
            timelineOrderKey = "local:second-run|20|assistant-second-run",
            timelineIdentityKey = "local:waiting:second-run",
            timelineItemKind = "waiting"
        )

        val ordered = sortTimelineMessagesV3(
            listOf(firstUser, firstWaiting, secondUser, secondWaiting)
        )

        assertEquals(
            listOf("user-first-run", "assistant-first-run", "user-second-run", "assistant-second-run"),
            ordered.map { it.id }
        )
    }

    @Ignore("Legacy fixture expectations without canonical order keys were removed.")
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
    fun canonicalToolAssistantFixtureObeysRelayOrderKey() {
        val result = applyFixture("canonical_tool_assistant_authority.json")

        assertEquals(listOf("assistant-authority", "tool-authority"), result.messages.map { it.id })
    }

    @Test
    fun canonicalTimelineUpsertsOnlyByIdentityAndSortsOnlyByOrderKey() {
        val existing = listOf(
            ChatMessage(
                id = "old-message-id",
                role = MessageRole.assistant,
                state = MessageState.completed,
                content = "old",
                contentBlocks = listOf(RelayChatContentBlock(type = "text", text = "old")),
                createdAt = "2026-06-09T08:00:00.000Z",
                seq = 99,
                timelineOrderKey = "0002",
                timelineIdentityKey = "identity-assistant",
                timelineItemKind = "message"
            )
        )

        val result = reconcileTimeline(
            existing = existing,
            snapshot = TimelineSnapshotPage(
                sessionKey = "main",
                messages = listOf(
                    TimelineSnapshotMessage(
                        messageId = "later-created-first",
                        seq = 2,
                        role = "assistant",
                        messageState = "completed",
                        createdAt = "2026-06-09T08:00:10.000Z",
                        content = listOf(RelayChatContentBlock(type = "text", text = "first by order")),
                        timelineOrderKey = "0001",
                        timelineIdentityKey = "identity-first",
                        timelineItemKind = "message"
                    ),
                    TimelineSnapshotMessage(
                        messageId = "new-message-id",
                        seq = 1,
                        role = "assistant",
                        messageState = "completed",
                        createdAt = "2026-06-09T08:00:00.000Z",
                        content = listOf(RelayChatContentBlock(type = "text", text = "updated")),
                        timelineOrderKey = "0002",
                        timelineIdentityKey = "identity-assistant",
                        timelineItemKind = "message"
                    )
                )
            )
        )

        assertEquals(listOf("later-created-first", "new-message-id"), result.messages.map { it.id })
        assertEquals(listOf("first by order", "updated"), result.messages.map { it.content })
        assertTrue(result.pending.isEmpty())
    }

    @Test
    fun fullCanonicalSnapshotDoesNotRetainExistingConfirmedMessagesMissingFromRelay() {
        val stale = ChatMessage(
            id = "stale-history",
            role = MessageRole.assistant,
            state = MessageState.completed,
            content = "stale",
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = "stale")),
            createdAt = "2026-06-09T08:00:00.000Z",
            seq = 1,
            timelineOrderKey = "0001",
            timelineIdentityKey = "identity-stale",
            timelineItemKind = "message:assistant"
        )
        val pending = ChatMessage(
            id = "assistant-waiting",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "[[clawlink:typing]]",
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = "[[clawlink:typing]]")),
            runId = "run-new",
            sortTimestamp = 100.001,
            timelineOrderKey = "local:run-new:20:assistant-waiting",
            timelineIdentityKey = "local:waiting:run-new",
            timelineItemKind = "waiting"
        )

        val result = reconcileTimeline(
            existing = listOf(stale, pending),
            snapshot = TimelineSnapshotPage(
                sessionKey = "main",
                messages = listOf(
                    TimelineSnapshotMessage(
                        messageId = "server-user",
                        role = "user",
                        messageState = "completed",
                        runId = "run-new",
                        createdAt = "2026-06-09T08:00:10.000Z",
                        content = listOf(RelayChatContentBlock(type = "text", text = "fresh")),
                        timelineOrderKey = "0002",
                        timelineIdentityKey = "identity-server-user",
                        timelineItemKind = "message:user"
                    )
                )
            )
        )

        assertEquals(listOf("server-user"), result.messages.map { it.id })
        assertEquals(listOf("assistant-waiting"), result.pending.map { it.id })
    }

    @Test
    fun canonicalSnapshotResolvesLocalUserWhenHostAddsRoleSuffixToRunIdentity() {
        val clientRunId = "66794ce4-6664-4581-88bd-ae57d27f5782"
        val localUser = ChatMessage(
            id = "user-$clientRunId",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "Send desktop screenshot to my phone test 1053",
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = "Send desktop screenshot to my phone test 1053")),
            createdAt = "",
            runId = "local-user-$clientRunId",
            sortTimestamp = 1_782_185_590.0,
            timelineOrderKey = "local:$clientRunId:010-user",
            timelineIdentityKey = "local:$clientRunId:message:user:010-user",
            timelineItemKind = "message:user"
        )
        val localAssistant = ChatMessage(
            id = "assistant-$clientRunId",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = protocolTypingMarkerText,
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = protocolTypingMarkerText)),
            createdAt = "",
            runId = clientRunId,
            sortTimestamp = 1_782_185_590.001,
            timelineOrderKey = "local:$clientRunId:020-waiting",
            timelineIdentityKey = "local:$clientRunId:message:waiting:020-waiting",
            timelineItemKind = "message:assistant"
        )

        val result = reconcileTimeline(
            existing = listOf(localUser, localAssistant),
            snapshot = TimelineSnapshotPage(
                sessionKey = "ios-session",
                messages = listOf(
                    TimelineSnapshotMessage(
                        messageId = "987948be",
                        seq = 1009,
                        turnSeq = 1,
                        turnId = "$clientRunId:user",
                        runId = "$clientRunId:user",
                        idempotencyKey = "$clientRunId:user",
                        role = "user",
                        messageState = "completed",
                        createdAt = "2026-06-23T02:53:13.000Z",
                        content = listOf(RelayChatContentBlock(type = "text", text = "Send desktop screenshot to my phone test 1053")),
                        timelineOrderKey = "v1|00000001782185593000|10|0000000000001009:part-text-1:987948be|user",
                        timelineIdentityKey = "ios-session:$clientRunId:user:message:user:987948be",
                        timelineItemKind = "message:user"
                    )
                )
            )
        )

        val visible = sortTimelineMessagesV3(result.messages + result.pending, "ios-session")
        assertEquals(listOf("987948be", "assistant-$clientRunId"), visible.map { it.id })
        assertEquals(1, visible.count { it.role == MessageRole.user })
        assertEquals("Send desktop screenshot to my phone test 1053", visible.first().content)
        assertEquals(listOf("assistant-$clientRunId"), result.pending.map { it.id })
    }

    @Test
    fun canonicalSnapshotReplacesLocalWaitingWhenStreamingAssistantRunHasRoleSuffix() {
        val clientRunId = "client-run-stream-suffix"
        val localUser = ChatMessage(
            id = "local-user-stream-suffix",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "帮我分析一下这张图",
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = "帮我分析一下这张图")),
            createdAt = "2026-06-29T09:07:30.000Z",
            runId = "local-user-$clientRunId",
            sortTimestamp = 1_782_704_850.0,
            timelineOrderKey = "local:$clientRunId:010-user",
            timelineIdentityKey = "local:$clientRunId:message:user:010-user",
            timelineItemKind = "message:user"
        )
        val waiting = ChatMessage(
            id = "assistant-local-waiting",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = protocolTypingMarkerText,
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = protocolTypingMarkerText)),
            createdAt = "2026-06-29T09:07:30.001Z",
            runId = clientRunId,
            sortTimestamp = 1_782_704_850.001,
            timelineOrderKey = "local:$clientRunId:020-waiting",
            timelineIdentityKey = "local:$clientRunId:waiting:020-waiting",
            timelineItemKind = "waiting"
        )

        val result = reconcileTimeline(
            existing = listOf(localUser, waiting),
            snapshot = TimelineSnapshotPage(
                sessionKey = "main",
                messages = listOf(
                    TimelineSnapshotMessage(
                        messageId = "assistant-stream-suffix",
                        seq = 42,
                        turnSeq = 42,
                        turnId = "$clientRunId:assistant",
                        runId = "$clientRunId:assistant",
                        role = "assistant",
                        messageState = "streaming",
                        createdAt = "2026-06-29T09:07:30.050Z",
                        content = listOf(RelayChatContentBlock(type = "text", text = protocolTypingMarkerText)),
                        timelineOrderKey = "v1|00000000000000000042|20|0000000000000042:part-text-1:assistant-stream-suffix",
                        timelineIdentityKey = "v1|main|message|assistant|assistant-stream-suffix",
                        timelineItemKind = "message:assistant"
                    )
                )
            )
        )

        val visible = sortTimelineMessagesV3(result.messages + result.pending, "main")
        assertEquals(listOf("local-user-stream-suffix", "assistant-stream-suffix"), visible.map { it.id })
        assertEquals(1, visible.count { it.role == MessageRole.assistant && it.state == MessageState.streaming })
    }

    @Test
    fun fullCanonicalSnapshotKeepsLocalUserForCurrentPendingTurnOnly() {
        val staleLocalUser = ChatMessage(
            id = "local-stale",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "stale",
            runId = "local-user-stale-run"
        )
        val currentLocalUser = ChatMessage(
            id = "local-current",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "current",
            runId = "local-user-current-run"
        )
        val waiting = ChatMessage(
            id = "assistant-waiting",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "[[clawlink:typing]]",
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = "[[clawlink:typing]]")),
            runId = "current-run"
        )

        val result = reconcileTimeline(
            existing = listOf(staleLocalUser, currentLocalUser, waiting),
            snapshot = TimelineSnapshotPage(sessionKey = "main", messages = emptyList())
        )

        assertTrue(result.messages.isEmpty())
        assertEquals(listOf("local-current", "assistant-waiting"), result.pending.map { it.id })
    }

    @Test
    fun boundedCanonicalSnapshotRetainsExistingMessagesOutsideFetchedRange() {
        val older = ChatMessage(
            id = "older",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "older",
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = "older")),
            createdAt = "2026-06-09T08:00:00.000Z",
            seq = 1,
            timelineOrderKey = "0001",
            timelineIdentityKey = "identity-older",
            timelineItemKind = "message:user"
        )
        val inRangeStale = ChatMessage(
            id = "in-range-stale",
            role = MessageRole.assistant,
            state = MessageState.completed,
            content = "stale",
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = "stale")),
            createdAt = "2026-06-09T08:00:01.000Z",
            seq = 2,
            timelineOrderKey = "0002",
            timelineIdentityKey = "identity-stale",
            timelineItemKind = "message:assistant"
        )

        val result = reconcileTimeline(
            existing = listOf(older, inRangeStale),
            snapshot = TimelineSnapshotPage(
                sessionKey = "main",
                rangeStartCursor = "seq:2",
                rangeEndCursor = "seq:4",
                messages = listOf(
                    TimelineSnapshotMessage(
                        messageId = "fresh",
                        seq = 2,
                        role = "assistant",
                        messageState = "completed",
                        createdAt = "2026-06-09T08:00:02.000Z",
                        content = listOf(RelayChatContentBlock(type = "text", text = "fresh")),
                        timelineOrderKey = "0002",
                        timelineIdentityKey = "identity-fresh",
                        timelineItemKind = "message:assistant"
                    )
                )
            )
        )

        assertEquals(listOf("older", "fresh"), result.messages.map { it.id })
    }

    @Test
    fun canonicalToolAndNonResolvingAttachmentDoNotClearWaitingButAssistantTextDoes() {
        val waitingAssistant = ChatMessage(
            id = "assistant-waiting",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "正在同步回复...",
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = "正在同步回复...")),
            runId = "run-tool",
            sortTimestamp = 100.001
        )

        val toolOnly = reconcileTimeline(
            existing = listOf(waitingAssistant),
            snapshot = TimelineSnapshotPage(
                sessionKey = "main",
                messages = listOf(
                    TimelineSnapshotMessage(
                        messageId = "tool-1",
                        role = "tool",
                        messageState = "completed",
                        runId = "run-tool",
                        createdAt = "2026-06-09T08:00:00.000Z",
                        content = listOf(RelayChatContentBlock(type = "tool_result", text = "tool output", toolCallId = "call-1")),
                        timelineOrderKey = "0001",
                        timelineIdentityKey = "identity-tool-1",
                        timelineItemKind = "tool"
                    )
                )
            )
        )

        assertEquals(listOf("tool-1"), toolOnly.messages.map { it.id })
        assertEquals(listOf("assistant-waiting"), toolOnly.pending.map { it.id })

        val attachmentResult = reconcileTimeline(
            existing = listOf(waitingAssistant),
            snapshot = TimelineSnapshotPage(
                sessionKey = "main",
                messages = listOf(
                    TimelineSnapshotMessage(
                        messageId = "image-1",
                        role = "assistant",
                        messageState = "completed",
                        runId = "run-tool",
                        createdAt = "2026-06-09T08:00:00.000Z",
                        content = listOf(
                            RelayChatContentBlock(
                                type = "image",
                                attachmentId = "att-image-1",
                                fileName = "result.png",
                                mimeType = "image/png",
                                downloadUrl = "https://relay.example/files/att-image-1"
                            )
                        ),
                        timelineOrderKey = "0001",
                        timelineIdentityKey = "identity-image-1",
                        timelineItemKind = "attachment",
                        timelineResolvesWaiting = false
                    )
                )
            )
        )

        assertEquals(listOf("image-1"), attachmentResult.messages.map { it.id })
        assertEquals(listOf("assistant-waiting"), attachmentResult.pending.map { it.id })

        val resolvingAttachmentResult = reconcileTimeline(
            existing = listOf(waitingAssistant),
            snapshot = TimelineSnapshotPage(
                sessionKey = "main",
                messages = listOf(
                    TimelineSnapshotMessage(
                        messageId = "image-2",
                        role = "assistant",
                        messageState = "completed",
                        runId = "run-tool",
                        createdAt = "2026-06-09T08:00:00.000Z",
                        content = listOf(
                            RelayChatContentBlock(
                                type = "image",
                                attachmentId = "att-image-2",
                                fileName = "result.png",
                                mimeType = "image/png",
                                downloadUrl = "https://relay.example/files/att-image-2"
                            )
                        ),
                        timelineOrderKey = "0001",
                        timelineIdentityKey = "identity-image-2",
                        timelineItemKind = "attachment",
                        timelineResolvesWaiting = true
                    )
                )
            )
        )

        assertEquals(listOf("image-2"), resolvingAttachmentResult.messages.map { it.id })
        assertTrue(resolvingAttachmentResult.pending.isEmpty())

        val assistantResult = reconcileTimeline(
            existing = listOf(waitingAssistant),
            snapshot = TimelineSnapshotPage(
                sessionKey = "main",
                messages = listOf(
                    TimelineSnapshotMessage(
                        messageId = "assistant-final",
                        role = "assistant",
                        messageState = "completed",
                        runId = "run-tool",
                        createdAt = "2026-06-09T08:00:01.000Z",
                        content = listOf(RelayChatContentBlock(type = "text", text = "final answer")),
                        timelineOrderKey = "0002",
                        timelineIdentityKey = "identity-assistant-final",
                        timelineItemKind = "message"
                    )
                )
            )
        )

        assertEquals(listOf("assistant-final"), assistantResult.messages.map { it.id })
        assertTrue(assistantResult.pending.isEmpty())
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
                        content = listOf(RelayChatContentBlock(type = "text", text = "Hi")),
                        timelineOrderKey = "0001",
                        timelineIdentityKey = "main:message:server-user-hi",
                        timelineItemKind = "message:user"
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

    @Ignore("Legacy seq ordering without canonical order keys is no longer supported.")
    @Test
    fun clockSkewFixtureOrdersByServerSeq() {
        val result = applyFixture("client_clock_skew_ordering.json")
        assertEquals(listOf("clock-user", "clock-assistant"), result.messages.map { it.id })
    }

    @Ignore("Legacy turnSeq ordering without canonical order keys is no longer supported.")
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

    @Ignore("Legacy seq-domain createdAt ordering was removed; Relay canonical order is required.")
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

    @Ignore("Legacy conversationSeq-domain createdAt ordering was removed; Relay canonical order is required.")
    @Test
    fun mixedConversationSeqDomainsUseCreatedAtOrder() {
        val result = reconcileTimeline(
            existing = emptyList(),
            snapshot = TimelineSnapshotPage(
                sessionKey = "main",
                messages = listOf(
                    TimelineSnapshotMessage(
                        messageId = "history-screenshot",
                        conversationSeq = 8,
                        seq = 8,
                        turnSeq = 8,
                        role = "assistant",
                        messageState = "completed",
                        createdAt = "2026-06-07T12:18:34.773Z",
                        content = listOf(RelayChatContentBlock(type = "text", text = "截图好了，发给你"))
                    ),
                    TimelineSnapshotMessage(
                        messageId = "user-capabilities",
                        conversationSeq = 1_780_834_382_099_000,
                        seq = 1_780_834_382_099_000,
                        turnSeq = 1_780_834_382_099_000,
                        role = "user",
                        messageState = "completed",
                        createdAt = "2026-06-07T12:13:02.099Z",
                        content = listOf(RelayChatContentBlock(type = "text", text = "你可以做什么"))
                    ),
                    TimelineSnapshotMessage(
                        messageId = "assistant-capabilities",
                        conversationSeq = 1_780_834_382_107_001,
                        seq = 1_780_834_382_107_001,
                        turnSeq = 1_780_834_382_107_001,
                        role = "assistant",
                        messageState = "completed",
                        createdAt = "2026-06-07T12:13:02.107Z",
                        content = listOf(RelayChatContentBlock(type = "text", text = "我可以帮你做很多实际操作型任务。"))
                    )
                )
            )
        )

        assertEquals(
            listOf("user-capabilities", "assistant-capabilities", "history-screenshot"),
            result.messages.map { it.id }
        )
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
                        content = listOf(RelayChatContentBlock(type = "text", text = "把桌面图片发过来")),
                        timelineOrderKey = "0001",
                        timelineIdentityKey = "main:message:srv_user_1",
                        timelineItemKind = "message:user"
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
                        content = listOf(RelayChatContentBlock(type = "text", text = "收到")),
                        timelineOrderKey = "0002",
                        timelineIdentityKey = "main:message:srv_assistant_1",
                        timelineItemKind = "message:assistant"
                    )
                )
            )
        )

        assertEquals(listOf("srv_user_1", "srv_assistant_1"), result.messages.map { it.id })
        assertEquals(listOf(1L, 2L), result.messages.map { it.seq })
    }

    @Ignore("Legacy same-run ordering without canonical keys was removed; Relay canonical order is required.")
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

    @Ignore("Legacy ordinal seq reset ordering was removed; Relay canonical order is required.")
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

    @Ignore("Legacy turnSeq resort without canonical order keys is no longer supported.")
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

    @Ignore("Legacy realtime seq ordering without canonical order keys is no longer supported.")
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
                        content = listOf(RelayChatContentBlock(type = "text", text = "look")),
                        timelineOrderKey = "0001",
                        timelineIdentityKey = "main:message:user-1",
                        timelineItemKind = "message:user"
                    ),
                    TimelineSnapshotMessage(
                        messageId = "assistant-1",
                        seq = 2,
                        runId = "run-1",
                        partId = "part-text-1",
                        role = "assistant",
                        messageState = "completed",
                        createdAt = "2026-06-07T12:00:01.000Z",
                        content = listOf(RelayChatContentBlock(type = "text", text = "ok")),
                        timelineOrderKey = "0002",
                        timelineIdentityKey = "main:message:assistant-1",
                        timelineItemKind = "message:assistant"
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
                        attachmentIds = listOf("att-local"),
                        timelineOrderKey = "0003",
                        timelineIdentityKey = "main:attachment:history-file",
                        timelineItemKind = "attachment"
                    )
                )
            )
        )

        assertEquals(listOf("history-file"), result.messages.map { it.id })
        assertEquals("/api/mobile/files/att-local", result.messages.single().contentBlocks.single().downloadUrl)
    }

}

private fun debugTimelineDump(messages: List<ChatMessage>, sessionKey: String): List<Map<String, String>> {
    return messages.map { message ->
        mapOf(
            "stableKey" to message.timelineStableKey
        )
    }
}
