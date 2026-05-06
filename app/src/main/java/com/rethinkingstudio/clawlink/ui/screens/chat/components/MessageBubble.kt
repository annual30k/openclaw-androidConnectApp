package com.rethinkingstudio.clawlink.ui.screens.chat.components

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.rethinkingstudio.clawlink.core.state.chat.RemoteAttachmentCache
import com.rethinkingstudio.clawlink.core.state.chat.chatAttachmentCacheKey
import com.rethinkingstudio.clawlink.core.state.chat.chatImageCacheKey
import com.rethinkingstudio.clawlink.ui.screens.chat.ChatColors
import com.rethinkingstudio.clawlink.ui.screens.chat.formatChatTimestamp
import java.io.File

@Composable
internal fun MessageBubble(
    message: ChatMessage,
    showInvocationProcess: Boolean,
    relayBaseUrl: String,
    accessToken: String,
    onImageClick: (block: RelayChatContentBlock, url: String, fileName: String?) -> Unit = { _, _, _ -> },
    onFileClick: (block: RelayChatContentBlock, url: String, fileName: String?) -> Unit = { _, _, _ -> }
) {
    val isUser = message.role == MessageRole.user
    val isTool = message.role == MessageRole.tool || message.hasToolContent
    val visibleToolBlocks = message.visibleToolContentBlocks(showInvocationProcess)
    val shouldShowToolMessage = isTool && (
        visibleToolBlocks.isNotEmpty() || (message.toolContentBlocks.isEmpty() && message.plainTextContent.isNotBlank())
    )
    if (isTool && !shouldShowToolMessage) return
    val syntheticFileBlocks = if (!isTool && message.fileContentBlocks.isEmpty()) {
        parseSendFileOutputBlocks(message.plainTextContent)
    } else emptyList()
    val fileBlocks = message.fileContentBlocks + syntheticFileBlocks
    val rawDisplayText = if (syntheticFileBlocks.isNotEmpty()) "" else message.plainTextContent
    val displayText = if (fileBlocks.isNotEmpty()) {
        val trimmed = rawDisplayText.trim()
        val shouldSuppress = fileBlocks.any { block ->
            val name = block.fileDisplayName?.trim().orEmpty()
            val status = block.fileStatusText?.trim().orEmpty()
            (name.isNotEmpty() && trimmed == name) || (status.isNotEmpty() && trimmed == status)
        }
        if (shouldSuppress) "" else rawDisplayText
    } else rawDisplayText
    val isStandaloneFileMessage = !isTool && displayText.isBlank() && fileBlocks.isNotEmpty() && message.voiceContentBlocks.isEmpty()

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        if (isTool) {
            ToolMessageCard(message = message, visibleToolBlocks = visibleToolBlocks, showInvocationProcess = showInvocationProcess)
            return@Column
        }
    if (isStandaloneFileMessage) {
            StandaloneFileMessage(blocks = fileBlocks, isUser = isUser, messageState = message.state, createdAt = message.createdAt, relayBaseUrl = relayBaseUrl, accessToken = accessToken, onImageClick = onImageClick, onFileClick = onFileClick)
            return@Column
        }
        if (!isUser && message.state == MessageState.streaming && (
            displayText.isBlank() || displayText.startsWith("正在连接") || displayText.startsWith("连接中断") ||
            displayText == "正在同步回复..." || displayText == "正在同步最终内容..." || displayText == "已完成，但未返回文本。"
        ) && fileBlocks.isEmpty() && message.voiceContentBlocks.isEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StreamingIndicatorBubble()
                Text("ClawLink", modifier = Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.labelMedium, color = ChatColors.secondaryText, fontWeight = FontWeight.Bold)
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
                    MarkdownMessageText(text = displayText, textColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, linkColor = if (isUser) Color.White else MaterialTheme.colorScheme.primary, textSizeSp = 15f, onDarkBackground = isUser)
                }
                fileBlocks.forEach { FileBlock(it, isUser, message.state, relayBaseUrl = relayBaseUrl, accessToken = accessToken, onImageClick = onImageClick, onFileClick = onFileClick) }
                message.voiceContentBlocks.forEach { VoiceBlock(it, isUser) }
                MessageFooter(title = if (isUser) "You" else "ClawLink", createdAt = message.createdAt, isUser = isUser)
            }
        }
    }
}

