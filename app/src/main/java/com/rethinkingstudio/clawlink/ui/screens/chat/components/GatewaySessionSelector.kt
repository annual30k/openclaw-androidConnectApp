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
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import com.rethinkingstudio.clawlink.ui.screens.chat.ChatColors
import com.rethinkingstudio.clawlink.ui.screens.chat.formatChatTimestamp
import com.rethinkingstudio.clawlink.ui.screens.settings.components.GatewayFlowPanel

@Composable
internal fun GatewaySessionSelector(
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
internal fun GatewaySessionDropdownPanel(
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
internal fun GatewaySessionRow(
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

