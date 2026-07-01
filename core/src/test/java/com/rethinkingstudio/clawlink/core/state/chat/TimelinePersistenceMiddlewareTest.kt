package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelinePersistenceMiddlewareTest {
    @Test
    fun schemaEnvelopeRoundTripsCanonicalTimelineState() {
        val state = ChatTimelineState(
            messages = listOf(
                ChatMessage(
                    id = "assistant-1",
                    role = MessageRole.assistant,
                    content = "hello",
                    timelineOrderKey = "0001",
                    timelineIdentityKey = "identity-assistant-1",
                    timelineItemKind = "message"
                )
            )
        )

        val encoded = TimelinePersistenceMiddleware.encodeSnapshot(state)
        val decoded = TimelinePersistenceMiddleware.decodeSnapshot(encoded)

        assertEquals(state, decoded)
    }

    @Test
    fun oldBareTimelineStateCacheIsIgnored() {
        val oldRawSnapshot = """{"messages":[],"activeRunId":null}"""

        assertNull(TimelinePersistenceMiddleware.decodeSnapshot(oldRawSnapshot))
    }

    @Test
    fun localOutgoingTextAttachmentTurnSurvivesSnapshotRoundTrip() {
        val draft = buildLocalTextOutgoingRun(
            currentMessages = emptyList(),
            content = "stable identity audit",
            gatewayId = "gateway-1",
            sessionKey = "main",
            clientRunId = "client-run-persist-1",
            attachmentIds = listOf("attachment-1"),
            attachmentBlocks = listOf(
                RelayChatContentBlock(
                    type = "file",
                    attachmentId = "attachment-1",
                    fileId = "file-1",
                    fileName = "report.pdf",
                    mimeType = "application/pdf",
                    downloadUrl = "/api/mobile/files/file-1",
                    sourceRunId = "client-run-persist-1"
                )
            )
        )
        val state = ChatTimelineState(
            messages = draft.messages,
            activeRunId = "client-run-persist-1",
            activeRunsByTurnId = mapOf("client-run-persist-1" to "client-run-persist-1"),
            activeTurnByRunId = mapOf("client-run-persist-1" to "client-run-persist-1")
        )

        val decoded = TimelinePersistenceMiddleware.decodeSnapshot(
            TimelinePersistenceMiddleware.encodeSnapshot(state)
        )

        assertNotNull(decoded)
        assertEquals(2, decoded!!.messages.size)
        assertEquals(
            listOf("user-client-run-persist-1", "assistant-client-run-persist-1"),
            decoded.messages.map { it.id }
        )
        assertEquals("stable identity audit", decoded.messages.first().content)
    }

    @Test
    fun attachmentOnlyUserEchoDerivesCanonicalSnapshotIdentityFromSourceRunId() {
        val state = ChatTimelineState(
            messages = listOf(
                ChatMessage(
                    id = "file-file-attachment-only-1",
                    role = MessageRole.user,
                    content = "android-hidden-attachment-only-fix.txt",
                    contentBlocks = listOf(
                        RelayChatContentBlock(
                            type = "file",
                            attachmentId = "att-attachment-only-1",
                            fileId = "file-attachment-only-1",
                            fileName = "android-hidden-attachment-only-fix.txt",
                            mimeType = "text/plain",
                            downloadUrl = "/api/mobile/files/file-attachment-only-1",
                            sourceRunId = "client-run-attachment-only-1"
                        )
                    ),
                    createdAt = "2026-07-01T01:20:08.452Z",
                    runId = "file-file-attachment-only-1",
                    sortTimestamp = 100.0
                )
            )
        )

        val decoded = TimelinePersistenceMiddleware.decodeSnapshot(
            TimelinePersistenceMiddleware.encodeSnapshot(state)
        )

        assertNotNull(decoded)
        assertEquals(1, decoded!!.messages.size)
        val message = decoded.messages.single()
        assertTrue(message.timelineOrderKey.startsWith("local:client-run-attachment-only-1|30|"))
        assertEquals(
            "local:attachment:att-attachment-only-1",
            message.timelineIdentityKey
        )
        assertEquals("attachment", message.timelineItemKind)
    }
}