@Composable
private fun MessageFooter(title: String, createdAt: String, isUser: Boolean, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val footerColor = if (isUser) Color.White.copy(alpha = 0.72f) else ChatColors.secondaryText
        Text(title, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = footerColor, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text(formatChatTimestamp(createdAt), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = footerColor, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StandaloneFileMessage(blocks: List<RelayChatContentBlock>, isUser: Boolean, messageState: MessageState, createdAt: String, relayBaseUrl: String, accessToken: String, onImageClick: (block: RelayChatContentBlock, url: String, fileName: String?) -> Unit = { _, _, _ -> }, onFileClick: (block: RelayChatContentBlock, url: String, fileName: String?) -> Unit = { _, _, _ -> }) {
    val maxContentWidth = if (blocks.any { it.isImageFileBlock }) 290.dp else 326.dp
    Column(modifier = Modifier.width(IntrinsicSize.Max).widthIn(max = maxContentWidth), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        blocks.forEach { block -> FileBlock(block = block, isUser = isUser, messageState = messageState, standalone = true, relayBaseUrl = relayBaseUrl, accessToken = accessToken, onImageClick = onImageClick, onFileClick = onFileClick) }
        MessageFooter(title = if (isUser) "You" else "ClawLink", createdAt = createdAt, isUser = false, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp))
    }
}

@Composable
private fun FileBlock(block: RelayChatContentBlock, isUser: Boolean, messageState: MessageState, standalone: Boolean = false, relayBaseUrl: String, accessToken: String, onImageClick: (block: RelayChatContentBlock, url: String, fileName: String?) -> Unit = { _, _, _ -> }, onFileClick: (block: RelayChatContentBlock, url: String, fileName: String?) -> Unit = { _, _, _ -> }) {
    val rawDownloadUrl = block.fileDownloadURLString?.trim()?.takeIf { it.isNotEmpty() }
    val isUploadingState = messageState == MessageState.streaming
    val localFilePath = rawDownloadUrl?.let { raw ->
        when {
            raw.startsWith("file://", ignoreCase = true) -> raw.removePrefix("file://").takeIf { File(it).exists() }
            raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true) -> null
            File(raw).exists() -> raw
            else -> null
        }
    }
    val downloadUrl = rawDownloadUrl?.takeIf { localFilePath == null }?.let { resolveFileUrl(it, relayBaseUrl) }
    val isStandaloneUserFile = isUser && standalone
    val isUploadCard = isUploadingState || block.status?.contains("上传中") == true || block.status?.contains("uploading", ignoreCase = true) == true
    val primaryText = when {
        isUploadCard -> Color.White
        isStandaloneUserFile -> Color.White
        isUser -> Color.White
        else -> MaterialTheme.colorScheme.onSurface
    }
    val secondaryText = when {
        isUploadCard -> Color.White.copy(alpha = 0.70f)
        isStandaloneUserFile -> Color.White.copy(alpha = 0.78f)
        isUser -> Color.White.copy(alpha = 0.72f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val background = when {
        isUploadCard -> Color(0xFF171A22)
        isStandaloneUserFile -> ChatColors.userBubble
        isUser -> Color.White.copy(alpha = 0.10f)
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
    }
    val border = when {
        isUploadCard -> Color(0xFF2A2F3A)
        isStandaloneUserFile -> Color.White.copy(alpha = 0.10f)
        isUser -> Color.White.copy(alpha = 0.16f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
    }
    if (block.isImageFileBlock) {
        val dimensions = imagePreviewDimensions(block)
        val cachedLocalImagePath = block.chatAttachmentCacheKey()
            ?.let { RemoteAttachmentCache.cachedFile(it) }
            ?.takeIf { it.exists() }
            ?.absolutePath
        Box(
            modifier = Modifier
                .width(dimensions.first)
                .height(dimensions.second)
        ) {
            when {
                localFilePath != null -> {
                    Box(modifier = downloadUrl?.let { url -> Modifier.clickable { onImageClick(block, url, block.fileDisplayName) } } ?: Modifier) {
                        LocalAttachmentImagePreview(
                            filePath = localFilePath,
                            width = dimensions.first,
                            height = dimensions.second,
                            cornerRadius = 18.dp,
                            cacheKey = block.chatImageCacheKey()
                        )
                    }
                }
                cachedLocalImagePath != null -> {
                    Box(modifier = downloadUrl?.let { url -> Modifier.clickable { onImageClick(block, url, block.fileDisplayName) } } ?: Modifier) {
                        LocalAttachmentImagePreview(
                            filePath = cachedLocalImagePath,
                            width = dimensions.first,
                            height = dimensions.second,
                            cornerRadius = 18.dp,
                            cacheKey = block.chatImageCacheKey()
                        )
                    }
                }
                downloadUrl != null -> {
                    Box(modifier = Modifier.clickable { onImageClick(block, downloadUrl, block.fileDisplayName) }) {
                        AuthenticatedRemoteImage(
                            url = downloadUrl,
                            accessToken = accessToken,
                            width = dimensions.first,
                            height = dimensions.second,
                            cacheKey = block.chatImageCacheKey(),
                            cornerRadius = 18.dp
                        )
                    }
                }
                else -> {
                    Surface(shape = RoundedCornerShape(18.dp), color = background, border = androidx.compose.foundation.BorderStroke(1.dp, border)) {
                        Column(modifier = Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(block.fileDisplayName ?: block.text ?: stringResource(R.string.chat_attachment), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, color = primaryText, style = MaterialTheme.typography.bodySmall)
                            Text(fileSubtitle(block), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = secondaryText)
                        }
                    }
                }
            }
            if (isUploadCard) {
                uploadProgressFromStatus(block.status)?.let { progress ->
                    AttachmentProgressBadge(
                        progress = progress,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                    )
                }
            }
        }
        return
    }

    Surface(onClick = { downloadUrl?.let { onFileClick(block, it, block.fileDisplayName) } }, enabled = downloadUrl != null, shape = RoundedCornerShape(18.dp), color = background, border = androidx.compose.foundation.BorderStroke(1.dp, border), modifier = if (standalone) Modifier.widthIn(max = 326.dp) else Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isUploadCard) Color.White.copy(alpha = 0.10f) else if (isUser) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                ) {
                    Icon(fileIcon(block), null, modifier = Modifier.padding(8.dp).size(16.dp), tint = if (isUploadCard) Color.White else primaryText)
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(block.fileDisplayName ?: stringResource(R.string.chat_attachment), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, color = primaryText, style = MaterialTheme.typography.bodySmall)
                    Text(fileSubtitle(block), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = secondaryText)
                    block.status?.takeIf { it.isNotBlank() && isUploadCard }?.let {
                        Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = secondaryText, fontWeight = if (isUploadCard) FontWeight.SemiBold else FontWeight.Normal)
                    }
                    if (isUploadingState) {
                        uploadProgressFromStatus(block.status)?.let { progress ->
                            LinearProgressIndicator(
                                progress = { progress.toFloat() },
                                modifier = Modifier.fillMaxWidth(),
                                color = if (isUploadCard) ChatColors.linkBlue else if (isUser) Color.White else MaterialTheme.colorScheme.primary,
                                trackColor = if (isUploadCard) Color.White.copy(alpha = 0.10f) else if (isUser) Color.White.copy(alpha = 0.20f) else Color(0xFFE5E7EB)
                            )
                        }
                    }
                }
                if (downloadUrl != null && !isUploadCard) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, modifier = Modifier.padding(top = 5.dp).size(15.dp), tint = secondaryText) }
            }
            block.expiresAt?.takeIf { it.isNotBlank() }?.let { Text("Expires $it", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = secondaryText) }
        }
    }
}

