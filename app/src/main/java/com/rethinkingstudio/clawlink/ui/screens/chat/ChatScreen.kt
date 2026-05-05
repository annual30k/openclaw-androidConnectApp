package com.rethinkingstudio.clawlink.ui.screens.chat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.core.models.catalog.ModelItem
import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.ChatSessionItem
import com.rethinkingstudio.clawlink.core.models.chat.ChatSlashCommand
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
import com.rethinkingstudio.clawlink.ui.screens.chat.components.ModelPickerSheetOverlay
import com.rethinkingstudio.clawlink.ui.screens.chat.components.SkillExpansionSheetOverlay
import com.rethinkingstudio.clawlink.ui.screens.chat.components.StreamingIndicatorBubble
import com.rethinkingstudio.clawlink.ui.screens.settings.components.GatewayFlowPanel
import androidx.core.view.WindowCompat
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import io.noties.markwon.Markwon
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

internal object ChatColors {
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
    val category: String,
    val icon: ImageVector
)

private data class UploadedAttachment(
    val fileId: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val downloadUrl: String? = null,
    val expiresAt: String? = null,
    val imageWidth: Int? = null,
    val imageHeight: Int? = null,
    val senderDisplayName: String? = null
) {
    val displaySize: String
        get() = when {
            sizeBytes < 1024 -> "$sizeBytes B"
            sizeBytes < 1024 * 1024 -> "%.1f KB".format(sizeBytes / 1024.0)
            else -> "%.1f MB".format(sizeBytes / (1024.0 * 1024))
        }

    fun contentBlock(gatewayId: String, sessionKey: String): RelayChatContentBlock {
        return RelayChatContentBlock(
            type = if (mimeType.trim().lowercase().startsWith("audio/")) "voice" else "file",
            text = fileName,
            name = fileName,
            fileId = fileId,
            fileName = fileName,
            mimeType = mimeType,
            sizeBytes = sizeBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            downloadUrl = downloadUrl,
            expiresAt = expiresAt,
            senderDisplayName = senderDisplayName,
            gatewayId = gatewayId,
            sessionKey = sessionKey,
            status = displaySize
        )
    }
}

private val defaultSlashActions = listOf(
    SlashAction("/new", "新会话", "开启一个新的聊天会话", "SESSION", Icons.Default.Add),
    SlashAction("/model", "切模型", "选择当前会话使用的模型", "SESSION", Icons.Default.SmartToy),
    SlashAction("/status", "看状态", "查看当前链路和网关状态", "SYSTEM", Icons.Default.GraphicEq),
    SlashAction("/doctor", "做诊断", "检查 Relay、网关和会话链路", "SYSTEM", Icons.Default.CheckCircle),
    SlashAction("/config", "配设置", "查看或调整当前配置", "SYSTEM", Icons.Default.Settings),
    SlashAction("/skills list", "skills", "List available skills.", "SKILLS", Icons.Default.AutoAwesome),
    SlashAction("/channels list", "channels", "List available channels.", "SYSTEM", Icons.Default.Terminal),
    SlashAction("/cron list", "cron", "List scheduled tasks.", "SYSTEM", Icons.Default.Refresh)
)

private fun slashCommandSuggestions(
    input: String,
    remoteCommands: List<ChatSlashCommand>?
): List<SlashAction> {
    val query = normalizedLeadingSlashQuery(input) ?: return emptyList()
    return mergedSlashActions(remoteCommands)
        .mapIndexedNotNull { index, action ->
            val rank = slashMatchRank(action.command.normalizedSlashCommand(), query) ?: return@mapIndexedNotNull null
            Triple(rank, index, action)
        }
        .sortedWith(
            compareBy<Triple<Int, Int, SlashAction>> { it.first }
                .thenBy { it.second }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.third.title }
        )
        .map { it.third }
}

private fun mergedSlashActions(remoteCommands: List<ChatSlashCommand>?): List<SlashAction> {
    val merged = mutableListOf<SlashAction>()
    val seen = mutableSetOf<String>()
    ((remoteCommands.orEmpty().mapNotNull { it.toSlashAction() }) + defaultSlashActions).forEach { action ->
        if (seen.add(action.command.normalizedSlashCommand())) {
            merged += action
        }
    }
    return merged
}

private fun ChatSlashCommand.toSlashAction(): SlashAction? {
    val resolvedCommand = command?.trim()?.takeIf { it.startsWith("/") && it.isNotBlank() } ?: return null
    val resolvedTitle = title?.trim()?.takeIf { it.isNotEmpty() }
        ?: name?.trim()?.takeIf { it.isNotEmpty() }
        ?: resolvedCommand
    val resolvedDetail = detail?.trim()?.takeIf { it.isNotEmpty() }
        ?: description?.trim()?.takeIf { it.isNotEmpty() }
        ?: ""
    val resolvedCategory = category?.trim()?.takeIf { it.isNotEmpty() }
        ?: defaultSlashCategory(resolvedCommand)
    return SlashAction(
        command = resolvedCommand,
        title = resolvedTitle,
        detail = resolvedDetail,
        category = resolvedCategory.uppercase(),
        icon = slashIcon(iconName, resolvedCommand)
    )
}

private fun normalizedLeadingSlashQuery(input: String): String? {
    val trimmed = input.trim()
    if (!trimmed.startsWith("/")) return null
    return trimmed.normalizedSlashCommand()
}

private fun String.normalizedSlashCommand(): String {
    return trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
}

private fun slashMatchRank(command: String, query: String): Int? {
    return when {
        query.isEmpty() -> 0
        command == query -> 0
        command.startsWith(query) -> 1
        query.startsWith(command) -> 2
        else -> null
    }
}

private fun defaultSlashCategory(command: String): String {
    val cmd = command.trim().lowercase()
    return when {
        listOf("session", "focus", "unfocus", "stop", "reset", "new", "compact", "clear", "model").any { cmd.contains(it) } -> "SESSION"
        cmd.contains("skill") || cmd.contains("tool") -> "TOOLS"
        else -> "SYSTEM"
    }
}

