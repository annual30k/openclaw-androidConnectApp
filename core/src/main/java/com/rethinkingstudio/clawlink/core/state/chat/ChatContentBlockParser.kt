package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import com.rethinkingstudio.clawlink.core.models.chat.RelayJSONValue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

internal fun parseContentBlocks(root: JsonObject): List<RelayChatContentBlock> {
    val arrays = mutableListOf<JsonArray>()
    collectContentBlockArrays(root, arrays, mutableSetOf())
    if (arrays.isEmpty()) return emptyList()

    val seen = linkedSetOf<String>()
    return arrays.flatMap { array ->
        array.mapNotNull { element -> parseContentBlock(element) }
    }.filter { block -> seen.add(block.signature()) }
}

private fun parseContentBlock(element: JsonElement): RelayChatContentBlock? {
    return try {
        val obj = element.jsonObject
        val text = obj["text"]?.jsonPrimitive?.content
        var type = obj["type"]?.jsonPrimitive?.content ?: if (text != null) "text" else return null

        val name = obj["name"]?.jsonPrimitive?.content 
            ?: obj["tool_name"]?.jsonPrimitive?.content 
            ?: obj["tool"]?.jsonPrimitive?.content

        if (type.trim().lowercase() == "text") {
            if (name != null) {
                type = "tool_result"
            }
        }
        RelayChatContentBlock(
            type = type,
            text = obj["text"]?.jsonPrimitive?.content,
            name = obj["name"]?.jsonPrimitive?.content ?: obj["tool_name"]?.jsonPrimitive?.content ?: obj["tool"]?.jsonPrimitive?.content,
            fileId = obj.string("fileId", "file_id"),
            fileName = obj.string("fileName", "file_name", "name"),
            mimeType = obj.string("mimeType", "mime_type"),
            sizeBytes = obj.int("sizeBytes", "size_bytes"),
            durationMs = obj.int("durationMs", "duration_ms"),
            imageWidth = obj.int("imageWidth", "image_width"),
            imageHeight = obj.int("imageHeight", "image_height"),
            downloadUrl = obj.string("downloadUrl", "download_url"),
            downloadPath = obj.string("downloadPath", "download_path"),
            thumbnailUrl = obj.string("thumbnailUrl", "thumbnail_url"),
            expiresAt = obj.string("expiresAt", "expires_at"),
            senderDisplayName = obj.string("senderDisplayName", "sender_display_name"),
            transcript = obj.string("transcript"),
            gatewayId = obj.string("gatewayId", "gateway_id"),
            sessionKey = obj.string("sessionKey", "session_key"),
            arguments = obj["arguments"]?.let { RelayJSONValue.fromJsonElement(it) },
            args = obj["args"]?.let { RelayJSONValue.fromJsonElement(it) },
            result = obj["result"]?.let { RelayJSONValue.fromJsonElement(it) },
            partialResult = (obj["partialResult"] ?: obj["partial_result"])?.let { RelayJSONValue.fromJsonElement(it) },
            content = obj["content"]?.let { RelayJSONValue.fromJsonElement(it) },
            output = obj["output"]?.let { RelayJSONValue.fromJsonElement(it) },
            error = obj["error"]?.let { RelayJSONValue.fromJsonElement(it) },
            toolCallId = obj["tool_call_id"]?.jsonPrimitive?.content ?: obj["toolCallId"]?.jsonPrimitive?.content,
            toolUseId = obj["tool_use_id"]?.jsonPrimitive?.content ?: obj["toolUseId"]?.jsonPrimitive?.content,
            toolName = obj.string("toolName", "tool_name"),
            status = obj["status"]?.jsonPrimitive?.content,
            isError = obj["is_error"]?.jsonPrimitive?.booleanOrNull ?: obj["isError"]?.jsonPrimitive?.booleanOrNull
        )
    } catch (_: Exception) {
        null
    }
}

private fun collectContentBlockArrays(
    element: JsonElement?,
    arrays: MutableList<JsonArray>,
    visited: MutableSet<Int>
) {
    val current = element ?: return
    val identity = System.identityHashCode(current)
    if (!visited.add(identity)) return

    when (current) {
        is JsonArray -> {
            if (current.any { it is JsonObject && it["type"] != null }) {
                arrays += current
            }
            current.forEach { child ->
                collectContentBlockArrays(child, arrays, visited)
            }
        }
        is JsonObject -> {
            current.values.forEach { child ->
                if (child is JsonArray || child is JsonObject) {
                    collectContentBlockArrays(child, arrays, visited)
                }
            }
        }
        else -> Unit
    }
}

internal fun RelayChatContentBlock.signature(): String {
    return listOf(
        type,
        toolCallId.orEmpty(),
        toolUseId.orEmpty(),
        name.orEmpty(),
        text.orEmpty(),
        fileId.orEmpty(),
        fileName.orEmpty(),
        status.orEmpty(),
        isError?.toString().orEmpty()
    ).joinToString("|")
}

internal fun JsonObject.string(vararg keys: String): String? {
    return keys.firstNotNullOfOrNull { key ->
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)
            ?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }
}

internal fun JsonObject.int(vararg keys: String): Int? {
    return keys.firstNotNullOfOrNull { key ->
        val primitive = this[key] as? kotlinx.serialization.json.JsonPrimitive ?: return@firstNotNullOfOrNull null
        primitive.intOrNull ?: primitive.longOrNull?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt()
    }
}
