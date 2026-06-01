package com.rethinkingstudio.clawlink.ui.screens.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.gateway.AggregateStatus
import com.rethinkingstudio.clawlink.core.models.gateway.GatewayType
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
import com.rethinkingstudio.clawlink.core.state.chat.ChatStore
import com.rethinkingstudio.clawlink.core.state.chat.RemoteAttachmentCache
import com.rethinkingstudio.clawlink.core.state.chat.RemoteImageCache
import com.rethinkingstudio.clawlink.core.state.chat.RemoteImageSizeCache
import com.rethinkingstudio.clawlink.core.state.chat.visibleContextUsageLine
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import com.rethinkingstudio.clawlink.core.state.model.ModelStore
import com.rethinkingstudio.clawlink.ui.components.ClawLinkAlertDialog
import com.rethinkingstudio.clawlink.ui.components.ClawLinkScaffold
import com.rethinkingstudio.clawlink.ui.screens.chat.components.ChatSessionSwitchLoadingOverlay
import com.rethinkingstudio.clawlink.ui.screens.chat.components.ChatTopBar
import com.rethinkingstudio.clawlink.ui.screens.chat.components.ComposerDock
import com.rethinkingstudio.clawlink.ui.screens.chat.components.DocumentFullscreenOverlay
import com.rethinkingstudio.clawlink.ui.screens.chat.components.GatewaySheetOverlay
import com.rethinkingstudio.clawlink.ui.screens.chat.components.ImageFullscreenOverlay
import com.rethinkingstudio.clawlink.ui.screens.chat.components.ModelPickerSheetOverlay
import com.rethinkingstudio.clawlink.ui.screens.chat.components.SkillExpansionSheetOverlay
import com.rethinkingstudio.clawlink.ui.screens.chat.components.SlashAction
import com.rethinkingstudio.clawlink.ui.screens.chat.components.slashCommandSuggestions
import com.rethinkingstudio.clawlink.ui.screens.chat.components.SlashCommandPanel
import com.rethinkingstudio.clawlink.ui.screens.chat.components.VoiceInputOverlay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ChatScreen(
    chatStore: ChatStore,
    gatewayStore: GatewayStore,
    modelStore: ModelStore,
    onBack: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    onOpenUsageGuide: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember(chatStore, gatewayStore, modelStore) {
        ChatViewModel(chatStore, gatewayStore, modelStore, scope)
    }

    val chatState by chatStore.state.collectAsState()
    val gatewayState by gatewayStore.state.collectAsState()
    val modelState by modelStore.state.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val dismissKeyboard = {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        Unit
    }
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)
    val recordAudioPermissionState = rememberPermissionState(android.Manifest.permission.RECORD_AUDIO)

    val gatewayId = gatewayState.selectedGatewayId
    val hasSelectedGateway = gatewayState.selectedGateway != null
    val isChatChainReady = gatewayState.isSelectedGatewayChatChainReady
    val hasActiveSession = hasSelectedGateway && chatState.currentSessionKey.isNotBlank() && isChatChainReady
    val selectedGatewayType = gatewayState.selectedGateway?.gatewayType ?: GatewayType.openclaw
    val showsSkillExpansionControls = showsSkillExpansionControlsForGateway(selectedGatewayType)
    val showsModelPicker = showsModelPickerForGateway(selectedGatewayType)
    LaunchedEffect(gatewayId, showsModelPicker) {
        if (showsModelPicker && !gatewayId.isNullOrBlank()) {
            modelStore.loadModels(gatewayId)
        }
    }
    val connectionIssueMessage = remember(
        hasSelectedGateway,
        gatewayState.appRelayStatus,
        gatewayState.selectedGatewayAggregateStatus,
        isChatChainReady,
        selectedGatewayType
    ) {
        when {
            !hasSelectedGateway -> null
            gatewayState.appRelayStatus == AggregateStatus.offline -> "无法连接到 Relay 服务器，请确认服务已启动且地址可访问。"
            !isChatChainReady -> if (selectedGatewayType == GatewayType.hermes) {
                "当前链路未全通，请确认 Relay 已连接到主机且 Hermes Agent 已启动。"
            } else {
                "当前链路未全通，请确认 Relay 已连接到主机且 OpenClaw 已启动。"
            }
            else -> null
        }
    }
    val statusAlertMessage = connectionIssueMessage ?: chatState.errorMessage ?: gatewayState.errorMessage ?: viewModel.composerNotice
    val isStatusAlertError = connectionIssueMessage != null || chatState.errorMessage != null || gatewayState.errorMessage != null
    var dismissedStatusAlertMessage by remember { mutableStateOf<String?>(null) }
    var lastObservedVisibleMessageCount by remember { mutableStateOf(0) }
    var lastObservedMessageSignature by remember { mutableStateOf("") }
    var lastObservedFirstVisibleMessageId by remember { mutableStateOf<String?>(null) }
    var lastObservedUserMessageIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var hasPendingMessagesBelow by remember { mutableStateOf(false) }
    var activeGatewaySwitchId by remember { mutableStateOf<String?>(null) }
    var lastAutoHistoryRequestKey by remember { mutableStateOf<String?>(null) }
    val visibleMessagesForScroll = remember(
        chatState.messages,
        chatState.showInvocationProcess
    ) {
        chatState.messages.filter { message ->
            message.shouldDisplayInChat(
                showInvocationProcess = chatState.showInvocationProcess
            ) ||
                message.state == MessageState.streaming && message.role == MessageRole.assistant
        }
    }
    val messageUpdateSignature = remember(visibleMessagesForScroll) {
        visibleMessagesForScroll.joinToString(separator = "\u001F") { message ->
            listOf(
                message.id,
                message.role.name,
                message.state.name,
                message.runId,
                message.content,
                message.contentBlocks.hashCode().toString()
            ).joinToString(separator = "\u001E")
        }
    }
    val isNearListBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems == 0) return@derivedStateOf true
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            val bottomGap = layoutInfo.viewportEndOffset - (lastVisible.offset + lastVisible.size)
            lastVisible.index >= totalItems - 1 && bottomGap <= with(density) { 40.dp.roundToPx() }
        }
    }

    suspend fun scrollChatToBottom(animated: Boolean = true) {
        val totalItems = listState.layoutInfo.totalItemsCount
        if (totalItems <= 0) return
        if (animated) {
            listState.animateScrollToItem(totalItems - 1)
        } else {
            listState.scrollToItem(totalItems - 1)
        }
        hasPendingMessagesBelow = false
    }

    fun requestBottomScrollForComposerFocus() {
        if (chatState.messages.isEmpty()) return
        hasPendingMessagesBelow = false
        scope.launch {
            scrollChatToBottom(animated = true)
            delay(120)
            scrollChatToBottom(animated = true)
            delay(220)
            scrollChatToBottom(animated = true)
        }
    }

    suspend fun loadAutoHistoryOnce(
        request: GatewayHistoryRequest,
        keepSwitchingOverlay: Boolean = true
    ) {
        val requestKey = gatewayHistoryRequestKey(request)
        if (lastAutoHistoryRequestKey == requestKey) return
        lastAutoHistoryRequestKey = requestKey
        try {
            chatStore.loadHistory(
                request.gatewayId,
                request.sessionKey,
                keepSwitchingOverlay = keepSwitchingOverlay
            )
        } catch (e: CancellationException) {
            if (lastAutoHistoryRequestKey == requestKey) {
                lastAutoHistoryRequestKey = null
            }
            throw e
        }
    }

    LaunchedEffect(statusAlertMessage) {
        if (statusAlertMessage == null) {
            dismissedStatusAlertMessage = null
        }
    }

    LaunchedEffect(context) {
        RemoteImageSizeCache.init(context.applicationContext)
        RemoteImageCache.init(context.applicationContext)
        RemoteAttachmentCache.init(context.applicationContext)
        com.rethinkingstudio.clawlink.core.state.chat.VoicePlaybackReadStore.init(context.applicationContext)
        com.rethinkingstudio.clawlink.core.state.chat.TimelinePersistenceMiddleware.init(context.applicationContext)
        chatStore.rehydrateTimelineState()
    }

    DisposableEffect(viewModel) {
        onDispose {
            viewModel.disposeVoiceInput()
        }
    }

    val voiceOverlayActive = viewModel.voiceInputPhase != VoiceInputPhase.Idle
    val imagePreview = viewModel.imagePreview
    val documentPreview = viewModel.documentPreview
    val systemBarStyle = chatSystemBarStyle(
        normalStatusBarColor = MaterialTheme.colorScheme.background,
        normalNavigationBarColor = ChatColors.dockSurface,
        modalOverlayActive = voiceOverlayActive || imagePreview != null || documentPreview != null
    )

    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        @Suppress("DEPRECATION")
        window.statusBarColor = systemBarStyle.statusBarColor.toArgb()
        @Suppress("DEPRECATION")
        window.navigationBarColor = systemBarStyle.navigationBarColor.toArgb()
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = systemBarStyle.useDarkStatusBarIcons
        controller.isAppearanceLightNavigationBars = systemBarStyle.useDarkNavigationBarIcons
    }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            viewModel.isUploadingAttachment = true
            viewModel.composerNotice = null
            try {
                val imported = ChatFileUtils.importPickedAttachments(context, uris)
                viewModel.composerAttachments = viewModel.composerAttachments + imported
                if (imported.isEmpty()) {
                    viewModel.composerNotice = context.getString(R.string.chat_attachment_import_failed)
                }
            } catch (e: Exception) {
                viewModel.composerNotice = context.getString(R.string.chat_attachment_import_failed_with_reason, e.message ?: choose("Unknown error", "未知错误"))
            } finally {
                viewModel.isUploadingAttachment = false
                viewModel.showAttachmentMenu = false
            }
        }
    }
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            viewModel.isUploadingAttachment = true
            viewModel.composerNotice = null
            try {
                val imported = ChatFileUtils.importPickedAttachments(context, uris)
                viewModel.composerAttachments = viewModel.composerAttachments + imported
                if (imported.isEmpty()) {
                    viewModel.composerNotice = context.getString(R.string.chat_attachment_import_failed)
                }
            } catch (e: Exception) {
                viewModel.composerNotice = context.getString(R.string.chat_attachment_import_failed_with_reason, e.message ?: choose("Unknown error", "未知错误"))
            } finally {
                viewModel.isUploadingAttachment = false
                viewModel.showAttachmentMenu = false
            }
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap == null) {
            viewModel.showAttachmentMenu = false
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            viewModel.isUploadingAttachment = true
            viewModel.composerNotice = null
            try {
                val imported = ChatFileUtils.importCapturedImage(context, bitmap)
                viewModel.composerAttachments = viewModel.composerAttachments + imported
            } catch (e: Exception) {
                viewModel.composerNotice = context.getString(R.string.chat_attachment_import_failed_with_reason, e.message ?: choose("Unknown error", "未知错误"))
            } finally {
                viewModel.isUploadingAttachment = false
                viewModel.showAttachmentMenu = false
            }
        }
    }
    val localSlashActions = remember(
        viewModel.messageText,
        gatewayState.selectedGateway?.slashCommands,
        gatewayState.selectedGateway?.gatewayType
    ) {
        slashCommandSuggestions(
            input = viewModel.messageText,
            remoteCommands = gatewayState.selectedGateway?.slashCommands,
            includeDefaultActions = gatewayState.selectedGateway?.gatewayType != GatewayType.hermes
        )
    }
    var lazyHermesSlashActions by remember(gatewayId) { mutableStateOf<List<SlashAction>>(emptyList()) }
    var lazyHermesSlashNextOffset by remember(gatewayId) { mutableStateOf<Int?>(null) }
    var lazyHermesSlashQuery by remember(gatewayId) { mutableStateOf("") }
    var isLoadingHermesSlashCommands by remember(gatewayId) { mutableStateOf(false) }
    LaunchedEffect(gatewayId, selectedGatewayType, viewModel.messageText) {
        val normalizedInput = viewModel.messageText.trim()
        if (gatewayId == null || selectedGatewayType != GatewayType.hermes || !normalizedInput.startsWith("/")) {
            lazyHermesSlashActions = emptyList()
            lazyHermesSlashNextOffset = null
            lazyHermesSlashQuery = ""
            isLoadingHermesSlashCommands = false
            return@LaunchedEffect
        }
        lazyHermesSlashActions = emptyList()
        lazyHermesSlashNextOffset = null
        lazyHermesSlashQuery = normalizedInput
        delay(80)
        isLoadingHermesSlashCommands = true
        try {
            val page = runCatching {
                gatewayStore.fetchSlashCommands(gatewayId, normalizedInput, limit = 16, offset = 0)
            }.getOrNull()
            val remoteCommands = page?.items.orEmpty()
            lazyHermesSlashActions = slashCommandSuggestions(
                input = normalizedInput,
                remoteCommands = remoteCommands,
                includeDefaultActions = false,
                limit = remoteCommands.size.coerceAtLeast(16)
            )
            lazyHermesSlashNextOffset = page?.nextOffset?.takeIf { page.hasMore }
                ?: if (page?.hasMore == true) remoteCommands.size else null
        } finally {
            isLoadingHermesSlashCommands = false
        }
    }
    val loadMoreHermesSlashCommands: (() -> Unit)? = if (
        selectedGatewayType == GatewayType.hermes &&
        gatewayId != null &&
        lazyHermesSlashNextOffset != null
    ) {
        {
            val nextOffset = lazyHermesSlashNextOffset
            val activeGatewayId = gatewayId
            val activeQuery = lazyHermesSlashQuery
            if (
                nextOffset != null &&
                activeQuery.isNotBlank() &&
                !isLoadingHermesSlashCommands
            ) {
                scope.launch {
                    isLoadingHermesSlashCommands = true
                    try {
                        val page = runCatching {
                            gatewayStore.fetchSlashCommands(activeGatewayId, activeQuery, limit = 16, offset = nextOffset)
                        }.getOrNull()
                        if (page != null && activeQuery == lazyHermesSlashQuery && activeGatewayId == gatewayId) {
                            val pageActions = slashCommandSuggestions(
                                input = activeQuery,
                                remoteCommands = page.items,
                                includeDefaultActions = false,
                                limit = page.items.size.coerceAtLeast(16)
                            )
                            lazyHermesSlashActions = mergeDistinctSlashActions(lazyHermesSlashActions, pageActions)
                            lazyHermesSlashNextOffset = page.nextOffset?.takeIf { page.hasMore }
                                ?: if (page.hasMore) nextOffset + page.items.size else null
                        }
                    } finally {
                        isLoadingHermesSlashCommands = false
                    }
                }
            }
        }
    } else {
        null
    }
    val slashActions = if (selectedGatewayType == GatewayType.hermes) {
        lazyHermesSlashActions
    } else {
        localSlashActions
    }

    LaunchedEffect(Unit) {
        gatewayStore.loadGateways()
    }

    LaunchedEffect(gatewayId) {
        if (gatewayId != null) {
            activeGatewaySwitchId = gatewayId
            try {
                chatStore.beginGatewaySwitch(gatewayId)
                chatStore.connectWebSocket()
                val sessionsLoaded = chatStore.loadSessions(gatewayId)
                if (sessionsLoaded) {
                    val stateAfterSessionLoad = chatStore.state.value
                    val keepSwitchingOverlay = gatewaySwitchHistoryBlocksOverlay(selectedGatewayType)
                    gatewaySwitchHistoryRequest(
                        selectedGatewayId = gatewayId,
                        currentGatewayId = stateAfterSessionLoad.currentGatewayId,
                        currentSessionKey = stateAfterSessionLoad.currentSessionKey,
                        isGatewaySwitchInProgress = false
                    )?.let { request ->
                        loadAutoHistoryOnce(request, keepSwitchingOverlay = keepSwitchingOverlay)
                    }
                }
                modelStore.loadModels(gatewayId)
            } finally {
                if (activeGatewaySwitchId == gatewayId) {
                    activeGatewaySwitchId = null
                }
            }
        } else {
            activeGatewaySwitchId = null
        }
    }

    LaunchedEffect(gatewayId, chatState.currentGatewayId, chatState.currentSessionKey) {
        yield()
        gatewaySwitchHistoryRequest(
            selectedGatewayId = gatewayId,
            currentGatewayId = chatState.currentGatewayId,
            currentSessionKey = chatState.currentSessionKey,
            isGatewaySwitchInProgress = activeGatewaySwitchId == gatewayId
        )?.let { request ->
            loadAutoHistoryOnce(request)
        }
    }

    LaunchedEffect(messageUpdateSignature) {
        val messageCount = visibleMessagesForScroll.size
        if (messageUpdateSignature.isEmpty() || messageCount == 0) {
            lastObservedVisibleMessageCount = 0
            lastObservedMessageSignature = ""
            lastObservedFirstVisibleMessageId = null
            lastObservedUserMessageIds = emptySet()
            hasPendingMessagesBelow = false
            return@LaunchedEffect
        }

        val previousCount = lastObservedVisibleMessageCount
        val previousSignature = lastObservedMessageSignature
        val previousFirstMessageId = lastObservedFirstVisibleMessageId
        val currentFirstMessageId = visibleMessagesForScroll.firstOrNull()?.id
        val currentUserMessageIds = visibleMessagesForScroll
            .asSequence()
            .filter { it.role == MessageRole.user }
            .map { it.id }
            .toSet()
        val isInitialLoad = previousSignature.isEmpty()
        val appended = messageCount > previousCount
        val previousFirstMessageStillVisible = previousFirstMessageId != null &&
            visibleMessagesForScroll.any { it.id == previousFirstMessageId }
        val isOlderHistoryWindowMove = !isInitialLoad &&
            previousFirstMessageStillVisible &&
            previousFirstMessageId != currentFirstMessageId
        val newTailMessages = if (appended && !isOlderHistoryWindowMove) {
            visibleMessagesForScroll.drop(previousCount.coerceAtMost(messageCount))
        } else {
            emptyList()
        }
        val hasNewUserMessage = !isInitialLoad && currentUserMessageIds.any { it !in lastObservedUserMessageIds }
        val isOwnNewMessage = newTailMessages.any { it.role == MessageRole.user } || hasNewUserMessage
        lastObservedVisibleMessageCount = messageCount
        lastObservedMessageSignature = messageUpdateSignature
        lastObservedFirstVisibleMessageId = currentFirstMessageId
        lastObservedUserMessageIds = currentUserMessageIds

        if (isOlderHistoryWindowMove && !isOwnNewMessage) {
            return@LaunchedEffect
        }

        if (isInitialLoad || isOwnNewMessage || isNearListBottom) {
            scrollChatToBottom(animated = !isInitialLoad)
        } else {
            hasPendingMessagesBelow = true
        }
    }

    LaunchedEffect(imeBottomPx) {
        if (imeBottomPx > 0 && chatState.messages.isNotEmpty()) {
            hasPendingMessagesBelow = false
            scrollChatToBottom(animated = true)
            delay(120)
            scrollChatToBottom(animated = true)
            delay(220)
            scrollChatToBottom(animated = true)
        }
    }

    LaunchedEffect(isNearListBottom) {
        if (isNearListBottom) {
            hasPendingMessagesBelow = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (voiceOverlayActive) Modifier.blur(8.dp) else Modifier)
        ) {
        ClawLinkScaffold(
            applyDefaultSystemBars = false,
            topBar = {
                ChatTopBar(
                    gateway = gatewayState.selectedGateway,
                    contextUsageLine = chatState.visibleContextUsageLine(gatewayState.selectedGateway),
                    appRelayStatus = gatewayState.appRelayStatus,
                    onGatewayClick = { viewModel.showGatewaySheet = true },
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
                    .background(MaterialTheme.colorScheme.background)
                    .padding(top = padding.calculateTopPadding())
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 0.dp)
                ) {
                    ChatConversationList(
                        chatState = chatState,
                        gatewayState = gatewayState,
                        listState = listState,
                        viewModel = viewModel,
                        chatStore = chatStore,
                        gatewayId = gatewayId,
                        hasSelectedGateway = hasSelectedGateway,
                        onOpenUsageGuide = onOpenUsageGuide,
                        onOpenSettings = onOpenSettings,
                        onLoadOlderHistory = {
                            val resolvedGatewayId = gatewayId ?: return@ChatConversationList
                            val resolvedSessionKey = chatState.currentSessionKey
                            if (resolvedSessionKey.isBlank()) return@ChatConversationList
                            scope.launch {
                                chatStore.loadOlderHistory(resolvedGatewayId, resolvedSessionKey)
                            }
                        },
                        onDismissKeyboard = dismissKeyboard,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 20.dp)
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .imePadding()
                            .onGloballyPositioned { coordinates ->
                                val height = coordinates.size.height
                                viewModel.composerHeight = with(density) { height.toDp() }
                            }
                    ) {
                        AnimatedVisibility(
                            slashActions.isNotEmpty() && hasActiveSession && !viewModel.voiceMode && !viewModel.voiceInputPhase.isBusy,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            SlashCommandPanel(
                                actions = slashActions,
                                onLoadMore = loadMoreHermesSlashCommands,
                                isLoadingMore = isLoadingHermesSlashCommands,
                                onAction = { action ->
                                    viewModel.messageText = action.command
                                }
                            )
                        }

                        ComposerDock(
                            messageText = viewModel.messageText,
                            onMessageTextChange = { viewModel.messageText = it },
                            selectedModelText = modelState.selectedModelDisplay,
                            isStreaming = chatState.isStreaming,
                            isStoppingRun = chatState.isStoppingRun,
                            voiceMode = viewModel.voiceMode,
                            voiceInputPhase = viewModel.voiceInputPhase,
                            voiceInputCancelPreview = viewModel.voiceInputCancelPreview,
                            showsOpenClawControls = showsSkillExpansionControls,
                            showsModelPicker = showsModelPicker,
                            attachments = viewModel.composerAttachments,
                            isUploadingAttachment = viewModel.isUploadingAttachment,
                            hasActiveSession = hasActiveSession,
                            canEditComposer = hasActiveSession && !chatState.isStreaming && !viewModel.isUploadingAttachment && !chatState.isStoppingRun && !viewModel.voiceInputPhase.isBusy,
                            canSendMessage = isChatChainReady,
                            showAttachmentMenu = viewModel.showAttachmentMenu,
                            onDismissAttachmentMenu = { viewModel.showAttachmentMenu = false },
                            attachmentButtonPosition = viewModel.attachmentButtonPosition,
                            attachmentButtonSize = viewModel.attachmentButtonSize,
                            onAttachmentButtonPositionChanged = { viewModel.attachmentButtonPosition = it },
                            onAttachmentButtonSizeChanged = { viewModel.attachmentButtonSize = it },
                            onPickFiles = {
                                dismissKeyboard()
                                viewModel.showAttachmentMenu = false
                                filePickerLauncher.launch(attachmentPickerMimeTypes(ComposerAttachmentPickTarget.FILES))
                            },
                            onPickAlbum = {
                                dismissKeyboard()
                                viewModel.showAttachmentMenu = false
                                imagePickerLauncher.launch(attachmentPickerMimeTypes(ComposerAttachmentPickTarget.IMAGES))
                            },
                            onPickCamera = {
                                dismissKeyboard()
                                viewModel.showAttachmentMenu = false
                                if (cameraPermissionState.status.isGranted) {
                                    runCatching {
                                        cameraLauncher.launch(null)
                                    }.onFailure {
                                        viewModel.composerNotice = context.getString(R.string.chat_composer_camera_unavailable)
                                    }
                                } else {
                                    cameraPermissionState.launchPermissionRequest()
                                }
                            },
                            onRemoveAttachment = { attachment ->
                                viewModel.removeAttachment(attachment)
                            },
                            onOpenModelPicker = {
                                viewModel.toggleModelPicker()
                            },
                            onShowSkillSheet = { viewModel.showSkillExpansionSheet = true },
                            onOpenAttachment = {
                                dismissKeyboard()
                                viewModel.showAttachmentMenu = !viewModel.showAttachmentMenu
                            },
                            onToggleVoiceMode = {
                                dismissKeyboard()
                                viewModel.toggleVoiceMode()
                            },
                            onBeginVoiceInputHold = {
                                dismissKeyboard()
                                if (recordAudioPermissionState.status.isGranted) {
                                    viewModel.beginVoiceInputHold(
                                        context = context,
                                        hasRecordAudioPermission = true
                                    )
                                } else {
                                    recordAudioPermissionState.launchPermissionRequest()
                                }
                            },
                            onEndVoiceInputHold = { viewModel.endVoiceInputHold() },
                            onCancelVoiceInput = { viewModel.cancelVoiceInput() },
                            onVoiceInputCancelPreviewChange = { viewModel.voiceInputCancelPreview = it },
                            onTextFieldFocusChanged = { isFocused ->
                                if (isFocused) {
                                    requestBottomScrollForComposerFocus()
                                }
                            },
                            onSend = { viewModel.onSend(context) },
                            onAbort = { chatStore.abortRun() }
                        )
                    }
                }
                if (!statusAlertMessage.isNullOrBlank() && dismissedStatusAlertMessage != statusAlertMessage) {
                    ClawLinkAlertDialog(
                        onDismissRequest = {
                            dismissedStatusAlertMessage = statusAlertMessage
                            viewModel.clearError()
                        },
                        title = if (isStatusAlertError) choose("Error", "错误") else choose("Notice", "提示"),
                        message = statusAlertMessage,
                        confirmText = choose("Close", "关闭"),
                        onConfirm = {
                            dismissedStatusAlertMessage = statusAlertMessage
                            viewModel.clearError()
                        }
                    )
                }
                if (hasPendingMessagesBelow) {
                    Surface(
                        onClick = {
                            scope.launch {
                                scrollChatToBottom(animated = true)
                            }
                        },
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)),
                        shadowElevation = 10.dp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = viewModel.composerHeight + 18.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = context.getString(R.string.chat_new_messages),
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                                style = androidx.compose.material3.MaterialTheme.typography.labelMedium
                            )
                            Icon(
                                Icons.Default.ArrowDownward,
                                contentDescription = null,
                                tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
                if (viewModel.showGatewaySheet) {
                    GatewaySheetOverlay(
                        gateways = gatewayState.gateways,
                        appRelayStatus = gatewayState.appRelayStatus,
                        selectedGatewayId = gatewayState.selectedGatewayId,
                        sessions = chatState.sessions,
                        currentSessionKey = chatState.currentSessionKey,
                        isLoading = gatewayState.isLoading,
                        onDismiss = { viewModel.showGatewaySheet = false },
                        onRefresh = { scope.launch { gatewayStore.loadGateways() } },
                        onRefreshSessions = { gateway ->
                            scope.launch { chatStore.loadSessions(gateway.id) }
                        },
                        onSelect = { gateway ->
                            gatewayStore.selectGateway(gateway.id)
                            viewModel.showGatewaySheet = false
                        },
                        onSelectSession = { gateway, session ->
                            gatewayStore.selectGateway(gateway.id)
                            chatStore.selectSession(session.sessionKey)
                            viewModel.showGatewaySheet = false
                        },
                        onUnpair = { gateway ->
                            scope.launch { gatewayStore.unpairGateway(gateway.id) }
                        }
                    )
                }

                if (showsSkillExpansionControls && viewModel.showSkillExpansionSheet) {
                    SkillExpansionSheetOverlay(
                        isHermesGateway = selectedGatewayType == GatewayType.hermes,
                        onDismiss = { viewModel.showSkillExpansionSheet = false },
                        onSendPrompt = { prompt ->
                            chatStore.sendCommand(
                                gatewayId = gatewayState.selectedGateway?.id.orEmpty(),
                                command = prompt
                            )
                        }
                    )
                }

                if (showsModelPicker && viewModel.showModelPicker) {
                    ModelPickerSheetOverlay(
                        models = modelState.models,
                        isLoading = modelState.isLoading,
                        errorMessage = modelState.errorMessage,
                        selectedModel = modelState.selectedModel,
                        onDismiss = { viewModel.showModelPicker = false },
                        onRefresh = {
                            gatewayId?.let { id ->
                                scope.launch { modelStore.loadModels(id) }
                            }
                        },
                        onSelect = { model ->
                            if (gatewayId != null) {
                                viewModel.showModelPicker = false
                                val sessionKey = chatState.currentSessionKey.takeIf { it.isNotBlank() }
                                scope.launch {
                                    modelStore.selectModel(gatewayId, model, sessionKey)
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

        if (imagePreview != null) {
            ImageFullscreenOverlay(
                url = imagePreview.url,
                accessToken = imagePreview.accessToken,
                fileName = imagePreview.fileName,
                cacheKey = imagePreview.cacheKey,
                onDismiss = { viewModel.imagePreview = null }
            )
        }

        if (documentPreview != null) {
            DocumentFullscreenOverlay(
                url = documentPreview.url,
                accessToken = documentPreview.accessToken,
                fileName = documentPreview.fileName,
                mimeType = documentPreview.mimeType,
                cacheKey = documentPreview.cacheKey,
                onDismiss = { viewModel.documentPreview = null }
            )
        }
        }

        VoiceInputOverlay(
            phase = viewModel.voiceInputPhase,
            transcript = viewModel.voiceInputTranscript,
            messageText = viewModel.messageText,
            recording = viewModel.voiceInputRecording,
            audioLevel = viewModel.voiceInputAudioLevel,
            cancelPreview = viewModel.voiceInputCancelPreview,
            canConfirm = hasActiveSession &&
                !chatState.isStreaming &&
                !chatState.isStoppingRun &&
                !viewModel.isUploadingAttachment &&
                viewModel.hasVoiceInputRecording,
            isSending = viewModel.isUploadingAttachment,
            onMessageTextChange = { viewModel.messageText = it },
            onCancel = { viewModel.cancelVoiceInput() },
            onContinue = { viewModel.continueVoiceInputEditing() },
            onConfirm = { viewModel.confirmVoiceInput(context) },
            modifier = Modifier.matchParentSize()
        )
    }
}

internal fun showsSkillExpansionControlsForGateway(gatewayType: GatewayType): Boolean =
    gatewayType == GatewayType.openclaw || gatewayType == GatewayType.hermes

internal fun showsModelPickerForGateway(gatewayType: GatewayType): Boolean =
    gatewayType == GatewayType.openclaw || gatewayType == GatewayType.hermes

private fun mergeDistinctSlashActions(
    current: List<SlashAction>,
    next: List<SlashAction>
): List<SlashAction> {
    val seen = current.map { it.command.trim().lowercase() }.toMutableSet()
    return current + next.filter { action ->
        seen.add(action.command.trim().lowercase())
    }
}
