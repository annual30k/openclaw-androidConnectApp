package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import org.junit.Assert.assertEquals
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
}
