package com.rethinkingstudio.clawlink.core.state.gateway

import com.rethinkingstudio.clawlink.core.models.gateway.AggregateStatus
import com.rethinkingstudio.clawlink.core.models.gateway.GatewaySummary
import com.rethinkingstudio.clawlink.core.models.gateway.GatewayType
import org.junit.Assert.assertEquals
import org.junit.Test

class GatewaySelectionResolverTest {
    @Test
    fun movesFromOfflineDuplicateToOnlineSameGatewayNameAndType() {
        val gateways = listOf(
            makeGateway(
                id = "old-openclaw",
                displayName = "Mac OpenClaw",
                gatewayType = GatewayType.openclaw,
                aggregateStatus = AggregateStatus.offline
            ),
            makeGateway(
                id = "new-openclaw",
                displayName = " Mac OpenClaw ",
                gatewayType = GatewayType.openclaw,
                aggregateStatus = AggregateStatus.online
            ),
            makeGateway(
                id = "other-hermes",
                displayName = "Mac OpenClaw",
                gatewayType = GatewayType.hermes,
                aggregateStatus = AggregateStatus.online
            )
        )

        assertEquals(
            "new-openclaw",
            GatewaySelectionResolver.preferredSelectedGatewayId(
                currentSelectedGatewayId = "old-openclaw",
                persistedSelectedGatewayId = null,
                gateways = gateways
            )
        )
    }

    @Test
    fun keepsOnlineCurrentSelectionEvenWhenDuplicateExists() {
        val gateways = listOf(
            makeGateway(
                id = "current-openclaw",
                displayName = "Mac OpenClaw",
                aggregateStatus = AggregateStatus.online
            ),
            makeGateway(
                id = "other-openclaw",
                displayName = "Mac OpenClaw",
                aggregateStatus = AggregateStatus.online
            )
        )

        assertEquals(
            "current-openclaw",
            GatewaySelectionResolver.preferredSelectedGatewayId(
                currentSelectedGatewayId = "current-openclaw",
                persistedSelectedGatewayId = null,
                gateways = gateways
            )
        )
    }

    @Test
    fun migratesPersistedOfflineSelectionWhenNothingIsCurrentlySelected() {
        val gateways = listOf(
            makeGateway(
                id = "old-hermes",
                displayName = "Mac Hermes Agent",
                gatewayType = GatewayType.hermes,
                aggregateStatus = AggregateStatus.offline
            ),
            makeGateway(
                id = "new-hermes",
                displayName = "Mac Hermes Agent",
                gatewayType = GatewayType.hermes,
                aggregateStatus = AggregateStatus.online
            )
        )

        assertEquals(
            "new-hermes",
            GatewaySelectionResolver.preferredSelectedGatewayId(
                currentSelectedGatewayId = null,
                persistedSelectedGatewayId = "old-hermes",
                gateways = gateways
            )
        )
    }

    private fun makeGateway(
        id: String,
        displayName: String = "Host $id",
        gatewayType: GatewayType = GatewayType.openclaw,
        aggregateStatus: AggregateStatus = AggregateStatus.online
    ): GatewaySummary {
        return GatewaySummary(
            gatewayId = id,
            displayName = displayName,
            platform = "android",
            gatewayType = gatewayType,
            aggregateStatus = aggregateStatus,
            lastSeenAt = "2026-06-25T10:00:00Z",
            currentModel = "mimo-v2.5-pro",
            contextUsage = "0"
        )
    }
}
