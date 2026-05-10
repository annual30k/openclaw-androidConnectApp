package com.rethinkingstudio.clawlink.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

enum class ClawLinkAlertActionRole {
    Default,
    Cancel,
    Destructive
}

@Composable
fun ClawLinkAlertDialog(
    title: String,
    message: String? = null,
    onDismissRequest: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    confirmRole: ClawLinkAlertActionRole = ClawLinkAlertActionRole.Default,
    confirmEnabled: Boolean = true,
    confirmLoading: Boolean = false,
    dismissText: String? = null,
    onDismissAction: (() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.42f))
        ) {
            val density = LocalDensity.current
            var dialogHeightPx by remember { mutableIntStateOf(0) }
            val containerHeightPx = with(density) { maxHeight.roundToPx() }
            val visualLiftPx = with(density) { 48.dp.roundToPx() }
            val centeredTopPx = ((containerHeightPx - dialogHeightPx) / 2 - visualLiftPx).coerceAtLeast(0)

            Surface(
                modifier = modifier
                    .align(Alignment.TopCenter)
                    .padding(top = with(density) { centeredTopPx.toDp() })
                    .fillMaxWidth(0.72f)
                    .widthIn(max = 360.dp)
                    .onSizeChanged { dialogHeightPx = it.height },
                shape = RoundedCornerShape(34.dp),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 26.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 21.sp),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Start,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (content != null) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                content = content
                            )
                        } else if (!message.isNullOrBlank()) {
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
                                textAlign = TextAlign.Start,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    if (dismissText != null && onDismissAction != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AlertActionButton(
                                text = dismissText,
                                role = ClawLinkAlertActionRole.Cancel,
                                enabled = true,
                                loading = false,
                                onClick = onDismissAction,
                                modifier = Modifier.weight(1f)
                            )
                            AlertActionButton(
                                text = confirmText,
                                role = confirmRole,
                                enabled = confirmEnabled,
                                loading = confirmLoading,
                                onClick = onConfirm,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        AlertActionButton(
                            text = confirmText,
                            role = confirmRole,
                            enabled = confirmEnabled,
                            loading = confirmLoading,
                            onClick = onConfirm,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertActionButton(
    text: String,
    role: ClawLinkAlertActionRole,
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val textColor = when (role) {
        ClawLinkAlertActionRole.Default -> MaterialTheme.colorScheme.onSurface
        ClawLinkAlertActionRole.Cancel -> MaterialTheme.colorScheme.onSurface
        ClawLinkAlertActionRole.Destructive -> MaterialTheme.colorScheme.error
    }
    val contentColor = if (enabled) textColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    Box(
        modifier = modifier
            .height(46.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(999.dp)
            )
            .clickable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = contentColor
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 20.sp),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = contentColor
            )
        }
    }
}
