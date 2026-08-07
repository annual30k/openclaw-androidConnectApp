package com.rethinkingstudio.clawlink.ui.screens.chat.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
import com.rethinkingstudio.clawlink.ui.screens.chat.ChatColors
import kotlin.math.abs

@Composable
internal fun QueuedMessagePanel(
    modifier: Modifier = Modifier,
    messages: List<ChatMessage>,
    onMove: (ChatMessage, Int) -> Unit,
    onRemove: (ChatMessage) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    val groupedListColor = if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        Color(0xFFF7F8FC)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("queued_message_panel"),
        shape = RoundedCornerShape(20.dp),
        color = ChatColors.dockSurface,
        tonalElevation = 0.dp,
        shadowElevation = 3.dp,
        border = BorderStroke(0.5.dp, ChatColors.dockBorder.copy(alpha = 0.72f))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(start = 16.dp, end = 8.dp)
                    .heightIn(min = 40.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = choose("Queued", "待发送"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.11f)
                ) {
                    Text(
                        text = "${messages.size}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = choose("Sends after the reply", "回复完成后依次发送"),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                    color = ChatColors.secondaryText
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = choose("Toggle queue", "展开或收起待发送消息"),
                    modifier = Modifier.size(22.dp),
                    tint = ChatColors.secondaryText
                )
            }

            if (expanded) {
                Surface(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    shape = RoundedCornerShape(15.dp),
                    color = groupedListColor,
                    border = BorderStroke(0.5.dp, ChatColors.dockBorder.copy(alpha = 0.72f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 126.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        messages.forEachIndexed { index, message ->
                            QueuedMessageRow(
                                message = message,
                                index = index,
                                totalCount = messages.size,
                                onMove = onMove,
                                onRemove = onRemove
                            )
                            if (index < messages.lastIndex) {
                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = ChatColors.dockBorder.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
                Text(
                    text = choose("Drag to reorder · use More to remove", "拖动可调整顺序 · 更多菜单可删除"),
                    modifier = Modifier.padding(start = 14.dp, top = 3.dp, end = 14.dp, bottom = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = ChatColors.secondaryText
                )
            }
        }
    }
}

@Composable
private fun QueuedMessageRow(
    message: ChatMessage,
    index: Int,
    totalCount: Int,
    onMove: (ChatMessage, Int) -> Unit,
    onRemove: (ChatMessage) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val dragStep = with(LocalDensity.current) { 34.dp.toPx() }
    val attachmentCount = message.contentBlocks.count { block ->
        block.isFileBlock || block.isVoiceMessageBlock
    }
    val text = message.clientMessageText?.trim()
        ?.takeIf { value -> value.isNotEmpty() }
        ?: message.plainTextContent.trim()
    val summary = when {
        text.isNotEmpty() -> text
        else -> choose("$attachmentCount attachments", "$attachmentCount 个附件")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 42.dp)
            .padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        QueueReorderHandle(
            message = message,
            dragStep = dragStep,
            onMove = onMove
        )

        Surface(
            modifier = Modifier.size(24.dp),
            shape = CircleShape,
            color = if (index == 0) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.11f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.64f)
            }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (index == 0) MaterialTheme.colorScheme.primary else ChatColors.secondaryText
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (index == 0) {
                    Text(
                        text = choose("Next", "下一条"),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(7.dp))
                }
                Text(
                    text = summary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (attachmentCount > 0 && text.isNotEmpty()) {
                Text(
                    text = choose("$attachmentCount attachments", "$attachmentCount 个附件"),
                    style = MaterialTheme.typography.labelSmall,
                    color = ChatColors.secondaryText
                )
            }
        }

        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = choose("Queue actions", "队列操作"),
                    modifier = Modifier.size(18.dp),
                    tint = ChatColors.secondaryText
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(choose("Move up", "上移")) },
                    leadingIcon = { Icon(Icons.Default.ArrowUpward, contentDescription = null) },
                    enabled = index > 0,
                    onClick = {
                        showMenu = false
                        onMove(message, -1)
                    }
                )
                DropdownMenuItem(
                    text = { Text(choose("Move down", "下移")) },
                    leadingIcon = { Icon(Icons.Default.ArrowDownward, contentDescription = null) },
                    enabled = index < totalCount - 1,
                    onClick = {
                        showMenu = false
                        onMove(message, 1)
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                DropdownMenuItem(
                    text = {
                        Text(
                            text = choose("Remove from queue", "从队列删除"),
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = {
                        showMenu = false
                        onRemove(message)
                    }
                )
            }
        }
    }
}

@Composable
private fun QueueReorderHandle(
    message: ChatMessage,
    dragStep: Float,
    onMove: (ChatMessage, Int) -> Unit
) {
    Box(
        modifier = Modifier
            .size(width = 32.dp, height = 40.dp)
            .pointerInput(message.id) {
                var unconsumedDrag = 0f
                detectDragGesturesAfterLongPress(
                    onDragStart = { unconsumedDrag = 0f },
                    onDragCancel = { unconsumedDrag = 0f },
                    onDragEnd = { unconsumedDrag = 0f },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        unconsumedDrag += dragAmount.y
                        if (abs(unconsumedDrag) >= dragStep) {
                            val offset = if (unconsumedDrag > 0) 1 else -1
                            onMove(message, offset)
                            unconsumedDrag = 0f
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.DragHandle,
            contentDescription = choose("Drag to reorder", "拖动调整顺序"),
            modifier = Modifier.size(19.dp),
            tint = ChatColors.secondaryText.copy(alpha = 0.62f)
        )
    }
}
