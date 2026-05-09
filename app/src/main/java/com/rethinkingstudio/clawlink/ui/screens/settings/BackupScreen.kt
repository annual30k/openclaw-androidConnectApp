package com.rethinkingstudio.clawlink.ui.screens.settings

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.core.models.backups.BackupDraft
import com.rethinkingstudio.clawlink.core.models.backups.BackupItem
import com.rethinkingstudio.clawlink.core.network.RelayAPIClient
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

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
    var maxBackups by remember { mutableStateOf(5) }
    var storagePath by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingBackup by remember { mutableStateOf<BackupItem?>(null) }
    var confirmDelete by remember { mutableStateOf<BackupItem?>(null) }
    var confirmRestore by remember { mutableStateOf<BackupItem?>(null) }

    val isLocked = gatewayState.restartingGatewayId != null
    val canManage = gatewayId != null && !isLocked && gatewayState.isSelectedGatewayChatChainReady

    suspend fun refreshBackups() {
        if (gatewayId == null || !gatewayState.isSelectedGatewayChatChainReady) return
        isLoading = true
        try {
            val response = apiClient.fetchBackups(gatewayId)
            backups = response.backups
            maxBackups = response.maxBackups
            storagePath = response.storagePath
        } catch (_: Exception) { }
        isLoading = false
    }

    LaunchedEffect(gatewayId, gatewayState.isSelectedGatewayChatChainReady) {
        refreshBackups()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BackupAppBackground()
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(stringResource(R.string.backup_title), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color(0xFF1F2937)) },
                    navigationIcon = {
                        Surface(
                            modifier = Modifier.padding(start = 16.dp).size(40.dp),
                            shape = CircleShape,
                            color = Color.White,
                            shadowElevation = 2.dp,
                            onClick = onBack
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_action_back), modifier = Modifier.size(20.dp))
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { scope.launch { refreshBackups() } }, enabled = canManage && !isLoading) {
                            if (isLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF3B82F6))
                            else Icon(Icons.Default.Refresh, null, modifier = Modifier.size(20.dp), tint = Color(0xFF3B82F6))
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Hero card
                item {
                    val latestUpdate = backups.firstOrNull()?.createdAt?.take(10) ?: stringResource(R.string.backup_latest_none)
                    BackupHeroCard(
                        gatewayName = selectedGateway?.displayName ?: "--",
                        backupCount = backups.size,
                        maxBackups = maxBackups,
                        storagePath = storagePath,
                        latestUpdate = latestUpdate
                    )
                }

                if (actionMessage != null) {
                    item {
                        BackupGlassCard {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.size(40.dp).background(Color(0xFF22C55E).copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF22C55E), modifier = Modifier.size(20.dp))
                                }
                                Text(actionMessage!!, color = Color(0xFF1F2937), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                // Create card
                item {
                    BackupGlassCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.backup_create_prompt), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1F2937))
                                Text(stringResource(R.string.backup_create_detail), fontSize = 11.sp, color = Color(0xFF6B7280))
                            }
                            Spacer(Modifier.width(12.dp))
                            Surface(
                                onClick = { showCreateDialog = true },
                                enabled = canManage && backups.size < maxBackups,
                                shape = CircleShape,
                                color = if (canManage && backups.size < maxBackups) Color(0xFF3B82F6) else Color(0xFFE5E7EB),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(20.dp), tint = Color.White)
                                }
                            }
                        }
                    }
                }

                // Backup list header
                item {
                    Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                        Text(stringResource(R.string.backup_list_title), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1F2937))
                        Text(stringResource(R.string.backup_list_subtitle), fontSize = 12.sp, color = Color(0xFF6B7280))
                    }
                }

                if (backups.isEmpty() && !isLoading) {
                    item {
                        BackupGlassCard {
                            Column(modifier = Modifier.padding(vertical = 30.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Inventory2, null, modifier = Modifier.size(48.dp), tint = Color(0xFF9CA3AF))
                                Spacer(Modifier.height(16.dp))
                                Text(stringResource(R.string.backup_empty_title), fontWeight = FontWeight.Bold, color = Color(0xFF1F2937), fontSize = 16.sp)
                                Text(stringResource(R.string.backup_empty_subtitle), fontSize = 13.sp, color = Color(0xFF6B7280), textAlign = TextAlign.Center)
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
            }
        }
    }

    // Create sheet
    if (showCreateDialog) {
        BackupEditorSheet(
            isCreate = true,
            initialDraft = createInitialDraft(),
            onDismiss = { showCreateDialog = false },
            onSave = { draft ->
                showCreateDialog = false
                if (gatewayId != null) {
                    scope.launch {
                        try {
                            val response = apiClient.createBackup(gatewayId, draft)
                            backups = response.backups
                            maxBackups = response.maxBackups
                            storagePath = response.storagePath
                            actionMessage = choose("Backup created successfully", "备份创建成功")
                        } catch (e: Exception) { }
                    }
                }
            }
        )
    }

    // Edit sheet
    editingBackup?.let { backup ->
        BackupEditorSheet(
            isCreate = false,
            initialDraft = BackupDraft(title = backup.title, detail = backup.detail, filename = backup.filename),
            onDismiss = { editingBackup = null },
            onSave = { draft ->
                editingBackup = null
                // Editing not implemented in API yet, but logic is ready
                scope.launch { refreshBackups() }
            }
        )
    }

    // Delete confirmation
    confirmDelete?.let { backup ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.backup_delete_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.backup_delete_message, backup.displayLabel)) },
            confirmButton = {
                TextButton(onClick = {
                    val b = backup
                    confirmDelete = null
                    if (gatewayId != null) {
                        scope.launch {
                            try {
                                val response = apiClient.deleteBackup(gatewayId, b.id)
                                backups = response.backups
                                maxBackups = response.maxBackups
                                storagePath = response.storagePath
                                actionMessage = choose("Backup deleted", "备份已删除")
                            } catch (_: Exception) { }
                        }
                    }
                }, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444))) {
                    Text(stringResource(R.string.backup_delete_action), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.common_action_cancel)) } }
        )
    }

    // Restore confirmation
    confirmRestore?.let { backup ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text(stringResource(R.string.backup_restore_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.backup_restore_message, backup.displayLabel)) },
            confirmButton = {
                TextButton(onClick = {
                    val b = backup
                    confirmRestore = null
                    if (gatewayId != null) {
                        scope.launch {
                            try {
                                val response = apiClient.restoreBackup(gatewayId, b.id)
                                backups = response.backups
                                maxBackups = response.maxBackups
                                storagePath = response.storagePath
                                actionMessage = choose("Backup restored successfully", "备份恢复成功")
                            } catch (_: Exception) { }
                        }
                    }
                }, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF22C55E))) {
                    Text(stringResource(R.string.backup_restore_action), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text(stringResource(R.string.common_action_cancel)) } }
        )
    }
}
