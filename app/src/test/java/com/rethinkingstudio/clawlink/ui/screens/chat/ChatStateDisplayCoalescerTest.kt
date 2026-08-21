package com.rethinkingstudio.clawlink.ui.screens.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.state.chat.ChatState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatStateDisplayCoalescerTest {
    @Test
    fun coalescedAssistantTailUpdateEmitsOnceAfterDelay() = runTest {
        val emitted = mutableListOf<ChatState>()
        val coalescer = ChatStateDisplayCoalescer(
            scope = backgroundScope,
            delayMillis = 50L,
            onEmit = emitted::add
        )
        val current = chatStateWithAssistantTail(content = "Hel", state = MessageState.streaming)
        val incoming = chatStateWithAssistantTail(content = "Hello", state = MessageState.streaming)

        coalescer.submit(currentDisplayed = current, incoming = incoming)

        advanceTimeBy(49L)
        runCurrent()
        assertTrue(emitted.isEmpty())

        advanceTimeBy(1L)
        runCurrent()
        assertEquals(listOf(incoming), emitted)
    }

    @Test
    fun newerCoalescedUpdateUsesLatestStateWithoutRestartingTheWindow() = runTest {
        val emitted = mutableListOf<ChatState>()
        val coalescer = ChatStateDisplayCoalescer(
            scope = backgroundScope,
            delayMillis = 50L,
            onEmit = emitted::add
        )
        val current = chatStateWithAssistantTail(content = "Hel", state = MessageState.streaming)
        val firstIncoming = chatStateWithAssistantTail(content = "Hello", state = MessageState.streaming)
        val secondIncoming = chatStateWithAssistantTail(content = "Hello world", state = MessageState.streaming)

        coalescer.submit(currentDisplayed = current, incoming = firstIncoming)
        advanceTimeBy(25L)
        runCurrent()
        coalescer.submit(currentDisplayed = current, incoming = secondIncoming)

        advanceTimeBy(25L)
        runCurrent()
        assertEquals(listOf(secondIncoming), emitted)
    }

    @Test
    fun immediateNonCoalescedUpdateCancelsPendingAndEmitsNow() = runTest {
        val emitted = mutableListOf<ChatState>()
        val coalescer = ChatStateDisplayCoalescer(
            scope = backgroundScope,
            delayMillis = 50L,
            onEmit = emitted::add
        )
        val current = chatStateWithAssistantTail(content = "Hel", state = MessageState.streaming)
        val coalescedIncoming = chatStateWithAssistantTail(content = "Hello", state = MessageState.streaming)
        val completedIncoming = chatStateWithAssistantTail(content = "Hello", state = MessageState.completed)

        coalescer.submit(currentDisplayed = current, incoming = coalescedIncoming)
        advanceTimeBy(25L)
        runCurrent()
        coalescer.submit(currentDisplayed = current, incoming = completedIncoming)

        assertEquals(listOf(completedIncoming), emitted)

        advanceTimeBy(100L)
        runCurrent()
        assertEquals(listOf(completedIncoming), emitted)
    }

    private fun chatStateWithAssistantTail(content: String, state: MessageState): ChatState {
        val user = ChatMessage(
            id = "user-1",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "Say hello",
            runId = "local-user-1",
            sortTimestamp = 1.0
        )
        val assistant = ChatMessage(
            id = "assistant-1",
            role = MessageRole.assistant,
            state = state,
            content = content,
            runId = "run-1",
            sortTimestamp = 2.0
        )
        return ChatState(messages = listOf(user, assistant), isStreaming = state == MessageState.streaming)
    }
}
