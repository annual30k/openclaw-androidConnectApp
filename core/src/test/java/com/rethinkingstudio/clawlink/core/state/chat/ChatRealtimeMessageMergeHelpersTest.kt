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
    fun keepsRunlessRemoteVoiceTranscriptSeparateFromLocalVoiceBubble() {
        val localVoice = voiceMessage(runId = "local-user-voice-run-fallback", sortTimestamp = 100.0)

        val merged = mergeRemoteUserMessageIntoCurrentMessages(
            currentMessages = listOf(localVoice),
            content = "你可以做什么",
            contentBlocks = emptyList(),
            runId = "",
            sortTimestamp = 101.0
        )

        assertEquals(2, merged.size)
        assertEquals("local-user-voice-run-fallback", merged.first().runId)
        assertEquals(null, merged.first().voiceTranscriptText)
        assertTrue(merged.any { it.role == MessageRole.user && !it.hasVoiceContent && it.content == "你可以做什么" })
    }

    @Test
    fun keepsRunlessDuplicateRemoteVoiceTranscriptSeparateFromLocalVoiceBubble() {
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

        assertEquals(2, merged.size)
        assertEquals("local-user-voice-run-duplicate", merged.first().runId)
        assertEquals(transcript, merged.first().voiceTranscriptText)
        assertTrue(merged.any { it.role == MessageRole.user && !it.hasVoiceContent && it.content == transcript })
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
    fun mergesUserPrefixedRemoteMediaEchoIntoMatchingLocalUserBubble() {
        val localUser = ChatMessage(
            id = "local-user",
            role = MessageRole.user,
            content = "分析一下这张图",
            runId = "local-user-client-run-image",
            sortTimestamp = 50.0
        )
        val pendingAssistant = assistantMessage(
            id = "assistant-1",
            runId = "client-run-image",
            content = "正在连接...",
            sortTimestamp = 50.001
        )

        val merged = mergeRemoteUserMessageIntoCurrentMessages(
            currentMessages = listOf(localUser, pendingAssistant),
            content = "分析一下这张图",
            contentBlocks = emptyList(),
            runId = "user-client-run-image",
            sortTimestamp = 51.0
        )

        assertEquals(listOf(MessageRole.user, MessageRole.assistant), merged.map { it.role })
        assertEquals(listOf("local-user-client-run-image", "client-run-image"), merged.map { it.runId })
        assertEquals(1, merged.count { it.role == MessageRole.user })
    }

    @Test
    fun mergesRunlessRemoteMediaEchoByContentBlockSourceRunId() {
        val runId = "client-run-image-source-only"
        val localUser = ChatMessage(
            id = "local-user",
            role = MessageRole.user,
            content = "分析一下这张图",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    fileId = "local-dinner",
                    fileName = "dinner.png",
                    mimeType = "image/png",
                    downloadUrl = "file:///tmp/dinner.png",
                    sourceRunId = runId
                )
            ),
            runId = "local-user-$runId",
            sortTimestamp = 50.0
        )
        val completedAssistant = ChatMessage(
            id = "assistant-1",
            role = MessageRole.assistant,
            runId = runId,
            content = "已经完成",
            state = MessageState.completed,
            sortTimestamp = 50.001
        )

        val merged = mergeRemoteUserMessageIntoCurrentMessages(
            currentMessages = listOf(localUser, completedAssistant),
            content = "分析一下这张图",
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = "分析一下这张图", sourceRunId = runId)),
            runId = "",
            sortTimestamp = 51.0
        )

        assertEquals(listOf(MessageRole.user, MessageRole.assistant), merged.map { it.role })
        assertEquals(1, merged.count { it.role == MessageRole.user })
        assertEquals(listOf("local-dinner"), merged.first().contentBlocks.mapNotNull { it.fileId })
    }

    @Test
    fun mergesRemoteAttachmentEchoIntoMatchingUploadPlaceholder() {
        val localPlaceholder = ChatMessage(
            id = "attachment-1",
            role = MessageRole.user,
            state = MessageState.streaming,
            content = "photo.png",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    attachmentId = "attachment-1",
                    fileName = "photo.png",
                    mimeType = "image/png",
                    downloadUrl = "file:///tmp/photo.png",
                    sourceRunId = "client-run-attachment-1"
                )
            ),
            runId = "upload-attachment-1",
            sortTimestamp = 60.0
        )

        val merged = mergeRemoteUserMessageIntoCurrentMessages(
            currentMessages = listOf(localPlaceholder),
            content = "photo.png",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    attachmentId = "attachment-1",
                    fileId = "file-photo-1",
                    fileName = "photo.png",
                    mimeType = "image/png",
                    downloadUrl = "/api/mobile/files/file-photo-1",
                    sourceRunId = "client-run-attachment-1"
                )
            ),
            runId = "client-run-attachment-1",
            sortTimestamp = 61.0
        )

        assertEquals(1, merged.size)
        val message = merged.single()
        assertEquals("attachment-1", message.id)
        assertEquals(MessageState.completed, message.state)
        assertEquals("file-photo-1", message.fileContentBlocks.single().fileId)
        assertEquals("file:///tmp/photo.png", message.fileContentBlocks.single().downloadUrl)
    }

    @Test
    fun mergesRemoteAttachmentEchoWithoutAttachmentIdIntoSingleMatchingUploadPlaceholder() {
        val localPlaceholder = ChatMessage(
            id = "attachment-1",
            role = MessageRole.user,
            state = MessageState.streaming,
            content = "photo.png",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    attachmentId = "attachment-1",
                    fileName = "photo.png",
                    mimeType = "image/png",
                    downloadUrl = "file:///tmp/photo.png",
                    sourceRunId = "client-run-attachment-1"
                )
            ),
            runId = "upload-attachment-1",
            sortTimestamp = 60.0
        )

        val merged = mergeRemoteUserMessageIntoCurrentMessages(
            currentMessages = listOf(localPlaceholder),
            content = "photo.png",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    fileId = "file-photo-1",
                    fileName = "photo.png",
                    mimeType = "image/png",
                    downloadUrl = "/api/mobile/files/file-photo-1",
                    sourceRunId = "client-run-attachment-1"
                )
            ),
            runId = "client-run-attachment-1",
            sortTimestamp = 61.0
        )

        assertEquals(1, merged.size)
        val message = merged.single()
        assertEquals("attachment-1", message.id)
        assertEquals(MessageState.completed, message.state)
        assertEquals("file-photo-1", message.fileContentBlocks.single().fileId)
        assertEquals("file:///tmp/photo.png", message.fileContentBlocks.single().downloadUrl)
    }

    @Test
    fun doesNotMergeRemoteAttachmentEchoWithoutAttachmentIdWhenMultipleUploadPlaceholdersShareSourceRunId() {
        val localPlaceholderA = ChatMessage(
            id = "attachment-1",
            role = MessageRole.user,
            state = MessageState.streaming,
            content = "photo-a.png",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    attachmentId = "attachment-1",
                    fileName = "photo-a.png",
                    mimeType = "image/png",
                    downloadUrl = "file:///tmp/photo-a.png",
                    sourceRunId = "client-run-shared"
                )
            ),
            runId = "upload-attachment-1",
            sortTimestamp = 60.0
        )
        val localPlaceholderB = ChatMessage(
            id = "attachment-2",
            role = MessageRole.user,
            state = MessageState.streaming,
            content = "photo-b.png",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    attachmentId = "attachment-2",
                    fileName = "photo-b.png",
                    mimeType = "image/png",
                    downloadUrl = "file:///tmp/photo-b.png",
                    sourceRunId = "client-run-shared"
                )
            ),
            runId = "upload-attachment-2",
            sortTimestamp = 60.001
        )

        val merged = mergeRemoteUserMessageIntoCurrentMessages(
            currentMessages = listOf(localPlaceholderA, localPlaceholderB),
            content = "photo-a.png",
            contentBlocks = listOf(
                RelayChatContentBlock(
                    type = "image",
                    fileId = "file-photo-1",
                    fileName = "photo-a.png",
                    mimeType = "image/png",
                    downloadUrl = "/api/mobile/files/file-photo-1",
                    sourceRunId = "client-run-shared"
                )
            ),
            runId = "client-run-shared",
            sortTimestamp = 61.0
        )

        assertEquals(3, merged.size)
        assertEquals(listOf("attachment-1", "attachment-2"), merged.take(2).map { it.id })
        assertEquals(3, merged.count { it.role == MessageRole.user })
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
    fun requestsHistorySyncWhenFinalOnlyContainsTypingMarkerText() {
        val pendingAssistant = assistantMessage(
            id = "assistant-1",
            runId = "run-1",
            content = "正在连接...",
            sortTimestamp = 50.001
        )

        assertTrue(
            shouldSyncAssistantFinalFromHistory(
                existing = pendingAssistant,
                finalText = protocolTypingMarkerText,
                finalContentBlocks = emptyList()
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
    fun doesNotMergeLegacyFinalIntoStreamingAssistantTextWhenRunIdIsMissing() {
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

        assertEquals(null, merged)
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
    fun removesResolvedStreamingAssistantWhenSameRunTerminalAssistantExists() {
        val messages = listOf(
            ChatMessage(
                id = "user-1",
                role = MessageRole.user,
                state = MessageState.completed,
                content = "write a short reply",
                runId = "local-user-run-1",
                sortTimestamp = 90.0
            ),
            ChatMessage(
                id = "assistant-streaming",
                role = MessageRole.assistant,
                state = MessageState.streaming,
                content = "Short",
                runId = "run-1",
                sortTimestamp = 90.001
            ),
            ChatMessage(
                id = "assistant-final",
                role = MessageRole.assistant,
                state = MessageState.completed,
                content = "Short final reply",
                runId = "run-1",
                sortTimestamp = 91.0
            )
        )

        val resolved = removeResolvedTransientAssistantPlaceholders(messages)

        assertEquals(listOf("user-1", "assistant-final"), resolved.map { it.id })
    }

    @Test
    fun keepsRunlessStreamingAssistantWhenOnlySameTurnTextMatches() {
        val messages = listOf(
            ChatMessage(
                id = "user-1",
                role = MessageRole.user,
                state = MessageState.completed,
                content = "reply OK",
                sortTimestamp = 100.0
            ),
            ChatMessage(
                id = "assistant-streaming",
                role = MessageRole.assistant,
                state = MessageState.streaming,
                content = "OK",
                runId = "",
                sortTimestamp = 100.001
            ),
            ChatMessage(
                id = "assistant-final",
                role = MessageRole.assistant,
                state = MessageState.completed,
                content = "OK",
                runId = "server-run",
                sortTimestamp = 101.0
            )
        )

        val resolved = removeResolvedTransientAssistantPlaceholders(messages)

        assertEquals(listOf("user-1", "assistant-streaming", "assistant-final"), resolved.map { it.id })
    }

    @Test
    fun keepsSameAssistantTextInDifferentUserTurns() {
        val messages = listOf(
            ChatMessage(
                id = "user-1",
                role = MessageRole.user,
                state = MessageState.completed,
                content = "first",
                sortTimestamp = 110.0
            ),
            ChatMessage(
                id = "assistant-streaming",
                role = MessageRole.assistant,
                state = MessageState.streaming,
                content = "OK",
                runId = "",
                sortTimestamp = 110.001
            ),
            ChatMessage(
                id = "user-2",
                role = MessageRole.user,
                state = MessageState.completed,
                content = "second",
                sortTimestamp = 111.0
            ),
            ChatMessage(
                id = "assistant-final",
                role = MessageRole.assistant,
                state = MessageState.completed,
                content = "OK",
                runId = "server-run",
                sortTimestamp = 112.0
            )
        )

        val resolved = removeResolvedTransientAssistantPlaceholders(messages)

        assertEquals(
            listOf("user-1", "assistant-streaming", "user-2", "assistant-final"),
            resolved.map { it.id }
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
