package com.rethinkingstudio.clawlink.ui.screens.chat

internal data class ChatStatusAlert(
    val message: String?,
    val isError: Boolean
)

/**
 * 连接健康状态只在页面内展示；弹窗仅保留给需要用户确认的操作结果，
 * 例如聊天发送失败。
 */
internal fun resolveChatStatusAlert(
    chatErrorMessage: String?,
    gatewayErrorMessage: String?,
    composerNotice: String?
): ChatStatusAlert {
    val visibleChatError = chatErrorMessage.takeUnless(::isInternalLifecycleCancellation)
    val visibleGatewayError = gatewayErrorMessage.takeUnless(::isInternalLifecycleCancellation)
    val visibleComposerNotice = composerNotice.takeUnless(::isInternalLifecycleCancellation)
    val message = visibleChatError ?: visibleGatewayError ?: visibleComposerNotice
    return ChatStatusAlert(
        message = message,
        isError = visibleChatError != null || visibleGatewayError != null
    )
}

private fun isInternalLifecycleCancellation(message: String?): Boolean {
    val normalized = message?.trim()?.lowercase().orEmpty()
    return normalized == "the coroutine scope left the composition"
}
