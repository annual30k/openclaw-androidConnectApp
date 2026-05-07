package com.rethinkingstudio.clawlink.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.core.domain.NotificationPort
import com.rethinkingstudio.clawlink.core.network.transport.RelayWebSocketClient
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
    onNavigateToPairing: () -> Unit,
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
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF8F9FB))
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8F9FB))
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Section: Gateway
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    ClawLinkSectionHeader(
                        title = stringResource(R.string.settings_section_gateway),
                        subtitle = stringResource(R.string.settings_section_gateway_subtitle),
                        modifier = Modifier.weight(1f)
                    )
                    
                    val hasBoundGateway = gatewayState.gateways.isNotEmpty()
                    val actionTitle = if (hasBoundGateway) {
                        stringResource(R.string.settings_gateway_action_add)
                    } else {
                        stringResource(R.string.settings_gateway_action_bind)
                    }

                    Surface(
                        onClick = onNavigateToPairing,
                        shape = CircleShape,
                        color = Color(0xFF3B82F6).copy(alpha = 0.12f),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = Color(0xFF3B82F6),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                actionTitle,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF3B82F6)
                            )
                        }
                    }
                }

                gatewayState.selectedGateway?.let { gw ->
                    GatewayStatusCard(
                        gateway = gw,
                        appRelayStatus = gatewayState.appRelayStatus,
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
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFF3F4F6),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Computer, null, tint = Color(0xFF6B7280))
                                }
                            }
                            Column {
                                Text(stringResource(R.string.settings_gateway_fallback_name), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Text(stringResource(R.string.settings_gateway_action_bind), style = MaterialTheme.typography.bodySmall, color = Color(0xFF3B82F6), fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color(0xFF9CA3AF))
                        }
                    }
                }
            }

            // Section: Features
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        HorizontalDivider(modifier = Modifier.padding(start = 64.dp), color = Color(0xFFF3F4F6))
                        SettingsNavigationRow(
                            icon = Icons.Default.Task,
                            title = stringResource(R.string.settings_row_tasks),
                            onClick = onNavigateToTasks
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 64.dp), color = Color(0xFFF3F4F6))
                        SettingsNavigationRow(
                            icon = Icons.Default.Extension,
                            title = stringResource(R.string.settings_row_skills),
                            onClick = onNavigateToSkills
                        )
                    }
                }
            }

            // Section: AI
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        HorizontalDivider(modifier = Modifier.padding(start = 64.dp), color = Color(0xFFF3F4F6))
                        SettingsNavigationRow(
                            icon = Icons.Default.ChatBubble,
                            title = stringResource(R.string.settings_row_sessions),
                            onClick = onNavigateToSessions
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 64.dp), color = Color(0xFFF3F4F6))
                        SettingsNavigationRow(
                            icon = Icons.Default.Mic,
                            title = stringResource(R.string.settings_row_voice_setup),
                            onClick = onNavigateToVoiceSetup
                        )
                    }
                }
            }

            // Section: Management
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        HorizontalDivider(modifier = Modifier.padding(start = 64.dp), color = Color(0xFFF3F4F6))
                        SettingsNavigationRow(
                            icon = Icons.Default.Help,
                            title = stringResource(R.string.settings_row_usage_guide),
                            onClick = onNavigateToHelp
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 64.dp), color = Color(0xFFF3F4F6))
                        SettingsNavigationRow(
                            icon = Icons.Default.Language,
                            title = stringResource(R.string.settings_row_language),
                            value = LanguageManager.getCurrentPreference().displayName,
                            onClick = onNavigateToLanguage
                        )
                    }
                }
            }

            // Section: Account
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFF3F4F6),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Logout, null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                    }
                                }
                                Text(stringResource(R.string.settings_account_sign_out), fontWeight = FontWeight.SemiBold, color = Color(0xFFEF4444))
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(start = 64.dp), color = Color(0xFFF3F4F6))
                        Surface(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                            color = Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFF3F4F6),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                    }
                                }
                                Text(
                                    stringResource(R.string.settings_account_delete),
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFEF4444)
                                )
                            }
                        }
                    }
                }
            }

            // Section: About
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

            Spacer(modifier = Modifier.height(20.dp))

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "ClawLink v${com.rethinkingstudio.clawlink.BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9CA3AF)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
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
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF3B82F6).copy(alpha = 0.1f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = Color(0xFF3B82F6), modifier = Modifier.size(18.dp))
                }
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp), fontWeight = FontWeight.Bold, color = Color(0xFF1F2937))
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Color(0xFF6B7280))
                }
            }
            if (value != null) {
                Text(value, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF6B7280), modifier = Modifier.padding(end = 4.dp))
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                tint = Color(0xFFD1D5DB),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

