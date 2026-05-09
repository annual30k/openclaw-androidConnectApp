package com.rethinkingstudio.clawlink.ui.screens.chat.components

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.BorderStroke
import io.noties.markwon.Markwon

internal fun parseMarkdownBlocks(raw: String): List<AndroidMarkdownBlock> {
    val decoded = raw.decodeEscapedMarkdownText()
    val statusSummary = decoded.normalizeOpenClawStatusSummary()
    if (statusSummary != decoded) {
        return listOf(AndroidMarkdownBlock.CompactLines(statusSummary.lines().filter { it.isNotBlank() }))
    }

    val normalized = decoded
        .normalizeMarkdownBlockBoundaries()
        .replace("\r\n", "\n")
        .replace("\r", "\n")
    val lines = normalized.lines()
    val blocks = mutableListOf<AndroidMarkdownBlock>()
    val paragraphBuffer = mutableListOf<String>()
    var index = 0

    fun flushParagraph() {
        val text = paragraphBuffer.joinToString("\n").trim()
        if (text.isNotEmpty() && !text.all { it == '#' }) {
            blocks.add(AndroidMarkdownBlock.Paragraph(text))
        }
        paragraphBuffer.clear()
    }

    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trim()

        if (trimmed.isEmpty()) {
            flushParagraph()
            index += 1
            continue
        }

        if (trimmed.startsWith("```")) {
            flushParagraph()
            val language = trimmed.removePrefix("```").trim().ifEmpty { null }
            val codeLines = mutableListOf<String>()
            index += 1
            while (index < lines.size) {
                val codeLine = lines[index]
                if (codeLine.trim().startsWith("```")) {
                    index += 1
                    break
                }
                codeLines.add(codeLine)
                index += 1
            }
            blocks.add(AndroidMarkdownBlock.Code(language, codeLines.joinToString("\n")))
            continue
        }

        if (isMarkdownTableHeader(index, lines)) {
            flushParagraph()
            val tableLines = mutableListOf(lines[index], lines[index + 1])
            index += 2
            while (index < lines.size && splitMarkdownTableRow(lines[index]).size >= 2) {
                tableLines.add(lines[index])
                index += 1
            }
            parseMarkdownTable(tableLines)?.let { blocks.add(AndroidMarkdownBlock.Table(it)) }
                ?: blocks.add(AndroidMarkdownBlock.Paragraph(tableLines.joinToString("\n")))
            continue
        }

        val heading = parseMarkdownHeading(trimmed)
        if (heading != null) {
            flushParagraph()
            blocks.add(AndroidMarkdownBlock.Heading(heading.first, heading.second))
            index += 1
            continue
        }

        if (trimmed.isMarkdownThematicBreak()) {
            flushParagraph()
            blocks.add(AndroidMarkdownBlock.ThematicBreak)
            index += 1
            continue
        }

        val unorderedList = parseMarkdownUnorderedList(index, lines)
        if (unorderedList != null) {
            flushParagraph()
            blocks.add(AndroidMarkdownBlock.UnorderedList(unorderedList.first))
            index = unorderedList.second
            continue
        }

        val orderedList = parseMarkdownOrderedList(index, lines)
        if (orderedList != null) {
            flushParagraph()
            blocks.add(AndroidMarkdownBlock.OrderedList(orderedList.first))
            index = orderedList.second
            continue
        }

        val blockquote = parseMarkdownBlockquote(index, lines)
        if (blockquote != null) {
            flushParagraph()
            blocks.add(AndroidMarkdownBlock.Blockquote(blockquote.first))
            index = blockquote.second
            continue
        }

        paragraphBuffer.add(line)
        index += 1
    }

    flushParagraph()
    return blocks.ifEmpty { listOf(AndroidMarkdownBlock.Paragraph(normalized)) }
}

internal fun parseMarkdownHeading(trimmed: String): Pair<Int, String>? {
    val match = Regex("""^(#{1,6})\s+(.+)$""").find(trimmed) ?: return null
    return match.groupValues[1].length to match.groupValues[2]
}

