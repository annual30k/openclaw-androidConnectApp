package com.rethinkingstudio.clawlink.core.models.gateway

import com.rethinkingstudio.clawlink.core.models.chat.ChatSlashCommand
import kotlinx.serialization.Serializable

@Serializable
enum class AggregateStatus {
    online, connecting, partial, offline
}

@Serializable
enum class ConnectionPhase {
    appRelay, relayHost, hostGateway
}

@Serializable
data class GatewayStatus(
    val id: String = java.util.UUID.randomUUID().toString(),
    val phase: ConnectionPhase,
    val status: AggregateStatus,
    val detail: String
)

@Serializable
data class GatewaySummary(
    val gatewayId: String,
    val displayName: String,
    val platform: String,
    val role: String? = null,
    val aggregateStatus: AggregateStatus,
    val lastSeenAt: String,
    val currentModel: String,
    val contextUsage: String,
    val contextUsageValue: Int? = null,
    val contextLimit: Int? = null,
    val mobileControlStatus: String? = null,
    val officeActivityKind: String? = null,
    val officeActivityTitle: String? = null,
    val officeActivityDetail: String? = null,
    val officeActivityPhase: String? = null,
    val officeActivityToolName: String? = null,
    val officeActivityToolCallId: String? = null,
    val officeActivityProgress: Double? = null,
    val officeActivityUpdatedAt: String? = null,
    val slashCommands: List<ChatSlashCommand>? = null,
    val statuses: List<GatewayStatus> = emptyList()
) {
    val id: String get() = gatewayId

    val statusIcon: String
        get() = when (aggregateStatus) {
            AggregateStatus.online -> "●"
            AggregateStatus.connecting -> "◌"
            AggregateStatus.partial -> "◐"
            AggregateStatus.offline -> "○"
        }

    val activitySummary: String?
        get() = officeActivityTitle ?: officeActivityDetail
}

data class GatewayFlowNode(
    val phase: ConnectionPhase,
    val status: AggregateStatus,
    val detail: String
)
