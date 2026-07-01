package com.rethinkingstudio.clawlink.ui.screens.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.rethinkingstudio.clawlink.core.state.chat.ChatState
import com.rethinkingstudio.clawlink.core.state.chat.ChatStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun rememberCoalescedChatState(chatStore: ChatStore): ChatState {
    val rawChatState by chatStore.state.collectAsState()
    var chatState by remember { mutableStateOf(rawChatState) }
    val scope = rememberCoroutineScope()
    val coalescer = remember(scope) {
        ChatStateDisplayCoalescer(scope = scope) { nextState ->
            chatState = nextState
        }
    }

    DisposableEffect(chatStore) {
        onDispose { coalescer.cancelPending() }
    }

    LaunchedEffect(rawChatState) {
        coalescer.submit(
            currentDisplayed = chatState,
            incoming = rawChatState
        )
    }
    return chatState
}

internal class ChatStateDisplayCoalescer(
    private val scope: CoroutineScope,
    private val delayMillis: Long = 50L,
    private val onEmit: (ChatState) -> Unit
) {
    private var pendingJob: Job? = null

    fun submit(
        currentDisplayed: ChatState,
        incoming: ChatState
    ) {
        if (!shouldCoalesceChatDisplayUpdate(currentDisplayed, incoming)) {
            pendingJob?.cancel()
            pendingJob = null
            onEmit(incoming)
            return
        }

        // 只在确认为“同一条 assistant 尾部文本增量”时做一次性延迟合并；
        // 不能用永久 50ms 轮询，否则聊天页在整个生命周期里都会持续唤醒主线程。
        pendingJob?.cancel()
        pendingJob = scope.launch {
            delay(delayMillis)
            pendingJob = null
            onEmit(incoming)
        }
    }

    fun cancelPending() {
        pendingJob?.cancel()
        pendingJob = null
    }
}
