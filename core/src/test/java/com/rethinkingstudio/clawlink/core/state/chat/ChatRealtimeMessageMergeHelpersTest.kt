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

    private fun voiceMessage(runId: String, sortTimestamp: Double): ChatMessage {
        return ChatMessage(
            id = runId,
            role = MessageRole.user,
            content = "voice-input.m4a",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "voice",
                    fileName = "voice-input.m4a",
                    mimeType = "audio/mp4",
                    downloadUrl = "file:///tmp/voice-input.m4a"
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
