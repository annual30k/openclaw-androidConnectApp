package com.rethinkingstudio.clawlink.ui.screens.chat.components

import android.content.ClipData
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
import com.rethinkingstudio.clawlink.core.state.chat.RemoteAttachmentCache
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.extractor.WordExtractor
import org.apache.poi.hssf.extractor.ExcelExtractor
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.hslf.usermodel.HSLFSlideShow
import org.apache.poi.xwpf.usermodel.BodyElementType
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFTable
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFTextShape
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

internal enum class AndroidDocumentPreviewKind {
    Text,
    Docx,
    Doc,
    Spreadsheet,
    LegacySpreadsheet,
    Presentation,
    LegacyPresentation,
    Pdf,
    Unsupported
}

internal fun RelayChatContentBlock.documentPreviewKind(): AndroidDocumentPreviewKind {
    val mime = mimeType?.trim()?.lowercase().orEmpty()
    val ext = fileName?.substringAfterLast('.', missingDelimiterValue = "")?.trim()?.lowercase().orEmpty()

    if (ext in setOf("apk", "app", "bat", "bin", "cmd", "com", "dmg", "exe", "ipa", "jar", "msi", "pkg", "ps1", "scr", "vb", "vbe", "vbs")) {
        return AndroidDocumentPreviewKind.Unsupported
    }
    if (
        mime.contains("application/x-msdownload") ||
        mime.contains("application/x-msdos-program") ||
        mime.contains("application/x-executable") ||
        mime.contains("application/x-mach-binary") ||
        mime.contains("application/vnd.android.package-archive") ||
        mime.contains("application/x-apple-diskimage") ||
        mime.contains("application/vnd.apple.installer+xml")
    ) {
        return AndroidDocumentPreviewKind.Unsupported
    }
    if (
        mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
        mime == "application/vnd.ms-word.document.macroenabled.12" ||
        ext in setOf("docx", "docm", "dotx", "dotm")
    ) {
        return AndroidDocumentPreviewKind.Docx
    }
    if (mime == "application/msword" || ext == "doc" || ext == "dot") {
        return AndroidDocumentPreviewKind.Doc
    }
    if (
        mime == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" ||
        mime == "application/vnd.ms-excel.sheet.macroenabled.12" ||
        ext in setOf("xlsx", "xlsm", "xltx", "xltm")
    ) {
        return AndroidDocumentPreviewKind.Spreadsheet
    }
    if (mime == "application/vnd.ms-excel" || ext in setOf("xls", "xlt")) {
        return AndroidDocumentPreviewKind.LegacySpreadsheet
    }
    if (
        mime == "application/vnd.openxmlformats-officedocument.presentationml.presentation" ||
        mime == "application/vnd.ms-powerpoint.presentation.macroenabled.12" ||
        ext in setOf("pptx", "pptm", "potx", "potm")
    ) {
        return AndroidDocumentPreviewKind.Presentation
    }
    if (mime == "application/vnd.ms-powerpoint" || ext in setOf("ppt", "pot")) {
        return AndroidDocumentPreviewKind.LegacyPresentation
    }
    if (mime.contains("pdf") || ext == "pdf") return AndroidDocumentPreviewKind.Pdf
    if (isPlainTextDocument(mime, ext)) return AndroidDocumentPreviewKind.Text
    return AndroidDocumentPreviewKind.Unsupported
}

internal fun isPlainTextDocument(mimeType: String, fileExtension: String): Boolean {
    if (mimeType.startsWith("text/")) return true
    if (
        mimeType in setOf(
            "application/ecmascript",
            "application/javascript",
            "application/json",
            "application/ld+json",
            "application/manifest+json",
            "application/sql",
            "application/xhtml+xml",
            "application/xml",
            "application/x-javascript"
        )
    ) return true
    return fileExtension in setOf(
        "bash", "c", "cfg", "conf", "csv", "css", "env", "go", "h", "htm", "html",
        "ini", "java", "js", "json", "jsonl", "jsx", "kt", "kts", "less", "log",
        "markdown", "md", "mdx", "m", "mm", "php", "pl", "py", "rb", "rs", "scss",
        "sh", "sql", "swift", "ts", "tsx", "txt", "toml", "tsv", "xml", "yaml", "yml", "zsh"
    )
}

