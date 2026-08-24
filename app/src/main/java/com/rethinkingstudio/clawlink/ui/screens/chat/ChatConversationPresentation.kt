package com.rethinkingstudio.clawlink.ui.screens.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import com.rethinkingstudio.clawlink.core.state.chat.ChatState
import com.rethinkingstudio.clawlink.core.state.chat.compareCanonicalTimelineOrderKeys
import java.io.File
import java.net.URI

internal fun conversationDisplayMessages(
    messages: List<ChatMessage>,
    showInvocationProcess: Boolean
): List<ChatMessage> {
    return messages
        .coalescedByCanonicalIdentity()
        .coalescedCompletedToolCallRows()
        .coalescedLocalUserAttachmentMessages()
        .coalescedSameTurnUserMediaMessages()
        .coalescedLocalUserLiveEchoes()
        .coalescedDuplicateFileTransferMessages()
        .coalescedResolvedTransientAssistantPlaceholders()
        .coalescedDuplicateTransientAssistantPlaceholders()
        .filter { message ->
            message.shouldDisplayInChat(showInvocationProcess = showInvocationProcess) ||
                message.state == MessageState.streaming && message.role == MessageRole.assistant
        }
}

// 同一稳定调用已有结果时只展示结果卡；不能按工具名或到达时间猜测配对关系。
private fun List<ChatMessage>.coalescedCompletedToolCallRows(): List<ChatMessage> {
    val completedToolCallIds = asSequence()
        .filter { message ->
            message.role == MessageRole.tool &&
                message.contentBlocks.none { it.isToolCallBlock } &&
                message.contentBlocks.any { !it.toolCallId.isNullOrBlank() || !it.toolUseId.isNullOrBlank() }
        }
        .flatMap { message -> message.toolCallIdentities().asSequence() }
        .toSet()
    if (completedToolCallIds.isEmpty()) return this

    return filterNot { message ->
        message.role == MessageRole.tool &&
            message.contentBlocks.any { it.isToolCallBlock } &&
            message.contentBlocks.none { it.isToolResultBlock } &&
            message.toolCallIdentities().any(completedToolCallIds::contains)
    }
}

