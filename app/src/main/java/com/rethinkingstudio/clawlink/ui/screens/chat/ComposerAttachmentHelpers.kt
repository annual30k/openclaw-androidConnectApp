package com.rethinkingstudio.clawlink.ui.screens.chat

import com.rethinkingstudio.clawlink.core.models.chat.ComposerAttachmentDraft as CoreComposerAttachmentDraft
import java.util.Locale

internal enum class ComposerAttachmentPickTarget {
    ALBUM,
    CAMERA,
    FILES,
    IMAGES
}

internal typealias ComposerAttachmentDraft = CoreComposerAttachmentDraft

internal val ComposerAttachmentDraft.filePath: String
    get() = fileUri

internal val ComposerAttachmentDraft.symbolName: String
    get() = attachmentSymbolName(mimeType)

internal val ComposerAttachmentDraft.isImage: Boolean
    get() = isImageMimeType(mimeType)

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
