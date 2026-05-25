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
import com.rethinkingstudio.clawlink.app.AppSystemBarsEffect
import com.rethinkingstudio.clawlink.core.models.backups.BackupDraft
import com.rethinkingstudio.clawlink.core.models.backups.BackupItem
import com.rethinkingstudio.clawlink.core.models.gateway.GatewayType
import com.rethinkingstudio.clawlink.core.network.RelayAPIClient
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import com.rethinkingstudio.clawlink.ui.components.ClawLinkAlertActionRole
import com.rethinkingstudio.clawlink.ui.components.ClawLinkAlertDialog
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
    AppSystemBarsEffect()

    val gatewayState by gatewayStore.state.collectAsState()
    val scope = rememberCoroutineScope()
    val selectedGateway = gatewayState.selectedGateway
    val gatewayId = selectedGateway?.id
    val isHermesGateway = selectedGateway?.gatewayType == GatewayType.hermes
    val defaultMaxBackups = if (isHermesGateway) 0 else 5

    var backups by remember { mutableStateOf<List<BackupItem>>(emptyList()) }
    var maxBackups by remember { mutableStateOf(defaultMaxBackups) }
    var storagePath by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingBackup by remember { mutableStateOf<BackupItem?>(null) }
    var confirmDelete by remember { mutableStateOf<BackupItem?>(null) }
    var confirmRestore by remember { mutableStateOf<BackupItem?>(null) }

    val isLocked = gatewayState.restartingGatewayId != null
    val canManage = gatewayId != null && !isLocked && gatewayState.isSelectedGatewayChatChainReady
    val canCreateBackup = canManage && (maxBackups <= 0 || backups.size < maxBackups)

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

    LaunchedEffect(gatewayId, isHermesGateway, gatewayState.isSelectedGatewayChatChainReady) {
        maxBackups = defaultMaxBackups
        storagePath = null
        refreshBackups()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BackupAppBackground()
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(stringResource(R.string.backup_title), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onBackground) },
                    navigationIcon = {
                        Surface(
                            modifier = Modifier.padding(start = 16.dp).size(40.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
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
                            if (isLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                            else Icon(Icons.Default.Refresh, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onBackground
                    )
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
                        storagePath = storagePath ?: backupStorageLocation(isHermesGateway),
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
                                Text(actionMessage!!, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                // Create card
                item {
                    BackupGlassCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.backup_create_prompt), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    stringResource(if (isHermesGateway) R.string.backup_create_detail_hermes else R.string.backup_create_detail),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Surface(
                                onClick = { showCreateDialog = true },
                                enabled = canCreateBackup,
                                shape = CircleShape,
                                color = if (canCreateBackup) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
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
                        Text(stringResource(R.string.backup_list_title), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            stringResource(if (isHermesGateway) R.string.backup_list_subtitle_hermes else R.string.backup_list_subtitle),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (backups.isEmpty() && !isLoading) {
                    item {
                        BackupGlassCard {
                            Column(modifier = Modifier.padding(vertical = 30.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Inventory2, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(16.dp))
                                Text(stringResource(R.string.backup_empty_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                                Text(
                                    stringResource(if (isHermesGateway) R.string.backup_empty_subtitle_hermes else R.string.backup_empty_subtitle),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                items(backups) { backup ->
                    BackupRowCard(
                        backup = backup,
                        canManage = canManage,
                        supportsEditing = !isHermesGateway,
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
            isHermesGateway = isHermesGateway,
            initialDraft = createInitialDraft(isHermesGateway),
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
                            actionMessage = if (isHermesGateway) {
                                choose(
                                    "Created Hermes backup \"${response.backup.displayLabel}\". You now have ${response.backups.size} backups.",
                                    "已创建 Hermes 备份「${response.backup.displayLabel}」，当前共有 ${response.backups.size} 个备份。"
                                )
                            } else {
                                choose("Backup created successfully", "备份创建成功")
                            }
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
            isHermesGateway = isHermesGateway,
            initialDraft = BackupDraft(title = backup.title, detail = backup.detail, filename = backup.filename),
            onDismiss = { editingBackup = null },
            onSave = { draft ->
                editingBackup = null
                if (gatewayId != null) {
                    scope.launch {
                        try {
                            val response = apiClient.updateBackup(gatewayId, backup.id, draft)
                            backups = response.backups
                            maxBackups = response.maxBackups
                            storagePath = response.storagePath
                            actionMessage = choose("Backup updated", "备份已更新")
                        } catch (e: Exception) {
                            actionMessage = choose(
                                "Failed to update backup: ${e.message ?: "Unknown error"}",
                                "更新备份失败：${e.message ?: "未知错误"}"
                            )
                            refreshBackups()
                        }
                    }
                }
            }
        )
    }

    // Delete confirmation
    confirmDelete?.let { backup ->
        ClawLinkAlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = stringResource(R.string.backup_delete_title),
            message = stringResource(R.string.backup_delete_message, backup.displayLabel),
            confirmText = stringResource(R.string.backup_delete_action),
            confirmRole = ClawLinkAlertActionRole.Destructive,
            onConfirm = {
                val b = backup
                confirmDelete = null
                if (gatewayId != null) {
                    scope.launch {
                        try {
                            val response = apiClient.deleteBackup(gatewayId, b.id)
                            backups = response.backups
                            maxBackups = response.maxBackups
                            storagePath = response.storagePath
                            actionMessage = if (isHermesGateway) {
                                choose(
                                    "Deleted Hermes backup \"${b.displayLabel}\". You now have ${response.backups.size} backups.",
                                    "已删除 Hermes 备份「${b.displayLabel}」。当前共有 ${response.backups.size} 个备份。"
                                )
                            } else {
                                choose("Backup deleted", "备份已删除")
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
            },
            dismissText = stringResource(R.string.common_action_cancel),
            onDismissAction = { confirmDelete = null }
        )
    }

    // Restore confirmation
    confirmRestore?.let { backup ->
        ClawLinkAlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = stringResource(R.string.backup_restore_title),
            message = stringResource(
                if (isHermesGateway) R.string.backup_restore_message_hermes else R.string.backup_restore_message,
                backup.displayLabel
            ),
            confirmText = stringResource(R.string.backup_restore_action),
            onConfirm = {
                val b = backup
                confirmRestore = null
                if (gatewayId != null) {
                    scope.launch {
                        try {
                            val response = apiClient.restoreBackup(gatewayId, b.id)
                            backups = response.backups
                            maxBackups = response.maxBackups
                            storagePath = response.storagePath
                            actionMessage = if (isHermesGateway) {
                                choose(
                                    "Imported Hermes backup \"${b.displayLabel}\". Restart Hermes Gateway if needed.",
                                    "已导入 Hermes 备份「${b.displayLabel}」，请在需要时重启 Hermes Gateway。"
                                )
                            } else {
                                choose("Backup restored successfully", "备份恢复成功")
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
            },
            dismissText = stringResource(R.string.common_action_cancel),
            onDismissAction = { confirmRestore = null }
        )
    }
}
