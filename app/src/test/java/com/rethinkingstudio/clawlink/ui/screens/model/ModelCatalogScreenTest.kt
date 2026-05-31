package com.rethinkingstudio.clawlink.ui.screens.model

import com.rethinkingstudio.clawlink.core.models.gateway.GatewayType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogScreenTest {
    @Test
    fun hermesDefaultModelConfirmationNamesHermesGateway() {
        val message = modelDefaultConfirmationMessage("gpt-5.2", GatewayType.hermes)

        assertTrue(message.contains("Hermes Agent"))
        assertFalse(message.contains("OpenClaw"))
    }

    @Test
    fun openClawDefaultModelConfirmationKeepsOpenClawGateway() {
        val message = modelDefaultConfirmationMessage("gpt-5.2", GatewayType.openclaw)

        assertTrue(message.contains("OpenClaw"))
        assertFalse(message.contains("Hermes Agent"))
    }

    @Test
    fun hermesProcessingOverlayDoesNotSayOpenClaw() {
        val title = modelProcessingTitle(operationsLocked = false, gatewayType = GatewayType.hermes)

        assertTrue(title.contains("Hermes Agent"))
        assertFalse(title.contains("OpenClaw"))
    }
}
