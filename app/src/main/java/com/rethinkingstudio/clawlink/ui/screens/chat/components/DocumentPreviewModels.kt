package com.rethinkingstudio.clawlink.ui.screens.chat.components

import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock

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
