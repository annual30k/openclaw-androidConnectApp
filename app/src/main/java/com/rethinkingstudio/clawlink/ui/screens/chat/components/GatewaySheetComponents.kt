package com.rethinkingstudio.clawlink.ui.screens.chat.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.core.models.chat.ChatSessionItem
import com.rethinkingstudio.clawlink.core.models.gateway.AggregateStatus
import com.rethinkingstudio.clawlink.core.models.gateway.GatewaySummary
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import com.rethinkingstudio.clawlink.ui.screens.chat.ChatColors
import com.rethinkingstudio.clawlink.ui.screens.settings.components.GatewayFlowPanel

private val ChatSessionItem.normalizedSessionKey: String
    get() = sessionKey.trim().ifBlank { "main" }

private val ChatSessionItem.displayTitle: String
    get() = listOf(displayName, label, derivedTitle)
        .firstOrNull { !it.isNullOrBlank() }
        ?.trim()
        ?: sessionDisplayName(sessionKey)

private val ChatSessionItem.activityText: String
    get() = lastActivityAt?.trim()?.takeIf { it.isNotEmpty() }?.let { "最近活动 $it" }.orEmpty()

internal fun sessionDisplayName(key: String): String {
    val normalized = key.trim().ifBlank { "main" }
    return when (normalized) {
        "main" -> "主会话"
        else -> normalized.removePrefix("session_").takeLast(8).ifBlank { "Session" }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GatewaySheetOverlay(
    gateways: List<GatewaySummary>,
    appRelayStatus: AggregateStatus,
    selectedGatewayId: String?,
    sessions: List<ChatSessionItem>,
    currentSessionKey: String,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onRefreshSessions: (GatewaySummary) -> Unit,
    onSelect: (GatewaySummary) -> Unit,
    onSelectSession: (GatewaySummary, ChatSessionItem) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var expandedSessionGatewayId by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ChatColors.sheet,
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 12.dp)
                    .size(width = 48.dp, height = 5.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFFB8BCC4))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CircleHeaderButton(Icons.Default.Close, stringResource(R.string.common_action_close), onDismiss)
                Text(stringResource(R.string.gateways_list_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = Color.Black)
                CircleHeaderButton(Icons.Default.Refresh, stringResource(R.string.gateways_refresh), onRefresh)
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 28.dp)
                ) {
                    if (gateways.isEmpty()) {
                        item {
                            EmptyGatewaySheetState()
                        }
                    } else {
                        items(gateways, key = { it.id }) { gateway ->
                            GatewayItemCard(
                                gateway = gateway,
                                appRelayStatus = appRelayStatus,
                                selected = selectedGatewayId == gateway.id,
                                sessions = if (selectedGatewayId == gateway.id) sessions else emptyList(),
                                currentSessionKey = if (selectedGatewayId == gateway.id) currentSessionKey else "",
                                isSessionExpanded = expandedSessionGatewayId == gateway.id,
                                onToggleSessionExpanded = {
                                    val shouldExpand = expandedSessionGatewayId != gateway.id
                                    expandedSessionGatewayId = if (shouldExpand) gateway.id else null
                                    if (shouldExpand) {
                                        onRefreshSessions(gateway)
                                    }
                                },
                                onRefreshSessions = { onRefreshSessions(gateway) },
                                onSelectSession = { session -> onSelectSession(gateway, session) },
                                onClick = { onSelect(gateway) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GatewayItemCard(
    gateway: GatewaySummary,
    appRelayStatus: AggregateStatus,
    selected: Boolean,
    sessions: List<ChatSessionItem>,
    currentSessionKey: String,
    isSessionExpanded: Boolean,
    onToggleSessionExpanded: () -> Unit,
    onRefreshSessions: () -> Unit,
    onSelectSession: (ChatSessionItem) -> Unit,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = 0.86f),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) ChatColors.selectionBlue else Color.White.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        gateway.displayName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 17.sp),
                        fontWeight = FontWeight.Black,
                        color = Color.Black
                    )
                    Text(
                        stringResource(R.string.gateway_last_seen, gateway.lastSeenAt),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium,
                        color = ChatColors.secondaryText,
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.gateway_last_model_label),
                            maxLines = 1,
                            style = MaterialTheme.typography.labelSmall,
                            color = ChatColors.secondaryText,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            gateway.currentModel.ifBlank { stringResource(R.string.common_not_selected) },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium,
                            color = ChatColors.secondaryText,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                StatusPill(
                    status = if (appRelayStatus == AggregateStatus.online) gateway.aggregateStatus else appRelayStatus
                )
            }
            GatewaySessionSelector(
                sessions = sessions,
                currentSessionKey = currentSessionKey,
                isExpanded = isSessionExpanded,
                onToggleExpanded = onToggleSessionExpanded,
                onRefreshSessions = onRefreshSessions,
                onSelectSession = onSelectSession
            )
            GatewayFlowPanel(
                statuses = GatewayStore.selectedGatewayStatuses(
                    selectedGateway = gateway,
                    appRelayStatus = appRelayStatus
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun GatewaySessionSelector(
    sessions: List<ChatSessionItem>,
    currentSessionKey: String,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onRefreshSessions: () -> Unit,
    onSelectSession: (ChatSessionItem) -> Unit
) {
    val currentSession = sessions.firstOrNull { it.normalizedSessionKey == currentSessionKey }
    val currentDisplayName = currentSession?.displayTitle ?: sessionDisplayName(currentSessionKey)
    val density = LocalDensity.current
    var selectorWidth by remember { mutableStateOf(0.dp) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (isExpanded) it.then(Modifier.zIndex(20f)) else it }
            .onGloballyPositioned { coordinates ->
                selectorWidth = with(density) { coordinates.size.width.toDp() }
            }
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onToggleExpanded)
                .padding(vertical = 1.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.chat_current_session),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = ChatColors.secondaryText,
                fontWeight = FontWeight.Medium
            )
            Text(
                currentDisplayName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = ChatColors.linkBlue,
                fontWeight = FontWeight.Medium
            )
            Icon(
                Icons.Default.ExpandMore,
                null,
                tint = Color(0xFF8B8F98),
                modifier = Modifier.size(16.dp)
            )
        }

        if (isExpanded) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, with(density) { 30.dp.roundToPx() }),
                onDismissRequest = onToggleExpanded,
                properties = PopupProperties(focusable = true)
            ) {
                GatewaySessionDropdownPanel(
                    sessions = sessions,
                    currentSessionKey = currentSessionKey,
                    onRefreshSessions = onRefreshSessions,
                    onSelectSession = onSelectSession,
                    modifier = Modifier.width(selectorWidth)
                )
            }
        }
    }
}

