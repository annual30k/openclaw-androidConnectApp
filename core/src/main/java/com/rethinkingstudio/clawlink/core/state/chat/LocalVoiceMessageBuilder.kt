package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import com.rethinkingstudio.clawlink.core.network.transport.VoiceSendAudioPayload
import java.io.File
import java.util.Base64

internal fun buildLocalVoiceUserMessage(
    audio: VoiceSendAudioPayload,
    gatewayId: String,
    sessionKey: String,
    clientRunId: String,
    sortTimestamp: Double
): ChatMessage {
    val userMessageId = "user-$clientRunId"
    val localFileUrl = writeLocalVoicePlaybackCopy(audio)
    val contentBlock = RelayChatContentBlock(
        type = "voice",
        fileName = audio.fileName,
        mimeType = audio.mimeType,
        sizeBytes = audio.sizeBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        downloadUrl = localFileUrl,
        sourceRunId = clientRunId,
        gatewayId = gatewayId,
        sessionKey = sessionKey
    )
    return ChatMessage(
        id = userMessageId,
        role = MessageRole.user,
        state = MessageState.completed,
        content = audio.fileName,
        contentBlocks = listOf(contentBlock),
        createdAt = "",
        runId = "local-user-$clientRunId",
        sortTimestamp = sortTimestamp,
        timelineOrderKey = localTimelineOrderKey(clientRunId, 10, userMessageId),
        timelineIdentityKey = localTimelineIdentityKey("message:user", clientRunId),
        timelineItemKind = "message:user"
    )
}

private fun writeLocalVoicePlaybackCopy(audio: VoiceSendAudioPayload): String {
    val suffix = audio.fileName
        .substringAfterLast('.', missingDelimiterValue = "m4a")
        .takeIf { it.isNotBlank() }
        ?.let { ".$it" }
        ?: ".m4a"
    val file = File.createTempFile("clawlink-user-voice-", suffix)
    file.writeBytes(Base64.getDecoder().decode(audio.contentBase64))
    return "file://${file.absolutePath}"
}
