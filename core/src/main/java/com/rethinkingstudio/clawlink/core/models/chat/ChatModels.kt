package com.rethinkingstudio.clawlink.core.models.chat

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.long

// ── MessageRole ──────────────────────────────────────────────────────────
@Serializable
enum class MessageRole {
    user, assistant, system, tool
}

// ── MessageState ─────────────────────────────────────────────────────────
@Serializable
enum class MessageState {
    completed, streaming, failed
}

// ── RelayJSONValue ───────────────────────────────────────────────────────
sealed class RelayJSONValue : java.io.Serializable {
    data object NullVal : RelayJSONValue()
    data class BoolVal(val value: Boolean) : RelayJSONValue()
    data class NumberVal(val value: Double) : RelayJSONValue()
    data class StringVal(val value: String) : RelayJSONValue()
    data class ArrayVal(val value: List<RelayJSONValue>) : RelayJSONValue()
    data class ObjectVal(val value: Map<String, RelayJSONValue>) : RelayJSONValue()

    val plainText: String?
        get() = when (this) {
            is NullVal -> "null"
            is BoolVal -> value.toString()
            is NumberVal -> if (value.toLong().toDouble() == value) value.toLong().toString() else value.toString()
            is StringVal -> value
            is ArrayVal, is ObjectVal -> prettyJsonString()
        }

    fun renderedText(preferredKeys: List<String> = emptyList()): String? {
        return when (this) {
            is NullVal -> null
            is BoolVal -> value.toString()
            is NumberVal -> if (value.toLong().toDouble() == value) value.toLong().toString() else value.toString()
            is StringVal -> value.trim().ifEmpty { null }
            is ArrayVal -> value.mapNotNull { it.renderedText(preferredKeys) }
                .map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n").ifEmpty { null }
            is ObjectVal -> {
                val searchKeys = preferredKeys + listOf(
                    "content", "markdown", "text", "body", "result", "output",
                    "value", "message", "command", "cmd", "script", "code",
                    "input", "prompt", "path", "filePath", "file_path"
                )
                for (key in searchKeys.distinct()) {
                    val rendered = value[key]?.renderedText(preferredKeys)
                    if (!rendered.isNullOrBlank()) return rendered
                }
                val leafTexts = value.values.mapNotNull { it.renderedText(preferredKeys) }
                    .map { it.trim() }.filter { it.isNotEmpty() }
                leafTexts.firstOrNull() ?: prettyJsonString()
            }
        }
    }

    fun stringValuesForKeys(keys: List<String>): String? {
        val obj = (this as? ObjectVal)?.value ?: return null
        for (key in keys) {
            val rendered = obj[key]?.renderedText(keys)
            if (!rendered.isNullOrBlank()) return rendered
        }
        return null
    }

    fun prettyJsonString(): String? {
        return try {
            toJsonElement().toString()
        } catch (_: Exception) {
            null
        }
    }

    fun toJsonElement(): JsonElement = when (this) {
        is NullVal -> JsonNull
        is BoolVal -> JsonPrimitive(value)
        is NumberVal -> JsonPrimitive(value)
        is StringVal -> JsonPrimitive(value)
        is ArrayVal -> kotlinx.serialization.json.JsonArray(value.map { it.toJsonElement() })
        is ObjectVal -> JsonObject(value.mapValues { it.value.toJsonElement() })
    }

    companion object {
        fun fromJsonElement(element: JsonElement): RelayJSONValue = when (element) {
            is JsonNull -> NullVal
            is JsonPrimitive -> when {
                element.isString -> StringVal(element.content)
                element.content.equals("true", ignoreCase = true) || element.content.equals("false", ignoreCase = true) ->
                    BoolVal(element.content.equals("true", ignoreCase = true))
                element.content.toLongOrNull() != null -> NumberVal(element.content.toLong().toDouble())
                element.content.toDoubleOrNull() != null -> NumberVal(element.content.toDouble())
                else -> StringVal(element.content)
            }
            is kotlinx.serialization.json.JsonArray -> ArrayVal(element.map { fromJsonElement(it) })
            is JsonObject -> ObjectVal(element.mapValues { fromJsonElement(it.value) })
        }
    }
}

