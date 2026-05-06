package com.rethinkingstudio.clawlink.ui.screens.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.ui.components.ClawLinkCard
import com.rethinkingstudio.clawlink.ui.screens.chat.ChatColors

@Composable
internal fun StatusBanner(text: String, isError: Boolean, onDismiss: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_action_close)) }
        }
    }
}

@Composable
internal fun UsageGuidePromptCard(onOpenUsageGuide: (() -> Unit)?) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color.White.copy(alpha = 0.94f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE1E4EA)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                IconBadge(Icons.Default.Description)
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(stringResource(R.string.gateway_usage_guide_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.Black)
                    Text(stringResource(R.string.gateway_usage_guide_prompt), style = MaterialTheme.typography.bodyMedium, color = ChatColors.secondaryText)
                }
            }
            FullWidthCardButton(
                text = stringResource(R.string.gateway_usage_guide_button),
                icon = Icons.Default.Description,
                onClick = onOpenUsageGuide ?: {}
            )
        }
    }
}

@Composable
internal fun EmptyGatewayCard(onOpenSettings: (() -> Unit)?) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color.White.copy(alpha = 0.94f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE1E4EA)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                IconBadge(Icons.Default.Settings)
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(stringResource(R.string.gateway_no_host_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.Black)
                    Text(stringResource(R.string.gateway_no_host_detail), style = MaterialTheme.typography.bodyMedium, color = ChatColors.secondaryText)
                }
            }
            Text(stringResource(R.string.gateway_no_host_hint), style = MaterialTheme.typography.bodyMedium, color = ChatColors.secondaryText, fontWeight = FontWeight.Bold)
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
        color = ChatColors.pending.copy(alpha = 0.24f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, tint = Color.Black, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, fontWeight = FontWeight.Black, color = Color.Black)
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
            color = Color.White.copy(alpha = 0.92f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.72f)),
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
                    color = Color.Black
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = ChatColors.linkBlue
        )
        Text(
            "ClawLink 正在思考…",
            style = MaterialTheme.typography.labelMedium,
            color = ChatColors.secondaryText,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
internal fun IconBadge(icon: ImageVector) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.66f)
    ) {
        Icon(icon, null, modifier = Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.primary)
    }
}
