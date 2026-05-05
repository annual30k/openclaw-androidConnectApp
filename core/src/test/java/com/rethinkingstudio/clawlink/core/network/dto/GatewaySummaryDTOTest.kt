package com.rethinkingstudio.clawlink.core.network.dto

import com.rethinkingstudio.clawlink.core.models.gateway.AggregateStatus
import com.rethinkingstudio.clawlink.core.models.gateway.ConnectionPhase
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class GatewaySummaryDTOTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun synthesizesGatewayFlowStatusesFromRuntimeFields() {
        val dto = json.decodeFromString<GatewaySummaryDTO>(
            """
            {
              "gatewayId": "gw-1",
              "displayName": "Mac",
              "platform": "darwin",
              "aggregateStatus": "healthy",
              "relayStatus": "relay_connected",
              "hostStatus": "healthy",
              "openclawStatus": "healthy",
              "lastSeenAt": "2026-05-05T03:40:43.846Z",
              "currentModel": "MiniMax-M2.7",
              "contextUsage": "0"
            }
            """.trimIndent()
        )

        val statuses = dto.toGatewaySummary().statuses

        assertEquals(ConnectionPhase.appRelay, statuses[0].phase)
        assertEquals(AggregateStatus.online, statuses[0].status)
        assertEquals(ConnectionPhase.relayHost, statuses[1].phase)
        assertEquals(AggregateStatus.online, statuses[1].status)
        assertEquals(ConnectionPhase.hostGateway, statuses[2].phase)
        assertEquals(AggregateStatus.online, statuses[2].status)
    }

    @Test
    fun decodesHumanReadablePhaseNames() {
        val status = GatewayStatusDTO(
            phase = "Relay -> Host",
            status = "relay_connected",
            detail = "Relay is connected to host"
        ).toGatewayStatus()

        assertEquals(ConnectionPhase.relayHost, status.phase)
        assertEquals(AggregateStatus.online, status.status)
    }

    @Test
    fun formatsNumericContextUsageFromRelaySummary() {
        val dto = json.decodeFromString<GatewaySummaryDTO>(
            """
            {
              "gatewayId": "gw-1",
              "displayName": "Mac",
              "platform": "darwin",
              "aggregateStatus": "healthy",
              "lastSeenAt": "2026-05-05T03:40:43.846Z",
              "currentModel": "MiniMax-M2.7",
              "contextUsage": 1200,
              "contextLimit": 204800
            }
            """.trimIndent()
        )

        val summary = dto.toGatewaySummary()

        assertEquals("1.2k/204.8k (1%)", summary.contextUsage)
        assertEquals(1200, summary.contextUsageValue)
        assertEquals(204800, summary.contextLimit)
    }
}
