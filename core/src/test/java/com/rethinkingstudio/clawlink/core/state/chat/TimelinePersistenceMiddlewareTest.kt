package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class TimelinePersistenceMiddlewareTest {
    private val scope = TimelinePersistenceScope(
        relayAccountId = "relay-account-1",
        gatewayId = "gateway-1",
        sessionKey = "main"
    )

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

        val snapshot = TimelinePersistenceMiddleware.buildSnapshot(scope, state, savedAtEpochMs = 123L)
        val encoded = TimelinePersistenceMiddleware.encodeSnapshot(snapshot)
        val decoded = TimelinePersistenceMiddleware.decodeSnapshot(encoded, scope)

        assertEquals(state, decoded?.restoredTimelineState())
    }

    @Test
    fun oldBareTimelineStateCacheIsIgnored() {
        val oldRawSnapshot = """{"messages":[],"activeRunId":null}"""

        assertNull(TimelinePersistenceMiddleware.decodeSnapshot(oldRawSnapshot, scope))
    }

    @Test
    fun snapshotFromAnotherRelayGatewayOrSessionIsIgnored() {
        val snapshot = TimelinePersistenceMiddleware.buildSnapshot(
            scope = scope,
            state = ChatTimelineState(),
            savedAtEpochMs = 123L
        )
        val encoded = TimelinePersistenceMiddleware.encodeSnapshot(snapshot)

        assertNull(
            TimelinePersistenceMiddleware.decodeSnapshot(
                encoded,
                scope.copy(sessionKey = "another-session")
            )
        )
        assertNull(
            TimelinePersistenceMiddleware.decodeSnapshot(
                encoded,
                scope.copy(gatewayId = "another-gateway")
            )
        )
        assertNull(
            TimelinePersistenceMiddleware.decodeSnapshot(
                encoded,
                scope.copy(relayAccountId = "another-relay")
            )
        )
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
            TimelinePersistenceMiddleware.encodeSnapshot(
                TimelinePersistenceMiddleware.buildSnapshot(scope, state, savedAtEpochMs = 123L)
            ),
            scope
        )

        assertNotNull(decoded)
        val restored = decoded!!.restoredTimelineState()
        assertEquals(2, restored.messages.size)
        assertEquals(
            listOf("user-client-run-persist-1", "assistant-client-run-persist-1"),
            restored.messages.map { it.id }
        )
        assertEquals("stable identity audit", restored.messages.first().content)
        assertEquals(2, decoded.pendingMessages.size)
        assertTrue(decoded.confirmedMessages.isEmpty())
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
            TimelinePersistenceMiddleware.encodeSnapshot(
                TimelinePersistenceMiddleware.buildSnapshot(scope, state, savedAtEpochMs = 123L)
            ),
            scope
        )

        assertNotNull(decoded)
        val restored = decoded!!.restoredTimelineState()
        assertEquals(1, restored.messages.size)
        val message = restored.messages.single()
        assertTrue(message.timelineOrderKey.startsWith("local:client-run-attachment-only-1|30|"))
        assertEquals(
            "local:attachment:att-attachment-only-1",
            message.timelineIdentityKey
        )
        assertEquals("attachment", message.timelineItemKind)
    }

    @Test
    fun canonicalHistoryPendingOverlayOutboxAndWatermarkRoundTripIndependently() {
        val confirmed = ChatMessage(
            id = "server-message",
            role = MessageRole.assistant,
            content = "confirmed",
            seq = 41,
            timelineOrderKey = "000041",
            timelineIdentityKey = "server:message",
            timelineItemKind = "message"
        )
        val pending = ChatMessage(
            id = "local-message",
            role = MessageRole.user,
            content = "pending",
            timelineOrderKey = "local:client-1|10|local-message",
            timelineIdentityKey = "local:user:client-1",
            timelineItemKind = "message"
        )
        val outbox = TimelineOutboxEntry(
            kind = TimelineOutboxKind.TEXT,
            clientMessageId = "client-1",
            idempotencyKey = "client-1",
            requestId = "client-1",
            content = "pending",
            createdAtEpochMs = 100L
        )

        val snapshot = TimelinePersistenceMiddleware.buildSnapshot(
            scope = scope,
            state = ChatTimelineState(messages = listOf(confirmed, pending)),
            outbox = listOf(outbox),
            snapshotRevision = "revision-9",
            highWatermark = 41L,
            savedAtEpochMs = 123L
        )
        val decoded = TimelinePersistenceMiddleware.decodeSnapshot(
            TimelinePersistenceMiddleware.encodeSnapshot(snapshot),
            scope
        )!!

        assertEquals(listOf(confirmed), decoded.confirmedMessages)
        assertEquals(listOf(pending), decoded.pendingMessages)
        assertEquals(listOf(outbox), decoded.outbox)
        assertEquals("revision-9", decoded.snapshotRevision)
        assertEquals(41L, decoded.highWatermark)
    }

    @Test
    fun ordinaryMessageSeqIsNeverInferredAsConversationWatermark() {
        val snapshot = TimelinePersistenceMiddleware.buildSnapshot(
            scope = scope,
            state = ChatTimelineState(
                messages = listOf(
                    ChatMessage(
                        id = "page-local-seq",
                        role = MessageRole.assistant,
                        seq = 9_999L,
                        timelineOrderKey = "9999",
                        timelineIdentityKey = "message:page-local-seq",
                        timelineItemKind = "message:assistant"
                    )
                )
            ),
            savedAtEpochMs = 123L
        )

        assertNull(snapshot.highWatermark)
    }

    @Test
    fun confirmedSnapshotWindowIsBoundedWhilePendingOverlayRemainsDurable() {
        val confirmed = (0 until 510).map { index ->
            ChatMessage(
                id = "server-$index",
                role = MessageRole.assistant,
                content = "message-$index",
                timelineOrderKey = index.toString().padStart(6, '0'),
                timelineIdentityKey = "server:message:$index",
                timelineItemKind = "message"
            )
        }
        val pending = ChatMessage(
            id = "local-pending",
            role = MessageRole.user,
            content = "pending",
            timelineOrderKey = "local:pending|10|local-pending",
            timelineIdentityKey = "local:user:pending",
            timelineItemKind = "message"
        )

        val snapshot = TimelinePersistenceMiddleware.buildSnapshot(
            scope = scope,
            state = ChatTimelineState(messages = confirmed + pending),
            savedAtEpochMs = 123L
        )

        assertEquals(500, snapshot.confirmedMessages.size)
        assertEquals("server-10", snapshot.confirmedMessages.first().id)
        assertEquals("server-509", snapshot.confirmedMessages.last().id)
        assertEquals(listOf(pending), snapshot.pendingMessages)
    }

    @Test
    fun jwtTokenRotationKeepsSameRelayAccountScopeStable() {
        val oldToken = jwtToken("""{"sub":"account-42","exp":100}""", signature = "old-signature")
        val refreshedToken = jwtToken("""{"sub":"account-42","exp":200}""", signature = "new-signature")

        assertEquals(
            timelineRelayAccountId("https://relay.example.com/", oldToken),
            timelineRelayAccountId("https://relay.example.com", refreshedToken)
        )
    }

    @Test
    fun relayTwoSegmentTokenRotationKeepsSameRelayAccountScopeStable() {
        val oldToken = relayToken("""{"userId":"account-42","exp":100}""", signature = "old-signature")
        val refreshedToken = relayToken("""{"userId":"account-42","exp":200}""", signature = "new-signature")

        assertEquals(
            timelineRelayAccountId("https://relay.example.com/", oldToken),
            timelineRelayAccountId("https://relay.example.com", refreshedToken)
        )
    }

    @Test
    fun jwtDifferentAccountsOnSameRelayRemainIsolated() {
        val first = jwtToken("""{"userId":"account-a"}""", signature = "signature")
        val second = jwtToken("""{"user_id":"account-b"}""", signature = "signature")

        assertTrue(
            timelineRelayAccountId("https://relay.example.com", first) !=
                timelineRelayAccountId("https://relay.example.com", second)
        )
    }

    @Test
    fun nonJwtTokensUseCredentialDigestFallback() {
        assertTrue(
            timelineRelayAccountId("https://relay.example.com", "opaque-token-a") !=
                timelineRelayAccountId("https://relay.example.com", "opaque-token-b")
        )
    }

    @Test
    fun scopedV7EnvelopeCanBeReadForOneTimeMigrationButV6IsDiscarded() {
        val snapshot = TimelinePersistenceMiddleware.buildSnapshot(
            scope = scope,
            state = ChatTimelineState(),
            highWatermark = 41L,
            savedAtEpochMs = 123L
        )
        val current = TimelinePersistenceMiddleware.encodeSnapshot(snapshot)
        val v7 = current.replaceFirst("\"schemaVersion\":9", "\"schemaVersion\":7")
        val v6 = current.replaceFirst("\"schemaVersion\":9", "\"schemaVersion\":6")

        assertNull(TimelinePersistenceMiddleware.decodeSnapshot(v7, scope)?.highWatermark)
        assertNull(TimelinePersistenceMiddleware.decodeSnapshot(v6, scope))
    }

    @Test
    fun atomicTimelineFileNeverContainsLargeVoiceOutboxPayload() {
        val secretPayload = "A".repeat(64_000)
        val snapshot = TimelinePersistenceMiddleware.buildSnapshot(
            scope = scope,
            state = ChatTimelineState(),
            outbox = listOf(
                TimelineOutboxEntry(
                    kind = TimelineOutboxKind.VOICE,
                    clientMessageId = "voice-1",
                    idempotencyKey = "voice-1",
                    requestId = "voice-1",
                    voice = TimelineOutboxVoice(
                        fileName = "voice.m4a",
                        mimeType = "audio/mp4",
                        sizeBytes = 48_000,
                        contentBase64 = secretPayload
                    ),
                    createdAtEpochMs = 1L
                )
            ),
            savedAtEpochMs = 123L
        )

        val diskJson = TimelinePersistenceMiddleware.encodeDiskSnapshot(snapshot)

        assertFalse(diskJson.contains(secretPayload))
        assertFalse(diskJson.contains("voice-1"))
    }

    private fun jwtToken(payload: String, signature: String): String {
        fun encode(value: String): String = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(value.toByteArray(Charsets.UTF_8))
        return "${encode("""{"alg":"none"}""")}.${encode(payload)}.${encode(signature)}"
    }

    private fun relayToken(payload: String, signature: String): String {
        fun encode(value: String): String = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(value.toByteArray(Charsets.UTF_8))
        return "${encode(payload)}.${encode(signature)}"
    }
}
