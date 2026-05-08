package com.rethinkingstudio.clawlink.ui.screens.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rethinkingstudio.clawlink.core.models.chat.ChatSlashCommand
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose

internal data class SlashAction(
    val command: String,
    val title: String,
    val detail: String,
    val category: String,
    val icon: ImageVector
)

private fun defaultSlashActions() = listOf(
    SlashAction("/new", choose("New session", "新会话"), choose("Start a new chat session", "开启一个新的聊天会话"), "SESSION", Icons.Default.Add),
    SlashAction("/model", choose("Switch model", "切模型"), choose("Choose the model for the current session", "选择当前会话使用的模型"), "SESSION", Icons.Default.SmartToy),
    SlashAction("/status", choose("Status", "看状态"), choose("View current connection and gateway status", "查看当前链路和网关状态"), "SYSTEM", Icons.Default.GraphicEq),
    SlashAction("/doctor", choose("Diagnose", "做诊断"), choose("Check Relay, gateway, and session links", "检查 Relay、网关和会话链路"), "SYSTEM", Icons.Default.CheckCircle),
    SlashAction("/config", choose("Settings", "配设置"), choose("View or adjust current settings", "查看或调整当前配置"), "SYSTEM", Icons.Default.Settings),
    SlashAction("/skills list", choose("Skills", "Skills"), choose("List available skills.", "列出可用技能。"), "SKILLS", Icons.Default.AutoAwesome),
    SlashAction("/channels list", choose("Channels", "Channels"), choose("List available channels.", "列出可用频道。"), "SYSTEM", Icons.Default.Terminal),
    SlashAction("/cron list", choose("Tasks", "Tasks"), choose("List scheduled tasks.", "列出定时任务。"), "SYSTEM", Icons.Default.Refresh)
)

internal fun slashCommandSuggestions(
    input: String,
    remoteCommands: List<ChatSlashCommand>?
): List<SlashAction> {
    val query = normalizedLeadingSlashQuery(input) ?: return emptyList()
    return mergedSlashActions(remoteCommands)
        .mapIndexedNotNull { index, action ->
            val rank = slashMatchRank(action.command.normalizedSlashCommand(), query) ?: return@mapIndexedNotNull null
            Triple(rank, index, action)
        }
        .sortedWith(
            compareBy<Triple<Int, Int, SlashAction>> { it.first }
                .thenBy { it.second }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.third.title }
        )
        .map { it.third }
}

private fun mergedSlashActions(remoteCommands: List<ChatSlashCommand>?): List<SlashAction> {
    val merged = mutableListOf<SlashAction>()
    val seen = mutableSetOf<String>()
    ((remoteCommands.orEmpty().mapNotNull { it.toSlashAction() }) + defaultSlashActions()).forEach { action ->
        if (seen.add(action.command.normalizedSlashCommand())) {
            merged += action
        }
    }
    return merged
}

private fun ChatSlashCommand.toSlashAction(): SlashAction? {
    val resolvedCommand = command?.trim()?.takeIf { it.startsWith("/") && it.isNotBlank() } ?: return null
    val resolvedTitle = title?.trim()?.takeIf { it.isNotEmpty() }
        ?: name?.trim()?.takeIf { it.isNotEmpty() }
        ?: resolvedCommand
    val resolvedDetail = detail?.trim()?.takeIf { it.isNotEmpty() }
        ?: description?.trim()?.takeIf { it.isNotEmpty() }
        ?: ""
    val resolvedCategory = category?.trim()?.takeIf { it.isNotEmpty() }
        ?: defaultSlashCategory(resolvedCommand)
    return SlashAction(
        command = resolvedCommand,
        title = resolvedTitle,
        detail = resolvedDetail,
        category = resolvedCategory.uppercase(),
        icon = slashIcon(iconName, resolvedCommand)
    )
}

private fun normalizedLeadingSlashQuery(input: String): String? {
    val trimmed = input.trim()
    if (!trimmed.startsWith("/")) return null
    return trimmed.normalizedSlashCommand()
}

private fun String.normalizedSlashCommand(): String {
    return trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
}

private fun slashMatchRank(command: String, query: String): Int? {
    return when {
        query.isEmpty() -> 0
        command == query -> 0
        command.startsWith(query) -> 1
        query.startsWith(command) -> 2
        else -> null
    }
}

private fun defaultSlashCategory(command: String): String {
    val cmd = command.trim().lowercase()
    return when {
        listOf("session", "focus", "unfocus", "stop", "reset", "new", "compact", "clear", "model").any { cmd.contains(it) } -> "SESSION"
        cmd.contains("skill") || cmd.contains("tool") -> "TOOLS"
        else -> "SYSTEM"
    }
}

private fun slashIcon(iconName: String?, command: String): ImageVector {
    val icon = iconName?.trim()?.lowercase().orEmpty()
    val cmd = command.trim().lowercase()
    return when {
        icon.contains("plus") || cmd.startsWith("/new") -> Icons.Default.Add
        icon.contains("cube") || cmd.startsWith("/model") -> Icons.Default.SmartToy
        icon.contains("gear") || cmd.startsWith("/config") -> Icons.Default.Settings
        icon.contains("stethoscope") || cmd.startsWith("/doctor") -> Icons.Default.CheckCircle
        icon.contains("clock") || cmd.startsWith("/cron") -> Icons.Default.Refresh
        icon.contains("wand") || cmd.startsWith("/skills") || cmd.startsWith("/skill") -> Icons.Default.AutoAwesome
        icon.contains("list") || cmd.startsWith("/commands") -> Icons.Default.List
        icon.contains("stop") || cmd.startsWith("/stop") -> Icons.Default.Stop
        icon.contains("question") || cmd.startsWith("/help") -> Icons.Default.Description
        icon.contains("waveform") || cmd.startsWith("/status") -> Icons.Default.GraphicEq
        else -> Icons.Default.Terminal
    }
}

@Composable
internal fun SlashCommandPanel(actions: List<SlashAction>, onAction: (SlashAction) -> Unit) {
    val grouped = actions.groupBy { it.category }
    val categoryOrder = listOf("SESSION", "TOOLS", "SKILLS", "SYSTEM")

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF101827),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Horizontal category pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categoryOrder.forEach { category ->
                    val categoryActions = grouped[category] ?: return@forEach
                    val categoryLabel = when (category) {
                        "SESSION" -> choose("Session", "会话")
                        "TOOLS" -> choose("Tools", "工具")
                        "SKILLS" -> "Skills"
                        else -> choose("System", "系统")
                    }
                    // Category group
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SlashCategoryHeader(category)
                        categoryActions.forEach { action ->
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onAction(action) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    action.icon,
                                    null,
                                    modifier = Modifier.size(14.dp),
                                    tint = slashCategoryColor(category)
                                )
                                Column {
                                    Text(
                                        action.title,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (action.detail.isNotBlank()) {
                                        Text(
                                            action.detail,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.54f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SlashCategoryHeader(category: String) {
    val label = when (category) {
        "SESSION" -> "SESSION"
        "TOOLS" -> "TOOLS"
        "SKILLS" -> "SKILLS"
        else -> "SYSTEM"
    }
    Text(
        label,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
        color = slashCategoryColor(category).copy(alpha = 0.82f),
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

private fun slashCategoryColor(category: String): Color = when (category) {
    "SESSION" -> Color(0xFF7EADF4)
    "TOOLS" -> Color(0xFF5ECF7A)
    "SKILLS" -> Color(0xFFF4A100)
    else -> Color(0xFFA0A4AF)
}
