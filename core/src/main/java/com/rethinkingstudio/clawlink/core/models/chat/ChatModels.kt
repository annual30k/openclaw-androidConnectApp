package com.rethinkingstudio.clawlink.core.models.chat

import com.rethinkingstudio.clawlink.core.state.chat.isProtocolTypingMarkerText
import com.rethinkingstudio.clawlink.core.state.chat.isTransientAssistantPlaceholderContent
import kotlinx.serialization.KSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonNames
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
                    if (key in listOf("tool", "status", "type", "toolName", "tool_name")) continue
                    val rendered = value[key]?.renderedText(preferredKeys)
                    if (!rendered.isNullOrBlank()) return rendered
                }
                prettyJsonString()
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

    fun numberValue(keys: List<String>): Double? {
        return findNumberRecursively(this, keys)
    }

    private fun findNumberRecursively(node: RelayJSONValue, keys: List<String>): Double? {
        when (node) {
            is ObjectVal -> {
                // First pass: check immediate keys
                for (key in keys) {
                    val valNode = node.value[key]
                    if (valNode is NumberVal) return valNode.value
                    if (valNode is StringVal) {
                        val d = valNode.value.toDoubleOrNull()
                        if (d != null) return d
                    }
                }
                // Second pass: recurse into children
                for (child in node.value.values) {
                    val result = findNumberRecursively(child, keys)
                    if (result != null) return result
                }
            }
            is ArrayVal -> {
                for (child in node.value) {
                    val result = findNumberRecursively(child, keys)
                    if (result != null) return result
                }
            }
            else -> {}
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
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("file_id")
    val fileId: String? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("file_name")
    val fileName: String? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("mime_type")
    val mimeType: String? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("size_bytes")
    val sizeBytes: Int? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("duration_ms")
    val durationMs: Int? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("image_width")
    val imageWidth: Int? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("image_height")
    val imageHeight: Int? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("download_url", "url")
    val downloadUrl: String? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("download_path")
    val downloadPath: String? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("thumbnail_url")
    val thumbnailUrl: String? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("expires_at")
    val expiresAt: String? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("sender_display_name")
    val senderDisplayName: String? = null,
    val transcript: String? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("source_run_id")
    val sourceRunId: String? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("gateway_id")
    val gatewayId: String? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("session_key")
    val sessionKey: String? = null,
    @Serializable(with = RelayJSONValueSerializer::class) val arguments: RelayJSONValue? = null,
    @Serializable(with = RelayJSONValueSerializer::class) val args: RelayJSONValue? = null,
    @Serializable(with = RelayJSONValueSerializer::class) val result: RelayJSONValue? = null,
    @Serializable(with = RelayJSONValueSerializer::class) val partialResult: RelayJSONValue? = null,
    @Serializable(with = RelayJSONValueSerializer::class) val content: RelayJSONValue? = null,
    @Serializable(with = RelayJSONValueSerializer::class) val output: RelayJSONValue? = null,
    @Serializable(with = RelayJSONValueSerializer::class) val error: RelayJSONValue? = null,
    val preview: String? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("tool_call_id")
    val toolCallId: String? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("tool_use_id")
    val toolUseId: String? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("tool_name")
    val toolName: String? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("tool_state")
    val toolState: String? = null,
    val status: String? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("has_full_detail")
    val hasFullDetail: Boolean? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("detail_truncated")
    val detailTruncated: Boolean? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("detail_expired")
    val detailExpired: Boolean? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("detail_expires_at")
    val detailExpiresAt: String? = null,
    val chunked: Boolean? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("is_error")
    val isError: Boolean? = null
) : java.io.Serializable {

    private val normalizedType: String
        get() = type.trim()
            .lowercase()
            .replace("_", "")
            .replace("-", "")
            .replace(" ", "")

    val isToolCallBlock: Boolean
        get() = normalizedType in listOf("tooluse", "toolcall", "toolcallupdate", "functioncall")
    val isToolResultBlock: Boolean
        get() = normalizedType in listOf("toolresult", "toolresulterror", "tooloutput", "toolouterror", "tooloutputerror", "functionresult")
    private val normalizedMimeType: String get() = mimeType?.trim()?.lowercase().orEmpty()
    private val normalizedFileName: String get() = fileDisplayName?.trim()?.lowercase().orEmpty()
    private val isAudioMimeType: Boolean get() = normalizedMimeType.startsWith("audio/")
    private val isAudioFileName: Boolean
        get() = normalizedFileName.endsWith(".m4a") ||
            normalizedFileName.endsWith(".aac") ||
            normalizedFileName.endsWith(".mp3") ||
            normalizedFileName.endsWith(".wav") ||
            normalizedFileName.endsWith(".aiff") ||
            normalizedFileName.endsWith(".caf") ||
            normalizedFileName.endsWith(".ogg") ||
            normalizedFileName.endsWith(".opus")
    val isVoiceMessageBlock: Boolean
        get() = type in listOf("voice", "voice_message", "voice_result") ||
            isAudioMimeType ||
            isAudioFileName
    val isFileBlock: Boolean
        get() {
            val normalized = type.trim().lowercase()
            if (isVoiceMessageBlock) return false
            return normalized in listOf(
                "file",
                "file_upload",
                "file_result",
                "fileattachment",
                "attachment",
                "image",
                "image_file",
                "video",
                "video_file"
            )
        }
    val isTextBlock: Boolean get() = type in listOf("text", "output_text", "input_text")

    private val resolvedPayload: RelayJSONValue? get() = result ?: partialResult ?: content ?: output ?: error ?: arguments ?: args
    val resolvedName: String? get() = name ?: toolName ?: toolCallId ?: toolUseId ?: result?.stringValuesForKeys(listOf("tool", "name")) ?: content?.stringValuesForKeys(listOf("tool", "name")) ?: output?.stringValuesForKeys(listOf("tool", "name")) ?: error?.stringValuesForKeys(listOf("tool", "name")) ?: arguments?.stringValuesForKeys(listOf("tool", "name")) ?: args?.stringValuesForKeys(listOf("tool", "name"))
    val fileDisplayName: String? get() = fileName ?: name ?: text
    val resolvedImageWidth: Int?
        get() {
            if (imageWidth != null && imageWidth > 0) return imageWidth
            val keys = listOf("imageWidth", "image_width", "width", "w")
            return resolvedPayload?.numberValue(keys)?.toInt()
        }
    val resolvedImageHeight: Int?
        get() {
            if (imageHeight != null && imageHeight > 0) return imageHeight
            val keys = listOf("imageHeight", "image_height", "height", "h")
            return resolvedPayload?.numberValue(keys)?.toInt()
        }
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
    val voiceDurationText: String?
        get() {
            val duration = durationMs?.takeIf { it > 0 } ?: return null
            val totalSeconds = maxOf(1, kotlin.math.round(duration / 1000.0).toInt())
            return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60)
        }
    val voiceTranscriptText: String? get() = transcript?.trim()?.takeIf { it.isNotEmpty() }
    val voiceStatusText: String?
        get() {
            val duration = voiceDurationText
            val sender = senderDisplayName?.trim()?.takeIf { it.isNotEmpty() }
            return listOfNotNull(duration, sender)
                .joinToString(" · ")
                .ifBlank { status?.trim()?.takeIf { it.isNotEmpty() } }
        }
    val voiceDownloadURLString: String? get() = fileDownloadURLString
    val voicePlaybackIdentifier: String
        get() {
            val stable = listOf(fileId, voiceDownloadURLString, fileName, text)
                .firstNotNullOfOrNull { it?.trim()?.takeIf { value -> value.isNotEmpty() } }
            if (stable != null) return stable
            val fallback = listOf(gatewayId, sessionKey, type, fileName, downloadUrl, downloadPath, text, durationMs?.toString())
                .mapNotNull { it?.trim()?.takeIf { value -> value.isNotEmpty() } }
            return if (fallback.isEmpty()) "voice-unknown" else "voice:${fallback.joinToString("|")}"
        }
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

    val voiceTranscriptText: String?
        get() = voiceContentBlocks.firstNotNullOfOrNull { it.voiceTranscriptText }

    val toolDisplayName: String? get() = toolContentBlocks.firstNotNullOfOrNull { it.resolvedName }

    val toolDisplaySummary: String
        get() {
            val names = toolContentBlocks.mapNotNull { it.resolvedName?.trim()?.takeIf { name -> name.isNotEmpty() } }
            if (names.isEmpty()) return ""
            return if (names.size <= 3) {
                names.joinToString(separator = ", ")
            } else {
                "${names.take(2).joinToString(separator = ", ")} +${names.size - 2} more"
            }
        }

    private val hasRenderablePlainTextContent: Boolean
        get() = content.trim().isNotEmpty()

    private val hasRenderableStructuredContent: Boolean
        get() = contentBlocks.any { block ->
            block.isFileBlock ||
                block.isVoiceMessageBlock ||
                block.isToolCallBlock ||
                block.isToolResultBlock ||
                !block.text.isNullOrBlank() ||
                !block.transcript.isNullOrBlank()
        }

    fun shouldDisplayInChat(
        showInvocationProcess: Boolean
    ): Boolean {
        val isStreamingAssistantPlaceholder = role == MessageRole.assistant && state == MessageState.streaming
        if (isProtocolTypingMarkerText(plainTextContent) && !isStreamingAssistantPlaceholder) return false
        if (role == MessageRole.assistant &&
            !isStreamingAssistantPlaceholder &&
            isTransientAssistantPlaceholderContent(plainTextContent)
        ) return false
        val hasRenderableContent = hasRenderablePlainTextContent || hasRenderableStructuredContent
        if (!hasRenderableContent && !isStreamingAssistantPlaceholder) return false

        if (!hasToolContent) return true

        return showInvocationProcess
    }
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

@Serializable
data class ToolDetailResponse(
    val toolCallId: String,
    val name: String? = null,
    val state: String? = null,
    val preview: String? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("has_full_detail")
    val hasFullDetail: Boolean = false,
    val truncated: Boolean = false,
    val expired: Boolean = false,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("expires_at")
    val expiresAt: String? = null,
    val content: String = "",
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("content_blocks")
    val contentBlocks: List<RelayChatContentBlock> = emptyList(),
    val offset: Int = 0,
    val limit: Int = 0,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("has_more")
    val hasMore: Boolean = false,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("next_cursor")
    val nextCursor: String? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("download_url")
    val downloadUrl: String? = null
)
