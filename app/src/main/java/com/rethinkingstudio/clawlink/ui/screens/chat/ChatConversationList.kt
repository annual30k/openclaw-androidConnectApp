package com.rethinkingstudio.clawlink.ui.screens.chat

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.state.chat.ChatStore
import com.rethinkingstudio.clawlink.core.state.chat.RemoteAttachmentCache
import com.rethinkingstudio.clawlink.core.state.chat.RemoteImageCache
import com.rethinkingstudio.clawlink.core.state.chat.chatAttachmentCacheKey
import com.rethinkingstudio.clawlink.core.state.chat.chatImageCacheKey
import com.rethinkingstudio.clawlink.core.state.chat.isExplicitAttachmentExpiredState
import com.rethinkingstudio.clawlink.core.state.chat.resolveAttachmentAvailability
import com.rethinkingstudio.clawlink.ui.screens.chat.components.ChatSessionLoadingCard
import com.rethinkingstudio.clawlink.ui.screens.chat.components.EmptyGatewayCard
import com.rethinkingstudio.clawlink.ui.screens.chat.components.MessageBubble
import com.rethinkingstudio.clawlink.ui.screens.chat.components.ThinkingRow
import com.rethinkingstudio.clawlink.ui.screens.chat.components.UsageGuidePromptCard
import com.rethinkingstudio.clawlink.ui.screens.chat.components.resolveFileDownloadUrl
import java.io.File

internal data class ConversationMessageListItem(
    val message: ChatMessage,
    val stableKey: String
)