object RelayJSONValueSerializer : KSerializer<RelayJSONValue> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("RelayJSONValue", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: RelayJSONValue) {
        val element = value.toJsonElement()
        encoder.encodeSerializableValue(JsonElement.serializer(), element)
    }

    override fun deserialize(decoder: Decoder): RelayJSONValue {
        val element = decoder.decodeSerializableValue(JsonElement.serializer())
        return RelayJSONValue.fromJsonElement(element)
    }
}

// ── RelayChatContentBlock ────────────────────────────────────────────────
@Serializable
data class RelayChatContentBlock(
    val type: String,
    val text: String? = null,
    val name: String? = null,
    val fileId: String? = null,
    val fileName: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Int? = null,
    val durationMs: Int? = null,
    val imageWidth: Int? = null,
    val imageHeight: Int? = null,
    val downloadUrl: String? = null,
    val downloadPath: String? = null,
    val thumbnailUrl: String? = null,
    val expiresAt: String? = null,
    val senderDisplayName: String? = null,
    val transcript: String? = null,
    val gatewayId: String? = null,
    val sessionKey: String? = null,
    @Serializable(with = RelayJSONValueSerializer::class) val arguments: RelayJSONValue? = null,
    @Serializable(with = RelayJSONValueSerializer::class) val args: RelayJSONValue? = null,
    @Serializable(with = RelayJSONValueSerializer::class) val result: RelayJSONValue? = null,
    @Serializable(with = RelayJSONValueSerializer::class) val partialResult: RelayJSONValue? = null,
    @Serializable(with = RelayJSONValueSerializer::class) val content: RelayJSONValue? = null,
    @Serializable(with = RelayJSONValueSerializer::class) val output: RelayJSONValue? = null,
    @Serializable(with = RelayJSONValueSerializer::class) val error: RelayJSONValue? = null,
    val toolCallId: String? = null,
    val toolUseId: String? = null,
    val toolName: String? = null,
    val status: String? = null,
    val isError: Boolean? = null
) : java.io.Serializable {

    val isToolCallBlock: Boolean get() = type in listOf("tool_use", "tool_call")
    val isToolResultBlock: Boolean get() = type in listOf("tool_result", "tool_call_result")
    val isFileBlock: Boolean
        get() {
            val normalized = type.trim().lowercase()
            return normalized in listOf("file", "file_upload", "file_result", "fileattachment", "attachment")
        }
    val isVoiceMessageBlock: Boolean get() = type in listOf("voice_message", "voice_result")
    val isTextBlock: Boolean get() = type in listOf("text", "output_text", "input_text")

    val resolvedName: String? get() = name ?: toolName
    val fileDisplayName: String? get() = fileName ?: name ?: text
    val fileDownloadURLString: String? get() = downloadUrl ?: downloadPath
    val fileStatusText: String?
        get() {
            val size = sizeBytes?.takeIf { it > 0 }?.let { ComposerAttachmentDraft.formatByteCount(it.toLong()) }
            val sender = senderDisplayName?.trim()?.takeIf { it.isNotEmpty() }
            return listOfNotNull(size, sender).joinToString(" · ").ifBlank { status }
        }
    val isImageFileBlock: Boolean
        get() {
            val normalizedMime = mimeType?.trim()?.lowercase().orEmpty()
            if (normalizedMime.startsWith("image/")) return true
            val normalizedName = fileDisplayName?.trim()?.lowercase().orEmpty()
            return normalizedName.endsWith(".png") ||
                normalizedName.endsWith(".jpg") ||
                normalizedName.endsWith(".jpeg") ||
                normalizedName.endsWith(".gif") ||
                normalizedName.endsWith(".webp") ||
                normalizedName.endsWith(".heic") ||
                normalizedName.endsWith(".heif") ||
                normalizedName.endsWith(".bmp") ||
                normalizedName.endsWith(".tiff") ||
                normalizedName.endsWith(".avif")
        }
    val voiceTranscriptText: String? get() = transcript
    val voiceStatusText: String? get() = status
}

