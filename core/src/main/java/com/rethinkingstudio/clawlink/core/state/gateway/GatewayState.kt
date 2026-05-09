package com.rethinkingstudio.clawlink.core.state.gateway

import com.rethinkingstudio.clawlink.core.models.MaintenanceLogEntry
import com.rethinkingstudio.clawlink.core.models.gateway.AggregateStatus
import com.rethinkingstudio.clawlink.core.models.gateway.ConnectionPhase
import com.rethinkingstudio.clawlink.core.models.gateway.GatewayStatus
import com.rethinkingstudio.clawlink.core.models.gateway.GatewaySummary

data class GatewayState(
    val gateways: List<GatewaySummary> = emptyList(),
    val selectedGatewayId: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val appRelayStatus: AggregateStatus = AggregateStatus.offline,
    val restartLogs: List<MaintenanceLogEntry> = emptyList(),
    val remoteRestartLogs: List<MaintenanceLogEntry> = emptyList(),
    val doctorFixLogs: List<MaintenanceLogEntry> = emptyList(),
    val restartRequestId: String? = null,
    val remoteRestartRequestId: String? = null,
    val doctorFixRequestId: String? = null,
    val restartingGatewayId: String? = null,
    val selectedChatSessionKey: String? = null,
    val isExecutingMaintenance: Boolean = false,
    val isWaitingForRecovery: Boolean = false,
    val maintenanceError: String? = null,
    val maintenanceStartedAt: Long? = null
) {
    val selectedGateway: GatewaySummary? get() = gateways.find { it.id == selectedGatewayId }
    val selectedGatewayStatuses: List<GatewayStatus>
        get() = GatewayStore.selectedGatewayStatuses(selectedGateway, appRelayStatus)
    val selectedGatewayAggregateStatus: AggregateStatus
        get() = GatewayStore.aggregateStatusForChain(selectedGateway, appRelayStatus)
    val isAppRelayOnline: Boolean get() = appRelayStatus == AggregateStatus.online
    val isRelayHostOnline: Boolean
        get() = selectedGatewayStatuses.find { it.phase == ConnectionPhase.relayHost }?.status == AggregateStatus.online
    val isHostGatewayOnline: Boolean
        get() = selectedGatewayStatuses.find { it.phase == ConnectionPhase.hostGateway }?.status == AggregateStatus.online
    val isSelectedGatewayChatChainReady: Boolean
        get() = isAppRelayOnline && GatewayStore.gatewayIsFullyOnline(selectedGateway)
    val canExecuteRecoveryAction: Boolean
        get() = selectedGateway != null && isAppRelayOnline && isRelayHostOnline && !isHostGatewayOnline
    val canExecuteRemoteHostAction: Boolean
        get() = isSelectedGatewayChatChainReady || canExecuteRecoveryAction
}

