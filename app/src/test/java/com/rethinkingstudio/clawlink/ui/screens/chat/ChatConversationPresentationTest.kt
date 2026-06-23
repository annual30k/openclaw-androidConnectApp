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