// ── ChatMessage ──────────────────────────────────────────────────────────
@Serializable
data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val state: MessageState = MessageState.completed,
    val content: String = "",
    val contentBlocks: List<RelayChatContentBlock> = emptyList(),
    val createdAt: String = "",
    val runId: String = "",
    val sortTimestamp: Double? = null
) {
    val plainTextContent: String
        get() {
            val trimmed = content.trim()
            if (trimmed.isNotEmpty()) return trimmed
            return contentBlocks
                .filter { it.isTextBlock }
                .mapNotNull { it.text?.trim()?.ifEmpty { null } }
                .joinToString("\n\n")
                .trim()
        }

    val toolContentBlocks: List<RelayChatContentBlock>
        get() = contentBlocks.filter { it.isToolCallBlock || it.isToolResultBlock }

    val hasToolContent: Boolean
        get() = role == MessageRole.tool || toolContentBlocks.isNotEmpty()

    val fileContentBlocks: List<RelayChatContentBlock>
        get() = contentBlocks.filter { it.isFileBlock }

    val hasFileContent: Boolean get() = fileContentBlocks.isNotEmpty()

    val voiceContentBlocks: List<RelayChatContentBlock>
        get() = contentBlocks.filter { it.isVoiceMessageBlock }

    val hasVoiceContent: Boolean get() = voiceContentBlocks.isNotEmpty()

    val toolDisplayName: String? get() = toolContentBlocks.firstNotNullOfOrNull { it.resolvedName }
}

// ── ComposerAttachmentDraft ──────────────────────────────────────────────
data class ComposerAttachmentDraft(
    val id: String = java.util.UUID.randomUUID().toString(),
    val fileUri: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val durationMs: Long? = null,
    val imageWidth: Int? = null,
    val imageHeight: Int? = null
) {
    val displaySize: String get() = formatByteCount(sizeBytes)
    val displaySubtitle: String
        get() {
            val parts = mutableListOf(mimeType)
            if (durationMs != null && durationMs > 0) {
                val totalSec = maxOf(1, (durationMs / 1000.0).toInt())
                parts.add(String.format("%d:%02d", totalSec / 60, totalSec % 60))
            }
            parts.add(displaySize)
            return parts.filter { it.isNotBlank() }.joinToString(" · ")
        }

    companion object {
        fun formatByteCount(bytes: Long): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
            else -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }
}

// ── AttachmentUploadPhase ────────────────────────────────────────────────
enum class AttachmentUploadPhase { uploading, completed, failed }

data class ComposerAttachmentUploadItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val gatewayId: String,
    val attachment: ComposerAttachmentDraft,
    val progress: Double = 0.0,
    val phase: AttachmentUploadPhase = AttachmentUploadPhase.uploading,
    val failureMessage: String? = null
) {
    val isImage: Boolean get() = attachment.mimeType.lowercase().startsWith("image/")
    val statusText: String
        get() = when (phase) {
            AttachmentUploadPhase.uploading -> "上传中 ${"%.0f".format(progress * 100)}%"
            AttachmentUploadPhase.completed -> "已发送"
            AttachmentUploadPhase.failed -> failureMessage?.takeIf { it.isNotBlank() } ?: "上传失败"
        }
}

// ── ChatSlashCommand ─────────────────────────────────────────────────────
@Serializable
data class ChatSlashCommand(
    val command: String? = null,
    val name: String? = null,
    val title: String? = null,
    val detail: String? = null,
    val description: String? = null,
    val category: String? = null,
    val iconName: String? = null
)

// ── ChatSessionItem ──────────────────────────────────────────────────────
@Serializable
data class ChatSessionItem(
    val sessionKey: String,
    val lastActivityAt: String? = null,
    val displayName: String? = null,
    val label: String? = null,
    val derivedTitle: String? = null,
    val kind: String? = null
)
