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

private val CardShape = RoundedCornerShape(28.dp)
private val ProviderCardShape = RoundedCornerShape(24.dp)
private val PillShape = RoundedCornerShape(999.dp)
private val AccentBlue = Color(0xFF0A84FF)
private val AccentBlueSoft = Color(0xFF5AC8FA)
private val WarningOrange = Color(0xFFFFB13D)
private val MetricPurple = Color(0xFF9B5CFF)
private val ScreenWhite = Color(0xFFFAFBFF)

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
        modelState.models.groupBy { it.provider.ifBlank { it.providerId.ifBlank { "Unknown" } } }
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
                        modifier = Modifier.animateItemPlacement()
                    )
                }

                if (defaultModel != null && searchText.isBlank()) {
                    item {
                        DefaultModelHero(defaultModel, modifier = Modifier.animateItemPlacement())
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
                            modifier = Modifier.animateItemPlacement()
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
                            modifier = Modifier.animateItemPlacement()
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

@Composable
private fun ModelScreenBackdrop() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFFF2F5FA), Color.White),
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(1000f, 1000f)
                )
            )
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(320.dp)
                .offset(x = 60.dp, y = (-60).dp)
                .graphicsLayer(alpha = 0.45f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(AccentBlue.copy(alpha = 0.25f), Color.Transparent),
                        radius = Float.POSITIVE_INFINITY
                    ),
                    CircleShape
                )
                .blur(80.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(340.dp)
                .offset(x = (-80).dp, y = 80.dp)
                .graphicsLayer(alpha = 0.4f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(AccentBlueSoft.copy(alpha = 0.22f), Color.Transparent),
                        radius = Float.POSITIVE_INFINITY
                    ),
                    CircleShape
                )
                .blur(90.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(500.dp)
                .offset(x = 100.dp, y = 150.dp)
                .graphicsLayer(alpha = 0.25f)
                .background(Brush.radialGradient(listOf(AccentBlue.copy(alpha = 0.1f), Color.Transparent)), CircleShape)
                .blur(120.dp)
        )
    }
}

@Composable
private fun MetricsSection(providerCount: Int, modelCount: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricsChip(choose("Providers", "供应商"), providerCount.toString(), Icons.Default.Business, AccentBlue, Modifier.weight(1f))
        MetricsChip(choose("Available models", "可用模型"), modelCount.toString(), Icons.Default.Memory, MetricPurple, Modifier.weight(1f))
    }
}

@Composable
private fun MetricsChip(title: String, value: String, icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
            .border(0.6.dp, tint.copy(alpha = 0.1f), RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(32.dp).background(tint.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun DefaultModelHero(model: ModelItem, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(10.dp, CardShape, clip = false, ambientColor = Color.Black.copy(alpha = 0.03f), spotColor = Color.Black.copy(alpha = 0.05f))
            .clip(CardShape)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                        WarningOrange.copy(alpha = 0.08f)
                    )
                )
            )
            .border(0.8.dp, WarningOrange.copy(alpha = 0.2f), CardShape)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Star, contentDescription = null, tint = WarningOrange, modifier = Modifier.size(14.dp))
            Text(
                choose("Global default model", "全局默认模型"),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            ProviderIcon(model = model, size = 48)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(model.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${model.providerLabel} · ${model.displayContextWindow}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ProviderSection(
    provider: String,
    models: List<ModelItem>,
    updatingDefaultModelKey: String?,
    enabled: Boolean,
    onModelClick: (ModelItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            provider.uppercase(),
            modifier = Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ProviderCardShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
                .border(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f), ProviderCardShape)
        ) {
            models.forEachIndexed { index, model ->
                ModelRow(
                    model = model,
                    isUpdating = updatingDefaultModelKey == ModelStore.modelKey(model),
                    enabled = enabled,
                    onClick = { onModelClick(model) }
                )
                if (index < models.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 64.dp)
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.10f))
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelRow(model: ModelItem, isUpdating: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled && !model.isDefault,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ProviderIcon(model = model, size = 32)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(model.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (model.isSelected) {
                    Box(Modifier.size(6.dp).background(AccentBlue, CircleShape))
                }
            }
            Text(model.displayContextWindow, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when {
            isUpdating -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = AccentBlue)
            model.isDefault -> Icon(Icons.Default.Star, contentDescription = choose("Default model", "默认模型"), tint = WarningOrange, modifier = Modifier.size(16.dp))
            else -> Box(Modifier.width(16.dp))
        }
    }
}

@Composable
private fun ProviderIcon(model: ModelItem, size: Int) {
    val colors = providerColors(model)
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(maxOf(6, (size * 0.28f).toInt()).dp))
            .background(colors.first),
        contentAlignment = Alignment.Center
    ) {
        val label = model.providerLabel.firstOrNull()?.uppercaseChar()?.toString().orEmpty()
        if (label.isEmpty()) {
            Icon(Icons.Default.SmartToy, contentDescription = null, tint = colors.second, modifier = Modifier.size((size * 0.52f).dp))
        } else {
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = colors.second)
        }
    }
}

