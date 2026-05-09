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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.input.pointer.pointerInput
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
import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
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
import com.rethinkingstudio.clawlink.ui.screens.chat.components.ThinkingRow
import com.rethinkingstudio.clawlink.ui.screens.chat.components.UsageGuidePromptCard
import com.rethinkingstudio.clawlink.ui.screens.chat.components.VoiceInputOverlay
import com.rethinkingstudio.clawlink.ui.screens.chat.components.visibleToolContentBlocks
import kotlinx.coroutines.launch

@Composable
internal fun ChatConversationList(
    chatState: com.rethinkingstudio.clawlink.core.state.chat.ChatState,
    gatewayState: com.rethinkingstudio.clawlink.core.state.gateway.GatewayState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    viewModel: ChatViewModel,
    chatStore: ChatStore,
    gatewayId: String?,
    hasSelectedGateway: Boolean,
    onOpenUsageGuide: (() -> Unit)?,
    onOpenSettings: (() -> Unit)?,
    onDismissKeyboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayMessages = remember(
    chatState.messages,
    chatState.showInvocationProcess,
    chatState.assistantVoiceRepliesEffectiveEnabled,
    chatState.assistantVoiceRepliesEnabledAt,
    chatState.voiceReplyTextOnlyRunIds
) {
    val now = System.currentTimeMillis() / 1000.0
    chatState.messages.filter { message ->
        val forceDisplayText = message.isVoiceReplyTextOnlyCandidate(
            assistantVoiceRepliesEffectiveEnabled = chatState.assistantVoiceRepliesEffectiveEnabled,
            assistantVoiceRepliesEnabledAt = chatState.assistantVoiceRepliesEnabledAt,
            voiceReplyTextOnlyRunIds = chatState.voiceReplyTextOnlyRunIds,
            now = now
        )
        message.shouldDisplayInChat(
            showInvocationProcess = chatState.showInvocationProcess,
            assistantVoiceRepliesEnabled = chatState.assistantVoiceRepliesEffectiveEnabled,
            assistantVoiceRepliesEnabledAt = chatState.assistantVoiceRepliesEnabledAt,
            forceDisplayTextWhenVoiceRepliesEnabled = forceDisplayText
        ) ||
            message.state == MessageState.streaming && message.role == MessageRole.assistant
    }
}

val hasStreamingAssistantMessage = displayMessages.any {
    it.role == MessageRole.assistant && it.state == MessageState.streaming
}
val conversationAnimationKey = "${gatewayId.orEmpty()}::${chatState.currentSessionKey}"

LazyColumn(
    modifier = modifier.pointerInput(onDismissKeyboard) {
        detectTapGestures(onTap = { onDismissKeyboard() })
    },
    state = listState,
    contentPadding = PaddingValues(top = 14.dp, bottom = 18.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
) {
    if (!hasSelectedGateway && gatewayState.isLoading) {
        item { ChatSessionLoadingCard() }
    } else if (!hasSelectedGateway) {
        item {
            UsageGuidePromptCard(onOpenUsageGuide = onOpenUsageGuide ?: onOpenSettings)
        }
        item {
            EmptyGatewayCard(onOpenSettings = onOpenSettings)
        }
    }

    if (chatState.isLoading && displayMessages.isEmpty() && !chatState.isSwitchingSession) {
        item { ChatSessionLoadingCard() }
    }

    items(displayMessages, key = { message -> "$conversationAnimationKey:${message.id}" }) { message ->
        val isVoiceReplyTextOnly = message.isVoiceReplyTextOnlyCandidate(
            assistantVoiceRepliesEffectiveEnabled = chatState.assistantVoiceRepliesEffectiveEnabled,
            assistantVoiceRepliesEnabledAt = chatState.assistantVoiceRepliesEnabledAt,
            voiceReplyTextOnlyRunIds = chatState.voiceReplyTextOnlyRunIds,
            now = System.currentTimeMillis() / 1000.0
        )
        ConversationMessageEnterAnimation(
            role = message.role,
            animationKey = "$conversationAnimationKey:${message.id}"
        ) {
            MessageBubble(
                message = message,
                showInvocationProcess = chatState.showInvocationProcess,
                isVoiceReplyTextOnly = isVoiceReplyTextOnly,
                relayBaseUrl = chatStore.relayBaseUrl,
                accessToken = chatStore.accessToken,
                readVoicePlaybackIdentifiers = chatState.readVoicePlaybackIdentifiers,
                onVoicePlaybackStart = { identifier ->
                    chatStore.markVoicePlaybackIdentifierRead(
                        identifier = identifier,
                        gatewayId = gatewayId,
                        sessionKey = chatState.currentSessionKey
                    )
                },
                gatewayId = gatewayId,
                sessionKey = chatState.currentSessionKey,
                onImageClick = { block, url, fileName ->
                    onDismissKeyboard()
                    viewModel.imagePreview = ChatImagePreviewState(
                        url = url,
                        accessToken = chatStore.accessToken,
                        fileName = fileName,
                        cacheKey = block.chatImageCacheKey()
                    )
                },
                onFileClick = { block, url, fileName ->
                    onDismissKeyboard()
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

    item(key = "$conversationAnimationKey:chat-bottom") {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
        )
    }
}
}
