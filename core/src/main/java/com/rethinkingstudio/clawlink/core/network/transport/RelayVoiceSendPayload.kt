package com.rethinkingstudio.clawlink.core.network.transport

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

data class VoiceSendAudioPayload(
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val contentBase64: String
)

fun buildChatVoiceSendPayload(
    gatewayId: String,
    sessionKey: String,
    requestId: String,
    idempotencyKey: String,
    audio: VoiceSendAudioPayload,
    message: String? = null,
    languageHint: String? = null
): JsonObject {
    val params = buildJsonObject {
        put("sessionKey", JsonPrimitive(sessionKey))
        put("idempotencyKey", JsonPrimitive(idempotencyKey))
        put(
            "audio",
            buildJsonObject {
                put("fileName", JsonPrimitive(audio.fileName))
                put("mimeType", JsonPrimitive(audio.mimeType))
                put("sizeBytes", JsonPrimitive(audio.sizeBytes))
                put("content", JsonPrimitive(audio.contentBase64))
            }
        )
        message?.trim()?.takeIf { it.isNotEmpty() }?.let {
            put("message", JsonPrimitive(it))
        }
        languageHint?.trim()?.takeIf { it.isNotEmpty() }?.let {
            put("languageHint", JsonPrimitive(it))
        }
    }
    return buildJsonObject {
        put("type", JsonPrimitive("cmd"))
        put("id", JsonPrimitive(requestId))
        put("gatewayId", JsonPrimitive(gatewayId))
        put("method", JsonPrimitive("chat.voice.send"))
        put("params", params)
    }
}
