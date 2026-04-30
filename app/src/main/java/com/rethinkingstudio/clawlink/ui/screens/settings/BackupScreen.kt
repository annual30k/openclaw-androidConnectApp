package com.rethinkingstudio.clawlink.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.core.models.backups.BackupItem
import com.rethinkingstudio.clawlink.core.state.backup.BackupStore
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import com.rethinkingstudio.clawlink.ui.components.ClawLinkScaffold
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    backupStore: BackupStore,
    gatewayStore: GatewayStore,
    onBack: () -> Unit
) {
    val backupState by backupStore.state.collectAsState()
    val gatewayState by gatewayStore.state.collectAsState()
    val scope = rememberCoroutineScope()
    val gatewayId = gatewayState.selectedGatewayId

    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(gatewayId) {
        if (gatewayId != null) backupStore.loadBackups(gatewayId)
    }

    ClawLinkScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_row_backups)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, null)
                    }
                }
            )
        }
    ) { padding ->
        if (backupState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (backupState.backups.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.backups_empty_state), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                items(backupState.backups, key = { it.id }) { backup ->
                    BackupRow(
                        backup = backup,
                        onDelete = {
                            if (gatewayId != null) {
                                scope.launch { backupStore.deleteBackup(gatewayId, backup.id) }
                            }
                        },
                        onRestore = {
                            if (gatewayId != null) {
                                scope.launch { backupStore.restoreBackup(gatewayId, backup.id) }
                            }
                        }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateBackupDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { label, note ->
                if (gatewayId != null) {
                    scope.launch { backupStore.createBackup(gatewayId, label, note) }
                }
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
                    TextButton(onClick = onRestore) { Text(stringResource(R.string.backups_action_restore)) }
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
        title = { Text(stringResource(R.string.backups_create_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.backups_create_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.backups_create_note)) },
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
            ) { Text(stringResource(R.string.common_action_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_action_cancel)) }
        }
    )
}
