package com.rethinkingstudio.clawlink.ui.screens.chat.components

import org.apache.poi.hslf.usermodel.HSLFSlideShow
import org.apache.poi.hssf.extractor.ExcelExtractor
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.extractor.WordExtractor
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFTextShape
import org.apache.poi.xwpf.usermodel.BodyElementType
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFTable
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
import com.rethinkingstudio.clawlink.core.state.chat.RemoteAttachmentCache
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max

internal class RemoteAttachmentExpiredException : java.io.IOException(
    choose("The file has been cleaned from the server", "文件已被服务器清理")
)

internal fun isRemoteAttachmentExpiredResponse(statusCode: Int, errorBody: String): Boolean {
    return statusCode == HttpURLConnection.HTTP_GONE || errorBody.contains("file_expired", ignoreCase = true)
}

internal fun downloadDocumentToCache(
    url: String,
    accessToken: String,
    cacheKey: String,
    fileName: String?
): File? {
    RemoteAttachmentCache.localOriginal(cacheKey)?.let { return it }
    RemoteAttachmentCache.cachedFile(cacheKey)?.let { cached ->
        return RemoteAttachmentCache.persistLocalOriginal(cacheKey, fileName ?: cached.name, cached) ?: cached
    }
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
        if (accessToken.isNotBlank()) setRequestProperty("Authorization", "Bearer $accessToken")
        connectTimeout = 15_000
        readTimeout = 30_000
        connect()
    }
    val responseCode = conn.responseCode
    if (responseCode !in 200..299) {
        val errorBody = runCatching {
            conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        }.getOrDefault("")
        if (isRemoteAttachmentExpiredResponse(responseCode, errorBody)) {
            RemoteAttachmentCache.markServerExpired(cacheKey)
            throw RemoteAttachmentExpiredException()
        }
        return null
    }
    val bytes = conn.inputStream.use { input -> input.readBytes() }
    // 用户成功下载的文件按消息提供的稳定 fileId/attachmentId 落为本地原件，
    // 后续进程启动不再依赖可能已经过期的远端 URL。
    return RemoteAttachmentCache.persistDownloadedOriginal(cacheKey, fileName, bytes)
}

internal fun loadPlainTextPreview(file: File): String {
    val data = file.readBytes()
    val encodings = listOf(
        Charsets.UTF_8,
        Charsets.UTF_16,
        Charsets.UTF_16LE,
        Charsets.UTF_16BE
    )
    encodings.forEach { encoding ->
        runCatching { String(data, encoding) }.getOrNull()?.let { return it }
    }
    return String(data, Charsets.UTF_8)
}

internal fun loadDocxMarkdownPreview(file: File): String {
    FileInputStream(file).use { input ->
        XWPFDocument(input).use { document ->
            val markdownBlocks = mutableListOf<String>()
            document.bodyElements.forEach { bodyElement ->
                when (bodyElement.elementType) {
                    BodyElementType.PARAGRAPH -> {
                        (bodyElement as XWPFParagraph).toMarkdownLine(document)?.let { markdownBlocks += it }
                    }
                    BodyElementType.TABLE -> {
                        (bodyElement as XWPFTable).toMarkdownTable()?.let { markdownBlocks += it }
                    }
                    else -> Unit
                }
            }
            return markdownBlocks.joinToString("\n\n").trim().ifBlank {
                choose("This Word document has no extractable text content.", "这个 Word 文档没有可提取的文本内容。")
            }
        }
    }
}

internal fun loadDocMarkdownPreview(file: File): String {
    FileInputStream(file).use { input ->
        HWPFDocument(input).use { document ->
            return WordExtractor(document).use { extractor ->
                val paragraphs = extractor.paragraphText
                    .map { it.replace("\u0007", "").trim() }
                    .filter { it.isNotBlank() }
                paragraphs.joinToString("\n\n").trim().ifBlank {
                    choose("This Word 97-2003 document has no extractable text content.", "这个 Word 97-2003 文档没有可提取的文本内容。")
                }
            }
        }
    }
}

internal fun loadSpreadsheetMarkdownPreview(file: File): String {
    FileInputStream(file).use { input ->
        WorkbookFactory.create(input).use { workbook ->
            val formatter = DataFormatter()
            val blocks = mutableListOf<String>()
            workbook.forEach { sheet ->
                blocks += "## ${sheet.sheetName}"
                val rows = mutableListOf<List<String>>()
                sheet.rowIterator().forEach { row ->
                    val cells = (0 until row.lastCellNum.coerceAtLeast(0).toInt()).map { index ->
                        formatter.formatCellValue(row.getCell(index)).trim()
                    }
                    if (cells.any { it.isNotBlank() }) {
                        rows += cells.map { it.ifBlank { " " }.toMarkdownInline() }
                    }
                }
                if (rows.isNotEmpty()) {
                    val columnCount = rows.maxOf { it.size }
                    val normalizedRows = rows.map { row -> row + List(columnCount - row.size) { " " } }
                    val header = normalizedRows.first()
                    val divider = List(columnCount) { "---" }
                    val body = normalizedRows.drop(1)
                    blocks += buildString {
                        append("| ").append(header.joinToString(" | ")).append(" |\n")
                        append("| ").append(divider.joinToString(" | ")).append(" |\n")
                        body.forEach { row ->
                            append("| ").append(row.joinToString(" | ")).append(" |\n")
                        }
                    }.trim()
                } else {
                    blocks += choose("This spreadsheet has no extractable text content.", "这个表格没有可提取的文本内容。")
                }
            }
            return blocks.joinToString("\n\n").trim().ifBlank { choose("This spreadsheet has no extractable text content.", "这个表格没有可提取的文本内容。") }
        }
    }
}

