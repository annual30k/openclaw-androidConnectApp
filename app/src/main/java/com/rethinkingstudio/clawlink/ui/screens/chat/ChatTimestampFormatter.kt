package com.rethinkingstudio.clawlink.ui.screens.chat

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val chatTimestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

internal fun formatChatTimestamp(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return "刚刚"

    runCatching { Instant.parse(trimmed) }.getOrNull()?.let { instant ->
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).format(chatTimestampFormatter)
    }

    val normalized = trimmed
        .removeSuffix("Z")
        .replace('T', ' ')
        .substringBefore('.')
    if (normalized.matches(Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}"""))) {
        return normalized
    }

    return try {
        LocalDateTime.parse(trimmed.substringBefore('.'), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .format(chatTimestampFormatter)
    } catch (_: DateTimeParseException) {
        trimmed
    } catch (_: IllegalArgumentException) {
        trimmed
    }
}
