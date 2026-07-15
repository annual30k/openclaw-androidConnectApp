package com.rethinkingstudio.clawlink.core.network.dto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRequestTest {
    private val json = Json

    @Test
    fun registrationCarriesCurrentLegalConsentAndAndroidPlatform() {
        val encoded = json.encodeToString(
            AuthRequest(
                name = "User",
                email = "user@example.com",
                password = "password",
                deviceId = "android-device",
                legalConsent = LegalConsentRequest.currentAccepted()
            )
        )

        assertTrue(encoded.contains("\"platform\":\"android\""))
        assertTrue(encoded.contains("\"accepted\":true"))
        assertTrue(encoded.contains("\"termsVersion\":\"2026-07-14\""))
        assertTrue(encoded.contains("\"privacyVersion\":\"2026-07-14\""))
    }

    @Test
    fun existingAccountLoginDoesNotClaimNewLegalConsent() {
        val encoded = json.encodeToString(
            AuthRequest(
                email = "user@example.com",
                password = "password",
                deviceId = "android-device"
            )
        )

        assertFalse(encoded.contains("legalConsent"))
        assertEquals("android", AuthRequest(email = "user@example.com", password = "password", deviceId = "device").platform)
    }
}
