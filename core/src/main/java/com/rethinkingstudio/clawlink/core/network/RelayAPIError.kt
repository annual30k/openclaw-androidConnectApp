package com.rethinkingstudio.clawlink.core.network

sealed class RelayAPIError : Exception() {
    data object NotConfigured : RelayAPIError() {
        override val message: String get() = "Relay client not configured"
    }

    data object InvalidResponse : RelayAPIError() {
        override val message: String get() = "Invalid server response"
    }

    data class ServerError(
        val statusCode: Int,
        val errorCode: String,
        val remainingAttempts: Int? = null,
        val retryAfterSeconds: Int? = null
    ) : RelayAPIError() {
        private val userMessage: String
            get() = when (errorCode) {
                "valid_email_required" -> "Enter a valid email address."
                "password_too_short" -> "Password must be at least 8 characters."
                "email_already_registered" -> "This email is already registered. Sign in instead."
                "email_verification_required" -> "Verify your email before signing in."
                "user_not_registered" -> "This email is not registered. Register first."
                "invalid_credentials" -> remainingAttempts?.let { "Email or password is incorrect. $it attempts remaining." }
                    ?: "Email or password is incorrect."
                "rate_limited" -> retryAfterSeconds?.let { "Too many attempts. Try again in ${it}s." }
                    ?: "Too many attempts. Try again later."
                "verification_code_required" -> "Enter the 6-digit verification code."
                "verification_code_not_found" -> "Verification code was not found. Request a new code."
                "verification_code_expired" -> "Verification code expired. Request a new code."
                "verification_code_rate_limited" -> "Too many verification attempts. Request a new code later."
                "verification_code_invalid" -> remainingAttempts?.let { "Verification code is incorrect. $it attempts remaining." }
                    ?: "Verification code is incorrect."
                "email_already_verified" -> "This email is already verified. Sign in instead."
                "verification_email_failed" -> "Failed to send verification email. Try again later."
                else -> errorCode
            }
        override val message: String get() = userMessage
    }

    data class NetworkError(val exception: Throwable) : RelayAPIError() {
        override val message: String get() = "Network error: ${exception.message}"
        override val cause: Throwable get() = exception
    }

    data object Unauthorized : RelayAPIError() {
        override val message: String get() = "Unauthorized - please log in again"
    }

    data class RateLimited(val retryAfterSeconds: Int?) : RelayAPIError() {
        override val message: String get() = "Rate limited, retry after ${retryAfterSeconds ?: "?"}s"
    }

    data object GatewayNotFound : RelayAPIError() {
        override val message: String get() = "Gateway not found"
    }

    data class PairingFailed(val errorMessage: String) : RelayAPIError() {
        override val message: String get() = "Pairing failed: $errorMessage"
    }

    companion object {
        fun fromStatusCode(
            code: Int,
            body: String?,
            remainingAttempts: Int? = null,
            retryAfterSeconds: Int? = null
        ): RelayAPIError {
            val errorCode = body ?: "unknown_error"
            return when (code) {
                401, 403 -> if (errorCode == "unauthorized") Unauthorized else ServerError(code, errorCode, remainingAttempts, retryAfterSeconds)
                404 -> if (errorCode == "gateway_not_found") GatewayNotFound else ServerError(code, errorCode, remainingAttempts, retryAfterSeconds)
                429 -> if (errorCode == "rate_limited") ServerError(code, errorCode, remainingAttempts, retryAfterSeconds) else RateLimited(retryAfterSeconds)
                else -> ServerError(code, errorCode, remainingAttempts, retryAfterSeconds)
            }
        }
    }
}
