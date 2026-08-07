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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.key
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
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
import com.rethinkingstudio.clawlink.core.state.chat.ToolDetailCacheEntry
import com.rethinkingstudio.clawlink.core.state.chat.chatAttachmentCacheKey
import com.rethinkingstudio.clawlink.core.state.chat.chatImageCacheKey
import com.rethinkingstudio.clawlink.ui.screens.chat.ChatColors
import com.rethinkingstudio.clawlink.ui.screens.chat.formatChatTimestamp
import com.rethinkingstudio.clawlink.ui.screens.chat.isUserAuthoredMessage
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal val messageFooterMinimumItemGap = 10.dp

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
    toolDetailCacheByKey: Map<String, ToolDetailCacheEntry> = emptyMap(),
    onLoadToolDetail: (gatewayId: String, sessionKey: String, toolCallId: String) -> Unit = { _, _, _ -> },
    onImageClick: (block: RelayChatContentBlock, url: String, fileName: String?) -> Unit = { _, _, _ -> },
    onFileClick: (block: RelayChatContentBlock, url: String, fileName: String?) -> Unit = { _, _, _ -> }
) {
    val isUser = message.isUserAuthoredMessage()
    val isTool = message.role == MessageRole.tool || message.hasToolContent
    val visibleToolBlocks = message.visibleToolContentBlocks(showInvocationProcess)
    if (isTool && !message.shouldDisplayInChat(showInvocationProcess = showInvocationProcess)) return
    val syntheticFileBlocks = if (!isTool && message.fileContentBlocks.isEmpty()) {
        parseSendFileOutputBlocks(message.plainTextContent)
    } else emptyList()
    val fileBlocks = message.fileContentBlocks + syntheticFileBlocks
    val voiceBlocks = message.voiceContentBlocks
    val rawDisplayText = if (syntheticFileBlocks.isNotEmpty()) {
        ""
    } else {
        coalescedMixedMediaDisplayText(message.plainTextContent, message.contentBlocks)
    }
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
    val useExpandedMixedMediaBubble = shouldUseExpandedMixedMediaBubble(displayText, fileBlocks.isNotEmpty(), voiceBlocks.isNotEmpty())
    val orderedMixedBlocks = orderedMixedContentBlocks(message.contentBlocks)

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        if (isTool) {
            ToolMessageCard(
                message = message,
                visibleToolBlocks = visibleToolBlocks,
                showInvocationProcess = showInvocationProcess,
                gatewayId = gatewayId,
                sessionKey = sessionKey,
                toolDetailCacheByKey = toolDetailCacheByKey,
                onLoadToolDetail = onLoadToolDetail,
                modifier = Modifier.padding(vertical = 2.dp)
            )
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
            StandaloneFileMessage(blocks = fileBlocks, isUser = isUser, messageState = message.state, deliveryState = message.deliveryState, createdAt = message.createdAt, relayBaseUrl = relayBaseUrl, accessToken = accessToken, onImageClick = onImageClick, onFileClick = onFileClick)
            return@Column
        }
        if (shouldShowStreamingWaitState(message.role, message.state) &&
            shouldUseStandaloneStreamingIndicator(displayText, fileBlocks.isNotEmpty(), voiceBlocks.isNotEmpty())
        ) {
            StreamingIndicatorBubble()
            return@Column
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart) {
            val expandedBubbleWidth = mixedMediaBubbleWidth(maxWidth)
            val embeddedImageMaxWidth = maxOf(120.dp, expandedBubbleWidth - 32.dp)
            val footerTitle = if (isUser) "You" else "ClawLink"
            val adaptiveBubbleWidth = if (useExpandedMixedMediaBubble) {
                val density = LocalDensity.current
                val textMeasurer = rememberTextMeasurer()
                val measuredTextWidth = remember(displayText, textMeasurer, density) {
                    val widestLinePx = displayText.lineSequence().maxOfOrNull { line ->
                        textMeasurer.measure(
                            text = line,
                            style = TextStyle(fontSize = 13.sp),
                            softWrap = false,
                            maxLines = 1
                        ).size.width
                    } ?: 0
                    with(density) {
                        widestLinePx.toDp() + if (displayText.isBlank()) 0.dp else 2.dp
                    }
                }
                val formattedTimestamp = formatChatTimestamp(message.createdAt)
                val measuredFooterWidth = remember(footerTitle, formattedTimestamp, textMeasurer, density) {
                    val footerStyle = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    val titleWidthPx = textMeasurer.measure(
                        text = footerTitle,
                        style = footerStyle,
                        softWrap = false,
                        maxLines = 1
                    ).size.width
                    val timestampWidthPx = textMeasurer.measure(
                        text = formattedTimestamp,
                        style = footerStyle,
                        softWrap = false,
                        maxLines = 1
                    ).size.width
                    with(density) {
                        (titleWidthPx + timestampWidthPx).toDp() + messageFooterMinimumItemGap
                    }
                }
                val widestImageWidth = fileBlocks
                    .filter { it.isImageFileBlock }
                    .maxOfOrNull { imagePreviewDimensions(it, maxWidth = embeddedImageMaxWidth).first }
                    ?: 0.dp
                val requiresFullContentWidth = voiceBlocks.isNotEmpty() || fileBlocks.any { !it.isImageFileBlock }
                adaptiveMixedMediaBubbleWidth(
                    maximumWidth = expandedBubbleWidth,
                    contentWidths = listOf(
                        measuredTextWidth,
                        measuredFooterWidth,
                        120.dp,
                        widestImageWidth,
                        if (requiresFullContentWidth) embeddedImageMaxWidth else 0.dp
                    )
                )
            } else 0.dp
            Surface(
                color = if (isUser) ChatColors.userBubble else MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shape = RoundedCornerShape(28.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isUser) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
                tonalElevation = 0.dp, shadowElevation = 0.dp,
                modifier = if (useExpandedMixedMediaBubble) {
                    Modifier.width(adaptiveBubbleWidth)
                } else {
                    Modifier.width(IntrinsicSize.Max).widthIn(max = 326.dp)
                }
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (orderedMixedBlocks.isNotEmpty()) {
                        orderedMixedBlocks.forEachIndexed { index, block ->
                            key(block.contentBlockId ?: block.stableAttachmentId ?: "legacy-mixed-content-block-$index") {
                                when {
                                    block.isVoiceMessageBlock -> VoiceBlock(
                                        block,
                                        isUser,
                                        relayBaseUrl = relayBaseUrl,
                                        accessToken = accessToken,
                                        readVoicePlaybackIdentifiers = readVoicePlaybackIdentifiers,
                                        onVoicePlaybackStart = onVoicePlaybackStart,
                                        gatewayId = gatewayId,
                                        sessionKey = sessionKey
                                    )
                                    block.isFileBlock -> FileBlock(
                                        block,
                                        isUser,
                                        message.state,
                                        relayBaseUrl = relayBaseUrl,
                                        accessToken = accessToken,
                                        imageMaxWidth = if (useExpandedMixedMediaBubble) embeddedImageMaxWidth else 290.dp,
                                        onImageClick = onImageClick,
                                        onFileClick = onFileClick
                                    )
                                    block.isTextBlock -> MixedContentMarkdownText(
                                        text = block.text.orEmpty(),
                                        isUser = isUser,
                                        isStreaming = message.state == MessageState.streaming
                                    )
                                }
                            }
                        }
                    } else {
                        voiceBlocks.forEachIndexed { index, block ->
                            key(block.contentBlockId ?: block.stableAttachmentId ?: "legacy-voice-block-$index") {
                                VoiceBlock(
                                    block,
                                    isUser,
                                    relayBaseUrl = relayBaseUrl,
                                    accessToken = accessToken,
                                    readVoicePlaybackIdentifiers = readVoicePlaybackIdentifiers,
                                    onVoicePlaybackStart = onVoicePlaybackStart,
                                    gatewayId = gatewayId,
                                    sessionKey = sessionKey
                                )
                            }
                        }
                        fileBlocks.forEachIndexed { index, block ->
                            key(block.contentBlockId ?: block.stableAttachmentId ?: "legacy-file-block-$index") {
                                FileBlock(
                                    block,
                                    isUser,
                                    message.state,
                                    relayBaseUrl = relayBaseUrl,
                                    accessToken = accessToken,
                                    imageMaxWidth = if (useExpandedMixedMediaBubble) embeddedImageMaxWidth else 290.dp,
                                    onImageClick = onImageClick,
                                    onFileClick = onFileClick
                                )
                            }
                        }
                        if (displayText.isNotEmpty()) {
                            MixedContentMarkdownText(
                                text = displayText,
                                isUser = isUser,
                                isStreaming = message.state == MessageState.streaming
                            )
                        }
                    }
                    if (shouldShowInlineStreamingIndicator(message.role, message.state, displayText, fileBlocks.isNotEmpty(), voiceBlocks.isNotEmpty())) {
                        InlineStreamingIndicator()
                    }
                    if (shouldShowMessageFooter(message.role, message.state, displayText, fileBlocks.isNotEmpty(), voiceBlocks.isNotEmpty(), isTool)) {
                        MessageFooter(
                            title = footerTitle,
                            createdAt = message.createdAt,
                            isUser = isUser,
                            deliveryState = message.deliveryState,
                            fillsAvailableWidth = true
                        )
                    }
                }
            }
        }
    }
}

