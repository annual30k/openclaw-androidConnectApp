package com.rethinkingstudio.clawlink.ui.screens.chat.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage

// 与微信端的紧凑悬浮比例保持一致；8dp 足以容纳 Surface 阴影，又不会在队列和输入区之间
// 留出一块看起来像空白控件的区域。
internal val queuedMessageOverlayGap = 8.dp
internal val queuedMessageOverlayHorizontalPadding = 14.dp

/**
 * Places the queue above the measured composer as a sibling overlay. The queue is deliberately
 * outside the conversation/composer Column so expanding it can never resize or move the composer.
 */
@Composable
internal fun BoxScope.QueuedMessageOverlay(
    messages: List<ChatMessage>,
    composerHeight: Dp,
    onMove: (ChatMessage, Int) -> Unit,
    onRemove: (ChatMessage) -> Unit,
    modifier: Modifier = Modifier
) {
    QueuedMessagePanel(
        messages = messages,
        onMove = onMove,
        onRemove = onRemove,
        modifier = modifier
            .align(Alignment.BottomCenter)
            .padding(
                start = queuedMessageOverlayHorizontalPadding,
                end = queuedMessageOverlayHorizontalPadding,
                bottom = composerHeight + queuedMessageOverlayGap
            )
    )
}