internal fun parseMarkdownUnorderedList(start: Int, lines: List<String>): Pair<List<String>, Int>? {
    val first = unorderedMarkdownItem(lines[start]) ?: return null
    val items = mutableListOf(first)
    var index = start + 1
    while (index < lines.size) {
        val trimmed = lines[index].trim()
        if (trimmed.isEmpty() || trimmed.startsWith("```") || parseMarkdownHeading(trimmed) != null || trimmed.isMarkdownThematicBreak() || isMarkdownTableHeader(index, lines)) break
        val next = unorderedMarkdownItem(lines[index])
        if (next != null) {
            items.add(next)
        } else {
            items[items.lastIndex] = items.last() + "\n" + trimmed
        }
        index += 1
    }
    return items to index
}

internal fun parseMarkdownOrderedList(start: Int, lines: List<String>): Pair<List<AndroidMarkdownOrderedListItem>, Int>? {
    val first = orderedMarkdownItem(lines[start]) ?: return null
    val items = mutableListOf(first)
    var index = start + 1
    while (index < lines.size) {
        val trimmed = lines[index].trim()
        if (trimmed.isEmpty() || trimmed.startsWith("```") || parseMarkdownHeading(trimmed) != null || trimmed.isMarkdownThematicBreak() || isMarkdownTableHeader(index, lines)) break
        val next = orderedMarkdownItem(lines[index])
        if (next != null) {
            items.add(next)
        } else {
            val last = items.last()
            items[items.lastIndex] = last.copy(text = last.text + "\n" + trimmed)
        }
        index += 1
    }
    return items to index
}

internal fun parseMarkdownBlockquote(start: Int, lines: List<String>): Pair<String, Int>? {
    val first = blockquoteMarkdownLine(lines[start]) ?: return null
    val collected = mutableListOf(first)
    var index = start + 1
    while (index < lines.size) {
        val next = blockquoteMarkdownLine(lines[index]) ?: break
        collected.add(next)
        index += 1
    }
    return collected.joinToString("\n") to index
}

internal fun unorderedMarkdownItem(line: String): String? {
    return Regex("""^\s{0,3}[-*+]\s+(.+)$""").find(line)?.groupValues?.get(1)
}

internal fun orderedMarkdownItem(line: String): AndroidMarkdownOrderedListItem? {
    val match = Regex("""^\s{0,3}(\d+)\.\s+(.+)$""").find(line) ?: return null
    return AndroidMarkdownOrderedListItem(match.groupValues[1].toIntOrNull() ?: 1, match.groupValues[2])
}

internal fun blockquoteMarkdownLine(line: String): String? {
    return Regex("""^\s{0,3}>\s?(.*)$""").find(line)?.groupValues?.get(1)
}

internal fun String.isMarkdownThematicBreak(): Boolean {
    val compact = trim().filterNot { it.isWhitespace() }
    return compact.length >= 3 && compact.all { it == compact.first() } && compact.first() in listOf('-', '*', '_')
}

internal fun String.normalizeOpenClawStatusSummary(): String {
    val flattened = replace("\r\n", "\n")
        .replace("\r", "\n")
        .replace('\n', ' ')
        .replace(Regex("""\s+"""), " ")
        .trim()
    if (!flattened.looksLikeOpenClawStatusSummary()) return this

    val markerPattern = Regex(
        """(?=(?:🦞\s*)?OpenClaw\s|🧠\s*Model:|🧮\s*Tokens:|🗄️?\s*Cache:|📚\s*Context:|📊\s*Usage:|🧵\s*Session:|⚙️?\s*Execution:|🧩\s*Queue:)"""
    )
    val starts = markerPattern.findAll(flattened)
        .map { it.range.first }
        .distinct()
        .sorted()
        .toList()
    if (starts.size < 3) return this

    val items = starts.mapIndexedNotNull { index, start ->
        val end = starts.getOrNull(index + 1) ?: flattened.length
        flattened.substring(start, end)
            .trim()
            .trim('·')
            .trim()
            .takeIf { it.isNotBlank() }
    }
    if (items.size < 3) return this

    return items.joinToString("\n")
}

