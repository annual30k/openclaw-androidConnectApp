package com.rethinkingstudio.clawlink.core.state.gateway

import com.rethinkingstudio.clawlink.core.models.gateway.AggregateStatus
import com.rethinkingstudio.clawlink.core.models.gateway.ConnectionPhase
import com.rethinkingstudio.clawlink.core.models.gateway.GatewayStatus
import com.rethinkingstudio.clawlink.core.models.gateway.GatewaySummary
import com.rethinkingstudio.clawlink.core.models.gateway.GatewayType
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose

internal object GatewayStatusResolver {
    fun aggregateStatusForChain(selectedGateway: GatewaySummary?, appRelayStatus: AggregateStatus): AggregateStatus {
        if (selectedGateway == null) return AggregateStatus.offline
        if (appRelayStatus != AggregateStatus.online) return appRelayStatus
        if (selectedGateway.gatewayType == GatewayType.hermes) return selectedGateway.aggregateStatus
        if (gatewayIsFullyOnline(selectedGateway)) return AggregateStatus.online
        val statuses = selectedGatewayStatuses(selectedGateway, appRelayStatus)
        return when {
            statuses.any { it.status == AggregateStatus.offline } -> AggregateStatus.offline
            statuses.any { it.status == AggregateStatus.connecting } -> AggregateStatus.connecting
            else -> AggregateStatus.partial
        }
    }

    fun gatewayIsFullyOnline(gateway: GatewaySummary?): Boolean {
        if (gateway == null || gateway.aggregateStatus != AggregateStatus.online) return false
        if (gateway.gatewayType == GatewayType.hermes) return true
        val relayHostStatus = gateway.statuses.find { it.phase == ConnectionPhase.relayHost }
        if (relayHostStatus?.status != AggregateStatus.online) return false
        val hostGatewayStatus = gateway.statuses.find { it.phase == ConnectionPhase.hostGateway }
        if (hostGatewayStatus?.status != AggregateStatus.online) return false
        val detail = hostGatewayStatus.detail.trim().lowercase()
        val stillWaiting = detail.contains("等待 openclaw") ||
            detail.contains("waiting openclaw") ||
            detail.contains("relay_connected") ||
            detail.contains("connecting openclaw") ||
            detail.contains("正在连接 openclaw") ||
            detail.contains("openclaw 未连接") ||
            detail.contains("openclaw 连接异常") ||
            detail.contains("openclaw 重试中")
        return !stillWaiting
    }

    fun selectedGatewayStatuses(
        selectedGateway: GatewaySummary?,
        appRelayStatus: AggregateStatus,
        appRelayDetail: String = if (appRelayStatus == AggregateStatus.online) choose("Session active", "会话有效") else choose("Session not established", "会话未建立")
    ): List<GatewayStatus> {
        if (selectedGateway?.gatewayType == GatewayType.hermes) {
            return hermesGatewayStatuses(selectedGateway, appRelayStatus, appRelayDetail)
        }
        val relayHost = selectedGateway?.statuses?.find { it.phase == ConnectionPhase.relayHost }
            ?: GatewayStatus(phase = ConnectionPhase.relayHost, status = AggregateStatus.offline, detail = choose("Relay is not connected to the host", "Relay 未连接到主机"))
        val hostGateway = selectedGateway?.statuses?.find { it.phase == ConnectionPhase.hostGateway }
            ?: GatewayStatus(phase = ConnectionPhase.hostGateway, status = AggregateStatus.offline, detail = choose("OpenClaw is not connected", "OpenClaw 未连接"))
        return listOf(GatewayStatus(phase = ConnectionPhase.appRelay, status = appRelayStatus, detail = appRelayDetail), relayHost, hostGateway)
    }

    private fun hermesGatewayStatuses(
        selectedGateway: GatewaySummary,
        appRelayStatus: AggregateStatus,
        appRelayDetail: String
    ): List<GatewayStatus> {
        val relayHost = selectedGateway.statuses.find { it.phase == ConnectionPhase.relayHost }
            ?: GatewayStatus(
                phase = ConnectionPhase.relayHost,
                status = selectedGateway.aggregateStatus,
                detail = choose("Relay is connected to the host", "Relay 已连接到主机")
            )
        val hostGateway = GatewayStatus(
            phase = ConnectionPhase.hostGateway,
            status = selectedGateway.aggregateStatus,
            detail = if (selectedGateway.aggregateStatus == AggregateStatus.online) {
                choose("Hermes Agent is running", "Hermes Agent 运行正常")
            } else {
                choose("Hermes Agent is not connected", "Hermes Agent 未连接")
            }
        )
        return listOf(GatewayStatus(phase = ConnectionPhase.appRelay, status = appRelayStatus, detail = appRelayDetail), relayHost, hostGateway)
    }
}
