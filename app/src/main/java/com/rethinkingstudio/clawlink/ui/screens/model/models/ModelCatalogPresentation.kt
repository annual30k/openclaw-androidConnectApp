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

internal object ModelCatalogPresentation {
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

internal val ModelItem.providerLabel: String
    get() = provider.ifBlank { providerId.ifBlank { choose("Unknown", "未知") } }

internal val ModelItem.displayContextWindow: String
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

internal fun providerColors(model: ModelItem): Pair<Color, Color> {
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

internal suspend fun waitForGatewayRecoveryAfterDefaultModelChange(
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

internal fun gatewayIsFullyOnline(gateway: GatewaySummary): Boolean {
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

internal fun gatewayNeedsRestartRecoveryWait(gateway: GatewaySummary): Boolean {
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
