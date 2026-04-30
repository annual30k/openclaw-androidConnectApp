package com.rethinkingstudio.clawlink.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.core.models.gateway.AggregateStatus
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import com.rethinkingstudio.clawlink.ui.components.ClawLinkCard
import com.rethinkingstudio.clawlink.ui.components.ClawLinkScaffold
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
    
    // In a real app, these would be in a ViewModel or Store
    var isExecuting by remember { mutableStateOf(false) }
    var logEntries by remember { mutableStateOf(listOf<String>()) }
    val listState = rememberLazyListState()

    LaunchedEffect(logEntries.size) {
        if (logEntries.isNotEmpty()) {
            listState.animateScrollToItem(logEntries.size - 1)
        }
    }

    ClawLinkScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.advanced_doctor_fix)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_action_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Control Card
            ClawLinkCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Build, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        Text(
                            selectedGateway?.displayName ?: "Unknown Gateway",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        StatusBadge(status = selectedGateway?.aggregateStatus)
                    }

                    if (!isExecuting && logEntries.isEmpty()) {
                        HorizontalDivider(modifier = Modifier.alpha(0.4f))
                        Text(
                            stringResource(R.string.advanced_doctor_fix_detail),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                isExecuting = true
                                logEntries = listOf("Starting OpenClaw Doctor Fix...", "Connecting to gateway...", "Analyzing system state...")
                                scope.launch {
                                    // Simulate execution for now
                                    kotlinx.coroutines.delay(2000)
                                    logEntries = logEntries + "Repairing configuration files..."
                                    kotlinx.coroutines.delay(1500)
                                    logEntries = logEntries + "Updating core components..."
                                    kotlinx.coroutines.delay(2000)
                                    logEntries = logEntries + "Verification successful."
                                    logEntries = logEntries + "Doctor Fix completed."
                                    isExecuting = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF57C00)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.advanced_doctor_fix_execute), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Terminal Card
            if (isExecuting || logEntries.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF151515))
                        .padding(bottom = 8.dp)
                ) {
                    // Terminal Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.15f))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.Red.copy(alpha = 0.8f)))
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.Yellow.copy(alpha = 0.8f)))
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.Green.copy(alpha = 0.8f)))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "TERMINAL",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (isExecuting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            IconButton(onClick = { logEntries = emptyList() }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Terminal Content
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(logEntries) { entry ->
                            Text(
                                entry,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp
                                ),
                                color = Color.White.copy(alpha = 0.95f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: AggregateStatus?) {
    val color = when (status) {
        AggregateStatus.online -> Color(0xFF4CAF50)
        else -> Color(0xFFF57C00)
    }
    Row(
        modifier = Modifier
            .clip(CapsuleShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(
            status?.name ?: "Unknown",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

private val CapsuleShape = androidx.compose.foundation.shape.RoundedCornerShape(50)
