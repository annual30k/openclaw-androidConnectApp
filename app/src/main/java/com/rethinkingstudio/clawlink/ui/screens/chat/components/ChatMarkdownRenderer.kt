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

@Composable
internal fun MarkdownMessageText(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color,
    linkColor: Color,
    textSizeSp: Float,
    onDarkBackground: Boolean
) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }
    SelectionContainer {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            blocks.forEach { block ->
                when (block) {
                    is AndroidMarkdownBlock.Paragraph -> MarkdownInlineText(
                        text = block.text,
                        textColor = textColor,
                        linkColor = linkColor,
                        textSizeSp = textSizeSp,
                        lineSpacingMultiplier = 1.08f
                    )
                    is AndroidMarkdownBlock.Heading -> MarkdownInlineText(
                        text = block.text,
                        textColor = textColor,
                        linkColor = linkColor,
                        textSizeSp = when (block.level) {
                            1 -> 22f
                            2 -> 19f
                            3 -> 17f
                            else -> 15f
                        },
                        bold = true
                    )
                    is AndroidMarkdownBlock.UnorderedList -> MarkdownUnorderedList(block.items, textColor, linkColor, textSizeSp)
                    is AndroidMarkdownBlock.OrderedList -> MarkdownOrderedList(block.items, textColor, linkColor, textSizeSp)
                    is AndroidMarkdownBlock.Blockquote -> MarkdownBlockquote(block.text, textColor, linkColor, textSizeSp)
                    is AndroidMarkdownBlock.CompactLines -> MarkdownCompactLines(block.lines, textColor, linkColor, textSizeSp)
                    AndroidMarkdownBlock.ThematicBreak -> MarkdownThematicBreak(textColor)
                    is AndroidMarkdownBlock.Code -> MarkdownCodeBlock(block.code, block.language, textColor, onDarkBackground)
                    is AndroidMarkdownBlock.Table -> MarkdownTable(block.table, textColor, onDarkBackground)
                }
            }
        }
    }
}

@Composable
private fun MarkdownCompactLines(lines: List<String>, textColor: Color, linkColor: Color, textSizeSp: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        lines.forEach { line ->
            MarkdownInlineText(
                text = line,
                textColor = textColor,
                linkColor = linkColor,
                textSizeSp = textSizeSp,
                lineSpacingMultiplier = 1.02f
            )
        }
    }
}

@Composable
private fun MarkdownInlineText(
    text: String,
    textColor: Color,
    linkColor: Color,
    textSizeSp: Float,
    bold: Boolean = false,
    lineSpacingMultiplier: Float = 1.0f
) {
    val context = LocalContext.current
    val markwon = remember(context) { Markwon.create(context) }
    AndroidView(
        factory = {
            TextView(it).apply {
                movementMethod = LinkMovementMethod.getInstance()
                includeFontPadding = false
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        update = { view ->
            view.setTextColor(textColor.toArgb())
            view.setLinkTextColor(linkColor.toArgb())
            view.textSize = textSizeSp
            view.setTypeface(null, if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            view.setLineSpacing(0f, lineSpacingMultiplier)
            markwon.setMarkdown(view, text.decodeEscapedMarkdownText())
        }
    )
}

@Composable
private fun MarkdownUnorderedList(items: List<String>, textColor: Color, linkColor: Color, textSizeSp: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Text("•", color = textColor, fontWeight = FontWeight.Bold, fontSize = (textSizeSp + 1).sp)
                MarkdownInlineText(item, textColor, linkColor, textSizeSp, lineSpacingMultiplier = 1.06f)
            }
        }
    }
}

@Composable
private fun MarkdownOrderedList(items: List<AndroidMarkdownOrderedListItem>, textColor: Color, linkColor: Color, textSizeSp: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Text(
                    "${item.number}.",
                    color = textColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = textSizeSp.sp,
                    modifier = Modifier.width(24.dp)
                )
                MarkdownInlineText(item.text, textColor, linkColor, textSizeSp, lineSpacingMultiplier = 1.06f)
            }
        }
    }
}

@Composable
private fun MarkdownBlockquote(text: String, textColor: Color, linkColor: Color, textSizeSp: Float) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .heightIn(min = 20.dp)
                .background(textColor.copy(alpha = 0.28f), RoundedCornerShape(2.dp))
        )
        MarkdownInlineText(text, textColor, linkColor, textSizeSp, lineSpacingMultiplier = 1.06f)
    }
}

@Composable
private fun MarkdownThematicBreak(textColor: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .height(1.dp)
            .background(textColor.copy(alpha = 0.22f))
    )
}

