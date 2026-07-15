package com.rethinkingstudio.clawlink.ui.screens.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import com.rethinkingstudio.clawlink.core.state.chat.ChatState
import java.io.File
import java.net.URI

internal fun conversationDisplayMessages(
    messages: List<ChatMessage>,
    showInvocationProcess: Boolean
): List<ChatMessage> {
    return messages
        .coalescedByCanonicalIdentity()
        .coalescedLocalUserAttachmentMessages()
        .coalescedSameTurnUserMediaMessages()
        .coalescedLocalUserLiveEchoes()
        .coalescedDuplicateTextHistoryMessages()
        .coalescedDuplicateFileTransferMessages()
        .coalescedResolvedTransientAssistantPlaceholders()
        .coalescedDuplicateTransientAssistantPlaceholders()
        .filter { message ->
            message.shouldDisplayInChat(showInvocationProcess = showInvocationProcess) ||
                message.state == MessageState.streaming && message.role == MessageRole.assistant
        }
        .orderedForConversationDisplay()
}

internal fun conversationDisplayMessagesForGatewayState(
    hasSelectedGateway: Boolean,
    messages: List<ChatMessage>,
    showInvocationProcess: Boolean
): List<ChatMessage> {
    // 最后一个网关解绑后本地时间线仍可保留，但未绑定页面不得泄露旧网关的聊天内容。
    if (!hasSelectedGateway) return emptyList()
    return conversationDisplayMessages(messages, showInvocationProcess)
}

internal fun conversationStructureSignature(messages: List<ChatMessage>): String {
    return messages.joinToString(separator = "\u001F") { message ->
        listOf(
            message.timelineIdentityKey.ifBlank { message.id },
            message.timelineOrderKey,
            message.role.name,
            message.state.name,
            message.runId,
            message.contentBlocks.hashCode().toString()
        ).joinToString(separator = "\u001E")
    }
}

internal fun conversationStreamingTailSignature(messages: List<ChatMessage>): String {
    val message = messages.lastOrNull {
        it.role == MessageRole.assistant && it.state == MessageState.streaming
    } ?: return ""
    return listOf(
        message.timelineIdentityKey.ifBlank { message.id },
        message.content.length.toString(),
        message.content.hashCode().toString(),
        message.contentBlocks.hashCode().toString()
    ).joinToString(separator = "\u001E")
}

internal fun shouldCoalesceChatDisplayUpdate(
    current: ChatState,
    incoming: ChatState
): Boolean {
    if (current.copy(messages = emptyList()) != incoming.copy(messages = emptyList())) {
        return false
    }
    if (current.messages.size != incoming.messages.size || incoming.messages.isEmpty()) {
        return false
    }

    val lastIndex = incoming.messages.lastIndex
    for (index in 0 until lastIndex) {
        if (current.messages[index] != incoming.messages[index]) return false
    }

    val currentTail = current.messages[lastIndex]
    val incomingTail = incoming.messages[lastIndex]
    if (incomingTail.role != MessageRole.assistant) {
        return false
    }
    if (currentTail.contentBlocks != incomingTail.contentBlocks) {
        return false
    }
    return currentTail.copy(content = incomingTail.content) == incomingTail
}

private fun List<ChatMessage>.coalescedByCanonicalIdentity(): List<ChatMessage> {
    val merged = linkedMapOf<String, ChatMessage>()
    forEachIndexed { index, message ->
        val key = message.timelineIdentityKey.trim()
            .ifBlank { message.id.trim() }
            .ifBlank { "blank-message-id-$index" }
        val existing = merged[key]
        merged[key] = if (existing == null) message else mergeSameIdentityDisplayMessage(existing, message)
    }
    return merged.values.toList()
}

private fun mergeSameIdentityDisplayMessage(existing: ChatMessage, incoming: ChatMessage): ChatMessage {
    return existing.copy(
        content = existing.content.ifBlank { incoming.content },
        contentBlocks = existing.contentBlocks.ifEmpty { incoming.contentBlocks },
        createdAt = existing.createdAt.ifBlank { incoming.createdAt },
        runId = existing.runId.ifBlank { incoming.runId },
        sortTimestamp = existing.sortTimestamp ?: incoming.sortTimestamp,
        seq = existing.seq ?: incoming.seq,
        turnSeq = existing.turnSeq ?: incoming.turnSeq,
        timelineStableKey = existing.timelineStableKey.ifBlank { incoming.timelineStableKey },
        timelineMessageId = existing.timelineMessageId.ifBlank { incoming.timelineMessageId },
        timelinePartId = existing.timelinePartId.ifBlank { incoming.timelinePartId },
        timelineOrderKey = existing.timelineOrderKey.ifBlank { incoming.timelineOrderKey },
        timelineIdentityKey = existing.timelineIdentityKey.ifBlank { incoming.timelineIdentityKey },
        timelineItemKind = existing.timelineItemKind.ifBlank { incoming.timelineItemKind },
        source = existing.source.ifBlank { incoming.source }
    )
}

