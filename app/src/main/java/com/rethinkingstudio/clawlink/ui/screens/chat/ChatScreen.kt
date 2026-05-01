package com.rethinkingstudio.clawlink.ui.screens.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.core.models.catalog.ModelItem
import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import com.rethinkingstudio.clawlink.core.models.gateway.AggregateStatus
import com.rethinkingstudio.clawlink.core.models.gateway.GatewaySummary
import com.rethinkingstudio.clawlink.core.state.chat.ChatStore
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import com.rethinkingstudio.clawlink.core.state.model.ModelStore
import com.rethinkingstudio.clawlink.ui.components.ClawLinkCard
import com.rethinkingstudio.clawlink.ui.components.ClawLinkScaffold
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch
import java.security.MessageDigest

private object ChatColors {
    val canvas = Color(0xFFF2F6FC)
    val sheet = Color(0xFFF6FAFF)
    val dockSurface = Color(0xFFFFFFFF)
    val dockControl = Color(0xFFF7F9FD)
    val dockBorder = Color(0xFFE6E9EF)
    val secondaryText = Color(0xFF8A8D96)
    val online = Color(0xFF5ECF7A)
    val offline = Color(0xFFE75F58)
    val pending = Color(0xFF7EADF4)
    val linkBlue = Color(0xFF2E83EE)
    val selectionBlue = Color(0xFF8AB8FF)
    val userBubble = Color(0xFF171923)
    val disabledAction = Color(0xFFE9EBF0)
}

private data class SlashAction(
    val command: String,
    val title: String,
    val detail: String,
    val icon: ImageVector
)

private data class UploadedAttachment(
    val fileId: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long
) {
    val displaySize: String
        get() = when {
            sizeBytes < 1024 -> "$sizeBytes B"
            sizeBytes < 1024 * 1024 -> "%.1f KB".format(sizeBytes / 1024.0)
            else -> "%.1f MB".format(sizeBytes / (1024.0 * 1024))
        }
}

