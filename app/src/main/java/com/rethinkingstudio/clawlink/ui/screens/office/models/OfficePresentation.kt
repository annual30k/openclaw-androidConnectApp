package com.rethinkingstudio.clawlink.ui.screens.office

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.core.models.OfficeActivityKind
import com.rethinkingstudio.clawlink.core.models.OfficeAgentSnapshot
import com.rethinkingstudio.clawlink.core.models.OfficeSceneSnapshot
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.gateway.AggregateStatus
import com.rethinkingstudio.clawlink.core.models.gateway.GatewaySummary
import com.rethinkingstudio.clawlink.core.models.office.OfficeScenePlanner
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
import com.rethinkingstudio.clawlink.core.state.auth.AuthStore
import com.rethinkingstudio.clawlink.core.state.chat.ChatStore
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import com.rethinkingstudio.clawlink.ui.screens.chat.formatChatTimestamp
import com.rethinkingstudio.clawlink.ui.screens.office.components.OfficePanelCard
import com.rethinkingstudio.clawlink.ui.screens.office.components.PixelOfficeScene

internal enum class OfficePresenceMode {
    WORKING,
    RESTING,
    SLEEPING;

    @Composable
    fun title(): String = when (this) {
        WORKING -> stringResource(R.string.office_mode_working)
        RESTING -> stringResource(R.string.office_mode_resting)
        SLEEPING -> stringResource(R.string.office_mode_sleeping)
    }

    @Composable
    fun subtitle(): String = when (this) {
        WORKING -> stringResource(R.string.office_mode_working_subtitle)
        RESTING -> stringResource(R.string.office_mode_resting_subtitle)
        SLEEPING -> stringResource(R.string.office_mode_sleeping_subtitle)
    }

    val tint: Color
        get() = when (this) {
            WORKING -> Color(0xFF4CAF50)
            RESTING -> Color(0xFF61A7E8)
            SLEEPING -> Color(0xFF999DAF)
        }
}

internal fun officePresenceMode(agent: OfficeAgentSnapshot?): OfficePresenceMode {
    if (agent == null) return OfficePresenceMode.RESTING
    return if (
        agent.activityKind == OfficeActivityKind.SLEEPING ||
        agent.activityKind == OfficeActivityKind.OFFLINE ||
        agent.aggregateStatus == AggregateStatus.offline
    ) {
        OfficePresenceMode.SLEEPING
    } else {
        OfficePresenceMode.WORKING
    }
}

internal fun OfficeAgentSnapshot.shouldShowToolDetail(): Boolean {
    val hasExplicitTool = !activityToolName.isNullOrBlank() || !activityToolCallId.isNullOrBlank()
    return hasExplicitTool || when (activityKind) {
        OfficeActivityKind.IDLE,
        OfficeActivityKind.WRITING,
        OfficeActivityKind.RESEARCHING,
        OfficeActivityKind.EXECUTING,
        OfficeActivityKind.SYNCING,
        OfficeActivityKind.ERROR -> true
        OfficeActivityKind.SLEEPING,
        OfficeActivityKind.OFFLINE -> false
    }
}

internal fun OfficeAgentSnapshot.prefersExpandedTaskLayout(): Boolean {
    if (activityDetail.isBlank()) return false
    return when (activityKind) {
        OfficeActivityKind.WRITING,
        OfficeActivityKind.RESEARCHING,
        OfficeActivityKind.EXECUTING,
        OfficeActivityKind.ERROR -> true
        OfficeActivityKind.SYNCING,
        OfficeActivityKind.IDLE,
        OfficeActivityKind.SLEEPING,
        OfficeActivityKind.OFFLINE -> false
    }
}

internal fun progressText(agent: OfficeAgentSnapshot): String {
    val progress = agent.activityProgress ?: return "—"
    return "${(progress * 100.0).coerceIn(0.0, 100.0).toInt()}%"
}

internal fun latestStreamingReplyText(messages: List<com.rethinkingstudio.clawlink.core.models.chat.ChatMessage>): String {
    return messages
        .asReversed()
        .firstOrNull { it.role == MessageRole.assistant && it.state == MessageState.streaming }
        ?.plainTextContent
        ?.trim()
        ?.takeUnless { isTransientStreamingText(it) }
        .orEmpty()
}

internal fun isTransientStreamingText(text: String): Boolean {
    val normalized = text.trim()
    return normalized.isBlank() ||
        normalized.startsWith("正在连接") ||
        normalized.startsWith("连接中断") ||
        normalized == "正在同步回复..." ||
        normalized == "正在同步最终内容..." ||
        normalized == "已完成，但未返回文本。"
}

internal fun contextUsagePercentage(usage: String): Float {
    val match = Regex("""\((\d+)%\)""").find(usage)
    return match?.groupValues?.getOrNull(1)?.toFloatOrNull()?.div(100f)?.coerceIn(0f, 1f) ?: 0f
}

@Composable
internal fun OfficeActivityKind.localizedDisplayName(): String = when (this) {
    OfficeActivityKind.IDLE -> stringResource(R.string.office_activity_idle)
    OfficeActivityKind.WRITING -> stringResource(R.string.office_activity_writing)
    OfficeActivityKind.RESEARCHING -> stringResource(R.string.office_activity_researching)
    OfficeActivityKind.EXECUTING -> stringResource(R.string.office_activity_executing)
    OfficeActivityKind.SYNCING -> stringResource(R.string.office_activity_syncing)
    OfficeActivityKind.SLEEPING -> stringResource(R.string.office_activity_sleeping)
    OfficeActivityKind.OFFLINE -> stringResource(R.string.office_activity_offline)
    OfficeActivityKind.ERROR -> stringResource(R.string.office_activity_error)
}

internal fun AggregateStatus.gatewayTint(): Color = when (this) {
    AggregateStatus.online -> Color(0xFF4CAF50)
    AggregateStatus.connecting -> Color(0xFFF2C94C)
    AggregateStatus.partial -> Color(0xFFFF8A4C)
    AggregateStatus.offline -> Color(0xFFE05A5A)
}

internal fun AggregateStatus.displayName(): String = when (this) {
    AggregateStatus.online -> "online"
    AggregateStatus.connecting -> "connecting"
    AggregateStatus.partial -> "partial"
    AggregateStatus.offline -> "offline"
}