private fun List<ChatMessage>.coalescedLocalUserAttachmentMessages(): List<ChatMessage> {
    val output = toMutableList()
    val attachmentIndexesToDrop = mutableSetOf<Int>()
    val localIndexesByRunId = mutableMapOf<String, MutableList<Int>>()

    output.forEachIndexed { localIndex, local ->
        if (local.role != MessageRole.user || !local.runId.trim().startsWith("local-user-")) {
            return@forEachIndexed
        }
        val localRunId = normalizedUserEchoRunId(local.runId)
        if (localRunId.isNotEmpty()) {
            localIndexesByRunId.getOrPut(localRunId) { mutableListOf() } += localIndex
        }
    }

    output.forEachIndexed { attachmentIndex, attachment ->
        if (attachment.role != MessageRole.user ||
            attachment.runId.trim().startsWith("local-user-") ||
            attachment.runId.trim().startsWith("history-") ||
            attachment.contentBlocks.none { it.isTransferContentBlock }
        ) {
            return@forEachIndexed
        }

        val localIndex = attachment.sourceRunIds()
            .asSequence()
            .flatMap { sourceRunId -> localIndexesByRunId[sourceRunId].orEmpty().asSequence() }
            .firstOrNull { index -> index != attachmentIndex }
            ?: return@forEachIndexed

        val mergedBlocks = mergedLocalAttachmentBlocks(
            localBlocks = output[localIndex].contentBlocks,
            completedBlocks = attachment.contentBlocks
        ) ?: return@forEachIndexed
        if (!mergedBlocksContainSourceRunIdentity(mergedBlocks, output[localIndex])) return@forEachIndexed

        output[localIndex] = output[localIndex].copy(contentBlocks = mergedBlocks)
        attachmentIndexesToDrop += attachmentIndex
    }

    if (attachmentIndexesToDrop.isEmpty()) return this
    return output.filterIndexed { index, _ -> index !in attachmentIndexesToDrop }
}

private fun List<ChatMessage>.coalescedSameTurnUserMediaMessages(): List<ChatMessage> {
    if (size < 2) return this

    val output = toMutableList()
    val indexesToDrop = mutableSetOf<Int>()
    val indexesByTurnIdentity = linkedMapOf<String, MutableList<Int>>()
    output.forEachIndexed { index, message ->
        if (message.role != MessageRole.user) return@forEachIndexed
        message.userMediaTurnIdentities().forEach { identity ->
            indexesByTurnIdentity.getOrPut(identity) { mutableListOf() } += index
        }
    }

    indexesByTurnIdentity.values.forEach { indexes ->
        val activeIndexes = indexes.distinct().filter { it !in indexesToDrop }
        if (activeIndexes.size < 2) return@forEach
        if (activeIndexes.none { index -> output[index].contentBlocks.any { it.isTransferContentBlock } }) {
            return@forEach
        }

        val keepIndex = activeIndexes.reduce { preferredIndex, candidateIndex ->
            if (output[candidateIndex].sameTurnUserMediaScore() > output[preferredIndex].sameTurnUserMediaScore()) {
                candidateIndex
            } else {
                preferredIndex
            }
        }
        val group = activeIndexes.map { index -> output[index] }
        // 同一 user turn 可能被 canonical 历史拆成 attachment 项和 message:user 项；只按 sourceRunId/runId 合并，不按文本或时间猜测。
        output[keepIndex] = output[keepIndex].mergedSameTurnUserMediaMessage(group)
        activeIndexes.forEach { index ->
            if (index != keepIndex) indexesToDrop += index
        }
    }

    if (indexesToDrop.isEmpty()) return this
    return output.filterIndexed { index, _ -> index !in indexesToDrop }
}

