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
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private val SheetShape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp)
private val CardShape = RoundedCornerShape(32.dp)
private val FieldShape = RoundedCornerShape(24.dp)
private val PillShape = RoundedCornerShape(999.dp)
private val AccentBlue = Color(0xFF0A84FF)
private val ScreenWhite = Color(0xFFFAFBFF)

@Composable
private fun BackupAppBackground() {
    val accentBlue = Color(0xFF0A84FF)
    val accentBlueSoft = Color(0xFF5AC8FA)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFFF2F5FA), Color.White),
                    start = Offset(0f, 0f),
                    end = Offset(1000f, 1000f)
                )
            )
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(320.dp)
                .offset(x = 60.dp, y = (-60).dp)
                .graphicsLayer(alpha = 0.45f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(accentBlue.copy(alpha = 0.25f), Color.Transparent)
                    ),
                    CircleShape
                )
                .blur(80.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(340.dp)
                .offset(x = (-80).dp, y = 80.dp)
                .graphicsLayer(alpha = 0.4f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(accentBlueSoft.copy(alpha = 0.22f), Color.Transparent)
                    ),
                    CircleShape
                )
                .blur(90.dp)
        )
    }
}

@Composable
private fun BackupGlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(24.dp),
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.04f),
                spotColor = Color.Black.copy(alpha = 0.12f)
            )
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.85f))
            .border(BorderStroke(0.8.dp, Color.White.copy(alpha = 0.4f)), RoundedCornerShape(24.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

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
                            actionMessage = "Backup created successfully"
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
                                actionMessage = "Backup deleted"
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
                                actionMessage = "Backup restored successfully"
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

private fun createInitialDraft(): BackupDraft {
    val now = Date()
    val titleSdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val fileSdf = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault())
    return BackupDraft(
        title = "备份 ${titleSdf.format(now)}",
        detail = "",
        filename = "openclaw-${fileSdf.format(now)}.json"
    )
}

@Composable
private fun BackupHeroCard(gatewayName: String, backupCount: Int, maxBackups: Int, storagePath: String?, latestUpdate: String) {
    BackupGlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                Box(Modifier.size(48.dp).background(Color(0xFF22C55E).copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Archive, null, tint = Color(0xFF22C55E), modifier = Modifier.size(22.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(gatewayName, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color(0xFF1F2937))
                    Text(stringResource(R.string.backup_hero_subtitle), fontSize = 12.sp, color = Color(0xFF6B7280))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("$backupCount/$maxBackups", fontWeight = FontWeight.Black, fontSize = 17.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF1F2937))
                    Text(stringResource(R.string.backup_hero_count_label), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B7280))
                }
            }
            HorizontalDivider(modifier = Modifier.alpha(0.4f), color = Color(0xFFE5E7EB))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BackupStatBlock(stringResource(R.string.backup_hero_latest), latestUpdate, Modifier.weight(1f))
                BackupStatBlock(stringResource(R.string.backup_hero_storage_node), "LOCAL HOST", Modifier.weight(1f))
            }

            // Storage Path Row (iOS Style)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF22C55E).copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Folder, null, tint = Color(0xFF22C55E).copy(alpha = 0.8f), modifier = Modifier.size(12.dp))
                Text(stringResource(R.string.backup_hero_storage_path).uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF22C55E).copy(alpha = 0.8f))
                Spacer(Modifier.weight(1f))
                Text(storagePath ?: "~/.clawconnect/backups/openclaw", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF1F2937).copy(alpha = 0.8f), maxLines = 1)
            }
        }
    }
}

@Composable
private fun BackupStatBlock(title: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.background(Color.Black.copy(alpha = 0.04f), RoundedCornerShape(12.dp)).padding(12.dp)) {
        Text(title.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B7280))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1F2937))
    }
}

