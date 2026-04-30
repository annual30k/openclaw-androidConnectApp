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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.core.models.gateway.AggregateStatus
import com.rethinkingstudio.clawlink.core.models.gateway.GatewaySummary

@Composable
fun GatewayStatusCard(
    gateway: GatewaySummary,
    onEditName: (String) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isEditing by remember { mutableStateOf(false) }
    var editedName by remember { mutableStateOf(gateway.displayName) }

    val statusColor = when (gateway.aggregateStatus) {
        AggregateStatus.online -> MaterialTheme.colorScheme.secondary
        AggregateStatus.partial -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }

    val statusText = when (gateway.aggregateStatus) {
        AggregateStatus.online -> stringResource(R.string.gateway_aggregate_online)
        AggregateStatus.connecting -> stringResource(R.string.gateway_aggregate_connecting)
        AggregateStatus.partial -> stringResource(R.string.gateway_aggregate_partial)
        AggregateStatus.offline -> stringResource(R.string.gateway_aggregate_offline)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                shape = RoundedCornerShape(28.dp)
            )
            .clickable { if (!isEditing) onClick() }
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isEditing) {
                        OutlinedTextField(
                            value = editedName,
                            onValueChange = { editedName = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                onEditName(editedName)
                                isEditing = false
                            }),
                            trailingIcon = {
                                IconButton(onClick = {
                                    onEditName(editedName)
                                    isEditing = false
                                }) {
                                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.secondary)
                                }
                            }
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = gateway.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(
                                onClick = { isEditing = true },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    Text(
                        text = stringResource(R.string.gateway_card_last_seen, gateway.lastSeenAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.gateway_card_recent_model),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = gateway.currentModel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = statusColor.copy(alpha = 0.12f),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(statusColor, CircleShape)
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                }
            }
            
            // Placeholder for Flow Panel (Simple visualization for now)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF0D1117))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(3) { index ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    if (index < gateway.statuses.size) {
                                        val s = gateway.statuses[index].status
                                        when (s) {
                                            AggregateStatus.online -> MaterialTheme.colorScheme.secondary
                                            AggregateStatus.partial -> MaterialTheme.colorScheme.tertiary
                                            else -> MaterialTheme.colorScheme.error
                                        }
                                    } else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                                    CircleShape
                                )
                        )
                        if (index < 2) {
                            Box(
                                modifier = Modifier
                                    .height(2.dp)
                                    .weight(1f)
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                            )
                        }
                    }
                }
            }
        }
    }
}