private fun ChatMessage.userMediaTurnIdentities(): List<String> {
    val identities = contentBlocks
        .mapNotNull { block -> normalizedDisplayTurnIdentity(block.sourceRunId).takeIf { it.isNotEmpty() } } +
        normalizedDisplayTurnIdentity(runId).takeIf { it.isNotEmpty() }.orEmpty()
    return identities.distinct()
}

private fun ChatMessage.sameTurnUserMediaScore(): Int {
    val transferBlocks = contentBlocks.filter { it.isTransferContentBlock }
    var score = transferBlocks.size * 100
    if (transferBlocks.any { it.stableTransferId.isNotEmpty() }) score += 20
    if (transferBlocks.any { block ->
            normalizedAttachmentReference(block.downloadUrl ?: block.downloadPath).startsWith("/api/", ignoreCase = true)
        }
    ) {
        score += 10
    }
    if (plainTextContent.trim().isNotEmpty()) score += 5
    return score
}

private fun ChatMessage.mergedSameTurnUserMediaMessage(group: List<ChatMessage>): ChatMessage {
    return copy(
        content = preferredSameTurnUserMediaContent(group),
        contentBlocks = mergedSameTurnUserMediaBlocks(group)
    )
}

private fun preferredSameTurnUserMediaContent(group: List<ChatMessage>): String {
    val attachmentLabels = group.flatMap { message ->
        message.contentBlocks
            .filter { it.isTransferContentBlock }
            .flatMap { block -> listOf(block.fileDisplayName, block.name, block.text) }
            .mapNotNull { value -> normalizedUserEchoContent(value.orEmpty()).lowercase().takeIf { it.isNotEmpty() } }
    }.toSet()
    return group
        .asSequence()
        .map { it.plainTextContent.trim() }
        .firstOrNull { text ->
            text.isNotEmpty() && text.lowercase() !in attachmentLabels
        }
        ?: group.firstOrNull()?.plainTextContent?.trim().orEmpty()
}

private fun ChatMessage.mergedSameTurnUserMediaBlocks(group: List<ChatMessage>): List<RelayChatContentBlock> {
    val merged = contentBlocks.toMutableList()
    group.forEach { message ->
        message.contentBlocks.forEach { block ->
            if (message === this && block in contentBlocks) return@forEach
            if (block.isTransferContentBlock) {
                val existingIndex = merged.indexOfFirst { existing ->
                    existing.isTransferContentBlock && existing.matchesCompletedAttachmentBlock(block)
                }
                if (existingIndex >= 0) {
                    merged[existingIndex] = richerAttachmentBlock(merged[existingIndex], block)
                } else {
                    merged += block
                }
                return@forEach
            }

            if (block.isTextBlock) {
                val text = normalizedUserEchoContent(block.text.orEmpty())
                val hasTextBlock = merged.any { existing ->
                    existing.isTextBlock && normalizedUserEchoContent(existing.text.orEmpty()) == text
                }
                if (text.isNotEmpty() && !hasTextBlock) merged += block
            }
        }
    }
    return merged
}

private fun mergedLocalAttachmentBlocks(
    localBlocks: List<RelayChatContentBlock>,
    completedBlocks: List<RelayChatContentBlock>
): List<RelayChatContentBlock>? {
    val completedMediaBlocks = completedBlocks.filter { it.isTransferContentBlock }
    if (completedMediaBlocks.isEmpty()) return null
    if (localBlocks.none { it.isTransferContentBlock }) {
        return localBlocks + completedMediaBlocks
    }

    var didMerge = false
    val usedCompletedIndexes = mutableSetOf<Int>()
    val merged = localBlocks.map { localBlock ->
        if (!localBlock.isTransferContentBlock) return@map localBlock
        val completedIndex = completedMediaBlocks.indices.firstOrNull { index ->
            index !in usedCompletedIndexes &&
                localBlock.matchesCompletedAttachmentBlock(completedMediaBlocks[index])
        } ?: return@map localBlock

        usedCompletedIndexes += completedIndex
        didMerge = true
        completedMediaBlocks[completedIndex].preservingLocalImagePreview(localBlock)
    }.toMutableList()

    if (!didMerge) return null
    completedMediaBlocks.forEachIndexed { index, block ->
        if (index !in usedCompletedIndexes) merged += block
    }
    return merged
}

private fun RelayChatContentBlock.preservingLocalImagePreview(
    localBlock: RelayChatContentBlock
): RelayChatContentBlock {
    if (!isImageFileBlock || !localBlock.isImageFileBlock) return this
    val localPreview = localBlock.localImagePreviewReference() ?: return this
    val existingThumbnail = normalizedAttachmentReference(thumbnailUrl)
    if (existingThumbnail.isNotEmpty() && !existingThumbnail.startsWith("/api/", ignoreCase = true)) {
        return this
    }
    return copy(thumbnailUrl = localPreview)
}

