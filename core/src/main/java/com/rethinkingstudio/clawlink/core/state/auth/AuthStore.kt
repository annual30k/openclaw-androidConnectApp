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
    val isPaired: Boolean = false
)

class AuthStore(
    private val apiClient: RelayAPIClient,
    private val credentialStore: CredentialStore
) {
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
        _state.value = _state.value.copy(isLoading = true, errorMessage = null)
        return try {
            apiClient.configure(SessionCredentials("", baseUrl))
            val creds = apiClient.authenticate(email, password, deviceId)
            credentialStore.saveCredentials(creds)
            apiClient.configure(creds)
            _state.value = AuthState(isLoggedIn = true, accessToken = creds.accessToken, relayBaseUrl = creds.relayBaseURL)
            true
        } catch (e: RelayAPIError) {
            _state.value = _state.value.copy(isLoading = false, errorMessage = e.message)
            false
        } catch (e: Exception) {
            _state.value = _state.value.copy(isLoading = false, errorMessage = "Connection failed: ${e.message}")
            false
        }
    }

    suspend fun register(baseUrl: String, name: String, email: String, password: String, deviceId: String): Boolean {
        _state.value = _state.value.copy(isLoading = true, errorMessage = null)
        return try {
            apiClient.configure(SessionCredentials("", baseUrl))
            val creds = apiClient.register(name, email, password, deviceId)
            credentialStore.saveCredentials(creds)
            apiClient.configure(creds)
            _state.value = AuthState(isLoggedIn = true, accessToken = creds.accessToken, relayBaseUrl = creds.relayBaseURL)
            true
        } catch (e: Exception) {
            _state.value = _state.value.copy(isLoading = false, errorMessage = e.message ?: "Registration failed")
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

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }
}
