package com.rethinkingstudio.clawlink.ui.screens.chat.components

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
import com.rethinkingstudio.clawlink.core.state.chat.RemoteAttachmentCache
import java.io.File
import java.io.FileInputStream

internal fun shareDocument(context: Context, file: File, fileName: String?, mimeType: String?) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", file)
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = resolveDocumentMimeType(fileName, mimeType)
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, choose("Share file", "分享文件")))
}

internal fun openDocumentWithOtherApp(context: Context, file: File, fileName: String?, mimeType: String?) {
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

internal fun saveDocumentToDownloads(context: Context, file: File, fileName: String?, mimeType: String?): Boolean {
    return try {
        val resolver = context.contentResolver
        val displayName = sanitizeDocumentFileName(fileName?.takeIf { it.isNotBlank() } ?: file.name)
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

internal fun ensureDocumentFile(
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

internal fun resolveDocumentMimeType(fileName: String?, mimeType: String?): String {
    val trimmedMime = mimeType?.trim().orEmpty()
    if (trimmedMime.isNotBlank() && trimmedMime != "application/octet-stream") return trimmedMime
    val ext = fileName?.substringAfterLast('.', missingDelimiterValue = "")?.trim()?.lowercase().orEmpty()
    val fromMap = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    return when {
        fromMap.isNullOrBlank() -> "application/octet-stream"
        else -> fromMap
    }
}

private fun sanitizeDocumentFileName(name: String): String {
    val cleaned = name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
    return cleaned.ifBlank { "document_${System.currentTimeMillis()}" }
}
