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

