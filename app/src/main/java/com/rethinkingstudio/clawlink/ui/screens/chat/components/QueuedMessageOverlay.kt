package com.rethinkingstudio.clawlink.ui.screens.chat.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage

// Android Surface 的 3dp 阴影会向外占用间距；32dp 几何间距可在阴影后保留约 24dp 的可见留白，
// 让队列浮层与 Skills / 模型栏形成清晰的光学分区。
internal val queuedMessageOverlayGap = 32.dp
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