@Composable
private fun ModelSearchBar(searchText: String, onSearchTextChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(25.dp, PillShape, clip = false, ambientColor = Color.Black.copy(alpha = 0.15f), spotColor = Color.Black.copy(alpha = 0.18f))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
                    )
                ),
                PillShape
            )
            .border(
                width = 0.8.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.05f)
                    )
                ),
                shape = PillShape
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        BasicTextField(
            value = searchText,
            onValueChange = onSearchTextChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                fontWeight = FontWeight.SemiBold
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Box {
                    if (searchText.isEmpty()) {
                        Text(
                            choose("Search models or providers", "搜索模型或提供商"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    innerTextField()
                }
            }
        )
        if (searchText.isNotEmpty()) {
            IconButton(onClick = { onSearchTextChange("") }, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.Close, contentDescription = choose("Clear", "清空"), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun ModelEmptyState(
    isLoading: Boolean,
    hasQuery: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 90.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp), color = AccentBlue)
            Text(choose("Syncing model catalog...", "正在同步模型目录..."), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Box(
                modifier = Modifier.size(80.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(if (errorMessage != null) Icons.Default.ErrorOutline else Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (hasQuery) choose("No matching models", "没有匹配的模型") else choose("No models available", "暂无可用模型"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    errorMessage ?: if (hasQuery) choose("Try another keyword.", "换个关键词再试。") else choose("Make sure the gateway is online and model providers are configured correctly.", "请确认网关在线，并且模型提供商已正确配置。"),
                    modifier = Modifier.padding(horizontal = 32.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            if (!hasQuery) {
                Button(
                    onClick = onRefresh,
                    shape = PillShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(8.dp))
                    Text(choose("Refresh now", "立即刷新"), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ProcessingOverlay(operationsLocked: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 40.dp)
                .shadow(20.dp, RoundedCornerShape(32.dp), clip = false, spotColor = Color.Black.copy(alpha = 0.15f))
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                .border(0.6.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(32.dp))
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(64.dp),
                    strokeWidth = 3.dp,
                    color = AccentBlue.copy(alpha = 0.12f),
                    trackColor = Color.Transparent
                )
                CircularProgressIndicator(
                    modifier = Modifier.size(42.dp),
                    strokeWidth = 3.5.dp,
                    color = AccentBlue,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
            
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (operationsLocked) choose("Gateway is recovering", "网关正在恢复") else choose("Waiting for OpenClaw", "等待 OpenClaw 响应"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    if (operationsLocked) choose("After changing the default model, the host may need a brief restart and recovery.", "默认模型变更后，主机可能需要短暂重启恢复。") else choose("Setting the global default model. Please wait.", "正在设置全局默认模型，请稍候。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private object ModelCatalogPresentation {
    fun filteredProviders(groupedModels: Map<String, List<ModelItem>>, models: List<ModelItem>, searchText: String): List<String> {
        val term = searchText.trim()
        val defaultProvider = models.firstOrNull { it.isDefault }?.providerLabel
        return groupedModels.keys.filter { provider ->
            if (term.isEmpty()) return@filter true
            val providerModels = groupedModels[provider].orEmpty()
            provider.contains(term, ignoreCase = true) ||
                providerModels.any {
                    it.displayName.contains(term, ignoreCase = true) ||
                        it.providerLabel.contains(term, ignoreCase = true) ||
                        it.modelId.contains(term, ignoreCase = true)
                }
        }.sortedWith { left, right ->
            when {
                left == defaultProvider && right != defaultProvider -> -1
                right == defaultProvider && left != defaultProvider -> 1
                else -> left.compareTo(right, ignoreCase = true)
            }
        }
    }

    fun filteredModels(models: List<ModelItem>, searchText: String): List<ModelItem> {
        val term = searchText.trim()
        return models.filter { model ->
            term.isEmpty() ||
                model.displayName.contains(term, ignoreCase = true) ||
                model.providerLabel.contains(term, ignoreCase = true) ||
                model.modelId.contains(term, ignoreCase = true)
        }.sortedWith(
            compareByDescending<ModelItem> { it.isDefault }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayName }
        )
    }
}

private val ModelItem.providerLabel: String
    get() = provider.ifBlank { providerId.ifBlank { "Unknown" } }

private val ModelItem.displayContextWindow: String
    get() = formatContextWindow(contextWindow)

private fun formatContextWindow(value: String?): String {
    val trimmed = value?.trim().orEmpty()
    if (trimmed.isEmpty() || trimmed == "--") return "--"
    val numeric = trimmed.replace(",", "").toLongOrNull() ?: return trimmed
    return when {
        numeric >= 1_000_000 -> "${numeric / 1_000_000}M tokens"
        numeric >= 1_000 -> "${numeric / 1_000}K tokens"
        else -> "$numeric tokens"
    }
}

private fun providerColors(model: ModelItem): Pair<Color, Color> {
    val key = "${model.providerId} ${model.provider}".lowercase()
    return when {
        "openai" in key -> Color(0xFF9EEBBD) to Color(0xFF1AA37A)
        "anthropic" in key || "claude" in key -> Color(0xFFFBE0BD) to Color(0xFFD98626)
        "google" in key || "gemini" in key -> Color(0xFFD6E5FF) to Color(0xFF2159F2)
        "moonshot" in key || "kimi" in key -> Color(0xFFDEDEFF) to Color(0xFF5A5CE2)
        "openrouter" in key -> Color(0xFFF78C33) to Color.White
        "deepseek" in key -> Color(0xFF14A6B3) to Color.White
        "qwen" in key || "modelstudio" in key || "alibaba" in key -> Color(0xFF2E78EA) to Color.White
        "mistral" in key -> Color(0xFFFC942E) to Color.White
        "xai" in key || "grok" in key -> Color(0xFF33333D) to Color.White
        "azure" in key || "microsoft" in key -> Color(0xFF338AF2) to Color.White
        "cohere" in key -> Color(0xFF45C28C) to Color.White
        "meta" in key || "llama" in key -> Color(0xFF4582F5) to Color.White
        else -> Color(0xFFE9EEF7) to Color(0xFF667085)
    }
}

private suspend fun waitForGatewayRecoveryAfterDefaultModelChange(
    gatewayStore: GatewayStore,
    gatewayId: String
): Boolean {
    val deadline = System.currentTimeMillis() + 90_000
    var observedRestartSignal = false

    while (System.currentTimeMillis() < deadline) {
        gatewayStore.loadGateways()
        val gateway = gatewayStore.state.value.gateways.firstOrNull { it.id == gatewayId }
        if (gateway != null) {
            if (gatewayNeedsRestartRecoveryWait(gateway)) {
                observedRestartSignal = true
            }
            if (observedRestartSignal && gatewayIsFullyOnline(gateway)) {
                return true
            }
        }
        delay(1_000)
    }

    return false
}

private fun gatewayIsFullyOnline(gateway: GatewaySummary): Boolean {
    if (gateway.aggregateStatus != AggregateStatus.online) return false
    val relayHostOnline = gateway.statuses.firstOrNull { it.phase == ConnectionPhase.relayHost }?.status == AggregateStatus.online
    if (!relayHostOnline) return false

    val hostGatewayStatus = gateway.statuses.firstOrNull { it.phase == ConnectionPhase.hostGateway } ?: return false
    if (hostGatewayStatus.status != AggregateStatus.online) return false

    val detail = hostGatewayStatus.detail.trim()
    val stillWaitingForOpenClaw = detail.contains("等待 OpenClaw", ignoreCase = true) ||
        detail.contains("waiting openclaw", ignoreCase = true) ||
        detail.contains("relay_connected", ignoreCase = true)

    return !stillWaitingForOpenClaw
}

private fun gatewayNeedsRestartRecoveryWait(gateway: GatewaySummary): Boolean {
    if (gateway.aggregateStatus != AggregateStatus.online) return true

    val relayHostStatus = gateway.statuses.firstOrNull { it.phase == ConnectionPhase.relayHost }
    if (relayHostStatus?.status != AggregateStatus.online) return true

    val hostGatewayStatus = gateway.statuses.firstOrNull { it.phase == ConnectionPhase.hostGateway } ?: return true
    if (hostGatewayStatus.status != AggregateStatus.online) return true

    val detail = hostGatewayStatus.detail.trim()
    return detail.contains("等待 OpenClaw", ignoreCase = true) ||
        detail.contains("waiting openclaw", ignoreCase = true) ||
        detail.contains("relay_connected", ignoreCase = true) ||
        detail.contains("connecting openclaw", ignoreCase = true) ||
        detail.contains("正在连接 openclaw", ignoreCase = true) ||
        detail.contains("openclaw 未连接", ignoreCase = true) ||
        detail.contains("openclaw 连接异常", ignoreCase = true) ||
        detail.contains("openclaw 重试中", ignoreCase = true)
}
