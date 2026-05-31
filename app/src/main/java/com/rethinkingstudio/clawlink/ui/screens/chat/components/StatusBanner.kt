package com.rethinkingstudio.clawlink.ui.screens.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.ui.components.ClawLinkCard
import com.rethinkingstudio.clawlink.ui.screens.chat.ChatColors

@Composable
internal fun UsageGuidePromptCard(onOpenUsageGuide: (() -> Unit)?) {
    Surface(
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                IconBadge(Icons.AutoMirrored.Filled.MenuBook)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.gateway_usage_guide_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp, lineHeight = 24.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        stringResource(R.string.gateway_usage_guide_prompt),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.sp),
                        color = ChatColors.secondaryText
                    )
                }
            }
            FullWidthCardButton(
                text = stringResource(R.string.gateway_usage_guide_button),
                icon = Icons.AutoMirrored.Filled.MenuBook,
                onClick = onOpenUsageGuide ?: {}
            )
        }
    }
}

@Composable
internal fun EmptyGatewayCard(onOpenSettings: (() -> Unit)?) {
    Surface(
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                IconBadge(Icons.Default.Dns)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.gateway_no_host_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp, lineHeight = 24.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        stringResource(R.string.gateway_no_host_detail),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.sp),
                        color = ChatColors.secondaryText
                    )
                }
            }
            Text(
                stringResource(R.string.gateway_no_host_hint),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = ChatColors.secondaryText,
                fontWeight = FontWeight.Medium
            )
            FullWidthCardButton(
                text = stringResource(R.string.gateway_open_settings),
                icon = Icons.Default.Settings,
                onClick = onOpenSettings ?: {}
            )
        }
    }
}

@Composable
private fun FullWidthCardButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.46f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 18.sp),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
internal fun ChatSessionLoadingCard() {
    ClawLinkCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Column {
                Text(stringResource(R.string.chat_switching_session), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.chat_syncing_history), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun ChatSessionSwitchLoadingOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(ChatColors.canvas.copy(alpha = 0.82f))
            .padding(horizontal = 34.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 34.dp, vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(58.dp),
                        strokeWidth = 4.dp,
                        color = ChatColors.linkBlue,
                    )
                }
                Text(
                    stringResource(R.string.chat_switching_session),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    stringResource(R.string.chat_syncing_history),
                    style = MaterialTheme.typography.bodySmall,
                    color = ChatColors.secondaryText
                )
            }
        }
    }
}

@Composable
internal fun ThinkingRow() {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        StreamingIndicatorBubble()
        Text(
            "ClawLink",
            modifier = Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = ChatColors.secondaryText,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
internal fun IconBadge(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f), RoundedCornerShape(18.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
    }
}
