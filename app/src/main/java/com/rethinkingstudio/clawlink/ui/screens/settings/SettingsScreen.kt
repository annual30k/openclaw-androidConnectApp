package com.rethinkingstudio.clawlink.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Task
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.core.domain.NotificationPort
import com.rethinkingstudio.clawlink.core.network.transport.RelayWebSocketClient
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ExperimentalMaterial3Api
import com.rethinkingstudio.clawlink.core.state.LanguageManager
import com.rethinkingstudio.clawlink.core.state.auth.AuthStore
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import com.rethinkingstudio.clawlink.ui.components.ClawLinkCard
import com.rethinkingstudio.clawlink.ui.components.ClawLinkScaffold
import com.rethinkingstudio.clawlink.ui.components.ClawLinkSectionHeader
import com.rethinkingstudio.clawlink.ui.screens.settings.components.GatewayStatusCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    authStore: AuthStore,
    gatewayStore: GatewayStore,
    wsClient: RelayWebSocketClient,
    notificationPort: NotificationPort,
    onBack: () -> Unit,
    onNavigateToGateways: () -> Unit,
    onNavigateToModels: () -> Unit,
    onNavigateToSkills: () -> Unit,
    onNavigateToTasks: () -> Unit,
    onNavigateToAdvanced: () -> Unit,
    onNavigateToHelp: () -> Unit,
    onNavigateToOffice: () -> Unit,
    onNavigateToSessions: () -> Unit,
    onNavigateToVoiceSetup: () -> Unit,
    onNavigateToLanguage: () -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    val authState by authStore.state.collectAsState()
    val gatewayState by gatewayStore.state.collectAsState()
    val scope = rememberCoroutineScope()

    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    ClawLinkScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Section: Gateway
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ClawLinkSectionHeader(
                    title = stringResource(R.string.settings_section_gateway),
                    subtitle = stringResource(R.string.settings_section_gateway_subtitle)
                )
                gatewayState.selectedGateway?.let { gw ->
                    GatewayStatusCard(
                        gateway = gw,
                        onEditName = { newName ->
                            scope.launch {
                                gatewayStore.updateGatewayName(gw.id, newName)
                            }
                        },
                        onClick = onNavigateToGateways
                    )
                } ?: run {
                    ClawLinkCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToGateways() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Computer, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Column {
                                Text(stringResource(R.string.settings_gateway_fallback_name), fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.settings_gateway_action_bind), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            // Section: Features
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ClawLinkSectionHeader(
                    title = stringResource(R.string.settings_section_features),
                    subtitle = stringResource(R.string.settings_section_features_subtitle)
                )
                ClawLinkCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        SettingsNavigationRow(
                            icon = Icons.Default.Dashboard,
                            title = stringResource(R.string.settings_row_office),
                            onClick = onNavigateToOffice
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                        SettingsNavigationRow(
                            icon = Icons.Default.Task,
                            title = stringResource(R.string.settings_row_tasks),
                            onClick = onNavigateToTasks
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                        SettingsNavigationRow(
                            icon = Icons.Default.Extension,
                            title = stringResource(R.string.settings_row_skills),
                            onClick = onNavigateToSkills
                        )
                    }
                }
            }

            // Section: AI
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ClawLinkSectionHeader(
                    title = stringResource(R.string.settings_section_ai),
                    subtitle = stringResource(R.string.settings_section_ai_subtitle)
                )
                ClawLinkCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        SettingsNavigationRow(
                            icon = Icons.Default.Tune,
                            title = stringResource(R.string.settings_row_models),
                            onClick = onNavigateToModels
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                        SettingsNavigationRow(
                            icon = Icons.Default.ChatBubble,
                            title = stringResource(R.string.settings_row_sessions),
                            onClick = onNavigateToSessions
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                        SettingsNavigationRow(
                            icon = Icons.Default.Mic,
                            title = stringResource(R.string.settings_row_voice_setup),
                            onClick = onNavigateToVoiceSetup
                        )
                    }
                }
            }

            // Section: Management
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ClawLinkSectionHeader(
                    title = stringResource(R.string.settings_section_management),
                    subtitle = stringResource(R.string.settings_section_management_subtitle)
                )
                ClawLinkCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        SettingsNavigationRow(
                            icon = Icons.Default.Settings,
                            title = stringResource(R.string.settings_row_advanced),
                            onClick = onNavigateToAdvanced
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                        SettingsNavigationRow(
                            icon = Icons.Default.Help,
                            title = stringResource(R.string.settings_row_usage_guide),
                            onClick = onNavigateToHelp
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                        SettingsNavigationRow(
                            icon = Icons.Default.Language,
                            title = stringResource(R.string.settings_row_language),
                            value = LanguageManager.getCurrentPreference().displayName,
                            onClick = onNavigateToLanguage
                        )
                    }
                }
            }

            // Section: About
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ClawLinkSectionHeader(
                    title = stringResource(R.string.settings_section_about),
                    subtitle = stringResource(R.string.settings_section_about_subtitle)
                )
                ClawLinkCard(modifier = Modifier.fillMaxWidth()) {
                    SettingsNavigationRow(
                        icon = Icons.Default.Info,
                        title = stringResource(R.string.settings_row_check_update),
                        subtitle = stringResource(R.string.settings_row_check_update_detail),
                        onClick = { /* Open App Store or External URL */ }
                    )
                }
            }

            // Section: Account
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ClawLinkSectionHeader(
                    title = stringResource(R.string.settings_section_account),
                    subtitle = stringResource(R.string.settings_section_account_subtitle)
                )
                ClawLinkCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Surface(
                            onClick = { showLogoutConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                            color = Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Default.Logout, null, tint = MaterialTheme.colorScheme.primary)
                                Text(stringResource(R.string.settings_account_sign_out), fontWeight = FontWeight.SemiBold)
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                        Surface(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                            color = Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                                Text(
                                    stringResource(R.string.settings_account_delete),
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "ClawLink v1.0.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text(stringResource(R.string.settings_account_sign_out)) },
            text = { Text("Are you sure you want to sign out?") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    scope.launch {
                        wsClient.disconnect()
                        notificationPort.cancelAll()
                        authStore.logout()
                        onLogout()
                    }
                }) {
                    Text(stringResource(R.string.settings_account_sign_out))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text(stringResource(R.string.common_action_cancel))
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.alert_delete_account_title)) },
            text = { Text(stringResource(R.string.alert_delete_account_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteAccount()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.settings_account_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
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
    value: String? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent
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
            if (value != null) {
                Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

