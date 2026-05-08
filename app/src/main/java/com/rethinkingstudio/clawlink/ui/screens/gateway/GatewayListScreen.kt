package com.rethinkingstudio.clawlink.ui.screens.gateway

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.core.models.gateway.AggregateStatus
import com.rethinkingstudio.clawlink.core.models.gateway.GatewaySummary
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import com.rethinkingstudio.clawlink.ui.components.ClawLinkScaffold
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GatewayListScreen(
    gatewayStore: GatewayStore,
    onNavigateToPairing: () -> Unit,
    onBack: () -> Unit
) {
    val state by gatewayStore.state.collectAsState()
    val scope = rememberCoroutineScope()

    ClawLinkScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.gateways_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToPairing) {
                        Icon(Icons.Default.Add, stringResource(R.string.settings_gateway_action_add))
                    }
                    IconButton(onClick = {
                        scope.launch { gatewayStore.loadGateways() }
                    }) {
                        Icon(Icons.Default.Refresh, stringResource(R.string.refresh))
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (state.gateways.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.gateways_empty), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.gateways_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { scope.launch { gatewayStore.loadGateways() } }) {
                        Text(stringResource(R.string.refresh))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onNavigateToPairing) {
                        Text(stringResource(R.string.settings_gateway_action_add))
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.gateways, key = { it.id }) { gateway ->
                    GatewayRow(
                        gateway = gateway,
                        appRelayStatus = state.appRelayStatus,
                        isSelected = gateway.id == state.selectedGatewayId,
                        onSelect = {
                            gatewayStore.selectGateway(gateway.id)
                            onBack()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun GatewayRow(
    gateway: GatewaySummary,
    appRelayStatus: AggregateStatus,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        colors = if (isSelected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    gateway.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "${gateway.platform} · ${gateway.currentModel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val effectiveStatus = if (appRelayStatus == AggregateStatus.online) {
                        gateway.aggregateStatus
                    } else {
                        appRelayStatus
                    }

                    Text(
                        gateway.statusIcon,
                        color = when (effectiveStatus) {
                            AggregateStatus.online -> MaterialTheme.colorScheme.primary
                            AggregateStatus.offline -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.tertiary
                        }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        when (effectiveStatus) {
                            AggregateStatus.online -> stringResource(R.string.gateway_status_online)
                            AggregateStatus.offline -> stringResource(R.string.gateway_status_offline)
                            AggregateStatus.connecting -> stringResource(R.string.gateway_aggregate_connecting)
                            AggregateStatus.partial -> stringResource(R.string.gateway_aggregate_partial)
                        },
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        gateway.contextUsage,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    stringResource(R.string.models_selected),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
