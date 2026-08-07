package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.ChatSessionItem
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import com.rethinkingstudio.clawlink.core.domain.NotificationPort
import com.rethinkingstudio.clawlink.core.network.RelayAPIClient
import com.rethinkingstudio.clawlink.core.network.dto.ChatHistoryResponse
import com.rethinkingstudio.clawlink.core.network.dto.ChatHistoryItem
import com.rethinkingstudio.clawlink.core.network.transport.RelayWebSocketClient
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

class ChatHistoryMergeHelpersTest {
    @Test
    fun canonicalAssistantTextWithTrailingToolCallKeepsAssistantRole() {
        val messages = buildHistoryMessagesFromItems(
            listOf(
                ChatHistoryItem(
                    id = "assistant-analysis-and-search",
                    role = "assistant",
                    content = JsonPrimitive("这是一张标准证件照。关于蜘蛛侠图片，我来找找："),
                    contentBlocks = listOf(
                        RelayChatContentBlock(type = "text", text = "这是一张标准证件照。关于蜘蛛侠图片，我来找找："),
                        RelayChatContentBlock(type = "tool_call", name = "exec", toolCallId = "call-find-spider")
                    ),
                    createdAt = "2026-07-27T02:23:00Z",
                    timelineOrderKey = "v4|0|00000000000000000010|50|assistant-analysis-and-search",
                    timelineIdentityKey = "v1|main|message|assistant|assistant-analysis-and-search",
                    timelineItemKind = "message:assistant"
                )
            )
        )

        assertEquals(1, messages.size)
        assertEquals(MessageRole.assistant, messages.single().role)
        assertFalse(messages.single().hasToolContent)
        assertTrue(messages.single().shouldDisplayInChat(showInvocationProcess = false))
    }

    @Test
    fun staleCanonicalAssistantToolCallOnlyHistoryBecomesToolInsteadOfBlankBubble() {
        val messages = buildHistoryMessagesFromItems(
            listOf(
                ChatHistoryItem(
                    id = "assistant-tool-only",
                    role = "assistant",
                    content = JsonPrimitive("{ \"command\": \"find spiderman.jpg\" }"),
                    contentBlocks = listOf(
                        RelayChatContentBlock(type = "thinking"),
                        RelayChatContentBlock(type = "tool_call", name = "exec", toolCallId = "call-find-spider")
                    ),
                    createdAt = "2026-07-27T02:23:01Z",
                    timelineOrderKey = "v4|0|00000000000000000012|30|assistant-tool-only",
                    timelineIdentityKey = "v1|main|tool|call-find-spider",
                    timelineItemKind = "message:assistant"
                )
            )
        )

        assertEquals(1, messages.size)
        assertEquals(MessageRole.tool, messages.single().role)
        assertFalse(messages.single().shouldDisplayInChat(showInvocationProcess = false))
        assertTrue(messages.single().shouldDisplayInChat(showInvocationProcess = true))
    }

