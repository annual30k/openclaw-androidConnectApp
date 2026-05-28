package com.rethinkingstudio.clawlink.ui.screens.chat.components

import android.content.Intent
import android.content.Context
import android.content.ClipData
import android.content.ContentValues
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.fillMaxHeight
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
import com.rethinkingstudio.clawlink.core.state.chat.RemoteImageCache
import com.rethinkingstudio.clawlink.core.state.chat.RemoteImageSizeCache
import com.rethinkingstudio.clawlink.core.state.chat.chatImageCacheKey
import com.rethinkingstudio.clawlink.ui.screens.chat.ChatColors
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

internal fun imagePreviewDimensions(block: RelayChatContentBlock): Pair<Dp, Dp> {
    val maxWidth = 290.dp
    val maxHeight = 400.dp
    val cacheKey = block.chatImageCacheKey()
    val w = block.resolvedImageWidth?.takeIf { it > 0 }
    val h = block.resolvedImageHeight?.takeIf { it > 0 }
    if (w != null && h != null) {
        val ratio = w.toFloat() / h.toFloat()
        val size = if (ratio >= 1f) {
            maxWidth to (maxWidth / ratio).coerceIn(60.dp, maxHeight)
        } else {
            val finalHeight = maxHeight.coerceAtMost(500.dp)
            (finalHeight * ratio).coerceIn(60.dp, maxWidth) to finalHeight
        }
        if (cacheKey != null) RemoteImageSizeCache.put(cacheKey, size.first.value to size.second.value)
        return size
    }
    if (cacheKey != null) {
        val cached = RemoteImageSizeCache.get(cacheKey)
        if (cached != null) return cached.first.dp to cached.second.dp
        val decodedSize = RemoteImageCache.cachedBitmapSize(cacheKey)
        if (decodedSize != null) {
            val ratio = decodedSize.first.toFloat() / decodedSize.second.toFloat()
            val size = if (ratio >= 1f) {
                maxWidth to (maxWidth / ratio).coerceIn(60.dp, maxHeight)
            } else {
                val finalHeight = maxHeight.coerceAtMost(500.dp)
                (finalHeight * ratio).coerceIn(60.dp, maxWidth) to finalHeight
            }
            RemoteImageSizeCache.put(cacheKey, size.first.value to size.second.value)
            return size
        }
    }
    return 220.dp to 200.dp
}

