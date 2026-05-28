package com.rethinkingstudio.clawlink.core.state.chat

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

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
