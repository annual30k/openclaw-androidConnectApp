package com.rethinkingstudio.clawlink.ui.screens.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * Utility functions for handling chat attachments and file operations.
 */
internal object ChatFileUtils {

    internal suspend fun importPickedAttachments(
        context: Context,
        uris: List<Uri>
    ): List<ComposerAttachmentDraft> = withContext(Dispatchers.IO) {
        val drafts = mutableListOf<ComposerAttachmentDraft>()
        for (uri in uris) {
            runCatching {
                importPickedAttachment(context, uri)
            }.onSuccess { draft ->
                drafts += draft
            }
        }
        drafts
    }

    private suspend fun importPickedAttachment(
        context: Context,
        uri: Uri
    ): ComposerAttachmentDraft {
        val resolver = context.contentResolver
        val fileName = normalizeComposerAttachmentFileName(
            queryDisplayName(context, uri) ?: uri.lastPathSegment ?: "attachment"
        )
        val mimeType = resolver.getType(uri) ?: "application/octet-stream"
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException(choose("Unable to read selected file", "无法读取所选文件"))
        
        val directory = File(context.cacheDir, "clawlink-compose-attachments").apply { mkdirs() }
        val targetFile = File(directory, "${UUID.randomUUID()}-$fileName")
        targetFile.writeBytes(bytes)
        
        val imageDimensions = if (isImageMimeType(mimeType)) {
            attachmentImageDimensions(targetFile)
        } else {
            null
        }
        
        return ComposerAttachmentDraft(
            fileUri = targetFile.absolutePath,
            fileName = fileName,
            mimeType = mimeType,
            sizeBytes = bytes.size.toLong(),
            imageWidth = imageDimensions?.first,
            imageHeight = imageDimensions?.second
        )
    }

    internal suspend fun importCapturedImage(
        context: Context,
        bitmap: Bitmap
    ): ComposerAttachmentDraft = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "clawlink-compose-attachments").apply { mkdirs() }
        val fileName = "camera-${UUID.randomUUID()}.jpg"
        val targetFile = File(directory, fileName)
        val buffer = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, buffer)
        targetFile.writeBytes(buffer.toByteArray())
        
        ComposerAttachmentDraft(
            fileUri = targetFile.absolutePath,
            fileName = fileName,
            mimeType = "image/jpeg",
            sizeBytes = targetFile.length(),
            imageWidth = bitmap.width.takeIf { it > 0 },
            imageHeight = bitmap.height.takeIf { it > 0 }
        )
    }

    internal fun normalizeComposerAttachmentFileName(fileName: String): String {
        val trimmed = fileName.trim()
        return trimmed.ifEmpty { "attachment" }.replace(Regex("""[\\/:*?"<>|]+"""), "_")
    }

    internal fun attachmentImageDimensions(file: File): Pair<Int, Int>? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            return null
        }
        return options.outWidth to options.outHeight
    }

    internal fun queryDisplayName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else {
                null
            }
        }
    }

    internal fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
