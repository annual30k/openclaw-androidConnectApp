package com.rethinkingstudio.clawlink.core.state.gateway

import com.rethinkingstudio.clawlink.core.models.gateway.AggregateStatus
import com.rethinkingstudio.clawlink.core.models.gateway.ConnectionPhase
import com.rethinkingstudio.clawlink.core.models.gateway.GatewayStatus
import com.rethinkingstudio.clawlink.core.models.gateway.GatewaySummary
import com.rethinkingstudio.clawlink.core.models.gateway.GatewayType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayStatusResolverTest {
    @Test
    fun hermesAggregateStatusIgnoresStaleOpenClawHostPhase() {
        val gateway = makeGateway(
            gatewayType = GatewayType.hermes,
            aggregateStatus = AggregateStatus.online,
            statuses = listOf(
                GatewayStatus(
                    phase = ConnectionPhase.relayHost,
                    status = AggregateStatus.online,
                    detail = "Relay 已连接到主机"
                ),
                GatewayStatus(
                    phase = ConnectionPhase.hostGateway,
                    status = AggregateStatus.offline,
                    detail = "OpenClaw 已离线"
                )
            )
        )

        assertEquals(
            AggregateStatus.online,
            GatewayStatusResolver.aggregateStatusForChain(gateway, AggregateStatus.online)
        )
        assertTrue(GatewayStatusResolver.gatewayIsFullyOnline(gateway))

        val statuses = GatewayStatusResolver.selectedGatewayStatuses(gateway, AggregateStatus.online)
        assertEquals(
            AggregateStatus.online,
            statuses.first { it.phase == ConnectionPhase.hostGateway }.status
        )
        assertEquals(
            "Hermes Agent 运行正常",
            statuses.first { it.phase == ConnectionPhase.hostGateway }.detail
        )
    }

    private fun makeGateway(
        gatewayType: GatewayType = GatewayType.openclaw,
        aggregateStatus: AggregateStatus = AggregateStatus.online,
        statuses: List<GatewayStatus> = emptyList()
    ): GatewaySummary = GatewaySummary(
        gatewayId = "gateway-1",
        displayName = "主机",
        platform = "Android",
        gatewayType = gatewayType,
        aggregateStatus = aggregateStatus,
        lastSeenAt = "2026-03-28T10:00:00Z",
        currentModel = "gpt-5.5",
        contextUsage = "12%",
        statuses = statuses
    )
}
