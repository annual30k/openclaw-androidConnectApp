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
data class APIErrorResponse(
    val error: String,
    val remainingAttempts: Int? = null,
    val retryAfterSeconds: Int? = null
)

@Serializable
data class AdvancedActionResponse(
    val requestId: String,
    val status: String,
    val log: List<com.rethinkingstudio.clawlink.core.models.OpenClawDoctorFixLogEntry>? = null
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
data class BackupListResponse(
    val backups: List<BackupItem>,
    val maxBackups: Int = 5,
    val storagePath: String? = null
)

@Serializable
data class BackupMutationResponse(
    val backup: BackupItem,
    val backups: List<BackupItem>,
    val maxBackups: Int = 5,
    val storagePath: String? = null
)

@Serializable
class EmptyResponse
