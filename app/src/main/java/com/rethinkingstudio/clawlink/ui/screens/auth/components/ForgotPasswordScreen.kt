package com.rethinkingstudio.clawlink.ui.screens.auth

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
import androidx.compose.material.icons.filled.Close
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
internal fun ForgotPasswordScreen(
    authStore: AuthStore,
    relayServer: String,
    isLoading: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var codeSent by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var passwordVisible by remember { mutableStateOf(false) }

    val canSend = !isLoading && isValidEmail(email)
    val canReset = !isLoading &&
        isValidEmail(email) &&
        code.trim().length == 6 &&
        newPassword.length >= 8 &&
        newPassword == confirmPassword

    LaunchedEffect(Unit) {
        authStore.clearError()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFF8FAFF),
                        Color(0xFFEAF4FF),
                        Color(0xFFFFFFFF)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            Surface(
                onClick = onDismiss,
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                modifier = Modifier.size(44.dp),
                shadowElevation = 0.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = choose("Close", "关闭"),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Text(
                choose("Forgot password", "忘记密码"),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                color = Color.Black
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.54f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.58f)),
                shadowElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        choose("Reset password", "重置密码"),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = Color.Black
                    )
                    Text(
                        choose("Enter your email, verify the code, then set a new password.", "输入邮箱并验证验证码后设置新密码。"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    ForgotPasswordInputField(
                        value = email,
                        onValueChange = { email = it },
                        label = choose("Email", "邮箱"),
                        placeholder = choose("Email", "邮箱"),
                        icon = Icons.Default.Email,
                        keyboardType = KeyboardType.Email
                    )

                    AnimatedVisibility(codeSent) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            ForgotPasswordInputField(
                            value = code,
                            onValueChange = { if (it.length <= 6) code = it.filter(Char::isDigit) },
                            label = choose("Verification code", "邮箱验证码"),
                            placeholder = choose("6-digit code", "6 位验证码"),
                            icon = Icons.Default.Numbers,
                            keyboardType = KeyboardType.Number
                        )
                            ForgotPasswordInputField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            label = choose("New password", "新密码"),
                            placeholder = choose("At least 8 characters", "至少 8 位"),
                            icon = Icons.Default.Key,
                            keyboardType = KeyboardType.Password,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailing = {
                                TextButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                                }
                            }
                        )
                            ForgotPasswordInputField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = choose("Confirm password", "确认新密码"),
                            placeholder = choose("Repeat new password", "再次输入新密码"),
                            icon = Icons.Default.Key,
                            keyboardType = KeyboardType.Password,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailing = {
                                TextButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                                }
                            }
                        )
                        }
                    }

                    (statusMessage ?: errorMessage)?.let { message ->
                        val isError = errorMessage != null && statusMessage == null
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = if (isError) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.76f)
                        ) {
                            Text(
                                message,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        enabled = if (codeSent) canReset else canSend,
                        onClick = {
                            scope.launch {
                                val deviceId = "android_${System.currentTimeMillis()}"
                                if (codeSent) {
                                    val didReset = authStore.confirmPasswordReset(relayServer, email.trim(), code.trim(), newPassword)
                                    if (didReset) {
                                        statusMessage = choose("Password updated. Sign in with the new password.", "密码已更新，请使用新密码登录。")
                                        onDismiss()
                                    } else {
                                        statusMessage = null
                                    }
                                } else {
                                    val didSend = authStore.requestPasswordReset(relayServer, email.trim(), deviceId)
                                    if (didSend) {
                                        codeSent = true
                                        statusMessage = choose("Verification code sent. Check your mailbox.", "验证码已发送，请查看邮箱。")
                                    } else {
                                        statusMessage = null
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.86f),
                            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                            contentColor = Color.White,
                            disabledContentColor = Color.White.copy(alpha = 0.42f)
                        )
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
                                    if (codeSent) Icons.Default.CheckCircle else Icons.Default.Email,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = if ((codeSent && canReset) || (!codeSent && canSend)) 1f else 0.42f)
                                )
                            }
                            Spacer(Modifier.size(10.dp))
                            Text(
                                if (codeSent) choose("Reset password", "重置密码") else choose("Send verification code", "发送验证码"),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ForgotPasswordInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    icon: ImageVector,
    keyboardType: KeyboardType,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: @Composable (() -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = Color.White,
            shadowElevation = 0.dp
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(placeholder, fontWeight = FontWeight.SemiBold) },
                leadingIcon = {
                    Icon(icon, null, tint = Color(0xFFB8BBC2))
                },
                trailingIcon = trailing,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                singleLine = true,
                visualTransformation = visualTransformation,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    focusedPlaceholderColor = Color(0xFFB8BBC2),
                    unfocusedPlaceholderColor = Color(0xFFB8BBC2)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
