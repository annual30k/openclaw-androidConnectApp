package com.rethinkingstudio.clawlink.ui.screens.settings

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.core.models.gateway.AggregateStatus
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import com.rethinkingstudio.clawlink.ui.components.ClawLinkScaffold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class MaintenanceMode {
    RESTART,
    REMOTE_RESTART
}

@Composable
fun AppBackground() {
    val accentBlue = Color(0xFF0A84FF)
    val accentBlueSoft = Color(0xFF5AC8FA)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFFF2F5FA), Color.White),
                    start = Offset(0f, 0f),
                    end = Offset(1000f, 1000f)
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
                        colors = listOf(accentBlue.copy(alpha = 0.25f), Color.Transparent)
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
                        colors = listOf(accentBlueSoft.copy(alpha = 0.22f), Color.Transparent)
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
                .background(
                    Brush.radialGradient(
                        listOf(accentBlue.copy(alpha = 0.1f), Color.Transparent)
                    ),
                    CircleShape
                )
                .blur(120.dp)
        )
    }
}

@Composable
private fun MetricChip(title: String, value: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF9FAFB),
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, Color(0xFFF3F4F6))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF9CA3AF))
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F2937))
        }
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val accentSoft = Color(0xFF5AC8FA)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(24.dp),
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.04f),
                spotColor = Color.Black.copy(alpha = 0.12f)
            )
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.82f))
            .border(BorderStroke(0.8.dp, Color.White.copy(alpha = 0.35f)), RoundedCornerShape(24.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.4f),
                            accentSoft.copy(alpha = 0.05f),
                            Color.White.copy(alpha = 0.1f)
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Column(content = content)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GatewayMaintenanceScreen(
    gatewayStore: GatewayStore,
    mode: MaintenanceMode,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val gatewayState by gatewayStore.state.collectAsState()
    val selectedGateway = gatewayState.selectedGateway
    val isExecuting = gatewayState.isExecutingMaintenance
    val isWaitingForRecovery = gatewayState.isWaitingForRecovery
    val isRemote = mode == MaintenanceMode.REMOTE_RESTART
    val logEntries = if (isRemote) gatewayState.remoteRestartLogs else gatewayState.restartLogs

    var isSendingRequest by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableLongStateOf(0) }
    var isTimedOut by remember { mutableStateOf(false) }

    val title = if (isRemote) stringResource(R.string.maintenance_remote_restart) else stringResource(R.string.maintenance_restart_gateway)

    val isLocked = gatewayState.restartingGatewayId != null && gatewayState.restartingGatewayId != selectedGateway?.id
    val canManage = selectedGateway != null &&
        !isLocked &&
        !isExecuting &&
        !isWaitingForRecovery &&
        if (isRemote) gatewayState.canExecuteRemoteHostAction else gatewayState.isSelectedGatewayChatChainReady

    val listState = rememberLazyListState()

    LaunchedEffect(logEntries.size) {
        if (logEntries.isNotEmpty()) {
            listState.animateScrollToItem(logEntries.size - 1)
        }
    }

    LaunchedEffect(isWaitingForRecovery) {
        if (isWaitingForRecovery) {
            elapsedSeconds = 0
            isTimedOut = false
            while (isWaitingForRecovery) {
                delay(1000)
                elapsedSeconds++
                if (elapsedSeconds >= 120) {
                    isTimedOut = true
                }
            }
        }
    }

    ClawLinkScaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color(0xFF1F2937)) },
                navigationIcon = {
                    Surface(
                        modifier = Modifier.padding(start = 16.dp).size(40.dp),
                        shape = CircleShape,
                        color = Color.White,
                        shadowElevation = 2.dp,
                        onClick = onBack
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_action_back), modifier = Modifier.size(20.dp))
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
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
                                                Icon(Icons.Default.Memory, null, tint = Color(0xFF3B82F6), modifier = Modifier.size(18.dp))
                                            }
                                        }
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                selectedGateway?.displayName ?: "--",
                                                fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1F2937)
                                            )
                                            Text(
                                                selectedGateway?.platform ?: "--",
                                                fontSize = 12.sp, color = Color(0xFF6B7280)
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
                                        visible = !isExecuting && logEntries.isEmpty() && !isWaitingForRecovery,
                                        enter = fadeIn() + expandVertically(),
                                        exit = fadeOut() + shrinkVertically()
                                    ) {
                                        Column {
                                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.8.dp, color = Color.Black.copy(alpha = 0.08f))
                                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Text(
                                                    if (isRemote) stringResource(R.string.maintenance_remote_restart_detail) else stringResource(R.string.maintenance_online_restart_detail),
                                                    fontSize = 12.sp, color = Color(0xFF6B7280)
                                                )
                                                Button(
                                                    onClick = {
                                                        isSendingRequest = true
                                                        scope.launch {
                                                            val method = if (isRemote) "clawpilot.gateway.remoteRestart" else "clawpilot.gateway.restart"
                                                            val kind = if (isRemote) "gateway.remoteRestart" else "gateway.restart"
                                                            gatewayStore.executeAdvancedAction(selectedGateway?.id ?: "", method, kind)
                                                            isSendingRequest = false
                                                        }
                                                    },
                                                    modifier = Modifier.fillMaxWidth().height(50.dp),
                                                    enabled = canManage,
                                                    shape = RoundedCornerShape(25.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A84FF))
                                                ) {
                                                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(if (isRemote) stringResource(R.string.maintenance_remote_restart) else stringResource(R.string.maintenance_restart_gateway), fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            if (!isExecuting && logEntries.isEmpty() && !isWaitingForRecovery) {
                                GlassCard {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(stringResource(R.string.maintenance_suggestion_title), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1F2937))
                                        Text(stringResource(R.string.maintenance_suggestion_body), fontSize = 12.sp, color = Color(0xFF6B7280))
                                    }
                                }
                            }
                        }

                        item {
                            AnimatedVisibility(
                                visible = isExecuting || isWaitingForRecovery || logEntries.isNotEmpty(),
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
                                        Text("TERMINAL", fontSize = 10.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, color = Color(0xFF9CA3AF))
                                        Spacer(Modifier.weight(1f))
                                        if (isExecuting) {
                                            CircularProgressIndicator(Modifier.size(14.dp), color = Color(0xFF9CA3AF), strokeWidth = 2.dp)
                                        } else if (logEntries.isNotEmpty()) {
                                            IconButton(onClick = { gatewayStore.clearMaintenanceLogs(if (isRemote) "gateway.remoteRestart" else "gateway.restart") }, Modifier.size(20.dp)) {
                                                Icon(Icons.Default.Delete, null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier.fillMaxWidth().weight(1f).background(Color(0xFF141414)).padding(vertical = 12.dp)
                                    ) {
                                        items(logEntries) { entry ->
                                            Text(
                                                entry.text,
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
                                                fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                                                color = if (entry.stream == "stderr") Color(0xFFF59E0B) else Color.White.copy(alpha = 0.95f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (isWaitingForRecovery) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.22f))
                                .pointerInput(Unit) { detectTapGestures { } },
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth(0.88f)
                                    .shadow(24.dp, RoundedCornerShape(28.dp), ambientColor = Color.Black.copy(alpha = 0.1f), spotColor = Color.Black.copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(28.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                            ) {
                                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                                    val tint = if (isTimedOut) Color(0xFFF59E0B) else Color(0xFF0A84FF)
                                    
                                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        Box(
                                            modifier = Modifier.size(58.dp).background(tint.copy(alpha = 0.12f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isTimedOut) {
                                                Icon(Icons.Default.Warning, null, tint = tint, modifier = Modifier.size(28.dp))
                                            } else {
                                                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = tint, strokeWidth = 3.dp)
                                            }
                                        }
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                if (isTimedOut) stringResource(R.string.maintenance_restart_waiting_timeout)
                                                else if (isSendingRequest) stringResource(R.string.maintenance_restart_sending_request)
                                                else stringResource(R.string.maintenance_restart_waiting),
                                                fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Color(0xFF1F2937)
                                            )
                                            val waitDetail = if (isTimedOut) stringResource(R.string.maintenance_restart_timeout_detail)
                                            else if (isSendingRequest) stringResource(R.string.maintenance_restart_sending_detail, selectedGateway?.displayName ?: "")
                                            else stringResource(R.string.maintenance_restart_waiting_detail, selectedGateway?.displayName ?: "")
                                            
                                            Text(waitDetail, fontSize = 14.sp, color = Color(0xFF6B7280), lineHeight = 20.sp)
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        val elapsedFmt = String.format("%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60)
                                        Box(modifier = Modifier.weight(1f)) {
                                            MetricChip(stringResource(R.string.maintenance_metric_elapsed), elapsedFmt)
                                        }
                                        val statusText = if (isTimedOut) stringResource(R.string.maintenance_restart_status_timeout)
                                        else if (isSendingRequest) stringResource(R.string.maintenance_restart_status_sending)
                                        else stringResource(R.string.maintenance_restart_status_checking)
                                        Box(modifier = Modifier.weight(1f)) {
                                            MetricChip(stringResource(R.string.maintenance_metric_status), statusText)
                                        }
                                    }

                                    val isLongRunning = elapsedSeconds > 30

                                    if (isTimedOut || isLongRunning) {
                                        if (isTimedOut) {
                                            Text(stringResource(R.string.maintenance_cancel_disclaimer), fontSize = 12.sp, color = Color(0xFF6B7280))
                                        }
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            if (isTimedOut) {
                                                Button(
                                                    onClick = {
                                                        scope.launch {
                                                            gatewayStore.checkSelectedGatewayRestartRecovery()
                                                        }
                                                    },
                                                    modifier = Modifier.fillMaxWidth().height(50.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A84FF)),
                                                    shape = RoundedCornerShape(14.dp)
                                                ) {
                                                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(stringResource(R.string.maintenance_retry), fontWeight = FontWeight.SemiBold)
                                                }
                                            }

                                            OutlinedButton(
                                                onClick = { gatewayStore.stopMaintenance() },
                                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                                shape = RoundedCornerShape(14.dp),
                                                border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                                            ) {
                                                Icon(Icons.Default.Close, null, tint = Color(0xFF6B7280), modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Text(stringResource(R.string.maintenance_cancel_wait), color = Color(0xFF6B7280), fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    } else {
                                        Text(
                                            if (isSendingRequest) stringResource(R.string.maintenance_waiting_sending_hint)
                                            else stringResource(R.string.maintenance_waiting_checking_hint),
                                            fontSize = 12.sp, color = Color(0xFF9CA3AF)
                                        )
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
