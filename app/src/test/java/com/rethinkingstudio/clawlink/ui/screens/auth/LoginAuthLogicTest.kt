package com.rethinkingstudio.clawlink.ui.screens.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginAuthLogicTest {
    @Test
    fun existingAccountLoginDoesNotRepeatLegalConsent() {
        assertTrue(
            canSubmit(
                isLoading = false,
                isRegisterMode = false,
                waitingForVerification = false,
                name = "",
                email = "user@example.com",
                password = "password",
                verificationCode = "",
                hasAcceptedLegal = false
            )
        )
    }

    @Test
    fun registrationCannotSubmitBeforeLegalConsent() {
        assertFalse(
            canSubmit(
                isLoading = false,
                isRegisterMode = true,
                waitingForVerification = false,
                name = "User",
                email = "user@example.com",
                password = "password",
                verificationCode = "",
                hasAcceptedLegal = false
            )
        )
    }

    @Test
    fun verificationCannotSubmitAfterConsentIsWithdrawn() {
        assertFalse(
            canSubmit(
                isLoading = false,
                isRegisterMode = true,
                waitingForVerification = true,
                name = "User",
                email = "user@example.com",
                password = "password",
                verificationCode = "123456",
                hasAcceptedLegal = false
            )
        )
    }

    @Test
    fun thirdPartyAuthOnlyRequiresConsentWhenCreatingAnAccount() {
        assertTrue(canUseThirdPartyAuth(isLoading = false, isRegisterMode = false, hasAcceptedLegal = false))
        assertFalse(canUseThirdPartyAuth(isLoading = false, isRegisterMode = true, hasAcceptedLegal = false))
        assertFalse(canUseThirdPartyAuth(isLoading = true, isRegisterMode = false, hasAcceptedLegal = true))
        assertTrue(canUseThirdPartyAuth(isLoading = false, isRegisterMode = true, hasAcceptedLegal = true))
    }
}