@Composable
private fun GatewaySessionDropdownPanel(
    sessions: List<ChatSessionItem>,
    currentSessionKey: String,
    onRefreshSessions: () -> Unit,
    onSelectSession: (ChatSessionItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF101827),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.settings_session_list_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.gateway_session_count, sessions.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.56f)
                    )
                }
                IconButton(onClick = onRefreshSessions, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.Refresh, null, tint = Color.White.copy(alpha = 0.74f), modifier = Modifier.size(16.dp))
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.12f))
            )

            if (sessions.isEmpty()) {
                Text(
                    stringResource(R.string.gateway_session_empty),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.62f)
                )
            } else {
                val maxHeight = ((sessions.size.coerceIn(1, 4) * 50).dp).coerceAtLeast(112.dp)
                LazyColumn(
                    modifier = Modifier.heightIn(max = maxHeight),
                    contentPadding = PaddingValues(vertical = 5.dp)
                ) {
                    items(sessions, key = { it.sessionKey }) { session ->
                        GatewaySessionRow(
                            session = session,
                            isCurrent = session.normalizedSessionKey == currentSessionKey,
                            onSelect = { onSelectSession(session) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GatewaySessionRow(
    session: ChatSessionItem,
    isCurrent: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(14.dp),
        color = if (isCurrent) ChatColors.linkBlue.copy(alpha = 0.12f) else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    session.displayTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isCurrent) ChatColors.linkBlue else Color.White,
                    fontWeight = if (isCurrent) FontWeight.Black else FontWeight.Bold
                )
                session.activityText.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.52f)
                    )
                }
            }
            if (isCurrent) {
                Surface(shape = RoundedCornerShape(999.dp), color = ChatColors.linkBlue.copy(alpha = 0.18f)) {
                    Text(
                        stringResource(R.string.gateway_session_current_badge),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = ChatColors.linkBlue,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPill(status: AggregateStatus) {
    val online = status == AggregateStatus.online
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (online) ChatColors.online.copy(alpha = 0.14f) else ChatColors.offline.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, if (online) ChatColors.online.copy(alpha = 0.36f) else ChatColors.offline.copy(alpha = 0.32f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(if (online) ChatColors.online else ChatColors.offline)
            )
            Text(
                if (online) stringResource(R.string.gateway_status_online) else stringResource(R.string.gateway_status_offline),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                color = if (online) ChatColors.online else ChatColors.offline,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun EmptyGatewaySheetState() {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.Settings, null, tint = ChatColors.secondaryText, modifier = Modifier.size(36.dp))
            Text("暂无网关", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.Black)
            Text(stringResource(R.string.gateway_connectivity_prompt), style = MaterialTheme.typography.bodySmall, color = ChatColors.secondaryText)
        }
    }
}
