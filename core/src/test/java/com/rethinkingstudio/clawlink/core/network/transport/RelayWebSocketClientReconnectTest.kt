package com.rethinkingstudio.clawlink.core.network.transport

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayWebSocketClientReconnectTest {
    @Test
    fun reconnectingStateDoesNotSuppressSameEndpointConnect() {
        val ignored = shouldIgnoreRelayWsConnectRequest(
            currentBaseUrl = "http://127.0.0.1:8080",
            currentAccessToken = "token",
            nextBaseUrl = "http://127.0.0.1:8080",
            nextAccessToken = "token",
            isConnected = false,
            connectionState = WsConnectionState.reconnecting
        )

        assertFalse(ignored)
    }

    @Test
    fun connectedAndConnectingSameEndpointStillSuppressDuplicateConnects() {
        assertTrue(
            shouldIgnoreRelayWsConnectRequest(
                currentBaseUrl = "http://127.0.0.1:8080",
                currentAccessToken = "token",
                nextBaseUrl = "http://127.0.0.1:8080",
                nextAccessToken = "token",
                isConnected = true,
                connectionState = WsConnectionState.connected
            )
        )
        assertTrue(
            shouldIgnoreRelayWsConnectRequest(
                currentBaseUrl = "http://127.0.0.1:8080",
                currentAccessToken = "token",
                nextBaseUrl = "http://127.0.0.1:8080",
                nextAccessToken = "token",
                isConnected = false,
                connectionState = WsConnectionState.connecting
            )
        )
    }

    @Test
    fun queuedSendCanForceReconnectFromReconnectingState() {
        assertTrue(shouldStartRelayWsReconnectNow(WsConnectionState.reconnecting))
        assertTrue(shouldStartRelayWsReconnectNow(WsConnectionState.disconnected))
        assertFalse(shouldStartRelayWsReconnectNow(WsConnectionState.connecting))
    }
}
