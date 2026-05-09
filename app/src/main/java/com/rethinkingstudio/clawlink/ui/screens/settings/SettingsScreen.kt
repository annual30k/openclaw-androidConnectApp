package com.rethinkingstudio.clawlink.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
import com.rethinkingstudio.clawlink.ui.components.ClawLinkCard
import com.rethinkingstudio.clawlink.ui.components.ClawLinkScaffold
import com.rethinkingstudio.clawlink.ui.components.ClawLinkSectionHeader
import com.rethinkingstudio.clawlink.ui.screens.settings.components.EmptyGatewayStatusCard
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
                            enabled = isChatChainReady,
                            onClick = onNavigateToTasks
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 64.dp), color = Color(0xFFF3F4F6))
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
                ClawLinkCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        SettingsNavigationRow(
                            icon = Icons.Default.Tune,
                            title = stringResource(R.string.settings_row_models),
                            enabled = isChatChainReady,
                            onClick = onNavigateToModels
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 64.dp), color = Color(0xFFF3F4F6))
                        SettingsNavigationRow(
                            icon = Icons.Default.ChatBubble,
                            title = stringResource(R.string.settings_row_sessions),
                            enabled = isChatChainReady,
                            onClick = onNavigateToSessions
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 64.dp), color = Color(0xFFF3F4F6))
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
                        SettingsNavigationRow(
                            icon = Icons.Default.Key,
                            title = choose("Change password", "修改密码"),
                            onClick = { showChangePassword = true }
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 64.dp), color = Color(0xFFF3F4F6))
                        Surface(
                            onClick = { showLogoutConfirm = true },
                            enabled = !authState.isLoading,
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
                            enabled = !authState.isLoading,
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
private fun ChangePasswordScreen(
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val canSubmit = !isLoading &&
        currentPassword.isNotEmpty() &&
        newPassword.length >= 8 &&
        newPassword == confirmPassword

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFF8FAFF),
                        Color(0xFFEAF4FF),
                        Color(0xFFFFFFFF)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            Surface(
                onClick = onDismiss,
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shadowElevation = 0.dp
            ) {
                Text(
                    choose("Close", "关闭"),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                choose("Change password", "修改密码"),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                color = Color.Black
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.54f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.58f)),
                shadowElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        choose("Update password", "更新密码"),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = Color.Black
                    )
                    Text(
                        choose("You will be signed out after the password is changed.", "密码修改成功后需要重新登录。"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    PasswordPageField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it },
                    label = choose("Current password", "当前密码"),
                        placeholder = choose("Current password", "当前密码"),
                    passwordVisible = passwordVisible,
                    onTogglePasswordVisible = { passwordVisible = !passwordVisible }
                )
                    PasswordPageField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = choose("New password", "新密码"),
                        placeholder = choose("At least 8 characters", "至少 8 位"),
                    passwordVisible = passwordVisible,
                    onTogglePasswordVisible = { passwordVisible = !passwordVisible }
                )
                    PasswordPageField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = choose("Confirm password", "确认新密码"),
                        placeholder = choose("Repeat new password", "再次输入新密码"),
                    passwordVisible = passwordVisible,
                    onTogglePasswordVisible = { passwordVisible = !passwordVisible }
                )

                    if (newPassword.isNotEmpty() && newPassword.length < 8) {
                        Text(
                            choose("Password must be at least 8 characters.", "密码至少需要 8 位。"),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (confirmPassword.isNotEmpty() && newPassword != confirmPassword) {
                        Text(
                            choose("Passwords do not match.", "两次输入的密码不一致。"),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Button(
                        enabled = canSubmit,
                        onClick = { onSubmit(currentPassword, newPassword) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.86f),
                            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                            contentColor = Color.White,
                            disabledContentColor = Color.White.copy(alpha = 0.42f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                            } else {
                                Icon(Icons.Default.Key, contentDescription = null, tint = Color.White.copy(alpha = if (canSubmit) 1f else 0.42f))
                            }
                            Spacer(Modifier.size(10.dp))
                            Text(choose("Update password", "更新密码"), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PasswordPageField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    passwordVisible: Boolean,
    onTogglePasswordVisible: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = Color.White,
            shadowElevation = 0.dp
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(placeholder, fontWeight = FontWeight.SemiBold) },
                leadingIcon = { Icon(Icons.Default.Key, null, tint = Color(0xFFB8BBC2)) },
                trailingIcon = {
                    IconButton(onClick = onTogglePasswordVisible) {
                        Icon(if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = Color(0xFFB8BBC2))
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    focusedPlaceholderColor = Color(0xFFB8BBC2),
                    unfocusedPlaceholderColor = Color(0xFFB8BBC2)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SettingsNavigationRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    enabled: Boolean = true,
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
                .alpha(if (enabled) 1f else 0.38f)
                .padding(horizontal = 16.dp, vertical = 14.dp),
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
