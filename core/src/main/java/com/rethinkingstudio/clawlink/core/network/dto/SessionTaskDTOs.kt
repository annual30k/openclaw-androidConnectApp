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

