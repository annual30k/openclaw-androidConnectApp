package com.rethinkingstudio.clawlink.ui.screens.chat

internal data class ChatImagePreviewState(
    val url: String,
    val accessToken: String,
    val fileName: String?,
    val cacheKey: String? = null
)

internal data class ChatDocumentPreviewState(
    val url: String,
    val accessToken: String,
    val fileName: String?,
    val mimeType: String?,
    val cacheKey: String? = null
)

