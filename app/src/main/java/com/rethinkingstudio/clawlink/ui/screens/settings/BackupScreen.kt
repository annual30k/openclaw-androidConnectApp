package com.rethinkingstudio.clawlink.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.core.models.backups.BackupItem
import com.rethinkingstudio.clawlink.core.network.RelayAPIClient
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import com.rethinkingstudio.clawlink.ui.components.ClawLinkScaffold
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    gatewayStore: GatewayStore,
    apiClient: RelayAPIClient,
    onBack: () -> Unit
) {
    val gatewayState by gatewayStore.state.collectAsState()
    val scope = rememberCoroutineScope()
    val selectedGateway = gatewayState.selectedGateway
    val gatewayId = selectedGateway?.id

    var backups by remember { mutableStateOf<List<BackupItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingBackup by remember { mutableStateOf<BackupItem?>(null) }
    var confirmDelete by remember { mutableStateOf<BackupItem?>(null) }
    var confirmRestore by remember { mutableStateOf<BackupItem?>(null) }

    val hasSession = gatewayState.gateways.isNotEmpty()
    val isLocked = gatewayState.restartingGatewayId != null
    val canManage = hasSession && gatewayId != null && !isLocked

    val accessHint = when {
        !hasSession -> stringResource(R.string.backup_hint_no_session)
        isLocked -> stringResource(R.string.backup_hint_locked)
        else -> null
    }

    suspend fun refreshBackups() {
        if (gatewayId == null) return
        isLoading = true
        try {
            backups = apiClient.fetchBackups(gatewayId)
        } catch (_: Exception) { }
        isLoading = false
    }

    LaunchedEffect(gatewayId) {
        refreshBackups()
    }

    ClawLinkScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { scope.launch { refreshBackups() } }, enabled = !isLoading) {
                        if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF8F9FB))
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(Color(0xFFF8F9FB)).padding(padding).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero card
            item {
                val latestUpdate = backups.firstOrNull()?.createdAt?.take(10) ?: stringResource(R.string.backup_latest_none)
                BackupHeroCard(
                    gatewayName = selectedGateway?.displayName ?: "--",
                    backupCount = backups.size,
                    latestUpdate = latestUpdate
                )
            }

            if (actionMessage != null) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4))) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF22C55E), modifier = Modifier.size(16.dp))
                            Text(actionMessage!!, color = Color(0xFF166534), fontSize = 13.sp)
                        }
                    }
                }
            }

            if (accessHint != null) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB))) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Lock, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                            Text(accessHint, color = Color(0xFF92400E), fontSize = 13.sp)
                        }
                    }
                }
            }

            // Create card
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.backup_create_prompt), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1F2937))
                            Text(stringResource(R.string.backup_create_detail), fontSize = 11.sp, color = Color(0xFF6B7280))
                        }
                        Spacer(Modifier.width(12.dp))
                        IconButton(
                            onClick = { showCreateDialog = true },
                            enabled = canManage && backups.size < 5
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(20.dp).background(Color(0xFF3B82F6), CircleShape).padding(2.dp), tint = Color.White)
                        }
                    }
                }
            }

            // Backup list header
            item {
                Column {
                    Text(stringResource(R.string.backup_list_title), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1F2937))
                    Text(stringResource(R.string.backup_list_subtitle), fontSize = 12.sp, color = Color(0xFF6B7280))
                }
            }

            if (backups.isEmpty() && !isLoading) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(modifier = Modifier.padding(30.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Inventory2, null, modifier = Modifier.size(40.dp), tint = Color(0xFF9CA3AF))
                            Spacer(Modifier.height(10.dp))
                            Text(stringResource(R.string.backup_empty_title), fontWeight = FontWeight.SemiBold, color = Color(0xFF1F2937))
                            Text(stringResource(R.string.backup_empty_subtitle), fontSize = 13.sp, color = Color(0xFF6B7280))
                        }
                    }
                }
            }

            items(backups) { backup ->
                BackupRowCard(
                    backup = backup,
                    canManage = canManage,
                    onEdit = { editingBackup = backup },
                    onDelete = { confirmDelete = backup },
                    onRestore = { confirmRestore = backup }
                )
            }

            item { Spacer(Modifier.height(20.dp)) }
        }
    }

    // Create dialog
    if (showCreateDialog) {
        BackupEditorDialog(
            isCreate = true,
            initialLabel = "",
            initialNote = "",
            onDismiss = { showCreateDialog = false },
            onSave = { label, note ->
                showCreateDialog = false
                if (gatewayId != null) {
                    scope.launch {
                        try {
                            apiClient.createBackup(gatewayId, label, note.ifBlank { null })
                            actionMessage = "Backup created successfully"
                            refreshBackups()
                        } catch (e: Exception) { }
                    }
                }
            }
        )
    }

    // Edit dialog
    editingBackup?.let { backup ->
        BackupEditorDialog(
            isCreate = false,
            initialLabel = backup.label,
            initialNote = backup.note ?: "",
            onDismiss = { editingBackup = null },
            onSave = { label, note ->
                editingBackup = null
                // Note: the API doesn't have an update backup endpoint, so we just refresh
                scope.launch { refreshBackups() }
            }
        )
    }

    // Delete confirmation
    confirmDelete?.let { backup ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.backup_delete_title)) },
            text = { Text(stringResource(R.string.backup_delete_message, backup.displayLabel)) },
            confirmButton = {
                TextButton(onClick = {
                    val b = backup
                    confirmDelete = null
                    if (gatewayId != null) {
                        scope.launch {
                            try {
                                apiClient.deleteBackup(gatewayId, b.id)
                                actionMessage = "Backup deleted"
                                refreshBackups()
                            } catch (_: Exception) { }
                        }
                    }
                }, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444))) {
                    Text(stringResource(R.string.backup_delete_action))
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.common_action_cancel)) } }
        )
    }

    // Restore confirmation
    confirmRestore?.let { backup ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text(stringResource(R.string.backup_restore_title)) },
            text = { Text(stringResource(R.string.backup_restore_message, backup.displayLabel)) },
            confirmButton = {
                TextButton(onClick = {
                    val b = backup
                    confirmRestore = null
                    if (gatewayId != null) {
                        scope.launch {
                            try {
                                apiClient.restoreBackup(gatewayId, b.id)
                                actionMessage = "Backup restored successfully"
                                refreshBackups()
                            } catch (_: Exception) { }
                        }
                    }
                }, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF22C55E))) {
                    Text(stringResource(R.string.backup_restore_action))
                }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text(stringResource(R.string.common_action_cancel)) } }
        )
    }
}

