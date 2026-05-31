package com.rethinkingstudio.clawlink.ui.screens.chat.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rethinkingstudio.clawlink.ui.screens.chat.ChatColors

@Composable
fun StreamingIndicatorBubble(
    modifier: Modifier = Modifier
) {
    Row(
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
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "dots")
        
        repeat(3) { index ->
            val animationProgress by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 720, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 160)
                ),
                label = "dot-$index"
            )
            
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .scale(0.82f + (0.18f * animationProgress))
                    .alpha(0.38f + (0.62f * animationProgress))
                    .background(
                        color = ChatColors.secondaryText.copy(alpha = 0.72f),
                        shape = CircleShape
                    )
            )
        }
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
                    animation = tween(durationMillis = 720, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 160)
                ),
                label = "inline-dot-$index"
            )

            Box(
                modifier = Modifier
                    .size(6.dp)
                    .scale(0.82f + (0.18f * animationProgress))
                    .alpha(0.38f + (0.62f * animationProgress))
                    .background(
                        color = ChatColors.secondaryText.copy(alpha = 0.72f),
                        shape = CircleShape
                    )
            )
        }
    }
}
