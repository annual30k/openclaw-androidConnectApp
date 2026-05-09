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


internal enum class SkillListFilter(val tint: Color) {
    All(AccentBlue),
    Ready(SuccessGreen),
    NeedsSetup(WarningOrange),
    Disabled(MutedGray);

    val title: String
        get() = when (this) {
            All -> choose("All", "全部")
            Ready -> choose("Ready", "就绪")
            NeedsSetup -> choose("Needs setup", "待配置")
            Disabled -> choose("Disabled", "已停用")
        }

    fun matches(skill: SkillItem): Boolean = when (this) {
        All -> true
        Ready -> skill.availability == SkillAvailability.Ready
        NeedsSetup -> skill.availability == SkillAvailability.NeedsSetup || skill.availability == SkillAvailability.Blocked
        Disabled -> skill.availability == SkillAvailability.Disabled
    }
}

internal enum class SkillAvailability(val tint: Color, val icon: ImageVector) {
    Ready(SuccessGreen, Icons.Default.CheckCircle),
    NeedsSetup(WarningOrange, Icons.Default.Build),
    Disabled(MutedGray, Icons.Default.PauseCircle),
    Blocked(DangerRed, Icons.Default.Shield);

    val title: String
        get() = when (this) {
            Ready -> choose("Ready", "就绪")
            NeedsSetup -> choose("Needs setup", "待配置")
            Disabled -> choose("Disabled", "已停用")
            Blocked -> choose("Blocked", "已限制")
        }
}

internal val SkillItem.availability: SkillAvailability
    get() = when {
        blockedByAllowlist || status.equals("blocked", ignoreCase = true) -> SkillAvailability.Blocked
        !isEnabled || status.equals("disabled", ignoreCase = true) -> SkillAvailability.Disabled
        eligible == false || !missing.isEmpty || status.equals("needs_setup", ignoreCase = true) -> SkillAvailability.NeedsSetup
        else -> SkillAvailability.Ready
    }

internal val SkillItem.sourceLabel: String
    get() = when (source?.trim()) {
        "openclaw-bundled" -> choose("Bundled", "内置")
        "openclaw-managed", "openclaw-hosted" -> choose("Managed", "托管")
        "openclaw-workspace" -> choose("Workspace", "工作区")
        "openclaw-extra" -> choose("Extra", "扩展")
        "openclaw-plugin" -> choose("Plugin", "插件")
        null, "" -> choose("Untagged", "未标记")
        else -> source.orEmpty()
    }

internal val SkillItem.statusDetailText: String
    get() = statusDetail?.takeIf { it.isNotBlank() } ?: when (availability) {
        SkillAvailability.Ready -> choose("Dependencies are satisfied and the skill is ready to use.", "依赖已满足，可直接使用。")
        SkillAvailability.NeedsSetup -> if (missing.count > 0) {
            choose("${missing.count} config or dependency items are missing.", "缺少 ${missing.count} 项配置或依赖。")
        } else {
            choose("Complete setup before using this skill.", "需要完成配置后才能使用。")
        }
        SkillAvailability.Disabled -> choose("The skill is disabled and will not participate in sessions until enabled.", "技能已停用，需要启用后才会参与会话。")
        SkillAvailability.Blocked -> choose("The skill is restricted by the allowlist and cannot be enabled now.", "技能被 allowlist 限制，当前不可启用。")
    }

internal fun SkillItem.matchesSearch(query: String): Boolean {
    val normalized = query.trim().lowercase()
    if (normalized.isEmpty()) return true
    val haystack = listOf(
        effectiveName,
        effectiveDescription,
        effectiveKey,
        source.orEmpty(),
        sourceLabel,
        primaryEnv.orEmpty(),
        homepage.orEmpty(),
        statusDetailText,
        requirements.bins.joinToString(" "),
        requirements.anyBins.joinToString(" "),
        requirements.env.joinToString(" "),
        missing.bins.joinToString(" "),
        missing.env.joinToString(" ")
    ).joinToString(" ").lowercase()
    return haystack.contains(normalized)
}

internal fun mergedEnvRequirements(skill: SkillItem): List<String> {
    return (skill.requirements.env + skill.envKeys.orEmpty()).distinct()
}
