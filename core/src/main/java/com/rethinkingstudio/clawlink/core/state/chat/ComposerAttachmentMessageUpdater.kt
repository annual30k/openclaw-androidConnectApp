package com.rethinkingstudio.clawlink.core.state.chat

import android.graphics.BitmapFactory
import com.rethinkingstudio.clawlink.core.models.chat.AttachmentUploadPhase
import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.ComposerAttachmentDraft
import com.rethinkingstudio.clawlink.core.models.chat.ComposerAttachmentUploadItem
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.network.dto.RelayFileTransferItem
import java.io.File
import java.time.Instant

internal data class ComposerAttachmentCompletionResult(
    val completed: Boolean,
    val messages: List<ChatMessage>
)

internal object ComposerAttachmentMessageUpdater {
    fun begin(
        currentMessages: List<ChatMessage>,
        attachments: List<ComposerAttachmentDraft>,
        gatewayId: String,
        sessionKey: String,
        senderDisplayName: String?,
        messageSortBaseTimestamp: Double,
        orderMessages: (List<ChatMessage>) -> List<ChatMessage>
    ): List<ChatMessage> {
        if (attachments.isEmpty()) return currentMessages

        val messages = currentMessages.toMutableList()
        attachments.forEachIndexed { index, attachment ->
            val sortTimestamp = messageSortBaseTimestamp + (index * 0.001)
            val statusText = ComposerAttachmentUploadItem(
                gatewayId = gatewayId,
                attachment = attachment,
                progress = 0.0,
                phase = AttachmentUploadPhase.uploading,
                failureMessage = null
            ).statusText
            val message = ChatMessage(
                id = attachment.id,
                role = MessageRole.user,
                state = MessageState.streaming,
                content = sanitizeChatDisplayText(attachment.fileName),
                contentBlocks = listOf(
                    makeComposerAttachmentUploadContentBlock(
                        attachment = attachment,
                        gatewayId = gatewayId,
                        sessionKey = sessionKey,
                        senderDisplayName = senderDisplayName,
                        statusText = statusText,
                        downloadUrlString = attachment.fileUri
                    )
                ),
                createdAt = Instant.ofEpochMilli((sortTimestamp * 1000).toLong()).toString(),
                runId = composerAttachmentUploadRunId(attachment),
                sortTimestamp = sortTimestamp
            )

            val existingIndex = messages.indexOfFirst { it.id == attachment.id }
            if (existingIndex >= 0) {
                messages[existingIndex] = message
            } else {
                messages.add(message)
            }
        }

        return orderMessages(messages)
    }

    fun update(
        currentMessages: List<ChatMessage>,
        attachment: ComposerAttachmentDraft,
        gatewayId: String,
        sessionKey: String,
        progress: Double,
        phase: AttachmentUploadPhase,
        failureMessage: String?,
        senderDisplayName: String?,
        orderMessages: (List<ChatMessage>) -> List<ChatMessage>
    ): List<ChatMessage>? {
        val messages = currentMessages.toMutableList()
        val index = messages.indexOfFirst { it.id == attachment.id }
        if (index < 0) return null

        val existing = messages[index]
        if (existing.transferContentBlocks().any { !it.fileId.isNullOrBlank() }) {
            return null
        }
        val uploadPlaceholder = existing.copy(
            contentBlocks = listOf(
                makeComposerAttachmentUploadContentBlock(
                    attachment = attachment,
                    gatewayId = gatewayId,
                    sessionKey = sessionKey,
                    senderDisplayName = senderDisplayName ?: existing.transferContentBlocks().firstOrNull()?.senderDisplayName,
                    statusText = null,
                    downloadUrlString = attachment.fileUri
                )
            )
        )
        val completedDuplicateIndex = messages.indexOfFirst { message ->
            message.id != existing.id && samePendingUploadMessage(uploadPlaceholder, message)
        }
        if (completedDuplicateIndex >= 0) {
            // 上传完成事件可能先生成了最终文件消息；进度回调再到时合并到原占位，保留本地排序并去掉重复。
            val completedDuplicate = messages[completedDuplicateIndex]
            messages[index] = mergeCompletedFileMessage(
                existing = existing,
                completed = completedDuplicate.copy(
                    id = existing.id,
                    sortTimestamp = existing.sortTimestamp ?: completedDuplicate.sortTimestamp
                )
            )
            messages.removeAt(completedDuplicateIndex)
            return orderMessages(messages)
        }
        val uploadItem = ComposerAttachmentUploadItem(
            gatewayId = gatewayId,
            attachment = attachment,
            progress = progress,
            phase = phase,
            failureMessage = failureMessage
        )
        messages[index] = ChatMessage(
            id = existing.id,
            role = existing.role,
            state = phase.toMessageState(),
            content = sanitizeChatDisplayText(attachment.fileName),
            contentBlocks = listOf(
                makeComposerAttachmentUploadContentBlock(
                    attachment = attachment,
                    gatewayId = gatewayId,
                    sessionKey = sessionKey,
                    senderDisplayName = senderDisplayName ?: existing.fileContentBlocks.firstOrNull()?.senderDisplayName,
                    statusText = uploadItem.statusText,
                    downloadUrlString = attachment.fileUri
                )
            ),
            createdAt = existing.createdAt,
            runId = existing.runId.ifBlank { composerAttachmentUploadRunId(attachment) },
            sortTimestamp = existing.sortTimestamp
        )

        return orderMessages(messages)
    }