private fun slashIcon(iconName: String?, command: String): ImageVector {
    val icon = iconName?.trim()?.lowercase().orEmpty()
    val cmd = command.trim().lowercase()
    return when {
        icon.contains("plus") || cmd.startsWith("/new") -> Icons.Default.Add
        icon.contains("cube") || cmd.startsWith("/model") -> Icons.Default.SmartToy
        icon.contains("gear") || cmd.startsWith("/config") -> Icons.Default.Settings
        icon.contains("stethoscope") || cmd.startsWith("/doctor") -> Icons.Default.CheckCircle
        icon.contains("clock") || cmd.startsWith("/cron") -> Icons.Default.Refresh
        icon.contains("wand") || cmd.startsWith("/skills") || cmd.startsWith("/skill") -> Icons.Default.AutoAwesome
        icon.contains("list") || cmd.startsWith("/commands") -> Icons.Default.List
        icon.contains("stop") || cmd.startsWith("/stop") -> Icons.Default.Stop
        icon.contains("question") || cmd.startsWith("/help") -> Icons.Default.Description
        icon.contains("waveform") || cmd.startsWith("/status") -> Icons.Default.GraphicEq
        else -> Icons.Default.Terminal
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatStore: ChatStore,
    gatewayStore: GatewayStore,
    modelStore: ModelStore,
    onBack: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    onOpenUsageGuide: (() -> Unit)? = null,
    hasSeenUsageGuide: Boolean = true
) {
    val chatState by chatStore.state.collectAsState()
    val gatewayState by gatewayStore.state.collectAsState()
    val modelState by modelStore.state.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current

    var messageText by remember { mutableStateOf("") }
    var showGatewaySheet by remember { mutableStateOf(false) }
    var showSkillExpansionSheet by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }
    var voiceMode by remember { mutableStateOf(false) }
    var composerNotice by remember { mutableStateOf<String?>(null) }
    var uploadedAttachments by remember { mutableStateOf<List<UploadedAttachment>>(emptyList()) }
    var isUploadingAttachment by remember { mutableStateOf(false) }
    var composerHeight by remember { mutableStateOf(0.dp) }

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
    val slashActions = remember(messageText, gatewayState.selectedGateway?.slashCommands) {
        slashCommandSuggestions(
            input = messageText,
            remoteCommands = gatewayState.selectedGateway?.slashCommands
        )
    }

    LaunchedEffect(Unit) {
        gatewayStore.loadGateways()
    }

    LaunchedEffect(gatewayId) {
        if (gatewayId != null) {
            chatStore.beginGatewaySwitch(gatewayId)
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

    Box(modifier = Modifier.fillMaxSize()) {
        ClawLinkScaffold(
            topBar = {
                ChatTopBar(
                    gateway = gatewayState.selectedGateway,
                    appRelayStatus = gatewayState.appRelayStatus,
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
                    .padding(horizontal = 20.dp)
                    .padding(top = 0.dp)
            ) {
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

                val displayMessages = remember(chatState.messages, chatState.showInvocationProcess) {
                    chatState.messages.filter { message ->
                        val isTool = message.role == MessageRole.tool || message.hasToolContent
                        if (isTool) {
                            val visible = message.visibleToolContentBlocks(chatState.showInvocationProcess)
                            visible.isNotEmpty() || (message.toolContentBlocks.isEmpty() && message.plainTextContent.isNotBlank())
                        } else {
                            message.content.isNotBlank() || message.fileContentBlocks.isNotEmpty() || message.voiceContentBlocks.isNotEmpty() || message.state == MessageState.streaming
                        }
                    }
                }
                val hasStreamingAssistantMessage = displayMessages.any {
                    it.role == MessageRole.assistant && it.state == MessageState.streaming
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    state = listState,
                    contentPadding = PaddingValues(top = 14.dp, bottom = composerHeight + 18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (!hasSelectedGateway && gatewayState.isLoading) {
                        item { ChatSessionLoadingCard() }
                    } else if (!hasSelectedGateway) {
                        if (!hasSeenUsageGuide) {
                            item {
                                UsageGuidePromptCard(onOpenUsageGuide = onOpenUsageGuide ?: onOpenSettings)
                            }
                        }
                        item {
                            EmptyGatewayCard(onOpenSettings = onOpenSettings)
                        }
                    }

                    if (chatState.isLoading && displayMessages.isEmpty() && !chatState.isSwitchingSession) {
                        item { ChatSessionLoadingCard() }
                    }

                    items(displayMessages, key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            showInvocationProcess = chatState.showInvocationProcess,
                            relayBaseUrl = chatStore.relayBaseUrl,
                            accessToken = chatStore.accessToken
                        )
                    }

                    if (chatState.isStreaming && !hasStreamingAssistantMessage) {
                        item { ThinkingRow() }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        val height = coordinates.size.height
                        composerHeight = with(density) { height.toDp() }
                    }
            ) {
                AnimatedVisibility(
                    slashActions.isNotEmpty() && hasActiveSession && !voiceMode,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    SlashCommandPanel(
                        actions = slashActions,
                        onAction = { action ->
                            messageText = action.command
                        }
                    )
                }

                ComposerDock(
                    messageText = messageText,
                    onMessageTextChange = { messageText = it },
                    selectedModelText = modelState.selectedModelDisplay,
                    isStreaming = chatState.isStreaming,
                    isStoppingRun = chatState.isStoppingRun,
                    voiceMode = voiceMode,
                    attachments = uploadedAttachments,
                    isUploadingAttachment = isUploadingAttachment,
                    hasActiveSession = hasActiveSession,
                    canSend = gatewayState.selectedGateway?.aggregateStatus == AggregateStatus.online,
                    onRemoveAttachment = { attachment ->
                        uploadedAttachments = uploadedAttachments.filterNot { it.fileId == attachment.fileId }
                    },
                    onOpenModelPicker = {
                        showModelPicker = !showModelPicker
                        if (showModelPicker && modelState.models.isEmpty()) {
                            gatewayId?.let { id -> scope.launch { modelStore.loadModels(id) } }
                        }
                    },
                    onShowSkillSheet = { showSkillExpansionSheet = true },
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
                                chatStore.sendCommand(
                                    gatewayId = gatewayState.selectedGateway?.id.orEmpty(),
                                    command = trimmed
                                )
                                messageText = ""
                            }
                            else -> {
                                chatStore.sendMessage(
                                    content = trimmed.ifBlank { " " },
                                    gatewayId = gatewayState.selectedGateway?.id.orEmpty(),
                                    attachmentIds = attachmentIds,
                                    attachmentBlocks = uploadedAttachments.map {
                                        it.contentBlock(
                                            gatewayId = gatewayState.selectedGateway?.id.orEmpty(),
                                            sessionKey = chatState.currentSessionKey
                                        )
                                    }
                                )
                                messageText = ""
                                uploadedAttachments = emptyList()
                            }
                        }
                    },
                    onAbort = {
                        chatStore.abortRun()
                    }
                )
            }

            if (showGatewaySheet) {
                GatewaySheetOverlay(
                    gateways = gatewayState.gateways,
                    appRelayStatus = gatewayState.appRelayStatus,
                    selectedGatewayId = gatewayState.selectedGatewayId,
                    sessions = chatState.sessions,
                    currentSessionKey = chatState.currentSessionKey,
                    isLoading = gatewayState.isLoading,
                    onDismiss = { showGatewaySheet = false },
                    onRefresh = { scope.launch { gatewayStore.loadGateways() } },
                    onRefreshSessions = { gateway ->
                        scope.launch { chatStore.loadSessions(gateway.id) }
                    },
                    onSelect = { gateway ->
                        gatewayStore.selectGateway(gateway.id)
                        showGatewaySheet = false
                    },
                    onSelectSession = { gateway, session ->
                        gatewayStore.selectGateway(gateway.id)
                        chatStore.selectSession(session.sessionKey)
                        showGatewaySheet = false
                    }
                )
            }

            if (showSkillExpansionSheet) {
                SkillExpansionSheetOverlay(
                    onDismiss = { showSkillExpansionSheet = false },
                    onSendPrompt = { prompt ->
                        chatStore.sendCommand(
                            gatewayId = gatewayState.selectedGateway?.id.orEmpty(),
                            command = prompt
                        )
                    }
                )
            }

            if (showModelPicker) {
                ModelPickerSheetOverlay(
                    models = modelState.models,
                    isLoading = modelState.isLoading,
                    errorMessage = modelState.errorMessage,
                    selectedModel = modelState.selectedModel,
                    onDismiss = { showModelPicker = false },
                    onRefresh = {
                        gatewayId?.let { id ->
                            scope.launch { modelStore.loadModels(id) }
                        }
                    },
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
            }
        }

        if (chatState.isSwitchingSession) {
            ChatSessionSwitchLoadingOverlay(modifier = Modifier.matchParentSize())
        }
    }
}

@Composable
private fun ChatTopBar(
    gateway: GatewaySummary?,
    appRelayStatus: AggregateStatus,
    onGatewayClick: () -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    onBack: (() -> Unit)?
) {
    val effectiveStatus = if (appRelayStatus == AggregateStatus.online) {
        gateway?.aggregateStatus ?: AggregateStatus.offline
    } else {
        appRelayStatus
    }

    val hasGateway = gateway != null
    val statusColor = when {
        !hasGateway -> ChatColors.pending
        effectiveStatus == AggregateStatus.online -> ChatColors.online
        else -> ChatColors.offline
    }
    val statusText = when {
        !hasGateway -> stringResource(R.string.gateway_status_unpaired)
        effectiveStatus == AggregateStatus.online -> stringResource(R.string.gateway_status_online)
        else -> stringResource(R.string.gateway_status_disconnected)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(84.dp)
            .background(ChatColors.canvas)
            .padding(horizontal = 24.dp),
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
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.92f)),
        modifier = Modifier.size(54.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = Color.Black, modifier = Modifier.size(28.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GatewaySheetOverlay(
    gateways: List<GatewaySummary>,
    appRelayStatus: AggregateStatus,
    selectedGatewayId: String?,
    sessions: List<ChatSessionItem>,
    currentSessionKey: String,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onRefreshSessions: (GatewaySummary) -> Unit,
    onSelect: (GatewaySummary) -> Unit,
    onSelectSession: (GatewaySummary, ChatSessionItem) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var expandedSessionGatewayId by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ChatColors.sheet,
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 12.dp)
                    .size(width = 48.dp, height = 5.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFFB8BCC4))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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
                            GatewayItemCard(
                                gateway = gateway,
                                appRelayStatus = appRelayStatus,
                                selected = selectedGatewayId == gateway.id,
                                sessions = if (selectedGatewayId == gateway.id) sessions else emptyList(),
                                currentSessionKey = if (selectedGatewayId == gateway.id) currentSessionKey else "",
                                isSessionExpanded = expandedSessionGatewayId == gateway.id,
                                onToggleSessionExpanded = {
                                    val shouldExpand = expandedSessionGatewayId != gateway.id
                                    expandedSessionGatewayId = if (shouldExpand) gateway.id else null
                                    if (shouldExpand) {
                                        onRefreshSessions(gateway)
                                    }
                                },
                                onRefreshSessions = { onRefreshSessions(gateway) },
                                onSelectSession = { session -> onSelectSession(gateway, session) },
                                onClick = { onSelect(gateway) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GatewayItemCard(
    gateway: GatewaySummary,
    appRelayStatus: AggregateStatus,
    selected: Boolean,
    sessions: List<ChatSessionItem>,
    currentSessionKey: String,
    isSessionExpanded: Boolean,
    onToggleSessionExpanded: () -> Unit,
    onRefreshSessions: () -> Unit,
    onSelectSession: (ChatSessionItem) -> Unit,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = 0.86f),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) ChatColors.selectionBlue else Color.White.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        gateway.displayName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 17.sp),
                        fontWeight = FontWeight.Black,
                        color = Color.Black
                    )
                    Text(
                        stringResource(R.string.gateway_last_seen, gateway.lastSeenAt),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium,
                        color = ChatColors.secondaryText,
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.gateway_last_model_label),
                            maxLines = 1,
                            style = MaterialTheme.typography.labelSmall,
                            color = ChatColors.secondaryText,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            gateway.currentModel.ifBlank { stringResource(R.string.common_not_selected) },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium,
                            color = ChatColors.secondaryText,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                StatusPill(
                    status = if (appRelayStatus == AggregateStatus.online) gateway.aggregateStatus else appRelayStatus
                )
            }
            GatewaySessionSelector(
                sessions = sessions,
                currentSessionKey = currentSessionKey,
                isExpanded = isSessionExpanded,
                onToggleExpanded = onToggleSessionExpanded,
                onRefreshSessions = onRefreshSessions,
                onSelectSession = onSelectSession
            )
            GatewayFlowPanel(
                statuses = GatewayStore.selectedGatewayStatuses(
                    selectedGateway = gateway,
                    appRelayStatus = appRelayStatus
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun GatewaySessionSelector(
    sessions: List<ChatSessionItem>,
    currentSessionKey: String,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onRefreshSessions: () -> Unit,
    onSelectSession: (ChatSessionItem) -> Unit
) {
    val currentSession = sessions.firstOrNull { it.normalizedSessionKey == currentSessionKey }
    val currentDisplayName = currentSession?.displayTitle ?: sessionDisplayName(currentSessionKey)
    val density = LocalDensity.current
    var selectorWidth by remember { mutableStateOf(0.dp) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isExpanded) 20f else 0f)
            .onGloballyPositioned { coordinates ->
                selectorWidth = with(density) { coordinates.size.width.toDp() }
            }
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onToggleExpanded)
                .padding(vertical = 1.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.chat_current_session),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = ChatColors.secondaryText,
                fontWeight = FontWeight.Medium
            )
            Text(
                currentDisplayName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = ChatColors.linkBlue,
                fontWeight = FontWeight.Medium
            )
            Icon(
                Icons.Default.ExpandMore,
                null,
                tint = Color(0xFF8B8F98),
                modifier = Modifier.size(16.dp)
            )
        }

        if (isExpanded) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, with(density) { 30.dp.roundToPx() }),
                onDismissRequest = onToggleExpanded,
                properties = PopupProperties(focusable = true)
            ) {
                GatewaySessionDropdownPanel(
                    sessions = sessions,
                    currentSessionKey = currentSessionKey,
                    onRefreshSessions = onRefreshSessions,
                    onSelectSession = onSelectSession,
                    modifier = Modifier.width(selectorWidth)
                )
            }
        }
    }
}

