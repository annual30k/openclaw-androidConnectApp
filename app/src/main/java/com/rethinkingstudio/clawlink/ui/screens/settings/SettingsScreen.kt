package com.rethinkingstudio.clawlink.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Task
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rethinkingstudio.clawlink.core.domain.NotificationPort
import com.rethinkingstudio.clawlink.core.network.transport.RelayWebSocketClient
import com.rethinkingstudio.clawlink.core.state.auth.AuthStore
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import com.rethinkingstudio.clawlink.ui.components.ClawLinkCard
import com.rethinkingstudio.clawlink.ui.components.ClawLinkScaffold
import com.rethinkingstudio.clawlink.ui.components.ClawLinkSectionHeader
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
    onLogout: () -> Unit
) {
    val authState by authStore.state.collectAsState()
    val gatewayState by gatewayStore.state.collectAsState()
    val scope = rememberCoroutineScope()

    ClawLinkScaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ClawLinkCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Account",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (authState.isLoggedIn) "Signed in and ready." else "Not signed in.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    SettingsRow(Icons.Default.Check, "Status", if (authState.isLoggedIn) "Logged in" else "Not logged in")
                    HorizontalDivider()
                    gatewayState.selectedGateway?.let { gw ->
                        SettingsRow(Icons.Default.Computer, "Gateway", gw.displayName)
                        HorizontalDivider()
                    }
                    SettingsRow(Icons.Default.Info, "Server", authState.relayBaseUrl.ifBlank { "Not set" })
                }
            }

            ClawLinkSectionHeader(
                title = "Features",
                subtitle = "Open the same work surfaces available on iOS."
            )

            ClawLinkCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsNavigationRow(
                        icon = Icons.Default.Computer,
                        title = "Gateways",
                        subtitle = "Switch hosts and inspect connection status",
                        onClick = onNavigateToGateways
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                    SettingsNavigationRow(
                        icon = Icons.Default.Task,
                        title = "Tasks",
                        subtitle = "Create, pause, and review scheduled work",
                        onClick = onNavigateToTasks
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                    SettingsNavigationRow(
                        icon = Icons.Default.Extension,
                        title = "Skills",
                        subtitle = "Toggle assistant capabilities and slash commands",
                        onClick = onNavigateToSkills
                    )
                }
            }

            ClawLinkSectionHeader(
                title = "AI",
                subtitle = "Models and conversation controls."
            )

            ClawLinkCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsNavigationRow(
                        icon = Icons.Default.Dashboard,
                        title = "Models",
                        subtitle = "Choose the active model for the gateway",
                        onClick = onNavigateToModels
                    )
                }
            }

            ClawLinkSectionHeader(
                title = "Management",
                subtitle = "Maintenance and support surfaces."
            )

            ClawLinkCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsNavigationRow(
                        icon = Icons.Default.Tune,
                        title = "Advanced Settings",
                        subtitle = "Logs, backups, and gateway maintenance",
                        onClick = onNavigateToAdvanced
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                    SettingsNavigationRow(
                        icon = Icons.Default.Help,
                        title = "Help & Usage Guide",
                        subtitle = "How to use ClawLink",
                        onClick = onNavigateToHelp
                    )
                }
            }

            ClawLinkSectionHeader(
                title = "Session",
                subtitle = "Cleanly leave the current relay session."
            )

            ClawLinkCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                wsClient.disconnect()
                                notificationPort.cancelAll()
                                authStore.logout()
                                onLogout()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Logout, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Logout", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "ClawLink v1.0.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    RowWithLeadingIcon(icon = icon) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SettingsNavigationRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.01f)) {
        RowWithLeadingIcon(icon = icon) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "›",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            )
        }
    }
}

@Composable
private fun RowWithLeadingIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        content = {
            Icon(icon, null, modifier = Modifier.padding(start = 4.dp), tint = MaterialTheme.colorScheme.primary)
            content()
        }
    )
}
