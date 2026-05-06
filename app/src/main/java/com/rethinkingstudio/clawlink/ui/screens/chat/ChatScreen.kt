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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.gateway.AggregateStatus
import com.rethinkingstudio.clawlink.core.state.chat.ChatStore
import com.rethinkingstudio.clawlink.core.state.chat.RemoteAttachmentCache
import com.rethinkingstudio.clawlink.core.state.chat.RemoteImageCache
import com.rethinkingstudio.clawlink.core.state.chat.RemoteImageSizeCache
import com.rethinkingstudio.clawlink.core.state.chat.chatAttachmentCacheKey
import com.rethinkingstudio.clawlink.core.state.chat.chatImageCacheKey
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import com.rethinkingstudio.clawlink.core.state.model.ModelStore
import com.rethinkingstudio.clawlink.ui.components.ClawLinkScaffold
import com.rethinkingstudio.clawlink.ui.screens.chat.components.ChatSessionLoadingCard
import com.rethinkingstudio.clawlink.ui.screens.chat.components.ChatSessionSwitchLoadingOverlay
import com.rethinkingstudio.clawlink.ui.screens.chat.components.ChatTopBar
import com.rethinkingstudio.clawlink.ui.screens.chat.components.ComposerDock
import com.rethinkingstudio.clawlink.ui.screens.chat.components.EmptyGatewayCard
import com.rethinkingstudio.clawlink.ui.screens.chat.components.DocumentFullscreenOverlay
import com.rethinkingstudio.clawlink.ui.screens.chat.components.GatewaySheetOverlay
import com.rethinkingstudio.clawlink.ui.screens.chat.components.ImageFullscreenOverlay
import com.rethinkingstudio.clawlink.ui.screens.chat.components.MessageBubble
import com.rethinkingstudio.clawlink.ui.screens.chat.components.ModelPickerSheetOverlay
import com.rethinkingstudio.clawlink.ui.screens.chat.components.SkillExpansionSheetOverlay
import com.rethinkingstudio.clawlink.ui.screens.chat.components.slashCommandSuggestions
import com.rethinkingstudio.clawlink.ui.screens.chat.components.documentPreviewKind
import com.rethinkingstudio.clawlink.ui.screens.chat.components.SlashCommandPanel
import com.rethinkingstudio.clawlink.ui.screens.chat.components.StatusBanner
import com.rethinkingstudio.clawlink.ui.screens.chat.components.ThinkingRow
import com.rethinkingstudio.clawlink.ui.screens.chat.components.UsageGuidePromptCard
import com.rethinkingstudio.clawlink.ui.screens.chat.components.visibleToolContentBlocks
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
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
    val view = LocalView.current
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    val gatewayId = gatewayState.selectedGatewayId
    val hasSelectedGateway = gatewayState.selectedGateway != null
    val hasActiveSession = hasSelectedGateway && chatState.currentSessionKey.isNotBlank()

    LaunchedEffect(context) {
        RemoteImageSizeCache.init(context.applicationContext)
        RemoteImageCache.init(context.applicationContext)
        RemoteAttachmentCache.init(context.applicationContext)
    }

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
                viewModel.composerNotice = context.getString(R.string.chat_attachment_import_failed_with_reason, e.message ?: "Unknown error")
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
                viewModel.composerNotice = context.getString(R.string.chat_attachment_import_failed_with_reason, e.message ?: "Unknown error")
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
                viewModel.composerNotice = context.getString(R.string.chat_attachment_import_failed_with_reason, e.message ?: "Unknown error")
            } finally {
                viewModel.isUploadingAttachment = false
                viewModel.showAttachmentMenu = false
            }
        }
    }
    val slashActions = remember(viewModel.messageText, gatewayState.selectedGateway?.slashCommands) {
        slashCommandSuggestions(
            input = viewModel.messageText,
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
                    .background(ChatColors.canvas)
                    .padding(top = padding.calculateTopPadding())
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp)
                        .padding(top = 0.dp)
                ) {
                    if (chatState.errorMessage != null || gatewayState.errorMessage != null || viewModel.composerNotice != null) {
                        StatusBanner(
                            text = chatState.errorMessage ?: gatewayState.errorMessage ?: viewModel.composerNotice.orEmpty(),
                            isError = chatState.errorMessage != null || gatewayState.errorMessage != null,
                            onDismiss = { viewModel.clearError() }
                        )
                    }

                    val displayMessages = remember(chatState.messages, chatState.showInvocationProcess) {
                        chatState.messages.filter { message ->
                            message.shouldDisplayInChat(showInvocationProcess = chatState.showInvocationProcess) ||
                                message.state == MessageState.streaming && message.role == MessageRole.assistant
                        }
                    }
                    val hasStreamingAssistantMessage = displayMessages.any {
                        it.role == MessageRole.assistant && it.state == MessageState.streaming
                    }
                    val conversationAnimationKey = "${gatewayId.orEmpty()}::${chatState.currentSessionKey}"

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        state = listState,
                        contentPadding = PaddingValues(top = 14.dp, bottom = viewModel.composerHeight + 18.dp),
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

                        items(displayMessages, key = { message -> "$conversationAnimationKey:${message.id}" }) { message ->
                            ConversationMessageEnterAnimation(
                                role = message.role,
                                animationKey = "$conversationAnimationKey:${message.id}"
                            ) {
                                MessageBubble(
                                    message = message,
                                    showInvocationProcess = chatState.showInvocationProcess,
                                    relayBaseUrl = chatStore.relayBaseUrl,
                                    accessToken = chatStore.accessToken,
                                    onImageClick = { block, url, fileName ->
                                        viewModel.imagePreview = ChatImagePreviewState(
                                            url = url,
                                            accessToken = chatStore.accessToken,
                                            fileName = fileName,
                                            cacheKey = block.chatImageCacheKey()
                                        )
                                    },
                                    onFileClick = { block, url, fileName ->
                                        viewModel.documentPreview = ChatDocumentPreviewState(
                                            url = url,
                                            accessToken = chatStore.accessToken,
                                            fileName = fileName,
                                            mimeType = block.mimeType,
                                            cacheKey = block.chatAttachmentCacheKey()
                                        )
                                    }
                                )
                            }
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
                            viewModel.composerHeight = with(density) { height.toDp() }
                        }
                ) {
                    AnimatedVisibility(
                        slashActions.isNotEmpty() && hasActiveSession && !viewModel.voiceMode,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        SlashCommandPanel(
                            actions = slashActions,
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
                        attachments = viewModel.composerAttachments,
                        isUploadingAttachment = viewModel.isUploadingAttachment,
                        hasActiveSession = hasActiveSession,
                        canEditComposer = hasActiveSession && !chatState.isStreaming && !viewModel.isUploadingAttachment && !chatState.isStoppingRun,
                        canSendMessage = gatewayState.selectedGateway?.aggregateStatus == AggregateStatus.online,
                        showAttachmentMenu = viewModel.showAttachmentMenu,
                        onDismissAttachmentMenu = { viewModel.showAttachmentMenu = false },
                        attachmentButtonPosition = viewModel.attachmentButtonPosition,
                        attachmentButtonSize = viewModel.attachmentButtonSize,
                        onAttachmentButtonPositionChanged = { viewModel.attachmentButtonPosition = it },
                        onAttachmentButtonSizeChanged = { viewModel.attachmentButtonSize = it },
                        onPickFiles = {
                            viewModel.showAttachmentMenu = false
                            filePickerLauncher.launch(attachmentPickerMimeTypes(ComposerAttachmentPickTarget.FILES))
                        },
                        onPickAlbum = {
                            viewModel.showAttachmentMenu = false
                            imagePickerLauncher.launch(attachmentPickerMimeTypes(ComposerAttachmentPickTarget.IMAGES))
                        },
                        onPickCamera = {
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
                            viewModel.showAttachmentMenu = !viewModel.showAttachmentMenu
                        },
                        onToggleVoiceMode = { viewModel.voiceMode = !viewModel.voiceMode },
                        onSend = { viewModel.onSend(context) },
                        onAbort = { chatStore.abortRun() }
                    )
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
                        }
                    )
                }

                if (viewModel.showSkillExpansionSheet) {
                    SkillExpansionSheetOverlay(
                        onDismiss = { viewModel.showSkillExpansionSheet = false },
                        onSendPrompt = { prompt ->
                            chatStore.sendCommand(
                                gatewayId = gatewayState.selectedGateway?.id.orEmpty(),
                                command = prompt
                            )
                        }
                    )
                }

                if (viewModel.showModelPicker) {
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
                                scope.launch {
                                    modelStore.selectModel(gatewayId, model, chatState.currentSessionKey.takeIf { it.isNotBlank() })
                                    viewModel.showModelPicker = false
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

        val preview = viewModel.imagePreview
        if (preview != null) {
            ImageFullscreenOverlay(
                url = preview.url,
                accessToken = preview.accessToken,
                fileName = preview.fileName,
                cacheKey = preview.cacheKey,
                onDismiss = { viewModel.imagePreview = null }
            )
        }

        val documentPreview = viewModel.documentPreview
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
}

@Composable
private fun ConversationMessageEnterAnimation(
    role: MessageRole,
    animationKey: String,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val startOffsetPx = with(density) {
        val direction = if (role == MessageRole.user) 1 else -1
        28.dp.toPx() * direction
    }
    val offsetX = remember(animationKey) { Animatable(startOffsetPx) }
    val alpha = remember(animationKey) { Animatable(0f) }

    LaunchedEffect(animationKey) {
        offsetX.snapTo(startOffsetPx)
        alpha.snapTo(0f)
        launch {
            offsetX.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = 240,
                    easing = LinearOutSlowInEasing
                )
            )
        }
        launch {
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 240,
                    easing = LinearOutSlowInEasing
                )
            )
        }
    }

    Box(
        modifier = Modifier.graphicsLayer {
            translationX = offsetX.value
            this.alpha = alpha.value
        }
    ) {
        content()
    }
}
