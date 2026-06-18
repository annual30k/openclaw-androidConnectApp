package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageState

internal object TimelinePendingOverlay {
    fun splitPending(messages: List<ChatMessage>): Pair<List<ChatMessage>, List<ChatMessage>> {
        return messages.partition {
            it.state != MessageState.pending &&
                it.state != MessageState.streaming &&
                !it.runId.trim().startsWith("local-user-")
        }
    }
}
