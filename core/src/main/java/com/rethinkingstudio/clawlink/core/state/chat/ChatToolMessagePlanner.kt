package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import com.rethinkingstudio.clawlink.core.models.chat.RelayJSONValue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import java.util.UUID

internal data class ChatToolMessagePlan(
    val toolRunId: String,
    val toolCallId: String,
    val toolName: String,
    val role: MessageRole,
    val state: MessageState,
    val content: String,
    val contentBlocks: List<RelayChatContentBlock>
)

internal object ChatToolMessagePlanner {
    fun plan(payload: ChatToolPayload): ChatToolMessagePlan? {
        val toolCallId = payload.toolCallId
            ?: payload.rawPayload.string("runId", "run_id")
            ?: payload.rawPayload.string("toolRunId", "tool_run_id")
            ?: UUID.randomUUID().toString()
        val explicitBlocks = payload.contentBlocks
        val contentBlocks = if (explicitBlocks.any { it.isToolCallBlock || it.isToolResultBlock }) {
            explicitBlocks
        } else {
            buildSyntheticToolContentBlocks(
                source = payload.source,
                toolCallId = toolCallId,
                toolName = payload.toolName,
                displayText = payload.displayText,
                isError = payload.state == MessageState.failed
            )
        }
        val role = if (contentBlocks.any { it.isToolCallBlock || it.isToolResultBlock }) {
            MessageRole.tool
        } else {
            MessageRole.assistant
        }
        val content = payload.displayText.ifBlank { contentBlocks.renderToolBlocksDisplayText() }

        if (content.isBlank() && contentBlocks.isEmpty()) {
            return null
        }

        return ChatToolMessagePlan(
            toolRunId = "tool:$toolCallId",
            toolCallId = toolCallId,
            toolName = payload.toolName,
            role = role,
            state = payload.state,
            content = content,
            contentBlocks = contentBlocks
        )
    }

    private fun buildSyntheticToolContentBlocks(
        source: JsonObject?,
        toolCallId: String,
        toolName: String,
        displayText: String,
        isError: Boolean
    ): List<RelayChatContentBlock> {
        val normalizedSource = source ?: buildJsonObject { }
        val normalizedToolName = toolName.trim().ifEmpty { "tool" }
        val displayValue = toolDisplayJsonValue(normalizedSource, displayText)
        val callArguments = firstJsonValue(normalizedSource, "args", "arguments", "content")
        val normalizedPhase = normalizedSource.string("phase", "state", "status")?.trim()?.lowercase().orEmpty()
        val isPartial = normalizedPhase == "update" || normalizedPhase == "streaming"

        val blocks = mutableListOf<RelayChatContentBlock>()
        if (callArguments != null || normalizedToolName.isNotEmpty()) {
            blocks += RelayChatContentBlock(
                type = "tool_use",
                name = normalizedToolName,
                toolCallId = toolCallId,
                arguments = callArguments,
                args = callArguments
            )
        }
        if (displayValue != null || displayText.isNotBlank() || isError) {
            blocks += RelayChatContentBlock(
                type = "tool_result",
                text = if (displayValue != null) null else displayText.ifBlank { null },
                name = normalizedToolName,
                toolCallId = toolCallId,
                result = if (isError || isPartial) null else displayValue,
                partialResult = if (isPartial) displayValue else null,
                content = displayValue,
                output = displayValue,
                error = if (isError) displayValue else null,
                isError = isError
            )
        }
        return blocks
    }

    private fun toolDisplayJsonValue(source: JsonObject, displayText: String): RelayJSONValue? {
        val direct = firstJsonValue(source, "result", "partialResult", "partial_result", "output", "content", "args")
        if (direct != null) {
            return direct
        }
        source.string("text", "delta", "error")?.let {
            return RelayJSONValue.StringVal(it)
        }
        val message = source["message"] as? JsonObject
        val messageContent = message?.get("content")
        if (messageContent != null) {
            return RelayJSONValue.fromJsonElement(messageContent)
        }
        return displayText.trim().takeIf { it.isNotEmpty() }?.let {
            RelayJSONValue.StringVal(it)
        }
    }

    private fun firstJsonValue(payload: JsonObject, vararg keys: String): RelayJSONValue? {
        return keys.firstNotNullOfOrNull { key ->
            payload[key]?.let { RelayJSONValue.fromJsonElement(it) }
        }
    }
}
