package com.rethinkingstudio.clawlink.core.network

sealed class RelayAPIError : Exception() {
    data object NotConfigured : RelayAPIError() {
        override val message: String get() = "Relay client not configured"
    }

    data object InvalidResponse : RelayAPIError() {
        override val message: String get() = "Invalid server response"
    }

    data class ServerError(val statusCode: Int, val errorMessage: String) : RelayAPIError() {
        override val message: String get() = "Server error ($statusCode): $errorMessage"
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
        fun fromStatusCode(code: Int, body: String?): RelayAPIError {
            return when (code) {
                401, 403 -> Unauthorized
                404 -> GatewayNotFound
                429 -> RateLimited(null)
                in 500..599 -> ServerError(code, body ?: "Unknown server error")
                else -> ServerError(code, body ?: "Unknown error")
            }
        }
    }
}
