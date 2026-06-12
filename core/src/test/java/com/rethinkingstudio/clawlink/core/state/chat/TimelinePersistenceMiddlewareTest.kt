package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