private val defaultSlashActions = listOf(
    SlashAction("/new", "New chat", "Start a fresh OpenClaw session", Icons.Default.Add),
    SlashAction("/status", "Status", "Ask the gateway for current runtime status", Icons.Default.Terminal),
    SlashAction("/model", "Model", "Open or refresh model selection", Icons.Default.SmartToy),
    SlashAction("/help", "Help", "Show available commands and usage", Icons.Default.Description)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatStore: ChatStore,
    gatewayStore: GatewayStore,
    modelStore: ModelStore,
    onBack: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    onOpenUsageGuide: (() -> Unit)? = null
) {
    val chatState by chatStore.state.collectAsState()
    val gatewayState by gatewayStore.state.collectAsState()
    val modelState by modelStore.state.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val view = LocalView.current

    var messageText by remember { mutableStateOf("") }
    var showGatewaySheet by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }
    var voiceMode by remember { mutableStateOf(false) }
    var composerNotice by remember { mutableStateOf<String?>(null) }
    var uploadedAttachments by remember { mutableStateOf<List<UploadedAttachment>>(emptyList()) }
    var isUploadingAttachment by remember { mutableStateOf(false) }

    val gatewayId = gatewayState.selectedGatewayId
    val hasSelectedGateway = gatewayState.selectedGateway != null
    val hasActiveSession = hasSelectedGateway && chatState.currentSessionKey.isNotBlank()

    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        window.statusBarColor = ChatColors.canvas.toArgb()
        window.navigationBarColor = ChatColors.canvas.toArgb()
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true
    }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val activeGatewayId = gatewayId
        if (activeGatewayId == null) {
            composerNotice = context.getString(R.string.gateway_unpaired_host)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            isUploadingAttachment = true
            composerNotice = null
            try {
                val uploaded = uris.map { uri ->
                    uploadPickedAttachment(context, chatStore, activeGatewayId, uri)
                }
                uploadedAttachments = uploadedAttachments + uploaded
            } catch (e: Exception) {
                composerNotice = "Attachment upload failed: ${e.message ?: "Unknown error"}"
            } finally {
                isUploadingAttachment = false
            }
        }
    }
    val slashActions = remember(messageText) {
        if (messageText.startsWith("/")) {
            defaultSlashActions.filter {
                it.command.startsWith(messageText.trim(), ignoreCase = true) ||
                    it.title.contains(messageText.trim('/'), ignoreCase = true)
            }
        } else {
            emptyList()
        }
    }

    LaunchedEffect(Unit) {
        gatewayStore.loadGateways()
    }

    LaunchedEffect(gatewayId) {
        if (gatewayId != null) {
            chatStore.clearMessages()
            chatStore.connectWebSocket()
            chatStore.loadSessions(gatewayId)
            modelStore.loadModels(gatewayId)
        }
    }

    LaunchedEffect(gatewayId, chatState.currentSessionKey) {
        if (gatewayId != null && chatState.currentSessionKey.isNotBlank()) {
            chatStore.loadHistory(gatewayId, chatState.currentSessionKey)
        }
    }

    LaunchedEffect(chatState.messages.size) {
        if (chatState.messages.isNotEmpty()) {
            listState.animateScrollToItem(chatState.messages.size - 1)
        }
    }

    ClawLinkScaffold(
        topBar = {
            ChatTopBar(
                gateway = gatewayState.selectedGateway,
                onGatewayClick = { showGatewaySheet = true },
                onRefresh = {
                    scope.launch {
                        gatewayStore.loadGateways()
                        val sessionKey = chatState.currentSessionKey
                        if (gatewayId != null && sessionKey.isNotBlank()) {
                            chatStore.loadHistory(gatewayId, sessionKey)
                            chatStore.loadSessions(gatewayId)
                            modelStore.loadModels(gatewayId)
                        }
                    }
                },
                onSettings = onOpenSettings ?: {},
                onBack = onBack
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ChatColors.canvas)
                .padding(top = padding.calculateTopPadding())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(top = 0.dp)
            ) {
                AnimatedVisibility(showModelPicker, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    ModelPickerPanel(
                        models = modelState.models,
                        isLoading = modelState.isLoading,
                        selectedModel = modelState.selectedModel,
                        onSelect = { model ->
                            if (gatewayId != null) {
                                scope.launch {
                                    modelStore.selectModel(gatewayId, model, chatState.currentSessionKey.takeIf { it.isNotBlank() })
                                    showModelPicker = false
                                }
                            }
                        }
                    )
                }

                if (chatState.errorMessage != null || gatewayState.errorMessage != null || composerNotice != null) {
                    StatusBanner(
                        text = chatState.errorMessage ?: gatewayState.errorMessage ?: composerNotice.orEmpty(),
                        isError = chatState.errorMessage != null || gatewayState.errorMessage != null,
                        onDismiss = {
                            chatStore.clearError()
                            gatewayStore.clearError()
                            composerNotice = null
                        }
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    state = listState,
                    contentPadding = PaddingValues(top = 12.dp, bottom = 132.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (!hasSelectedGateway) {
                        item {
                            UsageGuidePromptCard(onOpenUsageGuide = onOpenUsageGuide ?: onOpenSettings)
                        }
                        item {
                            EmptyGatewayCard(onOpenSettings = onOpenSettings)
                        }
                    } else if (chatState.messages.isEmpty() && !chatState.isLoading) {
                        item {
                            ChatWelcomeCards(
                                onShowCommands = { messageText = "/" },
                                onNewSession = {
                                    chatStore.newSession()
                                    chatStore.clearMessages()
                                },
                                onOpenModelPicker = { showModelPicker = true }
                            )
                        }
                    }

                    if (chatState.isLoading || (gatewayState.isLoading && !hasSelectedGateway)) {
                        item { ChatSessionLoadingCard() }
                    }

                    items(chatState.messages, key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            showInvocationProcess = chatState.showInvocationProcess
                        )
                    }

                    if (chatState.isStreaming) {
                        item { ThinkingRow() }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                AnimatedVisibility(slashActions.isNotEmpty(), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    SlashCommandPanel(
                        actions = slashActions,
                        onAction = { action ->
                            when (action.command) {
                                "/new" -> {
                                    chatStore.newSession()
                                    chatStore.clearMessages()
                                }
                                "/model" -> showModelPicker = true
                                else -> chatStore.sendCommand(action.command)
                            }
                            messageText = ""
                        }
                    )
                }

                ComposerDock(
                    messageText = messageText,
                    onMessageTextChange = { messageText = it },
                    selectedModelText = modelState.selectedModelDisplay,
                    isStreaming = chatState.isStreaming,
                    voiceMode = voiceMode,
                    attachments = uploadedAttachments,
                    isUploadingAttachment = isUploadingAttachment,
                    hasActiveSession = hasActiveSession,
                    canSend = gatewayState.selectedGateway?.aggregateStatus == AggregateStatus.online,
                    onRemoveAttachment = { attachment ->
                        uploadedAttachments = uploadedAttachments.filterNot { it.fileId == attachment.fileId }
                    },
                    onOpenModelPicker = { showModelPicker = !showModelPicker },
                    onShowSkillSheet = { messageText = "/" },
                    onOpenAttachment = {
                        filePickerLauncher.launch(arrayOf("*/*"))
                    },
                    onToggleVoiceMode = { voiceMode = !voiceMode },
                    onSend = {
                        val trimmed = messageText.trim()
                        val attachmentIds = uploadedAttachments.map { it.fileId }
                        when {
                            trimmed.isBlank() && attachmentIds.isEmpty() -> Unit
                            trimmed.startsWith("/") -> {
                                chatStore.sendCommand(trimmed)
                                messageText = ""
                            }
                            else -> {
                                chatStore.sendMessage(trimmed.ifBlank { " " }, attachmentIds)
                                messageText = ""
                                uploadedAttachments = emptyList()
                            }
                        }
                    }
                )
            }

            if (showGatewaySheet) {
                GatewaySheetOverlay(
                    gateways = gatewayState.gateways,
                    selectedGatewayId = gatewayState.selectedGatewayId,
                    isLoading = gatewayState.isLoading,
                    onDismiss = { showGatewaySheet = false },
                    onRefresh = { scope.launch { gatewayStore.loadGateways() } },
                    onSelect = { gateway ->
                        gatewayStore.selectGateway(gateway.id)
                        showGatewaySheet = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ChatTopBar(
    gateway: GatewaySummary?,
    onGatewayClick: () -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    onBack: (() -> Unit)?
) {
    val hasGateway = gateway != null
    val statusColor = when {
        !hasGateway -> ChatColors.pending
        gateway?.aggregateStatus == AggregateStatus.online -> ChatColors.online
        else -> ChatColors.offline
    }
    val statusText = when {
        !hasGateway -> stringResource(R.string.gateway_status_unpaired)
        gateway?.aggregateStatus == AggregateStatus.online -> stringResource(R.string.gateway_status_online)
        else -> stringResource(R.string.gateway_status_disconnected)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(72.dp)
            .background(ChatColors.canvas)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        CircleHeaderButton(
            icon = if (onBack == null) Icons.Default.Refresh else Icons.AutoMirrored.Filled.ArrowBack,
            label = if (onBack == null) stringResource(R.string.gateways_refresh) else stringResource(R.string.common_action_back),
            onClick = onBack ?: onRefresh
        )

        Surface(
            onClick = onGatewayClick,
            shape = RoundedCornerShape(12.dp),
            color = Color.Transparent,
            modifier = Modifier.weight(1f)
        ) {
            Column(
                modifier = Modifier.padding(vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Row 1: Name + Arrow
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        gateway?.displayName ?: stringResource(R.string.gateway_unpaired_host),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                        fontWeight = FontWeight.Black,
                        color = Color.Black
                    )
                    Icon(
                        Icons.Default.ExpandMore,
                        null,
                        tint = Color(0xFF8B8F98),
                        modifier = Modifier.size(18.dp).padding(start = 2.dp)
                    )
                }
                
                // Row 2: Context Usage
                if (hasGateway) {
                    Text(
                        text = (stringResource(R.string.gateway_context_usage_label) + " " + (gateway?.contextUsage?.takeIf { it.isNotBlank() } ?: stringResource(R.string.gateway_context_usage_empty))),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = Color(0xFF8B8F98),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
                
                // Row 3: Status dot + Text
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(top = 1.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Text(
                        statusText,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = Color(0xFF8B8F98),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }

        CircleHeaderButton(Icons.Default.Settings, stringResource(R.string.settings_title), onSettings)
    }
}

@Composable
private fun CircleHeaderButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.White,
        shadowElevation = 0.dp,
        modifier = Modifier.size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = Color.Black, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun GatewaySheetOverlay(
    gateways: List<GatewaySummary>,
    selectedGatewayId: String?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onSelect: (GatewaySummary) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.22f))
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.62f),
            shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
            color = ChatColors.sheet,
            shadowElevation = 18.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp)
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 48.dp, height = 5.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFFB8BCC4))
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    CircleHeaderButton(Icons.Default.Close, stringResource(R.string.common_action_close), onDismiss)
                    Text(stringResource(R.string.gateways_list_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = Color.Black)
                    CircleHeaderButton(Icons.Default.Refresh, stringResource(R.string.gateways_refresh), onRefresh)
                }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 28.dp)
                    ) {
                        if (gateways.isEmpty()) {
                            item {
                                EmptyGatewaySheetState()
                            }
                        } else {
                            items(gateways, key = { it.id }) { gateway ->
                                GatewaySheetCard(
                                    gateway = gateway,
                                    selected = gateway.id == selectedGatewayId,
                                    onClick = { onSelect(gateway) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GatewaySheetCard(gateway: GatewaySummary, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.86f),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) ChatColors.selectionBlue else Color.White.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(gateway.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = Color.Black)
                    Text(stringResource(R.string.gateway_last_seen, gateway.lastSeenAt), style = MaterialTheme.typography.bodySmall, color = ChatColors.secondaryText, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.gateway_last_model, gateway.currentModel.ifBlank { stringResource(R.string.common_not_selected) }), style = MaterialTheme.typography.bodySmall, color = ChatColors.secondaryText, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.chat_current_session), style = MaterialTheme.typography.bodySmall, color = ChatColors.secondaryText, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.chat_main_session), style = MaterialTheme.typography.bodySmall, color = ChatColors.linkBlue, fontWeight = FontWeight.Black)
                        Icon(Icons.Default.ExpandMore, null, tint = Color(0xFF8B8F98), modifier = Modifier.size(18.dp))
                    }
                }
                StatusPill(gateway.aggregateStatus)
            }
            GatewayFlowPanel(gateway.aggregateStatus)
        }
    }
}

