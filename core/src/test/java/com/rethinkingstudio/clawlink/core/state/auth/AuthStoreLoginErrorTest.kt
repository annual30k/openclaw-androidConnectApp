package com.rethinkingstudio.clawlink.core.state.auth

import com.rethinkingstudio.clawlink.core.network.RelayAPIError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthStoreLoginErrorTest {
    @Test
    fun privateRelayEmailVerificationErrorExplainsServerConfiguration() {
        val error = RelayAPIError.ServerError(
            statusCode = 403,
            errorCode = "email_verification_required"
        )

        val message = loginErrorMessage(error, isPrivateDeployment = true)

        assertTrue(message.contains("EMAIL_VERIFICATION_REQUIRED=false"))
    }

    @Test
    fun hostedRelayKeepsStandardEmailVerificationMessage() {
        val error = RelayAPIError.ServerError(
            statusCode = 403,
            errorCode = "email_verification_required"
        )

        assertEquals(error.message, loginErrorMessage(error, isPrivateDeployment = false))
    }
}
