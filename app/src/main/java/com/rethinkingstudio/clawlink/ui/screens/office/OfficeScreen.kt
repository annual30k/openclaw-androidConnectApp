package com.rethinkingstudio.clawlink.ui.screens.office

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.core.models.office.OfficeScenePlanner
import com.rethinkingstudio.clawlink.core.state.auth.AuthStore
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import com.rethinkingstudio.clawlink.ui.screens.office.components.OfficePanelCard
import com.rethinkingstudio.clawlink.ui.screens.office.components.PixelOfficeScene
import com.rethinkingstudio.clawlink.ui.screens.office.components.PulsingStatusDot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficeScreen(
    authStore: AuthStore,
    gatewayStore: GatewayStore,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    
    // Force landscape orientation with more stability
    DisposableEffect(activity) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        if (originalOrientation != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            activity?.requestedOrientation = originalOrientation
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val gatewayState by gatewayStore.state.collectAsState()
    var showStatusSheet by remember { mutableStateOf(false) }
    
    val scene = remember(gatewayState.gateways, gatewayState.selectedGatewayId) {
        OfficeScenePlanner.scene(
            gateways = gatewayState.gateways,
            selectedGatewayId = gatewayState.selectedGatewayId
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        PixelOfficeScene(
            scene = scene,
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                ) { showStatusSheet = true }
        )

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .padding(start = 16.dp, top = 16.dp)
                .size(48.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Black.copy(alpha = 0.30f),
                contentColor = Color.White
            )
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.common_action_back)
            )
        }

        if (showStatusSheet) {
            ModalBottomSheet(
                onDismissRequest = { showStatusSheet = false },
                containerColor = Color(0xFF191E29),
                contentColor = Color.White,
                dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.42f)) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    scene.focusAgent?.let { agent ->
                        OfficePanelCard(
                            modifier = Modifier.fillMaxWidth(),
                            title = agent.displayName
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PulsingStatusDot(
                                    color = if (agent.isWorking) Color(0xFF4CAF50) else Color(0xFF9E9E9E)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = agent.activityTitle,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = agent.activityDetail,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    OfficePanelCard(
                        modifier = Modifier.fillMaxWidth(),
                        title = stringResource(R.string.office_panel_dynamics)
                    ) {
                        Text(
                            text = "${scene.activeCount} Working · ${scene.onlineCount} Online",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White
                        )
                        Text(
                            text = stringResource(R.string.office_dynamics_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}