@Composable
internal fun DocumentFullscreenOverlay(
    url: String,
    accessToken: String,
    fileName: String?,
    mimeType: String?,
    cacheKey: String? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val resolvedCacheKey = cacheKey ?: url
    val previewKind = remember(fileName, mimeType) {
        RelayChatContentBlock(
            type = "file",
            fileName = fileName,
            mimeType = mimeType
        ).documentPreviewKind()
    }
    var showTopMenu by remember(resolvedCacheKey) { mutableStateOf(false) }
    var isLoading by remember(resolvedCacheKey) { mutableStateOf(true) }
    var didFail by remember(resolvedCacheKey) { mutableStateOf(false) }
    var localFile by remember(resolvedCacheKey) { mutableStateOf(RemoteAttachmentCache.cachedFile(resolvedCacheKey)) }
    var textPreview by remember(resolvedCacheKey) { mutableStateOf<String?>(null) }
    var docxPreview by remember(resolvedCacheKey) { mutableStateOf<String?>(null) }
    var officePreview by remember(resolvedCacheKey) { mutableStateOf<String?>(null) }

    LaunchedEffect(resolvedCacheKey, url, accessToken, fileName) {
        isLoading = true
        didFail = false
        try {
            val downloaded = withContext(Dispatchers.IO) {
                if (localFile?.exists() == true) {
                    localFile
                } else {
                    downloadDocumentToCache(
                        url = url,
                        accessToken = accessToken,
                        cacheKey = resolvedCacheKey,
                        fileName = fileName
                    )
                }
            }
            localFile = downloaded
            if (previewKind == AndroidDocumentPreviewKind.Text) {
                textPreview = downloaded?.let { file ->
                    withContext(Dispatchers.IO) { loadPlainTextPreview(file) }
                }
            } else if (previewKind == AndroidDocumentPreviewKind.Docx) {
                docxPreview = downloaded?.let { file ->
                    withContext(Dispatchers.IO) { loadDocxMarkdownPreview(file) }
                }
            } else if (previewKind == AndroidDocumentPreviewKind.Doc) {
                docxPreview = downloaded?.let { file ->
                    withContext(Dispatchers.IO) { loadDocMarkdownPreview(file) }
                }
            } else if (previewKind == AndroidDocumentPreviewKind.Spreadsheet) {
                officePreview = downloaded?.let { file ->
                    withContext(Dispatchers.IO) { loadSpreadsheetMarkdownPreview(file) }
                }
            } else if (previewKind == AndroidDocumentPreviewKind.LegacySpreadsheet) {
                officePreview = downloaded?.let { file ->
                    withContext(Dispatchers.IO) { loadLegacySpreadsheetMarkdownPreview(file) }
                }
            } else if (previewKind == AndroidDocumentPreviewKind.Presentation) {
                officePreview = downloaded?.let { file ->
                    withContext(Dispatchers.IO) { loadPresentationMarkdownPreview(file) }
                }
            } else if (previewKind == AndroidDocumentPreviewKind.LegacyPresentation) {
                officePreview = downloaded?.let { file ->
                    withContext(Dispatchers.IO) { loadLegacyPresentationMarkdownPreview(file) }
                }
            }
        } catch (_: Exception) {
            didFail = true
        } finally {
            isLoading = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF7F8FA))) {
        when {
            didFail -> {
                UnsupportedDocumentPreview(
                    fileName = fileName,
                    onDismiss = onDismiss
                )
            }
            previewKind == AndroidDocumentPreviewKind.Unsupported -> {
                UnsupportedDocumentPreview(
                    fileName = fileName,
                    onDismiss = onDismiss
                )
            }
            previewKind == AndroidDocumentPreviewKind.Text && textPreview != null -> {
                TextDocumentPreview(
                    title = fileName ?: "Document",
                    text = textPreview.orEmpty()
                )
            }
            (previewKind == AndroidDocumentPreviewKind.Docx || previewKind == AndroidDocumentPreviewKind.Doc) && docxPreview != null -> {
                TextDocumentPreview(
                    title = fileName ?: "Document",
                    text = docxPreview.orEmpty()
                )
            }
            (previewKind == AndroidDocumentPreviewKind.Spreadsheet || previewKind == AndroidDocumentPreviewKind.LegacySpreadsheet || previewKind == AndroidDocumentPreviewKind.Presentation || previewKind == AndroidDocumentPreviewKind.LegacyPresentation) && officePreview != null -> {
                TextDocumentPreview(
                    title = fileName ?: "Document",
                    text = officePreview.orEmpty()
                )
            }
            previewKind == AndroidDocumentPreviewKind.Pdf && localFile != null -> {
                PdfDocumentPreview(
                    title = fileName ?: "Document",
                    file = localFile!!,
                    isLoading = isLoading
                )
            }
            else -> {
                DocumentLoadingPreview(
                    title = fileName ?: "Document",
                    isLoading = isLoading
                )
            }
        }

        DocumentTopBar(
            title = fileName ?: "Document",
            onDismiss = onDismiss,
            onShare = {
                scope.launch {
                    ensureDocumentFile(context, localFile, url, accessToken, resolvedCacheKey, fileName)?.let {
                        shareDocument(context, it, fileName, mimeType)
                    }
                }
            },
            onSaveToLocal = {
                scope.launch {
                    ensureDocumentFile(context, localFile, url, accessToken, resolvedCacheKey, fileName)?.let {
                        saveDocumentToDownloads(context, it, fileName, mimeType)
                    }
                }
            },
            onOpenWithOtherApp = {
                scope.launch {
                    ensureDocumentFile(context, localFile, url, accessToken, resolvedCacheKey, fileName)?.let {
                        openDocumentWithOtherApp(context, it, fileName, mimeType)
                    }
                }
            },
            onCopyFileName = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("file_name", fileName ?: ""))
            },
        )
    }
}

