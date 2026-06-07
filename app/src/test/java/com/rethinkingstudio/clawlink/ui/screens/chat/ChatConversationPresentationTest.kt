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
    fun displayUpdateCoalescingOnlyAllowsTailStreamingTextProgress() {
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
