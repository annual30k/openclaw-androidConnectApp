package com.rethinkingstudio.clawlink.ui.screens.settings.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rethinkingstudio.clawlink.core.models.gateway.AggregateStatus
import com.rethinkingstudio.clawlink.core.models.gateway.GatewayStatus
import kotlin.math.pow

enum class GatewayFlowVisualState {
    Live, Pending, Degraded, Broken;

    companion object {
        fun fromStatuses(leading: AggregateStatus, trailing: AggregateStatus): GatewayFlowVisualState {
            return when {
                leading == AggregateStatus.offline || trailing == AggregateStatus.offline -> Broken
                leading == AggregateStatus.connecting || trailing == AggregateStatus.connecting -> Pending
                leading == AggregateStatus.partial || trailing == AggregateStatus.partial -> Degraded
                else -> Live
            }
        }
    }

    val tint: Color
        @Composable
        get() = when (this) {
            Live -> Color(0xFF4ADE80) // success
            Pending -> Color(0xFFFBBF24) // warning
            Degraded -> Color(0xFF60A5FA) // accentSoft
            Broken -> Color(0xFFF87171) // danger
        }
}

@Composable
fun GatewayFlowPanel(
    statuses: List<GatewayStatus>,
    modifier: Modifier = Modifier,
    isPaused: Boolean = false
) {
    val visibleStatuses = statuses.take(3)
    val infiniteTransition = rememberInfiniteTransition(label = "flow")
    
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    val segmentStates = remember(visibleStatuses) {
        if (visibleStatuses.size > 1) {
            visibleStatuses.zipWithNext { leading, trailing ->
                GatewayFlowVisualState.fromStatuses(leading.status, trailing.status)
            }
        } else emptyList()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(136.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF18253A), Color(0xFF101828), Color(0xFF0D1626)),
                    start = Offset.Zero,
                    end = Offset.Infinite
                )
            )
            .drawWithContent {
                drawContent()
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.08f),
                    style = Stroke(width = 1.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(28.dp.toPx())
                )
            }
    ) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val density = LocalDensity.current
        val dp28 = with(density) { 28.dp.toPx() }
        val dp38 = with(density) { 38.dp.toPx() }
        val dp44 = with(density) { 44.dp.toPx() }
        val dp50 = with(density) { 50.dp.toPx() }
        val dp18 = with(density) { 18.dp.toPx() }
        val dp16 = with(density) { 16.dp.toPx() }
        
        val horizontalInset = (width * 0.13f).coerceIn(dp28, dp38)
        val nodeY = (height * 0.36f).coerceIn(dp44, dp50)
        val spacing = if (visibleStatuses.size > 1) (width - horizontalInset * 2) / (visibleStatuses.size - 1) else 0f
        
        val nodeCenters = visibleStatuses.indices.map { index ->
            Offset(horizontalInset + index * spacing, nodeY)
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 24.dp, y = (-38).dp)
                .size(122.dp)
                .clip(CircleShape)
                .background(Color(0xFF0F73ED).copy(alpha = 0.11f))
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-18).dp, y = 28.dp)
                .size(96.dp)
                .clip(CircleShape)
                .background(Color(0xFF2BBD66).copy(alpha = 0.1f))
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            // Ambient Highlights
            nodeCenters.forEach { center ->
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.05f), Color.Transparent),
                        center = center,
                        radius = 34.dp.toPx()
                    ),
                    center = center,
                    radius = 34.dp.toPx()
                )
            }

            // Connectors
            segmentStates.forEachIndexed { index, state ->
                val start = nodeCenters[index]
                val end = nodeCenters[index + 1]
                val control = Offset((start.x + end.x) / 2, nodeY - (if (index == 0) dp18 else dp16))
                
                val path = Path().apply {
                    moveTo(start.x, start.y)
                    quadraticTo(control.x, control.y, end.x, end.y)
                }

                // Base path
                drawPath(
                    path = path,
                    color = Color.White.copy(alpha = 0.12f),
                    style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
                )

                val tint = when (state) {
                    GatewayFlowVisualState.Live -> Color(0xFF4ADE80)
                    GatewayFlowVisualState.Pending -> Color(0xFFFBBF24)
                    GatewayFlowVisualState.Degraded -> Color(0xFF60A5FA)
                    GatewayFlowVisualState.Broken -> Color(0xFFF87171)
                }

                when (state) {
                    GatewayFlowVisualState.Live -> {
                        drawPath(
                            path = path,
                            brush = Brush.linearGradient(
                                colors = listOf(tint.copy(alpha = 0.1f), tint.copy(alpha = 0.7f), tint.copy(alpha = 0.1f)),
                                start = start,
                                end = end
                            ),
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                        
                        // Particles
                        val phases = listOf(0.0f, 0.23f, 0.48f, 0.74f)
                        phases.forEach { phase ->
                            val progress = ((time * 0.26f) + phase) % 1f
                            val pos = getQuadPoint(start, control, end, progress)
                            drawParticle(pos, tint)
                        }
                    }
                    GatewayFlowVisualState.Pending -> {
                        // Dashed path
                        drawPath(
                            path = path,
                            color = tint.copy(alpha = 0.7f),
                            style = Stroke(
                                width = 2.5f.dp.toPx(),
                                cap = StrokeCap.Round,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 24f), -time * 50f)
                            )
                        )
                        val progress = (time * 0.18f) % 1f
                        drawParticle(getQuadPoint(start, control, end, progress), tint)
                    }
                    GatewayFlowVisualState.Degraded -> {
                        drawPath(
                            path = path,
                            color = tint.copy(alpha = 0.3f),
                            style = Stroke(width = 2.5f.dp.toPx(), cap = StrokeCap.Round)
                        )
                        val progress = 0.26f
                        drawParticle(getQuadPoint(start, control, end, progress), tint)
                    }
                    GatewayFlowVisualState.Broken -> {
                        // Simplified broken paths
                    }
                }
            }
        }

        // Pulse effect for Relay (middle node)
        if (visibleStatuses.size > 1) {
            val relayStatus = visibleStatuses[1].status
            if (relayStatus != AggregateStatus.offline) {
                val pulseProgress = (time * 0.55f) % 1f
                val relayCenter = nodeCenters[1]
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .offset(
                            x = with(LocalDensity.current) { relayCenter.x.toDp() - 34.dp },
                            y = with(LocalDensity.current) { relayCenter.y.toDp() - 34.dp }
                        )
                ) {
                    GatewayRelayPulse(
                        tint = getStatusTint(relayStatus),
                        progress = pulseProgress
                    )
                }
            }
        }

        // Nodes
        visibleStatuses.forEachIndexed { index, status ->
            val center = nodeCenters[index]
            val icon = when (index) {
                0 -> Icons.Default.PhoneAndroid
                1 -> Icons.Default.Router
                else -> Icons.Default.DesktopWindows
            }
            val label = when (index) {
                0 -> "App"
                1 -> "Relay"
                else -> "Host"
            }
            
            GatewayFloatingNode(
                status = status,
                icon = icon,
                label = label,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = with(LocalDensity.current) { center.x.toDp() - 37.dp },
                        y = with(LocalDensity.current) { center.y.toDp() - 21.dp }
                    )
            )
        }
        
        // Broken markers
        segmentStates.forEachIndexed { index, state ->
            if (state == GatewayFlowVisualState.Broken) {
                val start = nodeCenters[index]
                val end = nodeCenters[index + 1]
                val control = Offset((start.x + end.x) / 2, nodeY - (if (index == 0) dp18 else dp16))
                val mid = getQuadPoint(start, control, end, 0.5f)
                
                GatewayBrokenMarker(
                    tint = state.tint,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(
                            x = with(LocalDensity.current) { mid.x.toDp() - 12.dp },
                            y = with(LocalDensity.current) { mid.y.toDp() - 14.dp }
                        )
                )
            }
        }
    }
}

