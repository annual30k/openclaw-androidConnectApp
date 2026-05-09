package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayJSONValue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

internal object ChatPayloadTool {
    data class ToolPayload(
        val toolCallId: String?,
        val toolName: String,
        val displayText: String,
        val state: MessageState
    )

    fun extract(obj: JsonObject): ToolPayload? {
        return extractFromSource(obj)
    }

    private fun extractFromSource(obj: JsonObject): ToolPayload? {
        val stream = obj.string("stream")?.lowercase()
        val payload = obj["data"] as? JsonObject
        val office = obj["office"] as? JsonObject
        val source = payload ?: office ?: obj

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

        val isToolEnvelope = stream == "tool" ||
            source.string("role")?.lowercase() == "tool" ||
            obj.string("role")?.lowercase() == "tool"
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
            ((payload != null || office != null) && hasStructuredToolIdentity) ||
            ((payload != null || office != null || isToolEnvelope) && hasToolDataFields)

        if (!hasToolMarkers) {
            return null
        }

        val normalizedState = normalizeState(
            source.string("state", "phase", "status")
                ?: obj.string("state", "phase", "status")
        )
        val displayText = ChatPayloadText.extract(obj)
            .ifBlank { source.renderToolDisplayText() }

        return ToolPayload(
            toolCallId = toolCallId,
            toolName = toolName,
            displayText = displayText,
            state = when {
                normalizedState in listOf("error", "failed", "fail") -> MessageState.failed
                normalizedState in listOf("completed", "complete", "done", "final", "result") -> MessageState.completed
                normalizedState in listOf("streaming", "delta", "in_progress", "update") -> MessageState.streaming
                displayText.isNotBlank() -> MessageState.completed
                else -> MessageState.streaming
            }
        )
    }

    private fun normalizeState(value: String?): String {
        return value?.trim()?.lowercase().orEmpty()
    }

    private fun JsonObject.string(vararg keys: String): String? {
        return keys.firstNotNullOfOrNull { key ->
            (this[key] as? JsonPrimitive)
                ?.contentOrNull
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }
    }

    private fun JsonObject.renderToolDisplayText(): String {
        val preferredKeys = listOf("content", "markdown", "text", "body", "message", "value", "result", "output", "data")
        val keys = listOf("result", "partialResult", "partial_result", "output", "content", "args", "text", "delta", "error")
        for (key in keys) {
            val element = this[key] ?: continue
            val rendered = RelayJSONValue.fromJsonElement(element)
                .renderedText(preferredKeys)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            if (rendered != null) {
                return rendered
            }
            val plain = RelayJSONValue.fromJsonElement(element)
                .plainText
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            if (plain != null) {
                return plain
            }
        }
        return ""
    }
}

internal object ChatPayloadText {
    fun extract(obj: JsonObject): String {
        obj.stringValue("content", "text", "delta")?.let { return it }
        val data = obj["data"] as? JsonObject
        data?.stringValue("content", "text", "delta")?.let { return it }

        val message = obj["message"] as? JsonObject
        message?.stringValue("text")?.let { return it }
        val messageContent = message?.get("content")
        if (messageContent is JsonArray) {
            extractTextFromContentArray(messageContent)?.let { return it }
        }
        if (messageContent is JsonPrimitive) {
            messageContent.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        }

        val content = obj["content"]
        if (content is JsonArray) {
            extractTextFromContentArray(content)?.let { return it }
        }
        return ""
    }

    private fun extractTextFromContentArray(array: JsonArray): String? {
        return array.mapNotNull { element ->
            val block = element as? JsonObject ?: return@mapNotNull null
            val type = block["type"]?.jsonPrimitive?.contentOrNull
            if (type != null && type != "text" && type != "output_text") {
                return@mapNotNull null
            }
            block.rawStringValue("text", "content")
        }
            .joinToString("")
            .trim()
            .takeIf { it.isNotEmpty() }
    }

    private fun JsonObject.stringValue(vararg keys: String): String? {
        return keys.firstNotNullOfOrNull { key ->
            (this[key] as? JsonPrimitive)
                ?.contentOrNull
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }
    }

    private fun JsonObject.rawStringValue(vararg keys: String): String? {
        return keys.firstNotNullOfOrNull { key ->
            (this[key] as? JsonPrimitive)
                ?.contentOrNull
                ?.takeIf { it.isNotEmpty() }
        }
    }
}
