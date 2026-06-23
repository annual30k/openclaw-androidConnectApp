package com.rethinkingstudio.clawlink.core.state.chat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelinePayloadScopeResolverTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun usesTopLevelSessionKeyAndEnvelopeGatewayForReplaySnapshots() {
        val scope = resolveTimelinePayloadScope(
            envelope = jsonObject("""{"gatewayId":"gw_1"}"""),
            payload = jsonObject(
                """
                {
                  "sessionKey": "main",
                  "timelineSnapshot": {
                    "protocolVersion": 2,
                    "eventType": "history.snapshot.page",
                    "gatewayId": "gw_1",
                    "sessionKey": "other"
                  }
                }
                """.trimIndent()
            ),
            currentGatewayId = "gw_1",
            currentSessionKey = "main"
        )

        assertEquals("gw_1", scope.gatewayId)
        assertEquals("main", scope.sessionKey)
        assertTrue(scope.hasSessionKey)
    }

    @Test
    fun fallsBackToCanonicalTimelineEventScopeWhenTopLevelMetadataIsMissing() {
        val scope = resolveTimelinePayloadScope(
            envelope = jsonObject("""{}"""),
            payload = jsonObject(
                """
                {
                  "timelineEvents": [
                    {
                      "protocolVersion": 2,
                      "eventType": "message.completed",
                      "gatewayId": "gw_2",
                      "sessionKey": "agent:main:main",
                      "runId": "run_1"
                    }
                  ]
                }
                """.trimIndent()
            ),
            currentGatewayId = "gw_2",
            currentSessionKey = "main"
        )

        assertEquals("gw_2", scope.gatewayId)
        assertEquals("main", scope.sessionKey)
        assertTrue(sameSessionKey(scope.sessionKey, "main"))
    }

    @Test
    fun marksMissingSessionAsNotRoutable() {
        val scope = resolveTimelinePayloadScope(
            envelope = jsonObject("""{"gatewayId":"gw_1"}"""),
            payload = jsonObject("""{"timelineEvents":[]}"""),
            currentGatewayId = "gw_1",
            currentSessionKey = "main"
        )

        assertEquals("gw_1", scope.gatewayId)
        assertEquals("main", scope.sessionKey)
        assertFalse(scope.hasSessionKey)
    }

    private fun jsonObject(raw: String): JsonObject =
        json.parseToJsonElement(raw) as JsonObject
}
