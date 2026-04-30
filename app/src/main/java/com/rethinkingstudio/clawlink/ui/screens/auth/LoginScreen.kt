package com.rethinkingstudio.clawlink.ui.screens.auth

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
import androidx.compose.material3.AlertDialog
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
    var resendCountdown by remember { mutableIntStateOf(0) }

    val usesEmailVerification = isRegisterMode && !isPrivateDeployment
    val waitingForVerification = usesEmailVerification && state.pendingVerificationEmail != null
    val passwordIsValid = password.length >= 8
    val effectiveRelayServer = if (isPrivateDeployment) normalizeRelayURL(serverUrl) else AuthStore.DEFAULT_RELAY_SERVER_URL
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
                                label = "Name",
                                placeholder = "Your name",
                                icon = Icons.Default.Person,
                                keyboardType = KeyboardType.Text,
                                tint = modeTint
                            )
                        }

                        AuthField(
                            value = email,
                            onValueChange = { email = it },
                            label = "Email",
                            placeholder = "name@example.com",
                            icon = Icons.Default.Email,
                            keyboardType = KeyboardType.Email,
                            tint = modeTint
                        )

                        AuthField(
                            value = password,
                            onValueChange = { password = it },
                            label = "Password",
                            placeholder = if (isRegisterMode) "At least 8 characters" else "Your password",
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

                        AnimatedVisibility(usesEmailVerification) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                AuthField(
                                    value = verificationCode,
                                    onValueChange = { if (it.length <= 6) verificationCode = it.filter(Char::isDigit) },
                                    label = "Verification code",
                                    placeholder = "6-digit code",
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
                                        Text(if (resendCountdown > 0) "Resend in ${resendCountdown}s" else "Resend verification email")
                                    }
                                }
                            }
                        }

                        if (isRegisterMode && !waitingForVerification && password.isNotEmpty() && !passwordIsValid) {
                            Text(
                                "Password must be at least 8 characters.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }

                        if (state.errorMessage != null) {
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
                    Text("More", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
}

@Composable
private fun AuthHero(
    isRegisterMode: Boolean,
    waitingForVerification: Boolean,
    pendingEmail: String?,
    usesEmailVerification: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            modifier = Modifier.size(54.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
            shadowElevation = 3.dp
        ) {
            Image(
                painter = painterResource(id = R.drawable.launch_crab),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.padding(6.dp)
            )
        }

        Text(
            text = if (isRegisterMode) "Create your account" else "Sign in to ClawLink",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        val subtitle = when {
            waitingForVerification -> "Enter the code sent to ${pendingEmail.orEmpty()}."
            usesEmailVerification -> "We will send a verification code before creating your account."
            isRegisterMode -> "Create an account and connect it to your relay workspace."
            else -> "Use your relay account to continue."
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
    }
}

@Composable
private fun ModeSelector(
    isRegisterMode: Boolean,
    modeTint: Color,
    onLogin: () -> Unit,
    onRegister: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f), RoundedCornerShape(999.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ModeTab(
            title = "Sign in",
            icon = Icons.Default.ArrowForward,
            isSelected = !isRegisterMode,
            tint = modeTint,
            modifier = Modifier.weight(1f),
            onClick = onLogin
        )
        ModeTab(
            title = "Register",
            icon = Icons.Default.PersonAdd,
            isSelected = isRegisterMode,
            tint = modeTint,
            modifier = Modifier.weight(1f),
            onClick = onRegister
        )
    }
}

@Composable
private fun ModeTab(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, modifier = Modifier.size(18.dp), tint = if (isSelected) tint else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(8.dp))
            Text(title, fontWeight = FontWeight.SemiBold, color = if (isSelected) tint else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    icon: ImageVector,
    keyboardType: KeyboardType,
    tint: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(icon, null, tint = tint.copy(alpha = if (enabled) 1f else 0.45f)) },
        trailingIcon = trailing,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        enabled = enabled,
        visualTransformation = visualTransformation,
        shape = RoundedCornerShape(18.dp),
        colors = loginFieldColors(tint),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun AuthSubmitButton(
    isLoading: Boolean,
    isRegisterMode: Boolean,
    usesEmailVerification: Boolean,
    waitingForVerification: Boolean,
    modeTint: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 7.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = modeTint)
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
            } else {
                Icon(
                    imageVector = when {
                        waitingForVerification -> Icons.Default.CheckCircle
                        usesEmailVerification -> Icons.Default.Email
                        isRegisterMode -> Icons.Default.PersonAdd
                        else -> Icons.Default.ArrowForward
                    },
                    contentDescription = null,
                    tint = Color.White
                )
            }
            Spacer(Modifier.size(10.dp))
            Text(
                text = when {
                    isLoading -> "Processing..."
                    waitingForVerification -> "Verify and register"
                    usesEmailVerification -> "Send verification"
                    isRegisterMode -> "Register"
                    else -> "Sign in"
                },
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun AccountLoginSection(isLoading: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)).padding(top = 1.dp))
            Text("  or  ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.weight(1f).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)).padding(top = 1.dp))
        }
        Surface(
            onClick = {},
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = Color.Black
        ) {
            Text(
                "Sign in with Apple",
                modifier = Modifier.padding(vertical = 16.dp),
                color = Color.White,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun MoreSettingsDialog(
    isPrivateDeployment: Boolean,
    serverUrl: String,
    modeTint: Color,
    onPrivateDeploymentChange: (Boolean) -> Unit,
    onServerUrlChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("More settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Private deployment", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Use a custom Relay Server URL instead of the default hosted relay.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = isPrivateDeployment, onCheckedChange = onPrivateDeploymentChange)
                }

                AnimatedVisibility(isPrivateDeployment) {
                    AuthField(
                        value = serverUrl,
                        onValueChange = onServerUrlChange,
                        label = "Relay address",
                        placeholder = AuthStore.DEFAULT_RELAY_SERVER_URL,
                        icon = Icons.Default.Storage,
                        keyboardType = KeyboardType.Uri,
                        tint = modeTint
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

private fun canSubmit(
    isLoading: Boolean,
    isRegisterMode: Boolean,
    waitingForVerification: Boolean,
    name: String,
    email: String,
    password: String,
    verificationCode: String
): Boolean {
    if (isLoading) return false
    if (waitingForVerification) return verificationCode.trim().length == 6
    if (!isValidEmail(email) || password.isEmpty()) return false
    if (isRegisterMode && (name.trim().isEmpty() || password.length < 8)) return false
    return true
}

private fun submitAuth(
    authStore: AuthStore,
    isRegisterMode: Boolean,
    waitingForVerification: Boolean,
    isPrivateDeployment: Boolean,
    relayServer: String,
    name: String,
    email: String,
    password: String,
    verificationCode: String,
    onLoginSuccess: () -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    scope.launch {
        val deviceId = "android_${System.currentTimeMillis()}"
        val success = when {
            waitingForVerification -> authStore.verifyRegistrationEmail(relayServer, verificationCode.trim(), deviceId)
            isRegisterMode -> authStore.register(relayServer, name.trim(), email.trim(), password, deviceId, isPrivateDeployment)
            else -> authStore.login(relayServer, email.trim(), password, deviceId)
        }
        if (success) onLoginSuccess()
    }
}

private fun normalizeRelayURL(raw: String): String {
    val trimmed = raw.trim().ifBlank { AuthStore.DEFAULT_RELAY_SERVER_URL }
    return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
}

private fun isValidEmail(email: String): Boolean {
    return Regex("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", RegexOption.IGNORE_CASE)
        .matches(email.trim())
}

@Composable
private fun loginFieldColors(tint: Color) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = tint,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedLabelColor = tint,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor = tint,
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f),
    disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
)
