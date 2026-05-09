package com.rethinkingstudio.clawlink.ui.screens.skills
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rethinkingstudio.clawlink.core.models.skills.SkillCommand
import com.rethinkingstudio.clawlink.core.models.skills.SkillItem
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import com.rethinkingstudio.clawlink.core.state.skill.SkillStore
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SkillDetailSheet(
    skill: SkillItem,
    canEdit: Boolean,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onToggle: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
        containerColor = ScreenWhite,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 42.dp, height = 5.dp)
                    .clip(PillShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.26f))
            )
        }
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SkillDetailHero(skill = skill, canEdit = canEdit, isBusy = isBusy, onToggle = onToggle)
            }
            item {
                SkillDetailSection(choose("Status and source", "状态与来源")) {
                    SkillInfoRow(choose("Current status", "当前状态"), skill.availability.title, skill.availability.tint)
                    SkillInfoRow(choose("Source", "来源"), skill.sourceLabel)
                    SkillInfoRow(choose("Install path", "安装路径"), skill.filePath ?: skill.baseDir ?: skill.effectiveKey)
                    skill.primaryEnv?.takeIf { it.isNotBlank() }?.let { SkillInfoRow(choose("Primary environment variable", "主要环境变量"), it) }
                    skill.homepage?.takeIf { it.isNotBlank() }?.let { SkillInfoRow(choose("Homepage", "主页"), it) }
                }
            }
            item {
                SkillDetailSection(choose("Runtime requirements", "运行要求")) {
                    RequirementBlock(choose("Commands", "命令"), skill.requirements.bins, skill.missing.bins)
                    RequirementBlock(choose("Any command", "任一命令"), skill.requirements.anyBins, skill.missing.anyBins)
                    RequirementBlock(choose("Environment variables", "环境变量"), mergedEnvRequirements(skill), skill.missing.env)
                    RequirementBlock(choose("Config files", "配置文件"), skill.requirements.config, skill.missing.config)
                    RequirementBlock(choose("Platform", "平台"), skill.requirements.os, skill.missing.os)
                    if (skill.requirements.isEmpty && skill.missing.isEmpty && skill.envKeys.isNullOrEmpty()) {
                        Text(choose("No extra requirements", "没有额外要求"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (skill.configChecks.isNotEmpty()) {
                item {
                    SkillDetailSection(choose("Config checks", "配置检查")) {
                        skill.configChecks.forEach { check ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                                Icon(
                                    if (check.satisfied) Icons.Default.CheckCircle else Icons.Default.Info,
                                    contentDescription = null,
                                    tint = if (check.satisfied) SuccessGreen else WarningOrange,
                                    modifier = Modifier.size(16.dp)
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(check.path, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(if (check.satisfied) choose("Satisfied", "已满足") else choose("Not satisfied", "未满足"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            val commands = skill.commands.orEmpty()
            if (commands.isNotEmpty()) {
                item {
                    SkillDetailSection(choose("Available commands", "可用命令")) {
                        commands.forEach { command -> SkillCommandRow(command) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SkillDetailHero(skill: SkillItem, canEdit: Boolean, isBusy: Boolean, onToggle: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                        skill.availability.tint.copy(alpha = 0.10f)
                    )
                )
            )
            .border(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f), CardShape)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            SkillAvatar(skill = skill, diameter = 72)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(skill.effectiveName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SkillStatusBadge(skill.availability)
                    if (skill.always) {
                        Text(
                            choose("Always loaded", "常驻加载"),
                            modifier = Modifier.clip(PillShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)).padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        Text(skill.effectiveDescription.ifBlank { skill.statusDetailText }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(choose("Current status", "当前状态"), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (skill.isEnabled) choose("Enabled", "已启用") else choose("Disabled", "已停用"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = if (skill.isEnabled) SuccessGreen else MaterialTheme.colorScheme.onSurface)
            }
            Button(
                onClick = onToggle,
                enabled = canEdit && !isBusy && (skill.isEnabled || skill.availability != SkillAvailability.Blocked),
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(containerColor = if (skill.isEnabled) DangerRed else AccentBlue),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
            ) {
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Icon(if (skill.isEnabled) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (skill.isEnabled) choose("Disable", "停用") else choose("Enable", "启用"), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
internal fun SkillDetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
            .border(0.6.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f), CardShape)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable
internal fun SkillInfoRow(label: String, value: String, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = tint)
    }
}

@Composable
internal fun RequirementBlock(title: String, required: List<String>, missing: List<String>) {
    val visibleItems = if (required.isEmpty()) missing else required
    if (visibleItems.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
        visibleItems.forEach { item ->
            val isMissing = missing.contains(item)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isMissing) Icons.Default.Info else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (isMissing) WarningOrange else SuccessGreen,
                    modifier = Modifier.size(16.dp)
                )
                Text(item, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
internal fun SkillCommandRow(command: SkillCommand) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("/${command.name}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black, color = AccentBlue)
        command.description?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
