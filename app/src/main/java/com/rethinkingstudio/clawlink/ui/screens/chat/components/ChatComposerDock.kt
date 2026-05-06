package com.rethinkingstudio.clawlink.ui.screens.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.ui.screens.chat.ChatColors
import com.rethinkingstudio.clawlink.ui.screens.chat.ComposerAttachmentDraft
import com.rethinkingstudio.clawlink.ui.screens.chat.filePath
import com.rethinkingstudio.clawlink.ui.screens.chat.isImage

@Composable
internal fun ComposerDock(
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    selectedModelText: String,
    isStreaming: Boolean,
    isStoppingRun: Boolean,
    voiceMode: Boolean,
    attachments: List<ComposerAttachmentDraft>,
    isUploadingAttachment: Boolean,
    hasActiveSession: Boolean,
    canEditComposer: Boolean,
    canSendMessage: Boolean,
    showAttachmentMenu: Boolean,
    onDismissAttachmentMenu: () -> Unit,
    attachmentButtonPosition: IntOffset,
    attachmentButtonSize: IntSize,
    onAttachmentButtonPositionChanged: (IntOffset) -> Unit,
    onAttachmentButtonSizeChanged: (IntSize) -> Unit,
    onPickFiles: () -> Unit,
    onPickAlbum: () -> Unit,
    onPickCamera: () -> Unit,
    onRemoveAttachment: (ComposerAttachmentDraft) -> Unit,
    onOpenModelPicker: () -> Unit,
    onShowSkillSheet: () -> Unit,
    onOpenAttachment: () -> Unit,
    onToggleVoiceMode: () -> Unit,
    onSend: () -> Unit,
    onAbort: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ChatColors.dockSurface,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, ChatColors.dockBorder)
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DockPillButton(stringResource(R.string.chat_skills_extension), Icons.Default.AutoAwesome, enabled = hasActiveSession, onClick = onShowSkillSheet)
                Spacer(Modifier.weight(1f))
                DockPillButton(selectedModelText, Icons.Default.SmartToy, enabled = hasActiveSession, trailingIcon = Icons.Default.UnfoldMore, onClick = onOpenModelPicker)
            }

            if (attachments.isNotEmpty()) {
                AttachmentTray(
                    attachments = attachments,
                    isUploading = isUploadingAttachment,
                    onRemove = onRemoveAttachment
                )
            }

            if (voiceMode) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RoundIconButton(Icons.Default.Keyboard, stringResource(R.string.chat_placeholder), enabled = true, onClick = onToggleVoiceMode)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        modifier = Modifier.weight(1f).height(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.chat_hold_to_talk), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            val topLeft = coordinates.localToRoot(Offset.Zero)
                            onAttachmentButtonPositionChanged(IntOffset(topLeft.x.toInt(), topLeft.y.toInt()))
                            onAttachmentButtonSizeChanged(coordinates.size)
                        }
                    ) {
                        RoundIconButton(Icons.Default.Add, stringResource(R.string.chat_attachment), enabled = canEditComposer, onClick = onOpenAttachment)
                    }
                    BasicTextField(
                        value = messageText,
                        onValueChange = onMessageTextChange,
                        modifier = Modifier.weight(1f).height(42.dp),
                        enabled = canEditComposer,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { onSend() }),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.Black),
                        decorationBox = { innerTextField ->
                            Surface(
                                shape = RoundedCornerShape(21.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Color(0xFFE2E4E9))
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (messageText.isEmpty()) {
                                        Text(
                                            when {
                                                !hasActiveSession -> stringResource(R.string.chat_add_gateway_placeholder)
                                                !canSendMessage -> stringResource(R.string.gateway_status_disconnected)
                                                else -> stringResource(R.string.chat_placeholder)
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color(0xFFA0A4AF)
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        }
                    )
                    RoundIconButton(Icons.Default.Mic, stringResource(R.string.chat_voice_message), enabled = canEditComposer, onClick = onToggleVoiceMode)
                    SendButton(
                        enabled = (canSendMessage || isStreaming) && !isUploadingAttachment && !isStoppingRun && (messageText.isNotBlank() || attachments.isNotEmpty() || isStreaming),
                        isStreaming = isStreaming,
                        isStoppingRun = isStoppingRun,
                        onClick = { if (isStreaming && !isStoppingRun) onAbort() else onSend() }
                    )
                }
            }
        }
    }

    if (showAttachmentMenu) {
        AttachmentMenuPopup(
            anchorPosition = attachmentButtonPosition,
            anchorSize = attachmentButtonSize,
            onDismiss = onDismissAttachmentMenu,
            onPickAlbum = onPickAlbum,
            onPickCamera = onPickCamera,
            onPickFiles = onPickFiles
        )
    }
}

