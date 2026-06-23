package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.gateway.GatewaySummary
import com.rethinkingstudio.clawlink.core.models.gateway.GatewayType
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.utils.TokenDisplayFormatter
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

fun ChatState.visibleContextUsageLine(gateway: GatewaySummary?): String? {
    val selectedGateway = gateway ?: return null
    val sessionContextUsageLine = contextUsageLineFor(selectedGateway.gatewayId, currentSessionKey)
    val enrichedSessionContextUsageLine = sessionContextUsageLine.withGatewayContextLimit(selectedGateway.contextLimit)
    return when (selectedGateway.gatewayType) {
        GatewayType.openclaw -> preferredContextUsageLine(
            sessionContextUsageLine = enrichedSessionContextUsageLine,
            gatewayContextUsageLine = selectedGateway.contextUsage
        ) ?: "--"
        GatewayType.hermes -> enrichedSessionContextUsageLine
            ?: if (sameSessionKey(currentSessionKey, "main")) {
                selectedGateway.contextUsage.trim().ifBlank { "--" }
            } else if (!shouldShowHermesResetContext()) {
                selectedGateway.contextUsage.trim().ifBlank { "--" }
            } else {
                TokenDisplayFormatter.formatUsage(usedTokens = 0, limitTokens = selectedGateway.contextLimit, fallback = null)
            }
    }
}

private fun String?.withGatewayContextLimit(limitTokens: Int?): String? {
    val trimmed = this?.trim()?.takeIf { it.isNotEmpty() && it != "--" } ?: return null
    if (trimmed.contains("/") || trimmed.contains("%")) return trimmed

    val unprefixed = trimmed
        .removePrefix("上下文 ")
        .removePrefix("Context ")
        .trim()
    val usedTokens = TokenDisplayFormatter.parseFormattedCount(unprefixed) ?: return trimmed
    return TokenDisplayFormatter.formatUsage(
        usedTokens = usedTokens,
        limitTokens = limitTokens,
        fallback = trimmed
    )
}

internal fun ChatState.withContextUsageFromPayload(envelope: JsonObject, payload: JsonObject): ChatState {
    val gatewayId = (
        payload.deepString("gatewayId", "gateway_id")
            ?: envelope.deepString("gatewayId", "gateway_id")
            ?: currentGatewayId
        )
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: return this
    val sessionKey = normalizeSessionKey(
        payload.deepString("sessionKey", "session_key")
            ?: envelope.deepString("sessionKey", "session_key")
            ?: payload.deepString("hermesSessionId", "hermes_session_id")
            ?: envelope.deepString("hermesSessionId", "hermes_session_id")
            ?: currentSessionKey
    )

    if (isNewSessionResetPayload(payload)) {
        val usageLine = TokenDisplayFormatter.formatUsage(
            usedTokens = 0,
            limitTokens = contextLimitFrom(payload) ?: contextLimitFrom(envelope),
            fallback = null
        )
        return storingContextUsageLine(gatewayId, sessionKey, usageLine)
    }

    val usageLine = contextUsageLineFrom(payload)
        ?: contextUsageLineFrom(envelope)
        ?: return this
    return storingContextUsageLine(gatewayId, sessionKey, usageLine)
}

private fun ChatState.contextUsageLineFor(gatewayId: String, sessionKey: String): String? {
    val normalizedGatewayId = gatewayId.trim()
    val normalizedSessionKey = normalizeSessionKey(sessionKey)
    return contextUsageLinesByGatewayAndSession[normalizedGatewayId]
        ?.entries
        ?.firstOrNull { sameSessionKey(it.key, normalizedSessionKey) }
        ?.value
        ?.trim()
        ?.takeIf { it.isNotEmpty() && it != "--" }
}

private fun ChatState.storingContextUsageLine(gatewayId: String, sessionKey: String, usageLine: String): ChatState {
    val normalizedGatewayId = gatewayId.trim()
    val normalizedSessionKey = normalizeSessionKey(sessionKey)
    val normalizedUsageLine = usageLine.trim().takeIf { it.isNotEmpty() && it != "--" } ?: return this
    val usageBySession = contextUsageLinesByGatewayAndSession[normalizedGatewayId].orEmpty().toMutableMap()
    usageBySession[normalizedSessionKey] = normalizedUsageLine
    return copy(
        contextUsageLinesByGatewayAndSession = contextUsageLinesByGatewayAndSession.toMutableMap().also { outer ->
            outer[normalizedGatewayId] = usageBySession
        }
    )
}

