package com.rethinkingstudio.clawlink.ui.screens.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
import com.rethinkingstudio.clawlink.core.state.chat.ToolDetailCacheEntry
import com.rethinkingstudio.clawlink.core.state.chat.toolDetailCacheKey
import com.rethinkingstudio.clawlink.ui.screens.chat.ChatColors
import com.rethinkingstudio.clawlink.ui.screens.chat.formatChatTimestamp

private enum class TerminalMode { Command, Output }

@Composable
internal fun ToolMessageCard(
    message: ChatMessage,
    visibleToolBlocks: List<RelayChatContentBlock>,
    showInvocationProcess: Boolean,
    gatewayId: String? = null,
    sessionKey: String? = null,
    toolDetailCacheByKey: Map<String, ToolDetailCacheEntry> = emptyMap(),
    onLoadToolDetail: (gatewayId: String, sessionKey: String, toolCallId: String) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    var expanded by remember(message.id) { mutableStateOf(shouldStartToolMessageExpanded(showInvocationProcess, message.state)) }
    val detailSummaryBlock = visibleToolBlocks.firstOrNull { block ->
        block.isToolResultBlock &&
            !block.toolCallId.isNullOrBlank() &&
            (block.hasFullDetail == true || block.detailTruncated == true || block.detailExpired == true)
    }
    val detailToolCallId = detailSummaryBlock?.toolCallId?.trim()?.takeIf { it.isNotEmpty() }
    val detailGatewayId = detailSummaryBlock?.gatewayId?.trim()?.takeIf { it.isNotEmpty() } ?: gatewayId?.trim()?.takeIf { it.isNotEmpty() }
    val detailSessionKey = detailSummaryBlock?.sessionKey?.trim()?.takeIf { it.isNotEmpty() } ?: sessionKey?.trim()?.takeIf { it.isNotEmpty() }
    val detailCacheKey = if (detailGatewayId != null && detailSessionKey != null && detailToolCallId != null) {
        toolDetailCacheKey(detailGatewayId, detailSessionKey, detailToolCallId)
    } else {
        null
    }
    val detailEntry = detailCacheKey?.let { toolDetailCacheByKey[it] }
    val loadedDetail = detailEntry?.response
    val detailUnavailable = detailSummaryBlock?.detailExpired == true ||
        detailSummaryBlock?.hasFullDetail == false ||
        loadedDetail?.expired == true ||
        loadedDetail?.hasFullDetail == false
    val shouldLoadDetail = expanded &&
        detailSummaryBlock?.hasFullDetail == true &&
        !detailUnavailable &&
        detailGatewayId != null &&
        detailSessionKey != null &&
        detailToolCallId != null &&
        detailEntry?.response == null &&
        detailEntry?.isLoading != true &&
        detailEntry?.issueMessage == null

    LaunchedEffect(shouldLoadDetail, detailGatewayId, detailSessionKey, detailToolCallId) {
        if (shouldLoadDetail) {
            onLoadToolDetail(detailGatewayId.orEmpty(), detailSessionKey.orEmpty(), detailToolCallId.orEmpty())
        }
    }

    val cardTitle = if (showInvocationProcess && visibleToolBlocks.any { it.isToolCallBlock }) "Tool output" else "Tool result"
    val toolTitle = visibleToolBlocks.mapNotNull { it.resolvedName?.trim()?.takeIf { name -> name.isNotEmpty() } }.distinct().takeIf { it.isNotEmpty() }?.joinToString(", ")
        ?: message.toolDisplaySummary.trim().takeIf { it.isNotEmpty() }
        ?: message.toolDisplayName?.trim()?.takeIf { it.isNotEmpty() }
        ?: "tool"
    val preview = visibleToolBlocks.firstNotNullOfOrNull { block ->
        block.toolPreviewText(message.associatedToolCallBlock(block)).trim().ifEmpty { null }
    } ?: message.plainTextContent.trim().ifEmpty {
        when (message.state) {
            MessageState.completed -> "Completed"
            MessageState.failed -> "Failed"
            MessageState.streaming -> "Running"
            MessageState.pending -> "Pending"
            MessageState.deleted -> "Deleted"
            MessageState.recalled -> "Recalled"
        }
    }
    val statusColor = when (message.state) {
        MessageState.completed -> Color(0xFF5DCF7A)
        MessageState.failed -> Color(0xFFF24E3E)
        MessageState.streaming -> Color(0xFFF4A100)
        else -> Color(0xFF8B8F98)
    }
    val statusIcon = when (message.state) {
        MessageState.completed -> Icons.Default.CheckCircle
        MessageState.failed -> Icons.Default.Close
        MessageState.streaming -> Icons.Default.Refresh
        else -> Icons.Default.Refresh
    }

    Surface(
        modifier = modifier.widthIn(max = 326.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Surface(onClick = {
                expanded = !expanded
            }, color = Color.Transparent) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(if (expanded) Icons.Default.ExpandMore else Icons.AutoMirrored.Filled.KeyboardArrowRight, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(Icons.Default.Bolt, null, modifier = Modifier.size(16.dp), tint = Color(0xFFF24E3E))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(cardTitle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text(toolTitle, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(preview, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(statusIcon, null, modifier = Modifier.size(16.dp), tint = statusColor)
                }
            }
            AnimatedVisibility(visible = expanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val loadedBlocks = loadedDetail?.contentBlocks.orEmpty()
                    val expandedBlocks = loadedBlocks.takeIf { it.isNotEmpty() } ?: visibleToolBlocks
                    val issueMessage = detailEntry?.issueMessage
                    when {
                        detailUnavailable || !issueMessage.isNullOrBlank() -> {
                            ToolTextBlock(
                                text = issueMessage ?: choose("Full output unavailable", "完整输出不可用"),
                                toolName = message.toolDisplayName,
                                isError = false
                            )
                        }
                        detailEntry?.isLoading == true || shouldLoadDetail -> {
                            ToolDetailLoadingRow()
                        }
                        loadedDetail != null && loadedBlocks.isEmpty() && loadedDetail.content.isNotBlank() -> {
                            ToolTextBlock(text = loadedDetail.content, toolName = loadedDetail.name ?: message.toolDisplayName, isError = message.state == MessageState.failed)
                        }
                        expandedBlocks.isEmpty() -> {
                            ToolTextBlock(text = message.plainTextContent.ifBlank { preview }, toolName = message.toolDisplayName, isError = message.state == MessageState.failed)
                        }
                        else -> {
                            expandedBlocks.forEach { block ->
                                ToolBlockView(
                                    block = block,
                                    associatedToolCallBlock = associatedToolCallBlockFor(
                                        message = message,
                                        block = block,
                                        blocks = expandedBlocks
                                    )
                                )
                            }
                        }
                    }
                    if (loadedDetail?.hasMore == true) {
                        Text(
                            choose(
                                "Output is long. Showing the first ${loadedDetail.limit} characters.",
                                "输出很长，当前只显示前 ${loadedDetail.limit} 字。"
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Tool", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                if (message.createdAt.isNotBlank()) { Text(formatChatTimestamp(message.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium) }
            }
        }
    }
}

@Composable
private fun ToolDetailLoadingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 1.6.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            choose("Loading full output...", "正在加载完整输出..."),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun associatedToolCallBlockFor(
    message: ChatMessage,
    block: RelayChatContentBlock,
    blocks: List<RelayChatContentBlock>
): RelayChatContentBlock? {
    if (block.isToolCallBlock) return block
    val callId = block.toolDetailResolvedCallId()
    if (!callId.isNullOrBlank()) {
        blocks.firstOrNull { candidate ->
            candidate.isToolCallBlock && candidate.toolDetailResolvedCallId() == callId
        }?.let { return it }
    }
    return message.associatedToolCallBlock(block)
}

private fun RelayChatContentBlock.toolDetailResolvedCallId(): String? =
    toolCallId?.trim()?.takeIf { it.isNotEmpty() }
        ?: toolUseId?.trim()?.takeIf { it.isNotEmpty() }

internal fun shouldStartToolMessageExpanded(
    showInvocationProcess: Boolean,
    state: MessageState
): Boolean = false

@Composable
private fun ToolBlockView(block: RelayChatContentBlock, associatedToolCallBlock: RelayChatContentBlock?) {
    val accent = when {
        block.isError == true -> Color(0xFFF24E3E)
        block.isToolCallBlock -> Color(0xFFF2B545)
        block.isToolResultBlock -> Color(0xFF5DCF7A)
        else -> Color(0xFF8B8F98)
    }
    val title = when {
        block.isToolCallBlock -> "Tool call"
        block.isError == true -> "Tool error"
        block.isToolResultBlock -> "Tool result"
        else -> "Tool output"
    }
    val status = when {
        block.isError == true -> "Error"
        block.isToolCallBlock -> "Call"
        block.isToolResultBlock -> "Result"
        else -> "Output"
    }
    val name = block.resolvedName ?: "tool"
    val detail = block.toolCallId?.trim()?.takeIf { it.isNotEmpty() } ?: block.toolUseId?.trim()?.takeIf { it.isNotEmpty() }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = RoundedCornerShape(999.dp), color = accent.copy(alpha = 0.15f)) {
                    Text(title.uppercase(), modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = accent, fontWeight = FontWeight.Bold)
                }
                Text(name, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                detail?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(status, style = MaterialTheme.typography.labelSmall, color = accent, fontWeight = FontWeight.Bold)
            }
            ToolDisplayBody(content = block.toolDisplayContent(associatedToolCallBlock), toolName = name, isError = block.isError == true)
        }
    }
}

@Composable
private fun ToolDisplayBody(content: ToolDisplayContent, toolName: String?, isError: Boolean) {
    when (content) {
        is ToolDisplayContent.Markdown -> {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            ) {
                MarkdownMessageText(
                    text = content.text,
                    modifier = Modifier.padding(10.dp),
                    textColor = MaterialTheme.colorScheme.onSurface,
                    linkColor = ChatColors.linkBlue,
                    textSizeSp = 13f,
                    onDarkBackground = false
                )
            }
        }
        is ToolDisplayContent.Code -> {
            ToolCodeBlock(
                language = content.language,
                code = content.code,
                isError = isError
            )
        }
        is ToolDisplayContent.TerminalCommand -> TerminalBlock(
            title = "Shell command",
            subtitle = content.workdir?.let { "in $it" },
            text = content.command,
            mode = TerminalMode.Command,
            isError = isError
        )
        is ToolDisplayContent.TerminalOutput -> TerminalBlock(
            title = if (content.isError) "Shell error" else "Shell output",
            subtitle = content.workdir?.let { "in $it" },
            text = content.text,
            mode = TerminalMode.Output,
            isError = content.isError
        )
        is ToolDisplayContent.Text -> ToolTextBlock(text = content.text, toolName = toolName, isError = isError)
    }
}

@Composable
private fun ToolCodeBlock(language: String?, code: String, isError: Boolean) {
    val clipboardManager = LocalClipboardManager.current
    val label = language.displayCodeLanguageLabel() ?: "Code"
    val normalizedLanguage = language.normalizedCodeLanguageOrNull().orEmpty()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.62f))
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)), RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            CompactCopyButton(
                onClick = { clipboardManager.setText(AnnotatedString(code)) },
                tint = MaterialTheme.colorScheme.onSurface,
                iconSize = 14.dp
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            if (normalizedLanguage == "json") {
                Text(
                    jsonAnnotatedString(code, isError),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                )
            } else {
                Text(
                    code,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    ),
                    color = if (isError) Color(0xFFF24E3E) else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun CompactCopyButton(onClick: () -> Unit, tint: Color, iconSize: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.ContentCopy,
            contentDescription = choose("Copy", "复制"),
            modifier = Modifier.size(iconSize),
            tint = tint
        )
    }
}