private fun RelayChatContentBlock.localImagePreviewReference(): String? {
    if (!isImageFileBlock) return null
    return listOf(thumbnailUrl, downloadUrl, downloadPath)
        .map { normalizedAttachmentReference(it) }
        .firstOrNull { value -> value.isNotEmpty() && isLocalImagePreviewReference(value) }
}

private fun isLocalImagePreviewReference(value: String): Boolean {
    return when {
        value.startsWith("content://", ignoreCase = true) -> true
        value.startsWith("file:", ignoreCase = true) -> runCatching { File(URI(value)).exists() }.getOrDefault(false)
        value.startsWith("/") && !value.startsWith("/api/", ignoreCase = true) -> File(value).exists()
        else -> false
    }
}

private fun mergedBlocksContainSourceRunIdentity(
    blocks: List<RelayChatContentBlock>,
    matching: ChatMessage
): Boolean {
    val localRunId = normalizedUserEchoRunId(matching.runId)
    if (localRunId.isEmpty()) return false
    return blocks.any { block ->
        block.isTransferContentBlock && normalizedAttachmentText(block.sourceRunId) == localRunId
    }
}

private fun List<ChatMessage>.coalescedLocalUserLiveEchoes(): List<ChatMessage> {
    val localEchoKeys = mutableSetOf<LocalUserEchoKey>()
    val liveEchoIndexesToDrop = mutableSetOf<Int>()

    for (local in this) {
        if (local.role != MessageRole.user || !local.runId.trim().startsWith("local-user-")) continue
        val localContent = normalizedUserEchoContent(local.content)
        if (localContent.isEmpty()) continue
        val localRunId = normalizedUserEchoRunId(local.runId)
        if (localRunId.isEmpty()) continue
        localEchoKeys += LocalUserEchoKey(runId = localRunId, content = localContent)
    }

    forEachIndexed { liveIndex, live ->
        if (live.role != MessageRole.user ||
            live.runId.trim().startsWith("local-user-") ||
            live.runId.trim().startsWith("history-")
        ) {
            return@forEachIndexed
        }
        val liveKey = LocalUserEchoKey(
            runId = normalizedUserEchoRunId(live.runId),
            content = normalizedUserEchoContent(live.content)
        )
        if (liveKey.runId.isNotEmpty() && liveKey.content.isNotEmpty() && liveKey in localEchoKeys) {
            liveEchoIndexesToDrop += liveIndex
        }
    }

    if (liveEchoIndexesToDrop.isEmpty()) return this
    return filterIndexed { index, _ -> index !in liveEchoIndexesToDrop }
}

private fun List<ChatMessage>.coalescedDuplicateTextHistoryMessages(): List<ChatMessage> {
    val output = mutableListOf<ChatMessage>()
    val indexByKey = mutableMapOf<TextHistoryKey, Int>()
    var didMerge = false

    for (message in this) {
        val key = message.textHistoryKey()
        val duplicateIndex = key?.let { indexByKey[it] }
        if (duplicateIndex == null || !output[duplicateIndex].isDuplicateTextHistoryMessage(message)) {
            output += message
            if (key != null) indexByKey[key] = output.lastIndex
            continue
        }

        output[duplicateIndex] = preferredDuplicateTextMessage(output[duplicateIndex], message)
        didMerge = true
    }

    return if (didMerge) output else this
}

private fun ChatMessage.isDuplicateTextHistoryMessage(other: ChatMessage): Boolean {
    if (textHistoryKey() != other.textHistoryKey()) return false

    val leftTimestamp = sortTimestamp
    val rightTimestamp = other.sortTimestamp
    return leftTimestamp != null &&
        rightTimestamp != null &&
        kotlin.math.abs(leftTimestamp - rightTimestamp) <= duplicateTextHistoryWindowSeconds
}

