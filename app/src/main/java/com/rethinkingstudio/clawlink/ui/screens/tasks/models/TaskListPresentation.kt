package com.rethinkingstudio.clawlink.ui.screens.tasks
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import com.rethinkingstudio.clawlink.core.models.tasks.TaskDateCodec
import com.rethinkingstudio.clawlink.core.models.tasks.TaskDraft
import com.rethinkingstudio.clawlink.core.models.tasks.TaskItem
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import com.rethinkingstudio.clawlink.core.state.task.TaskStore
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId



internal enum class TaskListFilter {
    All,
    Active,
    Paused,
    Completed;

    val title: String
        get() = when (this) {
            All -> choose("All", "全部")
            Active -> choose("Active", "运行中")
            Paused -> choose("Paused", "已暂停")
            Completed -> choose("Completed", "已完成")
        }

    fun matches(task: TaskItem): Boolean = when (this) {
        All -> true
        Active -> task.enabled
        Paused -> !task.enabled && !TaskListPresentationLogic.isCompletedOnceTask(task)
        Completed -> TaskListPresentationLogic.isCompletedOnceTask(task)
    }
}

internal data class TaskListSection(val filter: TaskListFilter, val tasks: List<TaskItem>)

internal sealed interface TaskEditorMode {
    val title: String
    val subtitle: String
    val initialDraft: TaskDraft
    val updatingId: String

    data object Create : TaskEditorMode {
        override val title get() = choose("New task", "新建任务")
        override val subtitle get() = choose("Set task content, run time, and repeat rules.", "设置任务内容、执行时间和重复规则。")
        override val initialDraft = TaskDraft(
            scheduleKind = "once",
            scheduleAt = TaskDateCodec.isoString(Instant.now().plusSeconds(15 * 60)),
            repeatAmount = "1",
            repeatUnit = "days"
        )
        override val updatingId = "new"
    }

    data class Edit(val task: TaskItem) : TaskEditorMode {
        override val title get() = choose("Edit task", "编辑任务")
        override val subtitle get() = choose("Adjust task content, run time, and repeat rules.", "调整任务内容、执行时间和重复规则。")
        override val initialDraft = TaskDraft(
            title = task.title,
            prompt = task.prompt,
            scheduleKind = task.scheduleKind.ifBlank { "once" },
            scheduleAt = task.scheduleAt ?: TaskDateCodec.isoString(Instant.now().plusSeconds(15 * 60)),
            repeatAmount = task.repeatAmount ?: "1",
            repeatUnit = task.repeatUnit ?: "days"
        )
        override val updatingId = task.id
    }
}

internal object TaskListPresentationLogic {
    fun sortedTasks(tasks: List<TaskItem>): List<TaskItem> = TaskStore.sortTasks(tasks)

    fun visibleTasks(tasks: List<TaskItem>, selectedFilter: TaskListFilter, searchText: String): List<TaskItem> {
        val term = searchText.trim()
        return tasks.filter { selectedFilter.matches(it) && matchesSearch(it, term) }
    }

    fun groupedSections(tasks: List<TaskItem>): List<TaskListSection> {
        return listOf(TaskListFilter.Active, TaskListFilter.Paused, TaskListFilter.Completed).mapNotNull { filter ->
            val sectionTasks = tasks.filter { filter.matches(it) }
            if (sectionTasks.isEmpty()) null else TaskListSection(filter, sectionTasks)
        }
    }

    fun shouldGroupVisibleTasks(selectedFilter: TaskListFilter, searchText: String): Boolean {
        return selectedFilter == TaskListFilter.All && searchText.trim().isEmpty()
    }

    fun listSectionSubtitle(selectedFilter: TaskListFilter, searchText: String): String {
        val term = searchText.trim()
        return when {
            shouldGroupVisibleTasks(selectedFilter, searchText) -> choose("Grouped automatically by run status", "按运行状态自动分组")
            term.isNotEmpty() -> choose("Searching \"$term\"", "正在搜索「$term」")
            else -> choose("Showing current filter results", "显示当前筛选结果")
        }
    }

    fun nextScheduledTask(tasks: List<TaskItem>): TaskItem? {
        return tasks.mapNotNull { task -> task.nextRunDate?.let { it to task } }.minByOrNull { it.first }?.second
    }

    fun taskBadgeTitle(task: TaskItem): String = when {
        task.enabled && task.nextRunDate != null -> choose("Scheduled", "待执行")
        task.enabled -> choose("Enabled", "已启用")
        isCompletedOnceTask(task) -> choose("Completed", "已完成")
        else -> choose("Paused", "已暂停")
    }

    fun isCompletedOnceTask(task: TaskItem): Boolean = task.scheduleKind == "once" && task.nextRunDate == null

    private fun matchesSearch(task: TaskItem, term: String): Boolean {
        if (term.isEmpty()) return true
        return listOf(task.title, task.prompt, task.schedule, task.nextRunSummary, task.lastResultSummary)
            .any { it.contains(term, ignoreCase = true) }
    }
}
