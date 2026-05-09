package com.rethinkingstudio.clawlink.ui.screens.auth

import android.net.Uri
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.rethinkingstudio.clawlink.ui.components.ClawLinkCard
import com.rethinkingstudio.clawlink.ui.components.ClawLinkScaffold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    authStore: AuthStore,
    onLoginSuccess: () -> Unit
) {
    val state by authStore.state.collectAsState()
    val scope = rememberCoroutineScope()

    var isRegisterMode by remember { mutableStateOf(false) }
    var isPrivateDeployment by remember { mutableStateOf(false) }
    var serverUrl by remember { mutableStateOf(AuthStore.DEFAULT_RELAY_SERVER_URL) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showingMoreSettings by remember { mutableStateOf(false) }
    var showingForgotPassword by remember { mutableStateOf(false) }
    var resendCountdown by remember { mutableIntStateOf(0) }

    val usesEmailVerification = isRegisterMode && !isPrivateDeployment
    val waitingForVerification = usesEmailVerification && state.pendingVerificationEmail != null
    val passwordIsValid = password.length >= 8
    val effectiveRelayServer = if (isPrivateDeployment) normalizeRelayURL(serverUrl) else defaultRelayServerForDevice()
    val modeTint = if (isRegisterMode) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary

    LaunchedEffect(state.pendingVerificationEmail) {
        if (state.pendingVerificationEmail != null) {
            resendCountdown = 120
        }
    }

    LaunchedEffect(state.suggestRegister) {
        if (state.suggestRegister) {
            isRegisterMode = true
            verificationCode = ""
            authStore.clearPendingVerification()
            authStore.consumeRegisterSuggestion()
        }
    }

    LaunchedEffect(resendCountdown) {
        if (resendCountdown > 0) {
            delay(1_000)
            resendCountdown -= 1
        }
    }

    ClawLinkScaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 18.dp)
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AuthHero(
                    isRegisterMode = isRegisterMode,
                    waitingForVerification = waitingForVerification,
                    pendingEmail = state.pendingVerificationEmail,
                    usesEmailVerification = usesEmailVerification
                )

                ClawLinkCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ModeSelector(
                            isRegisterMode = isRegisterMode,
                            modeTint = modeTint,
                            onLogin = {
                                authStore.clearError()
                                authStore.clearPendingVerification()
                                verificationCode = ""
                                isRegisterMode = false
                            },
                            onRegister = {
                                authStore.clearError()
                                authStore.clearPendingVerification()
                                isRegisterMode = true
                            }
                        )

                        AnimatedVisibility(isRegisterMode) {
                            AuthField(
                                value = name,
                                onValueChange = { name = it },
                                label = choose("Name", "姓名"),
                                placeholder = choose("Your name", "你的姓名"),
                                icon = Icons.Default.Person,
                                keyboardType = KeyboardType.Text,
                                tint = modeTint
                            )
                        }

                        AuthField(
                            value = email,
                            onValueChange = { email = it },
                            label = choose("Email", "邮箱"),
                            placeholder = "name@example.com",
                            icon = Icons.Default.Email,
                            keyboardType = KeyboardType.Email,
                            tint = modeTint
                        )

                        AuthField(
                            value = password,
                            onValueChange = { password = it },
                            label = choose("Password", "密码"),
                            placeholder = if (isRegisterMode) choose("At least 8 characters", "至少 8 位字符") else choose("Your password", "你的密码"),
                            icon = Icons.Default.Key,
                            keyboardType = KeyboardType.Password,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailing = {
                                TextButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                                }
                            },
                            tint = modeTint
                        )

                        AnimatedVisibility(!isRegisterMode) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                Text(
                                    text = choose("Forgot password?", "忘记密码？"),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clickable { showingForgotPassword = true }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        AnimatedVisibility(usesEmailVerification) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                AuthField(
                                    value = verificationCode,
                                    onValueChange = { if (it.length <= 6) verificationCode = it.filter(Char::isDigit) },
                                    label = choose("Verification code", "验证码"),
                                    placeholder = choose("6-digit code", "6 位数字验证码"),
                                    icon = Icons.Default.Numbers,
                                    keyboardType = KeyboardType.Number,
                                    enabled = waitingForVerification,
                                    tint = modeTint
                                )

                                if (waitingForVerification) {
                                    TextButton(
                                        onClick = {
                                            if (resendCountdown == 0) {
                                                authStore.clearPendingVerification()
                                                submitAuth(
                                                    authStore = authStore,
                                                    isRegisterMode = true,
                                                    waitingForVerification = false,
                                                    isPrivateDeployment = isPrivateDeployment,
                                                    relayServer = effectiveRelayServer,
                                                    name = name,
                                                    email = email,
                                                    password = password,
                                                    verificationCode = verificationCode,
                                                    onLoginSuccess = onLoginSuccess,
                                                    scope = scope
                                                )
                                            }
                                        },
                                        enabled = resendCountdown == 0
                                    ) {
                                        Text(if (resendCountdown > 0) choose("Resend in ${resendCountdown}s", "${resendCountdown} 秒后重发") else choose("Resend verification email", "重新发送验证邮件"))
                                    }
                                }
                            }
                        }

                        if (isRegisterMode && !waitingForVerification && password.isNotEmpty() && !passwordIsValid) {
                            Text(
                                choose("Password must be at least 8 characters.", "密码至少需要 8 位字符。"),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }

                        if (state.errorMessage != null && !showingForgotPassword) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ) {
                                Text(
                                    text = state.errorMessage!!,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        AuthSubmitButton(
                            isLoading = state.isLoading,
                            isRegisterMode = isRegisterMode,
                            usesEmailVerification = usesEmailVerification,
                            waitingForVerification = waitingForVerification,
                            modeTint = modeTint,
                            enabled = canSubmit(
                                isLoading = state.isLoading,
                                isRegisterMode = isRegisterMode,
                                waitingForVerification = waitingForVerification,
                                name = name,
                                email = email,
                                password = password,
                                verificationCode = verificationCode
                            ),
                            onClick = {
                                submitAuth(
                                    authStore = authStore,
                                    isRegisterMode = isRegisterMode,
                                    waitingForVerification = waitingForVerification,
                                    isPrivateDeployment = isPrivateDeployment,
                                    relayServer = effectiveRelayServer,
                                    name = name,
                                    email = email,
                                    password = password,
                                    verificationCode = verificationCode,
                                    onLoginSuccess = onLoginSuccess,
                                    scope = scope
                                )
                            }
                        )
                    }
                }

                AccountLoginSection(isLoading = state.isLoading)

                TextButton(onClick = { showingMoreSettings = true }) {
                    Text(choose("More", "更多"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (showingMoreSettings) {
        MoreSettingsDialog(
            isPrivateDeployment = isPrivateDeployment,
            serverUrl = serverUrl,
            modeTint = modeTint,
            onPrivateDeploymentChange = { enabled ->
                isPrivateDeployment = enabled
                authStore.clearPendingVerification()
                verificationCode = ""
                if (!enabled) {
                    serverUrl = AuthStore.DEFAULT_RELAY_SERVER_URL
                }
            },
            onServerUrlChange = { serverUrl = it },
            onDismiss = { showingMoreSettings = false }
        )
    }

    if (showingForgotPassword) {
        ForgotPasswordScreen(
            authStore = authStore,
            relayServer = effectiveRelayServer,
            isLoading = state.isLoading,
            errorMessage = state.errorMessage,
            onDismiss = {
                authStore.clearError()
                showingForgotPassword = false
            },
            scope = scope
        )
    }
}
