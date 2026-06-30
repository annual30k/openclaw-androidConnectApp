package com.rethinkingstudio.clawlink.ui.screens.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import com.rethinkingstudio.clawlink.core.state.chat.ChatState
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatConversationPresentationTest {
    @Test
    fun displayMessagesCoalescesDuplicateIdsBeforeLazyListRendering() {
        val local = ChatMessage(
            id = "user-1",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "20260607",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    fileId = "file-1",
                    fileName = "pocketclaw-image-send-test.png",
                    mimeType = "image/png",
                    downloadUrl = "content://local-preview"
                )
            ),
            createdAt = "2026-06-06T12:00:00Z",
            runId = "run-1",
            sortTimestamp = 1.0
        )
        val relayEcho = local.copy(
            contentBlocks = emptyList(),
            createdAt = "2026-06-06T12:00:01Z",
            sortTimestamp = 2.0
        )

        val displayMessages = conversationDisplayMessages(
            messages = listOf(local, relayEcho),
            showInvocationProcess = false
        )

        assertEquals(1, displayMessages.size)
        assertEquals("user-1", displayMessages.single().id)
        assertEquals("20260607", displayMessages.single().content)
        assertEquals("file-1", displayMessages.single().contentBlocks.single().fileId)
        assertEquals(1.0, displayMessages.single().sortTimestamp)
    }

    @Test
    fun displayMessagesCoalescesDuplicateImageFileIdsBeforeLazyListRendering() {
        val assistantImage = ChatMessage(
            id = "assistant-run-1",
            role = MessageRole.assistant,
            state = MessageState.completed,
            content = "/Users/test/Downloads/chatgpt image.png",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    fileId = "file-img-1",
                    fileName = "chatgpt image.png",
                    mimeType = "image/png",
                    downloadUrl = "/api/mobile/files/file-img-1",
                    imageWidth = 1024,
                    imageHeight = 1024,
                    sizeBytes = 2048
                )
            ),
            createdAt = "2026-06-06T12:00:00Z",
            runId = "run-1",
            sortTimestamp = 1.0
        )
        val fileEcho = ChatMessage(
            id = "file-file-img-1",
            role = MessageRole.assistant,
            state = MessageState.completed,
            content = "chatgpt image.png",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    fileId = "file-img-1",
                    fileName = "chatgpt image.png",
                    mimeType = "image/png",
                    downloadUrl = "/api/mobile/files/file-img-1",
                    imageWidth = 1024,
                    imageHeight = 1024,
                    sizeBytes = 2048
                )
            ),
            createdAt = "2026-06-06T12:00:01Z",
            runId = "run-1",
            sortTimestamp = 2.0
        )

        val displayMessages = conversationDisplayMessages(
            messages = listOf(assistantImage, fileEcho),
            showInvocationProcess = false
        )

        assertEquals(1, displayMessages.size)
        assertEquals("assistant-run-1", displayMessages.single().id)
        assertEquals("file-img-1", displayMessages.single().contentBlocks.single().fileId)
        assertEquals(1.0, displayMessages.single().sortTimestamp)
    }

    @Test
    fun displayMessagesCoalescesDuplicateImageNamesWhenOneFileIdIsMissing() {
        val localPreview = ChatMessage(
            id = "assistant-run-1",
            role = MessageRole.assistant,
            state = MessageState.completed,
            content = "/Users/test/Downloads/chatgpt image.png",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    fileName = "chatgpt image.png",
                    mimeType = "image/png",
                    downloadUrl = "file:///Users/test/Downloads/chatgpt image.png",
                    imageWidth = 1024,
                    imageHeight = 1024,
                    sizeBytes = 2048
                )
            ),
            createdAt = "2026-06-06T12:00:00Z",
            runId = "run-1",
            sortTimestamp = 1.0
        )
        val uploadedEcho = ChatMessage(
            id = "file-file-img-1",
            role = MessageRole.assistant,
            state = MessageState.completed,
            content = "chatgpt image.png",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    fileId = "file-img-1",
                    fileName = "chatgpt image.png",
                    mimeType = "application/octet-stream",
                    downloadUrl = "/api/mobile/files/file-img-1",
                    imageWidth = 1024,
                    imageHeight = 1024,
                    sizeBytes = 2048
                )
            ),
            createdAt = "2026-06-06T12:00:01Z",
            runId = "run-1",
            sortTimestamp = 2.0
        )

        val displayMessages = conversationDisplayMessages(
            messages = listOf(localPreview, uploadedEcho),
            showInvocationProcess = false
        )

        assertEquals(1, displayMessages.size)
        assertEquals("assistant-run-1", displayMessages.single().id)
        assertEquals("chatgpt image.png", displayMessages.single().contentBlocks.single().fileName)
        assertEquals(1.0, displayMessages.single().sortTimestamp)
    }

    @Test
    fun displayMessagesKeepsDistinctImagesWhenOnlyNamesMatch() {
        val firstImage = ChatMessage(
            id = "assistant-run-1",
            role = MessageRole.assistant,
            state = MessageState.completed,
            content = "/Users/test/Downloads/chatgpt image.png",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    fileId = "file-img-1",
                    fileName = "chatgpt image.png",
                    mimeType = "image/png",
                    downloadUrl = "/api/mobile/files/file-img-1",
                    imageWidth = 1024,
                    imageHeight = 1024,
                    sizeBytes = 2048
                )
            ),
            createdAt = "2026-06-06T12:00:00Z",
            runId = "run-1",
            sortTimestamp = 1.0
        )
        val secondImage = ChatMessage(
            id = "assistant-run-2",
            role = MessageRole.assistant,
            state = MessageState.completed,
            content = "/Users/test/Downloads/chatgpt image.png",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    fileId = "file-img-2",
                    fileName = "chatgpt image.png",
                    mimeType = "image/png",
                    downloadUrl = "/api/mobile/files/file-img-2",
                    imageWidth = 1024,
                    imageHeight = 1024,
                    sizeBytes = 2048
                )
            ),
            createdAt = "2026-06-06T12:00:01Z",
            runId = "run-2",
            sortTimestamp = 2.0
        )

        val displayMessages = conversationDisplayMessages(
            messages = listOf(firstImage, secondImage),
            showInvocationProcess = false
        )

        assertEquals(listOf("assistant-run-1", "assistant-run-2"), displayMessages.map { it.id })
    }

    @Test
    fun displayMessagesCoalescesLiveAndHistoryAssistantDuplicate() {
        val liveAssistant = ChatMessage(
            id = "assistant-DA69CD14-756A-4114-9B81-E43686555BD4",
            role = MessageRole.assistant,
            state = MessageState.completed,
            content = "pong 1407",
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = "pong 1407")),
            createdAt = "2026-06-09T08:00:00Z",
            sortTimestamp = 1780992000.0
        )
        val historyAssistant = ChatMessage(
            id = "history:assistant-1407",
            role = MessageRole.assistant,
            state = MessageState.completed,
            content = "pong 1407",
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = "pong 1407")),
            createdAt = "2026-06-09T08:00:02Z",
            sortTimestamp = 1780992002.0
        )

        val displayMessages = conversationDisplayMessages(
            messages = listOf(liveAssistant, historyAssistant),
            showInvocationProcess = false
        )

        assertEquals(1, displayMessages.size)
        assertEquals("history:assistant-1407", displayMessages.single().id)
    }

    @Test
    fun displayMessagesCoalescesLiveUserEchoForLocalSend() {
        val localPrompt = ChatMessage(
            id = "local-user-spider",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "我还是要蜘蛛侠的",
            createdAt = "2026-06-22T09:39:00Z",
            runId = "local-user-client-run-spider",
            sortTimestamp = 100.0
        )
        val liveEcho = ChatMessage(
            id = "live-user-spider",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "我还是要蜘蛛侠的",
            createdAt = "2026-06-22T09:39:01Z",
            runId = "client-run-spider:user",
            sortTimestamp = 100.2
        )

        val displayMessages = conversationDisplayMessages(
            messages = listOf(localPrompt, liveEcho),
            showInvocationProcess = true
        )

        assertEquals(listOf("local-user-client-run-spider"), displayMessages.map { it.runId })
    }

    @Test
    fun displayMessagesMergesCompletedMobileAttachmentIntoLocalTextBubble() {
        val localPrompt = ChatMessage(
            id = "local-user-waterfall",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "分析一下",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "file",
                    text = "album-waterfall.jpg",
                    name = "album-waterfall.jpg",
                    fileName = "album-waterfall.jpg",
                    mimeType = "image/jpeg",
                    downloadUrl = "file:///tmp/album-waterfall.jpg",
                    sourceRunId = "client-run-waterfall"
                )
            ),
            createdAt = "2026-06-22T09:39:00Z",
            runId = "local-user-client-run-waterfall",
            sortTimestamp = 100.0
        )
        val completedAttachment = ChatMessage(
            id = "file-file-waterfall",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "album-waterfall.jpg",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "file",
                    text = "album-waterfall.jpg",
                    name = "album-waterfall.jpg",
                    fileId = "file-waterfall",
                    fileName = "album-waterfall.jpg",
                    mimeType = "image/jpeg",
                    downloadUrl = "/api/mobile/files/file-waterfall",
                    sourceRunId = "client-run-waterfall"
                )
            ),
            createdAt = "2026-06-22T09:39:01Z",
            runId = "file-file-waterfall",
            sortTimestamp = 100.7
        )

        val displayMessages = conversationDisplayMessages(
            messages = listOf(localPrompt, completedAttachment),
            showInvocationProcess = true
        )

        assertEquals(listOf("local-user-client-run-waterfall"), displayMessages.map { it.runId })
        assertEquals("分析一下", displayMessages[0].content)
        assertEquals(listOf("file-waterfall"), displayMessages[0].fileContentBlocks.map { it.fileId })
        assertEquals(listOf("/api/mobile/files/file-waterfall"), displayMessages[0].fileContentBlocks.map { it.downloadUrl })
    }

    @Test
    fun displayMessagesAppendsCompletedMobileAttachmentWhenLocalPlaceholderIsMissing() {
        val localPrompt = ChatMessage(
            id = "local-user-court",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "分析图片",
            createdAt = "2026-06-22T09:39:00Z",
            runId = "local-user-client-run-court",
            sortTimestamp = 100.0
        )
        val completedAttachment = ChatMessage(
            id = "file-file-court",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "album-court.jpg",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "file",
                    text = "album-court.jpg",
                    name = "album-court.jpg",
                    fileId = "file-court",
                    fileName = "album-court.jpg",
                    mimeType = "image/jpeg",
                    downloadUrl = "/api/mobile/files/file-court",
                    sourceRunId = "client-run-court"
                )
            ),
            createdAt = "2026-06-22T09:39:01Z",
            runId = "file-file-court",
            sortTimestamp = 100.1
        )

        val displayMessages = conversationDisplayMessages(
            messages = listOf(localPrompt, completedAttachment),
            showInvocationProcess = true
        )

        assertEquals(listOf("local-user-client-run-court"), displayMessages.map { it.runId })
        assertEquals("分析图片", displayMessages[0].content)
        assertEquals(listOf("file-court"), displayMessages[0].fileContentBlocks.map { it.fileId })
    }

    @Test
    fun displayMessagesKeepsCompletedMobileAttachmentSeparateWithoutSharedRunIdentity() {
        val localPrompt = ChatMessage(
            id = "local-user-waterfall",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "分析一下",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "file",
                    text = "album-waterfall.jpg",
                    name = "album-waterfall.jpg",
                    fileName = "album-waterfall.jpg",
                    mimeType = "image/jpeg",
                    downloadUrl = "file:///tmp/album-waterfall.jpg",
                    sourceRunId = "client-run-waterfall"
                )
            ),
            createdAt = "2026-06-22T09:39:00Z",
            runId = "local-user-client-run-waterfall",
            sortTimestamp = 100.0
        )
        val completedAttachment = ChatMessage(
            id = "file-file-waterfall",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "album-waterfall.jpg",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "file",
                    text = "album-waterfall.jpg",
                    name = "album-waterfall.jpg",
                    fileId = "file-waterfall",
                    fileName = "album-waterfall.jpg",
                    mimeType = "image/jpeg",
                    downloadUrl = "/api/mobile/files/file-waterfall"
                )
            ),
            createdAt = "2026-06-22T09:39:01Z",
            runId = "file-file-waterfall",
            sortTimestamp = 100.1
        )

        val displayMessages = conversationDisplayMessages(
            messages = listOf(localPrompt, completedAttachment),
            showInvocationProcess = true
        )

        assertEquals(
            listOf("local-user-client-run-waterfall", "file-file-waterfall"),
            displayMessages.map { it.runId }
        )
        assertEquals(listOf("file:///tmp/album-waterfall.jpg"), displayMessages[0].fileContentBlocks.map { it.downloadUrl })
        assertEquals(listOf("/api/mobile/files/file-waterfall"), displayMessages[1].fileContentBlocks.map { it.downloadUrl })
    }

    @Test
    fun displayMessagesMovesWaitingAfterSameTurnAttachmentOutput() {
        val localPrompt = ChatMessage(
            id = "local-user-spider-output",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "帮把桌面上的蜘蛛侠的图片发过来",
            createdAt = "2026-06-26T17:00:00Z",
            runId = "local-user-client-run-spider-output",
            sortTimestamp = 1780002000.0,
            timelineStableKey = "local:client-run-spider-output:message:user:010-user",
            timelineOrderKey = "local:client-run-spider-output:010-user",
            timelineIdentityKey = "local:client-run-spider-output:message:user:010-user",
            timelineItemKind = "message:user"
        )
        val waiting = ChatMessage(
            id = "waiting-spider-output",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "正在连接 Relay...",
            createdAt = "2026-06-26T17:00:00Z",
            runId = "client-run-spider-output",
            sortTimestamp = 1780002000.001,
            timelineStableKey = "local:client-run-spider-output:waiting:020-waiting",
            timelineOrderKey = "local:client-run-spider-output:020-waiting",
            timelineIdentityKey = "local:client-run-spider-output:waiting:020-waiting",
            timelineItemKind = "waiting"
        )
        val image = ChatMessage(
            id = "file-file-spider-output",
            role = MessageRole.assistant,
            state = MessageState.completed,
            content = "spiderman.jpg",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    fileId = "file-spider-output",
                    fileName = "spiderman.jpg",
                    mimeType = "image/jpeg",
                    downloadUrl = "/api/mobile/files/file-spider-output",
                    sourceRunId = "client-run-spider-output"
                )
            ),
            createdAt = "2026-06-26T17:00:01Z",
            runId = "file-file-spider-output",
            timelineStableKey = "local:file-file-spider-output:attachment:030-attachment",
            timelineOrderKey = "local:client-run-spider-output:030-attachment",
            timelineIdentityKey = "local:file-file-spider-output:attachment:030-attachment",
            timelineItemKind = "attachment"
        )

        val displayMessages = conversationDisplayMessages(
            messages = listOf(localPrompt, waiting, image),
            showInvocationProcess = true
        )

        assertEquals(
            listOf("local-user-spider-output", "file-file-spider-output", "waiting-spider-output"),
            displayMessages.map { it.id }
        )
    }

    @Test
    fun displayMessagesShowsAtMostOneTransientAssistantTypingBubble() {
        val firstUser = ChatMessage(
            id = "local-user-first",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "第一条",
            createdAt = "2026-06-30T00:48:00Z",
            runId = "local-user-client-run-first",
            timelineOrderKey = "local:client-run-first:010-user",
            timelineIdentityKey = "local:client-run-first:message:user:010-user",
            timelineItemKind = "message:user"
        )
        val firstTyping = ChatMessage(
            id = "waiting-first",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "[[clawlink:typing]]",
            createdAt = "2026-06-30T00:48:00Z",
            runId = "client-run-first",
            timelineOrderKey = "local:client-run-first:020-waiting",
            timelineIdentityKey = "local:client-run-first:waiting:020-waiting",
            timelineItemKind = "waiting"
        )
        val secondUser = ChatMessage(
            id = "local-user-second",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "第二条",
            createdAt = "2026-06-30T00:48:03Z",
            runId = "local-user-client-run-second",
            timelineOrderKey = "local:client-run-second:010-user",
            timelineIdentityKey = "local:client-run-second:message:user:010-user",
            timelineItemKind = "message:user"
        )
        val secondTyping = ChatMessage(
            id = "waiting-second",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "[[clawlink:typing]]",
            createdAt = "2026-06-30T00:48:03Z",
            runId = "client-run-second",
            timelineOrderKey = "local:client-run-second:020-waiting",
            timelineIdentityKey = "local:client-run-second:waiting:020-waiting",
            timelineItemKind = "waiting"
        )

        val displayMessages = conversationDisplayMessages(
            messages = listOf(firstUser, firstTyping, secondUser, secondTyping),
            showInvocationProcess = true
        )

        assertEquals(
            listOf("waiting-second"),
            displayMessages
                .filter { it.role == MessageRole.assistant && it.state == MessageState.streaming }
                .map { it.id }
        )
    }

    @Test
    fun displayMessagesRemovesTransientTypingAfterVisibleAssistantTextInCurrentTurn() {
        val localPrompt = ChatMessage(
            id = "local-user-waterfall",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "现在帮我分析一下这张图",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    fileId = "local-waterfall",
                    fileName = "waterfall.jpg",
                    mimeType = "image/jpeg",
                    sourceRunId = "client-run-waterfall-analysis"
                )
            ),
            createdAt = "2026-06-30T01:30:00Z",
            runId = "local-user-client-run-waterfall-analysis",
            timelineOrderKey = "local:client-run-waterfall-analysis:010-user",
            timelineIdentityKey = "local:client-run-waterfall-analysis:message:user:010-user",
            timelineItemKind = "message:user"
        )
        val assistantText = ChatMessage(
            id = "assistant-waterfall-text",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "这张拍得不错！分析如下：",
            createdAt = "2026-06-30T01:30:03Z",
            runId = "server-run-waterfall-analysis",
            timelineOrderKey = "server:waterfall-analysis:030-assistant",
            timelineIdentityKey = "server:waterfall-analysis:message:assistant:030-assistant",
            timelineItemKind = "message:assistant"
        )
        val staleTyping = ChatMessage(
            id = "waiting-waterfall",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "[[clawlink:typing]]",
            createdAt = "2026-06-30T01:30:00Z",
            runId = "client-run-waterfall-analysis",
            timelineOrderKey = "local:client-run-waterfall-analysis:020-waiting",
            timelineIdentityKey = "local:client-run-waterfall-analysis:waiting:020-waiting",
            timelineItemKind = "waiting"
        )

        val displayMessages = conversationDisplayMessages(
            messages = listOf(localPrompt, assistantText, staleTyping),
            showInvocationProcess = true
        )

        assertEquals(
            listOf("local-user-waterfall", "assistant-waterfall-text"),
            displayMessages.map { it.id }
        )
    }

    @Test
    fun displayMessagesKeepsNonWaitingConnectionStatusBeforeVisibleAssistantText() {
        val localPrompt = ChatMessage(
            id = "local-user-hermes-connect",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "Hermes 连接状态检查",
            createdAt = "2026-06-30T01:35:00Z",
            runId = "local-user-client-run-hermes-connect",
            timelineOrderKey = "local:client-run-hermes-connect:010-user",
            timelineIdentityKey = "local:client-run-hermes-connect:message:user:010-user",
            timelineItemKind = "message:user"
        )
        val connecting = ChatMessage(
            id = "assistant-hermes-connecting",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "正在连接 Mac Hermes Agent...",
            createdAt = "2026-06-30T01:35:01Z",
            runId = "client-run-hermes-connect",
            timelineOrderKey = "local:client-run-hermes-connect:020-status",
            timelineIdentityKey = "local:client-run-hermes-connect:message:assistant:020-status",
            timelineItemKind = "message:assistant"
        )
        val assistantText = ChatMessage(
            id = "assistant-hermes-text",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "Hermes 已开始返回正文。",
            createdAt = "2026-06-30T01:35:02Z",
            runId = "client-run-hermes-connect:assistant",
            timelineOrderKey = "local:client-run-hermes-connect:030-assistant",
            timelineIdentityKey = "local:client-run-hermes-connect:message:assistant:030-assistant",
            timelineItemKind = "message:assistant"
        )

        val displayMessages = conversationDisplayMessages(
            messages = listOf(localPrompt, connecting, assistantText),
            showInvocationProcess = true
        )

        assertEquals(
            listOf("local-user-hermes-connect", "assistant-hermes-connecting", "assistant-hermes-text"),
            displayMessages.map { it.id }
        )
    }

    @Test
    fun structureSignatureIgnoresStreamingTextProgress() {
        val streaming = ChatMessage(
            id = "assistant-streaming",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "Hel",
            runId = "run-1",
            sortTimestamp = 2.0
        )
        val updatedStreaming = streaming.copy(content = "Hello world")

        assertEquals(
            conversationStructureSignature(listOf(streaming)),
            conversationStructureSignature(listOf(updatedStreaming))
        )
    }

    @Test
    fun streamingTailSignatureTracksStreamingTextProgress() {
        val streaming = ChatMessage(
            id = "assistant-streaming",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "Hel",
            runId = "run-1",
            sortTimestamp = 2.0
        )
        val updatedStreaming = streaming.copy(content = "Hello world")

        assert(conversationStreamingTailSignature(listOf(streaming)).isNotBlank())
        assert(
            conversationStreamingTailSignature(listOf(streaming)) !=
            conversationStreamingTailSignature(listOf(updatedStreaming))
        )
    }

    @Test
    fun displayUpdateCoalescingAllowsTailAssistantTextProgress() {
        val user = ChatMessage(
            id = "user-1",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "Say hello",
            runId = "local-user-1",
            sortTimestamp = 1.0
        )
        val streaming = ChatMessage(
            id = "assistant-streaming",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "Hel",
            runId = "run-1",
            sortTimestamp = 2.0
        )
        val current = ChatState(messages = listOf(user, streaming), isStreaming = true)

        assert(
            shouldCoalesceChatDisplayUpdate(
                current,
                current.copy(messages = listOf(user, streaming.copy(content = "Hello")))
            )
        )
        val completed = streaming.copy(state = MessageState.completed, content = "Hel")
        val completedState = ChatState(messages = listOf(user, completed), isStreaming = true)
        assert(
            shouldCoalesceChatDisplayUpdate(
                completedState,
                completedState.copy(messages = listOf(user, completed.copy(content = "Hello")))
            )
        )
        assert(
            !shouldCoalesceChatDisplayUpdate(
                completedState,
                completedState.copy(
                    messages = listOf(
                        user,
                        completed.copy(
                            contentBlocks = listOf(
                                RelayChatContentBlock(
                                    type = "image",
                                    fileId = "file-img-1",
                                    fileName = "result.png",
                                    mimeType = "image/png",
                                    downloadUrl = "/api/mobile/files/file-img-1"
                                )
                            )
                        )
                    )
                )
            )
        )
        assert(
            !shouldCoalesceChatDisplayUpdate(
                current,
                current.copy(messages = listOf(user, streaming.copy(content = "Hello", state = MessageState.completed)))
            )
        )
        assert(
            !shouldCoalesceChatDisplayUpdate(
                current,
                current.copy(messages = listOf(user, streaming, streaming.copy(id = "assistant-2", content = "Next")))
            )
        )
    }
}
