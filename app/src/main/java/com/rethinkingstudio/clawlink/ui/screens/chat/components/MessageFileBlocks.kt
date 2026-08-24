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
import androidx.compose.runtime.key
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
import com.rethinkingstudio.clawlink.core.state.chat.RemoteImageCache
import com.rethinkingstudio.clawlink.core.state.chat.chatAttachmentCacheKey
import com.rethinkingstudio.clawlink.core.state.chat.chatImageCacheKey
import com.rethinkingstudio.clawlink.core.state.chat.isExplicitAttachmentExpiredState
import com.rethinkingstudio.clawlink.core.state.chat.resolveAttachmentAvailability
import com.rethinkingstudio.clawlink.ui.screens.chat.ChatColors
import com.rethinkingstudio.clawlink.ui.screens.chat.formatChatTimestamp
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun StandaloneFileMessage(blocks: List<RelayChatContentBlock>, isUser: Boolean, messageState: MessageState, deliveryState: String = "", createdAt: String, relayBaseUrl: String, accessToken: String, onImageClick: (block: RelayChatContentBlock, url: String, fileName: String?) -> Unit = { _, _, _ -> }, onFileClick: (block: RelayChatContentBlock, url: String, fileName: String?) -> Unit = { _, _, _ -> }) {
    val maxContentWidth = if (blocks.any { it.isImageFileBlock }) 290.dp else 326.dp
    Column(modifier = Modifier.width(IntrinsicSize.Max).widthIn(max = maxContentWidth), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        blocks.forEachIndexed { index, block ->
            key(block.contentBlockId ?: block.stableAttachmentId ?: "legacy-file-block-$index") {
                FileBlock(block = block, isUser = isUser, messageState = messageState, standalone = true, relayBaseUrl = relayBaseUrl, accessToken = accessToken, onImageClick = onImageClick, onFileClick = onFileClick)
            }
        }
        MessageFooter(
            title = if (isUser) "You" else "ClawLink",
            createdAt = createdAt,
            isUser = false,
            deliveryState = deliveryState,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
internal fun FileBlock(block: RelayChatContentBlock, isUser: Boolean, messageState: MessageState, standalone: Boolean = false, relayBaseUrl: String, accessToken: String, imageMaxWidth: Dp = 290.dp, onImageClick: (block: RelayChatContentBlock, url: String, fileName: String?) -> Unit = { _, _, _ -> }, onFileClick: (block: RelayChatContentBlock, url: String, fileName: String?) -> Unit = { _, _, _ -> }) {
    val rawDownloadUrl = if (block.isImageFileBlock) {
        block.preferredImagePreviewURLString?.trim()?.takeIf { it.isNotEmpty() }
    } else {
        block.fileDownloadURLString?.trim()?.takeIf { it.isNotEmpty() }
    }
    val isUploadingState = messageState == MessageState.streaming
    val localFilePath = rawDownloadUrl?.let { raw ->
        when {
            raw.startsWith("file://", ignoreCase = true) -> raw.removePrefix("file://").takeIf { File(it).exists() }
            raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true) -> null
            File(raw).exists() -> raw
            else -> null
        }
    }
    val attachmentCacheKey = block.chatAttachmentCacheKey()
    val cachedLocalFile = attachmentCacheKey
        ?.let { RemoteAttachmentCache.cachedFile(it) }
        ?.takeIf { it.exists() }
    val localThumbnail = block.chatImageCacheKey()
        ?.let { RemoteImageCache.cachedFile(it) }
        ?.takeIf { it.exists() }
    val resolvedRemoteUrl = resolveFileDownloadUrl(block, relayBaseUrl, rawDownloadUrl)
        ?.takeUnless { candidate -> candidate == localFilePath }
    val availability = resolveAttachmentAvailability(
        hasLocalOriginal = localFilePath != null,
        hasLocalCachedCopy = cachedLocalFile != null,
        hasLocalThumbnail = localThumbnail != null,
        hasRemoteReference = resolvedRemoteUrl != null,
        expiresAt = block.expiresAt,
        serverReportedExpired =
            attachmentCacheKey?.let(RemoteAttachmentCache::isServerExpired) == true ||
                isExplicitAttachmentExpiredState(block.transferState, block.status)
    )
    val downloadUrl = resolvedRemoteUrl?.takeIf { availability.shouldAttemptRemoteDownload }
    val localOpenPath = localFilePath ?: cachedLocalFile?.absolutePath
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
        val dimensions = imagePreviewDimensions(block, maxWidth = imageMaxWidth)
        val cachedLocalImagePath = cachedLocalFile?.absolutePath ?: localThumbnail?.absolutePath
        val imageOpenReference = localFilePath ?: cachedLocalImagePath ?: downloadUrl
        Box(
            modifier = Modifier
                .width(dimensions.first)
                .height(dimensions.second)
        ) {
            when {
                localFilePath != null -> {
                    Box(modifier = imageOpenReference?.let { reference -> Modifier.clickable { onImageClick(block, reference, block.fileDisplayName) } } ?: Modifier) {
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
                    Box(modifier = imageOpenReference?.let { reference -> Modifier.clickable { onImageClick(block, reference, block.fileDisplayName) } } ?: Modifier) {
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
                            cornerRadius = 18.dp,
                            maxWidth = imageMaxWidth
                        )
                    }
                }
                else -> {
                    Surface(shape = RoundedCornerShape(18.dp), color = background, border = androidx.compose.foundation.BorderStroke(1.dp, border)) {
                        Column(modifier = Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(block.fileDisplayName ?: block.text ?: stringResource(R.string.chat_attachment), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, color = primaryText, style = MaterialTheme.typography.bodySmall)
                            Text(fileSubtitle(block), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = secondaryText)
                            if (availability.source == com.rethinkingstudio.clawlink.core.state.chat.AttachmentSource.SERVER_CLEANED) {
                                Text(choose("The file has been cleaned from the server", "文件已被服务器清理"), style = MaterialTheme.typography.labelSmall, color = secondaryText)
                            }
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

    val fileOpenReference = localOpenPath ?: downloadUrl
    Surface(onClick = { fileOpenReference?.let { onFileClick(block, it, block.fileDisplayName) } }, enabled = fileOpenReference != null, shape = RoundedCornerShape(18.dp), color = background, border = androidx.compose.foundation.BorderStroke(1.dp, border), modifier = if (standalone) Modifier.widthIn(max = 326.dp) else Modifier.fillMaxWidth()) {
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
                if (fileOpenReference != null && !isUploadCard) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, modifier = Modifier.padding(top = 5.dp).size(15.dp), tint = secondaryText) }
            }
            if (availability.source == com.rethinkingstudio.clawlink.core.state.chat.AttachmentSource.SERVER_CLEANED) {
                Text(choose("The file has been cleaned from the server", "文件已被服务器清理"), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = secondaryText)
            } else if (localOpenPath == null) {
                block.expiresAt?.takeIf { it.isNotBlank() }?.let { Text(choose("Expires $it", "有效期至 $it"), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = secondaryText) }
            }
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
