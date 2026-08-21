package com.rethinkingstudio.clawlink.ui.screens.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import com.rethinkingstudio.clawlink.core.state.chat.ChatState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatConversationPresentationTest {
    @Test
    fun completedToolResultHidesItsSeparateHistoryCallCard() {
        val call = ChatMessage(
            id = "tool-call-row",
            role = MessageRole.tool,
            contentBlocks = listOf(
                RelayChatContentBlock(type = "tool_call", name = "image", toolCallId = "call-image")
            ),
            timelineIdentityKey = "source:8",
            timelineOrderKey = "v4|0|00000000000000000008|30|call",
            timelineItemKind = "tool"
        )
        val result = ChatMessage(
            id = "tool-result-row",
            role = MessageRole.tool,
            content = "image inspected",
            contentBlocks = listOf(
                RelayChatContentBlock(type = "text", text = "image inspected", toolCallId = "call-image")
            ),
            timelineIdentityKey = "source:9",
            timelineOrderKey = "v4|0|00000000000000000009|30|result",
            timelineItemKind = "tool"
        )

        val displayMessages = conversationDisplayMessages(listOf(call, result), showInvocationProcess = true)

        assertEquals(listOf("tool-result-row"), displayMessages.map { it.id })
    }

    @Test
    fun confirmedImageTurnKeepsRelayOrderWhenStaleToolSharesAttachmentTurn() {
        fun order(namespace: Int, sequence: Long, slot: Int, id: String): String =
            "v5|$namespace|${sequence.toString().padStart(20, '0')}|00000000000000000000|${slot.toString().padStart(2, '0')}|$id"

        fun message(
            id: String,
            role: MessageRole,
            namespace: Int,
            sequence: Long,
            slot: Int,
            runId: String,
            turnId: String,
            content: String,
            blocks: List<RelayChatContentBlock> = listOf(RelayChatContentBlock(type = "text", text = content))
        ) = ChatMessage(
            id = id,
            role = role,
            content = content,
            contentBlocks = blocks,
            runId = runId,
            turnId = turnId,
            conversationSeq = sequence,
            seq = sequence,
            conversationSeqState = "committed",
            timelineOrderKey = order(namespace, sequence, slot, id),
            timelineIdentityKey = "v1|main|${role.name}|$id",
            timelineItemKind = if (role == MessageRole.tool) "tool" else "message:${role.name}",
            source = "history"
        )

        val attachmentTurn = "attachment-f0f2b02ff0c8b3ebd24dd05328d1908450b3e9d5b6867ed7ad81ec6fb796fcec"
        val fileRun = "file-file_a60b9b239c8d459b94a5c914f3ed356b"
        val imageUser = message(
            id = "image-user-4905",
            role = MessageRole.user,
            namespace = 0,
            sequence = 4905,
            slot = 10,
            runId = fileRun,
            turnId = attachmentTurn,
            content = "分析一下这张图",
            blocks = listOf(
                RelayChatContentBlock(type = "text", text = "分析一下这张图"),
                RelayChatContentBlock(
                    type = "image",
                    fileId = "file-a60b9b239c8d459b94a5c914f3ed356b",
                    fileName = "photo.jpg",
                    mimeType = "image/jpeg",
                    downloadUrl = "/api/mobile/files/file-a60b9b239c8d459b94a5c914f3ed356b",
                    sourceRunId = fileRun
                )
            )
        )
        val rows = listOf(
            message(
                id = "stale-tool-ns0-seq10",
                role = MessageRole.tool,
                namespace = 0,
                sequence = 10,
                slot = 30,
                runId = attachmentTurn,
                turnId = attachmentTurn,
                content = "stale tool",
                blocks = listOf(RelayChatContentBlock(type = "tool_result", text = "stale tool"))
            ),
            message("old-user-4895", MessageRole.user, 0, 4895, 10, "old-turn", "old-turn", "旧问题"),
            message("old-answer-4896", MessageRole.assistant, 0, 4896, 50, "old-turn", "old-turn", "旧回答"),
            imageUser,
            message(
                id = "live-tool-ns1-seq5",
                role = MessageRole.tool,
                namespace = 1,
                sequence = 5,
                slot = 30,
                runId = attachmentTurn,
                turnId = attachmentTurn,
                content = "live tool",
                blocks = listOf(RelayChatContentBlock(type = "tool_result", text = "live tool"))
            ),
            message("live-answer-ns1-seq5", MessageRole.assistant, 1, 5, 50, attachmentTurn, attachmentTurn, "图片分析结果")
        )

        val visible = conversationDisplayMessages(rows, showInvocationProcess = false)

        assertEquals(
            listOf("old-user-4895", "old-answer-4896", "image-user-4905", "live-answer-ns1-seq5"),
            visible.map(ChatMessage::id)
        )
    }

    @Test
    fun unpairedGatewayStateHidesCachedMessagesFromPreviousGateway() {
        val cachedMessage = ChatMessage(
            id = "cached-user-message",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "旧网关聊天记录"
        )

        val displayMessages = conversationDisplayMessagesForGatewayState(
            hasSelectedGateway = false,
            messages = listOf(cachedMessage),
            showInvocationProcess = false
        )

        assertEquals(emptyList<ChatMessage>(), displayMessages)
    }

    @Test
    fun pairedGatewayStateStillDisplaysCurrentGatewayMessages() {
        val currentMessage = ChatMessage(
            id = "current-user-message",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "当前网关聊天记录"
        )

        val displayMessages = conversationDisplayMessagesForGatewayState(
            hasSelectedGateway = true,
            messages = listOf(currentMessage),
            showInvocationProcess = false
        )

        assertEquals(listOf(currentMessage), displayMessages)
    }

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
    fun displayMessagesPreservesDuplicateImageNamesWhenOneStableIdIsMissing() {
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

        assertEquals(2, displayMessages.size)
        assertEquals(listOf(localPreview.id, uploadedEcho.id), displayMessages.map { it.id })
        assertEquals(listOf(1.0, 2.0), displayMessages.map { it.sortTimestamp })
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
    fun displayMessagesPreservesIdenticalNearbyAssistantMessagesWithoutCanonicalIdentity() {
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

        assertEquals(listOf(liveAssistant.id, historyAssistant.id), displayMessages.map { it.id })
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
    fun displayMessagesPreservesCoreSlotsAfterLocalAndCanonicalEchoesCoalesce() {
        fun canonical(
            id: String,
            role: MessageRole,
            runId: String,
            content: String,
            conversationSequence: Int,
            slot: Int
        ) = ChatMessage(
            id = id,
            role = role,
            state = MessageState.completed,
            content = content,
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = content)),
            runId = runId,
            turnId = runId,
            timelineOrderKey = "v5|1|${conversationSequence.toString().padStart(20, '0')}|00000000000000000000|${slot.toString().padStart(2, '0')}|$id",
            timelineIdentityKey = "v1|mobile-hermes|message|${role.name}|$id",
            timelineItemKind = "message:${role.name}"
        )
        fun local(id: String, runId: String, content: String) = ChatMessage(
            id = id,
            role = MessageRole.user,
            state = MessageState.completed,
            content = content,
            runId = "local-user-$runId",
            turnId = runId,
            timelineOrderKey = "local:$runId|10|$id",
            timelineIdentityKey = "local:message:user:$runId",
            timelineItemKind = "message:user",
            source = "local"
        )

        val displayMessages = conversationDisplayMessages(
            messages = listOf(
                canonical("hermes-user", MessageRole.user, "hermes-run", "Reply exactly HERMESNEW0811", 2, 10),
                canonical("hermes-answer", MessageRole.assistant, "hermes-run", "HERMESNEW0811", 2, 50),
                local("local-hello", "hello-run", "你好"),
                local("local-new", "new-run", "/new"),
                canonical("relay-new", MessageRole.user, "new-run", "/new", 1, 10),
                canonical("new-answer", MessageRole.assistant, "new-run", "(^_^)v New session started!", 1, 50),
                canonical("relay-hello", MessageRole.user, "hello-run", "你好", 3, 10),
                canonical("hello-answer", MessageRole.assistant, "hello-run", "你好！有什么可以帮你的？", 3, 50)
            ),
            showInvocationProcess = true
        )

        assertEquals(
            listOf("hermes-user", "hermes-answer", "local-new", "new-answer", "local-hello", "hello-answer"),
            displayMessages.map { it.id }
        )
        assertEquals(
            listOf("Reply exactly HERMESNEW0811", "HERMESNEW0811", "/new", "(^_^)v New session started!", "你好", "你好！有什么可以帮你的？"),
            displayMessages.map { it.content }
        )
    }

    @Test
    fun displayMessagesPreservesCoreOrderAcrossMixedHermesOrderDomains() {
        fun message(
            id: String,
            role: MessageRole,
            runId: String,
            content: String,
            orderKey: String,
            localTurnOrder: Long? = null
        ) = ChatMessage(
            id = id,
            role = role,
            state = MessageState.completed,
            content = content,
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = content)),
            runId = runId,
            turnId = runId,
            timelineOrderKey = orderKey,
            timelineIdentityKey = "v1|mobile-hermes|message|${role.name}|$id",
            timelineItemKind = "message:${role.name}",
            localTurnOrder = localTurnOrder
        )

        val displayMessages = conversationDisplayMessages(
            messages = listOf(
                message("new-user", MessageRole.user, "new-run", "/new", "v5|0|00000001786421720969|00000000000000000000|10|new-user", 0),
                message("new-answer", MessageRole.assistant, "new-run", "New session started!", "v5|0|00001786421722525000|00000000000000000000|50|new-answer"),
                message("hello-user", MessageRole.user, "hello-run", "hello", "v5|0|00000000000000004349|00000000000000000000|10|hello-user", 1),
                message("hello-answer", MessageRole.assistant, "hello-run", "hello-answer", "v5|0|00000000000000004350|00000000000000000000|50|hello-answer"),
                message("ping-user", MessageRole.user, "ping-run", "ping", "v5|0|00000000000000004351|00000000000000000000|10|ping-user", 2),
                message("ping-answer", MessageRole.assistant, "ping-run", "pong", "v5|1|00000000000000000003|00000000000000000000|50|ping-answer")
            ),
            showInvocationProcess = true
        )

        assertEquals(
            listOf("/new", "New session started!", "hello", "hello-answer", "ping", "pong"),
            displayMessages.map { it.content }
        )
    }

    @Test
    fun displayMessagesMergesCompletedMobileAttachmentIntoLocalTextBubble() {
        val localImageFile = File.createTempFile("album-waterfall", ".jpg").apply { deleteOnExit() }
        val localImageUrl = localImageFile.toURI().toString()
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
                    downloadUrl = localImageUrl,
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
        assertEquals(listOf(localImageUrl), displayMessages[0].fileContentBlocks.map { it.thumbnailUrl })
        assertEquals(listOf(localImageUrl), displayMessages[0].fileContentBlocks.map { it.preferredImagePreviewURLString })
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
    fun displayMessagesCoalescesCanonicalMobileAttachmentAndTextEchoBySourceRunId() {
        val runId = "client-run-hermes-video"
        val prompt = "帮我分析一下"
        val completedAttachment = ChatMessage(
            id = "file-file-basketball",
            role = MessageRole.user,
            state = MessageState.completed,
            content = prompt,
            contentBlocks = listOf(
                RelayChatContentBlock(type = "text", text = prompt),
                RelayChatContentBlock(
                    type = "file",
                    text = "album-basketball.mp4",
                    name = "album-basketball.mp4",
                    fileId = "file-basketball",
                    fileName = "album-basketball.mp4",
                    mimeType = "video/mp4",
                    downloadUrl = "/api/mobile/files/file-basketball",
                    sourceRunId = runId
                )
            ),
            createdAt = "2026-06-30T11:09:00Z",
            runId = "file-file-basketball",
            sortTimestamp = 200.0,
            timelineOrderKey = "v1|00000001782800000000|40|000000|file-basketball",
            timelineIdentityKey = "v1|main|attachment|user|file-basketball",
            timelineItemKind = "attachment"
        )
        val textEcho = ChatMessage(
            id = "server-user-basketball",
            role = MessageRole.user,
            state = MessageState.completed,
            content = prompt,
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = prompt)),
            createdAt = "2026-06-30T11:09:00Z",
            runId = runId,
            sortTimestamp = 200.1,
            timelineOrderKey = "v1|00000001782800000000|10|000000|server-user-basketball",
            timelineIdentityKey = "v1|main|message|user|server-user-basketball",
            timelineItemKind = "message:user"
        )

        val displayMessages = conversationDisplayMessages(
            messages = listOf(completedAttachment, textEcho),
            showInvocationProcess = true
        )

        assertEquals(listOf("file-file-basketball"), displayMessages.map { it.id })
        assertEquals(prompt, displayMessages.single().content)
        assertEquals(listOf("file-basketball"), displayMessages.single().fileContentBlocks.map { it.fileId })
    }

    @Test
    fun displayMessagesUsesOneCanonicalTextProjectionForIosImageTurn() {
        val runId = "attachment-ios-image"
        val prompt = "分析一下这张图"
        val fileName = "album-B4358473-17EA-46AB-9319-B041A422E3C9.jpg"
        val media = ChatMessage(
            id = "user-ios-image",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "$prompt\n\n$fileName\n\n$prompt",
            contentBlocks = listOf(
                RelayChatContentBlock(type = "text", text = "$prompt\n\n$fileName\n\n$prompt"),
                RelayChatContentBlock(type = "text", text = "$prompt\n\n$fileName"),
                RelayChatContentBlock(type = "text", contentBlockId = "blk_prompt", text = prompt),
                RelayChatContentBlock(
                    type = "image",
                    contentBlockId = "blk_image",
                    attachmentId = "att_image",
                    fileId = "file_image",
                    fileName = fileName,
                    text = fileName,
                    mimeType = "image/jpeg",
                    downloadUrl = "/api/mobile/files/file_image",
                    sourceRunId = runId
                )
            ),
            runId = "local-user-$runId",
            timelineOrderKey = "v1|00000000000000000059|40|000000|file_image",
            timelineIdentityKey = "v1|main|message|user|srv_ios_image",
            timelineItemKind = "message:user",
            source = "local"
        )
        val historyEcho = ChatMessage(
            id = "history-ios-image",
            role = MessageRole.user,
            state = MessageState.completed,
            content = prompt,
            contentBlocks = listOf(
                RelayChatContentBlock(type = "text", contentBlockId = "blk_history_prompt", text = prompt)
            ),
            runId = "$runId:user",
            timelineOrderKey = "v1|00000000000000000059|10|000000|history-ios-image",
            timelineIdentityKey = "v1|main|message|user|srv_history_ios_image",
            timelineItemKind = "message:user",
            source = "history"
        )

        val visible = conversationDisplayMessages(
            messages = listOf(historyEcho, media),
            showInvocationProcess = true
        ).single()

        assertEquals("user-ios-image", visible.id)
        assertEquals(prompt, visible.content)
        assertEquals(listOf("blk_prompt"), visible.contentBlocks.filter { it.isTextBlock }.map { it.contentBlockId })
        assertEquals(listOf("blk_image"), visible.contentBlocks.filter { it.isFileBlock }.map { it.contentBlockId })
    }

    @Test
    fun displayMessagesPrefersCanonicalHistoryPromptOverUnkeyedMediaLabel() {
        val runId = "attachment-ios-file-label"
        val prompt = "分析一下这张图"
        val fileName = "album-ios.jpg"
        val historyPrompt = ChatMessage(
            id = "history-ios-file-label",
            role = MessageRole.user,
            state = MessageState.completed,
            content = prompt,
            contentBlocks = listOf(
                RelayChatContentBlock(type = "text", contentBlockId = "blk_history_prompt", text = prompt)
            ),
            runId = "$runId:user",
            timelineOrderKey = "v1|00000000000000000060|10|000000|history-ios-file-label",
            timelineIdentityKey = "v1|main|message|user|srv_history_ios_file_label",
            timelineItemKind = "message:user",
            source = "history"
        )
        val media = ChatMessage(
            id = "media-ios-file-label",
            role = MessageRole.user,
            state = MessageState.completed,
            content = fileName,
            contentBlocks = listOf(
                RelayChatContentBlock(type = "text", text = fileName),
                RelayChatContentBlock(
                    type = "image",
                    contentBlockId = "blk_image",
                    fileId = "file_image",
                    fileName = fileName,
                    text = fileName,
                    sourceRunId = runId
                )
            ),
            runId = "local-user-$runId",
            timelineOrderKey = "v1|00000000000000000060|40|000000|file_image",
            timelineIdentityKey = "v1|main|attachment|user|file_image",
            timelineItemKind = "attachment",
            source = "local"
        )

        val visible = conversationDisplayMessages(listOf(historyPrompt, media), true).single()

        assertEquals("media-ios-file-label", visible.id)
        assertEquals(prompt, visible.content)
        assertEquals(listOf("blk_history_prompt"), visible.contentBlocks.filter { it.isTextBlock }.map { it.contentBlockId })
        assertEquals(listOf("blk_image"), visible.contentBlocks.filter { it.isFileBlock }.map { it.contentBlockId })
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
            // Core owns the local overlay projection; presentation must keep
            // the already projected user -> output -> waiting slots unchanged.
            messages = listOf(localPrompt, image, waiting),
            showInvocationProcess = true
        )

        assertEquals(
            listOf("local-user-spider-output", "file-file-spider-output", "waiting-spider-output"),
            displayMessages.map { it.id }
        )
    }

    @Test
    fun displayMessagesKeepsTransientTypingBubblesForDistinctTurns() {
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
            listOf("waiting-first", "waiting-second"),
            displayMessages
                .filter { it.role == MessageRole.assistant && it.state == MessageState.streaming }
                .map { it.id }
        )
    }

    @Test
    fun displayMessagesCoalescesDuplicateTransientTypingWithinSameStableTurn() {
        val first = ChatMessage(
            id = "waiting-same-turn-first",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "[[clawlink:typing]]",
            runId = "client-run-same-turn",
            turnId = "client-run-same-turn",
            timelineOrderKey = "local:client-run-same-turn:020-waiting-a",
            timelineIdentityKey = "local:client-run-same-turn:waiting:a",
            timelineItemKind = "waiting"
        )
        val second = first.copy(
            id = "waiting-same-turn-second",
            timelineOrderKey = "local:client-run-same-turn:020-waiting-b",
            timelineIdentityKey = "local:client-run-same-turn:waiting:b"
        )

        val displayMessages = conversationDisplayMessages(
            messages = listOf(first, second),
            showInvocationProcess = true
        )

        assertEquals(listOf("waiting-same-turn-second"), displayMessages.map(ChatMessage::id))
    }

    @Test
    fun displayMessagesCoalescesWaitingWhenStableTurnAliasMatches() {
        val first = ChatMessage(
            id = "waiting-provider-run-a",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "[[clawlink:typing]]",
            contentBlocks = listOf(
                RelayChatContentBlock(type = "text", sourceRunId = "provider-run-a")
            ),
            runId = "provider-run-a",
            turnId = "client-turn-shared",
            timelineOrderKey = "local:client-turn-shared:020-waiting-a",
            timelineIdentityKey = "local:client-turn-shared:waiting:a",
            timelineItemKind = "waiting"
        )
        val second = first.copy(
            id = "waiting-provider-run-b",
            contentBlocks = listOf(
                RelayChatContentBlock(type = "text", sourceRunId = "provider-run-b")
            ),
            runId = "provider-run-b",
            timelineOrderKey = "local:client-turn-shared:020-waiting-b",
            timelineIdentityKey = "local:client-turn-shared:waiting:b"
        )

        val displayMessages = conversationDisplayMessages(
            messages = listOf(first, second),
            showInvocationProcess = true
        )

        assertEquals(listOf("waiting-provider-run-b"), displayMessages.map(ChatMessage::id))
    }

    @Test
    fun displayMessagesDoesNotResolveWaitingFromUnrelatedAssistantText() {
        val waiting = ChatMessage(
            id = "waiting-turn-a",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "[[clawlink:typing]]",
            runId = "turn-a",
            turnId = "turn-a",
            timelineOrderKey = "local:turn-a:020-waiting",
            timelineIdentityKey = "local:turn-a:waiting",
            timelineItemKind = "waiting"
        )
        val unrelatedAnswer = ChatMessage(
            id = "answer-turn-b",
            role = MessageRole.assistant,
            state = MessageState.completed,
            content = "turn b answer",
            runId = "turn-b",
            turnId = "turn-b",
            timelineOrderKey = "v5|0|00000000000000000001|00000000000000000000|50|answer-turn-b",
            timelineIdentityKey = "v1|main|message|assistant|answer-turn-b",
            timelineItemKind = "message:assistant"
        )

        val displayMessages = conversationDisplayMessages(
            messages = listOf(waiting, unrelatedAnswer),
            showInvocationProcess = true
        )

        assertEquals(listOf("waiting-turn-a", "answer-turn-b"), displayMessages.map(ChatMessage::id))
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
            turnId = "client-run-waterfall-analysis",
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
    fun conversationListKeysUseCanonicalIdentityWhenToolMessageIdsCollide() {
        val first = ChatMessage(
            id = "tool-shared-id",
            role = MessageRole.tool,
            content = "first tool row",
            timelineOrderKey = "v5|0|00000000000000000001|00000000000000000000|30|tool-a",
            timelineIdentityKey = "v1|main|tool|call-a",
            timelineItemKind = "tool"
        )
        val second = first.copy(
            content = "second tool row",
            timelineOrderKey = "v5|0|00000000000000000002|00000000000000000000|30|tool-b",
            timelineIdentityKey = "v1|main|tool|call-b"
        )

        val items = conversationMessageListItems(listOf(first, second), "gateway::main")

        assertEquals(2, items.map(ConversationMessageListItem::stableKey).toSet().size)
        assertEquals(
            listOf("gateway::main:timeline:v1|main|tool|call-a", "gateway::main:timeline:v1|main|tool|call-b"),
            items.map(ConversationMessageListItem::stableKey)
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
    fun displayMessagesCoalescesUserPrefixedLiveImagePromptEchoForLocalSend() {
        val runId = "client-run-android-hermes-image"
        val prompt = "分析一下这张图"
        val localPrompt = ChatMessage(
            id = "local-user-image-prompt",
            role = MessageRole.user,
            state = MessageState.completed,
            content = prompt,
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    text = "dinner.png",
                    fileId = "file-dinner",
                    fileName = "dinner.png",
                    mimeType = "image/png",
                    downloadUrl = "/api/mobile/files/file-dinner",
                    sourceRunId = runId
                )
            ),
            createdAt = "2026-06-30T08:08:00Z",
            runId = "local-user-$runId",
            sortTimestamp = 100.0
        )
        val liveEcho = ChatMessage(
            id = "relay-user-image-echo",
            role = MessageRole.user,
            state = MessageState.completed,
            content = prompt,
            createdAt = "2026-06-30T08:08:00Z",
            runId = "user-$runId",
            sortTimestamp = 100.2
        )

        val displayMessages = conversationDisplayMessages(
            messages = listOf(localPrompt, liveEcho),
            showInvocationProcess = true
        )

        assertEquals(listOf("local-user-image-prompt"), displayMessages.map { it.id })
        assertEquals(listOf(prompt), displayMessages.single().contentBlocks.filter { it.isTextBlock }.map { it.text })
        assertEquals(listOf("file-dinner"), displayMessages.single().fileContentBlocks.map { it.fileId })
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
    fun structureSignatureIgnoresHermesTextBlockProgress() {
        val streaming = ChatMessage(
            id = "assistant-streaming",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "Hel",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "text",
                    contentBlockId = "assistant-part-1",
                    text = "Hel",
                    contentHash = "hash-hel"
                )
            ),
            runId = "run-1",
            seq = 10,
            sortTimestamp = 2.0
        )
        val updatedStreaming = streaming.copy(
            content = "Hello world",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "text",
                    contentBlockId = "assistant-part-1",
                    text = "Hello world",
                    contentHash = "hash-hello-world"
                )
            ),
            seq = 11
        )

        assertEquals(
            conversationStructureSignature(listOf(streaming)),
            conversationStructureSignature(listOf(updatedStreaming))
        )
        assert(
            shouldCoalesceChatDisplayUpdate(
                ChatState(messages = listOf(streaming), isStreaming = true),
                ChatState(messages = listOf(updatedStreaming), isStreaming = true)
            )
        )
    }

    @Test
    fun streamingTextCoalescingDoesNotHideContentBlockIdentityChanges() {
        val streaming = ChatMessage(
            id = "assistant-streaming",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "Hel",
            contentBlocks = listOf(
                RelayChatContentBlock(type = "text", contentBlockId = "part-1", text = "Hel")
            ),
            runId = "run-1"
        )
        val differentPart = streaming.copy(
            content = "Hello",
            contentBlocks = listOf(
                RelayChatContentBlock(type = "text", contentBlockId = "part-2", text = "Hello")
            )
        )

        assert(
            !shouldCoalesceChatDisplayUpdate(
                ChatState(messages = listOf(streaming), isStreaming = true),
                ChatState(messages = listOf(differentPart), isStreaming = true)
            )
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
