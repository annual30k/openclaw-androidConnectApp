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
internal fun ConversationMessageEnterAnimation(
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

internal fun ChatMessage.isVoiceReplyTextOnlyCandidate(
    assistantVoiceRepliesEffectiveEnabled: Boolean,
    assistantVoiceRepliesEnabledAt: Double?,
    voiceReplyTextOnlyRunIds: Set<String>,
    now: Double
): Boolean {
    if (hasVoiceContent || hasFileContent) return false
    if (role != MessageRole.assistant || !assistantVoiceRepliesEffectiveEnabled) return false
    if (state == MessageState.streaming) return true
    val timestamp = sortTimestamp ?: return false
    val enabledAt = assistantVoiceRepliesEnabledAt ?: return false
    return timestamp >= enabledAt - 2.0 && now - timestamp < 120.0
}
