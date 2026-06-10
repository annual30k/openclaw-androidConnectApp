package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage

internal fun debugTimelineDump(
    messages: List<ChatMessage>,
    sessionKey: String = "main"
): List<Map<String, String?>> {
    return messages.map { message ->
        mapOf(
            "stableKey" to stableTimelineKey(sessionKey, message).stableKey,
            "seq" to message.sortTimestamp?.takeIf { it % 1.0 == 0.0 }?.toLong()?.toString(),
            "messageId" to message.id,
            "clientMessageId" to message.runId.removePrefix("local-user-").takeIf { it != message.runId && it.isNotBlank() },
            "state" to message.state.name,
            "role" to message.role.name,
            "runId" to message.runId.takeIf { it.isNotBlank() },
            "turnId" to null,
            "attachmentIds" to message.contentBlocks.mapNotNull { it.stableAttachmentId }.joinToString(",")
        )
    }
}
