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
internal fun UnsupportedDocumentPreview(
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