@Composable
private fun StatusPill(status: AggregateStatus) {
    val online = status == AggregateStatus.online
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (online) ChatColors.online.copy(alpha = 0.14f) else ChatColors.offline.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, if (online) ChatColors.online.copy(alpha = 0.3f) else ChatColors.offline.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(if (online) ChatColors.online else ChatColors.offline)
            )
            Text(if (online) stringResource(R.string.gateway_status_online) else stringResource(R.string.gateway_status_offline), color = if (online) ChatColors.online else ChatColors.offline, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GatewayFlowPanel(status: AggregateStatus) {
    val active = status == AggregateStatus.online
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF101827),
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 18.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val y = size.height * 0.42f
                drawLine(
                    color = if (active) Color(0xFF5DCF7A) else Color(0xFF314155),
                    start = androidx.compose.ui.geometry.Offset(size.width * 0.16f, y),
                    end = androidx.compose.ui.geometry.Offset(size.width * 0.84f, y),
                    strokeWidth = 7f,
                    cap = StrokeCap.Round
                )
            }
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FlowNode("App", active)
                FlowNode("Relay", active)
                FlowNode("Host", active)
            }
        }
    }
}

@Composable
private fun FlowNode(label: String, active: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            shape = CircleShape,
            color = Color(0xFF13251D),
            border = BorderStroke(2.dp, if (active) Color(0xFF5DCF7A) else Color(0xFF365066)),
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(if (active) Color(0xFF5DCF7A) else Color(0xFF5B6B7D))
                )
            }
        }
        Text(label, color = Color.White, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun ModelPickerPanel(
    models: List<ModelItem>,
    isLoading: Boolean,
    selectedModel: ModelItem?,
    onSelect: (ModelItem) -> Unit
) {
    ClawLinkCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.SmartToy, null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.nav_models), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            if (isLoading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.gateway_sync_models), style = MaterialTheme.typography.bodySmall)
                }
            } else if (models.isEmpty()) {
                Text(stringResource(R.string.gateway_no_models), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                models.take(10).forEach { model ->
                    ModelRow(model = model, selected = model.modelId == selectedModel?.modelId, onClick = { onSelect(model) })
                }
            }
        }
    }
}

