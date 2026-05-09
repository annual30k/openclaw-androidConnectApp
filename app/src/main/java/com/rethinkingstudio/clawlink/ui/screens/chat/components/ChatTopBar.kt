package com.rethinkingstudio.clawlink.ui.screens.chat.components

import androidx.compose.foundation.background
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
        effectiveStatus == AggregateStatus.online -> ChatColors.online
        else -> ChatColors.offline
    }
    val statusText = when {
        !hasGateway -> stringResource(R.string.gateway_status_unpaired)
        effectiveStatus == AggregateStatus.online -> stringResource(R.string.gateway_status_online)
        else -> stringResource(R.string.gateway_status_disconnected)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(72.dp)
            .background(ChatColors.canvas)
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

        Surface(
            onClick = onGatewayClick,
            shape = RoundedCornerShape(12.dp),
            color = Color.Transparent,
            modifier = Modifier.weight(1f)
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
                        color = Color.Black
                    )
                    Icon(
                        Icons.Default.ExpandMore,
                        null,
                        tint = Color(0xFF8B8F98),
                        modifier = Modifier.size(18.dp).padding(start = 2.dp)
                    )
                }

                if (hasGateway) {
                    Text(
                        text = (stringResource(R.string.gateway_context_usage_label) + " " + (gateway?.contextUsage?.takeIf { it.isNotBlank() } ?: stringResource(R.string.gateway_context_usage_empty))),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = Color(0xFF8B8F98),
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
                        color = Color(0xFF8B8F98),
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
        color = Color.White,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.92f)),
        modifier = Modifier.size(48.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = Color.Black, modifier = Modifier.size(24.dp))
        }
    }
}
