package com.rethinkingstudio.clawlink.core.models

import com.rethinkingstudio.clawlink.core.models.gateway.AggregateStatus
import kotlinx.serialization.Serializable

@Serializable
enum class OfficeActivityKind {
    IDLE,
    WRITING,
    RESEARCHING,
    EXECUTING,
    SYNCING,
    SLEEPING,
    OFFLINE,
    ERROR
}

@Serializable
enum class OfficeStation {
    DESK_LEFT,
    DESK_CENTER,
    COFFEE_DESK,
    SOFA,
    BED,
    SERVER_RACK,
    CORNER
}

@Serializable
data class OfficeAgentSnapshot(
    val id: String,
    val gatewayId: String,
    val displayName: String,
    val platform: String,
    val currentModel: String,
    val contextUsage: String,
    val aggregateStatus: AggregateStatus,
    val mobileControlStatus: String?,
    val activityKind: OfficeActivityKind,
    val activityTitle: String,
    val activityDetail: String,
    val activityPhase: String?,
    val activityToolName: String?,
    val activityToolCallId: String?,
    val activityProgress: Double?,
    val activityUpdatedAt: String?,
    val isSelected: Boolean,
    val motionSeed: Double
) {
    val isWorking: Boolean
        get() = when (activityKind) {
            OfficeActivityKind.SLEEPING, OfficeActivityKind.OFFLINE -> false
            else -> true
        }
}

@Serializable
data class OfficeSceneSnapshot(
    val agents: List<OfficeAgentSnapshot>
) {
    val focusAgent: OfficeAgentSnapshot?
        get() = agents.find { it.isSelected } ?: agents.firstOrNull()

    val primaryExecutingAgent: OfficeAgentSnapshot?
        get() = agents.find { it.activityKind == OfficeActivityKind.EXECUTING }

    val activeCount: Int
        get() = agents.count { it.isWorking }

    val onlineCount: Int
        get() = agents.count { it.aggregateStatus != AggregateStatus.offline }

    val offlineCount: Int
        get() = agents.count { it.aggregateStatus == AggregateStatus.offline }
}

@Serializable
data class OfficeNPC(
    val id: Int,
    val waypoints: List<PointF>,
    val speed: Float,
    val waitAtEachStop: Double,
    val timeOffset: Double
)

@Serializable
data class PointF(
    val x: Float,
    val y: Float
) {
    companion object
}