@Composable
internal fun LocalAttachmentImageThumbnail(filePath: String, size: Dp, cornerRadius: Dp = 14.dp, cacheKey: String? = null) {
    val resolvedCacheKey = cacheKey ?: filePath
    var bitmap by remember(resolvedCacheKey, filePath) { mutableStateOf(RemoteImageCache.get(resolvedCacheKey)) }
    var didFail by remember(resolvedCacheKey, filePath) { mutableStateOf(false) }
    LaunchedEffect(resolvedCacheKey, filePath) {
        if (bitmap != null) return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val options = BitmapFactory.Options().apply { inSampleSize = computeSampleSize(filePath, size.value.toInt()) }
                BitmapFactory.decodeFile(filePath, options)
            }.getOrNull()
        }
        if (bitmap == null) didFail = true
    }
    Box(modifier = Modifier.size(size).clip(RoundedCornerShape(cornerRadius))) {
        Crossfade(targetState = bitmap, animationSpec = tween(180), label = "thumb") { bmp ->
            if (bmp != null) {
                Image(bitmap = bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.12f))))
            } else {
                ImageLoadingPlaceholder(isFailed = didFail, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
internal fun LocalAttachmentImagePreview(filePath: String, width: Dp, height: Dp, cornerRadius: Dp = 18.dp, cacheKey: String? = null) {
    val resolvedCacheKey = cacheKey ?: filePath
    var bitmap by remember(resolvedCacheKey, filePath) { mutableStateOf(RemoteImageCache.get(resolvedCacheKey)) }
    var didFail by remember(resolvedCacheKey, filePath) { mutableStateOf(false) }
    LaunchedEffect(resolvedCacheKey, filePath, width, height) {
        if (bitmap != null) return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val targetPx = maxOf(width.value.toInt(), height.value.toInt()).coerceAtLeast(1)
                val options = BitmapFactory.Options().apply { inSampleSize = computeSampleSize(filePath, targetPx) }
                BitmapFactory.decodeFile(filePath, options)
            }.getOrNull()
        }
        didFail = bitmap == null
    }
    Box(modifier = Modifier.width(width).height(height).clip(RoundedCornerShape(cornerRadius))) {
        Crossfade(targetState = bitmap, animationSpec = tween(180), label = "local_img") { bmp ->
            if (bmp != null) {
                Image(bitmap = bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.12f))))
                Box(modifier = Modifier.fillMaxSize().border(width = 1.dp, color = Color.White.copy(alpha = 0.07f), shape = RoundedCornerShape(cornerRadius)))
            } else {
                ImageLoadingPlaceholder(isFailed = didFail, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

private fun computeSampleSize(filePath: String, targetPx: Int): Int {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(filePath, opts)
    var size = 1
    while ((opts.outWidth / size) > targetPx * 2 && (opts.outHeight / size) > targetPx * 2) { size *= 2 }
    return size
}

@Composable
internal fun AuthenticatedRemoteImage(
    url: String,
    accessToken: String,
    width: Dp,
    height: Dp,
    cacheKey: String? = null,
    cornerRadius: Dp = 18.dp
) {
    val resolvedCacheKey = cacheKey ?: url
    var bitmap by remember(resolvedCacheKey, url) { mutableStateOf(RemoteImageCache.get(resolvedCacheKey)) }
    var didFail by remember(resolvedCacheKey, url) { mutableStateOf(false) }
    val displaySize = remember(bitmap, url, width, height) {
        val isFallback = width == 220.dp && height == 200.dp
        if (isFallback && bitmap != null) {
            val bmp = bitmap!!
            val ratio = bmp.width.toFloat() / bmp.height.toFloat()
            val maxW = 290.dp
            val maxH = 400.dp
            if (ratio >= 1f) maxW to (maxW / ratio).coerceIn(60.dp, maxH)
            else {
                val fh = maxH.coerceAtMost(500.dp)
                (fh * ratio).coerceIn(60.dp, maxW) to fh
            }
        } else {
            width to height
        }
    }
    LaunchedEffect(resolvedCacheKey, url, accessToken) {
        if (bitmap != null) return@LaunchedEffect
        didFail = false
        val result = withContext(Dispatchers.IO) { loadRemoteBitmap(url, accessToken, resolvedCacheKey) }
        if (result != null) {
            RemoteImageCache.put(resolvedCacheKey, result)
            val ratio = result.width.toFloat() / result.height.toFloat()
            val maxW = 290.dp
            val maxH = 400.dp
            val size = if (ratio >= 1f) maxW to (maxW / ratio).coerceIn(60.dp, maxH)
            else {
                val fh = maxH.coerceAtMost(500.dp)
                (fh * ratio).coerceIn(60.dp, maxW) to fh
            }
            RemoteImageSizeCache.put(resolvedCacheKey, size.first.value to size.second.value)
        }
        bitmap = result; didFail = result == null
    }
    Box(modifier = Modifier.width(displaySize.first).height(displaySize.second).clip(RoundedCornerShape(cornerRadius))) {
        Crossfade(targetState = bitmap, animationSpec = tween(220), label = "remote_img") { bmp ->
            if (bmp != null) {
                Image(bitmap = bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.12f))))
                Box(modifier = Modifier.fillMaxSize().border(width = 1.dp, color = Color.White.copy(alpha = 0.07f), shape = RoundedCornerShape(cornerRadius)))
            } else {
                ImageLoadingPlaceholder(isFailed = didFail, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

private fun loadRemoteBitmap(url: String, accessToken: String, cacheKey: String): Bitmap? {
    return runCatching {
        val req = URL(url).openConnection() as HttpURLConnection
        if (accessToken.isNotBlank()) req.setRequestProperty("Authorization", "Bearer $accessToken")
        req.connectTimeout = 15_000
        req.readTimeout = 30_000
        req.connect()
        val responseCode = req.responseCode
        val contentType = req.contentType.orEmpty()
        if (responseCode !in 200..299) {
            Log.w("ClawLinkImage", "download failed code=$responseCode contentType=$contentType key=${cacheKey.take(48)} url=${sanitizeImageUrl(url)}")
            return@runCatching null
        }
        val bitmap = req.inputStream.use { BitmapFactory.decodeStream(it) }
        if (bitmap == null) {
            Log.w("ClawLinkImage", "decode failed code=$responseCode contentType=$contentType key=${cacheKey.take(48)} url=${sanitizeImageUrl(url)}")
        }
        bitmap
    }.onFailure { err ->
        Log.w("ClawLinkImage", "download exception key=${cacheKey.take(48)} url=${sanitizeImageUrl(url)} error=${err.javaClass.simpleName}: ${err.message}")
    }.getOrNull()
}

private fun sanitizeImageUrl(url: String): String {
    val queryIndex = url.indexOf('?')
    return if (queryIndex >= 0) url.substring(0, queryIndex) + "?..." else url
}

@Composable
private fun ImageLoadingPlaceholder(isFailed: Boolean, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(initialValue = 0.4f, targetValue = 0.85f, animationSpec = infiniteRepeatable(animation = tween(900, easing = LinearEasing), repeatMode = RepeatMode.Reverse), label = "shimmerAlpha")
    Box(modifier = modifier.background(if (isFailed) Color(0xFFF2F3F5) else Color(0xFFE8EAED).copy(alpha = if (isFailed) 1f else shimmerAlpha)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isFailed) {
                Icon(Icons.Default.Image, contentDescription = null, tint = Color(0xFFA0A4AF), modifier = Modifier.size(28.dp))
                Text(choose("Image load failed", "图片加载失败"), style = MaterialTheme.typography.labelSmall, color = Color(0xFFA0A4AF))
            } else {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = ChatColors.linkBlue.copy(alpha = 0.72f))
            }
        }
    }
}

@Composable
internal fun ImageFullscreenOverlay(
    url: String,
    accessToken: String,
    fileName: String?,
    cacheKey: String? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val resolvedCacheKey = cacheKey ?: url
    var bitmap by remember(resolvedCacheKey, url) { mutableStateOf(RemoteImageCache.get(resolvedCacheKey)) }
    var didFail by remember(resolvedCacheKey, url) { mutableStateOf(false) }
    var showTopMenu by remember(url) { mutableStateOf(false) }
    var localCopyFile by remember(resolvedCacheKey) { mutableStateOf(RemoteImageCache.cachedFile(resolvedCacheKey)) }
    LaunchedEffect(resolvedCacheKey, url, accessToken) {
        if (bitmap != null) return@LaunchedEffect
        didFail = false
        val result = withContext(Dispatchers.IO) { loadRemoteBitmap(url, accessToken, resolvedCacheKey) }
        if (result != null) {
            RemoteImageCache.put(resolvedCacheKey, result)
            localCopyFile = RemoteImageCache.cachedFile(resolvedCacheKey)
        }
        bitmap = result; didFail = result == null
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .modalTouchBarrier()
            .background(Color.Black)
            .zIndex(100f)
    ) {
        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.18f).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.78f), Color.Transparent))))
        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.16f).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f)))))
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val bmp = bitmap
            when {
                bmp != null -> Image(bitmap = bmp.asImageBitmap(), contentDescription = fileName, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                didFail -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = Color.White.copy(alpha = 0.80f), modifier = Modifier.size(42.dp))
                    Text(choose("Image load failed", "图片加载失败"), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.74f))
                }
                else -> CircularProgressIndicator(modifier = Modifier.size(32.dp), color = Color.White.copy(alpha = 0.80f), strokeWidth = 2.5.dp)
            }
        }
        // Top bar
        Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(onClick = onDismiss, shape = CircleShape, color = Color(0xFF2A2D36).copy(alpha = 0.92f), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = choose("Close", "关闭"), tint = Color.White, modifier = Modifier.size(18.dp)) }
            }
            Spacer(Modifier.size(12.dp))
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (!fileName.isNullOrBlank()) {
                    Surface(shape = RoundedCornerShape(999.dp), color = Color.Black.copy(alpha = 0.34f), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))) {
                        Text(fileName, modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp), style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.size(12.dp))
            Box {
                imageChromeButton(icon = Icons.Default.MoreVert) {
                    showTopMenu = !showTopMenu
                }

                DropdownMenu(
                    expanded = showTopMenu,
                    onDismissRequest = { showTopMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(choose("Share", "分享")) },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = {
                            shareCurrentImage(context, bitmap, localCopyFile)
                            showTopMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(choose("Save locally", "保存到本地")) },
                        leadingIcon = { Icon(Icons.Default.SaveAlt, contentDescription = null) },
                        onClick = {
                            saveImageToLocal(context, bitmap, localCopyFile, fileName)
                            showTopMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(choose("Copy filename", "复制文件名")) },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("file_name", fileName ?: ""))
                            showTopMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(choose("Close", "关闭")) },
                        leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                        onClick = {
                            showTopMenu = false
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun imageChromeButton(icon: androidx.compose.ui.graphics.vector.ImageVector, action: () -> Unit) {
    Surface(
        onClick = action,
        shape = CircleShape,
        color = Color(0xFF2A2D36).copy(alpha = 0.92f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

private fun shareCurrentImage(context: Context, bmp: Bitmap?, localCopyFile: File?) {
    val file = localCopyFile?.takeIf { it.exists() } ?: run {
        val image = bmp ?: return
        val generated = File(context.cacheDir, "shared_image_${System.currentTimeMillis()}.png")
        generated.outputStream().use { out -> image.compress(CompressFormat.PNG, 100, out) }
        generated
    }
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", file)
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, choose("Share image", "分享图片")))
}

private fun saveImageToLocal(context: Context, bmp: Bitmap?, localCopyFile: File?, fileName: String?) {
    val sourceFile = localCopyFile?.takeIf { it.exists() } ?: run {
        val image = bmp ?: run {
            Toast.makeText(context, choose("Image has not finished loading", "图片尚未加载完成"), Toast.LENGTH_SHORT).show()
            return
        }
        val generated = File(context.cacheDir, "saved_image_${System.currentTimeMillis()}.png")
        generated.outputStream().use { out -> image.compress(CompressFormat.PNG, 100, out) }
        generated
    }

    val targetName = sanitizeFileName(fileName ?: sourceFile.name)
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, targetName)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/ClawLink")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    try {
        val uri = resolver.insert(collection, values) ?: throw IllegalStateException(choose("Unable to create save location", "无法创建保存位置"))
        resolver.openOutputStream(uri)?.use { output ->
            sourceFile.inputStream().use { input -> input.copyTo(output) }
        } ?: throw IllegalStateException(choose("Unable to write file", "无法写入文件"))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        Toast.makeText(context, choose("Saved locally", "已保存到本地"), Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, choose("Save failed: ${e.message ?: "Unknown error"}", "保存失败：${e.message ?: "未知错误"}"), Toast.LENGTH_SHORT).show()
    }
}

private fun deleteCachedLocalCopy(
    context: Context,
    cacheKey: String,
    localCopyFile: File?,
    onDeleted: () -> Unit
) {
    try {
        val file = localCopyFile?.takeIf { it.exists() } ?: RemoteImageCache.cachedFile(cacheKey)
        if (file != null) {
            RemoteImageCache.remove(cacheKey)
        }
        onDeleted()
    } catch (e: Exception) {
        Toast.makeText(context, choose("Delete failed: ${e.message ?: "Unknown error"}", "删除失败：${e.message ?: "未知错误"}"), Toast.LENGTH_SHORT).show()
    }
}

private fun sanitizeFileName(name: String): String {
    val cleaned = name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
    return cleaned.ifBlank { "image_${System.currentTimeMillis()}.png" }
}
