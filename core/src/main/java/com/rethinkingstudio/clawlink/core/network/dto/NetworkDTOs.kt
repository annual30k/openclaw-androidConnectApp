package com.rethinkingstudio.clawlink.core.network.dto

import com.rethinkingstudio.clawlink.core.models.chat.ChatSessionItem
import com.rethinkingstudio.clawlink.core.models.backups.BackupItem
import com.rethinkingstudio.clawlink.core.models.catalog.ModelItem
import com.rethinkingstudio.clawlink.core.models.chat.ChatSlashCommand
import com.rethinkingstudio.clawlink.core.models.gateway.AggregateStatus
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

// ── Auth DTOs ────────────────────────────────────────────────────────────

@Serializable
data class PairRequest(
    val gatewayId: String? = null,
    val accessCode: String,
    val deviceId: String,
    val platform: String = "android",
    val appVersion: String = "1.0.0"
)

@Serializable
data class AuthRequest(
    val name: String? = null,
    val email: String,
    val password: String,
    val deviceId: String,
    val platform: String = "android",
    val appVersion: String = "1.0.0"
)

@Serializable
data class VerifyEmailRequest(
    val email: String,
    val code: String,
    val deviceId: String,
    val platform: String = "android",
    val appVersion: String = "1.0.0"
)

@Serializable
data class LoginResponse(
    val accessToken: String
)

@Serializable
data class RegisterResponse(
    val accessToken: String? = null,
    val verificationRequired: Boolean? = null,
    val email: String? = null,
    val expiresAt: String? = null
)

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
    val statuses: List<GatewayStatusDTO>? = null
) {
    fun toGatewaySummary(): GatewaySummary {
        val usageValue = contextUsageValue ?: com.rethinkingstudio.clawlink.core.utils.TokenDisplayFormatter.parseNonNegativeInteger(contextUsage)
        val usageText = com.rethinkingstudio.clawlink.core.utils.TokenDisplayFormatter.formatUsage(
            usedTokens = usageValue,
            limitTokens = contextLimit,
            fallback = contextUsage
        )
        
        return GatewaySummary(
            gatewayId = gatewayId,
            displayName = displayName,
            platform = platform,
            role = role,
            aggregateStatus = try { AggregateStatus.valueOf(aggregateStatus) } catch (_: Exception) { AggregateStatus.offline },
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
            statuses = statuses?.map { it.toGatewayStatus() } ?: emptyList()
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
            phase = try { com.rethinkingstudio.clawlink.core.models.gateway.ConnectionPhase.valueOf(phase) } catch (_: Exception) { com.rethinkingstudio.clawlink.core.models.gateway.ConnectionPhase.appRelay },
            status = try { AggregateStatus.valueOf(status) } catch (_: Exception) { AggregateStatus.offline },
            detail = detail
        )
    }
}

@Serializable
data class ModelListResponse(
    val items: List<ModelItem>
)

@Serializable
data class SkillsListResponse(
    val skills: List<SkillItem>
)

@Serializable
data class ChatHistoryResponse(
    val items: List<ChatHistoryItem>
)

@Serializable
data class ChatHistoryItem(
    val id: String,
    val role: String,
    val content: JsonElement? = null,
    val contentBlocks: List<com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock>? = null,
    val createdAt: String? = null
)

@Serializable
data class GatewayChatReadyResponse(
    val ready: Boolean
)

@Serializable
data class ChatSessionListResponse(
    val items: List<ChatSessionItem>
)

@Serializable
data class ChatSessionDeleteResponse(
    val ok: Boolean,
    val deleted: Boolean
)

@Serializable
data class TaskListResponse(
    val items: List<TaskItem>
)

@Serializable
data class TaskDetailResponse(
    val task: TaskItem
)

@Serializable
data class LogTailResponse(
    val logPath: String? = null,
    val lines: List<String>,
    val totalLines: Int,
    val returnedLines: Int,
    val truncated: Boolean
)

@Serializable
data class SelectModelRequest(
    val providerId: String,
    val modelId: String,
    val modelAlias: String,
    val modelName: String,
    val sessionKey: String? = null
)

@Serializable
data class DefaultModelRequest(
    val providerId: String,
    val modelId: String,
    val modelAlias: String
)

@Serializable
data class SkillUpdateRequest(
    val enabled: Boolean? = null,
    val apiKey: String? = null,
    val env: Map<String, String>? = null
)

@Serializable
data class TaskToggleRequest(
    val enabled: Boolean
)

@Serializable
data class UpdateGatewayDisplayNameRequest(
    val displayName: String
)

@Serializable
data class ApproveSensitiveActionRequest(
    val method: String
)

@Serializable
data class ApproveSensitiveActionResponse(
    val ok: Boolean,
    val gatewayId: String,
    val method: String,
    val expiresAt: String
)

@Serializable
data class FileUploadInitRequest(
    val sessionKey: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
    val durationMs: Int? = null,
    val imageWidth: Int? = null,
    val imageHeight: Int? = null,
    val senderDisplayName: String? = null,
    val clientCreatedAt: String? = null
)

@Serializable
data class FileUploadInitResponse(
    val fileId: String,
    val uploadId: String,
    val chunkSize: Int,
    val expiresAt: String,
    val uploadUrl: String
)

@Serializable
data class FileUploadCompleteRequest(
    val totalChunks: Int
)

@Serializable
data class FileUploadCompleteResponse(
    val ok: Boolean,
    val payload: RelayFileTransferItem
)

@Serializable
data class RelayFileTransferItem(
    val fileId: String,
    val gatewayId: String,
    val sessionKey: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val durationMs: Int? = null,
    val imageWidth: Int? = null,
    val imageHeight: Int? = null,
    val sha256: String,
    val origin: String,
    val senderDisplayName: String? = null,
    val createdAt: String,
    val sortTimestampMs: Long? = null,
    val updatedAt: String,
    val expiresAt: String,
    val status: String,
    val storagePath: String,
    val downloadPath: String,
    val chunkSize: Int,
    val totalChunks: Int
)

@Serializable
data class APIErrorResponse(
    val error: String,
    val remainingAttempts: Int? = null,
    val retryAfterSeconds: Int? = null
)

@Serializable
data class AdvancedActionResponse(
    val requestId: String,
    val status: String
)

@Serializable
data class AdvancedActionLogEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val requestId: String,
    val text: String,
    val stream: String,
    val createdAt: String
)

@Serializable
class EmptyResponse
