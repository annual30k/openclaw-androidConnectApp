package com.rethinkingstudio.clawlink.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rethinkingstudio.clawlink.ui.components.ClawLinkScaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    onBack: () -> Unit
) {
    ClawLinkScaffold(
        topBar = {
            TopAppBar(
                title = { Text("Help & Usage Guide") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HelpSection(
                icon = Icons.Default.Link,
                title = "Getting Started",
                content = "1. Make sure your relay server is running.\n" +
                        "2. Log in with your email and server URL.\n" +
                        "3. Pair your gateway using the Gateway ID and access code.\n" +
                        "4. Start chatting with your AI assistant!"
            )

            HelpSection(
                icon = Icons.Default.Chat,
                title = "Chat",
                content = "Send messages to your AI assistant through the paired gateway. " +
                        "Messages are streamed in real-time. Use the eye icon to toggle tool invocation details. " +
                        "Create new sessions or switch between existing ones using the history button."
            )

            HelpSection(
                icon = Icons.Default.SwapHoriz,
                title = "Gateways",
                content = "A gateway represents a running AI host (e.g., Claude on your Mac). " +
                        "You can have multiple gateways and switch between them. " +
                        "Each gateway shows its online status, current model, and context usage."
            )

            HelpSection(
                icon = Icons.Default.Dashboard,
                title = "Models",
                content = "Browse available AI models on your gateway. " +
                        "Tap a model to select it as the active model for your next conversation. " +
                        "The selected model is highlighted with a check mark."
            )

            HelpSection(
                icon = Icons.Default.Extension,
                title = "Skills",
                content = "Skills are plugins that extend your AI assistant's capabilities. " +
                        "Toggle skills on or off with the switch. " +
                        "Expand a skill to see its details, version, and available slash commands."
            )

            HelpSection(
                icon = Icons.Default.Task,
                title = "Tasks",
                content = "Tasks are scheduled jobs that run on your gateway. " +
                        "Create recurring tasks with custom prompts and schedules. " +
                        "Pause or resume tasks with the toggle switch. " +
                        "View task results and next run times by expanding the details."
            )

            HelpSection(
                icon = Icons.Default.Tune,
                title = "Advanced",
                content = "Manage backups of your gateway state. " +
                        "Create, restore, or delete backups. " +
                        "Log viewing will be available in a future update."
            )

            Spacer(modifier = Modifier.height(16.dp))

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "ClawLink v1.0.0 — PocketClaw Android",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HelpSection(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    content: String
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(content, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
