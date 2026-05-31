package com.rethinkingstudio.clawlink.ui.screens.chat.components

import android.media.MediaPlayer
import android.text.method.LinkMovementMethod
import android.net.Uri
import android.widget.Toast
import android.widget.TextView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
import com.rethinkingstudio.clawlink.core.state.chat.RemoteAttachmentCache
import com.rethinkingstudio.clawlink.core.state.chat.chatAttachmentCacheKey
import com.rethinkingstudio.clawlink.core.state.chat.chatImageCacheKey
import com.rethinkingstudio.clawlink.ui.screens.chat.ChatColors
import com.rethinkingstudio.clawlink.ui.screens.chat.formatChatTimestamp
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun MessageBubble(
    message: ChatMessage,
    showInvocationProcess: Boolean,
    relayBaseUrl: String,
    accessToken: String,
    readVoicePlaybackIdentifiers: Set<String> = emptySet(),
    onVoicePlaybackStart: (identifier: String) -> Unit = {},
    gatewayId: String? = null,
    sessionKey: String? = null,
    onImageClick: (block: RelayChatContentBlock, url: String, fileName: String?) -> Unit = { _, _, _ -> },
    onFileClick: (block: RelayChatContentBlock, url: String, fileName: String?) -> Unit = { _, _, _ -> }
) {
    val isUser = message.role == MessageRole.user
    val isTool = message.role == MessageRole.tool || message.hasToolContent
    val visibleToolBlocks = message.visibleToolContentBlocks(showInvocationProcess)
    if (isTool && !message.shouldDisplayInChat(showInvocationProcess = showInvocationProcess)) return
    val syntheticFileBlocks = if (!isTool && message.fileContentBlocks.isEmpty()) {
        parseSendFileOutputBlocks(message.plainTextContent)
    } else emptyList()
    val fileBlocks = message.fileContentBlocks + syntheticFileBlocks
    val voiceBlocks = message.voiceContentBlocks
    val rawDisplayText = if (syntheticFileBlocks.isNotEmpty()) "" else message.plainTextContent
    val displayText = if (fileBlocks.isNotEmpty() || voiceBlocks.isNotEmpty()) {
        val trimmed = rawDisplayText.trim()
        val shouldSuppressFileText = fileBlocks.any { block ->
            val name = block.fileDisplayName?.trim().orEmpty()
            val status = block.fileStatusText?.trim().orEmpty()
            (name.isNotEmpty() && trimmed == name) || (status.isNotEmpty() && trimmed == status)
        }
        val shouldSuppressVoiceText = voiceBlocks.any { block ->
            val name = block.fileDisplayName?.trim().orEmpty()
            val status = block.voiceStatusText?.trim().orEmpty()
            val transcript = block.voiceTranscriptText?.trim().orEmpty()
            (name.isNotEmpty() && trimmed == name) ||
                (status.isNotEmpty() && trimmed == status) ||
                (transcript.isNotEmpty() && trimmed == transcript)
        } || (message.role == MessageRole.assistant && voiceBlocks.isNotEmpty())
        if (shouldSuppressFileText || shouldSuppressVoiceText) "" else rawDisplayText
    } else rawDisplayText
    val isStandaloneFileMessage = !isTool && displayText.isBlank() && fileBlocks.isNotEmpty() && voiceBlocks.isEmpty()
    val isStandaloneVoiceMessage = !isTool && displayText.isBlank() && voiceBlocks.isNotEmpty() && fileBlocks.isEmpty()

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        if (isTool) {
            ToolMessageCard(message = message, visibleToolBlocks = visibleToolBlocks, showInvocationProcess = showInvocationProcess, modifier = Modifier.padding(vertical = 2.dp))
            return@Column
        }
        if (isStandaloneVoiceMessage) {
            StandaloneVoiceMessage(
                blocks = voiceBlocks,
                isUser = isUser,
                createdAt = message.createdAt,
                relayBaseUrl = relayBaseUrl,
                accessToken = accessToken,
                readVoicePlaybackIdentifiers = readVoicePlaybackIdentifiers,
                onVoicePlaybackStart = onVoicePlaybackStart,
                gatewayId = gatewayId,
                sessionKey = sessionKey
            )
            return@Column
        }
        if (isStandaloneFileMessage) {
            StandaloneFileMessage(blocks = fileBlocks, isUser = isUser, messageState = message.state, createdAt = message.createdAt, relayBaseUrl = relayBaseUrl, accessToken = accessToken, onImageClick = onImageClick, onFileClick = onFileClick)
            return@Column
        }
        if (shouldShowStreamingWaitState(message.role, message.state) &&
            shouldUseStandaloneStreamingIndicator(displayText, fileBlocks.isNotEmpty(), voiceBlocks.isNotEmpty())
        ) {
            StreamingIndicatorBubble()
            return@Column
        }
        Surface(
            color = if (isUser) ChatColors.userBubble else MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            shape = RoundedCornerShape(28.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (isUser) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
            tonalElevation = 0.dp, shadowElevation = 0.dp,
            modifier = Modifier.widthIn(max = 326.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                voiceBlocks.forEach {
                    VoiceBlock(
                        it,
                        isUser,
                        relayBaseUrl = relayBaseUrl,
                        accessToken = accessToken,
                        readVoicePlaybackIdentifiers = readVoicePlaybackIdentifiers,
                        onVoicePlaybackStart = onVoicePlaybackStart,
                        gatewayId = gatewayId,
                        sessionKey = sessionKey
                    )
                }
                fileBlocks.forEach { FileBlock(it, isUser, message.state, relayBaseUrl = relayBaseUrl, accessToken = accessToken, onImageClick = onImageClick, onFileClick = onFileClick) }
                if (displayText.isNotEmpty()) {
                    MarkdownMessageText(text = displayText, textColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, linkColor = if (isUser) Color.White else MaterialTheme.colorScheme.primary, textSizeSp = 13f, onDarkBackground = isUser)
                }
                if (shouldShowInlineStreamingIndicator(message.role, message.state, displayText, fileBlocks.isNotEmpty(), voiceBlocks.isNotEmpty())) {
                    InlineStreamingIndicator()
                }
                if (shouldShowMessageFooter(message.role, message.state, displayText, fileBlocks.isNotEmpty(), voiceBlocks.isNotEmpty(), isTool)) {
                    MessageFooter(title = if (isUser) "You" else "ClawLink", createdAt = message.createdAt, isUser = isUser)
                }
            }
        }
    }
}

