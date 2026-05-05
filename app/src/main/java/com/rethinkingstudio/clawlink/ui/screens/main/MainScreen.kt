package com.rethinkingstudio.clawlink.ui.screens.main

import androidx.compose.runtime.Composable
import com.rethinkingstudio.clawlink.AppContainer
import com.rethinkingstudio.clawlink.ui.screens.chat.ChatScreen

@Composable
fun MainScreen(
    container: AppContainer,
    onNavigateToSettings: () -> Unit,
    onNavigateToHelp: () -> Unit,
    hasSeenUsageGuide: Boolean
) {
    ChatScreen(
        chatStore = container.chatStore,
        gatewayStore = container.gatewayStore,
        modelStore = container.modelStore,
        onBack = null,
        onOpenSettings = onNavigateToSettings,
        onOpenUsageGuide = onNavigateToHelp,
        hasSeenUsageGuide = hasSeenUsageGuide
    )
}
