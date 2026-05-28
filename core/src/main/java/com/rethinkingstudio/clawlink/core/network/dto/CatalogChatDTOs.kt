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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.SerializationException

@Serializable
data class ModelListResponse(
    val items: List<ModelItem>
)

@Serializable
data class SkillsListResponse(
    val skills: List<SkillItem>
)

@Serializable(with = ChatHistoryResponseSerializer::class)
data class ChatHistoryResponse(
    val items: List<ChatHistoryItem>,
    val hasMore: Boolean = false,
    val nextCursor: String? = null,
    val newestCursor: String? = null
)

object ChatHistoryResponseSerializer : KSerializer<ChatHistoryResponse> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor
    private val itemJson = Json { ignoreUnknownKeys = true }

    override fun serialize(encoder: Encoder, value: ChatHistoryResponse) {
        val obj = buildJsonObject {
            put("items", JsonArray(value.items.map { itemJson.encodeToJsonElement(ChatHistoryItem.serializer(), it) }))
            put("hasMore", JsonPrimitive(value.hasMore))
            value.nextCursor?.let { put("nextCursor", JsonPrimitive(it)) }
            value.newestCursor?.let { put("newestCursor", JsonPrimitive(it)) }
        }
        encoder.encodeSerializableValue(JsonElement.serializer(), obj)
    }

    override fun deserialize(decoder: Decoder): ChatHistoryResponse {
        val element = decoder.decodeSerializableValue(JsonElement.serializer())
        val obj = element as? JsonObject ?: throw SerializationException("Expected chat history response object")
        val items = (obj["items"] as? JsonArray)
            ?.mapNotNull { element ->
                runCatching {
                    itemJson.decodeFromJsonElement(ChatHistoryItem.serializer(), element)
                }.getOrNull()
            }
            ?: emptyList()
        return ChatHistoryResponse(
            items = items,
            hasMore = obj.boolean("hasMore", "has_more") ?: false,
            nextCursor = obj.string("nextCursor", "next_cursor"),
            newestCursor = obj.string("newestCursor", "newest_cursor")
        )
    }
}

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
    private val blockJson = Json { ignoreUnknownKeys = true }

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
                    val blockObject = element as? JsonObject ?: return@runCatching null
                    blockJson.decodeFromJsonElement(
                        com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock.serializer(),
                        normalizeContentBlockObject(blockObject)
                    )
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

    private fun normalizeContentBlockObject(obj: JsonObject): JsonObject {
        val aliases = mapOf(
            "file_id" to "fileId",
            "file_name" to "fileName",
            "mime_type" to "mimeType",
            "size_bytes" to "sizeBytes",
            "duration_ms" to "durationMs",
            "image_width" to "imageWidth",
            "image_height" to "imageHeight",
            "download_url" to "downloadUrl",
            "download_path" to "downloadPath",
            "thumbnail_url" to "thumbnailUrl",
            "expires_at" to "expiresAt",
            "sender_display_name" to "senderDisplayName",
            "gateway_id" to "gatewayId",
            "session_key" to "sessionKey",
            "partial_result" to "partialResult",
            "tool_call_id" to "toolCallId",
            "tool_use_id" to "toolUseId",
            "tool_name" to "toolName",
            "is_error" to "isError"
        )
        return buildJsonObject {
            obj.forEach { (key, value) ->
                put(key, value)
                aliases[key]?.let { alias ->
                    if (!obj.containsKey(alias)) {
                        put(alias, value)
                    }
                }
            }
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

private fun JsonObject.boolean(vararg keys: String): Boolean? {
    return keys.firstNotNullOfOrNull { key ->
        (this[key] as? JsonPrimitive)?.booleanOrNull
    }
}
