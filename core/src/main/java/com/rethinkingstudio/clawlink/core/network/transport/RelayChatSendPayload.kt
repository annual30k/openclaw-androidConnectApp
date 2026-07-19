package com.rethinkingstudio.clawlink.core.network.transport

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

data class RelayChatSendAttachmentPayload(
    val fileId: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
    val sourceRunId: String?
) {
    fun toJsonObject(): JsonObject = buildJsonObject {
        put("fileId", JsonPrimitive(fileId))
        put("fileName", JsonPrimitive(fileName))
        put("mimeType", JsonPrimitive(mimeType))
        put("sizeBytes", JsonPrimitive(sizeBytes))
        put("sha256", JsonPrimitive(sha256))
        sourceRunId?.trim()?.takeIf { it.isNotEmpty() }?.let { put("sourceRunId", JsonPrimitive(it)) }
    }
}

fun buildChatSendPayload(
    gatewayId: String,
    sessionKey: String,
    content: String,
    attachments: List<RelayChatSendAttachmentPayload> = emptyList(),
    idempotencyKey: String,
    requestId: String
): JsonObject {
    return buildChatSendPayloadFromJsonAttachments(
        gatewayId = gatewayId,
        sessionKey = sessionKey,
        content = content,
        attachments = attachments.map { it.toJsonObject() },
        idempotencyKey = idempotencyKey,
        requestId = requestId
    )
}

internal fun buildChatSendPayloadFromJsonAttachments(
    gatewayId: String,
    sessionKey: String,
    content: String,
    attachments: List<JsonObject> = emptyList(),
    idempotencyKey: String,
    requestId: String
): JsonObject {
    val params = buildJsonObject {
        put("sessionKey", JsonPrimitive(sessionKey))
        put("message", JsonPrimitive(content))
        put("idempotencyKey", JsonPrimitive(idempotencyKey))
        if (attachments.isNotEmpty()) {
            put("attachments", JsonArray(attachments))
        }
    }
    return buildJsonObject {
        put("type", JsonPrimitive("cmd"))
        put("id", JsonPrimitive(requestId))
        put("gatewayId", JsonPrimitive(gatewayId))
        put("method", JsonPrimitive("chat.send"))
        put("params", params)
    }
}

fun buildSessionResetPayload(
    gatewayId: String,
    sessionKey: String,
    requestId: String
): JsonObject {
    val params = buildJsonObject {
        put("key", JsonPrimitive(sessionKey))
    }
    return buildJsonObject {
        put("type", JsonPrimitive("cmd"))
        put("id", JsonPrimitive(requestId))
        put("gatewayId", JsonPrimitive(gatewayId))
        put("method", JsonPrimitive("sessions.reset"))
        put("params", params)
    }
}
