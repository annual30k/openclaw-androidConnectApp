package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatSessionItem
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatSessionSelectionHelpersTest {
    @Test
    fun treatsRelayGatewayCommandTimeoutAsTransientLoadFailure() {
        assertEquals(true, isTransientGatewayLoadFailureMessage("Error: timeout: Gateway command timed out"))
    }

    @Test
    fun treatsPendingGatewayHeartbeatAsTransientLoadFailure() {
        assertEquals(true, isTransientGatewayLoadFailureMessage("Error: gateway_unavailable: Gateway heartbeat is pending"))
    }

    @Test
    fun hidesTransientGatewayLoadFailureFromUserVisibleError() {
        val message = visibleGatewayLoadErrorMessage(
            isTransientLoadFailure = true,
            rawMessage = "Error: timeout: Gateway command timed out"
        )

        assertEquals(null, message)
    }

    @Test
    fun selectsMostRecentFetchedSessionWhenPersistedSessionIsStale() {
        val selected = selectSessionKeyAfterLoad(
            sessions = listOf(
                ChatSessionItem(sessionKey = "main", lastActivityAt = "2026-05-24T14:41:00Z"),
                ChatSessionItem(sessionKey = "session_recent", lastActivityAt = "2026-05-24T14:40:00Z")
            ),
            currentSessionKey = "session_old_empty",
            persistedSessionKey = "session_old_empty",
            shouldKeepCurrent = false
        )

        assertEquals("main", selected)
    }

    @Test
    fun selectsMostRecentFetchedSessionWhenPersistedSessionIsNotFirst() {
        val selected = selectSessionKeyAfterLoad(
            sessions = listOf(
                ChatSessionItem(sessionKey = "main", lastActivityAt = "2026-05-24T14:41:00Z"),
                ChatSessionItem(sessionKey = "session_saved", lastActivityAt = "2026-05-24T14:40:00Z")
            ),
            currentSessionKey = "main",
            persistedSessionKey = "session_saved",
            shouldKeepCurrent = false
        )

        assertEquals("main", selected)
    }

    @Test
    fun keepsCurrentSessionWhenAllowed() {
        val selected = selectSessionKeyAfterLoad(
            sessions = listOf(ChatSessionItem(sessionKey = "main", lastActivityAt = null)),
            currentSessionKey = "active",
            persistedSessionKey = "main",
            shouldKeepCurrent = true
        )

        assertEquals("active", selected)
    }

    @Test
    fun keepsSwitchingSessionWhenItExistsInFetchedSessions() {
        val shouldKeep = shouldKeepCurrentSessionAfterLoad(
            sessions = listOf(
                ChatSessionItem(sessionKey = "main", lastActivityAt = "2026-05-24T14:41:00Z"),
                ChatSessionItem(sessionKey = "session_selected", lastActivityAt = "2026-05-24T14:40:00Z")
            ),
            currentSessionKey = "session_selected",
            hasCurrentMessages = false,
            isSwitchingSession = true,
            isNewGateway = false
        )

        assertEquals(true, shouldKeep)
    }

    @Test
    fun doesNotKeepSwitchingSessionWhenItIsStale() {
        val shouldKeep = shouldKeepCurrentSessionAfterLoad(
            sessions = listOf(
                ChatSessionItem(sessionKey = "main", lastActivityAt = "2026-05-24T14:41:00Z")
            ),
            currentSessionKey = "session_deleted",
            hasCurrentMessages = false,
            isSwitchingSession = true,
            isNewGateway = false
        )

        assertEquals(false, shouldKeep)
    }

    @Test
    fun doesNotKeepEmptyCurrentSessionWhenItIsNotMostRecentFetchedSession() {
        val shouldKeep = shouldKeepCurrentSessionAfterLoad(
            sessions = listOf(
                ChatSessionItem(sessionKey = "main", lastActivityAt = "2026-05-24T14:41:00Z"),
                ChatSessionItem(sessionKey = "session_old_empty", lastActivityAt = "2026-05-24T14:40:00Z")
            ),
            currentSessionKey = "session_old_empty",
            hasCurrentMessages = false,
            isSwitchingSession = false,
            isNewGateway = false
        )

        assertEquals(false, shouldKeep)
    }

    @Test
    fun keepsEmptyCurrentSessionWhenItIsMostRecentFetchedSession() {
        val shouldKeep = shouldKeepCurrentSessionAfterLoad(
            sessions = listOf(
                ChatSessionItem(sessionKey = "session_new", lastActivityAt = "2026-05-24T14:41:00Z"),
                ChatSessionItem(sessionKey = "main", lastActivityAt = "2026-05-24T14:40:00Z")
            ),
            currentSessionKey = "session_new",
            hasCurrentMessages = false,
            isSwitchingSession = false,
            isNewGateway = false
        )

        assertEquals(true, shouldKeep)
    }
}
