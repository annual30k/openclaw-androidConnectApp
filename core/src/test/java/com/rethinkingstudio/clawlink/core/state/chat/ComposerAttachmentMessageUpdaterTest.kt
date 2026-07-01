package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.AttachmentUploadPhase
import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.ComposerAttachmentDraft
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import com.rethinkingstudio.clawlink.core.network.dto.RelayFileTransferItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerAttachmentMessageUpdaterTest {
    @Test
    fun attachmentPlaceholderUsesCanonicalTimelineKeysWithoutTextTurn() {
        val attachment = ComposerAttachmentDraft(
            id = "attachment-1",
            fileUri = "file:///tmp/report.pdf",
            fileName = "report.pdf",
            mimeType = "application/pdf",
            sizeBytes = 12
        )

        val messages = ComposerAttachmentMessageUpdater.begin(
            currentMessages = emptyList(),
            attachments = listOf(attachment),
            gatewayId = "gateway-1",
            sessionKey = "main",
            senderDisplayName = "Hermes",
            sourceRunId = "client-run-attachment-1",
            messageSortBaseTimestamp = 123.0,
            orderMessages = { it }
        )

        val message = messages.single()
        assertEquals("local:client-run-attachment-1|30|attachment-1", message.timelineOrderKey)
        assertEquals("local:attachment:attachment-1", message.timelineIdentityKey)
        assertEquals("attachment", message.timelineItemKind)
        assertEquals("client-run-attachment-1", message.fileContentBlocks.single().sourceRunId)
    }

    @Test
    fun completedAttachmentPreservesCanonicalTimelineKeys() {
        val attachment = ComposerAttachmentDraft(
            id = "attachment-1",
            fileUri = "file:///tmp/report.pdf",
            fileName = "report.pdf",
            mimeType = "application/pdf",
            sizeBytes = 12
        )
        val initial = ComposerAttachmentMessageUpdater.begin(
            currentMessages = emptyList(),
            attachments = listOf(attachment),
            gatewayId = "gateway-1",
            sessionKey = "main",
            senderDisplayName = "Hermes",
            sourceRunId = "client-run-attachment-1",
            messageSortBaseTimestamp = 123.0,
            orderMessages = { it }
        )
        val updated = ComposerAttachmentMessageUpdater.update(
            currentMessages = initial,
            attachment = attachment,
            gatewayId = "gateway-1",
            sessionKey = "main",
            progress = 1.0,
            phase = AttachmentUploadPhase.completed,
            failureMessage = null,
            senderDisplayName = "Hermes",
            sourceRunId = "client-run-attachment-1",
            orderMessages = { it }
        )!!
        val completed = ComposerAttachmentMessageUpdater.complete(
            currentMessages = updated,
            attachment = attachment,
            record = RelayFileTransferItem(
                fileId = "file-1",
                gatewayId = "gateway-1",
                sessionKey = "main",
                fileName = "report.pdf",
                mimeType = "application/pdf",
                sizeBytes = 12,
                sha256 = "sha",
                origin = "mobile",
                senderDisplayName = "Hermes",
                createdAt = "2025-01-01T00:00:00Z",
                sortTimestampMs = 123000,
                updatedAt = "2025-01-01T00:00:01Z",
                expiresAt = "2025-01-02T00:00:00Z",
                status = "available",
                storagePath = "/tmp/report.pdf",
                downloadPath = "/api/mobile/files/file-1",
                chunkSize = 1,
                totalChunks = 1,
                sourceRunId = "client-run-attachment-1"
            ),
            sourceRunId = "client-run-attachment-1",
            completionSortTimestamp = 123.0,
            orderMessages = { it }
        )

        assertTrue(completed.completed)
        val message = completed.messages.single()
        assertEquals("local:client-run-attachment-1|30|attachment-1", message.timelineOrderKey)
        assertEquals("local:attachment:attachment-1", message.timelineIdentityKey)
        assertEquals("attachment", message.timelineItemKind)
        assertEquals("attachment-1", message.fileContentBlocks.single().attachmentId)
    }

    @Test
    fun lateProgressMergeWithCompletedDuplicateKeepsLocalTimelineKeys() {
        val attachment = ComposerAttachmentDraft(
            id = "attachment-1",
            fileUri = "file:///tmp/report.pdf",
            fileName = "report.pdf",
            mimeType = "application/pdf",
            sizeBytes = 12
        )
        val initial = ComposerAttachmentMessageUpdater.begin(
            currentMessages = emptyList(),
            attachments = listOf(attachment),
            gatewayId = "gateway-1",
            sessionKey = "main",
            senderDisplayName = "Hermes",
            sourceRunId = "client-run-attachment-1",
            messageSortBaseTimestamp = 123.0,
            orderMessages = { it }
        )
        val completedDuplicate = ChatMessage(
            id = "remote-duplicate",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "report.pdf",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "file",
                    attachmentId = "attachment-1",
                    fileId = "file-1",
                    fileName = "report.pdf",
                    mimeType = "application/pdf",
                    downloadUrl = "/api/mobile/files/file-1"
                )
            ),
            createdAt = "2025-01-01T00:00:00Z",
            runId = "file-file-1",
            sortTimestamp = 123.5
        )

        val merged = ComposerAttachmentMessageUpdater.update(
            currentMessages = initial + completedDuplicate,
            attachment = attachment,
            gatewayId = "gateway-1",
            sessionKey = "main",
            progress = 1.0,
            phase = AttachmentUploadPhase.uploading,
            failureMessage = null,
            senderDisplayName = "Hermes",
            sourceRunId = "client-run-attachment-1",
            orderMessages = { it }
        )!!

        val message = merged.single()
        assertEquals("local:client-run-attachment-1|30|attachment-1", message.timelineOrderKey)
        assertEquals("local:attachment:attachment-1", message.timelineIdentityKey)
        assertEquals("attachment", message.timelineItemKind)
    }

    @Test
    fun lateProgressMergeWithCompletedDuplicateMatchesBySourceRunIdWhenAttachmentIdMissing() {
        val attachment = ComposerAttachmentDraft(
            id = "attachment-1",
            fileUri = "file:///tmp/report.pdf",
            fileName = "report.pdf",
            mimeType = "application/pdf",
            sizeBytes = 12
        )
        val initial = ComposerAttachmentMessageUpdater.begin(
            currentMessages = emptyList(),
            attachments = listOf(attachment),
            gatewayId = "gateway-1",
            sessionKey = "main",
            senderDisplayName = "Hermes",
            sourceRunId = "client-run-attachment-1",
            messageSortBaseTimestamp = 123.0,
            orderMessages = { it }
        )
        val completedDuplicate = ChatMessage(
            id = "remote-duplicate",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "report.pdf",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "file",
                    fileId = "file-1",
                    fileName = "report.pdf",
                    mimeType = "application/pdf",
                    downloadUrl = "/api/mobile/files/file-1",
                    sourceRunId = "client-run-attachment-1"
                )
            ),
            createdAt = "2025-01-01T00:00:00Z",
            runId = "file-file-1",
            sortTimestamp = 123.5
        )

        val merged = ComposerAttachmentMessageUpdater.update(
            currentMessages = initial + completedDuplicate,
            attachment = attachment,
            gatewayId = "gateway-1",
            sessionKey = "main",
            progress = 1.0,
            phase = AttachmentUploadPhase.uploading,
            failureMessage = null,
            senderDisplayName = "Hermes",
            sourceRunId = "client-run-attachment-1",
            orderMessages = { it }
        )!!

        assertEquals(1, merged.size)
        val message = merged.single()
        assertEquals("attachment-1", message.id)
        assertEquals("local:client-run-attachment-1|30|attachment-1", message.timelineOrderKey)
        assertEquals("local:attachment:attachment-1", message.timelineIdentityKey)
        assertEquals("attachment", message.timelineItemKind)
    }

    @Test
    fun lateProgressMergeWithCompletedDuplicateDoesNotGuessBySharedSourceRunIdAcrossMultipleAttachments() {
        val attachmentA = ComposerAttachmentDraft(
            id = "attachment-1",
            fileUri = "file:///tmp/report-a.pdf",
            fileName = "report-a.pdf",
            mimeType = "application/pdf",
            sizeBytes = 12
        )
        val attachmentB = ComposerAttachmentDraft(
            id = "attachment-2",
            fileUri = "file:///tmp/report-b.pdf",
            fileName = "report-b.pdf",
            mimeType = "application/pdf",
            sizeBytes = 24
        )
        val initial = ComposerAttachmentMessageUpdater.begin(
            currentMessages = emptyList(),
            attachments = listOf(attachmentA, attachmentB),
            gatewayId = "gateway-1",
            sessionKey = "main",
            senderDisplayName = "Hermes",
            sourceRunId = "client-run-shared",
            messageSortBaseTimestamp = 123.0,
            orderMessages = { it }
        )
        val completedDuplicate = ChatMessage(
            id = "remote-duplicate",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "report-a.pdf",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "file",
                    fileId = "file-1",
                    fileName = "report-a.pdf",
                    mimeType = "application/pdf",
                    downloadUrl = "/api/mobile/files/file-1",
                    sourceRunId = "client-run-shared"
                )
            ),
            createdAt = "2025-01-01T00:00:00Z",
            runId = "file-file-1",
            sortTimestamp = 123.5
        )

        val updated = ComposerAttachmentMessageUpdater.update(
            currentMessages = initial + completedDuplicate,
            attachment = attachmentA,
            gatewayId = "gateway-1",
            sessionKey = "main",
            progress = 0.5,
            phase = AttachmentUploadPhase.uploading,
            failureMessage = null,
            senderDisplayName = "Hermes",
            sourceRunId = "client-run-shared",
            orderMessages = { it }
        )!!

        assertEquals(3, updated.size)
        assertEquals(listOf("attachment-1", "attachment-2", "remote-duplicate"), updated.map { it.id })
    }

    @Test
    fun lateProgressMergeWithSharedSourceRunIdDoesNotCollapseAssistantAttachment() {
        val attachment = ComposerAttachmentDraft(
            id = "attachment-1",
            fileUri = "file:///tmp/report.pdf",
            fileName = "report.pdf",
            mimeType = "application/pdf",
            sizeBytes = 12
        )
        val initial = ComposerAttachmentMessageUpdater.begin(
            currentMessages = emptyList(),
            attachments = listOf(attachment),
            gatewayId = "gateway-1",
            sessionKey = "main",
            senderDisplayName = "Hermes",
            sourceRunId = "client-run-shared",
            messageSortBaseTimestamp = 123.0,
            orderMessages = { it }
        )
        val assistantFile = ChatMessage(
            id = "assistant-file",
            role = MessageRole.assistant,
            state = MessageState.completed,
            content = "result.png",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "file",
                    fileId = "file-result-1",
                    fileName = "result.png",
                    mimeType = "image/png",
                    downloadUrl = "/api/mobile/files/file-result-1",
                    sourceRunId = "client-run-shared"
                )
            ),
            createdAt = "2025-01-01T00:00:00Z",
            runId = "file-result-1",
            sortTimestamp = 123.5
        )

        val updated = ComposerAttachmentMessageUpdater.update(
            currentMessages = initial + assistantFile,
            attachment = attachment,
            gatewayId = "gateway-1",
            sessionKey = "main",
            progress = 0.5,
            phase = AttachmentUploadPhase.uploading,
            failureMessage = null,
            senderDisplayName = "Hermes",
            sourceRunId = "client-run-shared",
            orderMessages = { it }
        )!!

        assertEquals(2, updated.size)
        assertEquals(listOf("attachment-1", "assistant-file"), updated.map { it.id })
        assertEquals(listOf(MessageRole.user, MessageRole.assistant), updated.map { it.role })
    }
}
