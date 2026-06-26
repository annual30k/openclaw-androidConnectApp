package com.rethinkingstudio.clawlink.core.state.gateway

import com.rethinkingstudio.clawlink.core.models.gateway.AggregateStatus
import com.rethinkingstudio.clawlink.core.models.gateway.GatewaySummary
import java.util.Locale

object GatewaySelectionResolver {
    fun preferredSelectedGatewayId(
        currentSelectedGatewayId: String?,
        persistedSelectedGatewayId: String?,
        gateways: List<GatewaySummary>
    ): String? {
        currentSelectedGatewayId?.let { selectedId ->
            val currentGateway = gateways.firstOrNull { it.id == selectedId }
            if (currentGateway != null) {
                return connectedReplacementGatewayId(currentGateway, gateways) ?: selectedId
            }
        }

        persistedSelectedGatewayId?.let { selectedId ->
            val persistedGateway = gateways.firstOrNull { it.id == selectedId }
            if (persistedGateway != null) {
                return connectedReplacementGatewayId(persistedGateway, gateways) ?: selectedId
            }
        }

        return gateways.firstOrNull()?.id
    }

    private fun connectedReplacementGatewayId(
        currentGateway: GatewaySummary,
        gateways: List<GatewaySummary>
    ): String? {
        if (currentGateway.aggregateStatus == AggregateStatus.online) return null

        val normalizedDisplayName = normalizedGatewayDisplayName(currentGateway.displayName)
        return gateways.firstOrNull { candidate ->
            candidate.id != currentGateway.id &&
                candidate.gatewayType == currentGateway.gatewayType &&
                candidate.aggregateStatus == AggregateStatus.online &&
                normalizedGatewayDisplayName(candidate.displayName) == normalizedDisplayName
        }?.id
    }

    private fun normalizedGatewayDisplayName(value: String): String {
        return value.trim().lowercase(Locale.ROOT)
    }
}
