package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatOutgoingMessageBuilderTest {
    @Test
    fun localAttachmentBlocksCarryOutgoingClientRunIdentity() {
        val draft = buildLocalTextOutgoingRun(
            currentMessages = emptyList(),
            content = "分析一下这个文件",
            gatewayId = "gateway-1",
            sessionKey = "main",
            clientRunId = "client-run-file-1",
            attachmentIds = listOf("attachment-1"),
            attachmentBlocks = listOf(
                RelayChatContentBlock(
                    type = "file",
                    fileId = "file-1",
                    fileName = "report.pdf",
                    mimeType = "application/pdf",
                    downloadUrl = "/api/mobile/files/file-1"
                )
            )
        )

        assertEquals("local-user-client-run-file-1", draft.userMessage.runId)
        assertEquals("client-run-file-1", draft.userMessage.fileContentBlocks.single().sourceRunId)
    }

    @Test
    fun localTextAttachmentUserMessageKeepsPromptAfterTimelineCanonicalization() {
        val draft = buildLocalTextOutgoingRun(
            currentMessages = emptyList(),
            content = "stable identity audit",
            gatewayId = "gateway-1",
            sessionKey = "main",
            clientRunId = "client-run-audit-1",
            attachmentIds = listOf("attachment-1"),
            attachmentBlocks = listOf(
                RelayChatContentBlock(
                    type = "file",
                    attachmentId = "attachment-1",
                    fileId = "file-1",
                    fileName = "report.pdf",
                    mimeType = "application/pdf",
                    downloadUrl = "/api/mobile/files/file-1"
                )
            )
        )

        val ordered = sortTimelineMessagesV3(draft.messages, "main")
        val userMessage = ordered.first { it.role.name == "user" }

        assertEquals("stable identity audit", userMessage.content)
        assertEquals("message:user", userMessage.timelineItemKind)
        assertEquals("client-run-audit-1", userMessage.fileContentBlocks.single().sourceRunId)
        assertTrue(userMessage.contentBlocks.first().isTextBlock)
    }

    @Test
    fun localTextUserMessageHasCanonicalTimelineKeys() {
        val draft = buildLocalTextOutgoingRun(
            currentMessages = emptyList(),
            content = "hello",
            gatewayId = "gateway-1",
            sessionKey = "main",
            clientRunId = "client-run-text-1",
            attachmentIds = emptyList(),
            attachmentBlocks = emptyList()
        )

        assertEquals("local:client-run-text-1|10|user-client-run-text-1", draft.userMessage.timelineOrderKey)
        assertEquals("local:message:user:client-run-text-1", draft.userMessage.timelineIdentityKey)
        assertEquals("message:user", draft.userMessage.timelineItemKind)
    }
}
