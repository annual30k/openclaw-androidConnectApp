package com.rethinkingstudio.clawlink.core.state.auth

import com.rethinkingstudio.clawlink.core.domain.CredentialStore
import com.rethinkingstudio.clawlink.core.models.SessionCredentials
import com.rethinkingstudio.clawlink.core.network.RelayAPIClient
import com.rethinkingstudio.clawlink.core.network.RelayAPIError
import com.rethinkingstudio.clawlink.core.network.dto.LegalConsentRequest
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
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
            _state.value = _state.value.copy(isLoading = false, errorMessage = choose("Connection failed: ${e.message}", "连接失败：${e.message}"))
            false
        }
    }

    suspend fun register(
        baseUrl: String,
        name: String,
        email: String,
        password: String,
        deviceId: String,
        legalConsent: LegalConsentRequest,
        isPrivateDeployment: Boolean = false
    ): Boolean {
        val validationError = validateRegistration(name, email, password)
        if (validationError != null) {
            _state.value = _state.value.copy(errorMessage = validationError)
            return false
        }

        _state.value = _state.value.copy(isLoading = true, errorMessage = null, suggestRegister = false)
        return try {
            apiClient.configure(SessionCredentials("", baseUrl))
            when (val result = apiClient.register(name, email, password, deviceId, legalConsent)) {
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
                            errorMessage = choose(
                                "This private relay still requires email verification. Set EMAIL_VERIFICATION_REQUIRED=false and restart the relay.",
                                "当前私有 Relay 仍要求邮箱验证。请设置 EMAIL_VERIFICATION_REQUIRED=false 并重启 Relay。"
                            )
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
            _state.value = _state.value.copy(isLoading = false, errorMessage = e.message ?: choose("Registration failed", "注册失败"))
            false
        }
    }

    suspend fun verifyRegistrationEmail(baseUrl: String, code: String, deviceId: String): Boolean {
        val email = _state.value.pendingVerificationEmail
        val normalizedCode = code.trim()
        if (email.isNullOrBlank()) {
            _state.value = _state.value.copy(errorMessage = choose("Submit registration details before entering the email verification code.", "请先提交注册信息，再输入邮箱验证码。"))
            return false
        }
        if (!Regex("^\\d{6}$").matches(normalizedCode)) {
            _state.value = _state.value.copy(errorMessage = choose("Enter the 6-digit verification code.", "请输入 6 位验证码。"))
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
            _state.value = _state.value.copy(isLoading = false, errorMessage = e.message ?: choose("Verification failed", "验证失败"))
            false
        }
    }

    suspend fun requestPasswordReset(baseUrl: String, email: String, deviceId: String): Boolean {
        if (!isValidEmail(email)) {
            _state.value = _state.value.copy(errorMessage = choose("Enter a valid email address.", "请输入有效邮箱地址。"))
            return false
        }

        _state.value = _state.value.copy(isLoading = true, errorMessage = null)
        return try {
            apiClient.configure(SessionCredentials("", baseUrl))
            apiClient.requestPasswordReset(email.trim(), deviceId)
            _state.value = _state.value.copy(isLoading = false, errorMessage = null)
            true
        } catch (e: Exception) {
            _state.value = _state.value.copy(isLoading = false, errorMessage = e.message ?: choose("Failed to send verification code.", "验证码发送失败。"))
            false
        }
    }

    suspend fun confirmPasswordReset(baseUrl: String, email: String, code: String, newPassword: String): Boolean {
        val normalizedCode = code.trim()
        if (!isValidEmail(email)) {
            _state.value = _state.value.copy(errorMessage = choose("Enter a valid email address.", "请输入有效邮箱地址。"))
            return false
        }
        if (!Regex("^\\d{6}$").matches(normalizedCode)) {
            _state.value = _state.value.copy(errorMessage = choose("Enter the 6-digit verification code.", "请输入 6 位验证码。"))
            return false
        }
        if (newPassword.length < 8) {
            _state.value = _state.value.copy(errorMessage = choose("Password must be at least 8 characters.", "密码至少需要 8 位。"))
            return false
        }

        _state.value = _state.value.copy(isLoading = true, errorMessage = null)
        return try {
            apiClient.configure(SessionCredentials("", baseUrl))
            apiClient.confirmPasswordReset(email.trim(), normalizedCode, newPassword)
            _state.value = _state.value.copy(isLoading = false, errorMessage = null)
            true
        } catch (e: Exception) {
            _state.value = _state.value.copy(isLoading = false, errorMessage = e.message ?: choose("Password reset failed.", "重置密码失败。"))
            false
        }
    }

    suspend fun pairGateway(baseUrl: String, gatewayId: String?, accessCode: String, gatewayType: String? = null, deviceId: String): Boolean {
        _state.value = _state.value.copy(isLoading = true, errorMessage = null)
        return try {
            apiClient.configure(
                SessionCredentials(
                    accessToken = _state.value.accessToken,
                    relayBaseURL = baseUrl.ifBlank { _state.value.relayBaseUrl.ifBlank { DEFAULT_RELAY_SERVER_URL } }
                )
            )
            val creds = apiClient.pairGateway(gatewayId, accessCode, gatewayType, deviceId)
            credentialStore.saveCredentials(creds)
            apiClient.configure(creds)
            _state.value = _state.value.copy(
                isLoading = false,
                isLoggedIn = true,
                isPaired = true,
                accessToken = creds.accessToken,
                relayBaseUrl = creds.relayBaseURL
            )
            true
        } catch (e: Exception) {
            _state.value = _state.value.copy(isLoading = false, errorMessage = e.message ?: choose("Pairing failed", "配对失败"))
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
                errorMessage = e.message ?: choose("Delete account failed", "注销账号失败")
            )
            false
        }
    }

    suspend fun changePassword(currentPassword: String, newPassword: String): Boolean {
        if (currentPassword.isEmpty()) {
            _state.value = _state.value.copy(errorMessage = choose("Enter your current password.", "请输入当前密码。"))
            return false
        }
        if (newPassword.length < 8) {
            _state.value = _state.value.copy(errorMessage = choose("Password must be at least 8 characters.", "密码至少需要 8 位。"))
            return false
        }

        _state.value = _state.value.copy(isLoading = true, errorMessage = null)
        return try {
            apiClient.changePassword(currentPassword, newPassword)
            credentialStore.clearCredentials()
            apiClient.clearSession()
            _state.value = AuthState()
            true
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                isLoading = false,
                errorMessage = e.message ?: choose("Change password failed", "修改密码失败")
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
        if (!isValidEmail(email)) return choose("Enter a valid email address.", "请输入有效邮箱地址。")
        if (password.isEmpty()) return choose("Enter your password.", "请输入密码。")
        return null
    }

    private fun validateRegistration(name: String, email: String, password: String): String? {
        if (name.trim().isEmpty()) return choose("Enter your name.", "请输入昵称。")
        if (!isValidEmail(email)) return choose("Enter a valid email address.", "请输入有效邮箱地址。")
        if (password.length < 8) return choose("Password must be at least 8 characters.", "密码至少需要 8 位。")
        return null
    }

    private fun isValidEmail(email: String): Boolean {
        val trimmed = email.trim()
        return Regex("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", RegexOption.IGNORE_CASE).matches(trimmed)
    }
}