    @Test
    fun samePendingUploadMessageDoesNotGuessByFileMetadataAlone() {
        val pending = ChatMessage(
            id = "attachment-1",
            role = MessageRole.user,
            state = MessageState.streaming,
            content = "same-name.png",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    fileName = "same-name.png",
                    mimeType = "image/png",
                    sizeBytes = 42,
                    gatewayId = "gateway-1",
                    sessionKey = "main"
                )
            ),
            runId = "upload-attachment-1",
            sortTimestamp = 10.0
        )
        val completed = ChatMessage(
            id = "file-file-2",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "same-name.png",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    fileId = "file-2",
                    fileName = "same-name.png",
                    mimeType = "image/png",
                    sizeBytes = 42,
                    downloadUrl = "/api/mobile/files/file-2",
                    gatewayId = "gateway-1",
                    sessionKey = "main"
                )
            ),
            runId = "file-file-2",
            sortTimestamp = 11.0
        )

        assertFalse(samePendingUploadMessage(pending, completed))
    }

    @Test
    fun samePendingUploadMessageMatchesByAttachmentId() {
        val pending = ChatMessage(
            id = "attachment-1",
            role = MessageRole.user,
            state = MessageState.streaming,
            content = "same-name.png",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    attachmentId = "attachment-1",
                    fileName = "same-name.png",
                    mimeType = "image/png",
                    sizeBytes = 42
                )
            ),
            runId = "upload-attachment-1",
            sortTimestamp = 10.0
        )
        val completed = ChatMessage(
            id = "file-file-2",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "renamed-final.png",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    attachmentId = "attachment-1",
                    fileId = "file-2",
                    fileName = "renamed-final.png",
                    mimeType = "image/png",
                    sizeBytes = 84,
                    downloadUrl = "/api/mobile/files/file-2"
                )
            ),
            runId = "file-file-2",
            sortTimestamp = 11.0
        )

        assertTrue(samePendingUploadMessage(pending, completed))
    }

    @Test
    fun samePendingUploadMessageDoesNotMergeAcrossRolesWhenAttachmentIdMatches() {
        val pending = ChatMessage(
            id = "attachment-1",
            role = MessageRole.user,
            state = MessageState.streaming,
            content = "same-name.png",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    attachmentId = "attachment-1",
                    fileName = "same-name.png",
                    mimeType = "image/png",
                    sizeBytes = 42
                )
            ),
            runId = "upload-attachment-1",
            sortTimestamp = 10.0
        )
        val assistantCompleted = ChatMessage(
            id = "file-file-2",
            role = MessageRole.assistant,
            state = MessageState.completed,
            content = "same-name.png",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    attachmentId = "attachment-1",
                    fileId = "file-2",
                    fileName = "same-name.png",
                    mimeType = "image/png",
                    sizeBytes = 84,
                    downloadUrl = "/api/mobile/files/file-2"
                )
            ),
            runId = "file-file-2",
            sortTimestamp = 11.0
        )

        assertFalse(samePendingUploadMessage(pending, assistantCompleted))
    }

    @Test
    fun sameFileMessageDoesNotMergeByFileMetadataWithoutStableIdentity() {
        val localPlaceholder = ChatMessage(
            id = "attachment-1",
            role = MessageRole.user,
            state = MessageState.streaming,
            content = "duplicate-name.png",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    fileName = "duplicate-name.png",
                    mimeType = "image/png",
                    sizeBytes = 42,
                    gatewayId = "gateway-1",
                    sessionKey = "main"
                )
            ),
            runId = "upload-attachment-1",
            sortTimestamp = 10.0
        )
        val completedOtherUpload = ChatMessage(
            id = "file-file-2",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "duplicate-name.png",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    fileId = "file-2",
                    fileName = "duplicate-name.png",
                    mimeType = "image/png",
                    sizeBytes = 42,
                    downloadUrl = "/api/mobile/files/file-2",
                    gatewayId = "gateway-1",
                    sessionKey = "main"
                )
            ),
            runId = "file-file-2",
            sortTimestamp = 11.0
        )

        assertFalse(sameFileMessage(localPlaceholder, completedOtherUpload))
    }

    @Test
    fun sameFileMessageDoesNotMergeAcrossRolesWhenAttachmentIdMatches() {
        val localPlaceholder = ChatMessage(
            id = "attachment-1",
            role = MessageRole.user,
            state = MessageState.streaming,
            content = "shared.png",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    attachmentId = "att-shared",
                    fileName = "shared.png",
                    mimeType = "image/png"
                )
            ),
            runId = "upload-att-shared",
            sortTimestamp = 10.0
        )
        val assistantFile = ChatMessage(
            id = "assistant-file",
            role = MessageRole.assistant,
            state = MessageState.completed,
            content = "shared.png",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    attachmentId = "att-shared",
                    fileId = "file-shared",
                    fileName = "shared.png",
                    mimeType = "image/png",
                    downloadUrl = "/api/mobile/files/file-shared"
                )
            ),
            runId = "file-file-shared",
            sortTimestamp = 11.0
        )

        assertFalse(sameFileMessage(localPlaceholder, assistantFile))
    }

    @Test
    fun sameFileMessageMatchesByAttachmentIdEvenWhenFileNameChanges() {
        val initial = ChatMessage(
            id = "message-1",
            role = MessageRole.assistant,
            state = MessageState.completed,
            content = "draft.png",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    attachmentId = "att-1",
                    fileName = "draft.png",
                    mimeType = "image/png"
                )
            ),
            runId = "attachment-run",
            sortTimestamp = 10.0
        )
        val renamed = ChatMessage(
            id = "message-2",
            role = MessageRole.assistant,
            state = MessageState.completed,
            content = "final.png",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    attachmentId = "att-1",
                    fileId = "file-2",
                    fileName = "final.png",
                    mimeType = "image/png",
                    downloadUrl = "/api/mobile/files/file-2"
                )
            ),
            runId = "file-file-2",
            sortTimestamp = 11.0
        )

        assertTrue(sameFileMessage(initial, renamed))
    }

    @Test
    fun sameFileMessageMatchesHistoricalAliasesOnlyWithExplicitProjection() {
        val first = ChatMessage(
            id = "first",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "look",
            contentBlocks = listOf(RelayChatContentBlock(
                type = "image",
                attachmentId = "attachment-old-a",
                fileId = "file-old-a",
                fileName = "photo.png",
                mimeType = "image/png",
                sha256 = "same-digest",
                sourceRunId = "run-1"
            )),
            runId = "file-file-old-a"
        )
        val duplicateAlias = ChatMessage(
            id = "second",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "look",
            contentBlocks = listOf(RelayChatContentBlock(
                type = "image",
                attachmentId = "attachment-old-b",
                projectionOf = "attachment-old-a",
                fileId = "file-old-b",
                fileName = "photo.png",
                mimeType = "image/png",
                contentHash = "same-digest",
                sourceRunId = "run-1"
            )),
            runId = "file-file-old-b"
        )

        assertTrue(sameFileMessage(first, duplicateAlias))
        assertFalse(sameFileMessage(first, duplicateAlias.copy(
            contentBlocks = duplicateAlias.contentBlocks.map {
                it.copy(projectionOf = null, sourceRunId = "run-1")
            }
        )))
    }

    @Test
    fun chatHistoryWindowStateDefaultsToIdleEmptyWindow() {
        val state = ChatHistoryWindowState()

        assertFalse(state.isLoadingOlder)
        assertFalse(state.isCatchingUp)
        assertFalse(state.hasOlder)
        assertEquals(null, state.olderCursor)
        assertEquals(null, state.newestCursor)
        assertEquals(0, state.loadedMessageCount)
        assertEquals(state, ChatState().historyWindow)
    }

    @Test
    fun abortRunCompletesStreamingPlaceholderLocally() {
        val user = ChatMessage(
            id = "user-1",
            role = MessageRole.user,
            content = "stop me",
            runId = "local-user-run-1",
            sortTimestamp = 10.0
        )
        val placeholder = ChatMessage(
            id = "assistant-1",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "正在连接...",
            runId = "run-stop-1",
            sortTimestamp = 11.0
        )

        val result = completeStreamingMessageLocallyAfterStop(
            messages = listOf(user, placeholder),
            runId = "run-stop-1"
        )

        assertEquals("run-stop-1", result.stoppedRunId)
        assertEquals(listOf(user.id), result.messages.map { it.id })
        assertFalse(result.messages.any { it.state == MessageState.streaming })
    }

    @Test
    fun abortRunCompletesEnglishStreamingPlaceholderLocally() {
        val placeholder = ChatMessage(
            id = "assistant-1",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "Connecting...",
            runId = "run-stop-en",
            sortTimestamp = 11.0
        )

        val result = completeStreamingMessageLocallyAfterStop(
            messages = listOf(placeholder),
            runId = "run-stop-en"
        )

        assertEquals("run-stop-en", result.stoppedRunId)
        assertTrue(result.messages.isEmpty())
    }

    @Test
    fun abortRunCompletesPartialStreamingTextLocally() {
        val partial = ChatMessage(
            id = "assistant-1",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "已经输出的内容",
            runId = "run-stop-2",
            sortTimestamp = 11.0
        )

        val result = completeStreamingMessageLocallyAfterStop(
            messages = listOf(partial),
            runId = "run-stop-2"
        )

        assertEquals("run-stop-2", result.stoppedRunId)
        assertEquals(1, result.messages.size)
        assertEquals(MessageState.completed, result.messages.single().state)
        assertEquals("已经输出的内容", result.messages.single().content)
    }

    @Test
    fun canonicalHistoryRefreshDropsStaleCompletedCacheButKeepsPendingTurnOverlay() {
        val staleCompleted = canonicalMessage(
            id = "stale-assistant",
            role = MessageRole.assistant,
            content = "old answer",
            order = "0001|50|stale",
            identity = "main:message:stale-assistant",
            runId = "old-run"
        )
        val localUser = ChatMessage(
            id = "local-user-run-1",
            role = MessageRole.user,
            content = "new question",
            runId = "local-user-run-1",
            sortTimestamp = 100.0,
            timelineOrderKey = "local:run-1:10:local-user-run-1",
            timelineIdentityKey = "local:message:user:run-1",
            timelineItemKind = "message:user"
        )
        val waiting = ChatMessage(
            id = "assistant-waiting",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "[[clawlink:typing]]",
            runId = "run-1",
            sortTimestamp = 100.001,
            timelineOrderKey = "local:run-1:20:assistant-waiting",
            timelineIdentityKey = "local:waiting:run-1",
            timelineItemKind = "waiting"
        )

        val merged = mergeHistoryWithCurrentMessages(
            historyMessages = emptyList(),
            currentMessages = listOf(staleCompleted, localUser, waiting),
            currentStreamingMessageId = waiting.id,
            isTrackedPendingAssistantMessageId = { it == waiting.id }
        )

        assertEquals(listOf(localUser.id, waiting.id), merged.map { it.id })
    }

    @Test
    fun canonicalHistoryRefreshKeepsEveryQueuedFutureTurnWhileCurrentTurnIsActive() {
        val activeUser = ChatMessage(
            id = "user-active-run",
            role = MessageRole.user,
            content = "active",
            runId = "local-user-active-run",
            sortTimestamp = 100.0,
            timelineOrderKey = "local:active-run|10|user-active-run",
            timelineIdentityKey = "local:message:user:active-run",
            timelineItemKind = "message:user"
        )
        val waiting = ChatMessage(
            id = "assistant-active-run",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = protocolTypingMarkerText,
            runId = "active-run",
            sortTimestamp = 100.001,
            timelineOrderKey = "local:active-run|20|assistant-active-run",
            timelineIdentityKey = "local:waiting:active-run",
            timelineItemKind = "waiting"
        )
        val queued = (1L..3L).map { position ->
            val runId = "queued-run-$position"
            ChatMessage(
                id = "user-$runId",
                role = MessageRole.user,
                content = "queued-$position",
                runId = "local-user-$runId",
                sortTimestamp = 100.0 + position,
                timelineOrderKey = "local:$runId|10|user-$runId",
                timelineIdentityKey = "local:message:user:$runId",
                timelineItemKind = "message:user",
                deliveryState = "queued",
                clientMessageText = "queued-$position",
                queuePosition = position
            )
        }

        val merged = mergeHistoryWithCurrentMessages(
            historyMessages = emptyList(),
            currentMessages = listOf(activeUser, waiting) + queued,
            currentStreamingMessageId = waiting.id,
            isTrackedPendingAssistantMessageId = { it == waiting.id }
        )

        assertEquals(
            listOf(activeUser.id, waiting.id) + queued.map { it.id },
            merged.map { it.id }
        )
        assertEquals(listOf(1L, 2L, 3L), merged.filter { it.deliveryState == "queued" }.map { it.queuePosition })
    }

    @Test
    fun canonicalHistoryRefreshUsesRelayAsOnlyCompletedSource() {
        val staleCompleted = canonicalMessage(
            id = "stale-assistant",
            role = MessageRole.assistant,
            content = "old answer",
            order = "0001|50|stale",
            identity = "main:message:stale-assistant",
            runId = "old-run"
        )
        val relayUser = canonicalMessage(
            id = "server-user",
            role = MessageRole.user,
            content = "relay question",
            order = "0002|10|server-user",
            identity = "main:message:server-user",
            runId = "relay-run"
        )
        val relayAssistant = canonicalMessage(
            id = "server-assistant",
            role = MessageRole.assistant,
            content = "relay answer",
            order = "0003|50|server-assistant",
            identity = "main:message:server-assistant",
            runId = "relay-run"
        )

        val merged = mergeHistoryWithCurrentMessages(
            historyMessages = listOf(relayAssistant, relayUser),
            currentMessages = listOf(staleCompleted),
            currentStreamingMessageId = null,
            isTrackedPendingAssistantMessageId = { false }
        )

        assertEquals(listOf("server-user", "server-assistant"), merged.map { it.id })
    }

    @Ignore("Legacy non-canonical history merge behavior was removed; Relay canonical order is required.")
    @Test
    fun refreshKeepsCompletedAssistantWhenHistoryHasNotCaughtUp() {
        val historyUser = ChatMessage(
            id = "history-user-completed-lag",
            role = MessageRole.user,
            content = "hello",
            runId = "history-user-completed-lag",
            sortTimestamp = 1_000.0
        )
        val localUser = ChatMessage(
            id = "local-user-completed-lag",
            role = MessageRole.user,
            content = "hello",
            runId = "local-user-completed-lag",
            sortTimestamp = 1_000.0
        )
        val completedAssistant = ChatMessage(
            id = "assistant-completed-lag",
            role = MessageRole.assistant,
            state = MessageState.completed,
            content = "final answer",
            runId = "run-completed-lag",
            sortTimestamp = 1_001.0
        )

        val merged = mergeHistoryWithCurrentMessages(
            historyMessages = listOf(historyUser),
            currentMessages = listOf(localUser, completedAssistant),
            currentStreamingMessageId = null,
            isTrackedPendingAssistantMessageId = { false }
        )

        assertEquals(listOf("local-user-completed-lag", "run-completed-lag"), merged.map { it.runId })
    }

    @Ignore("Legacy non-canonical history merge behavior was removed; Relay canonical order is required.")
    @Test
    fun refreshKeepsNewRepeatedCompletedAssistantWhenOnlyOldHistoryMatches() {
        val historyUser = ChatMessage(
            id = "history-old-user",
            role = MessageRole.user,
            content = "hello",
            runId = "history-old-user",
            sortTimestamp = 100.0
        )
        val historyAssistant = ChatMessage(
            id = "history-old-assistant",
            role = MessageRole.assistant,
            state = MessageState.completed,
            content = "final answer",
            runId = "history-old-assistant",
            sortTimestamp = 101.0
        )
        val localUser = ChatMessage(
            id = "local-user-new-turn",
            role = MessageRole.user,
            content = "hello",
            runId = "local-user-new-turn",
            sortTimestamp = 1_000.0
        )
        val completedAssistant = ChatMessage(
            id = "assistant-new-turn",
            role = MessageRole.assistant,
            state = MessageState.completed,
            content = "final answer",
            runId = "run-new-turn",
            sortTimestamp = 1_001.0
        )

        val merged = mergeHistoryWithCurrentMessages(
            historyMessages = listOf(historyUser, historyAssistant),
            currentMessages = listOf(localUser, completedAssistant),
            currentStreamingMessageId = null,
            isTrackedPendingAssistantMessageId = { false }
        )

        assertEquals(
            listOf("history-old-user", "history-old-assistant", "local-user-new-turn", "run-new-turn"),
            merged.map { it.runId }
        )
    }

    @Ignore("Legacy non-canonical history merge behavior was removed; Relay canonical order is required.")
    @Test
    fun refreshDropsCompletedAssistantWhenHistoryAlreadyContainsIt() {
        val historyUser = ChatMessage(
            id = "history-user-completed-present",
            role = MessageRole.user,
            content = "hello",
            runId = "history-user-completed-present",
            sortTimestamp = 2_000.0
        )
        val historyAssistant = ChatMessage(
            id = "history-assistant-completed-present",
            role = MessageRole.assistant,
            state = MessageState.completed,
            content = "final answer",
            runId = "history-assistant-completed-present",
            sortTimestamp = 2_001.0
        )
        val localUser = ChatMessage(
            id = "local-user-completed-present",
            role = MessageRole.user,
            content = "hello",
            runId = "local-user-completed-present",
            sortTimestamp = 2_000.0
        )
        val completedAssistant = ChatMessage(
            id = "assistant-completed-present",
            role = MessageRole.assistant,
            state = MessageState.completed,
            content = "final answer",
            runId = "run-completed-present",
            sortTimestamp = 2_001.0
        )

        val merged = mergeHistoryWithCurrentMessages(
            historyMessages = listOf(historyUser, historyAssistant),
            currentMessages = listOf(localUser, completedAssistant),
            currentStreamingMessageId = null,
            isTrackedPendingAssistantMessageId = { false }
        )

        assertEquals(
            listOf("local-user-completed-present", "history-assistant-completed-present"),
            merged.map { it.runId }
        )
    }

    @Ignore("Legacy non-canonical older-history window behavior was removed; Relay canonical order is required.")
    @Test
    fun olderWindowRetainsFetchedOlderMessagesWhenCurrentWindowIsFull() {
        val olderMessages = (0 until 50).map { index ->
            ChatMessage(
                id = "older-$index",
                role = MessageRole.assistant,
                content = "older $index",
                runId = "older-$index",
                sortTimestamp = index.toDouble()
            )
        }
        val currentMessages = (50 until 550).map { index ->
            ChatMessage(
                id = "current-$index",
                role = MessageRole.assistant,
                content = "current $index",
                runId = "current-$index",
                sortTimestamp = index.toDouble()
            )
        }

        val merged = mergeHistoryWithCurrentMessages(
            historyMessages = olderMessages,
            currentMessages = currentMessages,
            currentStreamingMessageId = null,
            isTrackedPendingAssistantMessageId = { false }
        )
        val bounded = olderBoundedHistoryWindowMessages(
            messages = merged,
            maxMessages = 500,
            shouldPreserveActiveMessage = { false }
        )

        assertEquals(500, bounded.size)
        assertEquals("older-0", bounded.first().id)
        assertTrue(bounded.any { it.id == "older-49" })
        assertFalse(bounded.any { it.id == "current-549" })
    }

    @Ignore("Legacy non-canonical older-history window behavior was removed; Relay canonical order is required.")
    @Test
    fun olderWindowRetainsFetchedOlderMessagesAndActivePendingAssistant() {
        val olderMessages = (0 until 50).map { index ->
            ChatMessage(
                id = "older-$index",
                role = MessageRole.assistant,
                content = "older $index",
                runId = "older-$index",
                sortTimestamp = index.toDouble()
            )
        }
        val currentMessages = (50 until 549).map { index ->
            ChatMessage(
                id = "current-$index",
                role = MessageRole.assistant,
                content = "current $index",
                runId = "current-$index",
                sortTimestamp = index.toDouble()
            )
        } + ChatMessage(
            id = "pending-assistant",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "正在连接...",
            runId = "pending-run",
            sortTimestamp = 549.0
        )

        val merged = mergeHistoryWithCurrentMessages(
            historyMessages = olderMessages,
            currentMessages = currentMessages,
            currentStreamingMessageId = "pending-assistant",
            isTrackedPendingAssistantMessageId = { it == "pending-assistant" }
        )
        val bounded = olderBoundedHistoryWindowMessages(
            messages = merged,
            maxMessages = 500,
            shouldPreserveActiveMessage = { it.id == "pending-assistant" }
        )

        assertEquals(500, bounded.size)
        assertEquals("older-0", bounded.first().id)
        assertTrue(bounded.any { it.id == "older-49" })
        assertTrue(bounded.any { it.id == "pending-assistant" })
    }

    @Ignore("Legacy non-canonical older-history window behavior was removed; Relay canonical order is required.")
    @Test
    fun loadOlderHistoryRetainsFetchedOlderPageAtFullStoreWindow() = runBlocking {
        val requests = mutableListOf<HistoryPageRequest>()
        val wsClient = RelayWebSocketClient()
        try {
            val store = ChatStore(
                apiClient = RelayAPIClient(),
                wsClient = wsClient,
                notificationPort = object : NotificationPort {
                    override fun showReplyNotification(sessionKey: String, title: String, body: String) = Unit
                    override fun cancelNotification(id: Int) = Unit
                    override fun cancelAll() = Unit
                },
                chatHistoryPageFetcher = { gatewayId, sessionKey, limit, cursor, direction ->
                    requests += HistoryPageRequest(gatewayId, sessionKey, limit, cursor, direction)
                    if (cursor == "seq:50") {
                        ChatHistoryResponse(
                            items = chatHistoryItems(0 until 50),
                            hasMore = true,
                            nextCursor = "seq:0",
                            newestCursor = "seq:49"
                        )
                    } else {
                        ChatHistoryResponse(
                            items = chatHistoryItems(50 until 550),
                            hasMore = true,
                            nextCursor = "seq:50",
                            newestCursor = "seq:549"
                        )
                    }
                }
            )

            store.loadHistory("gw_1", "main", limit = 500)
            assertEquals(500, store.state.value.messages.size)
            assertEquals("seq:50", store.state.value.historyWindow.olderCursor)
            assertEquals(null, store.state.value.errorMessage)

            store.loadOlderHistory("gw_1", "main")

            val state = store.state.value
            assertEquals(500, state.messages.size)
            assertTrue(state.messages.any { it.id == "history-0" })
            assertTrue(state.messages.any { it.id == "history-49" })
            assertFalse(state.messages.any { it.id == "history-549" })
            assertEquals("seq:0", state.historyWindow.olderCursor)
            assertEquals(500, state.historyWindow.loadedMessageCount)
            assertTrue(requests.any { it.limit == 500 && it.cursor == null && it.direction == "older" })
            assertTrue(requests.any { it.limit == 100 && it.cursor == "seq:50" && it.direction == "older" })
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun loadOlderPrepareExitClearsOwnedLoadingAndAllowsNextPageAttempt() = runBlocking {
        var fetchCount = 0
        var responseReturned = false
        var prepareAttemptCount = 0
        lateinit var store: ChatStore
        val wsClient = RelayWebSocketClient()
        try {
            store = ChatStore(
                apiClient = RelayAPIClient(),
                wsClient = wsClient,
                notificationPort = object : NotificationPort {
                    override fun showReplyNotification(sessionKey: String, title: String, body: String) = Unit
                    override fun cancelNotification(id: Int) = Unit
                    override fun cancelAll() = Unit
                },
                chatHistoryPageFetcher = { _, _, _, _, _ ->
                    fetchCount += 1
                    responseReturned = true
                    ChatHistoryResponse(
                        items = chatHistoryItems(1..1).map { item ->
                            item.copy(
                                timelineOrderKey = "main:0001",
                                timelineIdentityKey = "main:assistant:history-1",
                                timelineItemKind = "message:assistant"
                            )
                        },
                        hasMore = true,
                        nextCursor = "older:next"
                    )
                }
            )
            store.setStateForTest(
                ChatState(
                    currentGatewayId = "gw_1",
                    currentSessionKey = "main",
                    historyWindow = ChatHistoryWindowState(
                        hasOlder = true,
                        olderCursor = "older:start"
                    )
                )
            )
            store.historyPrepareAttemptHookForTest = {
                prepareAttemptCount += 1
                if (prepareAttemptCount == 1) {
                    assertTrue("prepare 必须发生在 HTTP response 返回之后", responseReturned)
                    // 精确模拟 response 返回后 websocket mutation 抢先提交，随后
                    // scope generation 变化使 awaitHistoryPrepareReady 返回 false。
                    store.noteCanonicalTimelineMutation()
                    store.advanceTimelineScopeGeneration()
                }
            }

            store.loadOlderHistory("gw_1", "main")

            assertEquals(1, fetchCount)
            assertFalse(store.state.value.historyWindow.isLoadingOlder)
            assertEquals("older:start", store.state.value.historyWindow.olderCursor)

            // 第一次请求的 finally 已释放 owner；同一游标可以立即再次翻页，
            // 不会被遗留的 isLoadingOlder=true 永久冻结。
            store.historyPrepareAttemptHookForTest = null
            responseReturned = false
            store.loadOlderHistory("gw_1", "main")

            assertEquals(2, fetchCount)
            assertFalse(store.state.value.historyWindow.isLoadingOlder)
            assertEquals("older:next", store.state.value.historyWindow.olderCursor)
            assertEquals(listOf("history-1"), store.state.value.messages.map { it.id })
        } finally {
            wsClient.destroy()
        }
    }

    @Ignore("Legacy non-canonical history failure cache behavior was removed; Relay canonical order is required.")
    @Test
    fun loadHistoryFailureKeepsExistingMessagesAndClearsLoading() = runBlocking {
        var shouldFail = false
        val wsClient = RelayWebSocketClient()
        try {
            val store = ChatStore(
                apiClient = RelayAPIClient(),
                wsClient = wsClient,
                notificationPort = object : NotificationPort {
                    override fun showReplyNotification(sessionKey: String, title: String, body: String) = Unit
                    override fun cancelNotification(id: Int) = Unit
                    override fun cancelAll() = Unit
                },
                chatHistoryPageFetcher = { _, _, _, _, _ ->
                    if (shouldFail) error("history unavailable")
                    ChatHistoryResponse(
                        items = chatHistoryItems(1..2),
                        hasMore = true,
                        nextCursor = "seq:1",
                        newestCursor = "seq:2"
                    )
                }
            )

            store.loadHistory("gw_1", "main", limit = 100)
            assertEquals(listOf("history-1", "history-2"), store.state.value.messages.map { it.id })

            shouldFail = true
            store.loadHistory("gw_1", "main", limit = 100)

            val state = store.state.value
            assertEquals(listOf("history-1", "history-2"), state.messages.map { it.id })
            assertFalse(state.isLoading)
            assertFalse(state.isSwitchingSession)
            assertTrue(state.errorMessage?.isNotBlank() == true)
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun staleHistoryLoadDoesNotSwitchBackAfterSessionSelection() = runBlocking {
        val requests = mutableListOf<HistoryPageRequest>()
        val wsClient = RelayWebSocketClient()
        try {
            val store = ChatStore(
                apiClient = RelayAPIClient(),
                wsClient = wsClient,
                notificationPort = object : NotificationPort {
                    override fun showReplyNotification(sessionKey: String, title: String, body: String) = Unit
                    override fun cancelNotification(id: Int) = Unit
                    override fun cancelAll() = Unit
                },
                chatHistoryPageFetcher = { gatewayId, sessionKey, limit, cursor, direction ->
                    requests += HistoryPageRequest(gatewayId, sessionKey, limit, cursor, direction)
                    ChatHistoryResponse(items = emptyList())
                }
            )
            store.setStateForTest(
                ChatState(
                    currentGatewayId = "gw_1",
                    currentSessionKey = "session-b",
                    sessions = listOf(
                        ChatSessionItem(sessionKey = "session-a", lastActivityAt = null),
                        ChatSessionItem(sessionKey = "session-b", lastActivityAt = null)
                    ),
                    isSwitchingSession = true
                )
            )

            store.loadHistory("gw_1", "session-a", limit = 100)

            val state = store.state.value
            assertEquals("session-b", state.currentSessionKey)
            assertTrue(state.isSwitchingSession)
            assertTrue(requests.isEmpty())
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun historyResponseThatFinishesAfterSessionSwitchCannotPoisonTimelineReducerState() = runBlocking {
        val requestStarted = CompletableDeferred<Unit>()
        val releaseResponse = CompletableDeferred<Unit>()
        val wsClient = RelayWebSocketClient()
        try {
            val store = ChatStore(
                apiClient = RelayAPIClient(),
                wsClient = wsClient,
                notificationPort = object : NotificationPort {
                    override fun showReplyNotification(sessionKey: String, title: String, body: String) = Unit
                    override fun cancelNotification(id: Int) = Unit
                    override fun cancelAll() = Unit
                },
                chatHistoryPageFetcher = { _, _, _, _, _ ->
                    requestStarted.complete(Unit)
                    releaseResponse.await()
                    ChatHistoryResponse(
                        items = emptyList(),
                        timelineSnapshot = Json.parseToJsonElement(
                            """
                            {
                              "timelineProtocolVersion": 3,
                              "sessionKey": "session-a",
                              "snapshotRevision": "7",
                              "messages": [
                                {
                                  "messageId": "assistant-session-a",
                                  "seq": 7,
                                  "role": "assistant",
                                  "messageState": "completed",
                                  "timelineOrderKey": "session-a:0007",
                                  "timelineIdentityKey": "session-a:assistant:7",
                                  "timelineItemKind": "message:assistant",
                                  "content": [{"type":"text","text":"stale"}]
                                }
                              ]
                            }
                            """.trimIndent()
                        )
                    )
                }
            )
            store.setStateForTest(
                ChatState(
                    currentGatewayId = "gw_1",
                    currentSessionKey = "session-a",
                    sessions = listOf(
                        ChatSessionItem("session-a", null),
                        ChatSessionItem("session-b", null)
                    )
                )
            )

            val historyJob = async { store.loadHistory("gw_1", "session-a", limit = 100) }
            requestStarted.await()
            store.selectSession("session-b")
            releaseResponse.complete(Unit)
            historyJob.await()

            assertEquals("session-b", store.state.value.currentSessionKey)
            assertTrue(store.state.value.messages.isEmpty())
            assertTrue(store.timelineState.messages.isEmpty())
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun prepareConflictWaitsForStreamingSignalAndReusesReturnedResponse() = runBlocking {
        val prepareConflictObserved = CompletableDeferred<Unit>()
        var fetchCount = 0
        var prepareAttemptCount = 0
        lateinit var store: ChatStore
        val wsClient = RelayWebSocketClient()
        try {
            store = ChatStore(
                apiClient = RelayAPIClient(),
                wsClient = wsClient,
                notificationPort = object : NotificationPort {
                    override fun showReplyNotification(sessionKey: String, title: String, body: String) = Unit
                    override fun cancelNotification(id: Int) = Unit
                    override fun cancelAll() = Unit
                },
                chatHistoryPageFetcher = { _, _, _, _, _ ->
                    fetchCount += 1
                    ChatHistoryResponse(
                        items = chatHistoryItems(1..1).map { item ->
                            item.copy(
                                timelineOrderKey = "main:0001",
                                timelineIdentityKey = "main:assistant:history-1",
                                timelineItemKind = "message:assistant"
                            )
                        }
                    )
                }
            )
            store.historyPrepareAttemptHookForTest = {
                prepareAttemptCount += 1
                if (prepareAttemptCount == 1) {
                    store.setStateForTest(store.state.value.copy(isStreaming = true))
                    store.noteCanonicalTimelineMutation()
                    prepareConflictObserved.complete(Unit)
                }
            }
            store.setStateForTest(ChatState(currentGatewayId = "gw_1", currentSessionKey = "main"))

            val historyJob = async { store.loadHistory("gw_1", "main", limit = 100) }
            withTimeout(3_000L) { prepareConflictObserved.await() }

            // response 已经在内存中；streaming 未结束前不重算、不重复 HTTP。
            assertEquals(1, fetchCount)
            assertTrue(store.state.value.isLoading)
            store.setStateForTest(store.state.value.copy(isStreaming = false))
            withTimeout(3_000L) { historyJob.await() }

            assertFalse(store.state.value.isLoading)
            assertFalse(store.state.value.isSwitchingSession)
            assertEquals(1, fetchCount)
            assertEquals(2, prepareAttemptCount)
            assertEquals(
                "state=${store.state.value}",
                listOf("history-1"),
                store.state.value.messages.map { it.id }
            )
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun helloDuringInFlightReconcileQueuesExactlyOneFollowUpWithoutTimer() = runBlocking {
        val firstFetchStarted = CompletableDeferred<Unit>()
        val releaseFirstFetch = CompletableDeferred<Unit>()
        val secondFetchStarted = CompletableDeferred<Unit>()
        var fetchCount = 0
        var prepareCount = 0
        val wsClient = RelayWebSocketClient()
        try {
            val store = ChatStore(
                apiClient = RelayAPIClient(),
                wsClient = wsClient,
                notificationPort = object : NotificationPort {
                    override fun showReplyNotification(sessionKey: String, title: String, body: String) = Unit
                    override fun cancelNotification(id: Int) = Unit
                    override fun cancelAll() = Unit
                },
                chatHistoryPageFetcher = { _, _, _, _, _ ->
                    fetchCount += 1
                    if (fetchCount == 1) {
                        firstFetchStarted.complete(Unit)
                        releaseFirstFetch.await()
                    } else if (fetchCount == 2) {
                        secondFetchStarted.complete(Unit)
                    }
                    ChatHistoryResponse(
                        items = chatHistoryItems(1..1).map { item ->
                            item.copy(
                                timelineOrderKey = "main:0001",
                                timelineIdentityKey = "main:assistant:history-1",
                                timelineItemKind = "message:assistant"
                            )
                        }
                    )
                }
            )
            store.historyPrepareAttemptHookForTest = { prepareCount += 1 }
            store.setStateForTest(ChatState(currentGatewayId = "gw_1", currentSessionKey = "main"))

            store.requestCanonicalHistoryReconcileAfterHello()
            withTimeout(3_000L) { firstFetchStarted.await() }

            // 对账进行中收到多个真实 hello/ready，pending 状态只保留一次补跑。
            repeat(3) { store.requestCanonicalHistoryReconcileAfterHello() }
            releaseFirstFetch.complete(Unit)

            withTimeout(3_000L) { secondFetchStarted.await() }
            withTimeout(3_000L) {
                while (prepareCount < 2 || store.state.value.messages.map { it.id } != listOf("history-1")) {
                    yield()
                }
            }
            assertEquals(2, fetchCount)
            assertEquals(2, prepareCount)
            assertEquals(
                "prepareCount=$prepareCount state=${store.state.value}",
                listOf("history-1"),
                store.state.value.messages.map { it.id }
            )
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun loadHistoryCanReleaseGatewaySwitchOverlayBeforeSlowHistoryCompletes() = runBlocking {
        val allowHistoryResponse = CompletableDeferred<Unit>()
        val wsClient = RelayWebSocketClient()
        try {
            val store = ChatStore(
                apiClient = RelayAPIClient(),
                wsClient = wsClient,
                notificationPort = object : NotificationPort {
                    override fun showReplyNotification(sessionKey: String, title: String, body: String) = Unit
                    override fun cancelNotification(id: Int) = Unit
                    override fun cancelAll() = Unit
                },
                chatHistoryPageFetcher = { _, _, _, _, _ ->
                    allowHistoryResponse.await()
                    ChatHistoryResponse(items = emptyList())
                }
            )
            store.setStateForTest(
                ChatState(
                    currentGatewayId = "gw_1",
                    currentSessionKey = "main",
                    isSwitchingSession = true
                )
            )

            val loadJob = async {
                store.loadHistory("gw_1", "main", limit = 100, keepSwitchingOverlay = false)
            }
            yield()

            val loadingState = store.state.value
            assertTrue(loadingState.isLoading)
            assertFalse(loadingState.isSwitchingSession)

            allowHistoryResponse.complete(Unit)
            loadJob.await()
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun loadHistoryTimelineSnapshotReplacesStaleLocalStreamingState() = runBlocking {
        val wsClient = RelayWebSocketClient()
        try {
            val store = ChatStore(
                apiClient = RelayAPIClient(),
                wsClient = wsClient,
                notificationPort = object : NotificationPort {
                    override fun showReplyNotification(sessionKey: String, title: String, body: String) = Unit
                    override fun cancelNotification(id: Int) = Unit
                    override fun cancelAll() = Unit
                },
                chatHistoryPageFetcher = { _, _, _, _, _ ->
                    ChatHistoryResponse(
                        items = emptyList(),
                        hasMore = false,
                        timelineSnapshot = Json.parseToJsonElement(
                            """
                            {
                              "protocolVersion": 2,
                              "eventType": "history.snapshot.page",
                              "gatewayId": "gw_1",
                              "sessionKey": "main",
                              "source": "history",
                              "messages": [
                                {
                                  "turnId": "turn-1",
                                  "runId": "run-1",
                                  "messageId": "user-1",
                                  "role": "user",
                                  "messageState": "completed",
	                                  "createdAt": "2026-05-29T09:18:00.000Z",
	                                  "timelineOrderKey": "v1|00000000000000000001|10|000000|user-1",
	                                  "timelineIdentityKey": "message:user:user-1",
	                                  "timelineItemKind": "message:user",
	                                  "content": [{ "type": "text", "text": "hello" }]
                                },
                                {
                                  "turnId": "turn-1",
                                  "runId": "run-1",
                                  "messageId": "assistant-1",
                                  "role": "assistant",
                                  "messageState": "completed",
	                                  "createdAt": "2026-05-29T09:18:05.000Z",
	                                  "timelineOrderKey": "v1|00000000000000000001|50|000000|assistant-1",
	                                  "timelineIdentityKey": "message:assistant:assistant-1",
	                                  "timelineItemKind": "message:assistant",
	                                  "content": [{ "type": "text", "text": "reply" }]
                                }
                              ],
                              "attachments": []
                            }
                            """.trimIndent()
                        )
                    )
                }
            )
            val stale = ChatMessage(
                id = "assistant-stale",
                role = MessageRole.assistant,
                state = MessageState.streaming,
                content = "Connecting...",
                runId = "stale-run"
            )
            store.setStateForTest(
                ChatState(
                    messages = listOf(stale),
                    currentGatewayId = "gw_1",
                    currentSessionKey = "main",
                    isStreaming = true
                )
            )
            store.setTimelineStateForTest(
                ChatTimelineState(
                    messages = listOf(stale),
                    activeRunId = "stale-run",
                    activeRunsByTurnId = mapOf("stale-turn" to "stale-run"),
                    activeTurnByRunId = mapOf("stale-run" to "stale-turn")
                )
            )
            store.setRunScopesForTest(
                linkedMapOf(
                    "stale-run" to ChatRunScope(
                        gatewayId = "gw_1",
                        sessionKey = "main",
                        assistantMessageId = "assistant-stale",
                        triggeringUserMessageId = null
                    )
                )
            )

            store.loadHistory("gw_1", "main", limit = 100)

            val state = store.state.value
            assertEquals(listOf("user-1", "assistant-1"), state.messages.map { it.id })
            assertEquals(listOf(MessageState.completed, MessageState.completed), state.messages.map { it.state })
            assertFalse(state.isStreaming)
            assertFalse(state.isStoppingRun)
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun loadHistoryTimelineSnapshotDropsCompletedLocalEntriesMissingFromAuthoritativeSnapshot() = runBlocking {
        val wsClient = RelayWebSocketClient()
        try {
            val store = ChatStore(
                apiClient = RelayAPIClient(),
                wsClient = wsClient,
                notificationPort = object : NotificationPort {
                    override fun showReplyNotification(sessionKey: String, title: String, body: String) = Unit
                    override fun cancelNotification(id: Int) = Unit
                    override fun cancelAll() = Unit
                },
                chatHistoryPageFetcher = { _, _, _, _, _ ->
                    ChatHistoryResponse(
                        items = emptyList(),
                        timelineSnapshot = Json.parseToJsonElement(
                            """
                            {
                              "protocolVersion": 2,
                              "eventType": "history.snapshot.page",
                              "gatewayId": "gw_1",
                              "sessionKey": "main",
                              "source": "history",
                              "messages": [
                                {
                                  "turnId": "server-turn",
                                  "runId": "server-turn",
                                  "messageId": "server-user",
                                  "role": "user",
                                  "messageState": "completed",
                                  "timelineOrderKey": "v1|00000000000000000002|10|000000|server-user",
                                  "timelineIdentityKey": "message:user:server-user",
                                  "timelineItemKind": "message:user",
                                  "content": [{ "type": "text", "text": "kept" }]
                                }
                              ]
                            }
                            """.trimIndent()
                        )
                    )
                }
            )
            val staleUser = ChatMessage(
                id = "stale-local-user",
                role = MessageRole.user,
                state = MessageState.completed,
                content = "internal cached turn",
                runId = "local-user-stale-run",
                timelineOrderKey = "local:stale-run:10",
                timelineIdentityKey = "local:message:user:stale-run",
                timelineItemKind = "message:user"
            )
            val staleAssistant = ChatMessage(
                id = "stale-local-assistant",
                role = MessageRole.assistant,
                state = MessageState.completed,
                content = "internal cached reply",
                runId = "stale-run",
                timelineOrderKey = "local:stale-run:50",
                timelineIdentityKey = "local:message:assistant:stale-run",
                timelineItemKind = "message:assistant"
            )
            store.setStateForTest(
                ChatState(
                    messages = listOf(staleUser, staleAssistant),
                    currentGatewayId = "gw_1",
                    currentSessionKey = "main"
                )
            )
            store.setTimelineStateForTest(ChatTimelineState(messages = listOf(staleUser, staleAssistant)))

            store.loadHistory("gw_1", "main", limit = 50)

            assertEquals(listOf("server-user"), store.state.value.messages.map { it.id })
        } finally {
            wsClient.destroy()
        }
    }

    @Test
    fun loadHistoryTimelineSnapshotPreservesOnlyExplicitlyActiveStreamingTurn() = runBlocking {
        val wsClient = RelayWebSocketClient()
        try {
            val store = ChatStore(
                apiClient = RelayAPIClient(),
                wsClient = wsClient,
                notificationPort = object : NotificationPort {
                    override fun showReplyNotification(sessionKey: String, title: String, body: String) = Unit
                    override fun cancelNotification(id: Int) = Unit
                    override fun cancelAll() = Unit
                },
                chatHistoryPageFetcher = { _, _, _, _, _ ->
                    ChatHistoryResponse(
                        items = emptyList(),
                        timelineSnapshot = Json.parseToJsonElement(
                            """
                            {
                              "protocolVersion": 2,
                              "eventType": "history.snapshot.page",
                              "gatewayId": "gw_1",
                              "sessionKey": "main",
                              "source": "history",
                              "messages": []
                            }
                            """.trimIndent()
                        )
                    )
                }
            )
            val localUser = ChatMessage(
                id = "local-user-active",
                role = MessageRole.user,
                state = MessageState.completed,
                content = "active prompt",
                runId = "local-user-active-run"
            )
            val activeAssistant = buildLocalTextAssistantPlaceholderMessage(
                id = "assistant-active-run",
                clientRunId = "active-run",
                sortTimestamp = 10.001
            )
            store.setStateForTest(
                ChatState(
                    messages = listOf(localUser, activeAssistant),
                    currentGatewayId = "gw_1",
                    currentSessionKey = "main",
                    isStreaming = true
                )
            )
            store.setTimelineStateForTest(ChatTimelineState(messages = listOf(localUser, activeAssistant)))
            store.setStreamingMessageIdForTest(activeAssistant.id)

            store.loadHistory("gw_1", "main", limit = 50)

            assertEquals(listOf(localUser.id, activeAssistant.id), store.state.value.messages.map { it.id })
            assertTrue(store.state.value.isStreaming)
        } finally {
            wsClient.destroy()
        }
    }

    @Ignore("Legacy voice transcript replacement by local matching was removed; Relay canonical order is required.")
    @Test
    fun loadHistoryTimelineSnapshotPreservesLocalVoiceBubbleOverHistoryTranscript() = runBlocking {
        val transcript = "The."
        val wsClient = RelayWebSocketClient()
        try {
            val store = ChatStore(
                apiClient = RelayAPIClient(),
                wsClient = wsClient,
                notificationPort = object : NotificationPort {
                    override fun showReplyNotification(sessionKey: String, title: String, body: String) = Unit
                    override fun cancelNotification(id: Int) = Unit
                    override fun cancelAll() = Unit
                },
                chatHistoryPageFetcher = { _, _, _, _, _ ->
                    ChatHistoryResponse(
                        items = emptyList(),
                        hasMore = false,
                        timelineSnapshot = Json.parseToJsonElement(
                            """
                            {
                              "protocolVersion": 2,
                              "eventType": "history.snapshot.page",
                              "gatewayId": "gw_1",
                              "sessionKey": "main",
                              "source": "history",
                              "messages": [
                                {
                                  "turnId": "voice-run-android",
                                  "runId": "voice-run-android",
                                  "messageId": "voice-run-android",
                                  "role": "user",
                                  "messageState": "completed",
                                  "createdAt": "2026-05-29T09:18:00.000Z",
                                  "content": [{ "type": "text", "text": "$transcript" }]
                                },
                                {
                                  "turnId": "voice-run-android",
                                  "runId": "voice-run-android",
                                  "messageId": "assistant-voice-run-android",
                                  "role": "assistant",
                                  "messageState": "completed",
                                  "createdAt": "2026-05-29T09:18:05.000Z",
                                  "content": [{ "type": "text", "text": "I’m here." }]
                                }
                              ],
                              "attachments": []
                            }
                            """.trimIndent()
                        )
                    )
                }
            )
            val localVoice = ChatMessage(
                id = "local-voice",
                role = MessageRole.user,
                content = "voice-input.m4a",
                contentBlocks = listOf(
                    RelayChatContentBlock(
                        type = "voice",
                        fileName = "voice-input.m4a",
                        mimeType = "audio/mp4",
                        downloadUrl = "file:///tmp/voice-input.m4a",
                        transcript = transcript
                    )
                ),
                runId = "local-user-voice-run-android",
                sortTimestamp = 100.0
            )
            store.setStateForTest(
                ChatState(
                    messages = listOf(localVoice),
                    currentGatewayId = "gw_1",
                    currentSessionKey = "main"
                )
            )
            store.setTimelineStateForTest(ChatTimelineState(messages = listOf(localVoice)))

            store.loadHistory("gw_1", "main", limit = 100)

            val state = store.state.value
            assertEquals("local-user-voice-run-android", state.messages.first().runId)
            assertEquals("assistant-voice-run-android", state.messages[1].id)
            assertTrue(state.messages.first().hasVoiceContent)
            assertEquals(transcript, state.messages.first().voiceTranscriptText)
            assertFalse(state.messages.any { it.role == MessageRole.user && !it.hasVoiceContent && it.content == transcript })
        } finally {
            wsClient.destroy()
        }
    }
}
