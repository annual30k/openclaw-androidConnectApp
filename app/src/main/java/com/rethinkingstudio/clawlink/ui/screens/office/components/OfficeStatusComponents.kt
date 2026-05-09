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
internal fun OfficeStatusOverlay(
    scene: OfficeSceneSnapshot,
    focusAgent: OfficeAgentSnapshot?,
    toolAgent: OfficeAgentSnapshot?,
    sceneMode: OfficePresenceMode,
    sceneTint: Color,
    selectedGateway: GatewaySummary?,
    onDismiss: () -> Unit
) {
    val prefersExpandedTaskLayout = toolAgent?.prefersExpandedTaskLayout() == true
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1E29))
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                onClick = onDismiss
            )
            .padding(start = 48.dp, top = 5.dp, end = 48.dp, bottom = 5.dp)
    ) {
        if (prefersExpandedTaskLayout) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        onClick = {}
                    ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OfficeDynamicsCard(
                    scene = scene,
                    focusAgent = focusAgent,
                    toolAgent = toolAgent,
                    sceneMode = sceneMode,
                    sceneTint = sceneTint,
                    expanded = true,
                    modifier = Modifier.fillMaxWidth()
                )

                GatewayIndicatorCard(
                    gateway = selectedGateway,
                    focusAgent = focusAgent,
                    sceneTint = sceneTint,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        onClick = {}
                    ),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                OfficeDynamicsCard(
                    scene = scene,
                    focusAgent = focusAgent,
                    toolAgent = toolAgent,
                    sceneMode = sceneMode,
                    sceneTint = sceneTint,
                    modifier = Modifier.weight(1f)
                )

                GatewayIndicatorCard(
                    gateway = selectedGateway,
                    focusAgent = focusAgent,
                    sceneTint = sceneTint,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
internal fun OfficeDynamicsCard(
    scene: OfficeSceneSnapshot,
    focusAgent: OfficeAgentSnapshot?,
    toolAgent: OfficeAgentSnapshot?,
    sceneMode: OfficePresenceMode,
    sceneTint: Color,
    expanded: Boolean = false,
    modifier: Modifier = Modifier
) {
    val subtitle = toolAgent?.activityToolName?.trim()?.takeIf { it.isNotEmpty() }
        ?: toolAgent?.activityKind?.localizedDisplayName()
        ?: stringResource(R.string.office_dynamics_subtitle)
    OfficePanelCard(
        modifier = modifier,
        title = stringResource(R.string.office_panel_dynamics),
        subtitle = subtitle,
        accent = Color(0xFFF2C94C)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                toolAgent?.let {
                    StatusGlyph(sceneTint)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    Text(
                        text = toolAgent?.activityTitle?.takeIf { it.isNotBlank() }
                            ?: focusAgent?.activityTitle?.takeIf { it.isNotBlank() }
                            ?: sceneMode.title(),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 24.sp),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = toolAgent?.activityDetail?.takeIf { it.isNotBlank() }
                            ?: focusAgent?.activityDetail?.takeIf { it.isNotBlank() }
                            ?: sceneMode.subtitle(),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = Color.White.copy(alpha = 0.78f),
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = if (expanded) 4 else 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (toolAgent != null) {
                DividerLine()
                DetailRow(title = stringResource(R.string.office_detail_agent), value = toolAgent.displayName)
                DetailRow(title = stringResource(R.string.office_detail_progress), value = progressText(toolAgent))
                ToolDetailBlock(agent = toolAgent, expanded = expanded)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatChip(title = stringResource(R.string.office_stat_working), value = scene.activeCount.toString(), modifier = Modifier.weight(1f))
                    StatChip(title = stringResource(R.string.office_stat_online), value = scene.onlineCount.toString(), modifier = Modifier.weight(1f))
                    StatChip(title = stringResource(R.string.office_stat_offline), value = scene.offlineCount.toString(), modifier = Modifier.weight(1f))
                }
            }

            focusAgent?.activityUpdatedAt?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = stringResource(R.string.office_last_active, formatChatTimestamp(it)),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = Color.White.copy(alpha = 0.44f),
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
internal fun GatewayIndicatorCard(
    gateway: GatewaySummary?,
    focusAgent: OfficeAgentSnapshot?,
    sceneTint: Color,
    modifier: Modifier = Modifier
) {
    OfficePanelCard(
        modifier = modifier,
        title = "Gateway",
        subtitle = gateway?.displayName ?: stringResource(R.string.office_no_host),
        accent = gateway?.aggregateStatus?.gatewayTint() ?: sceneTint
    ) {
        if (gateway == null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.office_no_host),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.office_no_host_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.72f)
                )
            }
            return@OfficePanelCard
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusDot(status = gateway.aggregateStatus)
                Text(
                    text = gateway.platform.uppercase(),
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = Color.White.copy(alpha = 0.60f),
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            DetailRow(title = stringResource(R.string.office_detail_model), value = gateway.currentModel.trim())
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DetailRow(title = stringResource(R.string.office_detail_context), value = gateway.contextUsage.trim())
                ContextProgressBar(usage = gateway.contextUsage)
            }
            val lastActiveText = focusAgent?.activityUpdatedAt?.takeIf { it.isNotBlank() }
                ?.let { formatChatTimestamp(it) }
                ?: formatChatTimestamp(gateway.lastSeenAt)
            DetailRow(title = stringResource(R.string.office_detail_online), value = lastActiveText)
        }
    }
}

