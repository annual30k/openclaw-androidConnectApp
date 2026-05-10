package com.rethinkingstudio.clawlink.ui.screens.settings

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.core.models.gateway.AggregateStatus
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import com.rethinkingstudio.clawlink.ui.components.ClawLinkScaffold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorFixScreen(
    gatewayStore: GatewayStore,
    onBack: () -> Unit
) {
    val gatewayState by gatewayStore.state.collectAsState()
    val scope = rememberCoroutineScope()
    val selectedGateway = gatewayState.selectedGateway
    val isExecuting = gatewayState.isExecutingMaintenance
    val logEntries = gatewayState.doctorFixLogs

    val isLocked = gatewayState.restartingGatewayId != null && gatewayState.restartingGatewayId != selectedGateway?.id
    val canExecute = selectedGateway != null && !isLocked && !isExecuting && gatewayState.canExecuteRemoteHostAction

    val listState = rememberLazyListState()

    LaunchedEffect(logEntries.size) {
        if (logEntries.isNotEmpty()) {
            listState.animateScrollToItem(logEntries.size - 1)
        }
    }

    ClawLinkScaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.advanced_doctor_fix), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface) },
                navigationIcon = {
                    Surface(
                        modifier = Modifier.padding(start = 16.dp).size(40.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 2.dp,
                        onClick = onBack
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_action_back), modifier = Modifier.size(20.dp))
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        content = { padding ->
            Box(modifier = Modifier.fillMaxSize()) {
                AppBackground()
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            GlassCard {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Surface(shape = CircleShape, color = Color(0xFF3B82F6).copy(alpha = 0.12f), modifier = Modifier.size(42.dp)) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.MedicalServices, null, tint = Color(0xFF3B82F6), modifier = Modifier.size(18.dp))
                                            }
                                        }
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                selectedGateway?.displayName ?: "--",
                                                fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                selectedGateway?.platform ?: "--",
                                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        val effectiveStatus = gatewayState.selectedGatewayAggregateStatus
                                        val statusColor = when (effectiveStatus) {
                                            AggregateStatus.online -> Color(0xFF22C55E)
                                            AggregateStatus.partial -> Color(0xFFF59E0B)
                                            else -> Color(0xFFEF4444)
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                                            modifier = Modifier.background(statusColor.copy(alpha = 0.12f), CircleShape).padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Box(Modifier.size(6.dp).background(statusColor, CircleShape))
                                            Text(
                                                when (effectiveStatus) {
                                                    AggregateStatus.online -> stringResource(R.string.gateway_aggregate_online)
                                                    AggregateStatus.connecting -> stringResource(R.string.gateway_aggregate_connecting)
                                                    AggregateStatus.partial -> stringResource(R.string.gateway_aggregate_partial)
                                                    AggregateStatus.offline -> stringResource(R.string.gateway_aggregate_offline)
                                                    else -> stringResource(R.string.advanced_status_unknown)
                                                },
                                                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = statusColor
                                            )
                                        }
                                    }

                                    AnimatedVisibility(
                                        visible = !isExecuting && logEntries.isEmpty(),
                                        enter = fadeIn() + expandVertically(),
                                        exit = fadeOut() + shrinkVertically()
                                    ) {
                                        Column {
                                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.8.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                                            Text(stringResource(R.string.advanced_doctor_fix_description), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                            Spacer(Modifier.height(12.dp))
                                            Button(
                                                onClick = {
                                                    val gatewayId = selectedGateway?.id ?: return@Button
                                                    scope.launch {
                                                        gatewayStore.executeAdvancedAction(gatewayId, "clawpilot.doctor.fix", "openclaw.doctorFix")
                                                    }
                                                },
                                                enabled = canExecute,
                                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                                                shape = RoundedCornerShape(25.dp)
                                            ) {
                                                Icon(Icons.Default.Healing, null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Text(stringResource(R.string.advanced_doctor_fix_execute), fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            AnimatedVisibility(
                                visible = isExecuting || logEntries.isNotEmpty(),
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().height(400.dp).clip(RoundedCornerShape(20.dp))) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().background(Color(0xFF1a1a2e)).padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Box(Modifier.size(8.dp).background(Color(0xFFFF5F56), CircleShape))
                                            Box(Modifier.size(8.dp).background(Color(0xFFFFBD2E), CircleShape))
                                            Box(Modifier.size(8.dp).background(Color(0xFF27C93F), CircleShape))
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text("TERMINAL", fontSize = 10.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.weight(1f))
                                        if (isExecuting) {
                                            CircularProgressIndicator(Modifier.size(14.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, strokeWidth = 2.dp)
                                        } else if (logEntries.isNotEmpty()) {
                                            IconButton(onClick = { gatewayStore.clearMaintenanceLogs("openclaw.doctorFix") }, Modifier.size(20.dp)) {
                                                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .background(Color(0xFF141414))
                                            .horizontalScroll(rememberScrollState())
                                            .padding(vertical = 12.dp)
                                    ) {
                                        items(logEntries) { entry ->
                                            Text(
                                                entry.text,
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 0.dp),
                                                fontFamily = FontFamily.Monospace, 
                                                fontSize = 9.sp,
                                                lineHeight = 13.sp,
                                                softWrap = false,
                                                color = if (entry.stream == "stderr") Color(0xFFF59E0B) else Color.White.copy(alpha = 0.95f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}
