package com.rethinkingstudio.clawlink.ui.screens.chat

internal data class ChatComposerAvailability(
    val hasActiveSession: Boolean,
    val canEditComposer: Boolean,
    val canSendMessage: Boolean
)

internal fun resolveChatComposerAvailability(
    hasSelectedGateway: Boolean,
    sessionKey: String,
    isChatChainReady: Boolean,
    isStreaming: Boolean,
    isStoppingRun: Boolean,
    isUploadingAttachment: Boolean,
    isVoiceInputBusy: Boolean
): ChatComposerAvailability {
    val hasSessionScope = hasSelectedGateway && sessionKey.isNotBlank()
    val canQueueBehindActiveRun = hasSessionScope && isStreaming && !isStoppingRun
    val canSendMessage = hasSessionScope &&
        !isStoppingRun &&
        (isChatChainReady || canQueueBehindActiveRun)
    val hasActiveSession = hasSessionScope &&
        (isChatChainReady || isStreaming || isStoppingRun)

    return ChatComposerAvailability(
        hasActiveSession = hasActiveSession,
        canEditComposer = canSendMessage && !isUploadingAttachment && !isVoiceInputBusy,
        canSendMessage = canSendMessage
    )
}
