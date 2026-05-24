package com.rethinkingstudio.clawlink.ui.screens.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
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
    remoteCommands: List<ChatSlashCommand>?,
    includeDefaultActions: Boolean = true,
    limit: Int = 16
): List<SlashAction> {
    val query = normalizedLeadingSlashQuery(input) ?: return emptyList()
    if (limit <= 0) return emptyList()
    return mergedSlashActions(remoteCommands, includeDefaultActions)
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
        .take(limit)
}

private fun mergedSlashActions(
    remoteCommands: List<ChatSlashCommand>?,
    includeDefaultActions: Boolean
): List<SlashAction> {
    val merged = mutableListOf<SlashAction>()
    val seen = mutableSetOf<String>()
    val defaults = if (includeDefaultActions) defaultSlashActions() else emptyList()
    ((remoteCommands.orEmpty().mapNotNull { it.toSlashAction() }) + defaults).forEach { action ->
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
    val trimmed = input.trim().replace('／', '/')
    if (!trimmed.startsWith("/")) return null
    return trimmed.normalizedSlashCommand()
}

private fun String.normalizedSlashCommand(): String {
    return trim().replace('／', '/').lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
}

private fun slashMatchRank(command: String, query: String): Int? {
    if (query.isEmpty()) return 0
    if (command == query) return 0
    if (command.startsWith(query)) return 1
    if (query.startsWith(command)) return 2

    val compactCommand = command.compactSlashSearchText()
    val compactQuery = query.compactSlashSearchText()
    if (compactQuery.isEmpty()) return 0
    if (compactCommand.contains(compactQuery)) return 3
    if (compactQuery.isSubsequenceOf(compactCommand)) return 4
    return null
}

private fun String.compactSlashSearchText(): String {
    return trim().removePrefix("/").replace(Regex("\\s+"), "").lowercase()
}

private fun String.isSubsequenceOf(value: String): Boolean {
    if (isEmpty()) return true
    var queryIndex = 0
    value.forEach { char ->
        if (char == this[queryIndex]) {
            queryIndex += 1
            if (queryIndex == length) return true
        }
    }
    return false
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
        icon.contains("list") || cmd.startsWith("/commands") -> Icons.AutoMirrored.Filled.List
        icon.contains("stop") || cmd.startsWith("/stop") -> Icons.Default.Stop
        icon.contains("question") || cmd.startsWith("/help") -> Icons.Default.Description
        icon.contains("waveform") || cmd.startsWith("/status") -> Icons.Default.GraphicEq
        else -> Icons.Default.Terminal
    }
}

@Composable
internal fun SlashCommandPanel(
    actions: List<SlashAction>,
    onAction: (SlashAction) -> Unit,
    onLoadMore: (() -> Unit)? = null,
    isLoadingMore: Boolean = false
) {
    val grouped = actions.groupBy { it.category }
    val categoryOrder = listOf("SESSION", "TOOLS", "SKILLS", "SYSTEM")
    val sections = categoryOrder.mapNotNull { category ->
        grouped[category]?.takeIf { it.isNotEmpty() }?.let { category to it }
    } + grouped
        .filter { (category, _) -> category !in categoryOrder }
        .toSortedMap()
        .map { it.key to it.value }
    val panelColor = MaterialTheme.colorScheme.surface.copy(alpha = if (isSystemInDarkTheme()) 0.94f else 0.96f)
    val panelBorder = MaterialTheme.colorScheme.outline.copy(alpha = if (isSystemInDarkTheme()) 0.24f else 0.10f)

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = panelColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(0.5.dp, panelBorder),
        shadowElevation = 12.dp,
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            sections.forEach { (category, categoryActions) ->
                item(key = "header-$category") {
                    SlashCategoryHeader(category)
                }
                items(categoryActions, key = { it.command }) { action ->
                    SlashCommandRow(action = action, onAction = onAction)
                }
            }
            if (onLoadMore != null) {
                item(key = "slash-load-more") {
                    LaunchedEffect(actions.size, isLoadingMore) {
                        if (!isLoadingMore) {
                            onLoadMore()
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoadingMore) {
                            Text(
                                choose("Loading...", "加载中..."),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SlashCommandRow(action: SlashAction, onAction: (SlashAction) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onAction(action) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            action.icon,
            null,
            modifier = Modifier.size(16.dp),
            tint = slashCategoryColor(action.category)
        )
        Column {
            Text(
                action.command,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (action.detail.isNotBlank()) {
                Text(
                    action.detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
    val color = slashCategoryColor(category)
    Row(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = color.copy(alpha = if (isSystemInDarkTheme()) 0.18f else 0.14f),
            contentColor = color
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
            )
        }
    }
}

private fun slashCategoryColor(category: String): Color = when (category) {
    "SESSION" -> Color(0xFF7EADF4)
    "TOOLS" -> Color(0xFF5ECF7A)
    "SKILLS" -> Color(0xFFF4A100)
    else -> Color(0xFFA0A4AF)
}
