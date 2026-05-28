package com.rethinkingstudio.clawlink.core.state.chat

data class ChatHistoryWindowState(
    val isLoadingOlder: Boolean = false,
    val isCatchingUp: Boolean = false,
    val hasOlder: Boolean = false,
    val olderCursor: String? = null,
    val newestCursor: String? = null,
    val loadedMessageCount: Int = 0
)
