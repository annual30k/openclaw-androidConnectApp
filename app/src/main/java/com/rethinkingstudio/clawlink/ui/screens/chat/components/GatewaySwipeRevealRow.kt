package com.rethinkingstudio.clawlink.ui.screens.chat.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal fun gatewaySwipeRevealOffset(
    isRevealed: Boolean,
    dragX: Float,
    revealWidth: Float
): Float {
    val baseOffset = if (isRevealed) -revealWidth else 0f
    return (baseOffset + dragX).coerceIn(-revealWidth, 0f)
}

internal fun gatewaySwipeShouldReveal(
    isRevealed: Boolean,
    predictedEndTranslationX: Float,
    revealWidth: Float
): Boolean {
    val baseOffset = if (isRevealed) -revealWidth else 0f
    return baseOffset + predictedEndTranslationX < -revealWidth * 0.45f
}

@Composable
internal fun GatewaySwipeRevealRow(
    isRevealed: Boolean,
    actionLabel: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onRevealChange: (Boolean) -> Unit,
    onAction: () -> Unit,
    content: @Composable () -> Unit
) {
    val offset by animateDpAsState(
        targetValue = if (isRevealed) (-80).dp else 0.dp,
        label = "gateway_swipe_offset"
    )
    val actionTint = MaterialTheme.colorScheme.primary
    val actionLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
    ) {
        if (enabled && offset < 0.dp) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Column(
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onAction
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .shadow(4.dp, CircleShape)
                            .background(actionTint, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        actionLabel,
                        color = actionLabelColor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .offset(x = offset)
                .pointerInput(enabled) {
                    if (enabled) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            if (dragAmount < -10) onRevealChange(true)
                            if (dragAmount > 10) onRevealChange(false)
                        }
                    }
                }
        ) {
            content()
        }
    }
}