@Composable
private fun DocumentTopBar(
    title: String,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onSaveToLocal: () -> Unit,
    onOpenWithOtherApp: () -> Unit,
    onCopyFileName: () -> Unit
) {
    var showMenu by remember(title) { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF7F8FA).copy(alpha = 0.96f))
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onDismiss,
            shape = CircleShape,
            color = Color(0xFF2A2D36).copy(alpha = 0.92f),
            border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.06f)),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = choose("Close", "关闭"),
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color(0xFF2A2D36).copy(alpha = 0.20f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Text(
                    text = title,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                    color = Color(0xFF1B1F24),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Box {
            Surface(
                onClick = { showMenu = true },
                shape = CircleShape,
                color = Color(0xFF2A2D36).copy(alpha = 0.92f),
                border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.06f)),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = choose("More", "更多"),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(choose("Share", "分享")) },
                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onShare()
                    }
                )
                DropdownMenuItem(
                    text = { Text(choose("Save locally", "保存到本地")) },
                    leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onSaveToLocal()
                    }
                )
                DropdownMenuItem(
                    text = { Text(choose("Open with another app", "用其他应用打开")) },
                    leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onOpenWithOtherApp()
                    }
                )
                DropdownMenuItem(
                    text = { Text(choose("Copy filename", "复制文件名")) },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onCopyFileName()
                    }
                )
                DropdownMenuItem(
                    text = { Text(choose("Close", "关闭")) },
                    leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun DocumentLoadingPreview(title: String, isLoading: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(color = Color(0xFF3B82F6), strokeWidth = 2.4.dp)
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF1B1F24),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (isLoading) choose("Opening document...", "正在打开文档...") else choose("Document preview is temporarily unavailable", "文档暂时不可预览"),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF6B7280)
            )
        }
    }
}

