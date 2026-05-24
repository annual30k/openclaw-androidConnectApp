package com.rethinkingstudio.clawlink.core.models.gateway

import com.rethinkingstudio.clawlink.core.models.chat.ChatSlashCommand
import kotlinx.serialization.Serializable

@Serializable
enum class AggregateStatus {
    online, connecting, partial, offline;

    companion object {
        fun fromString(value: String?): AggregateStatus {
            val normalized = value?.trim() ?: ""
            return when (normalized) {
                // Chinese server labels
                "在线" -> online
                "连接中" -> connecting
                "半可用" -> partial
                "离线" -> offline
                // English server values
                "healthy", "relay_connected" -> online
                "connecting", "connecting_relay", "connecting_openclaw" -> connecting
                "degraded", "backoff" -> partial
                "offline", "unknown" -> offline
                // Fallback: exact enum name match
                else -> entries.find { it.name == normalized } ?: offline
            }
        }
    }
}

@Serializable
enum class ConnectionPhase {
    appRelay, relayHost, hostGateway;

    companion object {
        fun fromString(value: String?): ConnectionPhase {
            val normalized = value
                ?.trim()
                ?.replace(" -> ", "_")
                ?.replace(" ", "_")
                ?.replace("-", "_")
                ?.lowercase()
                ?: ""
            return when (normalized) {
                "apprelay", "app_relay", "app_relay_phase" -> appRelay
                "relayhost", "relay_host" -> relayHost
                "hostgateway", "host_gateway", "host_openclaw", "host_to_openclaw",
                "host_hermes_agent", "host_to_hermes_agent" -> hostGateway
                else -> entries.find { it.name.equals(value?.trim(), ignoreCase = true) } ?: appRelay
            }
        }
    }
}

@Serializable
enum class GatewayType {
    openclaw, hermes;

    val displayTitle: String
        get() = when (this) {
            openclaw -> "OpenClaw"
            hermes -> "Hermes Agent"
        }

    companion object {
        fun fromString(value: String?): GatewayType {
            return when (value?.trim()?.lowercase()) {
                "hermes" -> hermes
                else -> openclaw
            }
        }
    }
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
    val gatewayType: GatewayType = GatewayType.openclaw,
    val capabilities: List<String> = emptyList(),
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