    fun complete(
        currentMessages: List<ChatMessage>,
        attachment: ComposerAttachmentDraft,
        record: RelayFileTransferItem,
        completionSortTimestamp: Double,
        orderMessages: (List<ChatMessage>) -> List<ChatMessage>
    ): ComposerAttachmentCompletionResult {
        val messages = currentMessages.toMutableList()
        val fileRunId = record.fileId.trim().takeIf { it.isNotEmpty() }?.let { fileMessageRunId(it) }
        val index = messages.indexOfFirst { message ->
            message.id == attachment.id ||
                (fileRunId != null && message.runId == fileRunId) ||
                (fileRunId != null && message.fileContentBlocks.any { it.fileId == record.fileId })
        }
        if (index < 0) return ComposerAttachmentCompletionResult(completed = false, messages = currentMessages)

        val existing = messages[index]
        val finalBlock = makeFileContentBlock(record)
        cacheCompletedAttachmentPreview(attachment, finalBlock)
        val completedMessage = ChatMessage(
            id = existing.id,
            role = if (record.origin.equals("mobile", ignoreCase = true)) MessageRole.user else MessageRole.assistant,
            state = MessageState.completed,
            content = sanitizeChatDisplayText(record.fileName),
            contentBlocks = listOf(finalBlock),
            createdAt = Instant.ofEpochMilli((completionSortTimestamp * 1000).toLong()).toString(),
            runId = if (record.fileId.isNotBlank()) fileMessageRunId(record.fileId) else existing.runId,
            sortTimestamp = existing.sortTimestamp ?: completionSortTimestamp
        )
        val finalMessage = mergeCompletedFileMessage(existing = existing, completed = completedMessage)
        messages[index] = finalMessage
        val dedupedMessages = messages.filterIndexed { messageIndex, message ->
            // 最终文件消息以 fileId/传输身份去重，不按文件名或文本内容猜测，避免同名附件被误合并。
            val isSameUploadPlaceholder = message.id == attachment.id ||
                message.runId == composerAttachmentUploadRunId(attachment)
            messageIndex == index || (!isSameUploadPlaceholder && !sameFileMessage(message, finalMessage))
        }
        return ComposerAttachmentCompletionResult(
            completed = true,
            messages = orderMessages(dedupedMessages)
        )
    }

    private fun cacheCompletedAttachmentPreview(
        attachment: ComposerAttachmentDraft,
        finalBlock: com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
    ) {
        val attachmentFile = File(attachment.fileUri)
        val attachmentCacheKey = finalBlock.chatAttachmentCacheKey()
        if (attachmentCacheKey != null) {
            runCatching {
                if (attachmentFile.exists()) {
                    RemoteAttachmentCache.put(
                        key = attachmentCacheKey,
                        fileName = attachment.fileName,
                        bytes = attachmentFile.readBytes()
                    )
                }
            }
        }
        if (finalBlock.isImageFileBlock) {
            val cacheKey = finalBlock.chatImageCacheKey()
            if (cacheKey != null) {
                runCatching {
                    BitmapFactory.decodeFile(attachmentFile.absolutePath)
                        ?.let { bitmap ->
                            RemoteImageCache.put(cacheKey, bitmap)
                            RemoteImageSizeCache.put(cacheKey, bitmap.width.toFloat() to bitmap.height.toFloat())
                        }
                }
            }
        }
    }
}