@Composable
private fun AttachmentProgressBadge(progress: Double, modifier: Modifier = Modifier) {
    Surface(
        shape = androidx.compose.foundation.shape.CircleShape,
        color = Color.Black.copy(alpha = 0.28f),
        modifier = modifier.size(42.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black.copy(alpha = 0.24f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress.toFloat().coerceIn(0f, 1f).coerceAtLeast(0.03f) },
                modifier = Modifier.size(42.dp),
                color = Color.White.copy(alpha = 0.46f),
                trackColor = Color.Black.copy(alpha = 0.24f),
                strokeWidth = 3.4.dp
            )
            Icon(Icons.Default.AccessTime, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun VoiceBlock(block: RelayChatContentBlock, isUser: Boolean) {
    Surface(shape = RoundedCornerShape(999.dp), color = if (isUser) Color.White.copy(alpha = 0.14f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.GraphicEq, null, modifier = Modifier.size(18.dp), tint = if (isUser) Color.White else MaterialTheme.colorScheme.primary)
            Text(block.voiceTranscriptText ?: block.voiceStatusText ?: stringResource(R.string.chat_voice_message), style = MaterialTheme.typography.bodySmall, color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface)
        }
    }
}

private fun fileIcon(block: RelayChatContentBlock): ImageVector {
    val mime = block.mimeType?.trim()?.lowercase().orEmpty()
    return when {
        mime.startsWith("image/") -> Icons.Default.Image
        mime.startsWith("audio/") -> Icons.Default.GraphicEq
        else -> Icons.Default.Description
    }
}

private fun fileSubtitle(block: RelayChatContentBlock): String {
    val parts = listOfNotNull(block.mimeType?.trim()?.takeIf { it.isNotEmpty() }, block.fileStatusText?.trim()?.takeIf { it.isNotEmpty() }).distinct()
    return parts.joinToString(" · ").ifBlank { block.status ?: "File" }
}

private fun resolveFileUrl(raw: String, relayBaseUrl: String): String {
    val trimmed = raw.trim()
    if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) return trimmed
    if (trimmed.startsWith("file://", ignoreCase = true)) return trimmed
    val base = relayBaseUrl.trim().trimEnd('/')
    if (base.isBlank()) return trimmed
    return "$base/${trimmed.trimStart('/')}"
}

private fun uploadProgressFromStatus(status: String?): Double? {
    val trimmed = status?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    val match = Regex("""\d{1,3}(?=%)""").find(trimmed) ?: return null
    return match.value.toDoubleOrNull()?.div(100.0)?.coerceIn(0.0, 1.0)
}

private fun parseSendFileOutputBlocks(text: String): List<RelayChatContentBlock> {
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

private fun inferMimeTypeFromName(fileName: String): String {
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

private fun String.parseFileSizeLabel(): Int? {
    val match = Regex("""([0-9]+(?:\.[0-9]+)?)\s*([KMGT]?B)""", RegexOption.IGNORE_CASE).find(this.trim()) ?: return null
    val value = match.groupValues[1].toDoubleOrNull() ?: return null
    val multiplier = when (match.groupValues[2].uppercase()) {
        "KB" -> 1024.0; "MB" -> 1024.0 * 1024.0; "GB" -> 1024.0 * 1024.0 * 1024.0; "TB" -> 1024.0 * 1024.0 * 1024.0 * 1024.0
        else -> 1.0
    }
    return (value * multiplier).toLong().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
