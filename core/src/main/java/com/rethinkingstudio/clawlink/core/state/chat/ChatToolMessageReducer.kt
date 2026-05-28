package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage

internal data class ChatToolMessageReduction(
    val messages: List<ChatMessage>,
    val message: ChatMessage
)

internal object ChatToolMessageReducer {
    fun upsert(
        messages: List<ChatMessage>,
        plan: ChatToolMessagePlan,
        nowEpochSeconds: Double,
        anchorAssistantMessageId: String? = null
    ): ChatToolMessageReduction {
        val existingIndex = messages.indexOfFirst { it.matchesToolPlan(plan) }
        val existing = messages.getOrNull(existingIndex)
        val anchorIndex = anchorAssistantMessageId
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { anchorId -> messages.indexOfFirst { it.id == anchorId } }
            ?.takeIf { it >= 0 }
        val resolvedSortTimestamp = existing?.sortTimestamp
            ?: anchorIndex
                ?.let { index -> messages[index].sortTimestamp?.minus(0.0001) }
            ?: nowEpochSeconds
        val mergedBlocks = if (existing != null) {
            (existing.contentBlocks + plan.contentBlocks)
                .distinctBy { it.signature() }
                .sortedBy { if (it.isToolCallBlock) 0 else 1 }
        } else {
            plan.contentBlocks
        }
        val message = ChatMessage(
            id = existing?.id ?: plan.toolRunId,
            role = plan.role,
            state = plan.state,
            content = plan.content.ifBlank { existing?.content.orEmpty() },
            contentBlocks = mergedBlocks,
            createdAt = existing?.createdAt ?: "",
            runId = existing?.runId?.takeIf { it.isNotBlank() } ?: plan.toolCallId,
            sortTimestamp = resolvedSortTimestamp
        )
        val reducedMessages = messages.toMutableList()
        if (existingIndex >= 0) {
            reducedMessages[existingIndex] = message
        } else if (anchorIndex != null) {
            reducedMessages.add(anchorIndex, message)
        } else {
            reducedMessages += message
        }
        return ChatToolMessageReduction(
            messages = reducedMessages,
            message = message
        )
    }

    private fun ChatMessage.matchesToolPlan(plan: ChatToolMessagePlan): Boolean {
        if (id == plan.toolRunId) return true
        if (role != com.rethinkingstudio.clawlink.core.models.chat.MessageRole.tool && !hasToolContent) return false
        return runId == plan.toolRunId ||
            runId == plan.toolCallId ||
            contentBlocks.any { block ->
                block.toolCallId == plan.toolCallId ||
                    block.toolUseId == plan.toolCallId ||
                    block.name == plan.toolCallId
            }
    }
}
