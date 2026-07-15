package com.rethinkingstudio.clawlink.core.network

import com.rethinkingstudio.clawlink.core.state.LocalizedText

sealed class RelayAPIError : Exception() {
    data object NotConfigured : RelayAPIError() {
        override val message: String get() = LocalizedText.choose("Relay client not configured", "Relay 客户端尚未配置")
    }

    data object InvalidResponse : RelayAPIError() {
        override val message: String get() = LocalizedText.choose("Invalid server response", "服务器响应无效")
    }

    data class ServerError(
        val statusCode: Int,
        val errorCode: String,
        val remainingAttempts: Int? = null,
        val retryAfterSeconds: Int? = null
    ) : RelayAPIError() {
        private val userMessage: String
            get() = when (errorCode) {
                "valid_email_required" -> LocalizedText.choose("Enter a valid email address.", "请输入有效邮箱地址。")
                "password_too_short" -> LocalizedText.choose("Password must be at least 8 characters.", "密码至少需要 8 位。")
                "email_already_registered" -> LocalizedText.choose("This email is already registered. Sign in instead.", "该邮箱已注册，请直接登录。")
                "legal_consent_required" -> LocalizedText.choose(
                    "Please read and agree to the User Agreement and Privacy Policy before registering.",
                    "注册前请先阅读并同意《用户协议》和《隐私政策》。"
                )
                "email_verification_required" -> LocalizedText.choose("Verify your email before signing in.", "请先完成邮箱验证后再登录。")
                "user_not_registered" -> LocalizedText.choose("This email is not registered. Register first.", "该邮箱尚未注册，请先注册。")
                "invalid_credentials" -> remainingAttempts?.let {
                    LocalizedText.choose("Email or password is incorrect. $it attempts remaining.", "邮箱或密码错误，还可尝试 $it 次。")
                } ?: LocalizedText.choose("Email or password is incorrect.", "邮箱或密码错误。")
                "rate_limited" -> retryAfterSeconds?.let {
                    LocalizedText.choose("Too many attempts. Try again in ${it}s.", "尝试次数过多，请在 ${it} 秒后重试。")
                } ?: LocalizedText.choose("Too many attempts. Try again later.", "尝试次数过多，请稍后重试。")
                "verification_code_required" -> LocalizedText.choose("Enter the 6-digit verification code.", "请输入 6 位验证码。")
                "verification_code_not_found" -> LocalizedText.choose("Verification code was not found. Request a new code.", "未找到验证码，请重新获取。")
                "verification_code_expired" -> LocalizedText.choose("Verification code expired. Request a new code.", "验证码已过期，请重新获取。")
                "verification_code_rate_limited" -> LocalizedText.choose("Too many verification attempts. Request a new code later.", "验证码尝试次数过多，请稍后重新获取。")
                "verification_code_invalid" -> remainingAttempts?.let {
                    LocalizedText.choose("Verification code is incorrect. $it attempts remaining.", "验证码错误，还可尝试 $it 次。")
                } ?: LocalizedText.choose("Verification code is incorrect.", "验证码错误。")
                "email_already_verified" -> LocalizedText.choose("This email is already verified. Sign in instead.", "该邮箱已验证，请直接登录。")
                "verification_email_failed" -> LocalizedText.choose("Failed to send verification email. Try again later.", "验证码邮件发送失败，请稍后重试。")
                "not_found", "unknown_error" -> LocalizedText.choose(
                    "This Relay does not support password reset yet. Use the local relay or deploy the latest relay server.",
                    "当前 Relay 尚未支持重置密码，请切换到本地 Relay 或部署最新中继服务。"
                )
                else -> errorCode
            }
        override val message: String get() = userMessage
    }

    data class NetworkError(val exception: Throwable) : RelayAPIError() {
        override val message: String get() = LocalizedText.choose("Network error: ${exception.message}", "网络错误：${exception.message}")
        override val cause: Throwable get() = exception
    }

    data object Unauthorized : RelayAPIError() {
        override val message: String get() = LocalizedText.choose("Unauthorized - please log in again", "登录已失效，请重新登录")
    }

    data class RateLimited(val retryAfterSeconds: Int?) : RelayAPIError() {
        override val message: String get() = LocalizedText.choose("Rate limited, retry after ${retryAfterSeconds ?: "?"}s", "请求过于频繁，请在 ${retryAfterSeconds ?: "?"} 秒后重试")
    }

    data object GatewayNotFound : RelayAPIError() {
        override val message: String get() = LocalizedText.choose("Gateway not found", "未找到网关")
    }

    data class PairingFailed(val errorMessage: String) : RelayAPIError() {
        override val message: String get() = LocalizedText.choose("Pairing failed: $errorMessage", "配对失败：$errorMessage")
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
