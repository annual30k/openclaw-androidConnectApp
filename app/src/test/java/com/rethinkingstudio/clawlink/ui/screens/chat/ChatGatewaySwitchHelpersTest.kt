package com.rethinkingstudio.clawlink.ui.screens.chat

import com.rethinkingstudio.clawlink.core.models.gateway.GatewayType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun hermesHistoryDoesNotBlockGatewaySwitchOverlay() {
        assertFalse(gatewaySwitchHistoryBlocksOverlay(GatewayType.hermes))
        assertTrue(gatewaySwitchHistoryBlocksOverlay(GatewayType.openclaw))
    }

    @Test
    fun duplicateInFlightHistoryRequestReleasesSessionSwitchOverlay() {
        val gate = GatewayHistoryRequestGate()
        val request = GatewayHistoryRequest(gatewayId = "hermes-gateway", sessionKey = "work-session")

        assertEquals(GatewayHistoryRequestDecision.StartLoad, gate.begin(request, isSwitchingSession = false))
        assertEquals(
            GatewayHistoryRequestDecision.ReleaseSwitchOverlay,
            gate.begin(request, isSwitchingSession = true)
        )
    }
}
