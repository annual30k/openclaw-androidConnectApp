package com.rethinkingstudio.clawlink.core.state.chat

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

internal fun resolveTimelinePayloadScope(
    envelope: JsonObject,
    payload: JsonObject,
    currentGatewayId: String?,
    currentSessionKey: String
): ChatEventScope {
    val gatewayId = envelope.deepString("gatewayId", "gateway_id")
        ?: payload.deepString("gatewayId", "gateway_id")
        ?: firstTimelineObjectString(payload, "gatewayId", "gateway_id")
        ?: currentGatewayId?.trim()?.takeIf { it.isNotEmpty() }
    val sessionKey = payload.deepString("sessionKey", "session_key")
        ?: envelope.deepString("sessionKey", "session_key")
        ?: firstTimelineObjectString(payload, "sessionKey", "session_key")
    return ChatEventScope(
        gatewayId = gatewayId?.trim()?.takeIf { it.isNotEmpty() },
        sessionKey = normalizeSessionKey(sessionKey ?: currentSessionKey),
        hasSessionKey = !sessionKey.isNullOrBlank()
    )
}

private fun firstTimelineObjectString(payload: JsonObject, vararg keys: String): String? {
    (payload["timelineSnapshot"] as? JsonObject)?.string(*keys)?.let { return it }
    val events = payload["timelineEvents"] as? JsonArray ?: return null
    return events.firstNotNullOfOrNull { event ->
        (event as? JsonObject)?.string(*keys)
    }
}
