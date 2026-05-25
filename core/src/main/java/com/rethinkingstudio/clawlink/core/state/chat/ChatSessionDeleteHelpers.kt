package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.gateway.GatewayType
import com.rethinkingstudio.clawlink.core.network.RelayAPIError
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.net.SocketTimeoutException

internal fun chatSessionDeleteRelayMethod(gatewayType: GatewayType): String {
    return if (gatewayType == GatewayType.hermes) "hermes.sessions.delete" else "sessions.delete"
}

internal fun buildChatSessionDeleteCommandParams(
    sessionKey: String,
    deleteTranscript: Boolean
): JsonObject {
    val normalizedSessionKey = normalizeSessionKey(sessionKey)
    return buildJsonObject {
        put("key", JsonPrimitive(normalizedSessionKey))
        put("deleteTranscript", JsonPrimitive(deleteTranscript))
    }
}

internal fun shouldFallbackToRelayCommandForSessionDelete(throwable: Throwable?): Boolean {
    var current = throwable
    while (current != null) {
        when (current) {
            is HttpRequestTimeoutException,
            is SocketTimeoutException -> return true
            is RelayAPIError.ServerError -> {
                if (current.statusCode == 404) return true
                if (current.statusCode == 502 && isTransientGatewayLoadFailureMessage(current.errorCode)) return true
            }
        }
        if (isTransientGatewayLoadFailureMessage(current.message.orEmpty())) {
            return true
        }
        current = current.cause
    }
    return false
}
