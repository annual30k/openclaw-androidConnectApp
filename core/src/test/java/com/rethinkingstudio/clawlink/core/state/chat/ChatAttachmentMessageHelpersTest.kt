package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.ComposerAttachmentDraft
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import com.rethinkingstudio.clawlink.core.network.dto.RelayFileTransferItem
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatAttachmentMessageHelpersTest {
    @Test
    fun composerAttachmentUploadContentBlockCarriesLocalAttachmentId() {
        val attachment = ComposerAttachmentDraft(
            id = "attachment-local-1",
            fileUri = "file:///tmp/image.png",
            fileName = "image.png",
            mimeType = "image/png",
            sizeBytes = 42,
            imageWidth = 320,
            imageHeight = 240
        )

        val block = makeComposerAttachmentUploadContentBlock(
            attachment = attachment,
            gatewayId = "gateway-1",
            sessionKey = "main",
            senderDisplayName = "Mac",
            statusText = "上传中 10%",
            downloadUrlString = attachment.fileUri,
            sourceRunId = "client-run-1"
        )

        assertEquals("attachment-local-1", block.attachmentId)
    }

    @Test
    fun uploadedAttachmentContentBlockAcceptsAttachmentIdOverride() {
        val record = RelayFileTransferItem(
            fileId = "file-1",
            gatewayId = "gateway-1",
            sessionKey = "main",
            fileName = "image.png",
            mimeType = "image/png",
            sizeBytes = 42,
            sha256 = "deadbeef",
            origin = "mobile",
            createdAt = "2026-07-01T00:00:00.000Z",
            updatedAt = "2026-07-01T00:00:01.000Z",
            expiresAt = "2026-07-02T00:00:00.000Z",
            status = "completed",
            storagePath = "/relay/files/file-1",
            downloadPath = "/api/mobile/files/file-1",
            chunkSize = 1024,
            totalChunks = 1
        )

        val block = makeUploadedAttachmentContentBlock(
            record = record,
            localDownloadUrlString = "file:///tmp/image.png",
            sourceRunIdOverride = "client-run-1",
            attachmentIdOverride = "attachment-local-1"
        )

        assertEquals("attachment-local-1", block.attachmentId)
    }

    @Test
    fun mergeCompletedFileMessageKeepsLocalPreviewWhenIncomingBlockOmitsAttachmentId() {
        val existing = ChatMessage(
            id = "local-user",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "image.png",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    attachmentId = "attachment-local-1",
                    fileId = "file-1",
                    fileName = "image.png",
                    mimeType = "image/png",
                    downloadUrl = "file:///tmp/image.png"
                )
            ),
            runId = "local-user-run-1",
            sortTimestamp = 1.0
        )
        val completed = ChatMessage(
            id = "server-user",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "image.png",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    fileId = "file-1",
                    fileName = "image.png",
                    mimeType = "image/png",
                    downloadUrl = "/api/mobile/files/file-1"
                )
            ),
            runId = "server-user-run-1",
            sortTimestamp = 2.0
        )

        val merged = mergeCompletedFileMessage(existing = existing, completed = completed)

        assertEquals("file:///tmp/image.png", merged.fileContentBlocks.single().downloadUrl)
        assertEquals("/api/mobile/files/file-1", merged.fileContentBlocks.single().downloadPath)
    }

    @Test
    fun mergeCompletedFileMessageKeepsLocalPreviewWhenAttachmentIdsDifferButFileIdMatches() {
        val existing = ChatMessage(
            id = "local-user",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "image.png",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    attachmentId = "attachment-local-1",
                    fileId = "file-1",
                    fileName = "image.png",
                    mimeType = "image/png",
                    downloadUrl = "file:///tmp/image.png"
                )
            ),
            runId = "local-user-run-1",
            sortTimestamp = 1.0
        )
        val completed = ChatMessage(
            id = "server-user",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "image.png",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    attachmentId = "server-attachment-9",
                    fileId = "file-1",
                    fileName = "image.png",
                    mimeType = "image/png",
                    downloadUrl = "/api/mobile/files/file-1"
                )
            ),
            runId = "server-user-run-1",
            sortTimestamp = 2.0
        )

        val merged = mergeCompletedFileMessage(existing = existing, completed = completed)

        assertEquals("file:///tmp/image.png", merged.fileContentBlocks.single().downloadUrl)
        assertEquals("/api/mobile/files/file-1", merged.fileContentBlocks.single().downloadPath)
    }

    @Test
    fun stripsRelayMediaAttachmentReferencesFromChatMessageText() {
        val text = """
            分析一下这张照片

            [media attached: /Users/example/.openclaw/media/outbound/run/photo.jpg (image/jpeg) | /Users/example/.openclaw/media/outbound/run/photo.jpg]
        """.trimIndent()

        assertEquals("分析一下这张照片", sanitizeChatMessageText(text))
    }

    @Test
    fun extractsRelayMediaAttachmentReferenceFileNames() {
        val text = """
            分析一下这张照片

            [media attached: /Users/example/.openclaw/media/outbound/run/photo.jpg (image/jpeg) | /Users/example/.openclaw/media/outbound/run/photo.jpg]
        """.trimIndent()

        assertEquals(listOf("photo.jpg"), chatMediaAttachmentReferenceFileNames(text))
    }

    @Test
    fun stripsCompactMediaUriAttachmentReferencesFromChatMessageText() {
        val text = """
            分析一下这张图片

            [media attached: media://inbound/album-8E28059F-104B-43E1-8059-2E97E07F0E1B---d786f4a0-bb83-4853-97ae-cb7a604326e0.heic]
        """.trimIndent()

        assertEquals("分析一下这张图片", sanitizeChatMessageText(text))
        assertEquals(
            listOf("album-8E28059F-104B-43E1-8059-2E97E07F0E1B---d786f4a0-bb83-4853-97ae-cb7a604326e0.heic"),
            chatMediaAttachmentReferenceFileNames(text)
        )
    }

    @Test
    fun stripsOpenClawMediaControlReferencesFromChatMessageText() {
        val text = """
            桌面截图已发送到你手机上了
            MEDIA:/Users/example/.openclaw/tmp/codex-shot.png
        """.trimIndent()

        assertEquals("桌面截图已发送到你手机上了", sanitizeChatMessageText(text))
    }

    @Test
    fun stripsRelayFileAttachmentReferencesFromChatMessageText() {
        val text = """
            请看看这个文件

            [file attached: /tmp/report.pdf]
        """.trimIndent()

        assertEquals("请看看这个文件", sanitizeChatMessageText(text))
    }

    @Test
    fun sanitizesHermesRuntimeContextFromTextBlocks() {
        val text = """
            昨天打完篮球小腿的前侧很痛这是怎么回事

            [Hermes runtime context]
            Current runtime: model=mimo-v2.5-pro, provider=Xiaomi MiMo.
            If the user asks which model or provider is currently being used, answer from this runtime context.

            [ClawConnect mobile bridge] You are connected to a mobile chat client through ClawConnect.
        """.trimIndent()

        val blocks = sanitizeChatContentBlocks(listOf(RelayChatContentBlock(type = "text", text = text)))

        assertEquals("昨天打完篮球小腿的前侧很痛这是怎么回事", blocks.single().text)
    }

    @Test
    fun coalescesLegacyMediaTextProjectionIntoCanonicalStableBlock() {
        val blocks = sanitizeChatContentBlocks(
            listOf(
                RelayChatContentBlock(type = "text", text = "分析一下这个图片\n\n[media attached: /tmp/22.JPG (image/jpeg) | /tmp/22.JPG]"),
                RelayChatContentBlock(type = "text", contentBlockId = "blk_prompt", text = "分析一下这个图片"),
                RelayChatContentBlock(type = "image", contentBlockId = "blk_image", fileId = "file_image")
            )
        )

        assertEquals(listOf("blk_prompt"), blocks.filter { it.isTextBlock }.map { it.contentBlockId })
        assertEquals("file_image", blocks.first { it.isFileBlock }.fileId)
    }

    @Test
    fun dropsAllUnidentifiedTextProjectionsWhenCanonicalMediaTextExists() {
        val prompt = "分析一下这张图"
        val fileName = "album-B4358473-17EA-46AB-9319-B041A422E3C9.jpg"
        val blocks = sanitizeChatContentBlocks(
            listOf(
                RelayChatContentBlock(type = "text", text = "$prompt\n\n$fileName\n\n$prompt"),
                RelayChatContentBlock(type = "text", text = "$prompt\n\n$fileName"),
                RelayChatContentBlock(type = "text", contentBlockId = "blk_prompt", text = prompt),
                RelayChatContentBlock(
                    type = "image",
                    contentBlockId = "blk_image",
                    attachmentId = "att_image",
                    fileId = "file_image",
                    fileName = fileName,
                    text = fileName
                )
            )
        )

        assertEquals(listOf("blk_prompt"), blocks.filter { it.isTextBlock }.map { it.contentBlockId })
        assertEquals(listOf("blk_image"), blocks.filter { it.isFileBlock }.map { it.contentBlockId })
    }
}