private fun preferredDuplicateTextMessage(existing: ChatMessage, incoming: ChatMessage): ChatMessage {
    val preferred = if (incoming.id.startsWith("history:") && !existing.id.startsWith("history:")) {
        incoming
    } else {
        existing
    }
    val fallback = if (preferred === existing) incoming else existing
    return preferred.copy(
        content = preferred.content.ifBlank { fallback.content },
        contentBlocks = preferred.contentBlocks.ifEmpty { fallback.contentBlocks },
        createdAt = preferred.createdAt.ifBlank { fallback.createdAt },
        runId = preferred.runId.ifBlank { fallback.runId },
        sortTimestamp = preferred.sortTimestamp ?: fallback.sortTimestamp,
        seq = preferred.seq ?: fallback.seq,
        turnSeq = preferred.turnSeq ?: fallback.turnSeq,
        timelineStableKey = preferred.timelineStableKey.ifBlank { fallback.timelineStableKey },
        timelineMessageId = preferred.timelineMessageId.ifBlank { fallback.timelineMessageId },
        timelinePartId = preferred.timelinePartId.ifBlank { fallback.timelinePartId },
        timelineOrderKey = preferred.timelineOrderKey.ifBlank { fallback.timelineOrderKey },
        timelineIdentityKey = preferred.timelineIdentityKey.ifBlank { fallback.timelineIdentityKey },
        timelineItemKind = preferred.timelineItemKind.ifBlank { fallback.timelineItemKind }
    )
}

private fun List<ChatMessage>.coalescedDuplicateFileTransferMessages(): List<ChatMessage> {
    val output = mutableListOf<ChatMessage>()
    val stableIndexByKey = mutableMapOf<String, Int>()
    val weakPreviewIndexByKey = mutableMapOf<String, Int>()
    var didMerge = false

    for (message in this) {
        val keys = message.fileTransferDisplayKeys()
        val duplicateIndex = keys?.stableKey
            ?.let { stableIndexByKey[it] }
            ?.takeIf { output[it].isDuplicateFileTransferDisplayMessage(message) }
            ?: keys?.weakKey
                ?.let { weakPreviewIndexByKey[it] }
                ?.takeIf { output[it].isDuplicateFileTransferDisplayMessage(message) }

        if (duplicateIndex == null) {
            output += message
            if (keys?.stableKey != null) stableIndexByKey[keys.stableKey] = output.lastIndex
            if (keys?.weakKey != null && keys.hasMissingStableId) {
                weakPreviewIndexByKey[keys.weakKey] = output.lastIndex
            }
        } else {
            output[duplicateIndex] = mergeDuplicateFileTransferDisplayMessage(output[duplicateIndex], message)
            didMerge = true
        }
    }

    return if (didMerge) output else this
}

private fun List<ChatMessage>.coalescedDuplicateTransientAssistantPlaceholders(): List<ChatMessage> {
    if (size < 2) return this

    val placeholderIndexes = indices.filter { index -> this[index].isTransientDisplayWaitingPlaceholder() }
    if (placeholderIndexes.size < 2) return this

    val keepIndex = placeholderIndexes.reduce { preferredIndex, candidateIndex ->
        val preferred = this[preferredIndex]
        val candidate = this[candidateIndex]
        if (candidate.prefersTransientAssistantPlaceholderOver(preferred)) candidateIndex else preferredIndex
    }

    // 展示层只允许一个纯 waiting 占位；真实文本、附件、工具输出不走这个分支，避免误删业务消息。
    return filterIndexed { index, message ->
        !message.isTransientDisplayWaitingPlaceholder() || index == keepIndex
    }
}

private fun List<ChatMessage>.coalescedResolvedTransientAssistantPlaceholders(): List<ChatMessage> {
    if (size < 2) return this

    val placeholderIndexesToDrop = mutableSetOf<Int>()
    forEachIndexed { index, message ->
        if (!message.isResolvableTransientTypingPlaceholder()) return@forEachIndexed
        val previousUserIndex = indices.lastOrNull { candidateIndex ->
            candidateIndex < index && this[candidateIndex].role == MessageRole.user
        } ?: return@forEachIndexed
        val nextUserIndex = indices.firstOrNull { candidateIndex ->
            candidateIndex > index && this[candidateIndex].role == MessageRole.user
        } ?: size
        val currentTurnHasVisibleAssistantText = (previousUserIndex + 1 until nextUserIndex).any { candidateIndex ->
            candidateIndex != index && this[candidateIndex].isVisibleAssistantTextOutput()
        }
        if (currentTurnHasVisibleAssistantText) {
            placeholderIndexesToDrop += index
        }
    }

    if (placeholderIndexesToDrop.isEmpty()) return this
    // 同一用户段里 assistant 已经输出真实文字后，typing 必须被正文替换，不能再作为第二个气泡残留。
    return filterIndexed { index, _ -> index !in placeholderIndexesToDrop }
}

