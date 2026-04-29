package com.rethinkingstudio.clawlink.core.models.tasks

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

@Serializable
data class TaskItem(
    val id: String,
    val title: String,
    val prompt: String,
    val scheduleKind: String,
    val scheduleAt: String? = null,
    @Serializable(with = FlexibleStringSerializer::class) val repeatAmount: String? = null,
    val repeatUnit: String? = null,
    val enabled: Boolean,
    val lastResult: String,
    val nextRunAt: String? = null,
    val createdAt: String,
    val updatedAt: String
) {
    val isPaused: Boolean get() = !enabled
    val template: String get() = prompt.trim().ifEmpty { "Custom" }
    val promptPreview: String get() = prompt.trim().ifEmpty { "(empty)" }
}

@Serializable
data class TaskDraft(
    val title: String = "",
    val prompt: String = "",
    val scheduleKind: String = "repeat",
    val scheduleAt: String? = null,
    val repeatAmount: String? = null,
    val repeatUnit: String? = "hours"
)

object FlexibleStringSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexibleString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
    }

    override fun deserialize(decoder: Decoder): String? {
        return try {
            val element = decoder.decodeSerializableValue(JsonElement.serializer())
            when {
                element is JsonPrimitive && element.isString -> element.content.trim().ifEmpty { null }
                element is JsonPrimitive && element.intOrNull != null -> element.intOrNull.toString()
                element is JsonPrimitive && element.doubleOrNull != null -> {
                    val d = element.doubleOrNull ?: return null
                    if (d.toLong().toDouble() == d) d.toLong().toString() else d.toString()
                }
                else -> null
            }
        } catch (_: Exception) {
            try { decoder.decodeString().trim().ifEmpty { null } } catch (_: Exception) { null }
        }
    }
}