@Composable
private fun TerminalBlock(title: String, subtitle: String?, text: String, mode: TerminalMode, isError: Boolean) {
    val clipboardManager = LocalClipboardManager.current
    val iconColor = if (isError) Color(0xFFF24E3E) else Color(0xFF5DCF7A)
    val bodyTextColor = if (isError) Color(0xFFFDC6BC) else Color.White.copy(alpha = 0.94f)
    val borderColor = if (isError) Color(0xFFF24E3E).copy(alpha = 0.34f) else Color(0xFF5DCF7A).copy(alpha = 0.24f)
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, borderColor, RoundedCornerShape(16.dp))) {
        Row(modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.06f)).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(if (mode == TerminalMode.Command) Icons.Default.Terminal else Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(13.dp), tint = iconColor)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.92f))
                if (!subtitle.isNullOrBlank()) { Text(subtitle, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 9.sp), color = Color.White.copy(alpha = 0.54f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            CompactCopyButton(
                onClick = { clipboardManager.setText(AnnotatedString(text)) },
                tint = Color.White.copy(alpha = 0.84f),
                iconSize = 11.dp
            )
        }
        Box(modifier = Modifier.fillMaxWidth().background(brush = Brush.linearGradient(colors = listOf(Color(0xFF14171C), Color(0xFF1C1F26)), start = Offset.Zero, end = Offset.Infinite)).horizontalScroll(rememberScrollState()).padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (mode == TerminalMode.Command) { Text("$", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold), color = iconColor) }
                Text(text, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, lineHeight = 18.sp), color = bodyTextColor)
            }
        }
    }
}

