package com.rethinkingstudio.clawlink.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rethinkingstudio.clawlink.core.state.auth.AuthStore
import com.rethinkingstudio.clawlink.ui.components.ClawLinkCard
import com.rethinkingstudio.clawlink.ui.components.ClawLinkHeroHeader
import com.rethinkingstudio.clawlink.ui.components.ClawLinkPrimaryButton
import com.rethinkingstudio.clawlink.ui.components.ClawLinkScaffold
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    authStore: AuthStore,
    onPairSuccess: () -> Unit,
    onBack: () -> Unit
) {
    val state by authStore.state.collectAsState()
    val scope = rememberCoroutineScope()

    var gatewayId by remember { mutableStateOf("") }
    var accessCode by remember { mutableStateOf("") }

    ClawLinkScaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pair Gateway") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                ClawLinkHeroHeader(
                    title = "Enter pairing details",
                    subtitle = "Use the access code shown by your ClawLink host terminal."
                )

                ClawLinkCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        OutlinedTextField(
                            value = gatewayId,
                            onValueChange = { gatewayId = it },
                            label = { Text("Gateway ID (optional)") },
                            singleLine = true,
                            shape = RoundedCornerShape(22.dp),
                            colors = pairingFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = accessCode,
                            onValueChange = { accessCode = it },
                            label = { Text("Access Code") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(22.dp),
                            colors = pairingFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )

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

                        ClawLinkPrimaryButton(
                            text = if (state.isLoading) "Pairing…" else "Pair gateway",
                            onClick = {
                                scope.launch {
                                    val deviceId = "android_${System.currentTimeMillis()}"
                                    val success = authStore.pairGateway(
                                        gatewayId.ifBlank { null },
                                        accessCode.trim(),
                                        deviceId
                                    )
                                    if (success) onPairSuccess()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun pairingFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface
)
