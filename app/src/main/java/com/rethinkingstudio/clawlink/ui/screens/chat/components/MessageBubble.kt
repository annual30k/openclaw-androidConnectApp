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
import androidx.compose.material3.CircularProgressIndicator
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
    isVoiceReplyTextOnly: Boolean = false,
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
        if (!isUser && isVoiceReplyTextOnly && fileBlocks.isEmpty() && voiceBlocks.isEmpty()) {
            LoadingVoiceMessage(createdAt = message.createdAt)
            return@Column
        }
        if (isStandaloneFileMessage) {
            StandaloneFileMessage(blocks = fileBlocks, isUser = isUser, messageState = message.state, createdAt = message.createdAt, relayBaseUrl = relayBaseUrl, accessToken = accessToken, onImageClick = onImageClick, onFileClick = onFileClick)
            return@Column
        }
        if (!isUser && message.state == MessageState.streaming && (
            displayText.isBlank() || displayText.startsWith("正在连接") || displayText.startsWith("连接中断") ||
            displayText == "正在同步回复..." || displayText == "正在同步最终内容..." || displayText == "已完成，但未返回文本。"
        ) && fileBlocks.isEmpty() && voiceBlocks.isEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StreamingIndicatorBubble()
                Text("ClawLink", modifier = Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = ChatColors.secondaryText, fontWeight = FontWeight.Medium)
            }
            return@Column
        }
        Surface(
            color = if (isUser) ChatColors.userBubble else Color.White.copy(alpha = 0.96f),
            shape = RoundedCornerShape(28.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (isUser) Color.White.copy(alpha = 0.08f) else Color(0xFFE1E4EA)),
            tonalElevation = 0.dp, shadowElevation = 0.dp,
            modifier = Modifier.widthIn(max = 326.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (displayText.isNotEmpty()) {
                    MarkdownMessageText(text = displayText, textColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, linkColor = if (isUser) Color.White else MaterialTheme.colorScheme.primary, textSizeSp = 13f, onDarkBackground = isUser)
                }
                fileBlocks.forEach { FileBlock(it, isUser, message.state, relayBaseUrl = relayBaseUrl, accessToken = accessToken, onImageClick = onImageClick, onFileClick = onFileClick) }
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
                MessageFooter(title = if (isUser) "You" else "ClawLink", createdAt = message.createdAt, isUser = isUser)
            }
        }
    }
}

@Composable
internal fun MessageFooter(title: String, createdAt: String, isUser: Boolean, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val footerColor = if (isUser) Color.White.copy(alpha = 0.72f) else ChatColors.secondaryText
        Text(title, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = footerColor, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        Text(formatChatTimestamp(createdAt), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = footerColor, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun LoadingVoiceMessage(createdAt: String) {
    Column(
        modifier = Modifier.widthIn(max = 336.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White.copy(alpha = 0.96f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE1E4EA)),
            modifier = Modifier.width(216.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(ChatColors.linkBlue.copy(alpha = 0.10f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = ChatColors.linkBlue
                    )
                }
                Text(
                    "正在生成语音",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                VoiceWaveformBars(tint = ChatColors.linkBlue.copy(alpha = 0.70f))
            }
        }
        MessageFooter(
            title = "ClawLink",
            createdAt = createdAt,
            isUser = false,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
        )
    }
}
