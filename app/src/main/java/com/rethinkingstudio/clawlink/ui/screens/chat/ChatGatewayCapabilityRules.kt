package com.rethinkingstudio.clawlink.ui.screens.chat

import com.rethinkingstudio.clawlink.core.models.gateway.GatewayType
import com.rethinkingstudio.clawlink.ui.screens.chat.components.SlashAction

internal fun showsSkillExpansionControlsForGateway(gatewayType: GatewayType): Boolean =
    gatewayType == GatewayType.openclaw || gatewayType == GatewayType.hermes

internal fun showsModelPickerForGateway(gatewayType: GatewayType): Boolean =
    gatewayType == GatewayType.openclaw || gatewayType == GatewayType.hermes

internal fun mergeDistinctSlashActions(
    current: List<SlashAction>,
    next: List<SlashAction>
): List<SlashAction> {
    val seen = current.map { it.command.trim().lowercase() }.toMutableSet()
    return current + next.filter { action ->
        seen.add(action.command.trim().lowercase())
    }
}
