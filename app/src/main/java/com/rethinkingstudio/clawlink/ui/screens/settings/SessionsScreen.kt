package com.rethinkingstudio.clawlink.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.core.models.chat.ChatSessionItem
import com.rethinkingstudio.clawlink.core.network.RelayAPIClient
import com.rethinkingstudio.clawlink.core.state.chat.ChatStore
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.math.abs

private val CardShape = RoundedCornerShape(24.dp)
private val RowShape = RoundedCornerShape(20.dp)
private val TileShape = RoundedCornerShape(16.dp)
private val PillShape = RoundedCornerShape(999.dp)
private val AccentBlue = Color(0xFF0A84FF)
private val SuccessGreen = Color(0xFF20C873)
private val WarningOrange = Color(0xFFFFB13D)
private val DangerRed = Color(0xFFFF453A)
private val AccentBlueSoft = Color(0xFF5AC8FA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    gatewayStore: GatewayStore,
    chatStore: ChatStore,
    apiClient: RelayAPIClient,
    onBack: () -> Unit,
    onNavigateToGateways: () -> Unit
) {
    val gatewayState by gatewayStore.state.collectAsState()
    val chatState by chatStore.state.collectAsState()
    val scope = rememberCoroutineScope()
    val selectedGateway = gatewayState.selectedGateway
    val gatewayId = selectedGateway?.id

    var isRefreshing by remember { mutableStateOf(false) }
    var deletingKey by remember { mutableStateOf<String?>(null) }
    var issueMessage by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<ChatSessionItem?>(null) }

    val operationsLocked = gatewayState.restartingGatewayId != null
    val canSwitchSessions = gatewayId != null && !operationsLocked && !chatState.isStreaming && !chatState.isStoppingRun
    val currentSessionKey = chatState.currentSessionKey.ifBlank { "main" }
    val sessions = remember(chatState.sessions, currentSessionKey) {
        SessionPresentation.visibleSessions(chatState.sessions, currentSessionKey)
    }
    val canShowDeleteHint = sessions.any {
        canDeleteSession(
            session = it,
            gatewayId = gatewayId,
            operationsLocked = operationsLocked,
            isStreaming = chatState.isStreaming,
            isStoppingRun = chatState.isStoppingRun,
            isRefreshing = isRefreshing,
            deletingKey = deletingKey,
            role = selectedGateway?.role
        )
    }
    val hasDedicatedSession = sessions.any { isDedicatedSession(it.normalizedSessionKey) }

    suspend fun refreshSessions(forceError: Boolean = true) {
        val id = gatewayId ?: return
        if (isRefreshing) return
        isRefreshing = true
        try {
            chatStore.loadSessions(id)
            issueMessage = null
        } catch (e: Exception) {
            if (forceError) issueMessage = "刷新会话失败：${e.message ?: "未知错误"}"
        } finally {
            isRefreshing = false
        }
    }

    LaunchedEffect(gatewayId) {
        refreshSessions(forceError = false)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SessionScreenBackdrop()
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.session_nav_title), fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_action_back))
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { scope.launch { refreshSessions(forceError = true) } },
                            enabled = !isRefreshing && deletingKey == null
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "刷新")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    SessionManagerSummaryCard(
                        gatewayName = selectedGateway?.displayName ?: "--",
                        sessionCount = sessions.size,
                        canCreateSession = canSwitchSessions,
                        onSwitchGateway = onNavigateToGateways,
                        onCreateSession = {
                            if (!canSwitchSessions) {
                                issueMessage = lockMessage(gatewayId, operationsLocked, chatState.isStreaming, chatState.isStoppingRun)
                                return@SessionManagerSummaryCard
                            }
                            chatStore.newSession()
                            issueMessage = null
                            onBack()
                        }
                    )
                }

                if (operationsLocked || gatewayId == null || chatState.isStreaming || chatState.isStoppingRun) {
                    item {
                        MaintenanceBanner(
                            title = "会话锁定",
                            message = lockMessage(gatewayId, operationsLocked, chatState.isStreaming, chatState.isStoppingRun),
                            icon = Icons.Default.Lock,
                            tint = WarningOrange
                        )
                    }
                }

                issueMessage?.let { message ->
                    item {
                        MaintenanceBanner(
                            title = "会话列表",
                            message = message,
                            icon = Icons.Default.Warning,
                            tint = WarningOrange
                        )
                    }
                }

                item {
                    SectionHeader(
                        title = stringResource(R.string.session_recent),
                        subtitle = when {
                            canShowDeleteHint -> stringResource(R.string.session_recent_subtitle_can_delete)
                            hasDedicatedSession -> "当前会话固定置顶，联调会话受保护。"
                            else -> stringResource(R.string.session_recent_subtitle_default)
                        }
                    )
                }

                if (sessions.isEmpty()) {
                    item { SessionManagerEmptyStateCard() }
                } else {
                    items(sessions, key = { it.sessionKey }) { session ->
                        val normalized = session.normalizedSessionKey
                        val isCurrent = normalized == currentSessionKey.trim().lowercase().ifBlank { "main" }
                        val isMain = normalized == "main" || normalized.endsWith(":main")
                        val isDeleting = deletingKey == normalized
                        val canDelete = canDeleteSession(
                            session = session,
                            gatewayId = gatewayId,
                            operationsLocked = operationsLocked,
                            isStreaming = chatState.isStreaming,
                            isStoppingRun = chatState.isStoppingRun,
                            isRefreshing = isRefreshing,
                            deletingKey = deletingKey,
                            role = selectedGateway?.role
                        )

                        var isSwiped by remember { mutableStateOf(false) }
                        val offset by androidx.compose.animation.core.animateDpAsState(
                            targetValue = if (isSwiped) (-80).dp else 0.dp,
                            label = "swipe_offset"
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RowShape)
                        ) {
                            // Background Delete Button
                            if (canDelete && offset < 0.dp) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .padding(end = 20.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = { confirmDelete = session }
                                            )
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .shadow(4.dp, CircleShape)
                                                .background(AccentBlue, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        Text(
                                            "删除",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = AccentBlue
                                        )
                                    }
                                }
                            }

                            // Content Row
                            SessionManagerSessionRow(
                                session = session,
                                isSelected = isCurrent,
                                isMainSession = isMain,
                                canTap = canSwitchSessions && !isDeleting,
                                modifier = Modifier
                                    .offset(x = offset)
                                    .pointerInput(canDelete) {
                                        if (canDelete) {
                                            detectHorizontalDragGestures { change, dragAmount ->
                                                change.consume()
                                                if (dragAmount < -10) isSwiped = true
                                                if (dragAmount > 10) isSwiped = false
                                            }
                                        }
                                    },
                                onTap = {
                                    if (isSwiped) {
                                        isSwiped = false
                                    } else {
                                        if (!canSwitchSessions) {
                                            issueMessage = lockMessage(gatewayId, operationsLocked, chatState.isStreaming, chatState.isStoppingRun)
                                            return@SessionManagerSessionRow
                                        }
                                        if (!isCurrent) {
                                            chatStore.selectSession(session.sessionKey)
                                        }
                                        issueMessage = null
                                        onBack()
                                    }
                                }
                            )
                        }
                    }
                }

                item {
                    Text(
                        if (canShowDeleteHint) stringResource(R.string.session_hint_can_delete)
                        else if (hasDedicatedSession) "主会话与联调会话不支持删除。"
                        else stringResource(R.string.session_hint_cannot_delete),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                        modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp)
                    )
                }
            }
        }

        if (deletingKey != null) {
            ProcessingOverlay(message = "正在删除会话", detail = "请稍候，正在同步云端状态...")
        }

        issueMessage?.let { message ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = { TextButton(onClick = { issueMessage = null }) { Text("关闭") } }
            ) {
                Text(message)
            }
        }
    }

    confirmDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("删除「${session.displayTitle}」？") },
            text = { Text(stringResource(R.string.session_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = session
                        val id = gatewayId
                        confirmDelete = null
                        if (id == null) {
                            issueMessage = "会话已失效，请重新配对"
                            return@TextButton
                        }
                        deletingKey = target.normalizedSessionKey
                        scope.launch {
                            try {
                                val deleted = chatStore.deleteSession(id, target.sessionKey)
                                if (deleted) {
                                    chatStore.loadSessions(id)
                                    issueMessage = null
                                } else {
                                    issueMessage = "删除会话失败：会话仍未移除，请刷新后重试。"
                                }
                            } catch (e: Exception) {
                                issueMessage = "删除会话失败：${e.message ?: "未知错误"}"
                            } finally {
                                deletingKey = null
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = DangerRed)
                ) {
                    Text(stringResource(R.string.session_delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) {
                    Text(stringResource(R.string.common_action_cancel))
                }
            }
        )
    }
}

@Composable
private fun SessionScreenBackdrop() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFFF2F5FA), Color.White),
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(1000f, 1000f)
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
                        colors = listOf(AccentBlue.copy(alpha = 0.25f), Color.Transparent),
                        radius = Float.POSITIVE_INFINITY
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
                        colors = listOf(AccentBlueSoft.copy(alpha = 0.22f), Color.Transparent),
                        radius = Float.POSITIVE_INFINITY
                    ),
                    CircleShape
                )
                .blur(90.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(500.dp)
                .offset(x = 100.dp, y = 150.dp)
                .graphicsLayer(alpha = 0.25f)
                .background(Brush.radialGradient(listOf(AccentBlue.copy(alpha = 0.1f), Color.Transparent)), CircleShape)
                .blur(120.dp)
        )
    }
}

