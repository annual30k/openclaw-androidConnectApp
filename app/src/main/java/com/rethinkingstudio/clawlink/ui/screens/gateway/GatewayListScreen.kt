package com.rethinkingstudio.clawlink.ui.screens.gateway

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.core.models.gateway.GatewaySummary
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
import com.rethinkingstudio.clawlink.core.state.chat.ChatStore
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import com.rethinkingstudio.clawlink.ui.components.ClawLinkAlertActionRole
import com.rethinkingstudio.clawlink.ui.components.ClawLinkAlertDialog
import com.rethinkingstudio.clawlink.ui.components.ClawLinkScaffold
import com.rethinkingstudio.clawlink.ui.screens.chat.components.GatewayItemCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GatewayListScreen(
    gatewayStore: GatewayStore,
    chatStore: ChatStore,
    onNavigateToPairing: () -> Unit,
    onBack: () -> Unit
) {
    val state by gatewayStore.state.collectAsState()
    val chatState by chatStore.state.collectAsState()
    val scope = rememberCoroutineScope()
    var expandedGatewayId by remember { mutableStateOf<String?>(null) }
    var pendingUnpairGateway by remember { mutableStateOf<GatewaySummary?>(null) }

    ClawLinkScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.gateway_list_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_action_back))
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToPairing) {
                        Icon(Icons.Default.Add, stringResource(R.string.settings_gateway_action_add))
                    }
                    IconButton(
                        onClick = { scope.launch { gatewayStore.loadGateways() } },
                        enabled = !state.isLoading
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, stringResource(R.string.refresh))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
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
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (state.restartingGatewayId != null) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.tertiary)
                                Column {
                                    Text(choose("Maintenance in progress", "维护进行中"), fontWeight = FontWeight.Bold)
                                    Text(
                                        choose("Gateway switching and session changes may be temporarily unavailable.", "网关切换和会话变更可能暂时不可用。"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                state.errorMessage?.let { message ->
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                                Text(message, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                items(state.gateways, key = { it.id }) { gateway ->
                    GatewayRow(
                        gateway = gateway,
                        appRelayStatus = state.appRelayStatus,
                        isSelected = gateway.id == state.selectedGatewayId,
                        sessions = if (expandedGatewayId == gateway.id) chatState.sessions else emptyList(),
                        currentSessionKey = if (chatState.currentGatewayId == gateway.id) chatState.currentSessionKey else "main",
                        isSessionExpanded = expandedGatewayId == gateway.id,
                        onToggleSessions = {
                            val shouldExpand = expandedGatewayId != gateway.id
                            expandedGatewayId = if (shouldExpand) gateway.id else null
                            if (shouldExpand && chatState.currentGatewayId != gateway.id) {
                                scope.launch { chatStore.loadSessions(gateway.id) }
                            }
                        },
                        onRefreshSessions = {
                            scope.launch { chatStore.loadSessions(gateway.id) }
                        },
                        onSelectSession = { session ->
                            scope.launch {
                                gatewayStore.selectGateway(gateway.id)
                                if (chatState.currentGatewayId != gateway.id) {
                                    chatStore.loadSessions(gateway.id)
                                }
                                chatStore.selectSession(session.sessionKey)
                                onBack()
                            }
                        },
                        onSelect = {
                            gatewayStore.selectGateway(gateway.id)
                            onBack()
                        },
                        onUnpair = { pendingUnpairGateway = gateway }
                    )
                }
            }
        }
    }

    pendingUnpairGateway?.let { gateway ->
        ClawLinkAlertDialog(
            onDismissRequest = { pendingUnpairGateway = null },
            title = choose("Unpair ${gateway.displayName}?", "解绑「${gateway.displayName}」？"),
            message = stringResource(R.string.gateway_unpair_session_message),
            confirmText = stringResource(R.string.settings_gateway_unpair),
            confirmRole = ClawLinkAlertActionRole.Destructive,
            onConfirm = {
                val target = gateway
                pendingUnpairGateway = null
                scope.launch { gatewayStore.unpairGateway(target.id) }
            },
            dismissText = stringResource(R.string.common_action_cancel),
            onDismissAction = { pendingUnpairGateway = null }
        )
    }
}

@Composable
private fun GatewayRow(
    gateway: GatewaySummary,
    appRelayStatus: com.rethinkingstudio.clawlink.core.models.gateway.AggregateStatus,
    isSelected: Boolean,
    sessions: List<com.rethinkingstudio.clawlink.core.models.chat.ChatSessionItem>,
    currentSessionKey: String,
    isSessionExpanded: Boolean,
    onToggleSessions: () -> Unit,
    onRefreshSessions: () -> Unit,
    onSelectSession: (com.rethinkingstudio.clawlink.core.models.chat.ChatSessionItem) -> Unit,
    onSelect: () -> Unit,
    onUnpair: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GatewayItemCard(
            gateway = gateway,
            appRelayStatus = appRelayStatus,
            selected = isSelected,
            sessions = sessions,
            currentSessionKey = currentSessionKey,
            isSessionExpanded = isSessionExpanded,
            onToggleSessionExpanded = onToggleSessions,
            onRefreshSessions = onRefreshSessions,
            onSelectSession = onSelectSession,
            onClick = onSelect
        )
        TextButton(
            onClick = onUnpair,
            modifier = Modifier.align(Alignment.End),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Text(stringResource(R.string.settings_gateway_unpair), fontWeight = FontWeight.Bold)
        }
    }
}
