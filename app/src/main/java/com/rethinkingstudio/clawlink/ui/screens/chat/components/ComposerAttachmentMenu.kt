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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.input.pointer.pointerInput
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
import com.rethinkingstudio.clawlink.ui.screens.chat.VoiceInputPhase
import com.rethinkingstudio.clawlink.ui.screens.chat.filePath
import com.rethinkingstudio.clawlink.ui.screens.chat.isImage

@Composable
internal fun AttachmentMenuPopup(
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
            shape = RoundedCornerShape(34.dp), color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
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
internal fun AttachmentMenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = ChatColors.linkBlue, modifier = Modifier.size(26.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}
