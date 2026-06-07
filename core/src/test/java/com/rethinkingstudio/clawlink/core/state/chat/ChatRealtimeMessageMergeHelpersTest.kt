package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRealtimeMessageMergeHelpersTest {
    @Test
    fun mergesRemoteVoiceTranscriptIntoMatchingLocalVoiceBubble() {
        val localVoice = voiceMessage(runId = "local-user-voice-run-1", sortTimestamp = 10.0)
        val pendingAssistant = assistantMessage(
            id = "assistant-1",
            runId = "voice-run-1",
            content = "等待宿主机识别语音...",
            sortTimestamp = 10.001
        )

        val merged = mergeRemoteUserMessageIntoCurrentMessages(
            currentMessages = listOf(localVoice, pendingAssistant),
            content = "今天天气不错",
            contentBlocks = emptyList(),
            runId = "voice-run-1",
            sortTimestamp = 11.0
        )

        assertEquals(listOf("local-user-voice-run-1", "voice-run-1"), merged.map { it.runId })
        assertTrue(merged.first().hasVoiceContent)
        assertEquals("今天天气不错", merged.first().voiceTranscriptText)
        assertFalse(merged.any { it.role == MessageRole.user && !it.hasVoiceContent && it.content == "今天天气不错" })
    }

    @Test
    fun mergesRunlessRemoteVoiceTranscriptIntoRecentLocalVoiceBubble() {
        val localVoice = voiceMessage(runId = "local-user-voice-run-fallback", sortTimestamp = 100.0)

        val merged = mergeRemoteUserMessageIntoCurrentMessages(
            currentMessages = listOf(localVoice),
            content = "你可以做什么",
            contentBlocks = emptyList(),
            runId = "",
            sortTimestamp = 101.0
        )

        assertEquals(listOf("local-user-voice-run-fallback"), merged.map { it.runId })
        assertEquals("你可以做什么", merged.first().voiceTranscriptText)
    }

    @Test
    fun consumesDuplicateRunlessRemoteVoiceTranscriptAlreadyAttachedToLocalVoiceBubble() {
        val transcript = "你可以做什么"
        val localVoice = voiceMessage(
            runId = "local-user-voice-run-duplicate",
            sortTimestamp = 100.0,
            transcript = transcript
        )

        val merged = mergeRemoteUserMessageIntoCurrentMessages(
            currentMessages = listOf(localVoice),
            content = transcript,
            contentBlocks = emptyList(),
            runId = "",
            sortTimestamp = 101.0
        )

        assertEquals(listOf("local-user-voice-run-duplicate"), merged.map { it.runId })
        assertEquals(transcript, merged.first().voiceTranscriptText)
        assertFalse(merged.any { it.role == MessageRole.user && !it.hasVoiceContent && it.content == transcript })
    }

    @Test
    fun insertsRemoteUserEchoBeforePendingAssistantForSameRun() {
        val pendingAssistant = assistantMessage(
            id = "assistant-1",
            runId = "run-1",
            content = "正在连接...",
            sortTimestamp = 20.001
        )

        val merged = mergeRemoteUserMessageIntoCurrentMessages(
            currentMessages = listOf(pendingAssistant),
            content = "hello",
            contentBlocks = emptyList(),
            runId = "run-1",
            sortTimestamp = 21.0
        )

        assertEquals(listOf(MessageRole.user, MessageRole.assistant), merged.map { it.role })
        assertEquals("run-1", merged.first().runId)
        assertTrue((merged.first().sortTimestamp ?: 0.0) < (pendingAssistant.sortTimestamp ?: 0.0))
    }

    @Test
    fun mergesRemoteMediaAttachmentEchoIntoMatchingLocalUserBubble() {
        val localUser = ChatMessage(
            id = "local-user",
            role = MessageRole.user,
            content = "分析一下这张照片",
            runId = "local-user-run-1",
            sortTimestamp = 50.0
        )
        val pendingAssistant = assistantMessage(
            id = "assistant-1",
            runId = "run-1",
            content = "正在连接...",
            sortTimestamp = 50.001
        )

        val merged = mergeRemoteUserMessageIntoCurrentMessages(
            currentMessages = listOf(localUser, pendingAssistant),
            content = """
                分析一下这张照片

                [media attached: /Users/example/photo.jpg (image/jpeg) | /Users/example/photo.jpg]
            """.trimIndent(),
            contentBlocks = emptyList(),
            runId = "run-1",
            sortTimestamp = 51.0
        )

        assertEquals(listOf("local-user-run-1", "run-1"), merged.map { it.runId })
        assertEquals("分析一下这张照片", merged.first().content)
        assertFalse(merged.any { it.content.contains("[media attached:") })
    }

    @Test
    fun mergesRemoteTextEchoIntoMatchingLocalImageUserBubble() {
        val imageBlock = RelayChatContentBlock(
            type = "image",
            fileId = "file-1",
            fileName = "photo.png",
            mimeType = "image/png",
            downloadUrl = "content://local/photo.png"
        )
        val localUser = ChatMessage(
            id = "local-user",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "20260608",
            contentBlocks = listOf(imageBlock),
            runId = "local-user-run-1",
            sortTimestamp = 50.0
        )
        val pendingAssistant = assistantMessage(
            id = "assistant-1",
            runId = "run-1",
            content = "正在连接...",
            sortTimestamp = 50.001
        )

        val merged = mergeRemoteUserMessageIntoCurrentMessages(
            currentMessages = listOf(localUser, pendingAssistant),
            content = "20260608",
            contentBlocks = emptyList(),
            runId = "run-1",
            sortTimestamp = 51.0
        )

        assertEquals(listOf("local-user-run-1", "run-1"), merged.map { it.runId })
        assertEquals(1, merged.count { it.role == MessageRole.user && it.content == "20260608" })
        assertEquals(listOf(imageBlock), merged.first().contentBlocks)
    }

    @Test
    fun appliesAssistantErrorToPendingAssistantMessage() {
        val localVoice = voiceMessage(runId = "local-user-client-run-1", sortTimestamp = 30.0)
        val pendingAssistant = assistantMessage(
            id = "assistant-1",
            runId = "client-run-1",
            content = "等待宿主机识别语音...",
            sortTimestamp = 30.001
        )

        val updated = applyAssistantErrorToCurrentMessages(
            currentMessages = listOf(localVoice, pendingAssistant),
            runId = "request-1",
            assistantMessageId = "assistant-1",
            errorMessage = "未安装语音输入技能",
            sortTimestamp = 31.0
        )

        assertEquals(2, updated.size)
        val assistant = updated.last()
        assertEquals(MessageState.failed, assistant.state)
        assertEquals("未安装语音输入技能", assistant.content)
        assertEquals("request-1", assistant.runId)
    }

    @Test
    fun ignoresProtocolTypingDeltaWhileVoiceTranscriptionPlaceholderIsVisible() {
        val pendingAssistant = assistantMessage(
            id = "assistant-1",
            runId = "voice-run-1",
            content = "等待宿主机识别语音...",
            sortTimestamp = 40.001
        )

        val updated = mergedAssistantStreamingDisplayContent(
            existing = pendingAssistant,
            delta = "[[clawlink:typing]][[clawlink:typing]]"
        )

        assertEquals("等待宿主机识别语音...", updated)
    }

    @Test
    fun replacesVoiceTranscriptionPlaceholderWhenRealAssistantDeltaArrives() {
        val pendingAssistant = assistantMessage(
            id = "assistant-1",
            runId = "voice-run-1",
            content = "Waiting for host transcription...",
            sortTimestamp = 41.001
        )

        val updated = mergedAssistantStreamingDisplayContent(
            existing = pendingAssistant,
            delta = "我听到了，正在处理。"
        )

        assertEquals("我听到了，正在处理。", updated)
    }

    @Test
    fun requestsHistorySyncWhenFinalArrivesWithoutRenderableAssistantText() {
        val pendingAssistant = assistantMessage(
            id = "assistant-1",
            runId = "run-1",
            content = "正在连接...",
            sortTimestamp = 50.001
        )

        assertTrue(
            shouldSyncAssistantFinalFromHistory(
                existing = pendingAssistant,
                finalText = "",
                finalContentBlocks = emptyList()
            )
        )
    }

    @Test
    fun requestsHistorySyncWhenFinalOnlyContainsEmptyTextBlock() {
        val pendingAssistant = assistantMessage(
            id = "assistant-1",
            runId = "run-1",
            content = "正在连接...",
            sortTimestamp = 50.001
        )

        assertTrue(
            shouldSyncAssistantFinalFromHistory(
                existing = pendingAssistant,
                finalText = "",
                finalContentBlocks = listOf(RelayChatContentBlock(type = "text", text = ""))
            )
        )
    }

    @Test
    fun completesLocallyWhenEmptyFinalHasRenderableStreamingText() {
        val pendingAssistant = assistantMessage(
            id = "assistant-1",
            runId = "run-1",
            content = "已经流式返回的答案",
            sortTimestamp = 51.001
        )

        assertFalse(
            shouldSyncAssistantFinalFromHistory(
                existing = pendingAssistant,
                finalText = "",
                finalContentBlocks = emptyList()
            )
        )
    }

    @Test
    fun mergesDuplicateCompletedAssistantFinalForSameRun() {
        val current = listOf(
            ChatMessage(
                id = "user-1",
                role = MessageRole.user,
                state = MessageState.completed,
                content = "Reply only OK",
                runId = "local-user-run-1",
                sortTimestamp = 60.0
            ),
            ChatMessage(
                id = "assistant-timeline",
                role = MessageRole.assistant,
                state = MessageState.completed,
                content = "OK",
                runId = "run-1",
                sortTimestamp = 60.001
            )
        )
        val legacyFinal = ChatMessage(
            id = "assistant-legacy-final",
            role = MessageRole.assistant,
            state = MessageState.completed,
            content = "OK",
            runId = "run-1",
            sortTimestamp = 62.0
        )

        val merged = mergeCompletedAssistantFinalIntoCurrentMessages(current, legacyFinal)

        requireNotNull(merged)
        assertEquals(listOf("user-1", "assistant-timeline"), merged.map { it.id })
        assertEquals(1, merged.count { it.role == MessageRole.assistant && it.content == "OK" })
        assertEquals(60.001, merged.last().sortTimestamp ?: -1.0, 0.0001)
    }

    @Test
    fun mergesLegacyFinalIntoSameTurnStreamingAssistantTextWhenRunIdIsMissing() {
        val current = listOf(
            ChatMessage(
                id = "user-1",
                role = MessageRole.user,
                state = MessageState.completed,
                content = "Reply only OK",
                runId = "local-user-run-1",
                sortTimestamp = 60.0
            ),
            ChatMessage(
                id = "assistant-streaming",
                role = MessageRole.assistant,
                state = MessageState.streaming,
                content = "OK",
                runId = "timeline-run-1",
                sortTimestamp = 60.001
            )
        )
        val legacyFinal = ChatMessage(
            id = "assistant-legacy-final",
            role = MessageRole.assistant,
            state = MessageState.completed,
            content = "OK",
            runId = "",
            sortTimestamp = 62.0
        )

        val merged = mergeCompletedAssistantFinalIntoCurrentMessages(current, legacyFinal)

        requireNotNull(merged)
        assertEquals(listOf("user-1", "assistant-streaming"), merged.map { it.id })
        assertEquals(listOf(MessageState.completed), merged.filter { it.role == MessageRole.assistant }.map { it.state })
        assertEquals(1, merged.count { it.role == MessageRole.assistant && it.content == "OK" })
    }

    @Test
    fun removesTransientPlaceholderWhenSameRunFails() {
        val current = listOf(
            ChatMessage(
                id = "user-1",
                role = MessageRole.user,
                state = MessageState.completed,
                content = "trigger failure",
                runId = "local-user-run-1",
                sortTimestamp = 70.0
            ),
            ChatMessage(
                id = "assistant-placeholder",
                role = MessageRole.assistant,
                state = MessageState.streaming,
                content = protocolTypingMarkerText,
                runId = "run-1",
                sortTimestamp = 70.001
            ),
            ChatMessage(
                id = "assistant-error",
                role = MessageRole.assistant,
                state = MessageState.failed,
                content = "API call failed after 3 retries: HTTP 429",
                runId = "run-1",
                sortTimestamp = 71.0
            )
        )

        val resolved = removeResolvedTransientAssistantPlaceholders(current)

        assertEquals(listOf("user-1", "assistant-error"), resolved.map { it.id })
        assertFalse(resolved.any { it.state == MessageState.streaming })
    }

    @Test
    fun removesTransientPlaceholderWhenSameTurnCompletesWithDifferentRun() {
        val current = listOf(
            ChatMessage(
                id = "user-1",
                role = MessageRole.user,
                state = MessageState.completed,
                content = "reply OK831",
                runId = "local-user-client-run",
                sortTimestamp = 80.0
            ),
            ChatMessage(
                id = "assistant-placeholder",
                role = MessageRole.assistant,
                state = MessageState.streaming,
                content = protocolTypingMarkerText,
                runId = "client-run",
                sortTimestamp = 80.001
            ),
            ChatMessage(
                id = "assistant-server-final",
                role = MessageRole.assistant,
                state = MessageState.completed,
                content = "OK831",
                runId = "server-run",
                sortTimestamp = 81.0
            )
        )

        val resolved = removeResolvedTransientAssistantPlaceholders(current)

        assertEquals(listOf("user-1", "assistant-server-final"), resolved.map { it.id })
        assertFalse(resolved.any { it.state == MessageState.streaming })
    }

    @Test
    fun doesNotMergeSameAssistantTextAcrossDifferentRuns() {
        val current = listOf(
            ChatMessage(
                id = "assistant-old",
                role = MessageRole.assistant,
                state = MessageState.completed,
                content = "OK",
                runId = "run-old"
            )
        )
        val nextFinal = ChatMessage(
            id = "assistant-new",
            role = MessageRole.assistant,
            state = MessageState.completed,
            content = "OK",
            runId = "run-new"
        )

        assertEquals(null, mergeCompletedAssistantFinalIntoCurrentMessages(current, nextFinal))
    }

    @Test
    fun removesResolvedTransientPlaceholderWhenCompletedAssistantExistsForSameRun() {
        val messages = listOf(
            ChatMessage(
                id = "user-1",
                role = MessageRole.user,
                state = MessageState.completed,
                content = "Reply only OK",
                runId = "local-user-run-1"
            ),
            ChatMessage(
                id = "assistant-local",
                role = MessageRole.assistant,
                state = MessageState.streaming,
                content = "正在连接...",
                runId = "run-1"
            ),
            ChatMessage(
                id = "assistant-final",
                role = MessageRole.assistant,
                state = MessageState.completed,
                content = "OK",
                runId = "run-1"
            )
        )

        val resolved = removeResolvedTransientAssistantPlaceholders(messages)

        assertEquals(listOf("user-1", "assistant-final"), resolved.map { it.id })
    }

    @Test
    fun removesDuplicateCompletedAssistantRepliesWithinSameUserTurn() {
        val messages = listOf(
            ChatMessage(
                id = "user-1",
                role = MessageRole.user,
                state = MessageState.completed,
                content = "garbled marker802"
            ),
            ChatMessage(
                id = "assistant-timeline",
                role = MessageRole.assistant,
                state = MessageState.completed,
                content = "Please send that again.",
                runId = "timeline-run"
            ),
            ChatMessage(
                id = "assistant-legacy",
                role = MessageRole.assistant,
                state = MessageState.completed,
                content = "Please send that again.",
                runId = "legacy-run"
            ),
            ChatMessage(
                id = "user-2",
                role = MessageRole.user,
                state = MessageState.completed,
                content = "same answer is valid for a new turn"
            ),
            ChatMessage(
                id = "assistant-new-turn",
                role = MessageRole.assistant,
                state = MessageState.completed,
                content = "Please send that again.",
                runId = "new-turn-run"
            )
        )

        val deduped = removeDuplicateCompletedAssistantRepliesInSameTurn(messages)

        assertEquals(
            listOf("user-1", "assistant-timeline", "user-2", "assistant-new-turn"),
            deduped.map { it.id }
        )
    }

    @Test
    fun movesDuplicateCompletedAssistantReplyAfterInterleavedToolMessage() {
        val messages = listOf(
            ChatMessage(
                id = "user-1",
                role = MessageRole.user,
                state = MessageState.completed,
                content = "search then answer"
            ),
            ChatMessage(
                id = "assistant-early",
                role = MessageRole.assistant,
                state = MessageState.completed,
                content = "Final answer",
                runId = "timeline-run"
            ),
            ChatMessage(
                id = "tool-1",
                role = MessageRole.tool,
                state = MessageState.completed,
                content = "Tool result",
                contentBlocks = listOf(RelayChatContentBlock(type = "tool_result", text = "Tool result", name = "web_search", toolCallId = "tool-1")),
                runId = "tool-1"
            ),
            ChatMessage(
                id = "assistant-late",
                role = MessageRole.assistant,
                state = MessageState.completed,
                content = "Final answer",
                runId = "history-run"
            )
        )

        val deduped = removeDuplicateCompletedAssistantRepliesInSameTurn(messages)

        assertEquals(
            listOf("user-1", "tool-1", "assistant-late"),
            deduped.map { it.id }
        )
    }

    @Test
    fun hiddenUserMessageDoesNotSplitDuplicateAssistantReplies() {
        val messages = listOf(
            ChatMessage(
                id = "user-1",
                role = MessageRole.user,
                state = MessageState.completed,
                content = "reply only OK"
            ),
            ChatMessage(
                id = "assistant-timeline",
                role = MessageRole.assistant,
                state = MessageState.completed,
                content = "OK805",
                runId = "timeline-run"
            ),
            ChatMessage(
                id = "hidden-user",
                role = MessageRole.user,
                state = MessageState.completed,
                content = ""
            ),
            ChatMessage(
                id = "assistant-legacy",
                role = MessageRole.assistant,
                state = MessageState.completed,
                content = "OK805",
                runId = "legacy-run"
            )
        )

        val deduped = removeDuplicateCompletedAssistantRepliesInSameTurn(messages)

        assertEquals(
            listOf("user-1", "assistant-timeline", "hidden-user"),
            deduped.map { it.id }
        )
    }

    private fun voiceMessage(
        runId: String,
        sortTimestamp: Double,
        transcript: String? = null
    ): ChatMessage {
        return ChatMessage(
            id = runId,
            role = MessageRole.user,
            content = "voice-input.m4a",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "voice",
                    fileName = "voice-input.m4a",
                    mimeType = "audio/mp4",
                    downloadUrl = "file:///tmp/voice-input.m4a",
                    transcript = transcript
                )
            ),
            runId = runId,
            sortTimestamp = sortTimestamp
        )
    }

    private fun assistantMessage(
        id: String,
        runId: String,
        content: String,
        sortTimestamp: Double
    ): ChatMessage {
        return ChatMessage(
            id = id,
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = content,
            runId = runId,
            sortTimestamp = sortTimestamp
        )
    }
}