@Composable
private fun ModelRow(model: ModelItem, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 11.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.SmartToy, null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(model.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(model.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (model.isDefault) Text(stringResource(R.string.models_selected), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
            if (selected) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun StatusBanner(text: String, isError: Boolean, onDismiss: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_action_close)) }
        }
    }
}

@Composable
private fun UsageGuidePromptCard(onOpenUsageGuide: (() -> Unit)?) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color.White.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, Color(0xFFE1E4EA)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                IconBadge(Icons.Default.Description)
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(stringResource(R.string.gateway_usage_guide_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.Black)
                    Text(stringResource(R.string.gateway_usage_guide_prompt), style = MaterialTheme.typography.bodyMedium, color = ChatColors.secondaryText)
                }
            }
            FullWidthCardButton(
                text = stringResource(R.string.gateway_usage_guide_button),
                icon = Icons.Default.Description,
                onClick = onOpenUsageGuide ?: {}
            )
        }
    }
}

@Composable
private fun EmptyGatewayCard(onOpenSettings: (() -> Unit)?) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color.White.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, Color(0xFFE1E4EA)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                IconBadge(Icons.Default.Settings)
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(stringResource(R.string.gateway_no_host_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.Black)
                    Text(stringResource(R.string.gateway_no_host_detail), style = MaterialTheme.typography.bodyMedium, color = ChatColors.secondaryText)
                }
            }
            Text(stringResource(R.string.gateway_no_host_hint), style = MaterialTheme.typography.bodyMedium, color = ChatColors.secondaryText, fontWeight = FontWeight.Bold)
            FullWidthCardButton(
                text = stringResource(R.string.gateway_open_settings),
                icon = Icons.Default.Settings,
                onClick = onOpenSettings ?: {}
            )
        }
    }
}

