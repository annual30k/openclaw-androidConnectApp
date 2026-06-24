package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatSessionItem
import java.time.Instant

internal data class DeletedSessionLocalUpdate(
    val state: ChatState,
    val nextSessionKeyToPersist: String?
)

internal fun sessionsWithActivity(
    sessions: List<ChatSessionItem>,
    sessionKey: String,
    lastActivityAt: String?
): List<ChatSessionItem> {
    val normalizedSessionKey = normalizeSessionKey(sessionKey)
    val activityAt = lastActivityAt?.trim()?.takeIf { it.isNotEmpty() } ?: Instant.now().toString()
    val existingIndex = sessions.indexOfFirst { sameSessionKey(it.sessionKey, normalizedSessionKey) }
    val updatedSessions = if (existingIndex >= 0) {
        sessions.mapIndexed { index, item ->
            if (index == existingIndex) item.copy(lastActivityAt = activityAt) else item
        }
    } else {
        listOf(ChatSessionItem(sessionKey = normalizedSessionKey, lastActivityAt = activityAt)) + sessions
    }
    return updatedSessions.distinctBy { normalizeSessionKey(it.sessionKey).lowercase() }
}

internal fun deletedSessionLocalUpdate(
    current: ChatState,
    gatewayId: String,
    sessionKey: String
): DeletedSessionLocalUpdate {
    val remainingSessions = current.sessions.filterNot { sameSessionKey(it.sessionKey, sessionKey) }
    val isActiveDeleted = current.currentGatewayId == gatewayId && sameSessionKey(current.currentSessionKey, sessionKey)
    val nextSessionKey = if (isActiveDeleted) {
        remainingSessions.firstOrNull()?.sessionKey?.trim()?.ifBlank { defaultSessionKey } ?: defaultSessionKey
    } else {
        current.currentSessionKey
    }
    // 删除当前会话时必须同步清理消息、工具详情和上下文用量，防止旧 session 的缓存误显示到新 session。
    val nextState = current.copy(
        sessions = remainingSessions,
        currentSessionKey = nextSessionKey,
        messages = if (isActiveDeleted) emptyList() else current.messages,
        isSwitchingSession = current.isSwitchingSession || isActiveDeleted,
        historyWindow = if (isActiveDeleted) ChatHistoryWindowState() else current.historyWindow,
        toolDetailCacheByKey = current.toolDetailCacheByKey.filterKeys { key ->
            !isToolDetailCacheKeyForSession(key, gatewayId, sessionKey)
        },
        contextUsageLinesByGatewayAndSession = current.contextUsageLinesByGatewayAndSession.toMutableMap().also { byGateway ->
            val usageBySession = byGateway[gatewayId]?.toMutableMap() ?: return@also
            usageBySession.keys
                .filter { sameSessionKey(it, sessionKey) }
                .forEach { usageBySession.remove(it) }
            byGateway[gatewayId] = usageBySession
        }
    )
    return DeletedSessionLocalUpdate(
        state = nextState,
        nextSessionKeyToPersist = nextSessionKey.takeIf { isActiveDeleted }
    )
}

internal fun isToolDetailCacheKeyForSession(key: String, gatewayId: String, sessionKey: String): Boolean {
    val parts = key.split("||", limit = 3)
    return parts.size == 3 && parts[0] == gatewayId && sameSessionKey(parts[1], sessionKey)
}

internal fun voicePlaybackReadStorageKey(
    identifier: String,
    gatewayId: String?,
    sessionKey: String?,
    currentGatewayId: String?,
    currentSessionKey: String
): String {
    val normalizedIdentifier = identifier.trim()
    if (normalizedIdentifier.isEmpty()) return ""

    val resolvedGatewayId = (gatewayId ?: currentGatewayId ?: "gateway").trim()
    val resolvedSessionKey = (sessionKey ?: currentSessionKey.ifBlank { defaultSessionKey }).trim()

    return "$resolvedGatewayId|$resolvedSessionKey|$normalizedIdentifier"
}
