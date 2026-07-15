package com.rethinkingstudio.clawlink.core.network

import org.junit.Assert.assertFalse
import org.junit.Test

class RelayAPIErrorTest {
    @Test
    fun legalConsentErrorIsMappedToUserFacingCopy() {
        val error = RelayAPIError.fromStatusCode(400, "legal_consent_required")

        assertFalse(error.message == "legal_consent_required")
    }
}
