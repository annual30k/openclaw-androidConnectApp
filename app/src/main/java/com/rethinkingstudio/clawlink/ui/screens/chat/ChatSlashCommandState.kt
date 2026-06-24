package com.rethinkingstudio.clawlink.ui.screens.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.rethinkingstudio.clawlink.core.models.chat.ChatSlashCommand
import com.rethinkingstudio.clawlink.core.models.gateway.GatewayType
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import com.rethinkingstudio.clawlink.ui.screens.chat.components.SlashAction
import com.rethinkingstudio.clawlink.ui.screens.chat.components.slashCommandSuggestions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal data class ChatSlashCommandState(
    val actions: List<SlashAction>,
    val isLoadingMore: Boolean,
    val onLoadMore: (() -> Unit)?
)

@Composable
internal fun rememberChatSlashCommandState(
    gatewayId: String?,
    selectedGatewayType: GatewayType,
    messageText: String,
    gatewaySlashCommands: List<ChatSlashCommand>?,
    gatewayStore: GatewayStore,
    scope: CoroutineScope
): ChatSlashCommandState {
    val localSlashActions = remember(
        messageText,
        gatewaySlashCommands,
        selectedGatewayType
    ) {
        slashCommandSuggestions(
            input = messageText,
            remoteCommands = gatewaySlashCommands,
            includeDefaultActions = selectedGatewayType != GatewayType.hermes
        )
    }
    var lazyHermesSlashActions by remember(gatewayId) { mutableStateOf<List<SlashAction>>(emptyList()) }
    var lazyHermesSlashNextOffset by remember(gatewayId) { mutableStateOf<Int?>(null) }
    var lazyHermesSlashQuery by remember(gatewayId) { mutableStateOf("") }
    var isLoadingHermesSlashCommands by remember(gatewayId) { mutableStateOf(false) }

    LaunchedEffect(gatewayId, selectedGatewayType, messageText) {
        val normalizedInput = messageText.trim()
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
    return ChatSlashCommandState(
        actions = if (selectedGatewayType == GatewayType.hermes) {
            lazyHermesSlashActions
        } else {
            localSlashActions
        },
        isLoadingMore = isLoadingHermesSlashCommands,
        onLoadMore = loadMoreHermesSlashCommands
    )
}
