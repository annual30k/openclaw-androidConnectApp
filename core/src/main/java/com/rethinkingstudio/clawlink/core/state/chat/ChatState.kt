package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.ChatSessionItem
import com.rethinkingstudio.clawlink.core.models.chat.ToolDetailResponse

data class ToolDetailCacheEntry(
    val isLoading: Boolean = false,
    val response: ToolDetailResponse? = null,
    val issueMessage: String? = null
) {
    companion object {
        val Loading = ToolDetailCacheEntry(isLoading = true)
        fun loaded(response: ToolDetailResponse) = ToolDetailCacheEntry(response = response)
        fun unavailable(message: String) = ToolDetailCacheEntry(issueMessage = message)
    }
}

data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val sessions: List<ChatSessionItem> = emptyList(),
    val currentGatewayId: String? = null,
    val currentSessionKey: String = "",
    val isLoading: Boolean = false,
    val isSwitchingSession: Boolean = false,
    val isStreaming: Boolean = false,
    val isStoppingRun: Boolean = false,
    val errorMessage: String? = null,
    val showInvocationProcess: Boolean = true,
    val contextUsageLinesByGatewayAndSession: Map<String, Map<String, String>> = emptyMap(),
    val readVoicePlaybackIdentifiers: Set<String> = emptySet(),
    val historyWindow: ChatHistoryWindowState = ChatHistoryWindowState(),
    val toolDetailCacheByKey: Map<String, ToolDetailCacheEntry> = emptyMap()
)

fun toolDetailCacheKey(gatewayId: String, sessionKey: String, toolCallId: String): String =
    "${gatewayId.trim()}||${sessionKey.trim().ifBlank { "main" }}||${toolCallId.trim()}"

internal data class ChatRunScope(
    val gatewayId: String,
    val sessionKey: String,
    val assistantMessageId: String? = null,
    val triggeringUserMessageId: String? = null
)

internal data class ChatEventScope(
    val gatewayId: String?,
    val sessionKey: String,
    val hasSessionKey: Boolean = true,
    val runScope: ChatRunScope? = null
)

internal data class ParsedAgentSessionKey(
    val agentId: String,
    val rest: String
)
