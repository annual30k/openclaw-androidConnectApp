package com.rethinkingstudio.clawlink.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.core.models.gateway.GatewayType
import com.rethinkingstudio.clawlink.core.state.UserPreferencesStore
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import com.rethinkingstudio.clawlink.ui.components.ClawLinkCard
import com.rethinkingstudio.clawlink.ui.components.ClawLinkScaffold
import com.rethinkingstudio.clawlink.ui.components.ClawLinkSectionHeader

internal fun advancedRemoteRestartDetailRes(gatewayType: GatewayType?): Int {
    return if (gatewayType == GatewayType.hermes) {
        R.string.advanced_remote_restart_detail_hermes
    } else {
        R.string.advanced_remote_restart_detail
    }
}

internal fun advancedShowsOnlineRestart(gatewayType: GatewayType?): Boolean = gatewayType != GatewayType.hermes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedScreen(
    prefsStore: UserPreferencesStore,
    gatewayStore: GatewayStore,
    onBack: () -> Unit,
    onNavigateToRestartGateway: () -> Unit,
    onNavigateToRemoteRestart: () -> Unit,
    onNavigateToDoctorFix: () -> Unit,
    onNavigateToLogs: () -> Unit,
    onNavigateToBackups: () -> Unit
) {
    val showsTools by prefsStore.showsToolInvocationProcess.collectAsState()
    val gatewayState by gatewayStore.state.collectAsState()
    val isNormalActionEnabled = gatewayState.isSelectedGatewayChatChainReady
    val isRecoveryActionEnabled = gatewayState.canExecuteRemoteHostAction
    val selectedGatewayType = gatewayState.selectedGateway?.gatewayType ?: GatewayType.openclaw
    val showsOpenClawFeatures = selectedGatewayType == GatewayType.openclaw
    val hasBoundGateway = gatewayState.selectedGateway != null

    ClawLinkScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.advanced_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ClawLinkSectionHeader(
                title = stringResource(R.string.advanced_section_connection),
                subtitle = stringResource(R.string.advanced_section_connection_detail)
            )

            if (!hasBoundGateway) {
                AdvancedGatewayRequirementCard(
                    title = stringResource(R.string.advanced_gateway_required_title),
                    detail = stringResource(R.string.advanced_gateway_required_detail)
                )
            }

            ClawLinkCard(modifier = Modifier.fillMaxWidth(), showsBorder = false) {
                Column {
                    if (advancedShowsOnlineRestart(selectedGatewayType)) {
                        AdvancedFeatureRow(
                            icon = Icons.Default.Refresh,
                            title = stringResource(R.string.advanced_restart),
                            detail = stringResource(R.string.advanced_restart_detail),
                            tint = Color(0xFF3B82F6),
                            enabled = isNormalActionEnabled,
                            onClick = onNavigateToRestartGateway
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 64.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                    }
                    AdvancedFeatureRow(
                        icon = Icons.Default.SettingsRemote,
                        title = stringResource(R.string.advanced_remote_restart),
                        detail = stringResource(advancedRemoteRestartDetailRes(selectedGatewayType)),
                        tint = Color(0xFF6366F1),
                        enabled = isRecoveryActionEnabled,
                        onClick = onNavigateToRemoteRestart
                    )
                    if (showsOpenClawFeatures) {
                        HorizontalDivider(modifier = Modifier.padding(start = 64.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                        AdvancedFeatureRow(
                            icon = Icons.Default.Build,
                            title = stringResource(R.string.advanced_doctor_fix),
                            detail = stringResource(R.string.advanced_doctor_fix_detail),
                            tint = Color(0xFFF59E0B),
                            enabled = isRecoveryActionEnabled,
                            onClick = onNavigateToDoctorFix
                        )
                    }
                }
            }

            ClawLinkSectionHeader(
                title = stringResource(R.string.advanced_section_data_display),
                subtitle = stringResource(R.string.advanced_section_data_display_detail)
            )

            ClawLinkCard(modifier = Modifier.fillMaxWidth(), showsBorder = false) {
                Column {
                    AdvancedFeatureRow(
                        icon = Icons.Default.Terminal,
                        title = stringResource(R.string.advanced_logs),
                        detail = stringResource(R.string.advanced_logs_detail),
                        tint = Color(0xFF14B8A6),
                        enabled = hasBoundGateway,
                        onClick = onNavigateToLogs
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 64.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                    AdvancedFeatureRow(
                        icon = Icons.Default.Archive,
                        title = stringResource(R.string.advanced_backups),
                        detail = stringResource(
                            if (selectedGatewayType == GatewayType.hermes) R.string.advanced_backups_detail_hermes
                            else R.string.advanced_backups_detail
                        ),
                        tint = Color(0xFF22C55E),
                        enabled = isNormalActionEnabled,
                        onClick = onNavigateToBackups
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 64.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                    AdvancedToggleRow(
                        icon = Icons.Default.Handyman,
                        title = stringResource(R.string.advanced_prefs_show_tools),
                        detail = stringResource(R.string.advanced_prefs_show_tools_detail),
                        tint = Color(0xFFF59E0B),
                        isOn = showsTools,
                        enabled = hasBoundGateway,
                        onToggle = { prefsStore.setShowsToolInvocationProcess(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun AdvancedFeatureRow(
    icon: ImageVector,
    title: String,
    detail: String,
    tint: Color,
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
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = tint.copy(alpha = 0.1f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }

            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun AdvancedToggleRow(
    icon: ImageVector,
    title: String,
    detail: String,
    tint: Color,
    isOn: Boolean,
    enabled: Boolean = true,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.38f)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = tint.copy(alpha = 0.1f),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }

        Switch(
            checked = isOn,
            onCheckedChange = if (enabled) onToggle else null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
private fun AdvancedGatewayRequirementCard(
    title: String,
    detail: String
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.09f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Dns,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
