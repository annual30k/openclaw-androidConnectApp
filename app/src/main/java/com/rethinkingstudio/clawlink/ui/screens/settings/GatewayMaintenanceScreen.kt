package com.rethinkingstudio.clawlink.ui.screens.settings

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.core.models.gateway.AggregateStatus
import com.rethinkingstudio.clawlink.core.network.RelayAPIClient
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import com.rethinkingstudio.clawlink.ui.components.ClawLinkScaffold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class MaintenanceMode { RESTART, REMOTE_RESTART }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GatewayMaintenanceScreen(
    mode: MaintenanceMode,
    gatewayStore: GatewayStore,
    apiClient: RelayAPIClient,
    onBack: () -> Unit
) {
    val gatewayState by gatewayStore.state.collectAsState()
    val scope = rememberCoroutineScope()
    val selectedGateway = gatewayState.selectedGateway

    var isExecuting by remember { mutableStateOf(false) }
    var isSendingRequest by remember { mutableStateOf(false) }
    var logEntries by remember { mutableStateOf<List<DoctorFixLogEntry>>(emptyList()) }
    var isWaitingForRecovery by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableStateOf(0) }
    var isTimedOut by remember { mutableStateOf(false) }

    val isRemote = mode == MaintenanceMode.REMOTE_RESTART
    val title = if (isRemote) stringResource(R.string.maintenance_remote_restart) else stringResource(R.string.maintenance_restart_gateway)
    val detail = if (isRemote) stringResource(R.string.maintenance_remote_restart_detail) else stringResource(R.string.maintenance_online_restart_detail)
    val icon = if (isRemote) Icons.Default.FlashOn else Icons.Default.RestartAlt

    val hasSession = gatewayState.gateways.isNotEmpty()
    val isLocked = gatewayState.restartingGatewayId != null
    val canManage = hasSession && selectedGateway != null && !isLocked && !isExecuting && !isWaitingForRecovery

    val accessHint = when {
        !hasSession -> stringResource(R.string.maintenance_hint_no_session)
        isLocked -> stringResource(R.string.advanced_doctor_fix_hint_locked)
        else -> null
    }

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
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isWaitingForRecovery) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF8F9FB))
            )
        },
        content = { padding ->
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFF8F9FB))
                        .padding(padding)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Control Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Surface(shape = CircleShape, color = Color(0xFF3B82F6).copy(alpha = 0.12f), modifier = Modifier.size(30.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Memory, null, tint = Color(0xFF3B82F6), modifier = Modifier.size(14.dp))
                                    }
                                }
                                Text(
                                    selectedGateway?.displayName ?: "--",
                                    fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1F2937),
                                    modifier = Modifier.weight(1f)
                                )
                                val statusColor = when (selectedGateway?.aggregateStatus) {
                                    AggregateStatus.online -> Color(0xFF22C55E)
                                    AggregateStatus.partial -> Color(0xFFF59E0B)
                                    else -> Color(0xFFEF4444)
                                }
                                val statusText = when (selectedGateway?.aggregateStatus) {
                                    AggregateStatus.online -> stringResource(R.string.gateway_aggregate_online)
                                    AggregateStatus.connecting -> stringResource(R.string.gateway_aggregate_connecting)
                                    AggregateStatus.partial -> stringResource(R.string.gateway_aggregate_partial)
                                    AggregateStatus.offline -> stringResource(R.string.gateway_aggregate_offline)
                                    null -> stringResource(R.string.advanced_status_unknown)
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.background(statusColor.copy(alpha = 0.12f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Box(Modifier.size(6.dp).background(statusColor, CircleShape))
                                    Text(statusText, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = statusColor)
                                }
                            }

                            if (!isExecuting && logEntries.isEmpty() && !isWaitingForRecovery) {
                                HorizontalDivider(color = Color(0xFFF3F4F6))
                                Text(detail, fontSize = 11.sp, color = Color(0xFF6B7280))
                                if (accessHint != null) {
                                    Text(accessHint, fontSize = 12.sp, color = Color(0xFFF59E0B), fontWeight = FontWeight.Medium)
                                }
                                Button(
                                    onClick = {
                                        val gatewayId = selectedGateway?.id ?: return@Button
                                        isExecuting = true
                                        isSendingRequest = true
                                        logEntries = emptyList()
                                        scope.launch {
                                            try {
                                                if (isRemote) {
                                                    apiClient.remoteRestartGateway(gatewayId)
                                                } else {
                                                    apiClient.restartGateway(gatewayId)
                                                }
                                                isSendingRequest = false
                                                logEntries = listOf(
                                                    DoctorFixLogEntry(text = "Restart command sent successfully.", isError = false),
                                                    DoctorFixLogEntry(text = "Waiting for gateway to come back online...", isError = false)
                                                )
                                                isExecuting = false
                                                isWaitingForRecovery = true
                                            } catch (e: Exception) {
                                                isSendingRequest = false
                                                logEntries = listOf(DoctorFixLogEntry(text = "Error: ${e.message}", isError = true))
                                                isExecuting = false
                                            }
                                        }
                                    },
                                    enabled = canManage,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = if (isRemote) Color(0xFF6366F1) else Color(0xFF3B82F6)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(icon, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(title, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Suggestion
                    if (!isExecuting && logEntries.isEmpty() && !isWaitingForRecovery) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F9FF))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(stringResource(R.string.maintenance_suggestion_title), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1F2937))
                                Text(stringResource(R.string.maintenance_suggestion_body), fontSize = 11.sp, color = Color(0xFF6B7280))
                            }
                        }
                    }

                    // Terminal Console
                    if (isExecuting || logEntries.isNotEmpty()) {
                        Column(modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(20.dp))) {
                            Row(
                                modifier = Modifier.fillMaxWidth().background(Color(0xFF1a1a2e)).padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(Modifier.size(8.dp).background(Color.Red.copy(alpha = 0.8f), CircleShape))
                                    Box(Modifier.size(8.dp).background(Color.Yellow.copy(alpha = 0.8f), CircleShape))
                                    Box(Modifier.size(8.dp).background(Color.Green.copy(alpha = 0.8f), CircleShape))
                                }
                                Spacer(Modifier.width(8.dp))
                                Text("TERMINAL", fontSize = 10.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, color = Color(0xFF9CA3AF))
                                Spacer(Modifier.weight(1f))
                                if (isExecuting) {
                                    CircularProgressIndicator(Modifier.size(14.dp), color = Color(0xFF9CA3AF), strokeWidth = 2.dp)
                                }
                            }
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxWidth().weight(1f).background(Color(0xFF141414)).padding(vertical = 12.dp, horizontal = 14.dp)
                            ) {
                                items(logEntries) { entry ->
                                    Text(entry.text, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                                        color = if (entry.isError) Color(0xFFFFA500) else Color.White.copy(alpha = 0.95f),
                                        modifier = Modifier.padding(vertical = 2.dp))
                                }
                            }
                        }
                    }
                }

                // Waiting for Recovery Overlay
                if (isWaitingForRecovery) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.widthIn(max = 360.dp).padding(horizontal = 20.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                val tint = if (isTimedOut) Color(0xFFF59E0B) else Color(0xFF3B82F6)
                                val waitTitle = if (isTimedOut) stringResource(R.string.maintenance_restart_waiting_timeout)
                                    else if (isSendingRequest) stringResource(R.string.maintenance_restart_sending_request)
                                    else stringResource(R.string.maintenance_restart_waiting)
                                val waitDetail = if (isTimedOut) stringResource(R.string.maintenance_restart_timeout_detail)
                                    else if (isSendingRequest) stringResource(R.string.maintenance_restart_sending_detail, selectedGateway?.displayName ?: "")
                                    else stringResource(R.string.maintenance_restart_waiting_detail, selectedGateway?.displayName ?: "")

                                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Box(
                                        modifier = Modifier.size(58.dp).background(tint.copy(alpha = 0.14f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isTimedOut) {
                                            Icon(Icons.Default.Warning, null, tint = tint, modifier = Modifier.size(23.dp))
                                        } else {
                                            CircularProgressIndicator(color = tint)
                                        }
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                                        Text(waitTitle, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = Color(0xFF1F2937))
                                        Text(waitDetail, fontSize = 13.sp, color = Color(0xFF6B7280))
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    val elapsedFmt = String.format("%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60)
                                    MetricChip(stringResource(R.string.maintenance_metric_elapsed), elapsedFmt)
                                    val statusText = if (isTimedOut) stringResource(R.string.maintenance_restart_status_timeout)
                                        else if (isSendingRequest) stringResource(R.string.maintenance_restart_status_sending)
                                        else stringResource(R.string.maintenance_restart_status_checking)
                                    MetricChip(stringResource(R.string.maintenance_metric_status), statusText)
                                }

                                if (isTimedOut) {
                                    Text(stringResource(R.string.maintenance_cancel_disclaimer), fontSize = 12.sp, color = Color(0xFF6B7280))
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Button(
                                            onClick = {
                                                isTimedOut = false
                                                scope.launch {
                                                    delay(3000)
                                                    isWaitingForRecovery = false
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                                        ) {
                                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(R.string.maintenance_retry), fontWeight = FontWeight.SemiBold)
                                        }
                                        OutlinedButton(
                                            onClick = { isWaitingForRecovery = false },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(R.string.maintenance_cancel_wait), fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                } else {
                                    Text(
                                        if (isSendingRequest) stringResource(R.string.maintenance_waiting_sending_hint)
                                        else stringResource(R.string.maintenance_waiting_checking_hint),
                                        fontSize = 12.sp, color = Color(0xFF6B7280)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun MetricChip(title: String, value: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF3F4F6),
        modifier = Modifier.widthIn(min = 80.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B7280))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1F2937), fontFamily = FontFamily.Monospace)
        }
    }
}
