package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ChatTimelineDeviceTest {
    @Test
    fun hermesStreamingDeltaMaterializesPartialTextOnDevice() {
        val event = TimelineEventLog.decodeEvent(
            """
            {
              "protocolVersion": 2,
              "eventId": "evt-hermes-delta",
              "eventType": "message.part.delta",
              "turnId": "turn-hermes-stream",
              "runId": "run-hermes-stream",
              "messageId": "assistant-stream",
              "partId": "text",
              "seq": 1,
              "role": "assistant",
              "content": [{ "type": "text", "text": "partial reply" }],
              "timelineOrderKey": "v1|00000000000000000001|50|000000|assistant-stream",
              "timelineIdentityKey": "message:assistant:assistant-stream",
              "timelineItemKind": "message:assistant"
            }
            """.trimIndent()
        )
        assertNotNull(event)

        val state = ChatTimelineReducer.reduce(ChatTimelineState(), event!!)

        assertEquals(MessageState.streaming, state.messages.single().state)
        assertEquals("partial reply", state.messages.single().content)
    }
}
