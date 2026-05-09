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
internal fun StandaloneVoiceMessage(
    blocks: List<RelayChatContentBlock>,
    isUser: Boolean,
    createdAt: String,
    relayBaseUrl: String,
    accessToken: String,
    readVoicePlaybackIdentifiers: Set<String>,
    onVoicePlaybackStart: (identifier: String) -> Unit,
    gatewayId: String?,
    sessionKey: String?
) {
    Column(
        modifier = Modifier.widthIn(max = 336.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        blocks.firstOrNull()?.let { block ->
            VoiceBlock(
                block = block,
                isUser = isUser,
                relayBaseUrl = relayBaseUrl,
                accessToken = accessToken,
                standalone = true,
                readVoicePlaybackIdentifiers = readVoicePlaybackIdentifiers,
                onVoicePlaybackStart = onVoicePlaybackStart,
                gatewayId = gatewayId,
                sessionKey = sessionKey
            )
        }
        MessageFooter(title = if (isUser) "You" else "ClawLink", createdAt = createdAt, isUser = false, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp))
    }
}

@Composable
internal fun VoiceBlock(
    block: RelayChatContentBlock,
    isUser: Boolean,
    relayBaseUrl: String,
    accessToken: String,
    standalone: Boolean = false,
    readVoicePlaybackIdentifiers: Set<String> = emptySet(),
    onVoicePlaybackStart: (identifier: String) -> Unit = {},
    gatewayId: String? = null,
    sessionKey: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isPlaying by remember(block.voicePlaybackIdentifier) { mutableStateOf(false) }
    var isLoading by remember(block.voicePlaybackIdentifier) { mutableStateOf(false) }
    var showTranscript by remember(block.voicePlaybackIdentifier) { mutableStateOf(false) }
    var player by remember(block.voicePlaybackIdentifier) { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(block.voicePlaybackIdentifier) {
        onDispose {
            player?.release()
            player = null
        }
    }

    val background = if (isUser) ChatColors.userBubble else Color.White.copy(alpha = 0.96f)
    val border = if (isUser) Color.White.copy(alpha = 0.10f) else Color(0xFFE1E4EA)
    val primary = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface
    val width = voiceBubbleWidth(block.durationMs)
    val transcript = block.voiceTranscriptText

    val storageKey = remember(block.voicePlaybackIdentifier, gatewayId, sessionKey) {
        val identifier = block.voicePlaybackIdentifier.trim()
        val gId = (gatewayId ?: block.gatewayId ?: "gateway").trim()
        val sKey = (sessionKey ?: block.sessionKey ?: "main").trim()
        if (identifier.isEmpty()) "" else "$gId|$sKey|$identifier"
    }
    val isRead = isUser || storageKey.isEmpty() || readVoicePlaybackIdentifiers.contains(storageKey)

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Box(contentAlignment = Alignment.CenterEnd) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = background,
                border = androidx.compose.foundation.BorderStroke(1.dp, border),
                modifier = Modifier
                    .width(width)
                    .pointerInput(block.voicePlaybackIdentifier, transcript) {
                        detectTapGestures(
                            onTap = {
                                scope.launch {
                                    try {
                                        if (isPlaying) {
                                            player?.stop()
                                            player?.release()
                                            player = null
                                            isPlaying = false
                                            return@launch
                                        }
                                        isLoading = true
                                        val playableFile = resolveVoicePlayableFile(block, relayBaseUrl, accessToken)
                                        val mediaPlayer = MediaPlayer().apply {
                                            setDataSource(context, Uri.fromFile(playableFile))
                                            setOnCompletionListener {
                                                isPlaying = false
                                                it.release()
                                                if (player === it) player = null
                                            }
                                            prepare()
                                            start()
                                        }
                                        if (!isRead) {
                                            onVoicePlaybackStart(block.voicePlaybackIdentifier)
                                        }
                                        player = mediaPlayer
                                        isPlaying = true
                                    } catch (error: Exception) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.chat_voice_play_failed, error.message ?: choose("Unknown error", "未知错误")),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
                            onLongPress = {
                                if (!transcript.isNullOrBlank()) {
                                    showTranscript = !showTranscript
                                }
                            }
                        )
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(primary.copy(alpha = if (isUser) 0.18f else 0.10f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            isLoading -> CircularProgressIndicator(modifier = Modifier.size(17.dp), strokeWidth = 2.dp, color = primary)
                            isPlaying -> Icon(Icons.Default.Pause, null, modifier = Modifier.size(18.dp), tint = primary)
                            else -> Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp), tint = primary)
                        }
                    }
                    block.voiceDurationText?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = primary, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.weight(1f))
                    VoiceWaveformBars(
                        tint = primary.copy(alpha = if (isPlaying) 0.96f else 0.70f),
                        isPlaying = isPlaying
                    )
                }
            }
            
            if (!isRead) {
                Box(
                    modifier = Modifier
                        .offset(x = 10.dp) // Pushed out further to match iOS (unreadDotDiameter + 2)
                        .size(10.dp)
                        .background(Color.White, androidx.compose.foundation.shape.CircleShape)
                        .padding(1.2.dp)
                        .background(Color(0xFFFF3B30), androidx.compose.foundation.shape.CircleShape)
                )
            }
        }

        if (showTranscript && !transcript.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = background,
                border = androidx.compose.foundation.BorderStroke(1.dp, border),
                modifier = Modifier.widthIn(max = if (standalone) 336.dp else 294.dp)
            ) {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp)) {
                    MarkdownMessageText(
                        text = formatVoiceTranscriptDisplay(transcript),
                        textColor = primary,
                        linkColor = if (isUser) Color.White else MaterialTheme.colorScheme.primary,
                        textSizeSp = 13f,
                        onDarkBackground = isUser
                    )
                }
            }
        }
    }
}

