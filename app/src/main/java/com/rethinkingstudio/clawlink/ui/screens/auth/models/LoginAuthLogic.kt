package com.rethinkingstudio.clawlink.ui.screens.auth

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
import com.rethinkingstudio.clawlink.core.state.auth.AuthStore
import com.rethinkingstudio.clawlink.core.network.dto.LegalConsentRequest
import com.rethinkingstudio.clawlink.core.utils.MobileDeviceId
import com.rethinkingstudio.clawlink.ui.components.ClawLinkCard
import com.rethinkingstudio.clawlink.ui.components.ClawLinkScaffold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal fun canSubmit(
    isLoading: Boolean,
    isRegisterMode: Boolean,
    waitingForVerification: Boolean,
    name: String,
    email: String,
    password: String,
    verificationCode: String,
    hasAcceptedLegal: Boolean
): Boolean {
    if (isLoading) return false
    // 已有账号不重复确认；注册及验证码确认仍需当前版本的主动同意。
    if (isRegisterMode && !hasAcceptedLegal) return false
    if (waitingForVerification) return verificationCode.trim().length == 6
    if (!isValidEmail(email) || password.isEmpty()) return false
    if (isRegisterMode && (name.trim().isEmpty() || password.length < 8)) return false
    return true
}

internal fun canUseThirdPartyAuth(
    isLoading: Boolean,
    isRegisterMode: Boolean,
    hasAcceptedLegal: Boolean
): Boolean {
    return !isLoading && (!isRegisterMode || hasAcceptedLegal)
}

internal fun submitAuth(
    context: Context,
    authStore: AuthStore,
    isRegisterMode: Boolean,
    waitingForVerification: Boolean,
    isPrivateDeployment: Boolean,
    relayServer: String,
    name: String,
    email: String,
    password: String,
    verificationCode: String,
    hasAcceptedLegal: Boolean,
    onLoginSuccess: () -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    scope.launch {
        val deviceId = MobileDeviceId.resolve(context)
        val success = when {
            waitingForVerification && hasAcceptedLegal -> authStore.verifyRegistrationEmail(relayServer, verificationCode.trim(), deviceId)
            waitingForVerification -> false
            isRegisterMode && hasAcceptedLegal -> authStore.register(
                baseUrl = relayServer,
                name = name.trim(),
                email = email.trim(),
                password = password,
                deviceId = deviceId,
                legalConsent = LegalConsentRequest.currentAccepted(),
                isPrivateDeployment = isPrivateDeployment
            )
            isRegisterMode -> false
            else -> authStore.login(relayServer, email.trim(), password, deviceId)
        }
        if (success) onLoginSuccess()
    }
}

internal fun normalizeRelayURL(raw: String): String {
    val trimmed = raw.trim().ifBlank { AuthStore.DEFAULT_RELAY_SERVER_URL }
    val normalized = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
    val uri = Uri.parse(normalized)
    val host = uri.host?.lowercase() ?: return normalized

    if (!isAndroidEmulator() || (host != "127.0.0.1" && host != "localhost" && host != "0.0.0.0")) {
        return normalized
    }

    return uri.buildUpon()
        .encodedAuthority(buildString {
            append("10.0.2.2")
            if (uri.port != -1) {
                append(":")
                append(uri.port)
            }
        })
        .build()
        .toString()
}

internal fun defaultRelayServerForDevice(): String {
    return if (isAndroidEmulator()) {
        "http://10.0.2.2:8080"
    } else {
        AuthStore.DEFAULT_RELAY_SERVER_URL
    }
}

internal fun isValidEmail(email: String): Boolean {
    return Regex("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", RegexOption.IGNORE_CASE)
        .matches(email.trim())
}

internal fun isAndroidEmulator(): Boolean {
    return Build.FINGERPRINT.startsWith("generic") ||
        Build.MODEL.contains("Emulator", ignoreCase = true) ||
        Build.MODEL.contains("sdk_gphone", ignoreCase = true) ||
        Build.HARDWARE.contains("ranchu", ignoreCase = true) ||
        Build.PRODUCT.contains("sdk", ignoreCase = true)
}