@Composable
private fun MarkdownCodeBlock(code: String, language: String?, textColor: Color, onDarkBackground: Boolean) {
    val borderColor = if (onDarkBackground) Color.White.copy(alpha = 0.22f) else Color(0xFFE1E4EA)
    val headerColor = if (onDarkBackground) Color.White.copy(alpha = 0.12f) else Color(0xFFF2F4F8)
    val bodyColor = if (onDarkBackground) Color.White.copy(alpha = 0.08f) else Color(0xFFF8FAFC)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bodyColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerColor)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    language?.normalizedCodeLanguage()?.uppercase() ?: "CODE",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = textColor.copy(alpha = 0.72f),
                    fontWeight = FontWeight.Bold
                )
            }
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    code,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = textColor
                )
            }
        }
    }
}

@Composable
private fun MarkdownTable(table: AndroidMarkdownTable, textColor: Color, onDarkBackground: Boolean) {
    val borderColor = if (onDarkBackground) Color.White.copy(alpha = 0.22f) else Color(0xFFE1E4EA)
    val headerBackground = if (onDarkBackground) Color.White.copy(alpha = 0.14f) else Color(0xFFF4F6FA)
    val bodyBackground = if (onDarkBackground) Color.White.copy(alpha = 0.06f) else Color.White
    val headerTextColor = if (onDarkBackground) textColor.copy(alpha = 0.9f) else Color(0xFF7A7E87)
    val columnWidths = remember(table) { table.columnWidths() }
    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = bodyBackground,
            border = BorderStroke(1.dp, borderColor)
        ) {
            Column {
                MarkdownTableRow(table.headers, columnWidths, headerTextColor, headerBackground, borderColor, FontWeight.SemiBold)
                table.rows.forEach { row ->
                    MarkdownTableRow(row, columnWidths, textColor, bodyBackground, borderColor, FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun MarkdownTableRow(
    values: List<String>,
    columnWidths: List<Dp>,
    textColor: Color,
    background: Color,
    borderColor: Color,
    fontWeight: FontWeight
) {
    Row {
        columnWidths.forEachIndexed { index, width ->
            Box(
                modifier = Modifier
                    .width(width)
                    .background(background)
            ) {
                Text(
                    values.getOrNull(index).orEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    color = textColor,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = fontWeight,
                    lineHeight = 18.sp,
                    softWrap = false
                )
                Canvas(modifier = Modifier.matchParentSize()) {
                    val stroke = 0.8.dp.toPx()
                    drawLine(borderColor, start = Offset(0f, size.height), end = Offset(size.width, size.height), strokeWidth = stroke)
                    drawLine(borderColor, start = Offset(size.width, 0f), end = Offset(size.width, size.height), strokeWidth = stroke)
                }
            }
        }
    }
}

internal sealed class AndroidMarkdownBlock {
    data class Paragraph(val text: String) : AndroidMarkdownBlock()
    data class Heading(val level: Int, val text: String) : AndroidMarkdownBlock()
    data class UnorderedList(val items: List<String>) : AndroidMarkdownBlock()
    data class OrderedList(val items: List<AndroidMarkdownOrderedListItem>) : AndroidMarkdownBlock()
    data class Blockquote(val text: String) : AndroidMarkdownBlock()
    data class CompactLines(val lines: List<String>) : AndroidMarkdownBlock()
    data object ThematicBreak : AndroidMarkdownBlock()
    data class Code(val language: String?, val code: String) : AndroidMarkdownBlock()
    data class Table(val table: AndroidMarkdownTable) : AndroidMarkdownBlock()
}

internal data class AndroidMarkdownOrderedListItem(val number: Int, val text: String)

internal data class AndroidMarkdownTable(
    val headers: List<String>,
    val rows: List<List<String>>
) {
    val columnCount: Int = maxOf(headers.size, rows.maxOfOrNull { it.size } ?: 0)

    fun cellText(row: Int, column: Int): String {
        val source = if (row == 0) headers else rows.getOrNull(row - 1).orEmpty()
        return source.getOrNull(column).orEmpty()
    }

    fun columnWidths(): List<Dp> {
        return (0 until maxOf(columnCount, 1)).map { column ->
            val maxUnits = (0..rows.size)
                .maxOf { row -> cellText(row, column).tableDisplayUnits() }
                .coerceIn(8.0, 72.0)
            (maxUnits * 11.5 + 44).dp
        }
    }
}

private fun String.tableDisplayUnits(): Double {
    return fold(0.0) { total, char ->
        total + when {
            char.isHighSurrogate() || char.isLowSurrogate() -> 1.4
            char.code in 0xFE00..0xFE0F -> 0.0
            char.isWhitespace() -> 0.45
            char.code <= 0x007F -> 0.72
            char.code in 0xFF61..0xFF9F -> 0.72
            else -> 1.0
        }
    }
}

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

private fun parseMarkdownHeading(trimmed: String): Pair<Int, String>? {
    val match = Regex("""^(#{1,6})\s+(.+)$""").find(trimmed) ?: return null
    return match.groupValues[1].length to match.groupValues[2]
}

private fun parseMarkdownUnorderedList(start: Int, lines: List<String>): Pair<List<String>, Int>? {
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

private fun parseMarkdownOrderedList(start: Int, lines: List<String>): Pair<List<AndroidMarkdownOrderedListItem>, Int>? {
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

private fun parseMarkdownBlockquote(start: Int, lines: List<String>): Pair<String, Int>? {
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

private fun unorderedMarkdownItem(line: String): String? {
    return Regex("""^\s{0,3}[-*+]\s+(.+)$""").find(line)?.groupValues?.get(1)
}

private fun orderedMarkdownItem(line: String): AndroidMarkdownOrderedListItem? {
    val match = Regex("""^\s{0,3}(\d+)\.\s+(.+)$""").find(line) ?: return null
    return AndroidMarkdownOrderedListItem(match.groupValues[1].toIntOrNull() ?: 1, match.groupValues[2])
}

private fun blockquoteMarkdownLine(line: String): String? {
    return Regex("""^\s{0,3}>\s?(.*)$""").find(line)?.groupValues?.get(1)
}

private fun String.isMarkdownThematicBreak(): Boolean {
    val compact = trim().filterNot { it.isWhitespace() }
    return compact.length >= 3 && compact.all { it == compact.first() } && compact.first() in listOf('-', '*', '_')
}

private fun String.normalizeOpenClawStatusSummary(): String {
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

private fun String.looksLikeOpenClawStatusSummary(): Boolean {
    if (!contains("OpenClaw", ignoreCase = true)) return false
    val signals = listOf("Tokens:", "Context:", "Runtime:", "Session:", "Queue:", "Compactions:", "Usage:")
    return signals.count { contains(it, ignoreCase = true) } >= 3
}

private fun isMarkdownTableHeader(index: Int, lines: List<String>): Boolean {
    if (index + 1 >= lines.size) return false
    val headers = splitMarkdownTableRow(lines[index])
    val separators = splitMarkdownTableRow(lines[index + 1])
    return headers.size >= 2 && separators.size == headers.size && separators.all { it.isMarkdownTableSeparatorCell() }
}

private fun parseMarkdownTable(lines: List<String>): AndroidMarkdownTable? {
    val rows = lines.map { splitMarkdownTableRow(it) }.filter { it.isNotEmpty() }
    if (rows.size < 2 || !rows[1].all { it.isMarkdownTableSeparatorCell() }) return null
    return AndroidMarkdownTable(
        headers = rows[0].map { it.stripInlineMarkdownForTable() },
        rows = rows.drop(2).map { row -> row.map { it.stripInlineMarkdownForTable() } }
    )
}

private fun splitMarkdownTableRow(line: String): List<String> {
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

private fun String.isMarkdownTableSeparatorCell(): Boolean {
    val compact = trim().removePrefix(":").removeSuffix(":")
    return compact.length >= 3 && compact.all { it == '-' }
}

private fun String.stripInlineMarkdownForTable(): String {
    return decodeEscapedMarkdownText()
        .replace(Regex("""`([^`]+)`"""), "$1")
        .replace(Regex("""\*\*([^*]+)\*\*"""), "$1")
        .replace(Regex("""__([^_]+)__"""), "$1")
        .replace(Regex("""\[(.+?)]\([^)]+\)"""), "$1")
        .trim()
}

private fun String.normalizeMarkdownBlockBoundaries(): String {
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

private fun String.normalizeMarkdownOutsideCode(): String {
    return replace(Regex("""([^\n])(```)"""), "$1\n$2")
        .replace(Regex("""([^\n])(#{1,6}\s)"""), "$1\n$2")
        .replace(Regex("""([：:。！？.!?])\s*(\d+\.\s)"""), "$1\n$2")
        .replace(Regex("""([：:。！？.!?])\s*([-*+]\s)"""), "$1\n$2")
        .replace(Regex("""([：:。！？.!?])\s*(>\s)"""), "$1\n$2")
}