private fun jsonAnnotatedString(code: String, isError: Boolean): AnnotatedString {
    val keyColor = Color(0xFF0066FF)
    val stringColor = if (isError) Color(0xFF00A850) else Color(0xFF00A850)
    val numberColor = Color(0xFFB26A00)
    val literalColor = Color(0xFF8B2BE2)
    val punctuationColor = Color(0xFF6B7280)

    return buildAnnotatedString {
        var index = 0
        while (index < code.length) {
            val char = code[index]
            when {
                char == '"' -> {
                    val start = index
                    index++
                    var escaped = false
                    while (index < code.length) {
                        val current = code[index]
                        if (current == '"' && !escaped) {
                            index++
                            break
                        }
                        escaped = current == '\\' && !escaped
                        if (current != '\\') escaped = false
                        index++
                    }
                    var cursor = index
                    while (cursor < code.length && code[cursor].isWhitespace()) cursor++
                    val isKey = cursor < code.length && code[cursor] == ':'
                    withStyle(SpanStyle(color = if (isKey) keyColor else stringColor)) {
                        append(code.substring(start, index))
                    }
                }
                char.isDigit() || char == '-' -> {
                    val start = index
                    index++
                    while (index < code.length && (code[index].isDigit() || code[index] in listOf('.', 'e', 'E', '+', '-'))) {
                        index++
                    }
                    withStyle(SpanStyle(color = numberColor)) {
                        append(code.substring(start, index))
                    }
                }
                code.startsWith("true", index) || code.startsWith("false", index) || code.startsWith("null", index) -> {
                    val token = when {
                        code.startsWith("true", index) -> "true"
                        code.startsWith("false", index) -> "false"
                        else -> "null"
                    }
                    withStyle(SpanStyle(color = literalColor)) {
                        append(token)
                    }
                    index += token.length
                }
                char in listOf('{', '}', '[', ']', ':', ',') -> {
                    withStyle(SpanStyle(color = punctuationColor)) {
                        append(char)
                    }
                    index++
                }
                else -> {
                    append(char)
                    index++
                }
            }
        }
    }
}