@Composable
private fun BackupRowCard(backup: BackupItem, canManage: Boolean, onEdit: () -> Unit, onDelete: () -> Unit, onRestore: () -> Unit) {
    BackupGlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(backup.displayLabel, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color(0xFF1F2937))
                    Text(if (backup.detail.isBlank()) stringResource(R.string.backup_editor_no_detail) else backup.detail, fontSize = 13.sp, color = Color(0xFF6B7280), maxLines = 2)
                }
                val sizeBytes = backup.sizeBytes
                if (sizeBytes != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        val sizeStr = if (sizeBytes < 1024) "${sizeBytes}B"
                            else if (sizeBytes < 1024 * 1024) "${sizeBytes / 1024}KB"
                            else "${sizeBytes / (1024 * 1024)}MB"
                        Text(sizeStr, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.06f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp))
                        Text(stringResource(R.string.backup_size_label), fontSize = 10.sp, color = Color(0xFF6B7280), fontWeight = FontWeight.Bold)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BackupInfoTag("FILE", backup.filename.ifBlank { backup.id.takeLast(12) }, Icons.Default.Description)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BackupInfoTag("CREATED", backup.createdAt.take(16).replace("T", " "), Icons.Default.CalendarToday, Modifier.weight(1f))
                    BackupInfoTag("VERSION", "2.0", Icons.Default.Tag, Modifier.weight(1f))
                }
            }

            HorizontalDivider(modifier = Modifier.alpha(0.4f), color = Color(0xFFE5E7EB))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onRestore, enabled = canManage,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Icon(Icons.Default.Restore, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.backup_restore_button), fontWeight = FontWeight.Bold)
                }
                
                IconButton(
                    onClick = onEdit, 
                    enabled = canManage, 
                    modifier = Modifier.size(44.dp).background(Color.Black.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(20.dp), tint = Color(0xFF1F2937))
                }
                
                IconButton(
                    onClick = onDelete, 
                    enabled = canManage, 
                    modifier = Modifier.size(44.dp).background(Color(0xFFEF4444).copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun BackupInfoTag(title: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.background(Color.Black.copy(alpha = 0.04f), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, null, tint = Color(0xFF6B7280), modifier = Modifier.size(10.dp))
        Text(title, fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color(0xFF6B7280))
        Text(value, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF1F2937), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupEditorSheet(
    isCreate: Boolean,
    initialDraft: BackupDraft,
    onDismiss: () -> Unit,
    onSave: (BackupDraft) -> Unit
) {
    var draft by remember { mutableStateOf(initialDraft) }
    var isSubmitting by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = SheetShape,
        containerColor = ScreenWhite,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 42.dp, height = 5.dp)
                    .clip(PillShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.26f))
            )
        }
    ) {
        val topSafePadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = topSafePadding + 8.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (isCreate) "新建备份" else "编辑备份", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(if (isCreate) "填写名称、详情和文件名，备份会保存在宿主机本地。" else "可以调整备份的展示名、备注和文件名。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Button(
                        onClick = { 
                            isSubmitting = true
                            onSave(draft) 
                        },
                        enabled = draft.title.isNotBlank() && !isSubmitting,
                        shape = PillShape,
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        if (isSubmitting) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp) else Text("保存")
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FieldLabel("备份名称")
                    IosTextField(
                        value = draft.title,
                        onValueChange = { draft = draft.copy(title = it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = "例如：2026-05-08 备份",
                        singleLine = true
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FieldLabel("备注详情")
                    IosTextField(
                        value = draft.detail,
                        onValueChange = { draft = draft.copy(detail = it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = "填写一些关于此备份的描述...",
                        minLines = 3,
                        maxLines = 5
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FieldLabel("文件名称")
                    IosTextField(
                        value = draft.filename,
                        onValueChange = { draft = draft.copy(filename = it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = "example.json",
                        singleLine = true
                    )
                    Text("提示：文件名必须以 .json 结尾", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FieldLabel("效果预览")
                    BackupRowCard(
                        backup = BackupItem(
                            id = "preview",
                            title = draft.title.ifBlank { "未命名备份" },
                            detail = draft.detail.ifBlank { "没有备注" },
                            filename = draft.filename.ifBlank { "自动生成文件名" },
                            createdAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date()),
                            sizeBytes = 1024 * 1024 // Mock size
                        ),
                        canManage = false,
                        onEdit = {},
                        onDelete = {},
                        onRestore = {}
                    )
                }
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun IosTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .clip(FieldShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
            .padding(horizontal = 18.dp, vertical = 17.dp),
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        textStyle = TextStyle(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = MaterialTheme.typography.bodyMedium.fontSize,
            fontWeight = FontWeight.Medium
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
                    )
                }
                innerTextField()
            }
        }
    )
}
