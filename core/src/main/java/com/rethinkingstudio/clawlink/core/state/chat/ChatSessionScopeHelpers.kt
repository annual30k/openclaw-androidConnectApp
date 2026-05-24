package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatSessionItem
import java.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal const val defaultSessionKey = "main"

internal fun eventTimestampIso(payload: JsonObject): String {
    eventTimestampMillis(payload)?.let { return Instant.ofEpochMilli(it).toString() }
    return payload.string("createdAt", "created_at")
        ?: (payload["message"] as? JsonObject)?.string("createdAt", "created_at")
        ?: Instant.now().toString()
}

internal fun eventTimestampMillis(payload: JsonObject): Long? {
    val raw = payload.firstPrimitiveContent("ts", "timestamp", "createdAt", "created_at", "time")
        ?: (payload["message"] as? JsonObject)?.firstPrimitiveContent("timestamp", "createdAt", "created_at")
        ?: return null
    raw.toDoubleOrNull()?.let { numeric ->
        return if (numeric > 10_000_000_000.0) numeric.toLong() else (numeric * 1000).toLong()
    }
    return runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
}

internal fun List<ChatSessionItem>.matchingSessionKey(candidate: String): String? {
    val normalizedCandidate = normalizeSessionKey(candidate)
    return firstOrNull { sameSessionKey(it.sessionKey, normalizedCandidate) }
        ?.sessionKey
        ?.trim()
        ?.ifBlank { defaultSessionKey }
}

internal fun sameSessionKey(left: String?, right: String?): Boolean {
    val normalizedLeft = normalizeSessionKey(left)
    val normalizedRight = normalizeSessionKey(right)
    if (normalizedLeft == normalizedRight) return true

    val parsedLeft = parseAgentSessionKey(normalizedLeft)
    val parsedRight = parseAgentSessionKey(normalizedRight)
    if (parsedLeft != null && parsedRight != null) {
        return parsedLeft.agentId == parsedRight.agentId && parsedLeft.rest == parsedRight.rest
    }
    if (parsedLeft != null) {
        return parsedLeft.rest == normalizedRight
    }
    if (parsedRight != null) {
        return normalizedLeft == parsedRight.rest
    }
    val prefixedLeft = parseHermesSessionKey(normalizedLeft)
    val prefixedRight = parseHermesSessionKey(normalizedRight)
    if (prefixedLeft != null && prefixedRight != null) {
        return prefixedLeft.first == prefixedRight.first && prefixedLeft.second == prefixedRight.second
    }
    if (prefixedLeft != null) {
        return prefixedLeft.second == normalizedRight
    }
    if (prefixedRight != null) {
        return normalizedLeft == prefixedRight.second
    }
    return false
}

internal fun normalizeSessionKey(sessionKey: String?): String {
    return sessionKey?.trim()?.takeIf { it.isNotEmpty() } ?: defaultSessionKey
}

internal fun parseAgentSessionKey(sessionKey: String): ParsedAgentSessionKey? {
    if (!sessionKey.startsWith("agent:")) return null
    val segments = sessionKey.split(":", limit = 3)
    if (segments.size < 3) return null
    val agentId = segments[1].trim()
    val rest = segments[2].trim()
    if (agentId.isBlank() || rest.isBlank()) return null
    return ParsedAgentSessionKey(agentId = agentId, rest = rest)
}

private fun parseHermesSessionKey(sessionKey: String): Pair<String, String>? {
    val prefix = "hermes:"
    if (!sessionKey.startsWith(prefix)) return null
    val rest = sessionKey.substring(prefix.length).trim()
    if (rest.isBlank()) return null
    return "hermes" to rest
}

internal fun JsonObject.deepString(vararg keys: String): String? {
    string(*keys)?.let { return it }
    val nestedKeys = listOf("payload", "params", "message", "data", "office")
    return nestedKeys.firstNotNullOfOrNull { nestedKey ->
        (this[nestedKey] as? JsonObject)?.string(*keys)
    }
}

internal fun JsonObject.firstPrimitiveContent(vararg keys: String): String? {
    return keys.firstNotNullOfOrNull { key ->
        (this[key] as? JsonPrimitive)
            ?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }
}
