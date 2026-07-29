package com.rethinkingstudio.clawlink.core.state.chat

enum class AttachmentSource {
    LOCAL_ORIGINAL,
    LOCAL_CACHED_COPY,
    LOCAL_THUMBNAIL,
    REMOTE,
    SERVER_CLEANED,
    UNAVAILABLE
}

data class AttachmentAvailability(
    val source: AttachmentSource,
    val canOpenOriginal: Boolean,
    val canDisplayThumbnail: Boolean,
    val shouldAttemptRemoteDownload: Boolean
)

/** UI、下载与缓存适配器共用的纯附件优先级模型。 */
@Suppress("UNUSED_PARAMETER")
fun resolveAttachmentAvailability(
    hasLocalOriginal: Boolean,
    hasLocalCachedCopy: Boolean,
    hasLocalThumbnail: Boolean,
    hasRemoteReference: Boolean,
    expiresAt: String?,
    serverReportedExpired: Boolean,
    nowEpochMs: Long = System.currentTimeMillis()
): AttachmentAvailability {
    if (hasLocalOriginal) {
        return AttachmentAvailability(AttachmentSource.LOCAL_ORIGINAL, true, hasLocalThumbnail, false)
    }
    if (hasLocalCachedCopy) {
        return AttachmentAvailability(AttachmentSource.LOCAL_CACHED_COPY, true, hasLocalThumbnail, false)
    }
    // expiresAt 是展示/缓存元数据，不是清理事实。客户端时钟可能漂移；只有
    // 服务端明确 state=expired，或下载端收到 410/file_expired 后写入的
    // serverReportedExpired，才可以停止远端尝试并显示“服务端已清理”。
    val remoteExpired = serverReportedExpired
    if (hasLocalThumbnail) {
        return AttachmentAvailability(
            source = AttachmentSource.LOCAL_THUMBNAIL,
            canOpenOriginal = false,
            canDisplayThumbnail = true,
            shouldAttemptRemoteDownload = hasRemoteReference && !remoteExpired
        )
    }
    if (remoteExpired) {
        return AttachmentAvailability(AttachmentSource.SERVER_CLEANED, false, false, false)
    }
    if (hasRemoteReference) {
        return AttachmentAvailability(AttachmentSource.REMOTE, false, false, true)
    }
    return AttachmentAvailability(AttachmentSource.UNAVAILABLE, false, false, false)
}

fun isExplicitAttachmentExpiredState(transferState: String?, state: String?): Boolean {
    return sequenceOf(transferState, state)
        .mapNotNull { it?.trim()?.lowercase()?.takeIf(String::isNotEmpty) }
        .any { it == "expired" || it == "file_expired" }
}