@Composable
private fun SessionManagerSummaryCard(
    gatewayName: String,
    sessionCount: Int,
    canCreateSession: Boolean,
    onSwitchGateway: () -> Unit,
    onCreateSession: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
            .border(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f), CardShape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("当前网关", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(gatewayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(
                modifier = Modifier
                    .clip(PillShape)
                    .background(AccentBlue.copy(alpha = 0.08f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("会话", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                Text(sessionCount.toString(), style = MaterialTheme.typography.titleMedium, color = AccentBlue, fontWeight = FontWeight.Black)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SessionManagerActionTile(
                title = stringResource(R.string.session_action_switch_gateway),
                detail = "选择另一台主机",
                icon = Icons.Default.Memory,
                tint = AccentBlue,
                modifier = Modifier.weight(1f),
                onClick = onSwitchGateway
            )
            SessionManagerActionTile(
                title = stringResource(R.string.session_action_create_session),
                detail = "开启独立上下文",
                icon = Icons.Default.Add,
                tint = SuccessGreen,
                enabled = canCreateSession,
                modifier = Modifier.weight(1f),
                onClick = onCreateSession
            )
        }
    }
}

@Composable
private fun SessionManagerActionTile(
    title: String,
    detail: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(TileShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f))
            .border(0.7.dp, tint.copy(alpha = 0.18f), TileShape)
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(34.dp).background(tint.copy(alpha = 0.14f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SessionManagerEmptyStateCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RowShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
            .border(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f), RowShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(stringResource(R.string.session_empty_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.session_empty_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SessionManagerSessionRow(
    session: ChatSessionItem,
    isSelected: Boolean,
    isMainSession: Boolean,
    canTap: Boolean,
    modifier: Modifier = Modifier,
    onTap: () -> Unit
) {
    val dedicated = isDedicatedSession(session.normalizedSessionKey)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RowShape)
            .background(if (isSelected) Color(0xFFF0F7FF) else MaterialTheme.colorScheme.surface)
            .border(0.8.dp, if (isSelected) AccentBlue.copy(alpha = 0.24f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.08f), RowShape)
            .clickable(enabled = canTap, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .size(38.dp)
                .background(if (isSelected) AccentBlue.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isSelected) Icons.Default.CheckCircle else if (isMainSession) Icons.Default.Home else Icons.Default.ChatBubbleOutline,
                contentDescription = null,
                tint = if (isSelected) AccentBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(session.displayTitle, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                when {
                    isSelected -> SessionStatusPill(stringResource(R.string.session_status_current), AccentBlue.copy(alpha = 0.12f), AccentBlue)
                    dedicated -> SessionStatusPill(stringResource(R.string.session_status_debug), SuccessGreen.copy(alpha = 0.14f), SuccessGreen)
                    isMainSession -> SessionStatusPill(stringResource(R.string.session_status_main), WarningOrange.copy(alpha = 0.14f), WarningOrange)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(session.displaySubtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(session.activityText, style = MaterialTheme.typography.labelMedium, color = if (isSelected) AccentBlue else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            }
            Text(session.sessionKey, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SessionStatusPill(title: String, tint: Color, foreground: Color) {
    Text(
        title,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = foreground,
        modifier = Modifier
            .clip(PillShape)
            .background(tint)
            .padding(horizontal = 9.dp, vertical = 5.dp)
    )
}

@Composable
private fun MaintenanceBanner(title: String, message: String, icon: ImageVector, tint: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.08f)),
        border = androidx.compose.foundation.BorderStroke(0.6.dp, tint.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(Modifier.size(42.dp).background(tint.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = tint)
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private object SessionPresentation {
    fun visibleSessions(sessions: List<ChatSessionItem>, currentSessionKey: String): List<ChatSessionItem> {
        val current = currentSessionKey.trim().ifBlank { "main" }
        val merged = sessions + ChatSessionItem(sessionKey = current, lastActivityAt = null)
        return merged
            .distinctBy { it.normalizedSessionKey }
            .sortedWith(
                compareByDescending<ChatSessionItem> { it.normalizedSessionKey == current.lowercase() }
                    .thenByDescending { parseInstant(it.lastActivityAt)?.toEpochMilli() ?: 0L }
                    .thenBy { it.displayTitle.lowercase() }
            )
    }
}

private val ChatSessionItem.normalizedSessionKey: String
    get() = sessionKey.trim().lowercase().ifBlank { "main" }

private val ChatSessionItem.displayTitle: String
    get() = firstPresentationText(displayName, derivedTitle, label) ?: displayTitleForKey(sessionKey)

private val ChatSessionItem.displaySubtitle: String
    get() {
        val kindValue = kind?.trim()?.lowercase()
        if (kindValue != null) {
            when (kindValue) {
                "direct" -> return "直连会话"
                "group" -> return "群组会话"
                "global" -> return "全局作用域"
                "unknown" -> return "未分类会话"
            }
        }
        return displaySubtitleForKey(sessionKey)
    }

private val ChatSessionItem.activityText: String
    get() = activityText(lastActivityAt)

private fun firstPresentationText(vararg values: String?): String? {
    return values.firstNotNullOfOrNull { value -> value?.trim()?.takeIf { it.isNotEmpty() } }
}

private fun displayTitleForKey(rawSessionKey: String): String {
    val normalized = rawSessionKey.trim().lowercase()
    if (normalized.isEmpty()) return "未命名会话"
    if (normalized == "main" || normalized.endsWith(":main")) return "主会话"
    if (normalized == "global") return "全局会话"
    if (isDedicatedSession(normalized)) return "文件联调"

    val localComponent = normalized.split(":").lastOrNull().orEmpty().ifBlank { normalized }
    if (listOf("ios-", "mobile-", "tui-", "session_").any { localComponent.startsWith(it) }) {
        val suffix = localComponent
            .removePrefix("ios-")
            .removePrefix("mobile-")
            .removePrefix("tui-")
            .removePrefix("session_")
            .takeLast(4)
            .uppercase()
        return if (suffix.isBlank()) "新会话" else "新会话 · $suffix"
    }

    return normalized.split(":").lastOrNull()?.takeIf { it.isNotBlank() } ?: normalized
}

private fun displaySubtitleForKey(rawSessionKey: String): String {
    val normalized = rawSessionKey.trim().lowercase()
    if (normalized.isEmpty()) return "暂无标识"
    if (normalized == "main" || normalized.endsWith(":main")) return "默认会话"
    if (normalized == "global") return "全局作用域"
    if (isDedicatedSession(normalized)) return "专用测试会话"

    val localComponent = normalized.split(":").lastOrNull().orEmpty().ifBlank { normalized }
    if (listOf("ios-", "mobile-", "tui-", "session_").any { localComponent.startsWith(it) }) {
        return "本地创建"
    }

    val parts = normalized.split(":")
    return if (parts.size > 1) parts.dropLast(1).joinToString(" · ").ifBlank { normalized } else normalized
}

private fun activityText(rawValue: String?): String {
    val instant = parseInstant(rawValue) ?: return "暂无活动"
    val now = Instant.now()
    val deltaSeconds = abs(now.epochSecond - instant.epochSecond)
    return when {
        deltaSeconds < 45 -> "刚刚"
        deltaSeconds < 3600 -> "${maxOf(1, deltaSeconds / 60)} 分钟前"
        isToday(instant) -> DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(instant)
        isSameYear(instant) -> DateTimeFormatter.ofPattern("M月d日 HH:mm").withZone(ZoneId.systemDefault()).format(instant)
        else -> DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm").withZone(ZoneId.systemDefault()).format(instant)
    }
}

private fun parseInstant(rawValue: String?): Instant? {
    val value = rawValue?.trim().orEmpty()
    if (value.isEmpty()) return null
    return try {
        Instant.parse(value)
    } catch (_: DateTimeParseException) {
        null
    }
}

private fun isToday(instant: Instant): Boolean {
    val zone = ZoneId.systemDefault()
    return instant.atZone(zone).toLocalDate() == Instant.now().atZone(zone).toLocalDate()
}

private fun isSameYear(instant: Instant): Boolean {
    val zone = ZoneId.systemDefault()
    return instant.atZone(zone).year == Instant.now().atZone(zone).year
}

private fun isDedicatedSession(normalizedSessionKey: String): Boolean {
    return normalizedSessionKey == "agent:main:file-transfer-lab"
}

private fun isProtectedSession(session: ChatSessionItem): Boolean {
    val normalized = session.normalizedSessionKey
    return normalized == "main" || normalized.endsWith(":main") || isDedicatedSession(normalized)
}

private fun canDeleteSession(
    session: ChatSessionItem,
    gatewayId: String?,
    operationsLocked: Boolean,
    isStreaming: Boolean,
    isStoppingRun: Boolean,
    isRefreshing: Boolean,
    deletingKey: String?,
    role: String?
): Boolean {
    if (isProtectedSession(session)) return false
    if (gatewayId == null) return false
    if (operationsLocked || isStreaming || isStoppingRun || isRefreshing) return false
    if (deletingKey != null && deletingKey != session.normalizedSessionKey) return false
    if (role?.trim()?.lowercase() == "viewer") return false
    return true
}

private fun lockMessage(gatewayId: String?, operationsLocked: Boolean, isStreaming: Boolean, isStoppingRun: Boolean): String {
    return when {
        gatewayId == null -> "请先完成配对后再切换会话。"
        operationsLocked -> "当前网关正在重启恢复中，暂不能切换会话。"
        isStreaming -> "当前会话还有未完成回复，请先等待或停止后再切换。"
        isStoppingRun -> "消息正在停止中，请稍后再切换会话。"
        else -> "当前不可切换会话。"
    }
}

@Composable
private fun ProcessingOverlay(message: String, detail: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 40.dp)
                .shadow(20.dp, RoundedCornerShape(32.dp), clip = false, spotColor = Color.Black.copy(alpha = 0.15f))
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                .border(0.6.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(32.dp))
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(64.dp),
                    strokeWidth = 3.dp,
                    color = AccentBlue.copy(alpha = 0.12f),
                    trackColor = Color.Transparent
                )
                CircularProgressIndicator(
                    modifier = Modifier.size(42.dp),
                    strokeWidth = 3.5.dp,
                    color = AccentBlue,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
            
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    message,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
