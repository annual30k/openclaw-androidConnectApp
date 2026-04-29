package com.rethinkingstudio.clawlink.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rethinkingstudio.clawlink.core.models.backups.BackupItem
import com.rethinkingstudio.clawlink.core.state.backup.BackupStore
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import com.rethinkingstudio.clawlink.ui.components.ClawLinkScaffold
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedScreen(
    backupStore: BackupStore,
    gatewayStore: GatewayStore,
    onBack: () -> Unit
) {
    val backupState by backupStore.state.collectAsState()
    val gatewayState by gatewayStore.state.collectAsState()
    val scope = rememberCoroutineScope()
    val gatewayId = gatewayState.selectedGatewayId

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Backups", "Logs")

    LaunchedEffect(gatewayId) {
        if (gatewayId != null) backupStore.loadBackups(gatewayId)
    }

    ClawLinkScaffold(
        topBar = {
            TopAppBar(
                title = { Text("Advanced") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTab) {
                0 -> BackupsTab(
                    backups = backupState.backups,
                    isLoading = backupState.isLoading,
                    onCreateBackup = { label, note ->
                        if (gatewayId != null) {
                            scope.launch { backupStore.createBackup(gatewayId, label, note) }
                        }
                    },
                    onDeleteBackup = { backupId ->
                        if (gatewayId != null) {
                            scope.launch { backupStore.deleteBackup(gatewayId, backupId) }
                        }
                    },
                    onRestoreBackup = { backupId ->
                        if (gatewayId != null) {
                            scope.launch { backupStore.restoreBackup(gatewayId, backupId) }
                        }
                    }
                )
                1 -> LogsTab()
            }
        }
    }
}

@Composable
private fun BackupsTab(
    backups: List<BackupItem>,
    isLoading: Boolean,
    onCreateBackup: (label: String, note: String?) -> Unit,
    onDeleteBackup: (backupId: String) -> Unit,
    onRestoreBackup: (backupId: String) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Backups", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Button(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Create")
                }
            }
        }

        if (isLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        items(backups, key = { it.id }) { backup ->
            BackupRow(
                backup = backup,
                onDelete = { onDeleteBackup(backup.id) },
                onRestore = { onRestoreBackup(backup.id) }
            )
        }

        if (!isLoading && backups.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No backups yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateBackupDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { label, note ->
                onCreateBackup(label, note)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun BackupRow(
    backup: BackupItem,
    onDelete: () -> Unit,
    onRestore: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(backup.label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    backup.note?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(backup.createdAt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row {
                    TextButton(onClick = onRestore) { Text("Restore") }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateBackupDialog(
    onDismiss: () -> Unit,
    onCreate: (label: String, note: String?) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(label.trim(), note.trim().ifEmpty { null }) },
                enabled = label.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun LogsTab() {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.List,
                null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Text("Logs Viewer", style = MaterialTheme.typography.titleMedium)
            Text(
                "Log viewing will be available in a future update.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
