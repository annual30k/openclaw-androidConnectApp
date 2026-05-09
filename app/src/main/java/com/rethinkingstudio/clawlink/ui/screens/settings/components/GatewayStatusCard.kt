package com.rethinkingstudio.clawlink.ui.screens.settings.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.core.models.gateway.AggregateStatus
import com.rethinkingstudio.clawlink.core.models.gateway.ConnectionPhase
import com.rethinkingstudio.clawlink.core.models.gateway.GatewayStatus
import com.rethinkingstudio.clawlink.core.models.gateway.GatewaySummary
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import kotlinx.coroutines.launch

@Composable
fun GatewayStatusCard(
    gateway: GatewaySummary,
    appRelayStatus: AggregateStatus,
    onEditName: suspend (String) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isEditing by remember { mutableStateOf(false) }
    var isSavingName by remember { mutableStateOf(false) }
    var editedName by remember { mutableStateOf(gateway.displayName) }
    val trimmedEditedName = editedName.trim()
    val nameFocusRequester = remember { FocusRequester() }
    val componentScope = rememberCoroutineScope()

    LaunchedEffect(gateway.id, gateway.displayName) {
        if (!isEditing) {
            editedName = gateway.displayName
        }
    }

    LaunchedEffect(isEditing) {
        if (isEditing && !isSavingName) {
            nameFocusRequester.requestFocus()
        }
    }

    fun saveEditedName() {
        if (isSavingName || trimmedEditedName.isBlank()) return
        isSavingName = true
        componentScope.launch {
            try {
                onEditName(trimmedEditedName)
                isEditing = false
            } finally {
                isSavingName = false
            }
        }
    }

    val effectiveStatus = GatewayStore.aggregateStatusForChain(gateway, appRelayStatus)

    val statusColor = when (effectiveStatus) {
        AggregateStatus.online -> Color(0xFF2BBD66)
        AggregateStatus.connecting -> Color(0xFFFAAF29)
        AggregateStatus.partial -> Color(0xFF70ADFA)
        AggregateStatus.offline -> Color(0xFFEF5450)
    }

    val statusText = when (effectiveStatus) {
        AggregateStatus.online -> stringResource(R.string.gateway_aggregate_online)
        AggregateStatus.connecting -> stringResource(R.string.gateway_aggregate_connecting)
        AggregateStatus.partial -> stringResource(R.string.gateway_aggregate_partial)
        AggregateStatus.offline -> stringResource(R.string.gateway_aggregate_offline)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(22.dp, RoundedCornerShape(28.dp), clip = false)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
            .clickable { if (!isEditing) onClick() }
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.36f),
                            Color(0xFF70ADFA).copy(alpha = 0.08f),
                            Color.White.copy(alpha = 0.10f)
                        )
                    )
                )
                .drawWithContent {
                    drawContent()
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.30f),
                        style = Stroke(width = 0.8.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(28.dp.toPx())
                    )
                }
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isEditing) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = editedName,
                                onValueChange = { editedName = it },
                                enabled = !isSavingName,
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(nameFocusRequester),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { saveEditedName() })
                            )

                            IconButton(
                                onClick = { saveEditedName() },
                                enabled = trimmedEditedName.isNotBlank() && !isSavingName,
                                modifier = Modifier.size(32.dp)
                            ) {
                                if (isSavingName) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFF2BBD66)
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = stringResource(R.string.common_action_save),
                                        tint = Color(0xFF2BBD66)
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    editedName = gateway.displayName
                                    isEditing = false
                                },
                                enabled = !isSavingName,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Cancel,
                                    contentDescription = stringResource(R.string.common_action_cancel),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.clickable {
                                editedName = gateway.displayName
                                isEditing = true
                            }
                        ) {
                            Text(
                                text = gateway.displayName,
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(
                                onClick = {
                                    editedName = gateway.displayName
                                    isEditing = true
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.gateway_edit_name),
                                    modifier = Modifier.size(18.dp),
                                    tint = Color(0xFF0F73ED)
                                )
                            }
                        }
                    }
                    Text(
                        text = stringResource(R.string.gateway_card_last_seen, gateway.lastSeenAt),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.gateway_card_recent_model),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = gateway.currentModel,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = statusColor.copy(alpha = 0.14f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.22f)),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .background(statusColor, CircleShape)
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = statusColor
                        )
                    }
                }
            }
            
            val synthesizedStatuses = remember(gateway.statuses, appRelayStatus) {
                GatewayStore.selectedGatewayStatuses(
                    selectedGateway = gateway,
                    appRelayStatus = appRelayStatus
                )
            }
            
            GatewayFlowPanel(
                statuses = synthesizedStatuses,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun EmptyGatewayStatusCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = Color(0xFF70ADFA)
    val emptyStatuses = remember {
        listOf(
            GatewayStatus(
                phase = ConnectionPhase.appRelay,
                status = AggregateStatus.partial,
                detail = "App connected"
            ),
            GatewayStatus(
                phase = ConnectionPhase.relayHost,
                status = AggregateStatus.offline,
                detail = "Relay unavailable"
            ),
            GatewayStatus(
                phase = ConnectionPhase.hostGateway,
                status = AggregateStatus.offline,
                detail = "Host unavailable"
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(22.dp, RoundedCornerShape(28.dp), clip = false)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.36f),
                            Color(0xFF70ADFA).copy(alpha = 0.08f),
                            Color.White.copy(alpha = 0.10f)
                        )
                    )
                )
                .drawWithContent {
                    drawContent()
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.30f),
                        style = Stroke(width = 0.8.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(28.dp.toPx())
                    )
                }
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.gateway_unpaired_host),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.settings_gateway_empty_last_seen),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.gateway_card_recent_model),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.settings_gateway_empty_recent_model),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = statusColor.copy(alpha = 0.14f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.22f)),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .background(statusColor, CircleShape)
                        )
                        Text(
                            text = stringResource(R.string.gateway_aggregate_partial),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = statusColor
                        )
                    }
                }
            }

            GatewayFlowPanel(
                statuses = emptyStatuses,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
