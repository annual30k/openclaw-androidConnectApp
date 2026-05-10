package com.rethinkingstudio.clawlink.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.core.models.chat.ChatSessionItem
import com.rethinkingstudio.clawlink.core.network.RelayAPIClient
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
import com.rethinkingstudio.clawlink.core.state.chat.ChatStore
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.math.abs

@Composable
internal fun SessionScreenBackdrop() {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        colorScheme.background,
                        colorScheme.surface,
                        colorScheme.surfaceVariant.copy(alpha = 0.68f)
                    ),
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(1000f, 1000f)
                )
            )
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(320.dp)
                .offset(x = 60.dp, y = (-60).dp)
                .graphicsLayer(alpha = 0.45f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(colorScheme.primary.copy(alpha = 0.14f), Color.Transparent),
                        radius = Float.POSITIVE_INFINITY
                    ),
                    CircleShape
                )
                .blur(80.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(340.dp)
                .offset(x = (-80).dp, y = 80.dp)
                .graphicsLayer(alpha = 0.4f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(colorScheme.secondary.copy(alpha = 0.12f), Color.Transparent),
                        radius = Float.POSITIVE_INFINITY
                    ),
                    CircleShape
                )
                .blur(90.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(500.dp)
                .offset(x = 100.dp, y = 150.dp)
                .graphicsLayer(alpha = 0.25f)
                .background(Brush.radialGradient(listOf(colorScheme.primary.copy(alpha = 0.08f), Color.Transparent)), CircleShape)
                .blur(120.dp)
        )
    }
}

@Composable
internal fun SessionManagerSummaryCard(
    gatewayName: String,
    sessionCount: Int,
    canCreateSession: Boolean,
    onSwitchGateway: () -> Unit,
    onCreateSession: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SessionCardShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .border(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f), SessionCardShape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(choose("Current gateway", "当前网关"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(gatewayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(
                modifier = Modifier
                    .clip(SessionPillShape)
                    .background(SessionAccentBlue.copy(alpha = 0.08f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(choose("Sessions", "会话"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                Text(sessionCount.toString(), style = MaterialTheme.typography.titleMedium, color = SessionAccentBlue, fontWeight = FontWeight.Black)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SessionManagerActionTile(
                title = stringResource(R.string.session_action_switch_gateway),
                detail = choose("Choose another host", "选择另一台主机"),
                icon = Icons.Default.Memory,
                tint = SessionAccentBlue,
                modifier = Modifier.weight(1f),
                onClick = onSwitchGateway
            )
            SessionManagerActionTile(
                title = stringResource(R.string.session_action_create_session),
                detail = choose("Start an independent context", "开启独立上下文"),
                icon = Icons.Default.Add,
                tint = SessionSuccessGreen,
                enabled = canCreateSession,
                modifier = Modifier.weight(1f),
                onClick = onCreateSession
            )
        }
    }
}

@Composable
internal fun SessionManagerActionTile(
    title: String,
    detail: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(SessionTileShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f))
            .border(0.7.dp, tint.copy(alpha = 0.18f), SessionTileShape)
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(34.dp).background(tint.copy(alpha = 0.14f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun SectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun SessionManagerEmptyStateCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SessionRowShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .border(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f), SessionRowShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(stringResource(R.string.session_empty_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text(stringResource(R.string.session_empty_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun SessionManagerSessionRow(
    session: ChatSessionItem,
    isSelected: Boolean,
    isMainSession: Boolean,
    canTap: Boolean,
    modifier: Modifier = Modifier,
    onTap: () -> Unit
) {
    val dedicated = isDedicatedSession(session.normalizedSessionKey)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(SessionRowShape)
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .border(0.8.dp, if (isSelected) SessionAccentBlue.copy(alpha = 0.30f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.14f), SessionRowShape)
            .clickable(enabled = canTap, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .size(38.dp)
                .background(if (isSelected) SessionAccentBlue.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isSelected) Icons.Default.CheckCircle else if (isMainSession) Icons.Default.Home else Icons.Default.ChatBubbleOutline,
                contentDescription = null,
                tint = if (isSelected) SessionAccentBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(session.displayTitle, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                when {
                    isSelected -> SessionStatusPill(stringResource(R.string.session_status_current), SessionAccentBlue.copy(alpha = 0.12f), SessionAccentBlue)
                    dedicated -> SessionStatusPill(stringResource(R.string.session_status_debug), SessionSuccessGreen.copy(alpha = 0.14f), SessionSuccessGreen)
                    isMainSession -> SessionStatusPill(stringResource(R.string.session_status_main), SessionWarningOrange.copy(alpha = 0.14f), SessionWarningOrange)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(session.displaySubtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(session.activityText, style = MaterialTheme.typography.labelMedium, color = if (isSelected) SessionAccentBlue else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            }
            Text(session.sessionKey, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun SessionStatusPill(title: String, tint: Color, foreground: Color) {
    Text(
        title,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = foreground,
        modifier = Modifier
            .clip(SessionPillShape)
            .background(tint)
            .padding(horizontal = 9.dp, vertical = 5.dp)
    )
}

@Composable
internal fun MaintenanceBanner(title: String, message: String, icon: ImageVector, tint: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.08f)),
        border = androidx.compose.foundation.BorderStroke(0.6.dp, tint.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(Modifier.size(42.dp).background(tint.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = tint)
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun ProcessingOverlay(message: String, detail: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 40.dp)
                .shadow(20.dp, RoundedCornerShape(32.dp), clip = false, spotColor = Color.Black.copy(alpha = 0.15f))
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                .border(0.6.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f), RoundedCornerShape(32.dp))
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(64.dp),
                    strokeWidth = 3.dp,
                    color = SessionAccentBlue.copy(alpha = 0.12f),
                    trackColor = Color.Transparent
                )
                CircularProgressIndicator(
                    modifier = Modifier.size(42.dp),
                    strokeWidth = 3.5.dp,
                    color = SessionAccentBlue,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
            
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    message,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
