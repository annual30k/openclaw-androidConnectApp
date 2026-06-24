package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import com.rethinkingstudio.clawlink.core.network.transport.VoiceSendAudioPayload
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose

internal data class LocalOutgoingRunDraft(
    val clientRunId: String,
    val userMessage: ChatMessage,
    val assistantMessage: ChatMessage,
    val runScope: ChatRunScope,
    val messages: List<ChatMessage>
)

internal fun buildLocalTextOutgoingRun(
    currentMessages: List<ChatMessage>,
    content: String,
    gatewayId: String,
    sessionKey: String,
    clientRunId: String,
    attachmentIds: List<String>,
    attachmentBlocks: List<RelayChatContentBlock>
): LocalOutgoingRunDraft {
    val attachmentIdSet = attachmentIds
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()
    val replacedUploadSortTimestamp = if (attachmentIdSet.isEmpty()) {
        null
    } else {
        currentMessages
            .filter { message ->
                message.id in attachmentIdSet || attachmentIdSet.any { attachmentId -> message.runId == "upload-$attachmentId" }
            }
            .mapNotNull { it.sortTimestamp }
            .minOrNull()
    }
    val baseMessages = if (attachmentIdSet.isEmpty()) {
        currentMessages
    } else {
        currentMessages.filterNot { message ->
            message.id in attachmentIdSet || attachmentIdSet.any { attachmentId -> message.runId == "upload-$attachmentId" }
        }
    }
    val userSortTimestamp = replacedUploadSortTimestamp ?: (System.currentTimeMillis() / 1000.0)
    val userMessage = ChatMessage(
        id = "user-$clientRunId",
        role = MessageRole.user,
        state = MessageState.completed,
        content = content.trim().takeIf { it.isNotEmpty() && it != " " } ?: "",
        contentBlocks = attachmentBlocks,
        createdAt = "",
        runId = "local-user-$clientRunId",
        sortTimestamp = userSortTimestamp
    )
    val assistantMessageId = "assistant-$clientRunId"
    val assistantMessage = buildLocalTextAssistantPlaceholderMessage(
        id = assistantMessageId,
        clientRunId = clientRunId,
        sortTimestamp = userSortTimestamp + 0.001
    )
    return LocalOutgoingRunDraft(
        clientRunId = clientRunId,
        userMessage = userMessage,
        assistantMessage = assistantMessage,
        runScope = ChatRunScope(
            gatewayId = gatewayId,
            sessionKey = sessionKey,
            assistantMessageId = assistantMessageId,
            triggeringUserMessageId = userMessage.id
        ),
        messages = baseMessages + userMessage + assistantMessage
    )
}

internal fun buildLocalVoiceOutgoingRun(
    currentMessages: List<ChatMessage>,
    gatewayId: String,
    sessionKey: String,
    clientRunId: String,
    audio: VoiceSendAudioPayload
): LocalOutgoingRunDraft {
    val now = System.currentTimeMillis() / 1000.0
    val userMessage = buildLocalVoiceUserMessage(
        audio = audio,
        gatewayId = gatewayId,
        sessionKey = sessionKey,
        clientRunId = clientRunId,
        sortTimestamp = now
    )
    val assistantMessageId = "assistant-$clientRunId"
    val assistantMessage = ChatMessage(
        id = assistantMessageId,
        role = MessageRole.assistant,
        state = MessageState.streaming,
        content = choose("Waiting for host transcription...", "等待宿主机识别语音..."),
        createdAt = "",
        runId = clientRunId,
        sortTimestamp = now + 0.001
    )
    return LocalOutgoingRunDraft(
        clientRunId = clientRunId,
        userMessage = userMessage,
        assistantMessage = assistantMessage,
        runScope = ChatRunScope(
            gatewayId = gatewayId,
            sessionKey = sessionKey,
            assistantMessageId = assistantMessageId,
            triggeringUserMessageId = userMessage.id
        ),
        messages = currentMessages + userMessage + assistantMessage
    )
}
