package com.rethinkingstudio.clawlink.ui.screens.chat

import com.rethinkingstudio.clawlink.core.models.gateway.GatewayType

internal data class GatewayHistoryRequest(
    val gatewayId: String,
    val sessionKey: String
)

internal fun gatewaySwitchHistoryRequest(
    selectedGatewayId: String?,
    currentGatewayId: String?,
    currentSessionKey: String,
    isGatewaySwitchInProgress: Boolean = false
): GatewayHistoryRequest? {
    if (isGatewaySwitchInProgress) return null
    val selected = selectedGatewayId?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val current = currentGatewayId?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val session = currentSessionKey.trim().takeIf { it.isNotBlank() } ?: return null
    if (current != selected) return null
    return GatewayHistoryRequest(gatewayId = selected, sessionKey = session)
}

internal fun gatewayHistoryRequestKey(request: GatewayHistoryRequest): String {
    return "${request.gatewayId.trim()}\u001F${request.sessionKey.trim()}"
}

internal fun gatewaySwitchHistoryBlocksOverlay(gatewayType: GatewayType): Boolean {
    return gatewayType != GatewayType.hermes
}
