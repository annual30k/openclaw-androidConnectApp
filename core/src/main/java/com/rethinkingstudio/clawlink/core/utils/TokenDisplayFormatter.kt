package com.rethinkingstudio.clawlink.core.utils

import kotlin.math.roundToInt

object TokenDisplayFormatter {
    fun formatCount(value: Int): String {
        if (value <= 0) return "0"
        if (value >= 1_000_000) {
            return formatScaled(value, 1_000_000, "m")
        }
        if (value >= 1_000) {
            return formatScaled(value, 1_000, "k")
        }
        return value.toString()
    }

    fun formatUsage(usedTokens: Int?, limitTokens: Int?, fallback: String?): String {
        if (usedTokens != null) {
            val usedLabel = formatCount(usedTokens)
            if (limitTokens != null && limitTokens > 0) {
                val limitLabel = formatCount(limitTokens)
                val percentage = ((usedTokens.toDouble() / limitTokens.toDouble()) * 100).roundToInt().coerceAtMost(999)
                return "$usedLabel/$limitLabel ($percentage%)"
            }
            return usedLabel
        }

        if (limitTokens != null && limitTokens > 0) {
            return "--/${formatCount(limitTokens)}"
        }

        val trimmedFallback = fallback?.trim() ?: ""
        return if (trimmedFallback.isEmpty()) "--" else trimmedFallback
    }

    private fun formatScaled(value: Int, divisor: Int, suffix: String): String {
        val scaled = value.toDouble() / divisor.toDouble()
        val formatted = java.util.Locale.US.let { "%.1f".format(it, scaled) }
        val trimmed = if (formatted.endsWith(".0")) formatted.substring(0, formatted.length - 2) else formatted
        return trimmed + suffix
    }

    fun parseNonNegativeInteger(value: String?): Int? {
        return value?.trim()?.toIntOrNull()?.takeIf { it >= 0 }
    }

    fun parseFormattedCount(value: String?): Int? {
        val trimmed = value?.trim()?.lowercase() ?: return null
        if (trimmed.isEmpty()) return null
        parseNonNegativeInteger(trimmed)?.let { return it }

        val suffix = trimmed.last()
        val multiplier = when (suffix) {
            'k' -> 1_000.0
            'm' -> 1_000_000.0
            else -> return null
        }
        val numericPart = trimmed.dropLast(1).trim()
        val parsed = numericPart.toDoubleOrNull()?.takeIf { it >= 0 } ?: return null
        val scaled = parsed * multiplier
        if (!scaled.isFinite() || scaled > Int.MAX_VALUE) return null
        return scaled.roundToInt()
    }
}
