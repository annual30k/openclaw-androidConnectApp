package com.rethinkingstudio.clawlink.ui.screens.chat.components

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
import java.io.Closeable
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun PdfDocumentPreview(
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
        var bitmap by remember(renderer, pageIndex, widthPx) { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(renderer, pageIndex, widthPx) {
            bitmap = withContext(Dispatchers.IO) {
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
        val currentBitmap = bitmap
        if (currentBitmap != null) {
            Image(
                bitmap = currentBitmap.asImageBitmap(),
                contentDescription = choose("PDF page $pageIndex", "PDF 第 $pageIndex 页"),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(density) { currentBitmap.height.toDp() })
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
