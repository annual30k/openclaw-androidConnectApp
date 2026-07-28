package com.rethinkingstudio.clawlink.ui.screens.chat.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.shadow
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
import com.rethinkingstudio.clawlink.core.models.gateway.GatewayType
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import com.rethinkingstudio.clawlink.ui.components.GatewayTypeIconBadge
import com.rethinkingstudio.clawlink.ui.screens.chat.ChatColors
import com.rethinkingstudio.clawlink.ui.screens.chat.formatChatTimestamp
import com.rethinkingstudio.clawlink.ui.screens.settings.components.GatewayFlowPanel

@Composable
internal fun GatewayItemCard(
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
    val selectionBreathAlpha = rememberGatewaySelectionBreathAlpha(selected)
    val cardShape = RoundedCornerShape(22.dp)
    val effectiveStatus = if (selected) {
        GatewayStore.aggregateStatusForChain(gateway, appRelayStatus)
    } else {
        gateway.aggregateStatus
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) {
                    Modifier.shadow(
                        elevation = selectionGlowElevation(selectionBreathAlpha),
                        shape = cardShape,
                        clip = false,
                        ambientColor = GatewaySelectionBreathingStyle.skyBlue.copy(alpha = selectionBreathAlpha * 0.22f),
                        spotColor = GatewaySelectionBreathingStyle.skyBlue.copy(alpha = selectionBreathAlpha * 0.26f)
                    )
                } else {
                    Modifier
                }
            ),
        shape = cardShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (selected) 0.62f else 0.46f),
        shadowElevation = 0.dp,
        border = BorderStroke(
            if (selected) GatewaySelectionBreathingStyle.borderWidth else 1.dp,
            if (selected) {
                GatewaySelectionBreathingStyle.skyBlue.copy(alpha = selectionBreathAlpha)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
            }
        )
    ) {
        Column(
            modifier = Modifier
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GatewayTypeIconBadge(gateway.gatewayType)
                        Text(
                            gateway.displayName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        stringResource(R.string.gateway_last_seen, formatChatTimestamp(gateway.lastSeenAt)),
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
                    status = effectiveStatus
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
private fun rememberGatewaySelectionBreathAlpha(selected: Boolean): Float {
    if (!selected) return GatewaySelectionBreathingStyle.minimumBorderAlpha
    val transition = rememberInfiniteTransition(label = "gateway-selection-breath")
    return transition.animateFloat(
        initialValue = GatewaySelectionBreathingStyle.minimumBorderAlpha,
        targetValue = GatewaySelectionBreathingStyle.maximumBorderAlpha,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = GatewaySelectionBreathingStyle.halfCycleMillis,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gateway-selection-border-alpha"
    ).value
}

private fun selectionGlowElevation(borderAlpha: Float) =
    (6f + 3f * ((borderAlpha - GatewaySelectionBreathingStyle.minimumBorderAlpha) /
        (GatewaySelectionBreathingStyle.maximumBorderAlpha - GatewaySelectionBreathingStyle.minimumBorderAlpha)))
        .dp

internal object GatewaySelectionBreathingStyle {
    val skyBlue = Color(0xFF70ADFA)
    val borderWidth = 2.dp
    const val halfCycleMillis = 1_200
    const val minimumBorderAlpha = 0.62f
    const val maximumBorderAlpha = 0.96f
}
