package com.rethinkingstudio.clawlink.ui.screens.settings

import com.rethinkingstudio.clawlink.core.models.chat.ChatSessionItem
import com.rethinkingstudio.clawlink.core.models.gateway.GatewayType
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.ui.screens.chat.showsModelPickerForGateway
import com.rethinkingstudio.clawlink.ui.screens.chat.showsSkillExpansionControlsForGateway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HermesParityPresentationTest {
    @Test
    fun hermesMainSessionCanBeDeletedLikeIos() {
        val session = ChatSessionItem(sessionKey = "main")

        assertTrue(
            canDeleteSession(
                session = session,
                gatewayId = "gateway-1",
                gatewayType = GatewayType.hermes,
                operationsLocked = false,
                isStreaming = false,
                isStoppingRun = false,
                isRefreshing = false,
                deletingKey = null,
                role = null
            )
        )
    }

    @Test
    fun openClawMainSessionRemainsProtected() {
        val session = ChatSessionItem(sessionKey = "main")

        assertFalse(
            canDeleteSession(
                session = session,
                gatewayId = "gateway-1",
                gatewayType = GatewayType.openclaw,
                operationsLocked = false,
                isStreaming = false,
                isStoppingRun = false,
                isRefreshing = false,
                deletingKey = null,
                role = null
            )
        )
    }

    @Test
    fun sessionSwitchingRemainsAvailableWhileCurrentSessionIsStreaming() {
        assertTrue(
            canSwitchSession(
                gatewayId = "gateway-1",
                operationsLocked = false,
                isStreaming = true,
                isStoppingRun = false
            )
        )
    }

    @Test
    fun hermesBackupDraftUsesZipArchiveName() {
        val draft = createInitialDraft(isHermesGateway = true)

        assertTrue(draft.filename.startsWith("hermes-backup-"))
        assertTrue(draft.filename.endsWith(".zip"))
    }

    @Test
    fun openClawBackupDraftUsesJsonName() {
        val draft = createInitialDraft(isHermesGateway = false)

        assertTrue(draft.filename.startsWith("openclaw-"))
        assertTrue(draft.filename.endsWith(".json"))
    }

    @Test
    fun hermesBackupStorageFallbackMatchesIos() {
        assertTrue(backupStorageLocation(isHermesGateway = true) == "~/hermes-backup-*.zip")
    }

    @Test
    fun openClawBackupStorageFallbackMatchesIos() {
        assertTrue(backupStorageLocation(isHermesGateway = false) == "~/.clawconnect/backups/openclaw")
    }

    @Test
    fun hermesRestartTargetsClawConnectProfileAgent() {
        assertEquals("hermes.agent.restart", maintenanceRestartMethod(GatewayType.hermes, isRemote = false))
        assertEquals("hermes.agent.restart", maintenanceRestartMethod(GatewayType.hermes, isRemote = true))
        assertEquals("clawpilot.gateway.restart", maintenanceRestartMethod(GatewayType.openclaw, isRemote = false))
        assertEquals("clawpilot.gateway.remoteRestart", maintenanceRestartMethod(GatewayType.openclaw, isRemote = true))
    }

    @Test
    fun hermesMaintenanceSuggestionUsesHermesBackupText() {
        assertEquals(R.string.maintenance_suggestion_body_hermes, maintenanceSuggestionBodyRes(GatewayType.hermes))
        assertEquals(R.string.maintenance_suggestion_body, maintenanceSuggestionBodyRes(GatewayType.openclaw))
    }

    @Test
    fun hermesAdvancedRemoteRestartDoesNotClaimHostReboot() {
        assertEquals(R.string.advanced_remote_restart_detail_hermes, advancedRemoteRestartDetailRes(GatewayType.hermes))
        assertEquals(R.string.advanced_remote_restart_detail, advancedRemoteRestartDetailRes(GatewayType.openclaw))
    }

    @Test
    fun hermesComposerKeepsSkillExpansionAndModelPicker() {
        assertTrue(showsSkillExpansionControlsForGateway(GatewayType.hermes))
        assertTrue(showsModelPickerForGateway(GatewayType.hermes))
    }

    @Test
    fun openClawComposerKeepsSkillExpansionAndModelPicker() {
        assertTrue(showsSkillExpansionControlsForGateway(GatewayType.openclaw))
        assertTrue(showsModelPickerForGateway(GatewayType.openclaw))
    }
}
