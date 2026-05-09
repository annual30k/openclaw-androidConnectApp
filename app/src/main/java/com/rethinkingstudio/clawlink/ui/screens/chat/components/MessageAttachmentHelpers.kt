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

internal fun fileIcon(block: RelayChatContentBlock): ImageVector {
    val mime = block.mimeType?.trim()?.lowercase().orEmpty()
    return when {
        mime.startsWith("image/") -> Icons.Default.Image
        mime.startsWith("audio/") -> Icons.Default.GraphicEq
        else -> Icons.Default.Description
    }
}

internal fun fileSubtitle(block: RelayChatContentBlock): String {
    val parts = listOfNotNull(block.mimeType?.trim()?.takeIf { it.isNotEmpty() }, block.fileStatusText?.trim()?.takeIf { it.isNotEmpty() }).distinct()
    return parts.joinToString(" · ").ifBlank { block.status ?: "File" }
}

internal fun resolveFileUrl(raw: String, relayBaseUrl: String): String {
    val trimmed = raw.trim()
    if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) return trimmed
    if (trimmed.startsWith("file://", ignoreCase = true)) return trimmed
    val base = relayBaseUrl.trim().trimEnd('/')
    if (base.isBlank()) return trimmed
    return "$base/${trimmed.trimStart('/')}"
}

internal fun uploadProgressFromStatus(status: String?): Double? {
    val trimmed = status?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    val match = Regex("""\d{1,3}(?=%)""").find(trimmed) ?: return null
    return match.value.toDoubleOrNull()?.div(100.0)?.coerceIn(0.0, 1.0)
}

internal fun parseSendFileOutputBlocks(text: String): List<RelayChatContentBlock> {
    if (!text.contains("[send-file] uploaded")) return emptyList()
    val chunks = Regex("""(?=\[send-file]\s+uploaded\s+)""").split(text).map { it.trim() }.filter { it.startsWith("[send-file] uploaded") && !it.startsWith("[send-file] uploaded chunk") }
    return chunks.mapNotNull { chunk ->
        val fileName = Regex("""\[send-file]\s+uploaded\s+(.+?)(?:\n|$)""").find(chunk)?.groupValues?.get(1)?.trim() ?: return@mapNotNull null
        if (fileName.startsWith("chunk ")) return@mapNotNull null
        val download = Regex("""(?m)^\s*download:\s*(\S+)""").find(chunk)?.groupValues?.get(1) ?: return@mapNotNull null
        val fileId = Regex("""(?m)^\s*file id:\s*(\S+)""").find(chunk)?.groupValues?.get(1)
        val gatewayId = Regex("""(?m)^\s*gateway:\s*(\S+)""").find(chunk)?.groupValues?.get(1)
        val sessionKey = Regex("""(?m)^\s*session:\s*(\S+)""").find(chunk)?.groupValues?.get(1)
        val sizeLabel = Regex("""(?m)^\s*size:\s*(.+)$""").find(chunk)?.groupValues?.get(1)?.trim()
        val expires = Regex("""(?m)^\s*expires:\s*(\S+)""").find(chunk)?.groupValues?.get(1)
        RelayChatContentBlock(type = "file", text = fileName, name = fileName, fileId = fileId, fileName = fileName, mimeType = inferMimeTypeFromName(fileName), sizeBytes = sizeLabel?.parseFileSizeLabel(), downloadUrl = download, expiresAt = expires, gatewayId = gatewayId, sessionKey = sessionKey)
    }
}

internal fun inferMimeTypeFromName(fileName: String): String {
    val lower = fileName.lowercase()
    return when {
        lower.endsWith(".png") -> "image/png"
        lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
        lower.endsWith(".gif") -> "image/gif"
        lower.endsWith(".webp") -> "image/webp"
        lower.endsWith(".pdf") -> "application/pdf"
        lower.endsWith(".zip") -> "application/zip"
        lower.endsWith(".mp3") -> "audio/mpeg"
        lower.endsWith(".wav") -> "audio/wav"
        lower.endsWith(".m4a") -> "audio/mp4"
        lower.endsWith(".mp4") -> "video/mp4"
        lower.endsWith(".md") -> "text/markdown"
        lower.endsWith(".txt") -> "text/plain"
        else -> "application/octet-stream"
    }
}

internal fun String.parseFileSizeLabel(): Int? {
    val match = Regex("""([0-9]+(?:\.[0-9]+)?)\s*([KMGT]?B)""", RegexOption.IGNORE_CASE).find(this.trim()) ?: return null
    val value = match.groupValues[1].toDoubleOrNull() ?: return null
    val multiplier = when (match.groupValues[2].uppercase()) {
        "KB" -> 1024.0; "MB" -> 1024.0 * 1024.0; "GB" -> 1024.0 * 1024.0 * 1024.0; "TB" -> 1024.0 * 1024.0 * 1024.0 * 1024.0
        else -> 1.0
    }
    return (value * multiplier).toLong().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