@Composable
private fun FullWidthCardButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = ChatColors.pending.copy(alpha = 0.24f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, tint = Color.Black, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, fontWeight = FontWeight.Black, color = Color.Black)
        }
    }
}

@Composable
private fun EmptyGatewaySheetState() {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.Settings, null, tint = ChatColors.secondaryText, modifier = Modifier.size(36.dp))
            Text("暂无网关", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.Black)
            Text(stringResource(R.string.gateway_connectivity_prompt), style = MaterialTheme.typography.bodySmall, color = ChatColors.secondaryText)
        }
    }
}

@Composable
private fun ChatWelcomeCards(
    onShowCommands: () -> Unit,
    onNewSession: () -> Unit,
    onOpenModelPicker: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ClawLinkCard(modifier = Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.launch_crab),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(18.dp))
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.chat_empty), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.chat_empty_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            QuickActionChip(stringResource(R.string.skills_title), Icons.Default.Terminal, Modifier.weight(1f), onShowCommands)
            QuickActionChip(stringResource(R.string.nav_models), Icons.Default.SmartToy, Modifier.weight(1f), onOpenModelPicker)
            QuickActionChip(stringResource(R.string.chat_new), Icons.Default.Add, Modifier.weight(1f), onNewSession)
        }
    }
}

@Composable
private fun ChatSessionLoadingCard() {
    ClawLinkCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Column {
                Text(stringResource(R.string.chat_switching_session), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.chat_syncing_history), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ThinkingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.8.dp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.chat_thinking), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SlashCommandPanel(actions: List<SlashAction>, onAction: (SlashAction) -> Unit) {
    ClawLinkCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        contentPadding = PaddingValues(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.skills_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            actions.forEach { action ->
                Surface(onClick = { onAction(action) }, shape = RoundedCornerShape(14.dp), color = Color.Transparent) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(action.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(action.command, fontWeight = FontWeight.SemiBold)
                            Text(action.detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposerDock(
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    selectedModelText: String,
    isStreaming: Boolean,
    voiceMode: Boolean,
    attachments: List<UploadedAttachment>,
    isUploadingAttachment: Boolean,
    hasActiveSession: Boolean,
    canSend: Boolean,
    onRemoveAttachment: (UploadedAttachment) -> Unit,
    onOpenModelPicker: () -> Unit,
    onShowSkillSheet: () -> Unit,
    onOpenAttachment: () -> Unit,
    onToggleVoiceMode: () -> Unit,
    onSend: () -> Unit
) {
    val canCompose = hasActiveSession && canSend && !isStreaming
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ChatColors.dockSurface,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, ChatColors.dockBorder)
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DockPillButton(stringResource(R.string.chat_skills_extension), Icons.Default.AutoAwesome, enabled = hasActiveSession, onClick = onShowSkillSheet)
                Spacer(Modifier.weight(1f))
                DockPillButton(selectedModelText, Icons.Default.SmartToy, enabled = hasActiveSession, onClick = onOpenModelPicker)
            }

            if (attachments.isNotEmpty() || isUploadingAttachment) {
                AttachmentTray(
                    attachments = attachments,
                    isUploading = isUploadingAttachment,
                    onRemove = onRemoveAttachment
                )
            }

            if (voiceMode) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RoundIconButton(Icons.Default.Keyboard, stringResource(R.string.chat_placeholder), enabled = true, onClick = onToggleVoiceMode)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.chat_hold_to_talk), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RoundIconButton(Icons.Default.Add, stringResource(R.string.chat_attachment), enabled = canCompose, onClick = onOpenAttachment)
                    BasicTextField(
                        value = messageText,
                        onValueChange = onMessageTextChange,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        enabled = canCompose,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { onSend() }),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.Black),
                        decorationBox = { innerTextField ->
                            Surface(
                                shape = RoundedCornerShape(21.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Color(0xFFE2E4E9))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (messageText.isEmpty()) {
                                        Text(
                                            when {
                                                !hasActiveSession -> stringResource(R.string.chat_add_gateway_placeholder)
                                                !canSend -> stringResource(R.string.gateway_status_disconnected)
                                                else -> stringResource(R.string.chat_placeholder)
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color(0xFFA0A4AF)
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        }
                    )
                    RoundIconButton(Icons.Default.Mic, stringResource(R.string.chat_voice_message), enabled = canCompose, onClick = onToggleVoiceMode)
                    SendButton(
                        enabled = canCompose && !isUploadingAttachment && (messageText.isNotBlank() || attachments.isNotEmpty()),
                        onClick = onSend
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachmentTray(
    attachments: List<UploadedAttachment>,
    isUploading: Boolean,
    onRemove: (UploadedAttachment) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        if (isUploading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.chat_uploading_attachment), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        attachments.forEach { attachment ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(attachment.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                        Text("${attachment.mimeType} · ${attachment.displaySize}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { onRemove(attachment) }) {
                        Text(stringResource(R.string.common_action_delete))
                    }
                }
            }
        }
    }
}

@Composable
private fun DockPillButton(text: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(999.dp),
        color = ChatColors.dockControl,
        border = BorderStroke(1.dp, ChatColors.dockBorder),
        modifier = Modifier.alpha(if (enabled) 1f else 0.55f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Icon(icon, null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
            Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = Color.Black)
        }
    }
}

@Composable
private fun DockSmallButton(icon: ImageVector, label: String, selected: Boolean = false, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(999.dp),
        color = if (selected) Color(0xFFE4F2FF) else Color(0xFFF7F9FD),
        contentColor = if (selected) ChatColors.linkBlue else Color.Black
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, null, modifier = Modifier.size(15.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun RoundIconButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = ChatColors.dockControl,
        contentColor = Color.Black,
        border = BorderStroke(1.dp, ChatColors.dockBorder),
        modifier = Modifier
            .size(42.dp)
            .alpha(if (enabled) 1f else 0.55f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, label, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SendButton(enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (enabled) ChatColors.linkBlue else ChatColors.disabledAction,
        contentColor = if (enabled) Color.White else Color.White.copy(alpha = 0.9f),
        modifier = Modifier.size(42.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.Send, "Send", modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    showInvocationProcess: Boolean
) {
    val isUser = message.role == MessageRole.user
    val isTool = message.role == MessageRole.tool || message.hasToolContent
    if (isTool && !showInvocationProcess) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = when {
                isUser -> ChatColors.userBubble
                isTool -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
                message.state == MessageState.streaming -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.88f)
                else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
            },
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (isUser) 20.dp else 8.dp,
                bottomEnd = if (isUser) 8.dp else 20.dp
            ),
            tonalElevation = if (isUser) 0.dp else 1.dp,
            shadowElevation = if (isUser) 0.dp else 2.dp,
            modifier = Modifier.widthIn(max = 326.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                if (isTool) {
                    ToolHeader(message.toolDisplayName ?: stringResource(R.string.chat_tool_activity))
                }

                val displayText = message.plainTextContent
                if (displayText.isNotEmpty()) {
                    Text(
                        displayText,
                        style = if (isTool) MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.labelMedium,
                        color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                }

                message.fileContentBlocks.forEach { FileBlock(it, isUser) }
                message.voiceContentBlocks.forEach { VoiceBlock(it, isUser) }
                message.toolContentBlocks.forEach { ToolBlock(it) }

                if (message.state == MessageState.streaming && message.content.isBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.6.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolHeader(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Default.Terminal, null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FileBlock(block: RelayChatContentBlock, isUser: Boolean) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isUser) Color.White.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (isUser) Color.White.copy(alpha = 0.18f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Icon(Icons.Default.Description, null, modifier = Modifier.size(20.dp), tint = if (isUser) Color.White else MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(block.fileDisplayName ?: stringResource(R.string.chat_attachment), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface)
                Text(block.fileStatusText ?: block.mimeType ?: stringResource(R.string.chat_attachment), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = if (isUser) Color.White.copy(alpha = 0.78f) else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun VoiceBlock(block: RelayChatContentBlock, isUser: Boolean) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (isUser) Color.White.copy(alpha = 0.14f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.GraphicEq, null, modifier = Modifier.size(18.dp), tint = if (isUser) Color.White else MaterialTheme.colorScheme.primary)
            Text(block.voiceTranscriptText ?: block.voiceStatusText ?: stringResource(R.string.chat_voice_message), style = MaterialTheme.typography.bodySmall, color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun ToolBlock(block: RelayChatContentBlock) {
    val text = block.result?.renderedText()
        ?: block.partialResult?.renderedText()
        ?: block.arguments?.renderedText()
        ?: block.args?.renderedText()
        ?: block.content?.renderedText()
        ?: block.output?.renderedText()
        ?: block.error?.renderedText()
        ?: block.text
        ?: block.status
        ?: return

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (block.isError == true) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.74f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Text(
            text,
            modifier = Modifier.padding(10.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = if (block.isError == true) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun QuickActionChip(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(5.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun IconBadge(icon: ImageVector) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.66f)
    ) {
        Icon(icon, null, modifier = Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

private fun sessionLabel(key: String): String {
    return key.removePrefix("session_").takeLast(8).ifBlank { "Session" }
}

private suspend fun uploadPickedAttachment(
    context: Context,
    chatStore: ChatStore,
    gatewayId: String,
    uri: Uri
): UploadedAttachment {
    val resolver = context.contentResolver
    val fileName = queryDisplayName(context, uri) ?: uri.lastPathSegment ?: "attachment"
    val mimeType = resolver.getType(uri) ?: "application/octet-stream"
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw IllegalStateException("Unable to read selected file")
    val fileId = chatStore.uploadAttachment(
        gatewayId = gatewayId,
        fileName = fileName,
        mimeType = mimeType,
        bytes = bytes,
        sha256 = sha256Hex(bytes)
    )
    return UploadedAttachment(
        fileId = fileId,
        fileName = fileName,
        mimeType = mimeType,
        sizeBytes = bytes.size.toLong()
    )
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) cursor.getString(index) else null
        } else {
            null
        }
    }
}

private fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return digest.joinToString("") { "%02x".format(it) }
}
