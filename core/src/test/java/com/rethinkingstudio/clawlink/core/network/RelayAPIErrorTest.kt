package com.rethinkingstudio.clawlink.core.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayAPIErrorTest {
    @Test
    fun legalConsentErrorIsMappedToUserFacingCopy() {
        val error = RelayAPIError.fromStatusCode(400, "legal_consent_required")

        assertFalse(error.message == "legal_consent_required")
    }

    @Test
    fun hostErrorsAreMappedToActionableCopy() {
        val error = RelayAPIError.fromStatusCode(502, "host_error")

        assertFalse(error.message == "host_error")
        assertTrue(error.message.orEmpty().contains("ClawConnect Agent"))
    }
}
