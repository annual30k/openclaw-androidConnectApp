package com.rethinkingstudio.clawlink.core.models.tasks

import kotlinx.serialization.KSerializer
import kotlinx.serialization.ExperimentalSerializationApi
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

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
    val scheduleDate: Instant? get() = TaskDateCodec.instantFrom(scheduleAt)
    val nextRunDate: Instant? get() = TaskDateCodec.instantFrom(nextRunAt)

    val schedule: String
        get() = when (scheduleKind) {
            "once" -> TaskDateCodec.displayString(scheduleAt)?.let { "一次性 - $it" } ?: "一次性任务"
            "repeat" -> {
                val amount = repeatAmount?.trim().orEmpty()
                val unit = localizedRepeatUnit(repeatUnit)
                val startText = TaskDateCodec.displayString(scheduleAt)
                when {
                    amount.isEmpty() && startText == null -> "重复任务"
                    amount.isEmpty() -> "重复任务，首次 $startText"
                    startText == null -> "每 $amount $unit"
                    else -> "每 $amount $unit，首次 $startText"
                }
            }
            else -> scheduleKind.ifBlank { "未设置" }
        }

    val nextRunSummary: String
        get() = TaskDateCodec.displayString(nextRunAt)?.let { "下次 $it" } ?: "暂无下次执行"

    val lastResultSummary: String
        get() = lastResult.trim().ifEmpty { "暂无执行结果" }

    companion object {
        fun localizedRepeatUnit(value: String?): String {
            return when (value?.trim()?.lowercase()) {
                "minutes" -> "分钟"
                "hours" -> "小时"
                "days" -> "天"
                "weeks" -> "周"
                else -> value?.trim()?.ifEmpty { null } ?: "次"
            }
        }
    }
}

@Serializable
data class TaskDraft(
    val title: String = "",
    val prompt: String = "",
    val scheduleKind: String = "once",
    val scheduleAt: String = "",
    val repeatAmount: String = "1",
    val repeatUnit: String = "days"
)

object TaskDateCodec {
    private val displayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())

    fun instantFrom(value: String?): Instant? {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return try {
            Instant.parse(trimmed)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    fun isoString(instant: Instant): String = instant.toString()

    fun displayString(value: String?): String? {
        val instant = instantFrom(value) ?: return null
        return displayFormatter.format(instant)
    }
}

object FlexibleStringSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexibleString", PrimitiveKind.STRING)

    @OptIn(ExperimentalSerializationApi::class)
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