@Composable
internal fun VoiceWaveformBars(tint: Color, isPlaying: Boolean = false) {
    val heights = listOf(5.dp, 9.dp, 14.dp, 11.dp, 15.dp, 10.dp, 8.dp, 6.dp)
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "waveform")
    
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        heights.forEachIndexed { index, baseHeight ->
            val animatedHeight = if (isPlaying) {
                val animation = infiniteTransition.animateFloat(
                    initialValue = 0.5f,
                    targetValue = 1.5f,
                    animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                        animation = androidx.compose.animation.core.tween(
                            durationMillis = 400 + (index * 50),
                            easing = androidx.compose.animation.core.LinearEasing
                        ),
                        repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                    ),
                    label = "bar_$index"
                )
                baseHeight * animation.value
            } else {
                baseHeight
            }
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height(animatedHeight)
                    .background(tint, RoundedCornerShape(2.dp))
            )
        }
    }
}

internal fun voiceBubbleWidth(durationMs: Int?): Dp {
    val min = 156.dp
    val max = 326.dp
    val duration = durationMs?.takeIf { it > 0 } ?: return 216.dp
    val progress = (duration / 1000.0 / 18.0).coerceIn(0.0, 1.0).toFloat()
    return min + (max - min) * progress
}

private suspend fun resolveVoicePlayableFile(block: RelayChatContentBlock, relayBaseUrl: String, accessToken: String): File {
    return withContext(Dispatchers.IO) {
        val raw = block.voiceDownloadURLString?.trim().orEmpty()
        if (raw.startsWith("file://", ignoreCase = true)) {
            val file = File(raw.removePrefix("file://"))
            if (file.exists()) return@withContext file
        }
        if (raw.isNotBlank()) {
            val local = File(raw)
            if (local.exists()) return@withContext local
        }
        val resolvedUrl = raw.takeIf { it.isNotBlank() }?.let { resolveFileUrl(it, relayBaseUrl) }
            ?: throw IllegalStateException(choose("Missing voice download URL", "缺少语音下载地址"))
        val cacheKey = block.chatAttachmentCacheKey() ?: block.voicePlaybackIdentifier
        RemoteAttachmentCache.cachedFile(cacheKey)?.let { return@withContext it }
        val connection = (URL(resolvedUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            if (accessToken.isNotBlank()) setRequestProperty("Authorization", "Bearer $accessToken")
        }
        val bytes = connection.inputStream.use { it.readBytes() }
        RemoteAttachmentCache.put(cacheKey, block.fileDisplayName ?: block.text ?: "voice.m4a", bytes)
            ?: throw IllegalStateException(choose("Unable to cache voice file", "无法缓存语音文件"))
    }
}

internal fun formatVoiceTranscriptDisplay(text: String): String {
    return text
        .replace("\\n", "\n")
        .replace("\r\n", "\n")
        .replace("\r", "\n")
        .trim()
        .lines()
        .joinToString("\n") { line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && trimmed != "---" && trimmed.split(" - ").size > 2) {
                trimmed.replace(Regex("""\s+-\s+"""), "\n- ")
            } else {
                trimmed
            }
        }
        .trim()
}
