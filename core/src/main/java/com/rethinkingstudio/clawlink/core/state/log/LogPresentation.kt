package com.rethinkingstudio.clawlink.core.state.log

enum class LogSeverity {
    Error,
    Warning,
    Info,
    Debug,
    Unknown
}

data class ParsedLogLine(
    val rawText: String,
    val displayText: String,
    val timestampText: String?,
    val sourceText: String?,
    val severity: LogSeverity
) {
    val searchText: String = listOfNotNull(
        rawText,
        displayText,
        timestampText,
        sourceText,
        severity.name
    ).joinToString(" ").lowercase()
}

fun parseLogLine(rawLine: String): ParsedLogLine {
    val stripped = stripAnsi(rawLine).trim()
    if (stripped.isEmpty()) {
        return ParsedLogLine(
            rawText = rawLine,
            displayText = " ",
            timestampText = null,
            sourceText = null,
            severity = LogSeverity.Unknown
        )
    }

    val timestampResult = extractTimestamp(stripped)
    var remainder = timestampResult.remainder ?: stripped
    var sourceText: String? = null

    extractBracketPrefix(remainder)?.let { sourceResult ->
        sourceText = sourceResult.label
        remainder = sourceResult.remainder
    }

    val displayText = remainder.trim().ifEmpty { stripped }
    return ParsedLogLine(
        rawText = stripped,
        displayText = displayText,
        timestampText = timestampResult.timestamp,
        sourceText = sourceText,
        severity = classifyLogSeverity(stripped)
    )
}

private data class TimestampResult(val timestamp: String?, val remainder: String?)

private data class SourceResult(val label: String, val remainder: String)

private fun stripAnsi(value: String): String =
    value.replace(Regex("\u001B\\[[0-9;]*[A-Za-z]"), "")

private fun extractTimestamp(value: String): TimestampResult {
    val patterns = listOf(
        Regex("""^(\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2}(?:[.,]\d{3})?(?:Z|[+-]\d{2}:?\d{2})?)(?:\s+[-–—]?\s*|\s+)?(.*)$"""),
        Regex("""^(\d{2}:\d{2}:\d{2}(?:[.,]\d{3})?)(?:\s+[-–—]?\s*|\s+)?(.*)$""")
    )

    for (pattern in patterns) {
        val match = pattern.find(value) ?: continue
        return TimestampResult(
            timestamp = match.groupValues.getOrNull(1)?.trim(),
            remainder = match.groupValues.getOrNull(2)?.trim()
        )
    }

    return TimestampResult(timestamp = null, remainder = null)
}

private fun extractBracketPrefix(value: String): SourceResult? {
    val match = Regex("""^\[([^\]]+)]\s*(.*)$""").find(value) ?: return null
    val label = match.groupValues.getOrNull(1)?.trim().orEmpty()
    if (label.isEmpty()) return null
    return SourceResult(
        label = label,
        remainder = match.groupValues.getOrNull(2)?.trim().orEmpty()
    )
}

private fun classifyLogSeverity(value: String): LogSeverity {
    val lowercased = value.lowercase()
    if (lowercased.contains("fatal")
        || lowercased.contains("panic")
        || lowercased.contains("exception")
        || lowercased.contains("unhandled")
        || lowercased.contains("失败")
        || lowercased.contains(Regex("""\berror\b"""))
        || matchesFailureKeyword(lowercased)
    ) {
        return LogSeverity.Error
    }

    if (lowercased.contains("warn")
        || lowercased.contains("retry")
        || lowercased.contains("timeout")
        || lowercased.contains("deprecated")
        || lowercased.contains("limit")
        || lowercased.contains("warning")
        || lowercased.contains("警告")
    ) {
        return LogSeverity.Warning
    }

    if (lowercased.contains("debug") || lowercased.contains("trace")) {
        return LogSeverity.Debug
    }

    if (lowercased.contains("info")
        || lowercased.contains("started")
        || lowercased.contains("connected")
        || lowercased.contains("completed")
        || lowercased.contains("loaded")
        || lowercased.contains("ready")
        || lowercased.contains("success")
    ) {
        return LogSeverity.Info
    }

    return LogSeverity.Unknown
}

private fun matchesFailureKeyword(value: String): Boolean {
    if (value.contains(Regex("""\bno\s+failed\b"""))
        || value.contains(Regex("""\bfailed\s*(?:count|jobs|tasks|items|requests|errors)?\s*[:=]?\s*0\b"""))
        || value.contains(Regex("""\b0\s+failed\b"""))
    ) {
        return false
    }

    return value.contains(Regex("""\bfailed\b"""))
        || value.contains(Regex("""\bfail:"""))
}
