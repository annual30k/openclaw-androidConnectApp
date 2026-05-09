package com.rethinkingstudio.clawlink.ui.screens.model

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rethinkingstudio.clawlink.core.models.catalog.ModelItem
import com.rethinkingstudio.clawlink.core.models.gateway.AggregateStatus
import com.rethinkingstudio.clawlink.core.models.gateway.ConnectionPhase
import com.rethinkingstudio.clawlink.core.models.gateway.GatewaySummary
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import com.rethinkingstudio.clawlink.core.state.model.ModelStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ModelCatalogScreen(
    modelStore: ModelStore,
    gatewayStore: GatewayStore,
    onBack: () -> Unit
) {
    val modelState by modelStore.state.collectAsState()
    val gatewayState by gatewayStore.state.collectAsState()
    val scope = rememberCoroutineScope()
    val gatewayId = gatewayState.selectedGatewayId
    val operationsLocked = gatewayState.restartingGatewayId != null || !gatewayState.isSelectedGatewayChatChainReady

    var searchText by remember { mutableStateOf("") }
    var modelToConfirm by remember { mutableStateOf<ModelItem?>(null) }

    LaunchedEffect(gatewayId, gatewayState.isSelectedGatewayChatChainReady) {
        if (gatewayId != null && gatewayState.isSelectedGatewayChatChainReady) modelStore.loadModels(gatewayId)
    }

    val groupedModels = remember(modelState.models) {
        modelState.models.groupBy { it.provider.ifBlank { it.providerId.ifBlank { choose("Unknown", "未知") } } }
    }
    val filteredProviders = remember(groupedModels, modelState.models, searchText) {
        ModelCatalogPresentation.filteredProviders(groupedModels, modelState.models, searchText)
    }
    val defaultModel = modelState.defaultModel
    val isProcessing = modelState.isUpdatingDefault || operationsLocked

    Box(modifier = Modifier.fillMaxSize()) {
        ModelScreenBackdrop()
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(choose("Models", "模型"), fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = choose("Back", "返回"))
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { if (gatewayId != null && gatewayState.isSelectedGatewayChatChainReady) scope.launch { modelStore.loadModels(gatewayId) } },
                            enabled = !modelState.isLoading && !isProcessing
                        ) {
                            if (modelState.isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = choose("Refresh", "刷新"))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    MetricsSection(
                        providerCount = groupedModels.size,
                        modelCount = modelState.models.size,
                        modifier = Modifier.animateItem()
                    )
                }

                if (defaultModel != null && searchText.isBlank()) {
                    item {
                        DefaultModelHero(defaultModel, modifier = Modifier.animateItem())
                    }
                }

                filteredProviders.forEach { provider ->
                    val models = ModelCatalogPresentation.filteredModels(groupedModels[provider].orEmpty(), searchText)
                    item(key = "provider_$provider") {
                        ProviderSection(
                            provider = provider,
                            models = models,
                            updatingDefaultModelKey = modelState.updatingDefaultModelKey,
                            enabled = !isProcessing,
                            onModelClick = { model ->
                                if (!model.isDefault) modelToConfirm = model
                            },
                            modifier = Modifier.animateItem()
                        )
                    }
                }

                if (filteredProviders.isEmpty()) {
                    item(key = "empty") {
                        ModelEmptyState(
                            isLoading = modelState.isLoading,
                            hasQuery = searchText.isNotBlank(),
                            errorMessage = modelState.errorMessage,
                            onRefresh = { if (gatewayId != null && gatewayState.isSelectedGatewayChatChainReady) scope.launch { modelStore.loadModels(gatewayId) } },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }

        if (isProcessing) {
            ProcessingOverlay(operationsLocked = operationsLocked)
        }

        ModelSearchBar(
            searchText = searchText,
            onSearchTextChange = { searchText = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp)
        )

        if (gatewayState.isSelectedGatewayChatChainReady) modelState.errorMessage?.let { message ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .padding(bottom = 72.dp),
                action = { TextButton(onClick = modelStore::clearError) { Text(choose("Close", "关闭")) } }
            ) {
                Text(message)
            }
        }
    }

    modelToConfirm?.let { model ->
        AlertDialog(
            onDismissRequest = { modelToConfirm = null },
            title = { Text(choose("Set as global default model?", "设为全局默认模型？")) },
            text = { Text(choose("Set \"${model.displayName}\" as OpenClaw's global default model. The host may briefly restart and recover.", "将「${model.displayName}」设为 OpenClaw 的全局默认模型。主机可能会短暂重启恢复。")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        modelToConfirm = null
                        if (gatewayId != null && gatewayState.isSelectedGatewayChatChainReady) {
                            scope.launch {
                                val didUpdate = modelStore.setDefaultModel(gatewayId, model) {
                                    waitForGatewayRecoveryAfterDefaultModelChange(gatewayStore, gatewayId)
                                }
                                if (didUpdate) {
                                    modelStore.loadModels(gatewayId)
                                }
                            }
                        }
                    }
                ) {
                    Text(choose("Set as default", "设为默认"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { modelToConfirm = null }) { Text(choose("Cancel", "取消")) } }
        )
    }
}
