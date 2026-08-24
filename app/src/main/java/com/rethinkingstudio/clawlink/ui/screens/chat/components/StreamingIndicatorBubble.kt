package com.rethinkingstudio.clawlink.ui.screens.chat.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.rethinkingstudio.clawlink.ui.screens.chat.ChatColors

internal object StreamingIndicatorMotion {
    const val durationMillis = 720
    const val staggerMillis = 160
    const val initialScale = 0.82f
    const val targetScale = 1f
    const val initialAlpha = 0.38f
    const val targetAlpha = 1f
    val initialVerticalOffset = 1.dp
    val targetVerticalOffset = (-2).dp

    fun verticalOffset(progress: Float): Dp = lerp(
        initialVerticalOffset,
        targetVerticalOffset,
        progress.coerceIn(0f, 1f)
    )
}

@Composable
fun StreamingIndicatorBubble(
    progressLabel: String = "正在思考…",
    modifier: Modifier = Modifier
) {
    val statusLabel = progressLabel.trim().ifEmpty { "正在思考…" }
    Box(
        modifier = modifier
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(22.dp),
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color.Black.copy(alpha = 0.08f)
            )
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shape = RoundedCornerShape(22.dp)
            )
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = statusLabel,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
            fontWeight = FontWeight.Medium,
            color = ChatColors.secondaryText,
            maxLines = 1
        )
    }
}

@Composable
fun InlineStreamingIndicator(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(top = 1.dp, bottom = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "inline-dots")

        repeat(3) { index ->
            val animationProgress by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = StreamingIndicatorMotion.durationMillis, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * StreamingIndicatorMotion.staggerMillis)
                ),
                label = "inline-dot-$index"
            )

            Box(
                modifier = Modifier
                    .size(6.dp)
                    .offset(y = StreamingIndicatorMotion.verticalOffset(animationProgress))
                    .testTag("inline_streaming_indicator_dot_$index")
                    .scale(
                        StreamingIndicatorMotion.initialScale +
                            ((StreamingIndicatorMotion.targetScale - StreamingIndicatorMotion.initialScale) * animationProgress)
                    )
                    .alpha(
                        StreamingIndicatorMotion.initialAlpha +
                            ((StreamingIndicatorMotion.targetAlpha - StreamingIndicatorMotion.initialAlpha) * animationProgress)
                    )
                    .background(
                        color = ChatColors.secondaryText.copy(alpha = 0.72f),
                        shape = CircleShape
                    )
            )
        }
    }
}