@Composable
private fun BackupHeroCard(gatewayName: String, backupCount: Int, latestUpdate: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                Box(Modifier.size(48.dp).background(Color(0xFF22C55E).copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Archive, null, tint = Color(0xFF22C55E), modifier = Modifier.size(20.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(gatewayName, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color(0xFF1F2937))
                    Text(stringResource(R.string.backup_hero_subtitle), fontSize = 12.sp, color = Color(0xFF6B7280))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("$backupCount/5", fontWeight = FontWeight.Black, fontSize = 17.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF1F2937))
                    Text(stringResource(R.string.backup_hero_count_label), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B7280))
                }
            }
            HorizontalDivider(color = Color(0xFFF3F4F6))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BackupStatBlock(stringResource(R.string.backup_hero_latest), latestUpdate, Modifier.weight(1f))
                BackupStatBlock(stringResource(R.string.backup_hero_storage_node), "LOCAL HOST", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BackupStatBlock(title: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.background(Color(0xFFF9FAFB), RoundedCornerShape(12.dp)).padding(12.dp)) {
        Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B7280))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1F2937))
    }
}

@Composable
private fun BackupRowCard(backup: BackupItem, canManage: Boolean, onEdit: () -> Unit, onDelete: () -> Unit, onRestore: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(backup.displayLabel, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color(0xFF1F2937))
                    Text(backup.note ?: stringResource(R.string.backup_editor_no_detail), fontSize = 13.sp, color = Color(0xFF6B7280), maxLines = 2)
                }
                val sizeBytes = backup.sizeBytes
                if (sizeBytes != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        val sizeStr = if (sizeBytes < 1024) "${sizeBytes}B"
                            else if (sizeBytes < 1024 * 1024) "${sizeBytes / 1024}KB"
                            else "${sizeBytes / (1024 * 1024)}MB"
                        Text(sizeStr, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                            modifier = Modifier.background(Color(0xFFF3F4F6), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp))
                        Text(stringResource(R.string.backup_size_label), fontSize = 10.sp, color = Color(0xFF6B7280))
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BackupInfoTag("FILE", backup.id.takeLast(12))
                BackupInfoTag("CREATED", backup.createdAt.take(10))
            }

            HorizontalDivider(color = Color(0xFFF3F4F6))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onRestore, enabled = canManage,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Restore, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.backup_restore_button), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onEdit, enabled = canManage, modifier = Modifier.size(38.dp).background(Color(0xFFF3F4F6), RoundedCornerShape(10.dp))) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(14.dp), tint = Color(0xFF1F2937))
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onDelete, enabled = canManage, modifier = Modifier.size(38.dp).background(Color(0xFFEF4444).copy(alpha = 0.1f), RoundedCornerShape(10.dp))) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(14.dp), tint = Color(0xFFEF4444))
                }
            }
        }
    }
}

@Composable
private fun BackupInfoTag(title: String, value: String) {
    Row(
        modifier = Modifier.background(Color(0xFFF9FAFB), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color(0xFF6B7280))
        Text(value, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF1F2937))
    }
}

@Composable
private fun BackupEditorDialog(
    isCreate: Boolean,
    initialLabel: String,
    initialNote: String,
    onDismiss: () -> Unit,
    onSave: (label: String, note: String) -> Unit
) {
    var label by remember { mutableStateOf(initialLabel) }
    var note by remember { mutableStateOf(initialNote) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isCreate) stringResource(R.string.backup_editor_create) else stringResource(R.string.backup_editor_save)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label, onValueChange = { label = it },
                    label = { Text(stringResource(R.string.backup_editor_name_label)) },
                    placeholder = { Text(stringResource(R.string.backup_editor_name_placeholder)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text(stringResource(R.string.backup_editor_detail_label)) },
                    placeholder = { Text(stringResource(R.string.backup_editor_detail_placeholder)) },
                    modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 3
                )
                Text(stringResource(R.string.backup_editor_filename_hint), fontSize = 12.sp, color = Color(0xFF6B7280))
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(label.trim(), note.trim()) }, enabled = label.isNotBlank()) {
                Text(if (isCreate) stringResource(R.string.backup_editor_create) else stringResource(R.string.backup_editor_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_action_cancel)) } }
    )
}