private val protocolTypingMarkerDisplayRegex = Regex("^(?:\\[\\[clawlink:typing]]\\s*)+$")

private fun isProtocolTypingMarkerDisplayText(text: String): Boolean {
    val trimmed = text.trim()
    return trimmed.isNotEmpty() && protocolTypingMarkerDisplayRegex.matches(trimmed)
}

internal fun isStreamingIndicatorDisplayText(text: String): Boolean {
    val trimmed = text.trim()
    return trimmed.isBlank() ||
        trimmed.startsWith("正在连接") ||
        trimmed.startsWith("连接中") ||
        trimmed.startsWith("Connecting") ||
        trimmed.startsWith("连接中断") ||
        trimmed.startsWith("Connection interrupted") ||
        trimmed == "正在同步回复..." ||
        trimmed == "Syncing reply..." ||
        trimmed == "正在同步最终内容..." ||
        trimmed == "Syncing final content..." ||
        trimmed == "已完成，但未返回文本。" ||
        trimmed == "Completed, but no text was returned." ||
        trimmed.startsWith("等待宿主机识别语音") ||
        trimmed.startsWith("Waiting for host transcription") ||
        isProtocolTypingMarkerDisplayText(trimmed)
}

internal fun shouldShowStreamingWaitState(role: MessageRole, state: MessageState): Boolean =
    role == MessageRole.assistant && state == MessageState.streaming

internal fun shouldUseStandaloneStreamingIndicator(
    displayText: String,
    hasFileBlocks: Boolean,
    hasVoiceBlocks: Boolean
): Boolean =
    !hasFileBlocks && !hasVoiceBlocks

internal fun shouldShowInlineStreamingIndicator(
    role: MessageRole,
    state: MessageState,
    displayText: String,
    hasFileBlocks: Boolean,
    hasVoiceBlocks: Boolean
): Boolean =
    shouldShowStreamingWaitState(role, state) &&
        !hasFileBlocks &&
        !hasVoiceBlocks &&
        !shouldUseStandaloneStreamingIndicator(displayText, hasFileBlocks, hasVoiceBlocks)

internal fun shouldShowMessageFooter(
    role: MessageRole,
    state: MessageState,
    displayText: String,
    hasFileBlocks: Boolean,
    hasVoiceBlocks: Boolean,
    isToolMessage: Boolean
): Boolean =
    !isToolMessage &&
        !(shouldShowStreamingWaitState(role, state) &&
            shouldUseStandaloneStreamingIndicator(displayText, hasFileBlocks, hasVoiceBlocks))

@Composable
internal fun MessageFooter(title: String, createdAt: String, isUser: Boolean, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val footerColor = if (isUser) Color.White.copy(alpha = 0.72f) else ChatColors.secondaryText
        Text(title, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = footerColor, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        Text(formatChatTimestamp(createdAt), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = footerColor, fontWeight = FontWeight.Medium)
    }
}
