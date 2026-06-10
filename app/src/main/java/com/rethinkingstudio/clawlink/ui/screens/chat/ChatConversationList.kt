package com.rethinkingstudio.clawlink.ui.screens.chat

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.state.chat.ChatStore
import com.rethinkingstudio.clawlink.core.state.chat.chatAttachmentCacheKey
import com.rethinkingstudio.clawlink.core.state.chat.chatImageCacheKey
import com.rethinkingstudio.clawlink.ui.screens.chat.components.ChatSessionLoadingCard
import com.rethinkingstudio.clawlink.ui.screens.chat.components.EmptyGatewayCard
import com.rethinkingstudio.clawlink.ui.screens.chat.components.MessageBubble
import com.rethinkingstudio.clawlink.ui.screens.chat.components.ThinkingRow
import com.rethinkingstudio.clawlink.ui.screens.chat.components.UsageGuidePromptCard

@Composable
internal fun ChatConversationList(
    chatState: com.rethinkingstudio.clawlink.core.state.chat.ChatState,
    gatewayState: com.rethinkingstudio.clawlink.core.state.gateway.GatewayState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    viewModel: ChatViewModel,
    chatStore: ChatStore,
    gatewayId: String?,
    hasSelectedGateway: Boolean,
    canAutoLoadOlderHistory: Boolean,
    onOpenUsageGuide: (() -> Unit)?,
    onOpenSettings: (() -> Unit)?,
    onLoadOlderHistory: () -> Unit,
    onDismissKeyboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayMessages = remember(
        chatState.messages,
        chatState.showInvocationProcess
    ) {
        conversationDisplayMessages(
            messages = chatState.messages,
            showInvocationProcess = chatState.showInvocationProcess
        )
    }

    val hasStreamingAssistantMessage = displayMessages.any {
        it.role == MessageRole.assistant && it.state == MessageState.streaming
    }
    val conversationAnimationKey = "${gatewayId.orEmpty()}::${chatState.currentSessionKey}"
    val shouldLoadOlder by remember(
        chatState.historyWindow,
        chatState.isLoading,
        chatState.isSwitchingSession,
        chatState.currentSessionKey,
        hasSelectedGateway,
        canAutoLoadOlderHistory,
        gatewayId,
        listState
    ) {
        derivedStateOf {
            hasSelectedGateway &&
                canAutoLoadOlderHistory &&
                !gatewayId.isNullOrBlank() &&
                chatState.currentSessionKey.isNotBlank() &&
                chatState.historyWindow.hasOlder &&
                !chatState.historyWindow.isLoadingOlder &&
                !chatState.isLoading &&
                !chatState.isSwitchingSession &&
                listState.firstVisibleItemIndex <= 3
        }
    }

    LaunchedEffect(shouldLoadOlder, gatewayId, chatState.currentSessionKey) {
        if (shouldLoadOlder) {
            onLoadOlderHistory()
        }
    }

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
        ConversationMessageEnterAnimation(
            role = message.role,
            animationKey = "$conversationAnimationKey:${message.id}"
        ) {
            MessageBubble(
                message = message,
                showInvocationProcess = chatState.showInvocationProcess,
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
                toolDetailCacheByKey = chatState.toolDetailCacheByKey,
                onLoadToolDetail = { detailGatewayId, detailSessionKey, toolCallId ->
                    viewModel.loadToolDetail(detailGatewayId, detailSessionKey, toolCallId)
                },
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
