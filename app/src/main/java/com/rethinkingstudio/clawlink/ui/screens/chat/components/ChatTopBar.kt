package com.rethinkingstudio.clawlink.ui.screens.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.core.models.gateway.AggregateStatus
import com.rethinkingstudio.clawlink.core.models.gateway.GatewaySummary
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import com.rethinkingstudio.clawlink.ui.screens.chat.ChatColors

@Composable
internal fun ChatTopBar(
    gateway: GatewaySummary?,
    contextUsageLine: String?,
    appRelayStatus: AggregateStatus,
    onGatewayClick: () -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    onBack: (() -> Unit)?
) {
    val effectiveStatus = GatewayStore.aggregateStatusForChain(gateway, appRelayStatus)

    val hasGateway = gateway != null
    val statusColor = when {
        !hasGateway -> ChatColors.pending
        else -> gatewayStatusTint(effectiveStatus)
    }
    val statusText = when {
        !hasGateway -> stringResource(R.string.gateway_status_unpaired)
        effectiveStatus == AggregateStatus.online -> stringResource(R.string.gateway_status_online)
        effectiveStatus == AggregateStatus.connecting -> stringResource(R.string.gateway_aggregate_connecting)
        effectiveStatus == AggregateStatus.partial -> stringResource(R.string.gateway_aggregate_partial)
        else -> stringResource(R.string.gateway_status_disconnected)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(72.dp)
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp)
            .padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        CircleHeaderButton(
            icon = if (onBack == null) Icons.Default.Refresh else Icons.AutoMirrored.Filled.ArrowBack,
            label = if (onBack == null) stringResource(R.string.gateways_refresh) else stringResource(R.string.common_action_back),
            onClick = onBack ?: onRefresh
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onGatewayClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.padding(vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        gateway?.displayName ?: stringResource(R.string.gateway_unpaired_host),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Icon(
                        Icons.Default.ExpandMore,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp).padding(start = 2.dp)
                    )
                }

                val visibleContextUsageLine = contextUsageLine?.trim()?.takeIf { it.isNotEmpty() }
                if (hasGateway && visibleContextUsageLine != null) {
                    Text(
                        text = (stringResource(R.string.gateway_context_usage_label) + " " + visibleContextUsageLine),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(top = 1.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Text(
                        statusText,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }

        CircleHeaderButton(Icons.Default.Settings, stringResource(R.string.settings_title), onSettings)
    }
}

@Composable
internal fun CircleHeaderButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)),
        modifier = Modifier.size(48.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
        }
    }
}
