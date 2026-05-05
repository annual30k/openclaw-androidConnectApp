package com.rethinkingstudio.clawlink.ui.screens.chat

import java.util.Locale

internal enum class ComposerAttachmentPickTarget {
    ALBUM,
    CAMERA,
    FILES,
    IMAGES
}

internal data class ComposerAttachmentDraft(
    val filePath: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val imageWidth: Int? = null,
    val imageHeight: Int? = null
) {
    val displaySize: String
        get() = formatAttachmentSize(sizeBytes)

    val displaySubtitle: String
        get() = listOf(mimeType, displaySize)
            .filter { it.trim().isNotEmpty() }
            .joinToString(" · ")

    val symbolName: String
        get() = attachmentSymbolName(mimeType)

    val isImage: Boolean
        get() = isImageMimeType(mimeType)
}

internal fun attachmentPickerMimeTypes(target: ComposerAttachmentPickTarget): Array<String> {
    return when (target) {
        ComposerAttachmentPickTarget.ALBUM -> arrayOf("image/*")
        ComposerAttachmentPickTarget.CAMERA -> arrayOf("image/*")
        ComposerAttachmentPickTarget.FILES -> arrayOf("*/*")
        ComposerAttachmentPickTarget.IMAGES -> arrayOf("image/*")
    }
}

internal fun composerAttachmentMenuTargets(): List<ComposerAttachmentPickTarget> {
    return listOf(
        ComposerAttachmentPickTarget.ALBUM,
        ComposerAttachmentPickTarget.CAMERA,
        ComposerAttachmentPickTarget.FILES
    )
}

internal fun isImageMimeType(mimeType: String): Boolean {
    return mimeType.trim().lowercase().startsWith("image/")
}

internal fun attachmentSymbolName(mimeType: String): String {
    val normalizedMimeType = mimeType.trim().lowercase()
    return when {
        normalizedMimeType.startsWith("image/") -> "photo"
        normalizedMimeType.startsWith("video/") -> "video"
        normalizedMimeType.startsWith("audio/") -> "waveform"
        normalizedMimeType.contains("pdf") -> "doc.richtext"
        normalizedMimeType.contains("zip") || normalizedMimeType.contains("archive") -> "archivebox"
        else -> "doc"
    }
}

internal fun formatAttachmentSize(sizeBytes: Long): String {
    return when {
        sizeBytes < 1024 -> "$sizeBytes B"
        sizeBytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", sizeBytes / 1024.0)
        else -> String.format(Locale.US, "%.1f MB", sizeBytes / (1024.0 * 1024.0))
    }
}
