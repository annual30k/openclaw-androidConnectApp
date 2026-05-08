package com.rethinkingstudio.clawlink.ui.screens.chat

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private val localDateTimeParser: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

internal fun formatChatTimestamp(raw: String, now: Instant = Instant.now()): String {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return "刚刚"

    val zoneId = ZoneId.systemDefault()
    val nowDateTime = ZonedDateTime.ofInstant(now, zoneId)
    parseChatTimestamp(trimmed, zoneId)?.let { dateTime ->
        return formatRelativeChatTimestamp(dateTime, nowDateTime)
    }

    return trimmed
}

private fun parseChatTimestamp(raw: String, zoneId: ZoneId): ZonedDateTime? {
    runCatching { Instant.parse(raw) }.getOrNull()?.let { instant ->
        return ZonedDateTime.ofInstant(instant, zoneId)
    }
    val normalized = raw
        .removeSuffix("Z")
        .replace('T', ' ')
        .substringBefore('.')
    if (normalized.matches(Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}"""))) {
        return runCatching {
            LocalDateTime.parse(normalized, localDateTimeParser).atZone(zoneId)
        }.getOrNull()
    }
    return try {
        LocalDateTime.parse(raw.substringBefore('.'), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .atZone(zoneId)
    } catch (_: DateTimeParseException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}

private fun formatRelativeChatTimestamp(dateTime: ZonedDateTime, now: ZonedDateTime): String {
    val deltaSeconds = Duration.between(dateTime.toInstant(), now.toInstant()).seconds
    if (deltaSeconds < 45) {
        return if (isChineseLocale()) "刚刚" else "Just now"
    }
    if (deltaSeconds < 3600) {
        val minutes = maxOf(1, deltaSeconds / 60)
        return if (isChineseLocale()) "${minutes}分钟前" else "${minutes} min ago"
    }

    return when {
        dateTime.toLocalDate() == now.toLocalDate() -> dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
        dateTime.year == now.year -> dateTime.format(monthDayFormatter())
        else -> dateTime.format(yearMonthDayFormatter())
    }
}

private fun monthDayFormatter(): DateTimeFormatter {
    return if (isChineseLocale()) {
        DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.getDefault())
    } else {
        DateTimeFormatter.ofPattern("MMM d HH:mm", Locale.getDefault())
    }
}

private fun yearMonthDayFormatter(): DateTimeFormatter {
    return if (isChineseLocale()) {
        DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.getDefault())
    } else {
        DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm", Locale.getDefault())
    }
}

private fun isChineseLocale(): Boolean {
    return Locale.getDefault().language.equals(Locale.CHINESE.language, ignoreCase = true)
}
