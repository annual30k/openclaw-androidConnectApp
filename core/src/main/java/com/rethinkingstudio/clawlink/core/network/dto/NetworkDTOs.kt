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

@Serializable(with = ChatHistoryItemSerializer::class)
data class ChatHistoryItem(
    val id: String,
    val role: String,
    val content: JsonElement? = null,
    val contentBlocks: List<com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock>? = null,
    val createdAt: String? = null
)

object ChatHistoryItemSerializer : KSerializer<ChatHistoryItem> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: ChatHistoryItem) {
        val obj = buildJsonObject {
            put("id", JsonPrimitive(value.id))
            put("role", JsonPrimitive(value.role))
            value.content?.let { put("content", it) }
            value.contentBlocks?.takeIf { it.isNotEmpty() }?.let { blocks ->
                put(
                    "contentBlocks",
                    JsonArray(blocks.map { Json.encodeToJsonElement(com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock.serializer(), it) })
                )
            }
            value.createdAt?.let { put("createdAt", JsonPrimitive(it)) }
        }
        encoder.encodeSerializableValue(JsonElement.serializer(), obj)
    }

    override fun deserialize(decoder: Decoder): ChatHistoryItem {
        val element = decoder.decodeSerializableValue(JsonElement.serializer())
        val obj = element as? JsonObject ?: throw SerializationException("Expected chat history item object")
        val id = obj.string("id") ?: throw SerializationException("Chat history item missing id")
        val role = obj.string("role") ?: "assistant"
        val content = obj["content"]
            ?: obj["text"]
            ?: (obj["message"] as? JsonObject)?.get("content")
            ?: (obj["message"] as? JsonObject)?.get("text")
        val contentBlocks = extractContentBlocks(obj)
        val createdAt = obj.string("createdAt", "created_at")
        return ChatHistoryItem(
            id = id,
            role = role,
            content = content,
            contentBlocks = contentBlocks,
            createdAt = createdAt
        )
    }

    private fun extractContentBlocks(root: JsonObject): List<com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock> {
        val arrays = mutableListOf<JsonArray>()
        collectArrays(root, arrays, mutableSetOf())
        if (arrays.isEmpty()) return emptyList()

        val seen = linkedSetOf<String>()
        return arrays.flatMap { array ->
            array.mapNotNull { element ->
                runCatching {
                    Json.decodeFromJsonElement(com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock.serializer(), element)
                }.getOrNull()
            }
        }.filter { block ->
            seen.add(
                listOf(
                    block.type,
                    block.toolCallId.orEmpty(),
                    block.toolUseId.orEmpty(),
                    block.resolvedName.orEmpty(),
                    block.text.orEmpty(),
                    block.fileId.orEmpty(),
                    block.fileName.orEmpty(),
                    block.status.orEmpty()
                ).joinToString("|")
            )
        }
    }

    private fun collectArrays(
        element: JsonElement?,
        arrays: MutableList<JsonArray>,
        visited: MutableSet<Int>
    ) {
        val current = element ?: return
        val identity = System.identityHashCode(current)
        if (!visited.add(identity)) return

        when (current) {
            is JsonArray -> {
                if (current.any { it is JsonObject && it["type"] != null }) {
                    arrays += current
                }
                current.forEach { child ->
                    collectArrays(child, arrays, visited)
                }
            }
            is JsonObject -> {
                current.values.forEach { child ->
                    if (child is JsonArray || child is JsonObject) {
                        collectArrays(child, arrays, visited)
                    }
                }
            }
            else -> Unit
        }
    }
}

private fun JsonObject.string(vararg keys: String): String? {
    return keys.firstNotNullOfOrNull { key ->
        (this[key] as? JsonPrimitive)
            ?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }
}

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
