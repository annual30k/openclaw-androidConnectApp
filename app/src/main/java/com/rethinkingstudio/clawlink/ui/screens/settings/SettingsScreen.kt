package com.rethinkingstudio.clawlink.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.core.domain.NotificationPort
import com.rethinkingstudio.clawlink.core.network.transport.RelayWebSocketClient
import com.rethinkingstudio.clawlink.core.state.LanguageManager
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
import com.rethinkingstudio.clawlink.core.state.auth.AuthStore
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import com.rethinkingstudio.clawlink.ui.components.ClawLinkScaffold
import com.rethinkingstudio.clawlink.ui.components.ClawLinkSectionHeader
import com.rethinkingstudio.clawlink.ui.screens.settings.components.EmptyGatewayStatusCard
import com.rethinkingstudio.clawlink.ui.screens.settings.components.GatewayStatusCard
import kotlinx.coroutines.launch

private val SettingsGroupShape = RoundedCornerShape(24.dp)
private val SettingsDividerColor = Color(0xFFE5E7EB)

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
) {
    val authState by authStore.state.collectAsState()
    val gatewayState by gatewayStore.state.collectAsState()
    val scope = rememberCoroutineScope()
    val isChatChainReady = gatewayState.isSelectedGatewayChatChainReady

    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showChangePassword by remember { mutableStateOf(false) }

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
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
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
                            gatewayStore.updateGatewayName(gw.id, newName)
                        },
                        onClick = onNavigateToGateways
                    )
                } ?: run {
                    EmptyGatewayStatusCard(onClick = onNavigateToGateways)
                }
            }

            // Section: Features
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ClawLinkSectionHeader(
                    title = stringResource(R.string.settings_section_features),
                    subtitle = stringResource(R.string.settings_section_features_subtitle)
                )
                SettingsGroup(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        SettingsNavigationRow(
                            icon = Icons.Default.Dashboard,
                            title = stringResource(R.string.settings_row_office),
                            onClick = onNavigateToOffice
                        )
                        SettingsDivider()
                        SettingsNavigationRow(
                            icon = Icons.Default.Task,
                            title = stringResource(R.string.settings_row_tasks),
                            enabled = isChatChainReady,
                            onClick = onNavigateToTasks
                        )
                        SettingsDivider()
                        SettingsNavigationRow(
                            icon = Icons.Default.Extension,
                            title = stringResource(R.string.settings_row_skills),
                            enabled = isChatChainReady,
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
                SettingsGroup(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        SettingsNavigationRow(
                            icon = Icons.Default.Tune,
                            title = stringResource(R.string.settings_row_models),
                            enabled = isChatChainReady,
                            onClick = onNavigateToModels
                        )
                        SettingsDivider()
                        SettingsNavigationRow(
                            icon = Icons.Default.ChatBubble,
                            title = stringResource(R.string.settings_row_sessions),
                            enabled = isChatChainReady,
                            onClick = onNavigateToSessions
                        )
                        SettingsDivider()
                        SettingsNavigationRow(
                            icon = Icons.Default.Mic,
                            title = stringResource(R.string.settings_row_voice_setup),
                            enabled = isChatChainReady,
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
                SettingsGroup(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        SettingsNavigationRow(
                            icon = Icons.Default.Language,
                            title = stringResource(R.string.settings_row_language),
                            value = LanguageManager.getCurrentPreference().displayName,
                            onClick = onNavigateToLanguage
                        )
                        SettingsDivider()
                        SettingsNavigationRow(
                            icon = Icons.Default.Settings,
                            title = stringResource(R.string.settings_row_advanced),
                            onClick = onNavigateToAdvanced
                        )
                        SettingsDivider()
                        SettingsNavigationRow(
                            icon = Icons.AutoMirrored.Filled.Help,
                            title = stringResource(R.string.settings_row_usage_guide),
                            onClick = onNavigateToHelp
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
                SettingsGroup(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        SettingsNavigationRow(
                            icon = Icons.Default.Key,
                            title = choose("Change password", "修改密码"),
                            onClick = { showChangePassword = true }
                        )
                        SettingsDivider()
                        SettingsAccountActionRow(
                            icon = Icons.AutoMirrored.Filled.Logout,
                            title = stringResource(R.string.settings_account_sign_out),
                            enabled = !authState.isLoading,
                            onClick = { showLogoutConfirm = true }
                        )
                        SettingsDivider()
                        SettingsAccountActionRow(
                            icon = Icons.Default.Delete,
                            title = stringResource(R.string.settings_account_delete),
                            enabled = !authState.isLoading,
                            onClick = { showDeleteConfirm = true }
                        )
                    }
                }
            }

            // Section: About
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ClawLinkSectionHeader(
                    title = stringResource(R.string.settings_section_about),
                    subtitle = stringResource(R.string.settings_section_about_subtitle)
                )
                SettingsGroup(modifier = Modifier.fillMaxWidth()) {
                    SettingsNavigationRow(
                        icon = Icons.Default.Info,
                        title = stringResource(R.string.settings_row_check_update),
                        subtitle = stringResource(R.string.settings_row_check_update_detail),
                        onClick = { /* Open App Store or External URL */ }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "ClawLink V${com.rethinkingstudio.clawlink.BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = Color(0xFF9CA3AF)
                )
                Text(
                    stringResource(R.string.settings_about_console),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = Color(0xFF9CA3AF)
                )
            }
        }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text(stringResource(R.string.settings_account_sign_out)) },
            text = { Text(choose("Are you sure you want to sign out?", "确定要退出登录吗？")) },
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

    if (showChangePassword) {
        ChangePasswordScreen(
            isLoading = authState.isLoading,
            onDismiss = { showChangePassword = false },
            onSubmit = { currentPassword, newPassword ->
                scope.launch {
                    val didChange = authStore.changePassword(currentPassword, newPassword)
                    if (didChange) {
                        showChangePassword = false
                        wsClient.disconnect()
                        notificationPort.cancelAll()
                        onLogout()
                    }
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
                        scope.launch {
                            val didDelete = authStore.deleteAccount()
                            if (didDelete) {
                                wsClient.disconnect()
                                notificationPort.cancelAll()
                                onLogout()
                            }
                        }
                    },
                    enabled = !authState.isLoading,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    if (authState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.settings_account_delete))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.common_action_cancel))
                }
            }
        )
    }

    authState.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = authStore::clearError,
            title = { Text(choose("Error", "错误")) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = authStore::clearError) {
                    Text(stringResource(R.string.common_action_ok))
                }
            }
        )
    }
}

@Composable
private fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = SettingsGroupShape,
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE1E4EA))
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp), content = content)
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 64.dp),
        color = SettingsDividerColor
    )
}

@Composable
private fun SettingsAccountActionRow(
    icon: ImageVector,
    title: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .alpha(if (enabled) 1f else 0.55f)
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFF1F5F9),
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = Color(0xFFEF4444), modifier = Modifier.size(17.dp))
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
                fontWeight = FontWeight.Normal,
                color = Color(0xFFEF4444)
            )
        }
    }
}