private fun ChatMessage.toolCallIdentities(): List<String> {
    return contentBlocks.flatMap { block -> listOf(block.toolCallId, block.toolUseId) }
        .mapNotNull { value -> value?.trim()?.takeIf { it.isNotEmpty() } }
        .distinct()
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
            message.contentBlocks
                .map(RelayChatContentBlock::displayStructureForStreaming)
                .hashCode()
                .toString()
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

internal fun activeToolProgressLabels(
    messages: List<ChatMessage>,
    showInvocationProcess: Boolean = false
): Map<String, String> {
    if (showInvocationProcess) return emptyMap()
    return messages.asSequence()
        .filter { message -> message.role == MessageRole.tool && message.state == MessageState.streaming }
        .mapNotNull { message ->
            message.runId.trim().takeIf { it.isNotEmpty() }?.let { runId ->
                runId to toolProgressLabel(message)
            }
        }
        .toMap()
}

internal fun streamingAssistantProgressLabel(
    message: ChatMessage,
    activeToolLabels: Map<String, String>
): String? {
    if (message.role != MessageRole.assistant || message.state != MessageState.streaming) return null
    return activeToolLabels[message.runId.trim()] ?: "正在思考…"
}

internal fun streamingWaitProgressLabel(
    messages: List<ChatMessage>,
    showInvocationProcess: Boolean = false
): String {
    if (showInvocationProcess) return "正在思考…"
    return messages.lastOrNull { message ->
        message.role == MessageRole.tool && message.state == MessageState.streaming
    }?.let(::toolProgressLabel) ?: "正在思考…"
}

private fun toolProgressLabel(message: ChatMessage): String {
    val detail = buildList {
        add(message.plainTextContent)
        add(message.toolDisplayName.orEmpty())
        message.contentBlocks.forEach { block ->
            add(block.text.orEmpty())
            add(block.name.orEmpty())
            add(block.toolName.orEmpty())
        }
    }.joinToString(separator = "\n").lowercase()
    return when {
        detail.contains("wttr.in") -> "正在查询天气…"
        detail.contains("search") -> "正在搜索资料…"
        detail.contains("browser") -> "正在获取网页信息…"
        detail.contains("exec") || detail.contains("bash") || detail.contains("process") -> "正在执行操作…"
        else -> "正在调用工具…"
    }
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
    if (currentTail.displayStructureForStreaming() != incomingTail.displayStructureForStreaming()) {
        return false
    }
    return currentTail.copy(
        content = incomingTail.content,
        contentBlocks = incomingTail.contentBlocks,
        seq = incomingTail.seq
    ) == incomingTail
}

private fun ChatMessage.displayStructureForStreaming(): ChatMessage {
    return copy(
        content = "",
        contentBlocks = contentBlocks.map(RelayChatContentBlock::displayStructureForStreaming),
        // Hermes message.part.delta 的 seq 会随正文增长；它不是可见列表结构。
        seq = null
    )
}

private fun RelayChatContentBlock.displayStructureForStreaming(): RelayChatContentBlock {
    if (!isTextBlock) return this
    // 只忽略文本块的增长字段；附件、工具和其他结构变化仍必须立即刷新。
    return copy(text = null, contentHash = null)
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
    val textSource = preferredSameTurnUserTextSource(group)
    return copy(
        content = textSource?.sameTurnUserTextContent().orEmpty(),
        contentBlocks = mergedSameTurnUserMediaBlocks(group, textSource)
    )
}

private fun ChatMessage.preferredSameTurnUserTextSource(group: List<ChatMessage>): ChatMessage? {
    if (canonicalSameTurnUserTextBlocks().isNotEmpty()) return this
    return group
        .filter { message ->
            message.preferredSameTurnUserTextBlocks().isNotEmpty() ||
                (message.contentBlocks.none { it.isTransferContentBlock } && message.content.trim().isNotEmpty())
        }
        .maxWithOrNull(Comparator { left, right ->
            left.sameTurnUserTextSourceScore().compareTo(right.sameTurnUserTextSourceScore())
                .takeIf { it != 0 }
                ?: compareCanonicalTimelineOrderKeys(left.timelineOrderKey, right.timelineOrderKey)
                    .takeIf { it != 0 }
                ?: left.timelineIdentityKey.compareTo(right.timelineIdentityKey)
                    .takeIf { it != 0 }
                ?: left.id.compareTo(right.id)
        })
        ?: takeIf { content.trim().isNotEmpty() }
}

private fun ChatMessage.sameTurnUserTextSourceScore(): Int {
    var score = 0
    if (canonicalSameTurnUserTextBlocks().isNotEmpty()) score += 100
    if (contentBlocks.none { it.isTransferContentBlock }) score += 20
    if (source.trim().equals("history", ignoreCase = true)) score += 10
    if (timelineIdentityKey.isNotBlank() && !timelineIdentityKey.startsWith("local:")) score += 5
    if (seq != null || turnSeq != null) score += 2
    return score
}

private fun ChatMessage.preferredSameTurnUserTextBlocks(): List<RelayChatContentBlock> {
    val renderable = contentBlocks.filter { block -> block.isTextBlock && !block.text.isNullOrBlank() }
    val canonical = canonicalSameTurnUserTextBlocks()
    return canonical.ifEmpty { renderable }
}

private fun ChatMessage.canonicalSameTurnUserTextBlocks(): List<RelayChatContentBlock> {
    return contentBlocks.filter { block ->
        block.isTextBlock && !block.text.isNullOrBlank() && !block.contentBlockId.isNullOrBlank()
    }
}

private fun ChatMessage.sameTurnUserTextContent(): String {
    return preferredSameTurnUserTextBlocks()
        .mapNotNull { block -> block.text?.trim()?.takeIf { it.isNotEmpty() } }
        .joinToString("\n\n")
        .ifBlank { content.trim() }
}

private fun ChatMessage.mergedSameTurnUserMediaBlocks(
    group: List<ChatMessage>,
    textSource: ChatMessage?
): List<RelayChatContentBlock> {
    val selectedTextBlocks = textSource?.preferredSameTurnUserTextBlocks().orEmpty()
    val merged = if (textSource === this) {
        val selectedCanonicalIds = selectedTextBlocks
            .mapNotNull { block -> block.contentBlockId?.trim()?.takeIf { it.isNotEmpty() } }
            .toSet()
        contentBlocks.filter { block ->
            !block.isTextBlock ||
                selectedCanonicalIds.isEmpty() ||
                block.contentBlockId?.trim().orEmpty() in selectedCanonicalIds
        }.toMutableList()
    } else {
        val projectedTextBlocks = selectedTextBlocks.ifEmpty {
            textSource?.content?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { text -> listOf(RelayChatContentBlock(type = "text", text = text)) }
                .orEmpty()
        }
        (projectedTextBlocks + contentBlocks.filterNot { it.isTextBlock }).toMutableList()
    }
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
    val localIndexesByRun = linkedMapOf<String, MutableList<Int>>()
    val relayIndexesByRun = linkedMapOf<String, MutableList<Int>>()
    forEachIndexed { index, message ->
        if (message.role != MessageRole.user) return@forEachIndexed
        val runIdentity = normalizedUserEchoRunId(message.runId).takeIf { it.isNotEmpty() }
            ?: return@forEachIndexed
        when {
            message.runId.trim().startsWith("local-user-") -> {
                localIndexesByRun.getOrPut(runIdentity, ::mutableListOf) += index
            }
            !message.runId.trim().startsWith("history-") -> {
                relayIndexesByRun.getOrPut(runIdentity, ::mutableListOf) += index
            }
        }
    }

    val localIndexByRelayIndex = mutableMapOf<Int, Int>()
    localIndexesByRun.forEach { (runIdentity, localIndexes) ->
        val relayIndexes = relayIndexesByRun[runIdentity].orEmpty()
        // 稳定 run 正常只对应一条本地回显和一条 Relay 回显；身份冲突时全部保留，
        // 不能再用文案或时间猜测应该合并哪一条。
        if (localIndexes.size == 1 && relayIndexes.size == 1) {
            localIndexByRelayIndex[relayIndexes.single()] = localIndexes.single()
        }
    }
    if (localIndexByRelayIndex.isEmpty()) return this

    val localIndexesToDrop = localIndexByRelayIndex.values.toSet()
    return mapIndexedNotNull { index, message ->
        when {
            index in localIndexesToDrop -> null
            index in localIndexByRelayIndex -> {
                val local = this[localIndexByRelayIndex.getValue(index)]
                message.copy(
                    // 保留 Compose item key 和本地预览，但位置及时间线身份必须继承
                    // 权威 Relay 回显，避免本地回显把已确认消息拉回旧位置。
                    id = local.id,
                    content = message.content.ifBlank { local.content },
                    contentBlocks = local.contentBlocks.ifEmpty { message.contentBlocks },
                    runId = local.runId,
                    clientMessageId = message.clientMessageId.ifBlank { local.clientMessageId },
                    idempotencyKey = message.idempotencyKey.ifBlank { local.idempotencyKey },
                    localTurnOrder = local.localTurnOrder ?: message.localTurnOrder
                )
            }
            else -> message
        }
    }
}

private fun List<ChatMessage>.coalescedDuplicateFileTransferMessages(): List<ChatMessage> {
    val output = mutableListOf<ChatMessage>()
    val stableIndexByKey = mutableMapOf<String, Int>()
    var didMerge = false

    for (message in this) {
        val keys = message.fileTransferDisplayKeys()
        val duplicateIndex = keys?.stableKey
            ?.let { stableIndexByKey[it] }
            ?.takeIf { output[it].isDuplicateFileTransferDisplayMessage(message) }

        if (duplicateIndex == null) {
            output += message
            if (keys?.stableKey != null) stableIndexByKey[keys.stableKey] = output.lastIndex
        } else {
            output[duplicateIndex] = mergeDuplicateFileTransferDisplayMessage(output[duplicateIndex], message)
            didMerge = true
        }
    }

    return if (didMerge) output else this
}

private fun List<ChatMessage>.coalescedDuplicateTransientAssistantPlaceholders(): List<ChatMessage> {
    if (size < 2) return this

    val placeholderIndexGroups = mutableListOf<MutableList<Int>>()
    val turnIdentitiesByGroup = mutableListOf<MutableSet<String>>()
    forEachIndexed { index, message ->
        if (!message.isTransientDisplayWaitingPlaceholder()) return@forEachIndexed
        val turnIdentities = message.displayTurnIdentities()
        if (turnIdentities.isEmpty()) return@forEachIndexed

        val matchingGroupIndexes = turnIdentitiesByGroup.indices.filter { groupIndex ->
            turnIdentitiesByGroup[groupIndex].any(turnIdentities::contains)
        }
        if (matchingGroupIndexes.isEmpty()) {
            placeholderIndexGroups += mutableListOf(index)
            turnIdentitiesByGroup += turnIdentities.toMutableSet()
            return@forEachIndexed
        }

        val targetGroupIndex = matchingGroupIndexes.first()
        placeholderIndexGroups[targetGroupIndex] += index
        turnIdentitiesByGroup[targetGroupIndex] += turnIdentities
        matchingGroupIndexes.drop(1).asReversed().forEach { groupIndex ->
            placeholderIndexGroups[targetGroupIndex] += placeholderIndexGroups[groupIndex]
            turnIdentitiesByGroup[targetGroupIndex] += turnIdentitiesByGroup[groupIndex]
            placeholderIndexGroups.removeAt(groupIndex)
            turnIdentitiesByGroup.removeAt(groupIndex)
        }
    }
    val indexesToDrop = mutableSetOf<Int>()
    placeholderIndexGroups.forEach { placeholderIndexes ->
        if (placeholderIndexes.size < 2) return@forEach
        val keepIndex = placeholderIndexes.reduce { preferredIndex, candidateIndex ->
            val preferred = this[preferredIndex]
            val candidate = this[candidateIndex]
            if (candidate.prefersTransientAssistantPlaceholderOver(preferred)) candidateIndex else preferredIndex
        }
        placeholderIndexes.filterTo(indexesToDrop) { index -> index != keepIndex }
    }
    if (indexesToDrop.isEmpty()) return this

    // 只压缩同一稳定 turn 的重复 waiting；不同 turn 或身份缺失时必须全部保留。
    return filterIndexed { index, _ -> index !in indexesToDrop }
}

private fun List<ChatMessage>.coalescedResolvedTransientAssistantPlaceholders(): List<ChatMessage> {
    if (size < 2) return this

    val visibleAssistantOutputs = filter(ChatMessage::isVisibleAssistantTextOutput)
    if (visibleAssistantOutputs.isEmpty()) return this
    val placeholderIndexesToDrop = mutableSetOf<Int>()
    forEachIndexed { index, message ->
        if (!message.isResolvableTransientTypingPlaceholder()) return@forEachIndexed
        val waitingIdentities = message.displayTurnIdentities()
        if (waitingIdentities.isEmpty()) return@forEachIndexed
        val currentTurnHasVisibleAssistantText = visibleAssistantOutputs.any { output ->
            output.displayTurnIdentities().any(waitingIdentities::contains)
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
    val keyComparison = compareCanonicalTimelineOrderKeys(timelineOrderKey, other.timelineOrderKey)
    if (keyComparison != 0) return keyComparison > 0

    val identityComparison = compareNormalizedText(timelineIdentityKey.ifBlank { id }, other.timelineIdentityKey.ifBlank { other.id })
    if (identityComparison != 0) return identityComparison > 0

    return false
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

private fun ChatMessage.displayTurnIdentities(): Set<String> {
    return buildList {
        contentBlocks.forEach { block -> add(block.sourceRunId) }
        add(turnId)
        add(clientMessageId)
        add(idempotencyKey)
        add(runId)
    }.mapNotNull { value ->
        normalizedDisplayTurnIdentity(value).takeIf { identity -> identity.isNotEmpty() }
    }.toSet()
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
    var preferred = existing
    var fallback = incoming
    val incomingHasStableId = incoming.stableTransferId.isNotEmpty()
    val existingHasStableId = existing.stableTransferId.isNotEmpty()
    if (incomingHasStableId && !existingHasStableId) {
        preferred = incoming
        fallback = existing
    }

    val incomingUrl = normalizedAttachmentText(incoming.downloadUrl ?: incoming.downloadPath)
    val existingUrl = normalizedAttachmentText(existing.downloadUrl ?: existing.downloadPath)
    if (incomingUrl.startsWith("/api/") && !existingUrl.startsWith("/api/")) {
        preferred = incoming
        fallback = existing
    }

    return preferred.preservingLocalVoicePlayback(fallback)
}

private fun RelayChatContentBlock.matchesCompletedAttachmentBlock(
    completedBlock: RelayChatContentBlock
): Boolean {
    if (sharesSourceBoundVoiceIdentity(completedBlock)) return true
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

private fun RelayChatContentBlock.sharesSourceBoundVoiceIdentity(other: RelayChatContentBlock): Boolean {
    if (!isVoiceMessageBlock || !other.isVoiceMessageBlock) return false
    val leftSource = normalizedDisplayTurnIdentity(sourceRunId)
    val rightSource = normalizedDisplayTurnIdentity(other.sourceRunId)
    return leftSource.isNotEmpty() && leftSource == rightSource
}

private fun RelayChatContentBlock.preservingLocalVoicePlayback(
    fallback: RelayChatContentBlock
): RelayChatContentBlock {
    if (!sharesSourceBoundVoiceIdentity(fallback)) return this
    val localReference = listOf(
        localPath,
        downloadUrl,
        downloadPath,
        fallback.localPath,
        fallback.downloadUrl,
        fallback.downloadPath
    ).map(::normalizedAttachmentReference)
        .firstOrNull(::isAndroidLocalVoiceReference)
        ?: return this
    return copy(
        localPath = localReference,
        durationMs = durationMs ?: fallback.durationMs,
        sourceRunId = sourceRunId ?: fallback.sourceRunId
    )
}

private fun isAndroidLocalVoiceReference(value: String): Boolean {
    return value.startsWith("file:", ignoreCase = true) ||
        value.startsWith("content:", ignoreCase = true) ||
        value.startsWith("/data/", ignoreCase = true)
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

    // 缺少 attachmentId/fileId 时保留为独立消息；同名文件不能作为身份依据。
    return FileTransferDisplayKeys(stableKey = stableKey)
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

private fun compareNormalizedText(left: String, right: String): Int {
    val normalizedLeft = left.trim()
    val normalizedRight = right.trim()
    if (normalizedLeft.isEmpty() && normalizedRight.isEmpty()) return 0
    if (normalizedLeft.isEmpty()) return -1
    if (normalizedRight.isEmpty()) return 1
    return normalizedLeft.compareTo(normalizedRight)
}

private data class FileTransferDisplayKeys(
    val stableKey: String?
)
