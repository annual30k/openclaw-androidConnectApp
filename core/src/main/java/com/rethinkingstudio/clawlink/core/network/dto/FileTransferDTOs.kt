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
    val clientCreatedAt: String? = null,
    val sourceRunId: String? = null
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
    val totalChunks: Int,
    val sourceRunId: String? = null
)
