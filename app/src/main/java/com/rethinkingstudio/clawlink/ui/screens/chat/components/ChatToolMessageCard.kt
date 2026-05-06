package com.rethinkingstudio.clawlink.ui.screens.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import com.rethinkingstudio.clawlink.ui.screens.chat.formatChatTimestamp

private enum class TerminalMode { Command, Output }

@Composable
internal fun ToolMessageCard(
    message: ChatMessage,
    visibleToolBlocks: List<RelayChatContentBlock>,
    showInvocationProcess: Boolean
) {
    var expanded by remember(message.id, visibleToolBlocks.size) { mutableStateOf(false) }
    val cardTitle = if (showInvocationProcess && visibleToolBlocks.any { it.isToolCallBlock }) "Tool output" else "Tool result"
    val toolTitle = visibleToolBlocks.mapNotNull { it.resolvedName?.trim()?.ifEmpty { null } }.distinct().takeIf { it.isNotEmpty() }?.joinToString(", ")
        ?: message.toolDisplayName?.trim()?.ifEmpty { null } ?: "tool"
    val preview = visibleToolBlocks.firstNotNullOfOrNull { block ->
        block.toolDisplayContent(message.associatedToolCallBlock(block)).previewText().trim().ifEmpty { null }
    } ?: message.plainTextContent.trim().ifEmpty {
        when (message.state) { MessageState.completed -> "Completed"; MessageState.failed -> "Failed"; MessageState.streaming -> "Running" }
    }
    val statusColor = when (message.state) { MessageState.completed -> Color(0xFF5DCF7A); MessageState.failed -> Color(0xFFF24E3E); MessageState.streaming -> Color(0xFFF4A100) }
    val statusIcon = when (message.state) { MessageState.completed -> Icons.Default.CheckCircle; MessageState.failed -> Icons.Default.Close; MessageState.streaming -> Icons.Default.Refresh }

    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = Color(0xFF101827).copy(alpha = 0.98f), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(onClick = { expanded = !expanded }, color = Color.Transparent) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(if (expanded) Icons.Default.ExpandMore else Icons.AutoMirrored.Filled.KeyboardArrowRight, null, modifier = Modifier.size(14.dp), tint = Color.White.copy(alpha = 0.54f))
                    Icon(Icons.Default.Bolt, null, modifier = Modifier.size(16.dp), tint = Color(0xFFF24E3E))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(cardTitle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
                            Text(toolTitle, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = Color.White.copy(alpha = 0.54f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(preview, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = Color.White.copy(alpha = 0.48f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(statusIcon, null, modifier = Modifier.size(16.dp), tint = statusColor)
                }
            }
            AnimatedVisibility(visible = expanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (visibleToolBlocks.isEmpty()) {
                        TerminalBlock(title = message.toolDisplayName ?: "Tool Output", subtitle = null, text = message.plainTextContent.ifBlank { preview }, mode = TerminalMode.Output, isError = message.state == MessageState.failed)
                    } else {
                        visibleToolBlocks.forEach { block ->
                            val associated = message.associatedToolCallBlock(block)
                            val title = when { block.isToolCallBlock -> "COMMAND"; block.isError == true -> "ERROR"; else -> "OUTPUT" }
                            val subtitle = associated?.toolDocumentPath() ?: block.toolDocumentPath()
                            TerminalBlock(title = title, subtitle = subtitle, text = block.toolDisplayContent(associated).previewText(), mode = if (block.isToolCallBlock) TerminalMode.Command else TerminalMode.Output, isError = block.isError == true)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Tool", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.44f), fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                if (message.createdAt.isNotBlank()) { Text(formatChatTimestamp(message.createdAt), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.44f), fontWeight = FontWeight.Medium) }
            }
        }
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
            IconButton(onClick = { clipboardManager.setText(AnnotatedString(text)) }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(11.dp), tint = Color.White.copy(alpha = 0.84f))
            }
        }
        Box(modifier = Modifier.fillMaxWidth().background(brush = Brush.linearGradient(colors = listOf(Color(0xFF14171C), Color(0xFF1C1F26)), start = Offset.Zero, end = Offset.Infinite)).horizontalScroll(rememberScrollState()).padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (mode == TerminalMode.Command) { Text("$", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold), color = iconColor) }
                Text(text, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, lineHeight = 18.sp), color = bodyTextColor)
            }
        }
    }
}

@Composable
private fun PlainToolText(text: String, isError: Boolean) {
    Surface(shape = RoundedCornerShape(12.dp), color = if (isError) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.74f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.82f), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))) {
        Text(text, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface)
    }
}