internal fun String.looksLikeOpenClawStatusSummary(): Boolean {
    if (!contains("OpenClaw", ignoreCase = true)) return false
    val signals = listOf("Tokens:", "Context:", "Runtime:", "Session:", "Queue:", "Compactions:", "Usage:")
    return signals.count { contains(it, ignoreCase = true) } >= 3
}

internal fun isMarkdownTableHeader(index: Int, lines: List<String>): Boolean {
    if (index + 1 >= lines.size) return false
    val headers = splitMarkdownTableRow(lines[index])
    val separators = splitMarkdownTableRow(lines[index + 1])
    return headers.size >= 2 && separators.size == headers.size && separators.all { it.isMarkdownTableSeparatorCell() }
}

internal fun parseMarkdownTable(lines: List<String>): AndroidMarkdownTable? {
    val rows = lines.map { splitMarkdownTableRow(it) }.filter { it.isNotEmpty() }
    if (rows.size < 2 || !rows[1].all { it.isMarkdownTableSeparatorCell() }) return null
    return AndroidMarkdownTable(
        headers = rows[0].map { it.stripInlineMarkdownForTable() },
        rows = rows.drop(2).map { row -> row.map { it.stripInlineMarkdownForTable() } }
    )
}

internal fun splitMarkdownTableRow(line: String): List<String> {
    var content = line.trim()
    if (content.isEmpty()) return emptyList()
    if (content.first() == '|') content = content.drop(1)
    if (content.lastOrNull() == '|') content = content.dropLast(1)

    val cells = mutableListOf<String>()
    val current = StringBuilder()
    var index = 0
    var inCodeSpan = false
    while (index < content.length) {
        val ch = content[index]
        if (ch == '\\' && index + 1 < content.length && content[index + 1] in listOf('|', '\\', '`')) {
            current.append(content[index + 1])
            index += 2
            continue
        }
        if (ch == '`') inCodeSpan = !inCodeSpan
        if (ch == '|' && !inCodeSpan) {
            cells.add(current.toString().trim())
            current.clear()
        } else {
            current.append(ch)
        }
        index += 1
    }
    cells.add(current.toString().trim())
    return cells
}

internal fun String.isMarkdownTableSeparatorCell(): Boolean {
    val compact = trim().removePrefix(":").removeSuffix(":")
    return compact.length >= 3 && compact.all { it == '-' }
}

internal fun String.stripInlineMarkdownForTable(): String {
    return decodeEscapedMarkdownText()
        .replace(Regex("""`([^`]+)`"""), "$1")
        .replace(Regex("""\*\*([^*]+)\*\*"""), "$1")
        .replace(Regex("""__([^_]+)__"""), "$1")
        .replace(Regex("""\[(.+?)]\([^)]+\)"""), "$1")
        .trim()
}

internal fun String.normalizeMarkdownBlockBoundaries(): String {
    val codeRegex = Regex("""```[\s\S]*?```""")
    val matches = codeRegex.findAll(this).toList()
    if (matches.isEmpty()) return normalizeMarkdownOutsideCode()

    val output = StringBuilder()
    var cursor = 0
    matches.forEach { match ->
        if (match.range.first > cursor) {
            output.append(substring(cursor, match.range.first).normalizeMarkdownOutsideCode())
        }
        output.append(match.value)
        cursor = match.range.last + 1
    }
    if (cursor < length) {
        output.append(substring(cursor).normalizeMarkdownOutsideCode())
    }
    return output.toString()
}

internal fun String.normalizeMarkdownOutsideCode(): String {
    return replace(Regex("""([^\n])(```)"""), "$1\n$2")
        .replace(Regex("""([^\n])(#{1,6}\s)"""), "$1\n$2")
        .replace(Regex("""([：:。！？.!?])\s*(\d+\.\s)"""), "$1\n$2")
        .replace(Regex("""([：:。！？.!?])\s*([-*+]\s)"""), "$1\n$2")
        .replace(Regex("""([：:。！？.!?])\s*(>\s)"""), "$1\n$2")
}
