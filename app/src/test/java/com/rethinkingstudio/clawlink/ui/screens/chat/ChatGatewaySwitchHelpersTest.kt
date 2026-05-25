package com.rethinkingstudio.clawlink.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatGatewaySwitchHelpersTest {
    @Test
    fun historyRequestUsesFinalSessionAfterGatewaySwitch() {
        val request = gatewaySwitchHistoryRequest(
            selectedGatewayId = "hermes-gateway",
            currentGatewayId = "hermes-gateway",
            currentSessionKey = "work-session",
            isGatewaySwitchInProgress = false
        )

        assertEquals("hermes-gateway", request?.gatewayId)
        assertEquals("work-session", request?.sessionKey)
    }

    @Test
    fun historyRequestIgnoresStaleGatewayState() {
        val request = gatewaySwitchHistoryRequest(
            selectedGatewayId = "openclaw-gateway",
            currentGatewayId = "hermes-gateway",
            currentSessionKey = "work-session",
            isGatewaySwitchInProgress = false
        )

        assertNull(request)
    }

    @Test
    fun historyRequestIgnoresBlankSession() {
        val request = gatewaySwitchHistoryRequest(
            selectedGatewayId = "openclaw-gateway",
            currentGatewayId = "openclaw-gateway",
            currentSessionKey = " ",
            isGatewaySwitchInProgress = false
        )

        assertNull(request)
    }

    @Test
    fun historyRequestIgnoresGatewaySwitchInProgress() {
        val request = gatewaySwitchHistoryRequest(
            selectedGatewayId = "hermes-gateway",
            currentGatewayId = "hermes-gateway",
            currentSessionKey = "work-session",
            isGatewaySwitchInProgress = true
        )

        assertNull(request)
    }
}
