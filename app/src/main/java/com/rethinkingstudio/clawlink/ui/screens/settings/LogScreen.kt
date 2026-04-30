package com.rethinkingstudio.clawlink.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.core.network.RelayAPIClient
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import com.rethinkingstudio.clawlink.ui.components.ClawLinkScaffold
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    gatewayStore: GatewayStore,
    apiClient: RelayAPIClient,
    onBack: () -> Unit
) {
    val gatewayState by gatewayStore.state.collectAsState()
    val gatewayId = gatewayState.selectedGatewayId
    val scope = rememberCoroutineScope()
    
    var logLines by remember { mutableStateOf(listOf<String>()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    
    val listState = rememberLazyListState()

    val refreshLogs = suspend {
        if (gatewayId != null) {
            isLoading = true
            error = null
            try {
                val response = apiClient.fetchLogs(gatewayId)
                logLines = response.lines
            } catch (e: Exception) {
                error = e.message
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(gatewayId) {
        refreshLogs()
    }

    ClawLinkScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_row_logs)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { scope.launch { refreshLogs() } }, enabled = !isLoading) {
                        Icon(Icons.Default.Refresh, stringResource(R.string.common_action_refresh))
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF151515))
        ) {
            if (isLoading && logLines.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (error != null && logLines.isEmpty()) {
                Text(
                    text = "Failed to load logs: $error",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(logLines) { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            ),
                            color = if (line.contains("ERROR", ignoreCase = true)) Color.Red else Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }
    }
}