internal fun conversationMessageListItems(
    messages: List<ChatMessage>,
    conversationAnimationKey: String
): List<ConversationMessageListItem> {
    val messageIdCounts = messages.groupingBy { message -> message.id.trim() }.eachCount()
    return messages.map { message ->
        val messageId = message.id.trim()
        val keyIdentity = when {
            messageId.isNotEmpty() && messageIdCounts[messageId] == 1 -> "message-id:$messageId"
            message.timelineIdentityKey.isNotBlank() -> "timeline:${message.timelineIdentityKey.trim()}"
            message.timelineStableKey.isNotBlank() -> "stable:${message.timelineStableKey.trim()}"
            else -> listOf(
                "message-id:$messageId",
                "timeline-message:${message.timelineMessageId.trim()}",
                "timeline-part:${message.timelinePartId.trim()}",
                "kind:${message.timelineItemKind.trim()}",
                "role:${message.role.name}"
            ).joinToString(separator = "|")
        }
        ConversationMessageListItem(
            message = message,
            stableKey = "$conversationAnimationKey:$keyIdentity"
        )
    }
}

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
    hasLocalSendBottomLock: Boolean,
    onOpenUsageGuide: (() -> Unit)?,
    onOpenSettings: (() -> Unit)?,
    onLoadOlderHistory: () -> Unit,
    onDismissKeyboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayMessages = remember(
        hasSelectedGateway,
        chatState.messages,
        chatState.showInvocationProcess
    ) {
        conversationDisplayMessagesForGatewayState(
            hasSelectedGateway = hasSelectedGateway,
            messages = chatState.messages,
            showInvocationProcess = chatState.showInvocationProcess
        )
    }

    if (!hasSelectedGateway) {
        // 未绑定状态不复用长会话 LazyColumn 的位置；旧 firstVisibleItemIndex 不能把引导卡留在视口外。
        Column(
            modifier = modifier
                .pointerInput(onDismissKeyboard) {
                    detectTapGestures(onTap = { onDismissKeyboard() })
                }
                .padding(top = 14.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (gatewayState.isLoading) {
                ChatSessionLoadingCard()
            } else {
                UsageGuidePromptCard(onOpenUsageGuide = onOpenUsageGuide ?: onOpenSettings)
                EmptyGatewayCard(onOpenSettings = onOpenSettings)
            }
        }
        return
    }

    val hasStreamingAssistantMessage = displayMessages.any {
        it.role == MessageRole.assistant && it.state == MessageState.streaming
    }
    val conversationAnimationKey = "${gatewayId.orEmpty()}::${chatState.currentSessionKey}"
    val displayItems = remember(displayMessages, conversationAnimationKey) {
        conversationMessageListItems(displayMessages, conversationAnimationKey)
    }
    val activeToolProgressLabels = remember(chatState.messages, chatState.showInvocationProcess) {
        activeToolProgressLabels(
            messages = chatState.messages,
            showInvocationProcess = chatState.showInvocationProcess
        )
    }
    val streamingWaitProgressLabel = remember(chatState.messages, chatState.showInvocationProcess) {
        streamingWaitProgressLabel(
            messages = chatState.messages,
            showInvocationProcess = chatState.showInvocationProcess
        )
    }
    val shouldLoadOlder by remember(
        chatState.historyWindow,
        chatState.isLoading,
        chatState.isSwitchingSession,
        chatState.currentSessionKey,
        hasSelectedGateway,
        canAutoLoadOlderHistory,
        hasLocalSendBottomLock,
        gatewayId,
        listState
    ) {
        derivedStateOf {
            hasSelectedGateway &&
                canAutoLoadOlderHistory &&
                !hasLocalSendBottomLock &&
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
    if (chatState.isLoading && displayMessages.isEmpty() && !chatState.isSwitchingSession) {
        item { ChatSessionLoadingCard() }
    }

    items(displayItems, key = ConversationMessageListItem::stableKey) { item ->
        val message = item.message
        ConversationMessageEnterAnimation(
            isUserAuthoredMessage = message.isUserAuthoredMessage(),
            animationKey = item.stableKey,
            shouldAnimate = shouldAnimateConversationMessageEntry(message)
        ) {
            MessageBubble(
                message = message,
                streamingProgressLabel = streamingAssistantProgressLabel(message, activeToolProgressLabels),
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
                    val allImages = displayMessages.flatMap { msg ->
                        msg.contentBlocks.filter { it.isImageFileBlock }.mapNotNull { b ->
                            val rawUrl = b.preferredImagePreviewURLString?.trim()?.takeIf { it.isNotEmpty() }
                            val resolvedUrl = resolveFileDownloadUrl(b, chatStore.relayBaseUrl, rawUrl)
                            val rawLocalPath = rawUrl?.let { reference ->
                                when {
                                    reference.startsWith("file://", ignoreCase = true) -> reference.removePrefix("file://")
                                    reference.startsWith("/") && !reference.startsWith("/api/") -> reference
                                    else -> null
                                }
                            }?.takeIf { File(it).exists() }
                            val attachmentKey = b.chatAttachmentCacheKey()
                            val cachedLocalPath = attachmentKey
                                ?.let(RemoteAttachmentCache::cachedFile)
                                ?.takeIf { it.exists() }
                                ?.absolutePath
                            val thumbnailPath = b.chatImageCacheKey()
                                ?.let(RemoteImageCache::cachedFile)
                                ?.takeIf { it.exists() }
                                ?.absolutePath
                            val availability = resolveAttachmentAvailability(
                                hasLocalOriginal = rawLocalPath != null,
                                hasLocalCachedCopy = cachedLocalPath != null,
                                hasLocalThumbnail = thumbnailPath != null,
                                hasRemoteReference = resolvedUrl != null,
                                expiresAt = b.expiresAt,
                                serverReportedExpired =
                                    attachmentKey?.let(RemoteAttachmentCache::isServerExpired) == true ||
                                        isExplicitAttachmentExpiredState(b.transferState, b.status)
                            )
                            val displayReference = rawLocalPath
                                ?: cachedLocalPath
                                ?: thumbnailPath
                                ?: resolvedUrl?.takeIf { availability.shouldAttemptRemoteDownload }
                            if (displayReference != null) {
                                ChatImageItem(
                                    url = displayReference,
                                    accessToken = chatStore.accessToken,
                                    fileName = b.fileDisplayName ?: b.fileName,
                                    cacheKey = b.chatImageCacheKey()
                                )
                            } else null
                        }
                    }
                    val clickedCacheKey = block.chatImageCacheKey()
                    val initialIndex = allImages.indexOfFirst { it.cacheKey == clickedCacheKey }.coerceAtLeast(0)
                    viewModel.imagePreview = ChatImagePreviewState(
                        images = allImages,
                        initialIndex = initialIndex
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

    if (hasSelectedGateway && chatState.isStreaming && !hasStreamingAssistantMessage) {
        item { ThinkingRow(progressLabel = streamingWaitProgressLabel) }
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
