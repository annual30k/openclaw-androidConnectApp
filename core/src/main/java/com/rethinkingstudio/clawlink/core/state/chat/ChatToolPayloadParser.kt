package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import com.rethinkingstudio.clawlink.core.models.chat.RelayJSONValue
import kotlinx.serialization.json.JsonObject

internal data class ChatToolPayload(
    val toolCallId: String?,
    val toolName: String,
    val displayText: String,
    val state: MessageState,
    val source: JsonObject,
    val rawPayload: JsonObject,
    val contentBlocks: List<RelayChatContentBlock>
)

internal object ChatToolPayloadParser {
    fun parse(obj: JsonObject): ChatToolPayload? {
        val stream = obj.string("stream")?.lowercase()
        val nestedData = obj["data"] as? JsonObject
        val office = obj["office"] as? JsonObject
        val source = nestedData ?: office ?: obj

        val resolvedSourceToolCallId = source.string(
            "toolCallId",
            "tool_call_id",
            "toolUseId",
            "tool_use_id",
            "name"
        )
        val toolCallId = resolvedSourceToolCallId
            ?: obj.string("toolCallId", "tool_call_id", "toolUseId", "tool_use_id", "runId", "run_id")

        val toolName = source.string("toolName", "tool_name", "tool")
            ?: source.string("name")
            ?: obj.string("toolName", "tool_name", "tool", "name")
            ?: "tool"

        val sourceRole = source.string("role")?.lowercase().orEmpty()
        val objRole = obj.string("role")?.lowercase().orEmpty()
        val isToolEnvelope = stream == "tool" ||
            sourceRole in toolRoles ||
            objRole in toolRoles
        val contentBlocks = parseContentBlocks(obj)
        val toolBlocks = contentBlocks.filter { it.isToolCallBlock || it.isToolResultBlock }
        val hasStructuredToolIdentity =
            !resolvedSourceToolCallId.isNullOrBlank() ||
                source.string("toolName", "tool_name", "tool", "name") != null
        val hasToolDataFields = source.containsKey("args") ||
            source.containsKey("arguments") ||
            source.containsKey("partialResult") ||
            source.containsKey("partial_result") ||
            source.containsKey("isError") ||
            source.containsKey("is_error")
        val hasToolMarkers = stream == "tool" ||
            isToolEnvelope ||
            ((nestedData != null || office != null) && hasStructuredToolIdentity) ||
            ((nestedData != null || office != null || isToolEnvelope) && hasToolDataFields)

        if (!hasToolMarkers && toolBlocks.isEmpty()) {
            return null
        }

        val identityBlock = toolBlocks.firstOrNull { it.isToolCallBlock } ?: toolBlocks.firstOrNull()
        val blockToolCallId = identityBlock?.toolCallId
            ?: identityBlock?.toolUseId
            ?: identityBlock?.name
        val blockToolName = identityBlock?.resolvedName
            ?: identityBlock?.toolName
            ?: identityBlock?.name
        val blockDisplayText = toolBlocks.renderToolBlocksDisplayText()
        val normalizedState = normalizeState(
            source.string("state", "phase", "status")
                ?: obj.string("state", "phase", "status")
                ?: identityBlock?.status
        )
        val isErrorState = normalizedState in listOf("error", "failed", "fail")
        val displayText = ChatPayloadText.extract(obj)
            .ifBlank {
                if (isErrorState) {
                    source.renderToolErrorDisplayText().ifBlank { source.renderToolDisplayText() }
                } else {
                    source.renderToolDisplayText()
                }
            }
            .ifBlank { blockDisplayText }

        return ChatToolPayload(
            toolCallId = toolCallId ?: blockToolCallId,
            toolName = blockToolName ?: toolName,
            displayText = displayText,
            state = when {
                isErrorState -> MessageState.failed
                normalizedState in listOf("completed", "complete", "done", "final", "result") -> MessageState.completed
                normalizedState in listOf("streaming", "delta", "in_progress", "update") -> MessageState.streaming
                displayText.isNotBlank() -> MessageState.completed
                else -> MessageState.streaming
            },
            source = source,
            rawPayload = obj,
            contentBlocks = contentBlocks
        )
    }

    private fun normalizeState(value: String?): String {
        return value?.trim()?.lowercase().orEmpty()
    }

    private fun JsonObject.renderToolDisplayText(): String {
        val preferredKeys = listOf("content", "markdown", "text", "body", "message", "value", "result", "output", "data")
        val keys = listOf("result", "partialResult", "partial_result", "output", "content", "args", "text", "delta", "error")
        for (key in keys) {
            val element = this[key] ?: continue
            val value = RelayJSONValue.fromJsonElement(element)
            val rendered = value.renderedText(preferredKeys)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            if (rendered != null) {
                return rendered
            }
            val plain = value.plainText
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            if (plain != null) {
                return plain
            }
        }
        return ""
    }

    private fun JsonObject.renderToolErrorDisplayText(): String {
        return (this["error"] ?: this["message"])?.let { element ->
            RelayJSONValue.fromJsonElement(element)
                .renderedText(listOf("message", "content", "text", "error"))
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: RelayJSONValue.fromJsonElement(element)
                    .plainText
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
        }.orEmpty()
    }

    private val toolRoles = setOf("tool", "toolresult", "tool_result", "tooloutput", "tool_output")
}

internal object ChatPayloadTool {
    fun extract(obj: JsonObject): ChatToolPayload? {
        return ChatToolPayloadParser.parse(obj)
    }
}

internal fun List<RelayChatContentBlock>.renderToolBlocksDisplayText(): String {
    val preferredKeys = listOf("content", "markdown", "text", "body", "message", "value", "result", "output")
    return firstNotNullOfOrNull { block ->
        block.text?.trim()?.takeIf { it.isNotEmpty() }
            ?: block.result?.renderedText(preferredKeys)?.trim()?.takeIf { it.isNotEmpty() }
            ?: block.partialResult?.renderedText(preferredKeys)?.trim()?.takeIf { it.isNotEmpty() }
            ?: block.content?.renderedText(preferredKeys)?.trim()?.takeIf { it.isNotEmpty() }
            ?: block.output?.renderedText(preferredKeys)?.trim()?.takeIf { it.isNotEmpty() }
            ?: block.error?.renderedText(preferredKeys)?.trim()?.takeIf { it.isNotEmpty() }
    }.orEmpty()
}
