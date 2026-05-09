package com.rethinkingstudio.clawlink.core.network.dto

import com.rethinkingstudio.clawlink.core.models.chat.ChatSessionItem
import com.rethinkingstudio.clawlink.core.models.backups.BackupItem
import com.rethinkingstudio.clawlink.core.models.catalog.ModelItem
import com.rethinkingstudio.clawlink.core.models.chat.ChatSlashCommand
import com.rethinkingstudio.clawlink.core.models.gateway.AggregateStatus
import com.rethinkingstudio.clawlink.core.models.gateway.ConnectionPhase
import com.rethinkingstudio.clawlink.core.models.gateway.GatewayStatus
import com.rethinkingstudio.clawlink.core.models.gateway.GatewaySummary
import com.rethinkingstudio.clawlink.core.models.skills.SkillItem
import com.rethinkingstudio.clawlink.core.models.tasks.TaskItem
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.SerializationException

@Serializable
data class GatewayListResponse(
    val gateways: List<GatewaySummaryDTO>
)

@Serializable
data class GatewaySummaryDTO(
    val gatewayId: String,
    val displayName: String,
    val platform: String,
    val role: String? = null,
    val aggregateStatus: String,
    val relayStatus: String? = null,
    val hostStatus: String? = null,
    val openclawStatus: String? = null,
    val lastSeenAt: String,
    val currentModel: String,
    val contextUsage: JsonElement? = null,
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
    val statuses: List<GatewayStatusDTO>? = null
) {
    fun toGatewaySummary(): GatewaySummary {
        val usageFallback = contextUsage.displayText()
        val usageValue = contextUsageValue ?: contextUsage.nonNegativeInt()
        val usageText = com.rethinkingstudio.clawlink.core.utils.TokenDisplayFormatter.formatUsage(
            usedTokens = usageValue,
            limitTokens = contextLimit,
            fallback = usageFallback
        )
        
        return GatewaySummary(
            gatewayId = gatewayId,
            displayName = displayName,
            platform = platform,
            role = role,
            aggregateStatus = AggregateStatus.fromString(aggregateStatus),
            lastSeenAt = lastSeenAt,
            currentModel = currentModel,
            contextUsage = usageText,
            contextUsageValue = usageValue,
            contextLimit = contextLimit,
            mobileControlStatus = mobileControlStatus,
            officeActivityKind = officeActivityKind,
            officeActivityTitle = officeActivityTitle,
            officeActivityDetail = officeActivityDetail,
            officeActivityPhase = officeActivityPhase,
            officeActivityToolName = officeActivityToolName,
            officeActivityToolCallId = officeActivityToolCallId,
            officeActivityProgress = officeActivityProgress,
            officeActivityUpdatedAt = officeActivityUpdatedAt,
            slashCommands = slashCommands,
            statuses = statuses?.map { it.toGatewayStatus() } ?: synthesizedStatuses()
        )
    }

    private fun JsonElement?.displayText(): String {
        val primitive = this as? JsonPrimitive ?: return ""
        return primitive.contentOrNull?.trim().orEmpty()
    }

    private fun JsonElement?.nonNegativeInt(): Int? {
        val primitive = this as? JsonPrimitive ?: return null
        val value = primitive.intOrNull ?: primitive.longOrNull?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt()
        return value?.takeIf { it >= 0 }
    }

    private fun synthesizedStatuses(): List<GatewayStatus> {
        val openClawState = openclawStatus ?: hostStatus
        return listOf(
            GatewayStatus(
                phase = ConnectionPhase.appRelay,
                status = AggregateStatus.online,
                detail = "Session active"
            ),
            GatewayStatus(
                phase = ConnectionPhase.relayHost,
                status = AggregateStatus.fromString(relayStatus),
                detail = relayStatus ?: "offline"
            ),
            GatewayStatus(
                phase = ConnectionPhase.hostGateway,
                status = AggregateStatus.fromString(openClawState),
                detail = openClawState ?: "offline"
            )
        )
    }
}

@Serializable
data class GatewayStatusDTO(
    val phase: String,
    val status: String,
    val detail: String
) {
    fun toGatewayStatus(): GatewayStatus {
        return GatewayStatus(
            phase = ConnectionPhase.fromString(phase),
            status = AggregateStatus.fromString(status),
            detail = detail
        )
    }
}

