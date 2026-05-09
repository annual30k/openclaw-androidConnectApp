package com.rethinkingstudio.clawlink.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.core.models.chat.ChatSessionItem
import com.rethinkingstudio.clawlink.core.network.RelayAPIClient
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
import com.rethinkingstudio.clawlink.core.state.chat.ChatStore
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.math.abs

internal object SessionPresentation {
    fun visibleSessions(sessions: List<ChatSessionItem>, currentSessionKey: String): List<ChatSessionItem> {
        val current = currentSessionKey.trim().ifBlank { "main" }
        val merged = sessions + ChatSessionItem(sessionKey = current, lastActivityAt = null)
        return merged
            .distinctBy { it.normalizedSessionKey }
            .sortedWith(
                compareByDescending<ChatSessionItem> { it.normalizedSessionKey == current.lowercase() }
                    .thenByDescending { parseInstant(it.lastActivityAt)?.toEpochMilli() ?: 0L }
                    .thenBy { it.displayTitle.lowercase() }
            )
    }
}

internal val ChatSessionItem.normalizedSessionKey: String
    get() = sessionKey.trim().lowercase().ifBlank { "main" }

internal val ChatSessionItem.displayTitle: String
    get() = firstPresentationText(displayName, derivedTitle, label) ?: displayTitleForKey(sessionKey)

internal val ChatSessionItem.displaySubtitle: String
    get() {
        val kindValue = kind?.trim()?.lowercase()
        if (kindValue != null) {
            when (kindValue) {
                "direct" -> return choose("Direct session", "直连会话")
                "group" -> return choose("Group session", "群组会话")
                "global" -> return choose("Global scope", "全局作用域")
                "unknown" -> return choose("Uncategorized session", "未分类会话")
            }
        }
        return displaySubtitleForKey(sessionKey)
    }

internal val ChatSessionItem.activityText: String
    get() = activityText(lastActivityAt)

private fun firstPresentationText(vararg values: String?): String? {
    return values.firstNotNullOfOrNull { value -> value?.trim()?.takeIf { it.isNotEmpty() } }
}

private fun displayTitleForKey(rawSessionKey: String): String {
    val normalized = rawSessionKey.trim().lowercase()
    if (normalized.isEmpty()) return choose("Untitled session", "未命名会话")
    if (normalized == "main" || normalized.endsWith(":main")) return choose("Main session", "主会话")
    if (normalized == "global") return choose("Global session", "全局会话")
    if (isDedicatedSession(normalized)) return choose("File transfer lab", "文件联调")

    val localComponent = normalized.split(":").lastOrNull().orEmpty().ifBlank { normalized }
    if (listOf("ios-", "mobile-", "tui-", "session_").any { localComponent.startsWith(it) }) {
        val suffix = localComponent
            .removePrefix("ios-")
            .removePrefix("mobile-")
            .removePrefix("tui-")
            .removePrefix("session_")
            .takeLast(4)
            .uppercase()
        return if (suffix.isBlank()) choose("New session", "新会话") else choose("New session · $suffix", "新会话 · $suffix")
    }

    return normalized.split(":").lastOrNull()?.takeIf { it.isNotBlank() } ?: normalized
}

private fun displaySubtitleForKey(rawSessionKey: String): String {
    val normalized = rawSessionKey.trim().lowercase()
    if (normalized.isEmpty()) return choose("No identifier", "暂无标识")
    if (normalized == "main" || normalized.endsWith(":main")) return choose("Default session", "默认会话")
    if (normalized == "global") return choose("Global scope", "全局作用域")
    if (isDedicatedSession(normalized)) return choose("Dedicated test session", "专用测试会话")

    val localComponent = normalized.split(":").lastOrNull().orEmpty().ifBlank { normalized }
    if (listOf("ios-", "mobile-", "tui-", "session_").any { localComponent.startsWith(it) }) {
        return choose("Created locally", "本地创建")
    }

    val parts = normalized.split(":")
    return if (parts.size > 1) parts.dropLast(1).joinToString(" · ").ifBlank { normalized } else normalized
}

private fun activityText(rawValue: String?): String {
    val instant = parseInstant(rawValue) ?: return choose("No activity yet", "暂无活动")
    val now = Instant.now()
    val deltaSeconds = abs(now.epochSecond - instant.epochSecond)
    return when {
        deltaSeconds < 45 -> choose("Just now", "刚刚")
        deltaSeconds < 3600 -> choose("${maxOf(1, deltaSeconds / 60)} min ago", "${maxOf(1, deltaSeconds / 60)} 分钟前")
        isToday(instant) -> DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(instant)
        isSameYear(instant) -> DateTimeFormatter.ofPattern(if (com.rethinkingstudio.clawlink.core.state.LocalizedText.isChinese()) "M月d日 HH:mm" else "MMM d HH:mm").withZone(ZoneId.systemDefault()).format(instant)
        else -> DateTimeFormatter.ofPattern(if (com.rethinkingstudio.clawlink.core.state.LocalizedText.isChinese()) "yyyy年M月d日 HH:mm" else "MMM d, yyyy HH:mm").withZone(ZoneId.systemDefault()).format(instant)
    }
}

private fun parseInstant(rawValue: String?): Instant? {
    val value = rawValue?.trim().orEmpty()
    if (value.isEmpty()) return null
    return try {
        Instant.parse(value)
    } catch (_: DateTimeParseException) {
        null
    }
}

private fun isToday(instant: Instant): Boolean {
    val zone = ZoneId.systemDefault()
    return instant.atZone(zone).toLocalDate() == Instant.now().atZone(zone).toLocalDate()
}

private fun isSameYear(instant: Instant): Boolean {
    val zone = ZoneId.systemDefault()
    return instant.atZone(zone).year == Instant.now().atZone(zone).year
}

internal fun isDedicatedSession(normalizedSessionKey: String): Boolean {
    return normalizedSessionKey == "agent:main:file-transfer-lab"
}

internal fun isProtectedSession(session: ChatSessionItem): Boolean {
    val normalized = session.normalizedSessionKey
    return normalized == "main" || normalized.endsWith(":main") || isDedicatedSession(normalized)
}

internal fun canDeleteSession(
    session: ChatSessionItem,
    gatewayId: String?,
    operationsLocked: Boolean,
    isStreaming: Boolean,
    isStoppingRun: Boolean,
    isRefreshing: Boolean,
    deletingKey: String?,
    role: String?
): Boolean {
    if (isProtectedSession(session)) return false
    if (gatewayId == null) return false
    if (operationsLocked || isStreaming || isStoppingRun || isRefreshing) return false
    if (deletingKey != null && deletingKey != session.normalizedSessionKey) return false
    if (role?.trim()?.lowercase() == "viewer") return false
    return true
}

internal fun lockMessage(gatewayId: String?, operationsLocked: Boolean, isStreaming: Boolean, isStoppingRun: Boolean): String {
    return when {
        gatewayId == null -> choose("Pair a gateway before switching sessions.", "请先完成配对后再切换会话。")
        operationsLocked -> choose("The current gateway is recovering from restart. Session switching is temporarily unavailable.", "当前网关正在重启恢复中，暂不能切换会话。")
        isStreaming -> choose("The current session still has an unfinished reply. Wait or stop it before switching.", "当前会话还有未完成回复，请先等待或停止后再切换。")
        isStoppingRun -> choose("The message is still stopping. Switch sessions later.", "消息正在停止中，请稍后再切换会话。")
        else -> choose("Session switching is currently unavailable.", "当前不可切换会话。")
    }
}