@Composable
private fun UnsupportedDocumentPreview(
    fileName: String?,
    onDismiss: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = Color(0xFF9CA3AF),
                modifier = Modifier.size(44.dp)
            )
            Text(
                text = fileName ?: "Document",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF1B1F24),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = choose("Native preview is not supported for this file type.", "这个文件类型暂不支持原生预览"),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF6B7280)
            )
            Surface(
                onClick = onDismiss,
                shape = RoundedCornerShape(999.dp),
                color = Color(0xFF2A2D36),
                modifier = Modifier.padding(top = 6.dp)
            ) {
                Text(
                    text = choose("Close", "关闭"),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun TextDocumentPreview(
    title: String,
    text: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
            .padding(top = 104.dp, bottom = 18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF111827),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            MarkdownMessageText(
                text = text,
                textColor = Color(0xFF111827),
                linkColor = Color(0xFF2563EB),
                textSizeSp = 15.5f,
                onDarkBackground = false
            )
        }
    }
}

@Composable
private fun PdfDocumentPreview(
    title: String,
    file: File,
    isLoading: Boolean
) {
    val rendererHandle = rememberPdfRenderer(file)
    if (rendererHandle == null) {
        UnsupportedDocumentPreview(fileName = title, onDismiss = {})
        return
    }
    Box(modifier = Modifier.fillMaxSize().padding(top = 104.dp, bottom = 18.dp)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = title,
                    modifier = Modifier.padding(horizontal = 18.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF111827),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            items((0 until rendererHandle.renderer.pageCount).toList()) { pageIndex ->
                PdfPagePreview(
                    renderer = rendererHandle.renderer,
                    pageIndex = pageIndex,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            }
            if (isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF3B82F6), strokeWidth = 2.2.dp, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPagePreview(
    renderer: PdfRenderer,
    pageIndex: Int,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val widthPx = remember(maxWidth) { with(density) { maxWidth.toPx().roundToInt().coerceAtLeast(1) } }
        val bitmapState = produceState<Bitmap?>(initialValue = null, renderer, pageIndex, widthPx) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    renderer.openPage(pageIndex).use { page ->
                        val scale = widthPx.toFloat() / max(1, page.width).toFloat()
                        val heightPx = max(1, (page.height * scale).roundToInt())
                        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                }.getOrNull()
            }
        }
        val bitmap = bitmapState.value
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = choose("PDF page $pageIndex", "PDF 第 $pageIndex 页"),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(density) { bitmap.height.toDp() })
                    .clip(RoundedCornerShape(16.dp))
                    .border(BorderStroke(1.dp, Color(0xFFE5E7EB)), RoundedCornerShape(16.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFF1F5F9)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF3B82F6), strokeWidth = 2.2.dp, modifier = Modifier.size(22.dp))
            }
        }
    }
}

private class PdfRendererHandle(
    private val parcelFileDescriptor: ParcelFileDescriptor,
    val renderer: PdfRenderer
) : Closeable {
    override fun close() {
        runCatching { renderer.close() }
        runCatching { parcelFileDescriptor.close() }
    }
}

@Composable
private fun rememberPdfRenderer(file: File): PdfRendererHandle? {
    val handle = remember(file.absolutePath) {
        runCatching {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRendererHandle(descriptor, PdfRenderer(descriptor))
        }.getOrNull()
    }
    DisposableEffect(handle) {
        onDispose { handle?.close() }
    }
    return handle
}

