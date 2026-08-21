package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageState

internal object TimelinePendingOverlay {
    fun splitPending(messages: List<ChatMessage>): Pair<List<ChatMessage>, List<ChatMessage>> {
        return messages.partition {
            it.state != MessageState.pending &&
                it.state != MessageState.streaming &&
                !it.runId.trim().startsWith("local-user-") &&
                // 本地回合即使 UI 已标记 completed，也必须等 Relay 以相同稳定身份确认后
                // 才能离开 overlay；滞后的 history page 不能因此把它从可见时间线删掉。
                !it.hasUnconfirmedLocalTimelineIdentity()
        }
    }
}