@Composable
private fun AttachmentTray(
    attachments: List<ComposerAttachmentDraft>,
    isUploading: Boolean,
    onRemove: (ComposerAttachmentDraft) -> Unit
) {
    val imageAttachments = attachments.filter { it.isImage }
    val fileAttachments = attachments.filter { !it.isImage }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = ChatColors.dockSurface.copy(alpha = 0.38f),
                shape = RoundedCornerShape(20.dp)
            )
            .border(BorderStroke(1.dp, ChatColors.dockBorder.copy(alpha = 0.92f)), RoundedCornerShape(20.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Text(
                text = stringResource(R.string.chat_attachment),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF111827)
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${attachments.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (imageAttachments.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                imageAttachments.forEach { attachment ->
                    Box(contentAlignment = Alignment.TopEnd) {
                        LocalAttachmentImageThumbnail(filePath = attachment.filePath, size = 88.dp, cornerRadius = 18.dp)
                        Surface(
                            onClick = { onRemove(attachment) },
                            shape = CircleShape,
                            color = Color(0xCC111827),
                            modifier = Modifier.padding(6.dp).size(20.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                            }
                        }
                    }
                }
                if (isUploading) {
                    Box(
                        modifier = Modifier.size(88.dp).clip(RoundedCornerShape(18.dp)).background(ChatColors.dockControl),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = ChatColors.linkBlue)
                    }
                }
            }
        }

        fileAttachments.forEach { attachment ->
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = ChatColors.dockSurface.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, ChatColors.dockBorder.copy(alpha = 0.92f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (attachment.isImage) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        else if (attachment.mimeType.lowercase().startsWith("video/")) ChatColors.linkBlue.copy(alpha = 0.12f)
                        else ChatColors.dockBorder.copy(alpha = 0.22f)
                    ) {
                        Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when {
                                    attachment.mimeType.lowercase().startsWith("audio/") -> Icons.Default.Mic
                                    attachment.mimeType.lowercase().startsWith("image/") -> Icons.Default.PhotoLibrary
                                    else -> Icons.Default.Description
                                },
                                contentDescription = null,
                                tint = Color(0xFF111827),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            attachment.fileName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF111827)
                        )
                        Text(
                            attachment.displaySubtitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { onRemove(attachment) }) {
                        Text(
                            stringResource(R.string.common_action_delete),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (isUploading && fileAttachments.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = ChatColors.linkBlue)
                Text(
                    stringResource(R.string.chat_uploading_attachment),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AttachmentMenuPopup(
    anchorPosition: IntOffset,
    anchorSize: IntSize,
    onDismiss: () -> Unit,
    onPickAlbum: () -> Unit,
    onPickCamera: () -> Unit,
    onPickFiles: () -> Unit
) {
    val density = LocalDensity.current
    val menuWidth = with(density) { 200.dp.roundToPx() }
    val menuHeight = with(density) { 206.dp.roundToPx() }
    val x = anchorPosition.x - with(density) { 8.dp.roundToPx() }
    val y = anchorPosition.y - menuHeight - with(density) { 12.dp.roundToPx() }
    val popupOffset = IntOffset(x.coerceAtLeast(with(density) { 12.dp.roundToPx() }), y.coerceAtLeast(with(density) { 12.dp.roundToPx() }))
    Popup(alignment = Alignment.TopStart, offset = popupOffset, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Surface(
            shape = RoundedCornerShape(34.dp), color = Color.White,
            border = BorderStroke(1.dp, Color(0xFFE8EBF1)),
            shadowElevation = 0.dp, tonalElevation = 0.dp,
            modifier = Modifier.width(with(density) { menuWidth.toDp() })
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
                AttachmentMenuItem(icon = Icons.Default.PhotoLibrary, label = stringResource(R.string.chat_attachment_album), onClick = onPickAlbum)
                AttachmentMenuItem(icon = Icons.Default.PhotoCamera, label = stringResource(R.string.chat_attachment_camera), onClick = onPickCamera)
                AttachmentMenuItem(icon = Icons.Default.Folder, label = stringResource(R.string.chat_attachment_file), onClick = onPickFiles)
            }
        }
    }
}

@Composable
private fun AttachmentMenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = ChatColors.linkBlue, modifier = Modifier.size(26.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827))
    }
}

@Composable
private fun DockPillButton(text: String, icon: ImageVector, enabled: Boolean, trailingIcon: ImageVector? = null, onClick: () -> Unit) {
    Surface(
        onClick = onClick, enabled = enabled, shape = RoundedCornerShape(999.dp),
        color = ChatColors.dockControl, border = BorderStroke(1.dp, ChatColors.dockBorder),
        modifier = Modifier.alpha(if (enabled) 1f else 0.55f)
    ) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = Color.Black)
            if (trailingIcon != null) { Icon(trailingIcon, null, modifier = Modifier.size(16.dp), tint = Color.Black.copy(alpha = 0.35f)) }
        }
    }
}

@Composable
private fun DockSmallButton(icon: ImageVector, label: String, selected: Boolean = false, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(999.dp), color = if (selected) Color(0xFFE4F2FF) else Color(0xFFF7F9FD), contentColor = if (selected) ChatColors.linkBlue else Color.Black) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, modifier = Modifier.size(15.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun RoundIconButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, enabled = enabled, shape = CircleShape,
        color = ChatColors.dockControl, contentColor = Color.Black,
        border = BorderStroke(1.dp, ChatColors.dockBorder),
        modifier = Modifier.size(42.dp).alpha(if (enabled) 1f else 0.55f)
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, label, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
private fun SendButton(enabled: Boolean, isStreaming: Boolean = false, isStoppingRun: Boolean = false, onClick: () -> Unit) {
    val backgroundColor = when {
        isStoppingRun -> ChatColors.offline.copy(alpha = 0.72f)
        !enabled -> ChatColors.disabledAction
        isStreaming -> ChatColors.offline
        else -> ChatColors.linkBlue
    }
    Surface(onClick = onClick, enabled = enabled, shape = CircleShape, color = backgroundColor, contentColor = if (enabled) Color.White else Color.White.copy(alpha = 0.9f), modifier = Modifier.size(42.dp)) {
        Box(contentAlignment = Alignment.Center) {
            if (isStoppingRun) { CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) }
            else if (isStreaming) { Icon(Icons.Default.Stop, "Stop", modifier = Modifier.size(20.dp)) }
            else { Icon(Icons.AutoMirrored.Filled.Send, "Send", modifier = Modifier.size(20.dp)) }
        }
    }
}
