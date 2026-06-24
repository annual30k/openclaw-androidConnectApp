package com.rethinkingstudio.clawlink.ui.screens.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.rethinkingstudio.clawlink.core.state.chat.ChatState
import com.rethinkingstudio.clawlink.core.state.chat.ChatStore
import kotlinx.coroutines.delay

@Composable
internal fun rememberCoalescedChatState(chatStore: ChatStore): ChatState {
    val rawChatState by chatStore.state.collectAsState()
    var chatState by remember { mutableStateOf(rawChatState) }
    var pendingCoalescedChatState by remember { mutableStateOf<ChatState?>(null) }
    LaunchedEffect(rawChatState) {
        if (shouldCoalesceChatDisplayUpdate(chatState, rawChatState)) {
            pendingCoalescedChatState = rawChatState
        } else {
            pendingCoalescedChatState = null
            chatState = rawChatState
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(50)
            pendingCoalescedChatState?.let { pending ->
                chatState = pending
                pendingCoalescedChatState = null
            }
        }
    }
    return chatState
}
