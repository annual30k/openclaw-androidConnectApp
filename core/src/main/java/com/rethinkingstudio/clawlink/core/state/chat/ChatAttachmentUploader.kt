package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.network.RelayAPIClient
import com.rethinkingstudio.clawlink.core.network.dto.RelayFileTransferItem

internal suspend fun uploadMobileAttachment(
    apiClient: RelayAPIClient,
    gatewayId: String,
    sessionKey: String,
    fileName: String,
    mimeType: String,
    bytes: ByteArray,
    sha256: String,
    durationMs: Int?,
    imageWidth: Int?,
    imageHeight: Int?,
    senderDisplayName: String?,
    clientCreatedAt: String?,
    onProgress: ((Double) -> Unit)?
): RelayFileTransferItem {
    val init = apiClient.initMobileFileUpload(
        gatewayId = gatewayId,
        sessionKey = sessionKey,
        fileName = fileName,
        mimeType = mimeType,
        sizeBytes = bytes.size.toLong(),
        sha256 = sha256,
        durationMs = durationMs,
        imageWidth = imageWidth,
        imageHeight = imageHeight,
        senderDisplayName = senderDisplayName,
        clientCreatedAt = clientCreatedAt
    )
    val chunkSize = init.chunkSize.coerceAtLeast(1)
    var offset = 0
    var chunkIndex = 0
    while (offset < bytes.size) {
        val end = minOf(offset + chunkSize, bytes.size)
        apiClient.uploadMobileFileChunk(init.uploadId, chunkIndex, bytes.copyOfRange(offset, end))
        offset = end
        chunkIndex += 1
        onProgress?.invoke((offset.toDouble() / bytes.size.toDouble()).coerceIn(0.0, 1.0))
    }
    if (bytes.isNotEmpty()) {
        onProgress?.invoke(1.0)
    }
    return apiClient.completeMobileFileUpload(init.uploadId, chunkIndex).payload
}