@Composable
fun GatewayFloatingNode(
    status: GatewayStatus,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    val tint = getStatusTint(status.status)
    
    Column(
        modifier = modifier.width(74.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF101927).copy(alpha = 0.92f))
                    .drawWithContent {
                        drawCircle(Color(0xFF101927))
                        drawContent()
                        drawCircle(
                            color = tint.copy(alpha = if (status.status == AggregateStatus.offline) 0.55f else 0.92f),
                            style = Stroke(width = 1.2.dp.toPx())
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(18.dp)
                )
            }
            
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 4.dp, y = 5.dp)
                    .size(11.dp)
                    .background(tint, CircleShape)
                    .drawWithContent {
                        drawContent()
                        drawCircle(
                            color = Color(0xFF111926),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
            )
        }
        
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.94f)
        )
    }
}

@Composable
fun GatewayRelayPulse(
    tint: Color,
    progress: Float
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val baseOpacity = 0.25f
        
        // Outer ring
        drawCircle(
            color = tint.copy(alpha = baseOpacity * (1 - progress)),
            radius = (size.minDimension / 2) * (1.0f + progress * 0.4f),
            style = Stroke(width = 1.2.dp.toPx())
        )
        
        // Inner ring
        val innerProgress = (progress + 0.4f) % 1f
        drawCircle(
            color = tint.copy(alpha = (baseOpacity * 0.7f) * (1 - innerProgress)),
            radius = (size.minDimension / 2) * (0.9f + innerProgress * 0.3f),
            style = Stroke(width = 1.dp.toPx())
        )
    }
}

@Composable
fun GatewayBrokenMarker(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(Color(0xFF162235))
            .drawWithContent {
                drawContent()
                drawCircle(tint.copy(alpha = 0.88f), style = Stroke(width = 1.dp.toPx()))
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(11.dp)
        )
    }
}

private fun DrawScope.drawParticle(pos: Offset, tint: Color) {
    drawCircle(
        color = tint.copy(alpha = 0.2f),
        radius = 8.dp.toPx(),
        center = pos
    )
    drawCircle(
        color = tint.copy(alpha = 0.95f),
        radius = 2.5.dp.toPx(),
        center = pos
    )
}

private fun getQuadPoint(start: Offset, control: Offset, end: Offset, t: Float): Offset {
    val x = (1 - t).pow(2) * start.x + 2 * (1 - t) * t * control.x + t.pow(2) * end.x
    val y = (1 - t).pow(2) * start.y + 2 * (1 - t) * t * control.y + t.pow(2) * end.y
    return Offset(x, y)
}

@Composable
private fun getStatusTint(status: AggregateStatus): Color {
    return when (status) {
        AggregateStatus.online -> Color(0xFF4ADE80)
        AggregateStatus.connecting -> Color(0xFFFBBF24)
        AggregateStatus.partial -> Color(0xFF60A5FA)
        AggregateStatus.offline -> Color(0xFFF87171)
    }
}