private fun downloadDocumentToCache(
    url: String,
    accessToken: String,
    cacheKey: String,
    fileName: String?
): File? {
    RemoteAttachmentCache.cachedFile(cacheKey)?.let { return it }
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
        if (accessToken.isNotBlank()) setRequestProperty("Authorization", "Bearer $accessToken")
        connectTimeout = 15_000
        readTimeout = 30_000
        connect()
    }
    val bytes = conn.inputStream.use { input ->
        if (conn.responseCode !in 200..299) return null
        input.readBytes()
    }
    return RemoteAttachmentCache.put(cacheKey, fileName, bytes)
}

private fun loadPlainTextPreview(file: File): String {
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

private fun loadDocxMarkdownPreview(file: File): String {
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

private fun loadDocMarkdownPreview(file: File): String {
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

private fun loadSpreadsheetMarkdownPreview(file: File): String {
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

private fun loadLegacySpreadsheetMarkdownPreview(file: File): String {
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

private fun loadPresentationMarkdownPreview(file: File): String {
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

private fun loadLegacyPresentationMarkdownPreview(file: File): String {
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

private fun shareDocument(context: Context, file: File, fileName: String?, mimeType: String?) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", file)
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = resolveDocumentMimeType(fileName, mimeType)
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, choose("Share file", "分享文件")))
}

private fun openDocumentWithOtherApp(context: Context, file: File, fileName: String?, mimeType: String?) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", file)
    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, resolveDocumentMimeType(fileName, mimeType))
        clipData = ClipData.newRawUri(file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val chooser = Intent.createChooser(viewIntent, choose("Open with another app", "用其他应用打开")).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(chooser)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, choose("No app can open this file", "没有可用于打开该文件的应用"), Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, choose("Open failed: ${e.message ?: "Unknown error"}", "打开失败：${e.message ?: "未知错误"}"), Toast.LENGTH_SHORT).show()
    }
}

private fun saveDocumentToDownloads(context: Context, file: File, fileName: String?, mimeType: String?): Boolean {
    return try {
        val resolver = context.contentResolver
        val displayName = sanitizeFileName(fileName?.takeIf { it.isNotBlank() } ?: file.name)
        val resolvedMimeType = resolveDocumentMimeType(fileName, mimeType)
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/ClawLink"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, resolvedMimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Files.getContentUri("external")
        }
        val uri = resolver.insert(collection, values) ?: throw IllegalStateException(choose("Unable to create save location", "无法创建保存位置"))
        resolver.openOutputStream(uri)?.use { output ->
            FileInputStream(file).use { input -> input.copyTo(output) }
        } ?: throw IllegalStateException(choose("Unable to write file", "无法写入文件"))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        Toast.makeText(context, choose("Saved to Downloads", "已保存到下载目录"), Toast.LENGTH_SHORT).show()
        true
    } catch (e: Exception) {
        Toast.makeText(context, choose("Save failed: ${e.message ?: "Unknown error"}", "保存失败：${e.message ?: "未知错误"}"), Toast.LENGTH_SHORT).show()
        false
    }
}

private fun ensureDocumentFile(
    context: Context,
    localFile: File?,
    url: String,
    accessToken: String,
    cacheKey: String,
    fileName: String?
): File? {
    localFile?.takeIf { it.exists() }?.let { return it }
    return downloadDocumentToCache(
        url = url,
        accessToken = accessToken,
        cacheKey = cacheKey,
        fileName = fileName
    ) ?: RemoteAttachmentCache.cachedFile(cacheKey)
}

private fun resolveDocumentMimeType(fileName: String?, mimeType: String?): String {
    val trimmedMime = mimeType?.trim().orEmpty()
    if (trimmedMime.isNotBlank() && trimmedMime != "application/octet-stream") return trimmedMime
    val ext = fileName?.substringAfterLast('.', missingDelimiterValue = "")?.trim()?.lowercase().orEmpty()
    val fromMap = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    return when {
        fromMap.isNullOrBlank() -> "application/octet-stream"
        else -> fromMap
    }
}

private fun sanitizeFileName(name: String): String {
    val cleaned = name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
    return cleaned.ifBlank { "document_${System.currentTimeMillis()}" }
}
