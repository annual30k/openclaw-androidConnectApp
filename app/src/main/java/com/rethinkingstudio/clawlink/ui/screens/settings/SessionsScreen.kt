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
import com.rethinkingstudio.clawlink.app.AppSystemBarsEffect
import com.rethinkingstudio.clawlink.core.models.chat.ChatSessionItem
import com.rethinkingstudio.clawlink.core.models.gateway.GatewayType
import com.rethinkingstudio.clawlink.core.network.RelayAPIClient
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
import com.rethinkingstudio.clawlink.core.state.chat.ChatStore
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import com.rethinkingstudio.clawlink.ui.components.ClawLinkAlertActionRole
import com.rethinkingstudio.clawlink.ui.components.ClawLinkAlertDialog
import com.rethinkingstudio.clawlink.ui.screens.chat.newMobileDraftSessionKey
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    gatewayStore: GatewayStore,
    chatStore: ChatStore,
    apiClient: RelayAPIClient,
    onBack: () -> Unit,
    onNavigateToGateways: () -> Unit
) {
    AppSystemBarsEffect()

    val gatewayState by gatewayStore.state.collectAsState()
    val chatState by chatStore.state.collectAsState()
    val scope = rememberCoroutineScope()
    val selectedGateway = gatewayState.selectedGateway
    val gatewayId = selectedGateway?.id
    val selectedGatewayType = selectedGateway?.gatewayType ?: GatewayType.openclaw

    var isRefreshing by remember { mutableStateOf(false) }
    var deletingKey by remember { mutableStateOf<String?>(null) }
    var issueMessage by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<ChatSessionItem?>(null) }

    val operationsLocked = gatewayState.restartingGatewayId != null || !gatewayState.isSelectedGatewayChatChainReady
    val canSwitchSessions = canSwitchSession(
        gatewayId = gatewayId,
        operationsLocked = operationsLocked,
        isStreaming = chatState.isStreaming,
        isStoppingRun = chatState.isStoppingRun
    )
    val currentSessionKey = chatState.currentSessionKey.ifBlank { "main" }
    val sessions = remember(chatState.sessions, currentSessionKey) {
        SessionPresentation.visibleSessions(chatState.sessions, currentSessionKey)
    }
    val canShowDeleteHint = sessions.any {
        canDeleteSession(
            session = it,
            gatewayId = gatewayId,
            gatewayType = selectedGatewayType,
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
        if (!gatewayState.isSelectedGatewayChatChainReady) return
        if (isRefreshing) return
        isRefreshing = true
        try {
            chatStore.loadSessions(id)
            issueMessage = null
        } catch (e: Exception) {
            if (forceError) issueMessage = choose("Failed to refresh sessions: ${e.message ?: "Unknown error"}", "刷新会话失败：${e.message ?: "未知错误"}")
        } finally {
            isRefreshing = false
        }
    }

    LaunchedEffect(gatewayId, gatewayState.isSelectedGatewayChatChainReady) {
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
                            enabled = gatewayState.isSelectedGatewayChatChainReady && !isRefreshing && deletingKey == null
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = choose("Refresh", "刷新"))
                            }
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
                            if (!canSwitchSessions) return@SessionManagerSummaryCard
                            val nextSessionKey = newMobileDraftSessionKey()
                            chatStore.newSession(nextSessionKey)
                            if (selectedGatewayType == GatewayType.hermes) {
                                gatewayId?.let { id ->
                                    chatStore.sendCommand(id, "/new")
                                }
                            } else {
                                gatewayId?.let { id ->
                                    chatStore.resetSession(id, nextSessionKey)
                                }
                            }
                            issueMessage = null
                            onBack()
                        }
                    )
                }

                if (gatewayState.isSelectedGatewayChatChainReady) issueMessage?.let { message ->
                    item {
                        MaintenanceBanner(
                            title = choose("Session list", "会话列表"),
                            message = message,
                            icon = Icons.Default.Warning,
                            tint = SessionWarningOrange
                        )
                    }
                }

                item {
                    SectionHeader(
                        title = stringResource(R.string.session_recent),
                        subtitle = when {
                            canShowDeleteHint -> stringResource(R.string.session_recent_subtitle_can_delete)
                            hasDedicatedSession -> choose("The current session is pinned, and the lab session is protected.", "当前会话固定置顶，联调会话受保护。")
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
                            gatewayType = selectedGatewayType,
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
                                .clip(SessionRowShape)
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
                                                .background(SessionAccentBlue, CircleShape),
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
                                            choose("Delete", "删除"),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = SessionAccentBlue
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
                                        if (!canSwitchSessions) return@SessionManagerSessionRow
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
                        else if (hasDedicatedSession) choose("Main and lab sessions cannot be deleted.", "主会话与联调会话不支持删除。")
                        else stringResource(R.string.session_hint_cannot_delete),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                        modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp)
                    )
                }
            }
        }

        if (deletingKey != null) {
            ProcessingOverlay(message = choose("Deleting session", "正在删除会话"), detail = choose("Please wait while cloud state is syncing...", "请稍候，正在同步云端状态..."))
        }

        if (gatewayState.isSelectedGatewayChatChainReady) issueMessage?.let { message ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = { TextButton(onClick = { issueMessage = null }) { Text(choose("Close", "关闭")) } }
            ) {
                Text(message)
            }
        }
    }

    confirmDelete?.let { session ->
        ClawLinkAlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = choose("Delete \"${session.displayTitle}\"?", "删除「${session.displayTitle}」？"),
            message = stringResource(R.string.session_delete_message),
            confirmText = stringResource(R.string.session_delete_action),
            confirmRole = ClawLinkAlertActionRole.Destructive,
            onConfirm = {
                val target = session
                val id = gatewayId
                confirmDelete = null
                if (id == null) {
                    issueMessage = choose("Session expired. Please pair again.", "会话已失效，请重新配对")
                    return@ClawLinkAlertDialog
                }
                deletingKey = target.normalizedSessionKey
                scope.launch {
                    try {
                        val deleted = chatStore.deleteSession(
                            gatewayId = id,
                            sessionKey = target.sessionKey,
                            deleteTranscript = true,
                            gatewayType = selectedGatewayType
                        )
                        if (deleted) {
                            chatStore.loadSessions(id)
                            issueMessage = null
                        } else {
                            issueMessage = choose("Failed to delete session: the session is still present. Refresh and try again.", "删除会话失败：会话仍未移除，请刷新后重试。")
                        }
                    } catch (e: Exception) {
                        issueMessage = choose("Failed to delete session: ${e.message ?: "Unknown error"}", "删除会话失败：${e.message ?: "未知错误"}")
                    } finally {
                        deletingKey = null
                    }
                }
            },
            dismissText = stringResource(R.string.common_action_cancel),
            onDismissAction = { confirmDelete = null }
        )
    }
}
