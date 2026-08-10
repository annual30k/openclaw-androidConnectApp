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
    isRecoveringMessages: Boolean,
    isStreaming: Boolean,
    isStoppingRun: Boolean,
    isUploadingAttachment: Boolean,
    isVoiceInputBusy: Boolean
): ChatComposerAvailability {
    val hasSessionScope = hasSelectedGateway && sessionKey.isNotBlank()
    val canQueueBehindActiveRun = hasSessionScope && isStreaming && !isStoppingRun
    // 历史恢复会短暂早于在线状态收敛，但此时 gateway/session scope 已稳定。
    // 允许先写入 durable outbox，晚到的磁盘快照会被 mutation revision 拒绝，
    // WebSocket 恢复后再按相同幂等身份发送，不能让历史加载废掉排队能力。
    val canQueueDuringRecovery = hasSessionScope && isRecoveringMessages
    val canSendMessage = hasSessionScope &&
        !isStoppingRun &&
        (isChatChainReady || canQueueBehindActiveRun || canQueueDuringRecovery)
    val hasActiveSession = hasSessionScope &&
        (isChatChainReady || isRecoveringMessages || isStreaming || isStoppingRun)

    return ChatComposerAvailability(
        hasActiveSession = hasActiveSession,
        canEditComposer = canSendMessage && !isUploadingAttachment && !isVoiceInputBusy,
        canSendMessage = canSendMessage
    )
}