@Composable
private fun GatewaySessionDropdownPanel(
    sessions: List<ChatSessionItem>,
    currentSessionKey: String,
    onRefreshSessions: () -> Unit,
    onSelectSession: (ChatSessionItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF101827),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.settings_session_list_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.gateway_session_count, sessions.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.56f)
                    )
                }
                IconButton(onClick = onRefreshSessions, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.Refresh, null, tint = Color.White.copy(alpha = 0.74f), modifier = Modifier.size(16.dp))
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.12f))
            )

            if (sessions.isEmpty()) {
                Text(
                    stringResource(R.string.gateway_session_empty),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.62f)
                )
            } else {
                val maxHeight = ((sessions.size.coerceIn(1, 4) * 50).dp).coerceAtLeast(112.dp)
                LazyColumn(
                    modifier = Modifier.heightIn(max = maxHeight),
                    contentPadding = PaddingValues(vertical = 5.dp)
                ) {
                    items(sessions, key = { it.sessionKey }) { session ->
                        GatewaySessionRow(
                            session = session,
                            isCurrent = session.normalizedSessionKey == currentSessionKey,
                            onSelect = { onSelectSession(session) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GatewaySessionRow(
    session: ChatSessionItem,
    isCurrent: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(14.dp),
        color = if (isCurrent) ChatColors.linkBlue.copy(alpha = 0.12f) else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    session.displayTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isCurrent) ChatColors.linkBlue else Color.White,
                    fontWeight = if (isCurrent) FontWeight.Black else FontWeight.Bold
                )
                session.activityText.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.52f)
                    )
                }
            }
            if (isCurrent) {
                Surface(shape = RoundedCornerShape(999.dp), color = ChatColors.linkBlue.copy(alpha = 0.18f)) {
                    Text(
                        stringResource(R.string.gateway_session_current_badge),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = ChatColors.linkBlue,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPill(status: AggregateStatus) {
    val online = status == AggregateStatus.online
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (online) ChatColors.online.copy(alpha = 0.14f) else ChatColors.offline.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, if (online) ChatColors.online.copy(alpha = 0.36f) else ChatColors.offline.copy(alpha = 0.32f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(if (online) ChatColors.online else ChatColors.offline)
            )
            Text(
                if (online) stringResource(R.string.gateway_status_online) else stringResource(R.string.gateway_status_offline),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                color = if (online) ChatColors.online else ChatColors.offline,
                fontWeight = FontWeight.Bold
            )
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
private fun ChatSessionSwitchLoadingOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(ChatColors.canvas.copy(alpha = 0.82f))
            .padding(horizontal = 34.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = Color.White.copy(alpha = 0.92f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.72f)),
            shadowElevation = 18.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 34.dp, vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(58.dp),
                        strokeWidth = 4.dp,
                        color = ChatColors.linkBlue,
                        trackColor = ChatColors.linkBlue.copy(alpha = 0.12f)
                    )
                    Icon(
                        Icons.Default.List,
                        contentDescription = null,
                        tint = ChatColors.linkBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.chat_loading_switching_session),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF171923)
                    )
                    Text(
                        stringResource(R.string.chat_loading_syncing),
                        style = MaterialTheme.typography.bodySmall,
                        color = ChatColors.secondaryText
                    )
                }
            }
        }
    }
}

