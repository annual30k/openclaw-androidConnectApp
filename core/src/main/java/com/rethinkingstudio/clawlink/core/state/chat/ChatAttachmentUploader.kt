package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.network.RelayAPIClient
import com.rethinkingstudio.clawlink.core.network.dto.RelayFileTransferItem

internal fun mobileAttachmentChunkRanges(byteCount: Int, chunkSize: Int): List<Pair<Int, Int>> {
    val safeSize = byteCount.coerceAtLeast(0)
    val safeChunkSize = chunkSize.coerceAtLeast(1)
    if (safeSize == 0) return listOf(0 to 0)
    return buildList {
        var offset = 0
        while (offset < safeSize) {
            val end = minOf(offset + safeChunkSize, safeSize)
            add(offset to end)
            offset = end
        }
    }
}

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
    sourceRunId: String?,
    idempotencyKey: String,
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
        clientCreatedAt = clientCreatedAt,
        sourceRunId = sourceRunId,
        idempotencyKey = idempotencyKey
    )
    val chunkSize = init.chunkSize.coerceAtLeast(1)
    var chunkIndex = 0
    for ((start, end) in mobileAttachmentChunkRanges(bytes.size, chunkSize)) {
        apiClient.uploadMobileFileChunk(init.uploadId, chunkIndex, bytes.copyOfRange(start, end))
        chunkIndex += 1
        val progress = if (bytes.isEmpty()) 1.0 else end.toDouble() / bytes.size.toDouble()
        onProgress?.invoke(progress.coerceIn(0.0, 1.0))
    }
    return apiClient.completeMobileFileUpload(init.uploadId, chunkIndex).payload
}
