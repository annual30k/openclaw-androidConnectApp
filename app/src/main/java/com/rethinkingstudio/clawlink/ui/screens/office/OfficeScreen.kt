package com.rethinkingstudio.clawlink.ui.screens.office

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.core.models.OfficeActivityKind
import com.rethinkingstudio.clawlink.core.models.OfficeAgentSnapshot
import com.rethinkingstudio.clawlink.core.models.OfficeSceneSnapshot
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.gateway.AggregateStatus
import com.rethinkingstudio.clawlink.core.models.gateway.GatewaySummary
import com.rethinkingstudio.clawlink.core.models.office.OfficeScenePlanner
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
import com.rethinkingstudio.clawlink.core.state.auth.AuthStore
import com.rethinkingstudio.clawlink.core.state.chat.ChatStore
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import com.rethinkingstudio.clawlink.ui.screens.chat.formatChatTimestamp
import com.rethinkingstudio.clawlink.ui.screens.office.components.OfficePanelCard
import com.rethinkingstudio.clawlink.ui.screens.office.components.PixelOfficeScene

@Composable
fun OfficeScreen(
    authStore: AuthStore,
    gatewayStore: GatewayStore,
    chatStore: ChatStore,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    
    // Force landscape orientation with more stability
    DisposableEffect(activity) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            activity?.requestedOrientation = originalOrientation
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val gatewayState by gatewayStore.state.collectAsState()
    val chatState by chatStore.state.collectAsState()
    var showStatusSheet by remember { mutableStateOf(false) }
    
    val pendingRunsByGateway = remember(chatState.currentGatewayId, chatState.isStreaming) {
        val gatewayId = chatState.currentGatewayId?.takeIf { it.isNotBlank() }
        if (gatewayId != null && chatState.isStreaming) {
            mapOf(gatewayId to listOf(Unit))
        } else {
            emptyMap()
        }
    }
    val activeOfficeReply = remember(chatState.currentGatewayId, chatState.isStreaming, chatState.messages) {
        latestStreamingReplyText(chatState.messages)
    }
    val officeGateways = remember(
        gatewayState.gateways,
        gatewayState.appRelayStatus,
        chatState.currentGatewayId,
        chatState.isStreaming,
        activeOfficeReply
    ) {
        val activeGatewayId = chatState.currentGatewayId?.takeIf { it.isNotBlank() }
        val activityGateways = if (activeGatewayId == null || !chatState.isStreaming || activeOfficeReply.isBlank()) {
            gatewayState.gateways
        } else {
            gatewayState.gateways.map { gateway ->
                if (gateway.id != activeGatewayId) {
                    gateway
                } else {
                    gateway.copy(
                        officeActivityKind = gateway.officeActivityKind ?: "writing",
                        officeActivityTitle = gateway.officeActivityTitle ?: choose("Replying", "回复中"),
                        officeActivityDetail = activeOfficeReply,
                        officeActivityPhase = gateway.officeActivityPhase ?: "streaming",
                        officeActivityUpdatedAt = gateway.officeActivityUpdatedAt ?: java.time.Instant.now().toString()
                    )
                }
            }
        }

        activityGateways.map { gateway ->
            gateway.copy(
                aggregateStatus = GatewayStore.aggregateStatusForChain(
                    gateway,
                    gatewayState.appRelayStatus
                )
            )
        }
    }

    val scene = remember(officeGateways, gatewayState.selectedGatewayId, pendingRunsByGateway) {
        OfficeScenePlanner.scene(
            gateways = officeGateways,
            selectedGatewayId = gatewayState.selectedGatewayId,
            pendingRuns = pendingRunsByGateway
        )
    }
    val selectedGateway = gatewayState.selectedGateway
    val focusAgent = scene.focusAgent
    val sceneMode = officePresenceMode(focusAgent)
    val sceneTint = sceneMode.tint
    val toolAgent = focusAgent?.takeIf { it.shouldShowToolDetail() }
    val shouldShowOfficeOccupants = gatewayState.isAppRelayOnline &&
        focusAgent?.aggregateStatus != AggregateStatus.offline
    val renderScene = remember(scene, shouldShowOfficeOccupants) {
        if (!shouldShowOfficeOccupants) {
            scene.copy(agents = emptyList())
        } else {
            val focusAgentId = scene.focusAgent?.id
            scene.copy(
                agents = scene.agents.filter { agent ->
                    agent.id == focusAgentId ||
                        (agent.aggregateStatus != AggregateStatus.offline &&
                            agent.activityKind != OfficeActivityKind.SLEEPING &&
                            agent.activityKind != OfficeActivityKind.OFFLINE)
                }
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(OfficeSceneLetterboxColor)) {
        PixelOfficeScene(
            scene = renderScene,
            showsOccupants = shouldShowOfficeOccupants,
            showsRestingCat = sceneMode == OfficePresenceMode.SLEEPING,
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                ) { showStatusSheet = true }
        )

        Surface(
            modifier = Modifier
                .padding(start = 32.dp, top = 32.dp)
                .size(44.dp)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onBack
                ),
            shape = RoundedCornerShape(14.dp),
            color = Color.Black.copy(alpha = 0.45f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
            shadowElevation = 0.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.common_action_back),
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        if (showStatusSheet) {
            OfficeStatusOverlay(
                scene = scene,
                focusAgent = focusAgent,
                toolAgent = toolAgent,
                sceneMode = sceneMode,
                sceneTint = sceneTint,
                selectedGateway = selectedGateway,
                onDismiss = { showStatusSheet = false }
            )
        }
    }
}
