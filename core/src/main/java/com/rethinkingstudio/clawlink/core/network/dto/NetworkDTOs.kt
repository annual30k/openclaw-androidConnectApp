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
data class PasswordResetRequest(
    val email: String,
    val deviceId: String,
    val platform: String = "android",
    val appVersion: String = "1.0.0"
)

@Serializable
data class PasswordResetConfirmRequest(
    val email: String,
    val code: String,
    val newPassword: String
)

@Serializable
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

@Serializable
data class LoginResponse(
    val accessToken: String
)

@Serializable
data class PasswordResetResponse(
    val ok: Boolean,
    val email: String? = null,
    val expiresAt: String? = null
)

@Serializable
data class RegisterResponse(
    val accessToken: String? = null,
    val verificationRequired: Boolean? = null,
    val email: String? = null,
    val expiresAt: String? = null
)
