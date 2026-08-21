package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.gateway.GatewayType
import com.rethinkingstudio.clawlink.core.network.RelayAPIClient
import com.rethinkingstudio.clawlink.core.network.transport.RelayWebSocketClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

internal class ChatSessionDeletionCoordinator(
    private val apiClient: RelayAPIClient,
    private val wsClient: RelayWebSocketClient,
    private val sessionSelectionStore: ChatSessionSelectionStore?,
    private val getState: () -> ChatState,
    private val setState: (ChatState) -> Unit,
    private val connectWebSocket: () -> Unit,
    private val clearSessionCaches: (String, String) -> Unit,
    private val persistSelectedSession: (String, String) -> Unit
) {
    suspend fun deleteSession(
        gatewayId: String,
        sessionKey: String,
        deleteTranscript: Boolean,
        gatewayType: GatewayType
    ): Boolean {
        val normalizedGatewayId = gatewayId.trim()
        val normalizedSessionKey = sessionKey.trim().ifBlank { defaultSessionKey }
        if (normalizedGatewayId.isBlank() || normalizedSessionKey.isBlank()) return false

        val apiFailure = try {
            if (apiClient.deleteChatSession(normalizedGatewayId, normalizedSessionKey, deleteTranscript)) {
                applyDeletedSessionLocally(normalizedGatewayId, normalizedSessionKey)
                return true
            }
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!shouldFallbackToRelayCommandForSessionDelete(e)) {
                throw e
            }
            e
        }

        if (confirmDeletedSession(normalizedGatewayId, normalizedSessionKey)) {
            applyDeletedSessionLocally(normalizedGatewayId, normalizedSessionKey)
            return true
        }

        android.util.Log.w("ChatStore", "Falling back to relay command for chat session delete", apiFailure)
        connectWebSocket()
        wsClient.executeCommand(
            gatewayId = normalizedGatewayId,
            method = chatSessionDeleteRelayMethod(gatewayType),
            params = buildChatSessionDeleteCommandParams(normalizedSessionKey, deleteTranscript)
        )
        delay(150)

        if (confirmDeletedSession(normalizedGatewayId, normalizedSessionKey)) {
            applyDeletedSessionLocally(normalizedGatewayId, normalizedSessionKey)
            return true
        }
        return false
    }

    private suspend fun confirmDeletedSession(gatewayId: String, sessionKey: String): Boolean {
        repeat(4) { attempt ->
            val sessions = try {
                retryOnceOnTransientFailure(operationName = "chat sessions after delete for $gatewayId") {
                    apiClient.fetchChatSessions(gatewayId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("ChatStore", "Failed to confirm deleted chat session for $gatewayId/$sessionKey", e)
                null
            }

            if (sessions != null) {
                val current = getState()
                setState(current.copy(sessions = sessions, currentGatewayId = gatewayId, errorMessage = null))
                if (sessions.none { sameSessionKey(it.sessionKey, sessionKey) }) {
                    return true
                }
            }

            if (attempt < 3) {
                delay(200)
            }
        }
        return false
    }

    private fun applyDeletedSessionLocally(gatewayId: String, sessionKey: String) {
        val update = deletedSessionLocalUpdate(getState(), gatewayId, sessionKey)
        setState(update.state)
        clearSessionCaches(gatewayId, sessionKey)
        sessionSelectionStore?.clear(gatewayId, sessionKey)
        sessionSelectionStore?.clearSyncCursor(gatewayId, sessionKey)
        update.nextSessionKeyToPersist?.let { nextSessionKey ->
            persistSelectedSession(gatewayId, nextSessionKey)
        }
    }
}