@Composable
private fun ToolTextBlock(text: String, toolName: String?, isError: Boolean) {
    val clipboardManager = LocalClipboardManager.current
    val trimmed = text.trim()
    prettyPrintedJsonForToolCard(trimmed)?.let { json ->
        ToolCodeBlock(language = "json", code = json, isError = isError)
        return
    }
    if (trimmed.looksLikeToolCardCommandLine()) {
        TerminalBlock(
            title = "Shell command",
            subtitle = null,
            text = trimmed,
            mode = TerminalMode.Command,
            isError = isError
        )
        return
    }
    if (trimmed.looksLikeToolCardCodeSnippet()) {
        ToolCodeBlock(language = null, code = trimmed, isError = isError)
        return
    }
    val accentBarColor = Color(0xFFF4A100).copy(alpha = 0.18f)
    val backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.54f)
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(BorderStroke(0.7.dp, borderColor), RoundedCornerShape(12.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(accentBarColor)
                .padding(top = 1.dp)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MarkdownMessageText(
                text = trimmed,
                textColor = if (isError) Color(0xFFF24E3E) else MaterialTheme.colorScheme.onSurface,
                linkColor = ChatColors.linkBlue,
                textSizeSp = 13f,
                onDarkBackground = false
            )
            if (trimmed.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    CompactCopyButton(
                        onClick = { clipboardManager.setText(AnnotatedString(trimmed)) },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        iconSize = 11.dp
                    )
                }
            }
        }
    }
}

private val toolCardPrettyJson = kotlinx.serialization.json.Json { prettyPrint = true }

private fun prettyPrintedJsonForToolCard(text: String): String? {
    val trimmed = text.trim()
    if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) return null
    return runCatching {
        toolCardPrettyJson.encodeToString(
            kotlinx.serialization.json.JsonElement.serializer(),
            kotlinx.serialization.json.Json.parseToJsonElement(trimmed)
        )
    }.getOrNull()
}

private fun String.looksLikeToolCardCommandLine(): Boolean {
    val normalized = trim()
    if (normalized.startsWith("$ ") || normalized.startsWith("> ")) return true
    val prefixes = listOf("git ", "npm ", "pnpm ", "yarn ", "bun ", "npx ", "node ", "python ", "python3 ", "pip ", "uv ", "curl ", "wget ", "brew ", "docker ", "kubectl ", "ssh ", "scp ", "cd ", "ls ", "pwd ", "mkdir ", "rm ", "cp ", "mv ", "cat ", "sed ", "awk ", "xcodebuild ", "swift ", "bash ", "sh ", "zsh ")
    return prefixes.any { normalized.startsWith(it) }
}

private fun String.looksLikeToolCardCodeSnippet(): Boolean {
    val trimmed = trim()
    if (trimmed.isBlank() || trimmed.contains("```")) return false
    val codeSignals = listOf("fun ", "class ", "struct ", "import ", "const ", "let ", "var ", "=>", "</", "{", "}", ";")
    val lines = trimmed.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val signalCount = lines.take(20).sumOf { line -> codeSignals.count { line.contains(it) } }
    return signalCount >= 2 || (lines.size >= 3 && signalCount >= 1)
}