internal fun loadLegacySpreadsheetMarkdownPreview(file: File): String {
    FileInputStream(file).use { input ->
        HSSFWorkbook(input).use { workbook ->
            val extractor = ExcelExtractor(workbook)
            return extractor.use {
                val text = it.getText().trim()
                if (text.isNotBlank()) {
                    text.lines().map { line -> line.trim() }.filter { it.isNotBlank() }.joinToString("\n")
                } else {
                    choose("This Excel 97-2003 document has no extractable text content.", "这个 Excel 97-2003 文档没有可提取的文本内容。")
                }
            }
        }
    }
}

internal fun loadPresentationMarkdownPreview(file: File): String {
    FileInputStream(file).use { input ->
        XMLSlideShow(input).use { slideshow ->
            val blocks = mutableListOf<String>()
            slideshow.slides.forEachIndexed { index, slide ->
                val texts = slide.shapes.filterIsInstance<XSLFTextShape>().flatMap { shape ->
                    val raw = runCatching { shape.text }.getOrNull().orEmpty()
                    raw.lines().map { it.trim() }.filter { it.isNotBlank() }
                }
                blocks += "## Slide ${index + 1}"
                if (texts.isNotEmpty()) {
                    blocks += texts.joinToString("\n") { "- $it" }
                } else {
                    blocks += choose("This slide has no extractable text content.", "这一页没有可提取的文本内容。")
                }
            }
            return blocks.joinToString("\n\n").trim().ifBlank { choose("This presentation has no extractable text content.", "这个演示文稿没有可提取的文本内容。") }
        }
    }
}

internal fun loadLegacyPresentationMarkdownPreview(file: File): String {
    FileInputStream(file).use { input ->
        HSLFSlideShow(input).use { slideshow ->
            val blocks = mutableListOf<String>()
            slideshow.slides.forEachIndexed { index, slide ->
                val texts = slide.textParagraphs.flatMap { paragraphList ->
                    paragraphList.flatMap { paragraph ->
                        paragraph.textRuns.mapNotNull { run ->
                            run.rawText?.replace("\r", " ")?.replace("\n", " ")?.trim()?.takeIf { it.isNotBlank() }
                        }
                    }
                }
                blocks += "## Slide ${index + 1}"
                if (texts.isNotEmpty()) {
                    blocks += texts.joinToString("\n") { "- $it" }
                } else {
                    blocks += choose("This slide has no extractable text content.", "这一页没有可提取的文本内容。")
                }
            }
            return blocks.joinToString("\n\n").trim().ifBlank { choose("This PowerPoint 97-2003 document has no extractable text content.", "这个 PowerPoint 97-2003 文档没有可提取的文本内容。") }
        }
    }
}

private fun XWPFParagraph.toMarkdownLine(document: XWPFDocument): String? {
    val rawText = buildString {
        runs.forEachIndexed { index, run ->
            val runText = run.text()?.replace("\r", " ")?.replace("\n", " ")?.trim().orEmpty()
            if (runText.isBlank()) return@forEachIndexed
            if (index > 0 && isNotEmpty() && last() != ' ') append(' ')
            append(runText.toMarkdownInline())
        }
    }.trim()
    if (rawText.isBlank()) return null

    val styleName = styleName(document).lowercase()
    val headingLevel = when {
        styleName.startsWith("heading 1") || styleName == "title" -> 1
        styleName.startsWith("heading 2") || styleName == "subtitle" -> 2
        styleName.startsWith("heading 3") -> 3
        styleName.startsWith("heading 4") -> 4
        styleName.startsWith("heading 5") -> 5
        styleName.startsWith("heading 6") -> 6
        else -> 0
    }
    if (headingLevel > 0) return "${"#".repeat(headingLevel)} $rawText"

    val isBulleted = styleName.contains("bullet") || styleName.contains("list")
    return if (isBulleted) "- $rawText" else rawText
}

private fun XWPFParagraph.styleName(document: XWPFDocument): String {
    val styleId = styleID?.trim().orEmpty()
    if (styleId.isBlank()) return ""
    return runCatching {
        document.styles?.getStyle(styleId)?.name ?: styleId
    }.getOrDefault(styleId)
}

private fun String.toMarkdownInline(): String {
    return buildString {
        for (char in this@toMarkdownInline) {
            when (char) {
                '\\', '*', '_', '[', ']', '(', ')', '#', '+', '-', '!', '`', '|' -> {
                    append('\\')
                    append(char)
                }
                else -> append(char)
            }
        }
    }
}

private fun XWPFTable.toMarkdownTable(): String? {
    val rows = rows.map { row ->
        row.tableCells.map { cell ->
            cell.text.replace("\r", " ").replace("\n", " ").trim().ifBlank { " " }.toMarkdownInline()
        }
    }
    if (rows.isEmpty()) return null
    val columnCount = rows.maxOf { it.size }
    val normalizedRows = rows.map { row -> row + List(columnCount - row.size) { " " } }
    val header = normalizedRows.first()
    val divider = List(columnCount) { "---" }
    val body = normalizedRows.drop(1)
    val markdown = buildString {
        append("| ").append(header.joinToString(" | ")).append(" |\n")
        append("| ").append(divider.joinToString(" | ")).append(" |\n")
        body.forEach { row ->
            append("| ").append(row.joinToString(" | ")).append(" |\n")
        }
    }.trim()
    return markdown.ifBlank { null }
}
