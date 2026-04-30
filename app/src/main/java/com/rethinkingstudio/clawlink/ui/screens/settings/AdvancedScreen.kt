package com.rethinkingstudio.clawlink.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.core.state.UserPreferencesStore
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import com.rethinkingstudio.clawlink.ui.components.ClawLinkCard
import com.rethinkingstudio.clawlink.ui.components.ClawLinkScaffold
import com.rethinkingstudio.clawlink.ui.components.ClawLinkSectionHeader
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedScreen(
    gatewayStore: GatewayStore,
    prefsStore: UserPreferencesStore,
    onBack: () -> Unit,
    onNavigateToBackups: () -> Unit,
    onNavigateToLogs: () -> Unit,
    onNavigateToDoctorFix: () -> Unit
) {
    val gatewayState by gatewayStore.state.collectAsState()
    val showsTools by prefsStore.showsToolInvocationProcess.collectAsState()
    val scope = rememberCoroutineScope()
    
    var showRestartConfirm by remember { mutableStateOf(false) }
    var showRemoteRestartConfirm by remember { mutableStateOf(false) }

    ClawLinkScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.advanced_title)) },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Section: Gateway Maintenance
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ClawLinkSectionHeader(
                    title = stringResource(R.string.settings_section_maintenance),
                    subtitle = stringResource(R.string.settings_section_maintenance_subtitle)
                )
                ClawLinkCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Surface(
                            onClick = { showRestartConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                            color = androidx.compose.ui.graphics.Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Default.Refresh, null, tint = MaterialTheme.colorScheme.primary)
                                Column {
                                    Text(stringResource(R.string.advanced_restart_gateway), fontWeight = FontWeight.SemiBold)
                                    Text(stringResource(R.string.advanced_restart_gateway_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                        Surface(
                            onClick = { showRemoteRestartConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                            color = androidx.compose.ui.graphics.Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Default.PowerSettingsNew, null, tint = MaterialTheme.colorScheme.error)
                                Column {
                                    Text(stringResource(R.string.advanced_remote_restart), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                                    Text(stringResource(R.string.advanced_remote_restart_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            // Section: OpenClaw Repair
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ClawLinkSectionHeader(
                    title = stringResource(R.string.settings_section_repair),
                    subtitle = stringResource(R.string.settings_section_repair_subtitle)
                )
                ClawLinkCard(modifier = Modifier.fillMaxWidth()) {
                    SettingsNavigationRow(
                        icon = Icons.Default.Build,
                        title = stringResource(R.string.advanced_doctor_fix),
                        subtitle = stringResource(R.string.advanced_doctor_fix_detail),
                        onClick = onNavigateToDoctorFix
                    )
                }
            }

            // Section: Features
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ClawLinkSectionHeader(
                    title = stringResource(R.string.settings_section_features),
                    subtitle = stringResource(R.string.advanced_section_features_subtitle)
                )
                ClawLinkCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Visibility, null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.advanced_show_tools), fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.advanced_show_tools_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = showsTools,
                            onCheckedChange = { prefsStore.setShowsToolInvocationProcess(it) }
                        )
                    }
                }
            }

            // Section: Data Management
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ClawLinkSectionHeader(
                    title = stringResource(R.string.settings_section_data),
                    subtitle = stringResource(R.string.settings_section_data_subtitle)
                )
                ClawLinkCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        SettingsNavigationRow(
                            icon = Icons.Default.Backup,
                            title = stringResource(R.string.settings_row_backups),
                            subtitle = stringResource(R.string.advanced_backups_detail),
                            onClick = onNavigateToBackups
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                        SettingsNavigationRow(
                            icon = Icons.Default.ListAlt,
                            title = stringResource(R.string.settings_row_logs),
                            subtitle = stringResource(R.string.advanced_logs_detail),
                            onClick = onNavigateToLogs
                        )
                    }
                }
            }
        }
    }

    if (showRestartConfirm) {
        AlertDialog(
            onDismissRequest = { showRestartConfirm = false },
            title = { Text(stringResource(R.string.advanced_restart_gateway)) },
            text = { Text("Are you sure you want to restart the gateway software?") },
            confirmButton = {
                TextButton(onClick = {
                    showRestartConfirm = false
                    gatewayState.selectedGatewayId?.let { id ->
                        scope.launch { gatewayStore.restartGateway(id) }
                    }
                }) {
                    Text(stringResource(R.string.common_action_restart))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestartConfirm = false }) {
                    Text(stringResource(R.string.common_action_cancel))
                }
            }
        )
    }

    if (showRemoteRestartConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoteRestartConfirm = false },
            title = { Text(stringResource(R.string.advanced_remote_restart)) },
            text = { Text("This will attempt to restart the host machine. Are you sure?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRemoteRestartConfirm = false
                        gatewayState.selectedGatewayId?.let { id ->
                            scope.launch { gatewayStore.remoteRestartGateway(id) }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.common_action_restart))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoteRestartConfirm = false }) {
                    Text(stringResource(R.string.common_action_cancel))
                }
            }
        )
    }
}

@Composable
private fun SettingsNavigationRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = androidx.compose.ui.graphics.Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
