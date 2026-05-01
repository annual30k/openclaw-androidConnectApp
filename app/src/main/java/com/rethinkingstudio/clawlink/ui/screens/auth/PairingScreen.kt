package com.rethinkingstudio.clawlink.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.core.state.auth.AuthStore
import com.rethinkingstudio.clawlink.core.utils.PairingInputResolver
import com.rethinkingstudio.clawlink.ui.components.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun PairingScreen(
    authStore: AuthStore,
    onPairSuccess: () -> Unit,
    onBack: () -> Unit
) {
    val authState by authStore.state.collectAsState()
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var relayServer by remember { mutableStateOf("") }
    var pairingCode by remember { mutableStateOf("") }
    var gatewayId by remember { mutableStateOf("") }
    var qrPayload by remember { mutableStateOf("") }
    var showingScanner by remember { mutableStateOf(false) }

    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        if (relayServer.isEmpty()) {
            relayServer = authStore.state.value.relayBaseUrl ?: ""
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF8F9FB))
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8F9FB))
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
                        Text(stringResource(R.string.auth_pairing_wrong_account_hint), style = MaterialTheme.typography.bodySmall, color = Color(0xFF6B7280))
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                authStore.logout()
                                onBack()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF4444).copy(alpha = 0.1f),
                            contentColor = Color(0xFFEF4444)
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
                            Text(stringResource(R.string.auth_pairing_scan_description), style = MaterialTheme.typography.bodySmall, color = Color(0xFF6B7280))
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
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                        ) {
                            Icon(Icons.Default.QrCodeScanner, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.auth_pairing_scan_button))
                        }
                    }
                    Text(
                        stringResource(R.string.auth_pairing_scan_no_camera_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF9CA3AF)
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
                        OutlinedTextField(
                            value = relayServer,
                            onValueChange = { relayServer = it },
                            label = { Text(stringResource(R.string.auth_relay_address)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF3B82F6),
                                unfocusedBorderColor = Color(0xFFE5E7EB)
                            )
                        )

                        OutlinedTextField(
                            value = pairingCode,
                            onValueChange = { pairingCode = it },
                            label = { Text(stringResource(R.string.auth_pairing_pairing_code_placeholder)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF3B82F6),
                                unfocusedBorderColor = Color(0xFFE5E7EB)
                            )
                        )
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                val success = authStore.pairGateway(
                                    gatewayId.ifBlank { null },
                                    pairingCode.trim(),
                                    "android_${System.currentTimeMillis()}"
                                )
                                if (success) {
                                    onPairSuccess()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
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
                        color = Color(0xFF9CA3AF)
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
                    showingScanner = false
                    
                    // Auto-bind after scan
                    scope.launch {
                        authStore.pairGateway(
                            gatewayId = gatewayId.ifBlank { null },
                            accessCode = pairingCode.trim(),
                            deviceId = "android_${System.currentTimeMillis()}"
                        )
                        onBack()
                    }
                } catch (e: Exception) {
                    // Handle error
                }
            },
            onClose = { showingScanner = false }
        )
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = Color(0xFF111827))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF6B7280))
    }
}