private fun ChatMessage.prefersTransientAssistantPlaceholderOver(other: ChatMessage): Boolean {
    val keyComparison = compareNormalizedText(timelineOrderKey, other.timelineOrderKey)
    if (keyComparison != 0) return keyComparison > 0

    val identityComparison = compareNormalizedText(timelineIdentityKey.ifBlank { id }, other.timelineIdentityKey.ifBlank { other.id })
    if (identityComparison != 0) return identityComparison > 0

    return false
}

private fun List<ChatMessage>.orderedForConversationDisplay(): List<ChatMessage> {
    if (size < 2) return this
    return mapIndexed { index, message -> IndexedDisplayMessage(index, message) }
        .sortedWith { left, right ->
            when {
                sameTurnOutputBeforeWaiting(left.message, right.message) -> -1
                sameTurnOutputBeforeWaiting(right.message, left.message) -> 1
                else -> left.index.compareTo(right.index)
            }
        }
        .map { it.message }
}

private fun sameTurnOutputBeforeWaiting(left: ChatMessage, right: ChatMessage): Boolean {
    if (!right.isResolvableTransientTypingPlaceholder()) return false
    val leftTurn = left.displayTurnIdentity()
    // 同一用户 turn 的真实输出必须排在 transient waiting 前面，避免附件到达后仍被 loading 气泡压住。
    return leftTurn.isNotEmpty() &&
        leftTurn == right.displayTurnIdentity() &&
        left.isAssistantOrToolOutput()
}

private fun ChatMessage.isAssistantOrToolOutput(): Boolean {
    if (isTransientDisplayWaitingPlaceholder()) return false
    return role == MessageRole.assistant || role == MessageRole.tool
}

private fun ChatMessage.isVisibleAssistantTextOutput(): Boolean {
    if (role != MessageRole.assistant || isTransientDisplayWaitingPlaceholder()) return false

    val textBlockContent = contentBlocks
        .filter { it.isTextBlock }
        .mapNotNull { it.text?.trim()?.ifEmpty { null } }
        .joinToString("\n\n")
        .trim()
    if (textBlockContent.isNotEmpty() && !isTransientDisplayWaitingText(textBlockContent)) {
        return true
    }

    if (contentBlocks.any { it.isTransferContentBlock || it.isToolCallBlock || it.isToolResultBlock }) {
        return false
    }
    val text = plainTextContent.trim()
    return text.isNotEmpty() && !isTransientDisplayWaitingText(text)
}

private fun ChatMessage.isResolvableTransientTypingPlaceholder(): Boolean {
    if (!isTransientDisplayWaitingPlaceholder()) return false
    val text = plainTextContent.trim()
    return timelineItemKind.trim() == "waiting" || text.isEmpty() || text == "[[clawlink:typing]]"
}

private fun ChatMessage.isTransientDisplayWaitingPlaceholder(): Boolean {
    if (role != MessageRole.assistant || state != MessageState.streaming) return false
    if (contentBlocks.any { it.isTransferContentBlock || it.isToolCallBlock || it.isToolResultBlock }) return false
    val text = plainTextContent.trim()
    return timelineItemKind.trim() == "waiting" || text.isEmpty() || isTransientDisplayWaitingText(text)
}

private fun ChatMessage.displayTurnIdentity(): String {
    val blockSourceRunId = contentBlocks.firstNotNullOfOrNull { block ->
        normalizedDisplayTurnIdentity(block.sourceRunId)
            .takeIf { it.isNotEmpty() }
    }
    if (!blockSourceRunId.isNullOrEmpty()) return blockSourceRunId
    return normalizedDisplayTurnIdentity(runId)
}

private fun normalizedDisplayTurnIdentity(value: String?): String {
    return normalizedUserEchoRunId(value.orEmpty()).lowercase()
}

private fun isTransientDisplayWaitingText(text: String): Boolean {
    val trimmed = text.trim()
    return trimmed.startsWith("正在连接") ||
        trimmed.startsWith("连接中") ||
        trimmed.startsWith("Connecting") ||
        trimmed.startsWith("连接中断") ||
        trimmed.startsWith("Connection interrupted") ||
        trimmed == "正在同步回复..." ||
        trimmed == "Syncing reply..." ||
        trimmed == "已完成，但未返回文本。" ||
        trimmed == "Completed, but no text was returned." ||
        trimmed == "正在同步最终内容..." ||
        trimmed == "Syncing final content..." ||
        trimmed == "[[clawlink:typing]]"
}