@Composable
internal fun ToolDetailBlock(agent: OfficeAgentSnapshot, expanded: Boolean = false) {
    val isCompleted = (agent.activityProgress ?: 0.0) >= 0.99
    val toolName = agent.activityToolName?.trim().orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
        Row(
            modifier = Modifier
                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⚡", fontSize = 10.sp, color = if (isCompleted) Color(0xFF4CAF50) else Color(0xFFE05A5A))
            Text(
                text = if (isCompleted) "Tool output" else "Tool call",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = toolName.ifBlank { if (isCompleted) "result" else "executing" },
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = Color.White.copy(alpha = 0.50f),
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = agent.activityDetail,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (expanded) Modifier.heightIn(min = 64.dp) else Modifier)
                .background(Color.Black.copy(alpha = 0.24f), RoundedCornerShape(4.dp))
                .padding(8.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = Color.White.copy(alpha = 0.80f),
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
internal fun StatusGlyph(tint: Color) {
    val glyph = ImageBitmap.imageResource(R.drawable.star_working_sheet)
    Box(
        modifier = Modifier
            .size(22.dp)
            .background(Color(0xFFF5EED9), RoundedCornerShape(6.dp))
            .border(1.5.dp, tint.copy(alpha = 0.85f), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = BitmapPainter(
                image = glyph,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(300, 300)
            ),
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
internal fun DetailRow(title: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Text(
            text = title,
            modifier = Modifier.width(42.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = Color.White.copy(alpha = 0.44f),
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value.ifBlank { "—" },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
            color = Color.White.copy(alpha = 0.92f),
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun StatChip(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = Color.White.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = Color.White.copy(alpha = 0.58f), fontWeight = FontWeight.SemiBold)
            Text(value, style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp), color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun StatusDot(status: AggregateStatus) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(status.gatewayTint(), CircleShape)
        )
        Text(
            text = status.displayName(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp),
            color = status.gatewayTint(),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
internal fun ContextProgressBar(usage: String) {
    val percentage = remember(usage) { contextUsagePercentage(usage) }
    val color = when {
        percentage < 0.4f -> Color(0xFF4CAF50)
        percentage < 0.7f -> Color(0xFFF2C94C)
        else -> Color(0xFFE05A5A)
    }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 52.dp)
            .height(6.dp)
    ) {
        drawRoundRect(
            color = Color.White.copy(alpha = 0.10f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f, size.height / 2f)
        )
        drawRoundRect(
            color = color,
            size = androidx.compose.ui.geometry.Size((size.width * percentage).coerceAtLeast(8f), size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f, size.height / 2f)
        )
    }
}

@Composable
internal fun DividerLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.12f))
    )
}