internal fun orderedMixedContentBlocks(
    contentBlocks: List<RelayChatContentBlock>
): List<RelayChatContentBlock> {
    val visibleBlocks = contentBlocks.filter { block ->
        block.isFileBlock || block.isVoiceMessageBlock || (block.isTextBlock && !block.text.isNullOrBlank())
    }
    val hasText = visibleBlocks.any { it.isTextBlock && !it.text.isNullOrBlank() }
    val hasMedia = visibleBlocks.any { it.isFileBlock || it.isVoiceMessageBlock }
    return if (hasText && hasMedia) visibleBlocks else emptyList()
}

@Composable
private fun MixedContentMarkdownText(text: String, isUser: Boolean, isStreaming: Boolean) {
    MarkdownMessageText(
        text = text,
        modifier = Modifier,
        textColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        linkColor = if (isUser) Color.White else MaterialTheme.colorScheme.primary,
        textSizeSp = 13f,
        onDarkBackground = isUser,
        isStreaming = isStreaming
    )
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
    !hasFileBlocks && !hasVoiceBlocks && isStreamingIndicatorDisplayText(displayText)

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
        isStreamingIndicatorDisplayText(displayText) &&
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

internal fun shouldUseExpandedMixedMediaBubble(
    displayText: String,
    hasFileBlocks: Boolean,
    hasVoiceBlocks: Boolean
): Boolean = displayText.isNotBlank() && (hasFileBlocks || hasVoiceBlocks)

internal fun coalescedMixedMediaDisplayText(
    displayText: String,
    contentBlocks: List<RelayChatContentBlock>
): String {
    val normalized = displayText.trim()
    if (normalized.isEmpty() || contentBlocks.none { it.isFileBlock || it.isVoiceMessageBlock }) return normalized

    val canonicalTextById = contentBlocks.mapNotNull { block ->
        val id = block.contentBlockId?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
        if (!block.isTextBlock) return@mapNotNull null
        val text = block.text?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
        id to text
    }
    canonicalTextById
        .groupBy(keySelector = { it.second }, valueTransform = { it.first })
        .filterValues { ids -> ids.distinct().size == 1 }
        .keys
        .forEach { canonical ->
            for (count in 2..8) {
                if (normalized == List(count) { canonical }.joinToString("\n\n") ||
                    normalized == List(count) { canonical }.joinToString("\n")) {
                    return canonical
                }
            }
        }
    return normalized
}

internal fun mixedMediaBubbleWidth(availableRowWidth: Dp): Dp {
    val usableWidth = (availableRowWidth - 54.dp).coerceAtLeast(0.dp)
    return minOf(usableWidth, 560.dp)
}

internal fun adaptiveMixedMediaBubbleWidth(maximumWidth: Dp, contentWidths: List<Dp>): Dp {
    val contentWidth = contentWidths.maxOrNull() ?: 0.dp
    return (contentWidth + 32.dp).coerceAtMost(maximumWidth).coerceAtLeast(0.dp)
}

@Composable
internal fun MessageFooter(
    title: String,
    createdAt: String,
    isUser: Boolean,
    modifier: Modifier = Modifier,
    fillsAvailableWidth: Boolean = false,
    deliveryState: String = ""
) {
    // 气泡本身先按正文/媒体/最小 footer 宽度收缩；footer 再在该宽度内两端对齐。
    Row(
        modifier = if (fillsAvailableWidth) modifier.fillMaxWidth() else modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        val footerColor = if (isUser) Color.White.copy(alpha = 0.72f) else ChatColors.secondaryText
        val deliveryLabel = when (deliveryState.trim().lowercase()) {
            "queued" -> choose("Queued", "排队中")
            "failed" -> choose("Send failed", "发送失败")
            else -> ""
        }
        Text(
            listOf(title, deliveryLabel).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = footerColor,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.width(messageFooterMinimumItemGap))
        if (fillsAvailableWidth) {
            Spacer(Modifier.weight(1f))
        }
        Text(formatChatTimestamp(createdAt), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = footerColor, fontWeight = FontWeight.Medium)
    }
}
