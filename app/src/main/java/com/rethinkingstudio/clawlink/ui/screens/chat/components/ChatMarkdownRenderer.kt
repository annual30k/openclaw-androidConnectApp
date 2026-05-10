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
    val borderColor = if (onDarkBackground) Color.White.copy(alpha = 0.10f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
    val headerColor = if (onDarkBackground) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f)
    val bodyColor = if (onDarkBackground) Color.White.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f)
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
    val borderColor = if (onDarkBackground) Color.White.copy(alpha = 0.10f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
    val headerBackground = if (onDarkBackground) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f)
    val bodyBackground = if (onDarkBackground) Color.White.copy(alpha = 0.04f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
    val headerTextColor = if (onDarkBackground) textColor.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant
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
