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
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
import com.rethinkingstudio.clawlink.ui.screens.chat.ChatColors
import com.rethinkingstudio.clawlink.ui.screens.chat.ComposerAttachmentDraft
import com.rethinkingstudio.clawlink.ui.screens.chat.VoiceInputPhase
import com.rethinkingstudio.clawlink.ui.screens.chat.filePath
import com.rethinkingstudio.clawlink.ui.screens.chat.isImage

@Composable
internal fun DockPillButton(text: String, icon: ImageVector, enabled: Boolean, trailingIcon: ImageVector? = null, onClick: () -> Unit) {
    Surface(
        onClick = onClick, enabled = enabled, shape = RoundedCornerShape(999.dp),
        color = ChatColors.dockControl, border = BorderStroke(1.dp, ChatColors.dockBorder),
        modifier = Modifier.alpha(if (enabled) 1f else 0.55f)
    ) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            if (trailingIcon != null) { Icon(trailingIcon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)) }
        }
    }
}

@Composable
internal fun DockSmallButton(icon: ImageVector, label: String, selected: Boolean = false, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(999.dp), color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f) else ChatColors.dockControl, contentColor = if (selected) ChatColors.linkBlue else MaterialTheme.colorScheme.onSurface) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, modifier = Modifier.size(15.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun RoundIconButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, enabled = enabled, shape = CircleShape,
        color = ChatColors.dockControl, contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, ChatColors.dockBorder),
        modifier = Modifier.size(42.dp).alpha(if (enabled) 1f else 0.55f)
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, label, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
internal fun SendButton(enabled: Boolean, isStreaming: Boolean = false, isStoppingRun: Boolean = false, onClick: () -> Unit) {
    val backgroundColor = when {
        isStoppingRun -> ChatColors.offline.copy(alpha = 0.72f)
        !enabled -> ChatColors.disabledAction
        isStreaming -> ChatColors.offline
        else -> ChatColors.linkBlue
    }
    Surface(onClick = onClick, enabled = enabled, shape = CircleShape, color = backgroundColor, contentColor = if (enabled) Color.White else Color.White.copy(alpha = 0.9f), modifier = Modifier.size(42.dp)) {
        Box(contentAlignment = Alignment.Center) {
            if (isStoppingRun) { CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) }
            else if (isStreaming) { Icon(Icons.Default.Stop, choose("Stop", "停止"), modifier = Modifier.size(20.dp)) }
            else { Icon(Icons.AutoMirrored.Filled.Send, choose("Send", "发送"), modifier = Modifier.size(20.dp)) }
        }
    }
}