private fun contextUsageLineFrom(payload: JsonObject): String? {
    val candidates = payload.contextUsageCandidateObjects()
    val fallback = candidates.firstNotNullOfOrNull {
        it.primitiveString("contextUsage", "context_usage", "contextUsageLine", "context_usage_line")
    }
    val usedTokens = candidates.firstNotNullOfOrNull {
        it.primitiveInt(
            "contextUsage",
            "context_usage",
            "contextUsageValue",
            "context_usage_value",
            "promptTokens",
            "prompt_tokens",
            "inputTokens",
            "input_tokens",
            "usedTokens",
            "used_tokens",
            "totalInputTokens",
            "total_input_tokens",
            "totalTokens",
            "total_tokens"
        )
    }
    val limitTokens = candidates.firstNotNullOfOrNull {
        it.primitiveInt(
            "contextLimit",
            "context_limit",
            "maxInputTokens",
            "max_input_tokens",
            "maxContextTokens",
            "max_context_tokens",
            "limit"
        )
    }
    return TokenDisplayFormatter.formatUsage(
        usedTokens = usedTokens,
        limitTokens = limitTokens,
        fallback = fallback
    )
        .trim()
        .takeIf { it.isNotEmpty() && it != "--" }
}

private fun preferredContextUsageLine(sessionContextUsageLine: String?, gatewayContextUsageLine: String?): String? {
    val session = sessionContextUsageLine?.trim()?.takeIf { it.isNotEmpty() && it != "--" }
    val gateway = gatewayContextUsageLine?.trim()?.takeIf { it.isNotEmpty() && it != "--" }
    return when {
        session != null && gateway != null -> {
            val sessionHasDetail = session.contains("/") || session.contains("%")
            val gatewayHasDetail = gateway.contains("/") || gateway.contains("%")
            when {
                sessionHasDetail && gatewayHasDetail && isZeroContextUsageLine(session) && !isZeroContextUsageLine(gateway) -> gateway
                sessionHasDetail && !gatewayHasDetail -> session
                gatewayHasDetail && !sessionHasDetail -> gateway
                else -> session
            }
        }
        session != null -> session
        gateway != null -> gateway
        else -> null
    }
}

private fun isZeroContextUsageLine(usageLine: String): Boolean {
    val normalized = usageLine
        .trim()
        .removePrefix("上下文 ")
        .removePrefix("Context ")
        .trim()
        .lowercase()
    return normalized == "0" ||
        normalized.startsWith("0/") ||
        normalized.startsWith("0 /") ||
        normalized.contains("(0%)")
}

private fun ChatState.shouldShowHermesResetContext(): Boolean {
    val visibleMessages = messages
        .filter { it.role != MessageRole.system }
        .map { it.plainTextContent.trim() }
        .filter { it.isNotEmpty() }
    if (visibleMessages.isEmpty()) return true
    return visibleMessages.all(::isNewSessionResetText)
}

private fun contextLimitFrom(payload: JsonObject): Int? {
    return payload.contextUsageCandidateObjects().firstNotNullOfOrNull {
        it.primitiveInt(
            "contextLimit",
            "context_limit",
            "maxInputTokens",
            "max_input_tokens",
            "maxContextTokens",
            "max_context_tokens",
            "limit"
        )
    }
}

private fun JsonObject.contextUsageCandidateObjects(): List<JsonObject> {
    val result = mutableListOf<JsonObject>()
    fun visit(obj: JsonObject) {
        result += obj
        listOf("payload", "params", "message", "data", "usage", "context", "metadata", "stats").forEach { key ->
            (obj[key] as? JsonObject)?.let(::visit)
        }
    }
    visit(this)
    return result
}

private fun JsonObject.primitiveString(vararg keys: String): String? {
    return keys.firstNotNullOfOrNull { key ->
        (this[key] as? JsonPrimitive)
            ?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }
}

private fun JsonObject.primitiveInt(vararg keys: String): Int? {
    return keys.firstNotNullOfOrNull { key ->
        val primitive = this[key] as? JsonPrimitive ?: return@firstNotNullOfOrNull null
        val value = primitive.intOrNull
            ?: primitive.longOrNull?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt()
            ?: primitive.contentOrNull?.trim()?.toIntOrNull()
        value?.takeIf { it >= 0 }
    }
}

private fun isNewSessionResetPayload(payload: JsonObject): Boolean {
    val text = ChatPayloadText.extract(payload).trim()
    return isNewSessionResetText(text)
}

private fun isNewSessionResetText(text: String): Boolean {
    val normalized = text.trim()
    if (normalized.isEmpty()) return false
    val lower = normalized.lowercase()
    return normalized.startsWith("新会话已开始") ||
        (lower.contains("new session") && (lower.contains("started") || lower.contains("created")))
}