@Composable
private fun ThinkingRow() {
    Column(
        modifier = Modifier.padding(start = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StreamingIndicatorBubble()
        Text(
            "ClawLink",
            modifier = Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = ChatColors.secondaryText,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SlashCommandPanel(actions: List<SlashAction>, onAction: (SlashAction) -> Unit) {
    val sections = remember(actions) {
        val preferred = listOf("SESSION", "SYSTEM", "TOOLS", "SKILLS")
        val grouped = actions.groupBy { it.category.ifBlank { "SYSTEM" } }
        preferred.mapNotNull { category ->
            grouped[category]?.takeIf { it.isNotEmpty() }?.let { category to it }
        } + grouped
            .filterKeys { it !in preferred }
            .toSortedMap()
            .map { it.key to it.value }
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.96f),
        border = BorderStroke(0.5.dp, ChatColors.dockBorder.copy(alpha = 0.8f)),
        shadowElevation = 10.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Terminal, null, tint = ChatColors.linkBlue, modifier = Modifier.size(14.dp))
                Text(
                    stringResource(R.string.chat_skill_panel_title),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                    color = Color.Black,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(999.dp), color = ChatColors.dockControl) {
                    Text(
                        actions.size.toString(),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        color = ChatColors.secondaryText,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                sections.forEach { (category, items) ->
                    item(key = "header-$category") {
                        SlashCategoryHeader(category)
                    }
                    items(items, key = { "${it.category}|${it.command}" }) { action ->
                        Surface(onClick = { onAction(action) }, shape = RoundedCornerShape(12.dp), color = Color.Transparent) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = slashCategoryColor(action.category).copy(alpha = 0.12f),
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            action.icon,
                                            null,
                                            tint = slashCategoryColor(action.category),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                        Text(
                                            action.command,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 15.sp),
                                            fontWeight = FontWeight.Black,
                                            color = Color.Black
                                        )
                                        if (action.command.contains("session")) {
                                            Text(
                                                "[action]",
                                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                                color = ChatColors.secondaryText.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                    if (action.detail.isNotBlank()) {
                                        Text(
                                            action.detail,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                                            color = ChatColors.secondaryText
                                        )
                                    }
                                }
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    null,
                                    tint = ChatColors.secondaryText.copy(alpha = 0.5f),
                                    modifier = Modifier.size(16.dp)
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
private fun SlashCategoryHeader(category: String) {
    val color = slashCategoryColor(category)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = color.copy(alpha = 0.15f)
        ) {
            Text(
                category,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = color,
                fontWeight = FontWeight.Black
            )
        }
    }
}

private fun slashCategoryColor(category: String): Color {
    return when (category.uppercase()) {
        "SESSION" -> ChatColors.offline
        "TOOLS", "SKILLS" -> ChatColors.online
        else -> ChatColors.linkBlue
    }
}

@Composable
private fun ComposerDock(
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    selectedModelText: String,
    isStreaming: Boolean,
    isStoppingRun: Boolean,
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
    onSend: () -> Unit,
    onAbort: () -> Unit
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
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DockPillButton(stringResource(R.string.chat_skills_extension), Icons.Default.AutoAwesome, enabled = hasActiveSession, onClick = onShowSkillSheet)
                Spacer(Modifier.weight(1f))
                DockPillButton(selectedModelText, Icons.Default.SmartToy, enabled = hasActiveSession, trailingIcon = Icons.Default.UnfoldMore, onClick = onOpenModelPicker)
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
                        enabled = (canCompose || isStreaming) && !isUploadingAttachment && !isStoppingRun && (messageText.isNotBlank() || attachments.isNotEmpty() || isStreaming),
                        isStreaming = isStreaming,
                        isStoppingRun = isStoppingRun,
                        onClick = { if (isStreaming && !isStoppingRun) onAbort() else onSend() }
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
private fun DockPillButton(
    text: String, 
    icon: ImageVector, 
    enabled: Boolean, 
    trailingIcon: ImageVector? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(999.dp),
        color = ChatColors.dockControl,
        border = BorderStroke(1.dp, ChatColors.dockBorder),
        modifier = Modifier.alpha(if (enabled) 1f else 0.55f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = Color.Black)
            if (trailingIcon != null) {
                Icon(trailingIcon, null, modifier = Modifier.size(16.dp), tint = Color.Black.copy(alpha = 0.35f))
            }
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
private fun SendButton(enabled: Boolean, isStreaming: Boolean = false, isStoppingRun: Boolean = false, onClick: () -> Unit) {
    val backgroundColor = when {
        isStoppingRun -> ChatColors.offline.copy(alpha = 0.72f)
        !enabled -> ChatColors.disabledAction
        isStreaming -> ChatColors.offline
        else -> ChatColors.linkBlue
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = backgroundColor,
        contentColor = if (enabled) Color.White else Color.White.copy(alpha = 0.9f),
        modifier = Modifier.size(42.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isStoppingRun) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else if (isStreaming) {
                Icon(Icons.Default.Stop, "Stop", modifier = Modifier.size(20.dp))
            } else {
                Icon(Icons.AutoMirrored.Filled.Send, "Send", modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    showInvocationProcess: Boolean,
    relayBaseUrl: String,
    accessToken: String
) {
    val isUser = message.role == MessageRole.user
    val isTool = message.role == MessageRole.tool || message.hasToolContent
    val visibleToolBlocks = message.visibleToolContentBlocks(showInvocationProcess)
    val shouldShowToolMessage = isTool && (
        visibleToolBlocks.isNotEmpty() ||
            (message.toolContentBlocks.isEmpty() && message.plainTextContent.isNotBlank())
        )
    if (isTool && !shouldShowToolMessage) return
    val syntheticFileBlocks = if (!isTool && message.fileContentBlocks.isEmpty()) {
        parseSendFileOutputBlocks(message.plainTextContent)
    } else {
        emptyList()
    }
    val fileBlocks = message.fileContentBlocks + syntheticFileBlocks
    val displayText = if (syntheticFileBlocks.isNotEmpty()) "" else message.plainTextContent
    val isStandaloneFileMessage = !isTool &&
        displayText.isBlank() &&
        fileBlocks.isNotEmpty() &&
        message.voiceContentBlocks.isEmpty()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (isTool) {
            ToolMessageCard(
                message = message,
                visibleToolBlocks = visibleToolBlocks,
                showInvocationProcess = showInvocationProcess
            )
            return@Column
        }

        if (isStandaloneFileMessage) {
            StandaloneFileMessage(
                blocks = fileBlocks,
                isUser = isUser,
                createdAt = message.createdAt,
                relayBaseUrl = relayBaseUrl,
                accessToken = accessToken
            )
            return@Column
        }
        if (!isUser && message.state == MessageState.streaming && (
                displayText.isBlank() ||
                displayText.startsWith("正在连接") ||
                displayText.startsWith("连接中断") ||
                displayText == "正在同步回复..." ||
                displayText == "正在同步最终内容..." ||
                displayText == "已完成，但未返回文本。"
            ) && fileBlocks.isEmpty() && message.voiceContentBlocks.isEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StreamingIndicatorBubble()
                Text(
                    "ClawLink",
                    modifier = Modifier.padding(horizontal = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = ChatColors.secondaryText,
                    fontWeight = FontWeight.Bold
                )
            }
            return@Column
        }

        Surface(
            color = if (isUser) ChatColors.userBubble else Color.White.copy(alpha = 0.96f),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(
                1.dp,
                if (isUser) Color.White.copy(alpha = 0.08f) else Color(0xFFE1E4EA)
            ),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.widthIn(max = 326.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (displayText.isNotEmpty()) {
                    MarkdownMessageText(
                        text = displayText,
                        textColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        linkColor = if (isUser) Color.White else MaterialTheme.colorScheme.primary,
                        textSizeSp = 15f,
                        onDarkBackground = isUser
                    )
                }

                fileBlocks.forEach { FileBlock(it, isUser, relayBaseUrl = relayBaseUrl, accessToken = accessToken) }
                message.voiceContentBlocks.forEach { VoiceBlock(it, isUser) }

                MessageFooter(
                    title = if (isUser) "You" else "ClawLink",
                    createdAt = message.createdAt,
                    isUser = isUser
                )
            }
        }
    }
}

@Composable
private fun MessageFooter(
    title: String,
    createdAt: String,
    isUser: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val footerColor = if (isUser) Color.White.copy(alpha = 0.72f) else ChatColors.secondaryText
        Text(
            title,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = footerColor,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.weight(1f))
        Text(
            formatChatTimestamp(createdAt),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = footerColor,
            fontWeight = FontWeight.Bold
        )
    }
}

private val chatTimestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

private fun formatChatTimestamp(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return "刚刚"

    runCatching { Instant.parse(trimmed) }.getOrNull()?.let { instant ->
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).format(chatTimestampFormatter)
    }

    val normalized = trimmed
        .removeSuffix("Z")
        .replace('T', ' ')
        .substringBefore('.')
    if (normalized.matches(Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}"""))) {
        return normalized
    }

    return try {
        LocalDateTime.parse(trimmed.substringBefore('.'), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .format(chatTimestampFormatter)
    } catch (_: DateTimeParseException) {
        trimmed
    } catch (_: IllegalArgumentException) {
        trimmed
    }
}

@Composable
private fun StandaloneFileMessage(
    blocks: List<RelayChatContentBlock>,
    isUser: Boolean,
    createdAt: String,
    relayBaseUrl: String,
    accessToken: String
) {
    val maxContentWidth = if (blocks.any { it.isImageFileBlock }) 290.dp else 326.dp
    Column(
        modifier = Modifier.width(IntrinsicSize.Max).widthIn(max = maxContentWidth),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        blocks.forEach { block ->
            FileBlock(
                block = block,
                isUser = isUser,
                standalone = true,
                relayBaseUrl = relayBaseUrl,
                accessToken = accessToken
            )
        }
        MessageFooter(
            title = if (isUser) "You" else "ClawLink",
            createdAt = createdAt,
            isUser = false,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun FileBlock(
    block: RelayChatContentBlock,
    isUser: Boolean,
    standalone: Boolean = false,
    relayBaseUrl: String,
    accessToken: String
) {
    val context = LocalContext.current
    val downloadUrl = block.fileDownloadURLString
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { resolveFileUrl(it, relayBaseUrl) }
    val primaryText = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface
    val secondaryText = if (isUser) Color.White.copy(alpha = 0.72f) else MaterialTheme.colorScheme.onSurfaceVariant
    val background = if (isUser) Color.White.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
    val border = if (isUser) Color.White.copy(alpha = 0.16f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)

    if (block.isImageFileBlock && downloadUrl != null) {
        val dimensions = imagePreviewDimensions(block)
        AsyncImage(
            model = imageRequest(context, downloadUrl, accessToken),
            contentDescription = block.fileDisplayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(dimensions.first)
                .height(dimensions.second)
                .clip(RoundedCornerShape(18.dp))
        )
        return
    }

    Surface(
        onClick = { downloadUrl?.let { openFileUrl(context, it) } },
        enabled = downloadUrl != null,
        shape = RoundedCornerShape(18.dp),
        color = background,
        border = BorderStroke(1.dp, border),
        modifier = if (standalone) Modifier.widthIn(max = 326.dp) else Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(11.dp),
                    color = if (isUser) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                ) {
                    Icon(
                        fileIcon(block),
                        null,
                        modifier = Modifier.padding(8.dp).size(16.dp),
                        tint = primaryText
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        block.fileDisplayName ?: stringResource(R.string.chat_attachment),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryText,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        fileSubtitle(block),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = secondaryText
                    )
                    block.status?.takeIf { it.isNotBlank() && it != block.fileStatusText }?.let {
                        Text(
                            it,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = secondaryText
                        )
                    }
                }
                if (downloadUrl != null) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        null,
                        modifier = Modifier.padding(top = 5.dp).size(15.dp),
                        tint = secondaryText
                    )
                }
            }
            block.expiresAt?.takeIf { it.isNotBlank() }?.let {
                Text(
                    "Expires $it",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryText
                )
            }
        }
    }
}

private fun fileIcon(block: RelayChatContentBlock): ImageVector {
    val mime = block.mimeType?.trim()?.lowercase().orEmpty()
    return when {
        mime.startsWith("image/") -> Icons.Default.Image
        mime.startsWith("audio/") -> Icons.Default.GraphicEq
        else -> Icons.Default.Description
    }
}

private fun fileSubtitle(block: RelayChatContentBlock): String {
    val parts = listOfNotNull(
        block.mimeType?.trim()?.takeIf { it.isNotEmpty() },
        block.fileStatusText?.trim()?.takeIf { it.isNotEmpty() }
    ).distinct()
    return parts.joinToString(" · ").ifBlank { block.status ?: "File" }
}

private fun imagePreviewDimensions(block: RelayChatContentBlock): Pair<Dp, Dp> {
    val maxWidth = 290.dp
    val maxHeight = 290.dp
    val width = block.imageWidth?.takeIf { it > 0 } ?: return 224.dp to maxHeight
    val height = block.imageHeight?.takeIf { it > 0 } ?: return 224.dp to maxHeight
    val ratio = width.toFloat() / height.toFloat()
    return if (ratio >= 1f) {
        maxWidth to (maxWidth / ratio).coerceAtMost(maxHeight)
    } else {
        (maxHeight * ratio).coerceAtMost(maxWidth) to maxHeight
    }
}

private fun imageRequest(context: Context, url: String, accessToken: String): ImageRequest {
    val builder = ImageRequest.Builder(context)
        .data(url)
        .crossfade(true)
    if (accessToken.isNotBlank()) {
        builder.addHeader("Authorization", "Bearer $accessToken")
    }
    return builder.build()
}

private fun resolveFileUrl(raw: String, relayBaseUrl: String): String {
    val trimmed = raw.trim()
    if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
        return trimmed
    }
    val base = relayBaseUrl.trim().trimEnd('/')
    if (base.isBlank()) return trimmed
    return "$base/${trimmed.trimStart('/')}"
}

private fun openFileUrl(context: Context, url: String) {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return
    val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun parseSendFileOutputBlocks(text: String): List<RelayChatContentBlock> {
    if (!text.contains("[send-file] uploaded")) return emptyList()
    val chunks = Regex("""(?=\[send-file]\s+uploaded\s+)""")
        .split(text)
        .map { it.trim() }
        .filter {
            it.startsWith("[send-file] uploaded") &&
                !it.startsWith("[send-file] uploaded chunk")
        }
    return chunks.mapNotNull { chunk ->
        val fileName = Regex("""\[send-file]\s+uploaded\s+(.+?)(?:\n|$)""").find(chunk)?.groupValues?.get(1)?.trim()
            ?: return@mapNotNull null
        if (fileName.startsWith("chunk ")) return@mapNotNull null
        val download = Regex("""(?m)^\s*download:\s*(\S+)""").find(chunk)?.groupValues?.get(1)
            ?: return@mapNotNull null
        val fileId = Regex("""(?m)^\s*file id:\s*(\S+)""").find(chunk)?.groupValues?.get(1)
        val gatewayId = Regex("""(?m)^\s*gateway:\s*(\S+)""").find(chunk)?.groupValues?.get(1)
        val sessionKey = Regex("""(?m)^\s*session:\s*(\S+)""").find(chunk)?.groupValues?.get(1)
        val sizeLabel = Regex("""(?m)^\s*size:\s*(.+)$""").find(chunk)?.groupValues?.get(1)?.trim()
        val expires = Regex("""(?m)^\s*expires:\s*(\S+)""").find(chunk)?.groupValues?.get(1)
        RelayChatContentBlock(
            type = "file",
            text = fileName,
            name = fileName,
            fileId = fileId,
            fileName = fileName,
            mimeType = inferMimeTypeFromName(fileName),
            sizeBytes = sizeLabel?.parseFileSizeLabel(),
            downloadUrl = download,
            expiresAt = expires,
            gatewayId = gatewayId,
            sessionKey = sessionKey
        )
    }
}

private fun inferMimeTypeFromName(fileName: String): String {
    val lower = fileName.lowercase()
    return when {
        lower.endsWith(".png") -> "image/png"
        lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
        lower.endsWith(".gif") -> "image/gif"
        lower.endsWith(".webp") -> "image/webp"
        lower.endsWith(".pdf") -> "application/pdf"
        lower.endsWith(".zip") -> "application/zip"
        lower.endsWith(".mp3") -> "audio/mpeg"
        lower.endsWith(".wav") -> "audio/wav"
        lower.endsWith(".m4a") -> "audio/mp4"
        lower.endsWith(".mp4") -> "video/mp4"
        lower.endsWith(".md") -> "text/markdown"
        lower.endsWith(".txt") -> "text/plain"
        else -> "application/octet-stream"
    }
}

private fun String.parseFileSizeLabel(): Int? {
    val match = Regex("""([0-9]+(?:\.[0-9]+)?)\s*([KMGT]?B)""", RegexOption.IGNORE_CASE).find(this.trim()) ?: return null
    val value = match.groupValues[1].toDoubleOrNull() ?: return null
    val multiplier = when (match.groupValues[2].uppercase()) {
        "KB" -> 1024.0
        "MB" -> 1024.0 * 1024.0
        "GB" -> 1024.0 * 1024.0 * 1024.0
        "TB" -> 1024.0 * 1024.0 * 1024.0 * 1024.0
        else -> 1.0
    }
    return (value * multiplier).toLong().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
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
private fun ToolMessageCard(
    message: ChatMessage,
    visibleToolBlocks: List<RelayChatContentBlock>,
    showInvocationProcess: Boolean
) {
    var expanded by remember(message.id, visibleToolBlocks.size) { mutableStateOf(false) }
    val cardTitle = if (showInvocationProcess && visibleToolBlocks.any { it.isToolCallBlock }) {
        "Tool output"
    } else {
        "Tool result"
    }
    val toolTitle = visibleToolBlocks.mapNotNull { it.resolvedName?.trim()?.ifEmpty { null } }
        .distinct()
        .takeIf { it.isNotEmpty() }
        ?.joinToString(", ")
        ?: message.toolDisplayName?.trim()?.ifEmpty { null }
        ?: "tool"
    val preview = visibleToolBlocks.firstNotNullOfOrNull { block ->
        block.toolDisplayContent(message.associatedToolCallBlock(block)).previewText().trim().ifEmpty { null }
    } ?: message.plainTextContent.trim().ifEmpty {
        when (message.state) {
            MessageState.completed -> "Completed"
            MessageState.failed -> "Failed"
            MessageState.streaming -> "Running"
        }
    }
    val statusColor = when (message.state) {
        MessageState.completed -> Color(0xFF5DCF7A) // Success
        MessageState.failed -> Color(0xFFF24E3E)    // Danger
        MessageState.streaming -> Color(0xFFF4A100)  // Warning
    }
    val statusIcon = when (message.state) {
        MessageState.completed -> Icons.Default.CheckCircle
        MessageState.failed -> Icons.Default.Close
        MessageState.streaming -> Icons.Default.Refresh
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF101827).copy(alpha = 0.98f), // chatChromeSurfaceStrong
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                onClick = { expanded = !expanded },
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        if (expanded) Icons.Default.ExpandMore else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        null,
                        modifier = Modifier.size(14.dp),
                        tint = Color.White.copy(alpha = 0.54f)
                    )
                    Icon(
                        Icons.Default.Bolt, 
                        null, 
                        modifier = Modifier.size(16.dp), 
                        tint = Color(0xFFF24E3E)
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                cardTitle, 
                                style = MaterialTheme.typography.bodyMedium, 
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                toolTitle,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = Color.White.copy(alpha = 0.54f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            preview,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = Color.White.copy(alpha = 0.48f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(statusIcon, null, modifier = Modifier.size(16.dp), tint = statusColor)
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (visibleToolBlocks.isEmpty()) {
                        TerminalBlock(
                            title = message.toolDisplayName ?: "Tool Output",
                            subtitle = null,
                            text = message.plainTextContent.ifBlank { preview },
                            mode = TerminalMode.Output,
                            isError = message.state == MessageState.failed
                        )
                    } else {
                        visibleToolBlocks.forEach { block ->
                            val associated = message.associatedToolCallBlock(block)
                            val title = when {
                                block.isToolCallBlock -> "COMMAND"
                                block.isError == true -> "ERROR"
                                else -> "OUTPUT"
                            }
                            val subtitle = associated?.toolDocumentPath() ?: block.toolDocumentPath()
                            TerminalBlock(
                                title = title,
                                subtitle = subtitle,
                                text = block.toolDisplayContent(associated).previewText(),
                                mode = if (block.isToolCallBlock) TerminalMode.Command else TerminalMode.Output,
                                isError = block.isError == true
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Tool", 
                    style = MaterialTheme.typography.labelSmall, 
                    color = Color.White.copy(alpha = 0.44f),
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.weight(1f))
                if (message.createdAt.isNotBlank()) {
                    Text(
                        formatChatTimestamp(message.createdAt),
                        style = MaterialTheme.typography.labelSmall, 
                        color = Color.White.copy(alpha = 0.44f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

private enum class TerminalMode { Command, Output }

@Composable
private fun TerminalBlock(
    title: String,
    subtitle: String?,
    text: String,
    mode: TerminalMode,
    isError: Boolean
) {
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val iconColor = if (isError) Color(0xFFF24E3E) else Color(0xFF5DCF7A)
    val bodyTextColor = if (isError) Color(0xFFFDC6BC) else Color.White.copy(alpha = 0.94f)
    val borderColor = if (isError) Color(0xFFF24E3E).copy(alpha = 0.34f) else Color(0xFF5DCF7A).copy(alpha = 0.24f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.06f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                if (mode == TerminalMode.Command) Icons.Default.Terminal else Icons.AutoMirrored.Filled.ArrowForward,
                null,
                modifier = Modifier.size(13.dp),
                tint = iconColor
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.92f)
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 9.sp),
                        color = Color.White.copy(alpha = 0.54f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(
                onClick = { 
                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(text))
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    null,
                    modifier = Modifier.size(11.dp),
                    tint = Color.White.copy(alpha = 0.84f)
                )
            }
        }

        // Body
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF14171C), Color(0xFF1C1F26)),
                        start = androidx.compose.ui.geometry.Offset.Zero,
                        end = androidx.compose.ui.geometry.Offset.Infinite
                    )
                )
                .horizontalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (mode == TerminalMode.Command) {
                    Text(
                        "$",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                        color = iconColor
                    )
                }
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, lineHeight = 18.sp),
                    color = bodyTextColor
                )
            }
        }
    }
}

@Composable
private fun PlainToolText(text: String, isError: Boolean) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isError) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.74f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Text(
            text,
            modifier = Modifier.padding(10.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun MarkdownMessageText(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color,
    linkColor: Color,
    textSizeSp: Float,
    onDarkBackground: Boolean
) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }
    SelectionContainer {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            blocks.forEach { block ->
                when (block) {
                    is AndroidMarkdownBlock.Paragraph -> MarkdownInlineText(
                        text = block.text,
                        textColor = textColor,
                        linkColor = linkColor,
                        textSizeSp = textSizeSp,
                        lineSpacingMultiplier = 1.08f
                    )
                    is AndroidMarkdownBlock.Heading -> MarkdownInlineText(
                        text = block.text,
                        textColor = textColor,
                        linkColor = linkColor,
                        textSizeSp = when (block.level) {
                            1 -> 22f
                            2 -> 19f
                            3 -> 17f
                            else -> 15f
                        },
                        bold = true
                    )
                    is AndroidMarkdownBlock.UnorderedList -> MarkdownUnorderedList(block.items, textColor, linkColor, textSizeSp)
                    is AndroidMarkdownBlock.OrderedList -> MarkdownOrderedList(block.items, textColor, linkColor, textSizeSp)
                    is AndroidMarkdownBlock.Blockquote -> MarkdownBlockquote(block.text, textColor, linkColor, textSizeSp)
                    is AndroidMarkdownBlock.CompactLines -> MarkdownCompactLines(block.lines, textColor, linkColor, textSizeSp)
                    AndroidMarkdownBlock.ThematicBreak -> MarkdownThematicBreak(textColor)
                    is AndroidMarkdownBlock.Code -> MarkdownCodeBlock(block.code, block.language, textColor, onDarkBackground)
                    is AndroidMarkdownBlock.Table -> MarkdownTable(block.table, textColor, onDarkBackground)
                }
            }
        }
    }
}

@Composable
private fun MarkdownCompactLines(lines: List<String>, textColor: Color, linkColor: Color, textSizeSp: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        lines.forEach { line ->
            MarkdownInlineText(
                text = line,
                textColor = textColor,
                linkColor = linkColor,
                textSizeSp = textSizeSp,
                lineSpacingMultiplier = 1.02f
            )
        }
    }
}

@Composable
private fun MarkdownInlineText(
    text: String,
    textColor: Color,
    linkColor: Color,
    textSizeSp: Float,
    bold: Boolean = false,
    lineSpacingMultiplier: Float = 1.0f
) {
    val context = LocalContext.current
    val markwon = remember(context) { Markwon.create(context) }
    AndroidView(
        factory = {
            TextView(it).apply {
                movementMethod = LinkMovementMethod.getInstance()
                includeFontPadding = false
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        update = { view ->
            view.setTextColor(textColor.toArgb())
            view.setLinkTextColor(linkColor.toArgb())
            view.textSize = textSizeSp
            view.setTypeface(null, if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            view.setLineSpacing(0f, lineSpacingMultiplier)
            markwon.setMarkdown(view, text.decodeEscapedMarkdownText())
        }
    )
}

@Composable
private fun MarkdownUnorderedList(items: List<String>, textColor: Color, linkColor: Color, textSizeSp: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Text("•", color = textColor, fontWeight = FontWeight.Bold, fontSize = (textSizeSp + 1).sp)
                MarkdownInlineText(item, textColor, linkColor, textSizeSp, lineSpacingMultiplier = 1.06f)
            }
        }
    }
}

@Composable
private fun MarkdownOrderedList(items: List<AndroidMarkdownOrderedListItem>, textColor: Color, linkColor: Color, textSizeSp: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Text(
                    "${item.number}.",
                    color = textColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = textSizeSp.sp,
                    modifier = Modifier.width(24.dp)
                )
                MarkdownInlineText(item.text, textColor, linkColor, textSizeSp, lineSpacingMultiplier = 1.06f)
            }
        }
    }
}

@Composable
private fun MarkdownBlockquote(text: String, textColor: Color, linkColor: Color, textSizeSp: Float) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .heightIn(min = 20.dp)
                .background(textColor.copy(alpha = 0.28f), RoundedCornerShape(2.dp))
        )
        MarkdownInlineText(text, textColor, linkColor, textSizeSp, lineSpacingMultiplier = 1.06f)
    }
}

@Composable
private fun MarkdownThematicBreak(textColor: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .height(1.dp)
            .background(textColor.copy(alpha = 0.22f))
    )
}

@Composable
private fun MarkdownCodeBlock(code: String, language: String?, textColor: Color, onDarkBackground: Boolean) {
    val borderColor = if (onDarkBackground) Color.White.copy(alpha = 0.22f) else Color(0xFFE1E4EA)
    val headerColor = if (onDarkBackground) Color.White.copy(alpha = 0.12f) else Color(0xFFF2F4F8)
    val bodyColor = if (onDarkBackground) Color.White.copy(alpha = 0.08f) else Color(0xFFF8FAFC)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bodyColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerColor)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    language?.normalizedCodeLanguage()?.uppercase() ?: "CODE",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = textColor.copy(alpha = 0.72f),
                    fontWeight = FontWeight.Bold
                )
            }
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    code,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = textColor
                )
            }
        }
    }
}

@Composable
private fun MarkdownTable(table: AndroidMarkdownTable, textColor: Color, onDarkBackground: Boolean) {
    val borderColor = if (onDarkBackground) Color.White.copy(alpha = 0.22f) else Color(0xFFE1E4EA)
    val headerBackground = if (onDarkBackground) Color.White.copy(alpha = 0.14f) else Color(0xFFF4F6FA)
    val bodyBackground = if (onDarkBackground) Color.White.copy(alpha = 0.06f) else Color.White
    val headerTextColor = if (onDarkBackground) textColor.copy(alpha = 0.9f) else Color(0xFF7A7E87)
    val columnWidths = remember(table) { table.columnWidths() }
    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = bodyBackground,
            border = BorderStroke(1.dp, borderColor)
        ) {
            Column {
                MarkdownTableRow(table.headers, columnWidths, headerTextColor, headerBackground, borderColor, FontWeight.SemiBold)
                table.rows.forEach { row ->
                    MarkdownTableRow(row, columnWidths, textColor, bodyBackground, borderColor, FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun MarkdownTableRow(
    values: List<String>,
    columnWidths: List<Dp>,
    textColor: Color,
    background: Color,
    borderColor: Color,
    fontWeight: FontWeight
) {
    Row {
        columnWidths.forEachIndexed { index, width ->
            Box(
                modifier = Modifier
                    .width(width)
                    .background(background)
            ) {
                Text(
                    values.getOrNull(index).orEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    color = textColor,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = fontWeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Canvas(modifier = Modifier.matchParentSize()) {
                    val stroke = 0.8.dp.toPx()
                    drawLine(borderColor, start = androidx.compose.ui.geometry.Offset(0f, size.height), end = androidx.compose.ui.geometry.Offset(size.width, size.height), strokeWidth = stroke)
                    drawLine(borderColor, start = androidx.compose.ui.geometry.Offset(size.width, 0f), end = androidx.compose.ui.geometry.Offset(size.width, size.height), strokeWidth = stroke)
                }
            }
        }
    }
}

private sealed class AndroidMarkdownBlock {
    data class Paragraph(val text: String) : AndroidMarkdownBlock()
    data class Heading(val level: Int, val text: String) : AndroidMarkdownBlock()
    data class UnorderedList(val items: List<String>) : AndroidMarkdownBlock()
    data class OrderedList(val items: List<AndroidMarkdownOrderedListItem>) : AndroidMarkdownBlock()
    data class Blockquote(val text: String) : AndroidMarkdownBlock()
    data class CompactLines(val lines: List<String>) : AndroidMarkdownBlock()
    data object ThematicBreak : AndroidMarkdownBlock()
    data class Code(val language: String?, val code: String) : AndroidMarkdownBlock()
    data class Table(val table: AndroidMarkdownTable) : AndroidMarkdownBlock()
}

private data class AndroidMarkdownOrderedListItem(val number: Int, val text: String)

private data class AndroidMarkdownTable(
    val headers: List<String>,
    val rows: List<List<String>>
) {
    val columnCount: Int = maxOf(headers.size, rows.maxOfOrNull { it.size } ?: 0)

    fun cellText(row: Int, column: Int): String {
        val source = if (row == 0) headers else rows.getOrNull(row - 1).orEmpty()
        return source.getOrNull(column).orEmpty()
    }

    fun columnWidths(): List<Dp> {
        return (0 until maxOf(columnCount, 1)).map { column ->
            val maxChars = (0..rows.size)
                .maxOf { row -> cellText(row, column).length }
                .coerceIn(6, 64)
            (maxChars * 8 + 28).dp
        }
    }
}

private fun parseMarkdownBlocks(raw: String): List<AndroidMarkdownBlock> {
    val decoded = raw.decodeEscapedMarkdownText()
    val statusSummary = decoded.normalizeOpenClawStatusSummary()
    if (statusSummary != decoded) {
        return listOf(AndroidMarkdownBlock.CompactLines(statusSummary.lines().filter { it.isNotBlank() }))
    }

    val normalized = decoded
        .normalizeMarkdownBlockBoundaries()
        .replace("\r\n", "\n")
        .replace("\r", "\n")
    val lines = normalized.lines()
    val blocks = mutableListOf<AndroidMarkdownBlock>()
    val paragraphBuffer = mutableListOf<String>()
    var index = 0

    fun flushParagraph() {
        val text = paragraphBuffer.joinToString("\n").trim()
        if (text.isNotEmpty() && !text.all { it == '#' }) {
            blocks.add(AndroidMarkdownBlock.Paragraph(text))
        }
        paragraphBuffer.clear()
    }

    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trim()

        if (trimmed.isEmpty()) {
            flushParagraph()
            index += 1
            continue
        }

        if (trimmed.startsWith("```")) {
            flushParagraph()
            val language = trimmed.removePrefix("```").trim().ifEmpty { null }
            val codeLines = mutableListOf<String>()
            index += 1
            while (index < lines.size) {
                val codeLine = lines[index]
                if (codeLine.trim().startsWith("```")) {
                    index += 1
                    break
                }
                codeLines.add(codeLine)
                index += 1
            }
            blocks.add(AndroidMarkdownBlock.Code(language, codeLines.joinToString("\n")))
            continue
        }

        if (isMarkdownTableHeader(index, lines)) {
            flushParagraph()
            val tableLines = mutableListOf(lines[index], lines[index + 1])
            index += 2
            while (index < lines.size && splitMarkdownTableRow(lines[index]).size >= 2) {
                tableLines.add(lines[index])
                index += 1
            }
            parseMarkdownTable(tableLines)?.let { blocks.add(AndroidMarkdownBlock.Table(it)) }
                ?: blocks.add(AndroidMarkdownBlock.Paragraph(tableLines.joinToString("\n")))
            continue
        }

        val heading = parseMarkdownHeading(trimmed)
        if (heading != null) {
            flushParagraph()
            blocks.add(AndroidMarkdownBlock.Heading(heading.first, heading.second))
            index += 1
            continue
        }

        if (trimmed.isMarkdownThematicBreak()) {
            flushParagraph()
            blocks.add(AndroidMarkdownBlock.ThematicBreak)
            index += 1
            continue
        }

        val unorderedList = parseMarkdownUnorderedList(index, lines)
        if (unorderedList != null) {
            flushParagraph()
            blocks.add(AndroidMarkdownBlock.UnorderedList(unorderedList.first))
            index = unorderedList.second
            continue
        }

        val orderedList = parseMarkdownOrderedList(index, lines)
        if (orderedList != null) {
            flushParagraph()
            blocks.add(AndroidMarkdownBlock.OrderedList(orderedList.first))
            index = orderedList.second
            continue
        }

        val blockquote = parseMarkdownBlockquote(index, lines)
        if (blockquote != null) {
            flushParagraph()
            blocks.add(AndroidMarkdownBlock.Blockquote(blockquote.first))
            index = blockquote.second
            continue
        }

        paragraphBuffer.add(line)
        index += 1
    }

    flushParagraph()
    return blocks.ifEmpty { listOf(AndroidMarkdownBlock.Paragraph(normalized)) }
}

private fun parseMarkdownHeading(trimmed: String): Pair<Int, String>? {
    val match = Regex("""^(#{1,6})\s+(.+)$""").find(trimmed) ?: return null
    return match.groupValues[1].length to match.groupValues[2]
}

private fun parseMarkdownUnorderedList(start: Int, lines: List<String>): Pair<List<String>, Int>? {
    val first = unorderedMarkdownItem(lines[start]) ?: return null
    val items = mutableListOf(first)
    var index = start + 1
    while (index < lines.size) {
        val trimmed = lines[index].trim()
        if (trimmed.isEmpty() || trimmed.startsWith("```") || parseMarkdownHeading(trimmed) != null || trimmed.isMarkdownThematicBreak() || isMarkdownTableHeader(index, lines)) break
        val next = unorderedMarkdownItem(lines[index])
        if (next != null) {
            items.add(next)
        } else {
            items[items.lastIndex] = items.last() + "\n" + trimmed
        }
        index += 1
    }
    return items to index
}

private fun parseMarkdownOrderedList(start: Int, lines: List<String>): Pair<List<AndroidMarkdownOrderedListItem>, Int>? {
    val first = orderedMarkdownItem(lines[start]) ?: return null
    val items = mutableListOf(first)
    var index = start + 1
    while (index < lines.size) {
        val trimmed = lines[index].trim()
        if (trimmed.isEmpty() || trimmed.startsWith("```") || parseMarkdownHeading(trimmed) != null || trimmed.isMarkdownThematicBreak() || isMarkdownTableHeader(index, lines)) break
        val next = orderedMarkdownItem(lines[index])
        if (next != null) {
            items.add(next)
        } else {
            val last = items.last()
            items[items.lastIndex] = last.copy(text = last.text + "\n" + trimmed)
        }
        index += 1
    }
    return items to index
}

private fun parseMarkdownBlockquote(start: Int, lines: List<String>): Pair<String, Int>? {
    val first = blockquoteMarkdownLine(lines[start]) ?: return null
    val collected = mutableListOf(first)
    var index = start + 1
    while (index < lines.size) {
        val next = blockquoteMarkdownLine(lines[index]) ?: break
        collected.add(next)
        index += 1
    }
    return collected.joinToString("\n") to index
}

private fun unorderedMarkdownItem(line: String): String? {
    return Regex("""^\s{0,3}[-*+]\s+(.+)$""").find(line)?.groupValues?.get(1)
}

private fun orderedMarkdownItem(line: String): AndroidMarkdownOrderedListItem? {
    val match = Regex("""^\s{0,3}(\d+)\.\s+(.+)$""").find(line) ?: return null
    return AndroidMarkdownOrderedListItem(match.groupValues[1].toIntOrNull() ?: 1, match.groupValues[2])
}

private fun blockquoteMarkdownLine(line: String): String? {
    return Regex("""^\s{0,3}>\s?(.*)$""").find(line)?.groupValues?.get(1)
}

private fun String.isMarkdownThematicBreak(): Boolean {
    val compact = trim().filterNot { it.isWhitespace() }
    return compact.length >= 3 && compact.all { it == compact.first() } && compact.first() in listOf('-', '*', '_')
}

private fun String.normalizeOpenClawStatusSummary(): String {
    val flattened = replace("\r\n", "\n")
        .replace("\r", "\n")
        .replace('\n', ' ')
        .replace(Regex("""\s+"""), " ")
        .trim()
    if (!flattened.looksLikeOpenClawStatusSummary()) return this

    val markerPattern = Regex(
        """(?=(?:🦞\s*)?OpenClaw\s|🧠\s*Model:|🧮\s*Tokens:|🗄️?\s*Cache:|📚\s*Context:|📊\s*Usage:|🧵\s*Session:|⚙️?\s*Execution:|🧞\s*Queue:)"""
    )
    val starts = markerPattern.findAll(flattened)
        .map { it.range.first }
        .distinct()
        .sorted()
        .toList()
    if (starts.size < 3) return this

    val items = starts.mapIndexedNotNull { index, start ->
        val end = starts.getOrNull(index + 1) ?: flattened.length
        flattened.substring(start, end)
            .trim()
            .trim('·')
            .trim()
            .takeIf { it.isNotBlank() }
    }
    if (items.size < 3) return this

    return items.joinToString("\n")
}

private fun String.looksLikeOpenClawStatusSummary(): Boolean {
    if (!contains("OpenClaw", ignoreCase = true)) return false
    val signals = listOf("Tokens:", "Context:", "Runtime:", "Session:", "Queue:", "Compactions:", "Usage:")
    return signals.count { contains(it, ignoreCase = true) } >= 3
}

private fun isMarkdownTableHeader(index: Int, lines: List<String>): Boolean {
    if (index + 1 >= lines.size) return false
    val headers = splitMarkdownTableRow(lines[index])
    val separators = splitMarkdownTableRow(lines[index + 1])
    return headers.size >= 2 && separators.size == headers.size && separators.all { it.isMarkdownTableSeparatorCell() }
}

private fun parseMarkdownTable(lines: List<String>): AndroidMarkdownTable? {
    val rows = lines.map { splitMarkdownTableRow(it) }.filter { it.isNotEmpty() }
    if (rows.size < 2 || !rows[1].all { it.isMarkdownTableSeparatorCell() }) return null
    return AndroidMarkdownTable(
        headers = rows[0].map { it.stripInlineMarkdownForTable() },
        rows = rows.drop(2).map { row -> row.map { it.stripInlineMarkdownForTable() } }
    )
}

private fun splitMarkdownTableRow(line: String): List<String> {
    var content = line.trim()
    if (content.isEmpty()) return emptyList()
    if (content.first() == '|') content = content.drop(1)
    if (content.lastOrNull() == '|') content = content.dropLast(1)

    val cells = mutableListOf<String>()
    val current = StringBuilder()
    var index = 0
    var inCodeSpan = false
    while (index < content.length) {
        val ch = content[index]
        if (ch == '\\' && index + 1 < content.length && content[index + 1] in listOf('|', '\\', '`')) {
            current.append(content[index + 1])
            index += 2
            continue
        }
        if (ch == '`') inCodeSpan = !inCodeSpan
        if (ch == '|' && !inCodeSpan) {
            cells.add(current.toString().trim())
            current.clear()
        } else {
            current.append(ch)
        }
        index += 1
    }
    cells.add(current.toString().trim())
    return cells
}

private fun String.isMarkdownTableSeparatorCell(): Boolean {
    val compact = trim().removePrefix(":").removeSuffix(":")
    return compact.length >= 3 && compact.all { it == '-' }
}

private fun String.stripInlineMarkdownForTable(): String {
    return decodeEscapedMarkdownText()
        .replace(Regex("""`([^`]+)`"""), "$1")
        .replace(Regex("""\*\*([^*]+)\*\*"""), "$1")
        .replace(Regex("""__([^_]+)__"""), "$1")
        .replace(Regex("""\[(.+?)]\([^)]+\)"""), "$1")
        .trim()
}

private fun String.normalizeMarkdownBlockBoundaries(): String {
    val codeRegex = Regex("""```[\s\S]*?```""")
    val matches = codeRegex.findAll(this).toList()
    if (matches.isEmpty()) return normalizeMarkdownOutsideCode()

    val output = StringBuilder()
    var cursor = 0
    matches.forEach { match ->
        if (match.range.first > cursor) {
            output.append(substring(cursor, match.range.first).normalizeMarkdownOutsideCode())
        }
        output.append(match.value)
        cursor = match.range.last + 1
    }
    if (cursor < length) {
        output.append(substring(cursor).normalizeMarkdownOutsideCode())
    }
    return output.toString()
}

private fun String.normalizeMarkdownOutsideCode(): String {
    return replace(Regex("""([^\n])(```)"""), "$1\n$2")
        .replace(Regex("""([^\n])(#{1,6}\s)"""), "$1\n$2")
        .replace(Regex("""([：:。！？.!?])\s*(\d+\.\s)"""), "$1\n$2")
        .replace(Regex("""([：:。！？.!?])\s*([-*+]\s)"""), "$1\n$2")
        .replace(Regex("""([：:。！？.!?])\s*(>\s)"""), "$1\n$2")
}

private sealed class ToolDisplayContent {
    data class Markdown(val text: String) : ToolDisplayContent()
    data class Code(val language: String?, val code: String) : ToolDisplayContent()
    data class TerminalCommand(val command: String, val workdir: String?) : ToolDisplayContent()
    data class TerminalOutput(val text: String, val isError: Boolean, val workdir: String?) : ToolDisplayContent()
    data class Text(val text: String) : ToolDisplayContent()

    fun previewText(): String = when (this) {
        is Markdown -> text.condensedToolPreview()
        is Code -> {
            if (language?.lowercase() == "json") {
                jsonPreviewSummary(code)?.let { "JSON: $it" } ?: "JSON"
            } else {
                val preview = code.condensedToolPreview()
                val label = language?.trim()?.ifEmpty { null }
                when {
                    preview.isBlank() && label != null -> label
                    label != null -> "$label: $preview"
                    preview.isBlank() -> "Code"
                    else -> preview
                }
            }
        }
        is TerminalCommand -> listOf(command.condensedToolPreview().ifBlank { "Shell command" }, workdir).filter { !it.isNullOrBlank() }.joinToString(" - ")
        is TerminalOutput -> {
            val prefix = if (isError) "Shell error" else "Shell output"
            listOf(text.condensedToolPreview().ifBlank { prefix }.let { if (it == prefix) it else "$prefix: $it" }, workdir).filter { !it.isNullOrBlank() }.joinToString(" - ")
        }
        is Text -> jsonPreviewSummary(text)?.let { "JSON: $it" } ?: text.condensedToolPreview().ifBlank { text.trim() }
    }
}

private fun ChatMessage.visibleToolContentBlocks(showInvocationProcess: Boolean): List<RelayChatContentBlock> {
    return if (showInvocationProcess) toolContentBlocks else toolContentBlocks.filter { it.isToolResultBlock }
}

private fun ChatMessage.associatedToolCallBlock(block: RelayChatContentBlock): RelayChatContentBlock? {
    if (block.isToolCallBlock) return block
    val callId = block.resolvedToolCallId()?.trim()
    if (!callId.isNullOrEmpty()) {
        toolContentBlocks.firstOrNull { it.isToolCallBlock && it.resolvedToolCallId() == callId }?.let { return it }
    }
    return toolContentBlocks.firstOrNull { it.isToolCallBlock }
}

private fun RelayChatContentBlock.toolDisplayContent(associatedToolCallBlock: RelayChatContentBlock? = null): ToolDisplayContent {
    val documentPath = toolDocumentPath() ?: associatedToolCallBlock?.toolDocumentPath()
    markdownDisplayText(documentPath)?.let { return ToolDisplayContent.Markdown(it) }
    structuredToolSnippet()?.let { return it }

    val toolName = (associatedToolCallBlock?.resolvedName ?: resolvedName).orEmpty().trim().lowercase()
    val workdir = toolWorkingDirectory(associatedToolCallBlock)
    val fallback = displayText()
        ?: resolvedPayload()?.renderedText(toolPreferredKeys)
        ?: resolvedArguments()?.renderedText(toolPreferredKeys)
        ?: ""
    val trimmed = fallback.trim()
    if (trimmed.isEmpty()) return ToolDisplayContent.Text("Completed")

    prettyPrintedJson(trimmed)?.let { return ToolDisplayContent.Code("json", it) }

    if (toolName.isCommandToolName()) {
        if (isToolCallBlock) {
            val command = toolCommandText()
            if (!command.isNullOrBlank()) return ToolDisplayContent.TerminalCommand(command.trim(), workdir)
        }
        if (isToolResultBlock) {
            return ToolDisplayContent.TerminalOutput(trimmed, isError == true, workdir)
        }
    }

    if ((isToolCallBlock || toolName == "shell") && trimmed.looksLikeCommandLine()) {
        return ToolDisplayContent.TerminalCommand(trimmed, workdir)
    }
    if (trimmed.looksLikeCodeSnippet()) {
        return ToolDisplayContent.Code(null, trimmed)
    }
    return ToolDisplayContent.Text(trimmed)
}

private val toolPreferredKeys = listOf("content", "markdown", "text", "body", "message", "value", "result", "output")

private fun RelayChatContentBlock.markdownDisplayText(documentPath: String?): String? {
    val candidate = when {
        isToolCallBlock -> resolvedArguments()?.renderedText(listOf("content", "markdown", "text", "body", "message", "value"))
        isToolResultBlock -> resolvedPayload()?.renderedText(listOf("content", "markdown", "text", "body", "message", "value")) ?: displayText()
        else -> displayText()
    }?.normalizeToolDisplayText()?.trim().orEmpty()
    if (candidate.isBlank()) return null
    if (hasExplicitMarkdownHint(documentPath) || candidate.shouldRenderToolMarkdown(resolvedName, documentPath)) return candidate
    return null
}

private fun RelayChatContentBlock.structuredToolSnippet(): ToolDisplayContent? {
    listOf(resolvedArguments(), resolvedPayload()).forEach { value ->
        val code = value?.stringValuesForKeys(listOf("code", "content", "text", "body", "script", "source", "value", "message", "result", "output"))?.trim()
        if (code.isNullOrBlank()) return@forEach
        val language = value.stringValuesForKeys(listOf("language", "lang", "syntax", "codeLanguage", "languageId", "format"))?.normalizedCodeLanguage()
        when {
            language in listOf("markdown", "md", "mdx") -> return ToolDisplayContent.Markdown(code)
            language in listOf("shell", "bash", "sh", "zsh", "terminal") || code.looksLikeCommandLine() -> return ToolDisplayContent.TerminalCommand(code, toolWorkingDirectory(null))
            language != null || code.looksLikeCodeSnippet() -> return ToolDisplayContent.Code(language, code)
        }
    }
    return null
}

private fun RelayChatContentBlock.displayText(): String? {
    return listOfNotNull(
        text,
        result?.renderedText(toolPreferredKeys),
        partialResult?.renderedText(toolPreferredKeys),
        content?.renderedText(toolPreferredKeys),
        output?.renderedText(toolPreferredKeys),
        error?.renderedText(toolPreferredKeys),
        status
    ).firstOrNull { it.isNotBlank() }?.trim()
}

private fun RelayChatContentBlock.resolvedArguments() = arguments ?: args
private fun RelayChatContentBlock.resolvedPayload() = result ?: partialResult ?: content ?: output ?: error
private fun RelayChatContentBlock.resolvedToolCallId() = toolCallId ?: toolUseId

private fun RelayChatContentBlock.toolDocumentPath(): String? {
    val keys = listOf("path", "filePath", "file_path", "targetPath", "target_path")
    return resolvedArguments()?.stringValuesForKeys(keys) ?: resolvedPayload()?.stringValuesForKeys(keys)
}

private fun RelayChatContentBlock.toolCommandText(): String? {
    val keys = listOf("command", "cmd", "script", "code", "input", "text", "value")
    return resolvedArguments()?.stringValuesForKeys(keys) ?: args?.stringValuesForKeys(keys) ?: text
}

private fun RelayChatContentBlock.toolWorkingDirectory(associatedToolCallBlock: RelayChatContentBlock?): String? {
    val keys = listOf("workdir", "cwd", "workingDirectory", "working_directory")
    return resolvedArguments()?.stringValuesForKeys(keys)
        ?: resolvedPayload()?.stringValuesForKeys(keys)
        ?: associatedToolCallBlock?.resolvedArguments()?.stringValuesForKeys(keys)
        ?: associatedToolCallBlock?.resolvedPayload()?.stringValuesForKeys(keys)
}

private fun RelayChatContentBlock.hasExplicitMarkdownHint(documentPath: String?): Boolean {
    if (documentPath?.isMarkdownDocumentPath() == true) return true
    val hints = listOf(
        resolvedArguments()?.stringValuesForKeys(listOf("format", "contentFormat", "mimeType", "mediaType", "type", "language", "lang")),
        resolvedPayload()?.stringValuesForKeys(listOf("format", "contentFormat", "mimeType", "mediaType", "type", "language", "lang"))
    )
    return hints.any { it?.trim()?.lowercase() in listOf("markdown", "md", "mdx", "text/markdown") }
}

private fun String.shouldRenderToolMarkdown(toolName: String?, documentPath: String?): Boolean {
    if (documentPath?.isMarkdownDocumentPath() == true) return true
    val normalized = normalizeToolDisplayText().trim()
    if (normalized.isBlank()) return false
    if (normalized.looksLikeMarkdownStructure()) return true
    val normalizedToolName = toolName?.trim()?.lowercase().orEmpty()
    if (normalizedToolName in listOf("read", "write", "append", "prepend", "insert", "edit", "multiedit", "replace")) {
        return normalized.looksLikeMarkdownDocument()
    }
    return normalized.looksLikeMarkdownDocument()
}

private fun String.looksLikeMarkdownStructure(): Boolean {
    val patterns = listOf(
        Regex("""(?m)^#{1,6}\s+\S"""),
        Regex("""(?m)^[-*+]\s+\S"""),
        Regex("""(?m)^\d+\.\s+\S"""),
        Regex("""(?m)^>\s+\S"""),
        Regex("""(?m)^```"""),
        Regex("""\[[^\]]+]\([^)]+\)"""),
        Regex("""!\[[^]]*]\([^)]+\)"""),
        Regex("""(?m)^\|.+\|\s*$""")
    )
    return patterns.any { it.containsMatchIn(this) } || contains("**") || contains("__") || contains("`")
}

private fun String.looksLikeMarkdownDocument(): Boolean {
    val lines = trim().lines().map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.isEmpty()) return false
    var score = 0
    lines.take(24).forEach { line ->
        when {
            Regex("""^#{1,6}\s+\S""").containsMatchIn(line) -> score += 2
            Regex("""^[-*+]\s+\S""").containsMatchIn(line) -> score += 1
            Regex("""^\d+\.\s+\S""").containsMatchIn(line) -> score += 1
            Regex("""^>\s+\S""").containsMatchIn(line) -> score += 1
            line.startsWith("|") && line.endsWith("|") && line.count { it == '|' } >= 2 -> score += 2
            Regex("""\[[^\]]+]\([^)]+\)""").containsMatchIn(line) -> score += 1
            line.contains("**") || line.contains("__") -> score += 1
        }
    }
    if (lines.first() == "---" || lines.first() == "+++") score += 1
    return score > 0
}

private fun String.looksLikeCommandLine(): Boolean {
    val normalized = trim()
    if (normalized.startsWith("$ ") || normalized.startsWith("> ")) return true
    val prefixes = listOf(
        "git ", "npm ", "pnpm ", "yarn ", "bun ", "npx ", "node ",
        "python ", "python3 ", "pip ", "uv ", "curl ", "wget ", "brew ",
        "docker ", "kubectl ", "ssh ", "scp ", "cd ", "ls ", "pwd ",
        "mkdir ", "rm ", "cp ", "mv ", "cat ", "sed ", "awk ",
        "xcodebuild ", "swift ", "bash ", "sh ", "zsh "
    )
    return prefixes.any { normalized.startsWith(it) }
}

private fun String.looksLikeCodeSnippet(): Boolean {
    val trimmed = trim()
    if (trimmed.isBlank() || trimmed.contains("```")) return false
    val codeSignals = listOf("fun ", "class ", "struct ", "import ", "const ", "let ", "var ", "=>", "</", "{", "}", ";")
    val lines = trimmed.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val signalCount = lines.take(20).sumOf { line -> codeSignals.count { line.contains(it) } }
    return signalCount >= 2 || (lines.size >= 3 && signalCount >= 1)
}

private fun String.isMarkdownDocumentPath(): Boolean {
    val lower = trim().lowercase()
    return lower.endsWith(".md") || lower.endsWith(".markdown") || lower.endsWith(".mdx")
}

private fun String.isCommandToolName(): Boolean {
    return this in listOf("shell", "bash", "terminal", "exec", "run", "command", "python", "node")
}

private fun String.normalizedCodeLanguage(): String? {
    val lower = trim().lowercase()
    return when (lower) {
        "text/markdown" -> "markdown"
        "javascript" -> "js"
        "typescript" -> "ts"
        "shellscript" -> "shell"
        "" -> null
        else -> lower
    }
}

private fun String.normalizeToolDisplayText(): String {
    return decodeEscapedMarkdownText()
        .replace("\r\n", "\n")
        .replace("\r", "\n")
        .trim()
}

private fun String.decodeEscapedMarkdownText(): String {
    return replace("\\n", "\n")
        .replace("\\t", "\t")
        .replace("\\\"", "\"")
}

private fun String.condensedToolPreview(): String {
    val firstLine = lines().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return ""
    val cleaned = firstLine
        .replace(Regex("""^\s{0,3}(#{1,6}\s+|[-*+]\s+|\d+\.\s+|>\s+)"""), "")
        .trim()
        .ifEmpty { firstLine }
    return if (cleaned.length > 80) cleaned.take(80) + "..." else cleaned
}

private val prettyJson = Json { prettyPrint = true }

private fun prettyPrintedJson(text: String): String? {
    return runCatching {
        val element = Json.parseToJsonElement(text)
        prettyJson.encodeToString(JsonElement.serializer(), element)
    }.getOrNull()
}

private fun jsonPreviewSummary(text: String): String? {
    val element = runCatching { Json.parseToJsonElement(text) }.getOrNull() ?: return null
    return when (element) {
        is kotlinx.serialization.json.JsonObject -> element.entries.take(3).joinToString(", ") { (key, value) -> "$key=${value.jsonPreviewValue()}" }
        is kotlinx.serialization.json.JsonArray -> "${element.size} items"
        else -> element.jsonPreviewValue()
    }.takeIf { it.isNotBlank() }
}

private fun JsonElement.jsonPreviewValue(): String {
    return when (this) {
        is JsonPrimitive -> contentOrNull ?: toString()
        is kotlinx.serialization.json.JsonObject -> "{...}"
        is kotlinx.serialization.json.JsonArray -> "[${size}]"
        else -> toString()
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

private val ChatSessionItem.normalizedSessionKey: String
    get() = sessionKey.trim().ifBlank { "main" }

private val ChatSessionItem.displayTitle: String
    get() = listOf(displayName, label, derivedTitle)
        .firstOrNull { !it.isNullOrBlank() }
        ?.trim()
        ?: sessionDisplayName(sessionKey)

private val ChatSessionItem.activityText: String
    get() = lastActivityAt?.trim()?.takeIf { it.isNotEmpty() }?.let { "最近活动 $it" }.orEmpty()

private fun sessionDisplayName(key: String): String {
    val normalized = key.trim().ifBlank { "main" }
    return when (normalized) {
        "main" -> "主会话"
        else -> normalized.removePrefix("session_").takeLast(8).ifBlank { "Session" }
    }
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
    val record = chatStore.uploadAttachment(
        gatewayId = gatewayId,
        fileName = fileName,
        mimeType = mimeType,
        bytes = bytes,
        sha256 = sha256Hex(bytes)
    )
    return UploadedAttachment(
        fileId = record.fileId,
        fileName = record.fileName,
        mimeType = record.mimeType,
        sizeBytes = record.sizeBytes,
        downloadUrl = record.downloadPath,
        expiresAt = record.expiresAt,
        imageWidth = record.imageWidth,
        imageHeight = record.imageHeight,
        senderDisplayName = record.senderDisplayName
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
