package com.rethinkingstudio.clawlink.core.state.auth

import com.rethinkingstudio.clawlink.core.domain.CredentialStore
import com.rethinkingstudio.clawlink.core.models.SessionCredentials
import com.rethinkingstudio.clawlink.core.network.RelayAPIClient
import com.rethinkingstudio.clawlink.core.network.RelayAPIError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AuthState(
    val isLoggedIn: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val accessToken: String = "",
    val relayBaseUrl: String = "",
    val isPaired: Boolean = false,
    val pendingVerificationEmail: String? = null,
    val pendingVerificationExpiresAt: String? = null,
    val suggestRegister: Boolean = false
)

class AuthStore(
    private val apiClient: RelayAPIClient,
    private val credentialStore: CredentialStore
) {
    companion object {
        const val DEFAULT_RELAY_SERVER_URL = "https://clawlinks.cn"
    }

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    val isLoggedIn: Boolean get() = _state.value.isLoggedIn

    suspend fun tryRestoreSession() {
        val creds = credentialStore.loadCredentials()
        if (creds != null && creds.accessToken.isNotBlank()) {
            apiClient.configure(creds)
            _state.value = AuthState(
                isLoggedIn = true,
                accessToken = creds.accessToken,
                relayBaseUrl = creds.relayBaseURL,
                isPaired = true
            )
        }
    }

    suspend fun login(baseUrl: String, email: String, password: String, deviceId: String): Boolean {
        val validationError = validateLogin(email, password)
        if (validationError != null) {
            _state.value = _state.value.copy(errorMessage = validationError)
            return false
        }

        _state.value = _state.value.copy(isLoading = true, errorMessage = null, suggestRegister = false)
        return try {
            apiClient.configure(SessionCredentials("", baseUrl))
            val creds = apiClient.authenticate(email, password, deviceId)
            credentialStore.saveCredentials(creds)
            apiClient.configure(creds)
            _state.value = AuthState(isLoggedIn = true, isPaired = true, accessToken = creds.accessToken, relayBaseUrl = creds.relayBaseURL)
            true
        } catch (e: RelayAPIError) {
            val shouldSuggestRegister = e is RelayAPIError.ServerError && e.errorCode == "user_not_registered"
            _state.value = _state.value.copy(
                isLoading = false,
                errorMessage = e.message,
                suggestRegister = shouldSuggestRegister
            )
            false
        } catch (e: Exception) {
            _state.value = _state.value.copy(isLoading = false, errorMessage = "Connection failed: ${e.message}")
            false
        }
    }

    suspend fun register(baseUrl: String, name: String, email: String, password: String, deviceId: String, isPrivateDeployment: Boolean = false): Boolean {
        val validationError = validateRegistration(name, email, password)
        if (validationError != null) {
            _state.value = _state.value.copy(errorMessage = validationError)
            return false
        }

        _state.value = _state.value.copy(isLoading = true, errorMessage = null, suggestRegister = false)
        return try {
            apiClient.configure(SessionCredentials("", baseUrl))
            when (val result = apiClient.register(name, email, password, deviceId)) {
                is RelayAPIClient.RegistrationResult.Authenticated -> {
                    val creds = result.credentials
                    credentialStore.saveCredentials(creds)
                    apiClient.configure(creds)
                    _state.value = AuthState(isLoggedIn = true, isPaired = true, accessToken = creds.accessToken, relayBaseUrl = creds.relayBaseURL)
                    true
                }
                is RelayAPIClient.RegistrationResult.VerificationRequired -> {
                    if (isPrivateDeployment) {
                        _state.value = _state.value.copy(
                            isLoading = false,
                            pendingVerificationEmail = null,
                            pendingVerificationExpiresAt = null,
                            errorMessage = "This private relay still requires email verification. Set EMAIL_VERIFICATION_REQUIRED=false and restart the relay."
                        )
                    } else {
                        _state.value = _state.value.copy(
                            isLoading = false,
                            pendingVerificationEmail = result.email,
                            pendingVerificationExpiresAt = result.expiresAt,
                            errorMessage = null
                        )
                    }
                    false
                }
            }
        } catch (e: Exception) {
            _state.value = _state.value.copy(isLoading = false, errorMessage = e.message ?: "Registration failed")
            false
        }
    }

    suspend fun verifyRegistrationEmail(baseUrl: String, code: String, deviceId: String): Boolean {
        val email = _state.value.pendingVerificationEmail
        val normalizedCode = code.trim()
        if (email.isNullOrBlank()) {
            _state.value = _state.value.copy(errorMessage = "Submit registration details before entering the email verification code.")
            return false
        }
        if (!Regex("^\\d{6}$").matches(normalizedCode)) {
            _state.value = _state.value.copy(errorMessage = "Enter the 6-digit verification code.")
            return false
        }

        _state.value = _state.value.copy(isLoading = true, errorMessage = null)
        return try {
            apiClient.configure(SessionCredentials("", baseUrl))
            val creds = apiClient.verifyEmail(email, normalizedCode, deviceId)
            credentialStore.saveCredentials(creds)
            apiClient.configure(creds)
            _state.value = AuthState(isLoggedIn = true, isPaired = true, accessToken = creds.accessToken, relayBaseUrl = creds.relayBaseURL)
            true
        } catch (e: Exception) {
            _state.value = _state.value.copy(isLoading = false, errorMessage = e.message ?: "Verification failed")
            false
        }
    }

    suspend fun pairGateway(gatewayId: String?, accessCode: String, deviceId: String): Boolean {
        _state.value = _state.value.copy(isLoading = true, errorMessage = null)
        return try {
            val creds = apiClient.pairGateway(gatewayId, accessCode, deviceId)
            credentialStore.saveCredentials(creds)
            apiClient.configure(creds)
            _state.value = _state.value.copy(isLoading = false, isLoggedIn = true, isPaired = true, accessToken = creds.accessToken)
            true
        } catch (e: Exception) {
            _state.value = _state.value.copy(isLoading = false, errorMessage = e.message ?: "Pairing failed")
            false
        }
    }

    suspend fun logout() {
        credentialStore.clearCredentials()
        apiClient.clearSession()
        _state.value = AuthState()
    }

    suspend fun deleteAccount(): Boolean {
        _state.value = _state.value.copy(isLoading = true, errorMessage = null)
        return try {
            apiClient.deleteAccount()
            credentialStore.clearCredentials()
            apiClient.clearSession()
            _state.value = AuthState()
            true
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                isLoading = false,
                errorMessage = e.message ?: "Delete account failed"
            )
            false
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null, suggestRegister = false)
    }

    fun clearPendingVerification() {
        _state.value = _state.value.copy(pendingVerificationEmail = null, pendingVerificationExpiresAt = null)
    }

    fun consumeRegisterSuggestion() {
        _state.value = _state.value.copy(suggestRegister = false)
    }

    private fun validateLogin(email: String, password: String): String? {
        if (!isValidEmail(email)) return "Enter a valid email address."
        if (password.isEmpty()) return "Enter your password."
        return null
    }

    private fun validateRegistration(name: String, email: String, password: String): String? {
        if (name.trim().isEmpty()) return "Enter your name."
        if (!isValidEmail(email)) return "Enter a valid email address."
        if (password.length < 8) return "Password must be at least 8 characters."
        return null
    }

    private fun isValidEmail(email: String): Boolean {
        val trimmed = email.trim()
        return Regex("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", RegexOption.IGNORE_CASE).matches(trimmed)
    }
}
