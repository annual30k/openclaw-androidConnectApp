package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import org.junit.Assert.assertEquals
import org.junit.Test

class AttachmentCacheKeyTest {
    @Test
    fun stableRecordUsesFileIdBeforeAttachmentId() {
        val block = RelayChatContentBlock(
            type = "file",
            fileId = "file-1",
            attachmentId = "attachment-1",
            gatewayId = "gateway-1",
            sessionKey = "session-1",
            downloadUrl = "/temporary/url"
        )

        assertEquals("gateway-1|session-1|file-1", block.chatAttachmentCacheKey())
    }

    @Test
    fun stableRecordFallsBackToAttachmentIdInsteadOfTemporaryUrl() {
        val block = RelayChatContentBlock(
            type = "file",
            attachmentId = "attachment-1",
            gatewayId = "gateway-1",
            sessionKey = "session-1",
            downloadUrl = "/temporary/url"
        )

        assertEquals("gateway-1|session-1|attachment-1", block.chatAttachmentCacheKey())
    }
}