private fun ChatMessage.isDuplicateFileTransferDisplayMessage(other: ChatMessage): Boolean {
    if (role != other.role) return false
    if (runId.trim().startsWith("local-user-") || other.runId.trim().startsWith("local-user-")) {
        return false
    }

    val leftBlocks = contentBlocks.filter { it.isTransferContentBlock }
    val rightBlocks = other.contentBlocks.filter { it.isTransferContentBlock }
    if (leftBlocks.isEmpty() || leftBlocks.size != rightBlocks.size) return false

    val usedRightIndexes = mutableSetOf<Int>()
    return leftBlocks.all { left ->
        val match = rightBlocks.indices.firstOrNull { index ->
            index !in usedRightIndexes && left.matchesCompletedAttachmentBlock(rightBlocks[index])
        } ?: return false
        usedRightIndexes += match
        true
    }
}

private fun mergeDuplicateFileTransferDisplayMessage(
    existing: ChatMessage,
    incoming: ChatMessage
): ChatMessage {
    return existing.copy(
        content = existing.content.ifBlank { incoming.content },
        contentBlocks = mergedDuplicateFileTransferBlocks(existing.contentBlocks, incoming.contentBlocks),
        createdAt = existing.createdAt.ifBlank { incoming.createdAt },
        runId = existing.runId.ifBlank { incoming.runId },
        sortTimestamp = existing.sortTimestamp ?: incoming.sortTimestamp,
        seq = existing.seq ?: incoming.seq,
        turnSeq = existing.turnSeq ?: incoming.turnSeq,
        timelineStableKey = existing.timelineStableKey.ifBlank { incoming.timelineStableKey },
        timelineMessageId = existing.timelineMessageId.ifBlank { incoming.timelineMessageId },
        timelinePartId = existing.timelinePartId.ifBlank { incoming.timelinePartId },
        timelineOrderKey = existing.timelineOrderKey.ifBlank { incoming.timelineOrderKey },
        timelineIdentityKey = existing.timelineIdentityKey.ifBlank { incoming.timelineIdentityKey },
        timelineItemKind = existing.timelineItemKind.ifBlank { incoming.timelineItemKind }
    )
}

private fun mergedDuplicateFileTransferBlocks(
    existingBlocks: List<RelayChatContentBlock>,
    incomingBlocks: List<RelayChatContentBlock>
): List<RelayChatContentBlock> {
    val incomingTransferBlocks = incomingBlocks.filter { it.isTransferContentBlock }
    val usedIncomingIndexes = mutableSetOf<Int>()
    val merged = existingBlocks.map { existingBlock ->
        if (!existingBlock.isTransferContentBlock) return@map existingBlock
        val incomingIndex = incomingTransferBlocks.indices.firstOrNull { index ->
            index !in usedIncomingIndexes &&
                existingBlock.matchesCompletedAttachmentBlock(incomingTransferBlocks[index])
        } ?: return@map existingBlock

        usedIncomingIndexes += incomingIndex
        richerAttachmentBlock(existingBlock, incomingTransferBlocks[incomingIndex])
    }.toMutableList()

    incomingTransferBlocks.forEachIndexed { index, block ->
        if (index !in usedIncomingIndexes) merged += block
    }
    return merged
}

private fun richerAttachmentBlock(
    existing: RelayChatContentBlock,
    incoming: RelayChatContentBlock
): RelayChatContentBlock {
    val incomingHasStableId = incoming.stableTransferId.isNotEmpty()
    val existingHasStableId = existing.stableTransferId.isNotEmpty()
    if (incomingHasStableId && !existingHasStableId) return incoming

    val incomingUrl = normalizedAttachmentText(incoming.downloadUrl ?: incoming.downloadPath)
    val existingUrl = normalizedAttachmentText(existing.downloadUrl ?: existing.downloadPath)
    if (incomingUrl.startsWith("/api/") && !existingUrl.startsWith("/api/")) return incoming

    return existing
}

