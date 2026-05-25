package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.gateway.GatewayType
import com.rethinkingstudio.clawlink.core.network.RelayAPIError
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSessionDeleteHelpersTest {
    @Test
    fun deleteMethodMatchesGatewayType() {
        assertEquals("sessions.delete", chatSessionDeleteRelayMethod(GatewayType.openclaw))
        assertEquals("hermes.sessions.delete", chatSessionDeleteRelayMethod(GatewayType.hermes))
    }

    @Test
    fun deleteCommandParamsMatchIosFallbackShape() {
        val params = buildChatSessionDeleteCommandParams(" session_123 ", deleteTranscript = true)

        assertEquals("session_123", params["key"]!!.jsonPrimitive.content)
        assertTrue(params["deleteTranscript"]!!.jsonPrimitive.boolean)
        assertFalse(params.containsKey("sessionKey"))
        assertFalse(params.containsKey("sessionId"))
    }

    @Test
    fun deleteFallbackHandlesNotFoundAndGatewayTimeoutsOnly() {
        assertTrue(
            shouldFallbackToRelayCommandForSessionDelete(
                RelayAPIError.ServerError(statusCode = 404, errorCode = "not_found")
            )
        )
        assertTrue(
            shouldFallbackToRelayCommandForSessionDelete(
                RelayAPIError.ServerError(statusCode = 502, errorCode = "Error: timeout: Gateway command timed out")
            )
        )
        assertFalse(
            shouldFallbackToRelayCommandForSessionDelete(
                RelayAPIError.ServerError(statusCode = 403, errorCode = "forbidden")
            )
        )
    }
}
