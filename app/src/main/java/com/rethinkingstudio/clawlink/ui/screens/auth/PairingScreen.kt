package com.rethinkingstudio.clawlink.ui.screens.auth

import androidx.camera.core.ExperimentalGetImage
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.core.models.gateway.GatewayType
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
import com.rethinkingstudio.clawlink.core.state.auth.AuthStore
import com.rethinkingstudio.clawlink.core.utils.MobileDeviceId
import com.rethinkingstudio.clawlink.core.utils.PairingInputResolver
import com.rethinkingstudio.clawlink.ui.components.*
import kotlinx.coroutines.launch

@ExperimentalGetImage
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun PairingScreen(
    authStore: AuthStore,
    onPairSuccess: () -> Unit,
    onBack: () -> Unit
) {
    val authState by authStore.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var relayServer by remember { mutableStateOf("") }
    var pairingCode by remember { mutableStateOf("") }
    var gatewayId by remember { mutableStateOf("") }
    var pairingGatewayType by remember { mutableStateOf<GatewayType?>(null) }
    var qrPayload by remember { mutableStateOf("") }
    var showingScanner by remember { mutableStateOf(false) }
    var didAutoOpenScanner by remember { mutableStateOf(false) }
    var pairingErrorMessage by remember { mutableStateOf<String?>(null) }

    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)
    val preferredRelayServer = remember(authState.relayBaseUrl) {
        authState.relayBaseUrl.ifBlank { AuthStore.DEFAULT_RELAY_SERVER_URL }
    }
    val usesPrivateRelay = remember(preferredRelayServer) {
        !sameRelayServer(preferredRelayServer, AuthStore.DEFAULT_RELAY_SERVER_URL)
    }

    LaunchedEffect(preferredRelayServer) {
        if (relayServer.isEmpty()) {
            relayServer = preferredRelayServer
        }
    }

    LaunchedEffect(cameraPermissionState.status.isGranted) {
        if (!didAutoOpenScanner) {
            didAutoOpenScanner = true
            if (cameraPermissionState.status.isGranted) {
                showingScanner = true
            } else {
                cameraPermissionState.launchPermissionRequest()
            }
        }
    }

    ClawLinkScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.auth_pairing_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            SectionHeader(
                title = stringResource(R.string.auth_pairing_title),
                subtitle = stringResource(R.string.auth_pairing_subtitle)
            )

            // Account Section
            ClawLinkCard {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.auth_pairing_current_account), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(stringResource(R.string.auth_pairing_wrong_account_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                authStore.logout()
                                onBack()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(stringResource(R.string.settings_account_sign_out), fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Scan Section
            ClawLinkCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.auth_pairing_scan_title), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text(stringResource(R.string.auth_pairing_scan_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(
                            onClick = {
                                if (cameraPermissionState.status.isGranted) {
                                    showingScanner = true
                                } else {
                                    cameraPermissionState.launchPermissionRequest()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.QrCodeScanner, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.auth_pairing_scan_button))
                        }
                    }
                    Text(
                        stringResource(R.string.auth_pairing_scan_no_camera_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                    )
                }
            }

            // Manual Section
            ClawLinkCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(stringResource(R.string.auth_pairing_manual_title), fontWeight = FontWeight.Bold, fontSize = 17.sp)

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (usesPrivateRelay) {
                            OutlinedTextField(
                                value = relayServer,
                                onValueChange = {
                                    relayServer = it
                                    pairingGatewayType = null
                                },
                                label = { Text(stringResource(R.string.auth_relay_address)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
                                )
                            )
                        } else {
                            ReadOnlyRelayAddressField(value = relayServer)
                        }

                        Text(
                            text = if (usesPrivateRelay) {
                                stringResource(R.string.auth_pairing_relay_private_hint)
                            } else {
                                stringResource(R.string.auth_pairing_relay_default_hint)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                        )

                        OutlinedTextField(
                            value = pairingCode,
                            onValueChange = {
                                pairingCode = it
                                pairingGatewayType = null
                            },
                            label = { Text(stringResource(R.string.auth_pairing_pairing_code_placeholder)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
                            )
                        )
                        pairingGatewayType?.let { type ->
                            GatewayTypeBadge(type = type)
                        }
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    pairingErrorMessage = null
                                    val resolved = PairingInputResolver.resolvePairingInput(
                                        qrPayload = "",
                                        currentServerURL = relayServer,
                                        manualGatewayID = gatewayId,
                                        manualAccessCode = pairingCode
                                    )
                                    val success = authStore.pairGateway(
                                        baseUrl = resolved.serverURL,
                                        gatewayId = resolved.gatewayID,
                                        accessCode = resolved.accessCode,
                                        gatewayType = (resolved.gatewayType ?: pairingGatewayType)?.name,
                                        deviceId = MobileDeviceId.resolve(context)
                                    )
                                    if (success) {
                                        onPairSuccess()
                                    } else {
                                        pairingErrorMessage = authState.errorMessage ?: choose("Pairing failed. Check the code and Relay address.", "绑定失败，请检查配对码和 Relay 地址。")
                                    }
                                } catch (e: Exception) {
                                    pairingErrorMessage = e.message ?: choose("Pairing failed. Check the code and Relay address.", "绑定失败，请检查配对码和 Relay 地址。")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        enabled = !authState.isLoading
                    ) {
                        if (authState.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.auth_pairing_binding_in_progress))
                        } else {
                            Text(stringResource(R.string.auth_pairing_complete_binding), fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    Text(
                        stringResource(R.string.auth_pairing_hint_verifying),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                    )
                }
            }
        }
    }

    if (showingScanner) {
        QRScannerView(
            onCodeScanned = { payload ->
                try {
                    val resolved = PairingInputResolver.resolvePairingInput(
                        qrPayload = payload,
                        currentServerURL = relayServer,
                        manualGatewayID = "",
                        manualAccessCode = ""
                    )
                    qrPayload = ""
                    gatewayId = resolved.gatewayID ?: ""
                    relayServer = resolved.serverURL
                    pairingCode = resolved.accessCode
                    pairingGatewayType = resolved.gatewayType
                    showingScanner = false
                    
                    // Auto-bind after scan
                    scope.launch {
                        val success = authStore.pairGateway(
                            baseUrl = resolved.serverURL,
                            gatewayId = gatewayId.ifBlank { null },
                            accessCode = pairingCode.trim(),
                            gatewayType = resolved.gatewayType?.name,
                            deviceId = MobileDeviceId.resolve(context)
                        )
                        if (success) {
                            onPairSuccess()
                        } else {
                            pairingErrorMessage = authState.errorMessage ?: choose("Pairing failed. Check the code and Relay address.", "绑定失败，请检查配对码和 Relay 地址。")
                        }
                    }
                } catch (e: Exception) {
                    showingScanner = false
                    pairingErrorMessage = e.message ?: choose("Invalid QR code payload", "二维码内容无效")
                }
            },
            onClose = { showingScanner = false }
        )
    }

    pairingErrorMessage?.let { message ->
        ClawLinkAlertDialog(
            onDismissRequest = { pairingErrorMessage = null },
            title = stringResource(R.string.auth_pairing_result_fail_title),
            message = message,
            confirmText = stringResource(R.string.common_action_ok),
            onConfirm = { pairingErrorMessage = null }
        )
    }
}

private fun sameRelayServer(lhs: String, rhs: String): Boolean {
    return lhs.trim().trimEnd('/').equals(rhs.trim().trimEnd('/'), ignoreCase = true)
}

@Composable
private fun ReadOnlyRelayAddressField(value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.auth_relay_address),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f), RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.38f), RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface
            ) {
                Box(
                    modifier = Modifier.size(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun GatewayTypeBadge(type: GatewayType) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
        ) {
            Text(
                text = type.displayTitle,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