private fun RelayChatContentBlock.matchesCompletedAttachmentBlock(
    completedBlock: RelayChatContentBlock
): Boolean {
    val localId = stableTransferId
    val completedId = completedBlock.stableTransferId
    if (localId.isNotEmpty() && completedId.isNotEmpty()) {
        return localId == completedId
    }

    val localName = normalizedAttachmentText(fileDisplayName)
    val completedName = normalizedAttachmentText(completedBlock.fileDisplayName)
    if (localName.isEmpty() || localName != completedName) return false

    val localMimeType = normalizedAttachmentText(mimeType)
    val completedMimeType = normalizedAttachmentText(completedBlock.mimeType)
    return localMimeType.isEmpty() ||
        completedMimeType.isEmpty() ||
        localMimeType == completedMimeType ||
        localMimeType == "application/octet-stream" ||
        completedMimeType == "application/octet-stream"
}

private val RelayChatContentBlock.isTransferContentBlock: Boolean
    get() = isFileBlock || isVoiceMessageBlock

private val RelayChatContentBlock.stableTransferId: String
    get() = normalizedAttachmentText(attachmentId ?: fileId)

private fun ChatMessage.sourceRunIds(): List<String> {
    return contentBlocks
        .mapNotNull { normalizedAttachmentText(it.sourceRunId).takeIf { sourceRunId -> sourceRunId.isNotEmpty() } }
        .distinct()
}

private fun ChatMessage.textHistoryKey(): TextHistoryKey? {
    if (contentBlocks.any { it.isTransferContentBlock || it.isToolCallBlock || it.isToolResultBlock }) return null
    val normalizedContent = normalizedUserEchoContent(plainTextContent)
    if (normalizedContent.isEmpty()) return null
    return TextHistoryKey(role = role, content = normalizedContent)
}

private fun ChatMessage.fileTransferDisplayKeys(): FileTransferDisplayKeys? {
    if (runId.trim().startsWith("local-user-")) return null
    val transferBlocks = contentBlocks.filter { it.isTransferContentBlock }
    if (transferBlocks.isEmpty()) return null

    val stableIds = transferBlocks.map { it.stableTransferId }
    val hasMissingStableId = stableIds.any { it.isEmpty() }
    val stableKey = if (!hasMissingStableId) {
        listOf(role.name, "stable", stableIds.sorted().joinToString(separator = "|"))
            .joinToString(separator = "\u001E")
    } else {
        null
    }

    val weakParts = transferBlocks.mapNotNull { block ->
        val name = normalizedAttachmentText(block.fileDisplayName)
        if (name.isEmpty()) return@mapNotNull null
        name
    }
    val weakKey = if (weakParts.size == transferBlocks.size) {
        listOf(role.name, "weak", weakParts.sorted().joinToString(separator = "|"))
            .joinToString(separator = "\u001E")
    } else {
        null
    }

    return FileTransferDisplayKeys(
        stableKey = stableKey,
        weakKey = weakKey,
        hasMissingStableId = hasMissingStableId
    )
}

private fun normalizedAttachmentText(value: String?): String {
    return value?.trim()?.lowercase().orEmpty()
}

private fun normalizedAttachmentReference(value: String?): String {
    return value?.trim().orEmpty()
}

private fun normalizedUserEchoRunId(value: String): String {
    var normalized = value.trim()
    // 本地回显和服务端回声会使用不同前缀，但二者指向同一个 clientRunID。
    for (prefix in listOf("local-user-", "user-")) {
        if (normalized.startsWith(prefix)) {
            normalized = normalized.removePrefix(prefix)
            break
        }
    }
    for (suffix in listOf(":user", ":assistant", ":tool", ":system")) {
        if (normalized.endsWith(suffix)) {
            normalized = normalized.removeSuffix(suffix)
            break
        }
    }
    return normalized.trim()
}

private fun normalizedUserEchoContent(value: String): String {
    return value.trim().replace(Regex("\\s+"), " ")
}

private fun compareNormalizedText(left: String, right: String): Int {
    val normalizedLeft = left.trim()
    val normalizedRight = right.trim()
    if (normalizedLeft.isEmpty() && normalizedRight.isEmpty()) return 0
    if (normalizedLeft.isEmpty()) return -1
    if (normalizedRight.isEmpty()) return 1
    return normalizedLeft.compareTo(normalizedRight)
}

private const val duplicateTextHistoryWindowSeconds = 30.0

private data class LocalUserEchoKey(
    val runId: String,
    val content: String
)

private data class TextHistoryKey(
    val role: MessageRole,
    val content: String
)

private data class FileTransferDisplayKeys(
    val stableKey: String?,
    val weakKey: String?,
    val hasMissingStableId: Boolean
)

private data class IndexedDisplayMessage(
    val index: Int,
    val message: ChatMessage
)
