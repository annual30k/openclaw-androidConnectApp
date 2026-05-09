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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
internal fun AuthHero(
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
            text = if (isRegisterMode) choose("Create your account", "创建账号") else choose("Sign in to ClawLink", "登录 ClawLink"),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        val subtitle = when {
            waitingForVerification -> choose("Enter the code sent to ${pendingEmail.orEmpty()}.", "请输入发送到 ${pendingEmail.orEmpty()} 的验证码。")
            usesEmailVerification -> choose("We will send a verification code before creating your account.", "创建账号前会先发送验证码。")
            isRegisterMode -> choose("Create an account and connect it to your relay workspace.", "创建账号并连接到你的 Relay 工作区。")
            else -> choose("Use your relay account to continue.", "使用你的 Relay 账号继续。")
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
internal fun ModeSelector(
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
            title = choose("Sign in", "登录"),
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            isSelected = !isRegisterMode,
            tint = modeTint,
            modifier = Modifier.weight(1f),
            onClick = onLogin
        )
        ModeTab(
            title = choose("Register", "注册"),
            icon = Icons.Default.PersonAdd,
            isSelected = isRegisterMode,
            tint = modeTint,
            modifier = Modifier.weight(1f),
            onClick = onRegister
        )
    }
}

@Composable
internal fun ModeTab(
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
internal fun AuthField(
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
internal fun AuthSubmitButton(
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
                        else -> Icons.AutoMirrored.Filled.ArrowForward
                    },
                    contentDescription = null,
                    tint = Color.White
                )
            }
            Spacer(Modifier.size(10.dp))
            Text(
                text = when {
                    isLoading -> choose("Processing...", "处理中...")
                    waitingForVerification -> choose("Verify and register", "验证并注册")
                    usesEmailVerification -> choose("Send verification", "发送验证码")
                    isRegisterMode -> choose("Register", "注册")
                    else -> choose("Sign in", "登录")
                },
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
internal fun AccountLoginSection(isLoading: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)).padding(top = 1.dp))
            Text(choose("  or  ", "  或  "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                choose("Sign in with Apple", "使用 Apple 登录"),
                modifier = Modifier.padding(vertical = 16.dp),
                color = Color.White,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}


@Composable
internal fun loginFieldColors(tint: Color) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color.Transparent,
    unfocusedBorderColor = Color.Transparent,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedLabelColor = tint,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor = tint,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f),
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f),
    disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f),
    disabledBorderColor = Color.Transparent
)
