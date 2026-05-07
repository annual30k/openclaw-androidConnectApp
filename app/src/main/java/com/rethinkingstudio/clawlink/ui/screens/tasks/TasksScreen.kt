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
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import com.rethinkingstudio.clawlink.core.state.task.TaskStore
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

private val SheetShape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp)
private val CardShape = RoundedCornerShape(32.dp)
private val FieldShape = RoundedCornerShape(24.dp)
private val PillShape = RoundedCornerShape(999.dp)
private val SuccessGreen = Color(0xFF20C873)
private val WarningOrange = Color(0xFFFFB13D)
private val AccentBlue = Color(0xFF0A84FF)
private val AccentBlueSoft = Color(0xFF5AC8FA)
private val ScreenBlue = Color(0xFFEAF4FF)
private val ScreenWhite = Color(0xFFFAFBFF)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TasksScreen(
    taskStore: TaskStore,
    gatewayStore: GatewayStore,
    onBack: () -> Unit
) {
    val taskState by taskStore.state.collectAsState()
    val gatewayState by gatewayStore.state.collectAsState()
    val scope = rememberCoroutineScope()
    val gatewayId = gatewayState.selectedGatewayId
    val canManageTasks = gatewayId != null

    var editorMode by remember { mutableStateOf<TaskEditorMode?>(null) }
    var deleteTarget by remember { mutableStateOf<TaskItem?>(null) }
    var selectedFilter by remember { mutableStateOf(TaskListFilter.All) }
    var searchText by remember { mutableStateOf("") }

    LaunchedEffect(gatewayId) {
        if (gatewayId != null) taskStore.loadTasks(gatewayId)
    }

    val sortedTasks = remember(taskState.tasks) { TaskListPresentationLogic.sortedTasks(taskState.tasks) }
    val visibleTasks = remember(sortedTasks, selectedFilter, searchText) {
        TaskListPresentationLogic.visibleTasks(sortedTasks, selectedFilter, searchText)
    }
    val nextTask = remember(sortedTasks) { TaskListPresentationLogic.nextScheduledTask(sortedTasks) }

    Box(modifier = Modifier.fillMaxSize()) {
        TaskScreenBackdrop()
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("定时任务", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { if (gatewayId != null) scope.launch { taskStore.loadTasks(gatewayId) } },
                            enabled = !taskState.isLoading
                        ) {
                            if (taskState.isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "刷新")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            bottomBar = {
                TaskSearchBar(
                    searchText = searchText,
                    onSearchTextChange = { searchText = it },
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp)
                )
            }
        ) { padding ->
            if (taskState.isLoading && taskState.tasks.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 112.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    item {
                        if (gatewayState.restartingGatewayId != null) {
                            MaintenanceBanner(
                                title = "网关维护中",
                                message = androidx.compose.ui.res.stringResource(com.rethinkingstudio.clawlink.R.string.advanced_doctor_fix_hint_locked),
                                icon = Icons.Default.Bolt,
                                tint = WarningOrange
                            )
                        }
                    }

                    item {
                        TasksOverviewCard(
                            totalCount = taskState.tasks.size,
                            enabledCount = taskState.tasks.count { it.enabled },
                            pausedCount = taskState.tasks.count { !it.enabled },
                            nextTask = nextTask,
                            canCreateTask = canManageTasks,
                            isRefreshingTasks = taskState.isLoading,
                            onCreateTask = { editorMode = TaskEditorMode.Create }
                        )
                    }

                    item {
                        TasksFilterStrip(
                            tasks = taskState.tasks,
                            selectedFilter = selectedFilter,
                            onSelectFilter = { selectedFilter = it }
                        )
                    }

                    item(key = "section_list_title") {
                        Box(Modifier.animateItemPlacement()) {
                            SectionTitle(
                                title = "任务列表",
                                subtitle = TaskListPresentationLogic.listSectionSubtitle(selectedFilter, searchText)
                            )
                        }
                    }

                    if (TaskListPresentationLogic.shouldGroupVisibleTasks(selectedFilter, searchText)) {
                        val sections = TaskListPresentationLogic.groupedSections(sortedTasks)
                        if (sections.isEmpty()) {
                            item(key = "empty_state") {
                                Box(Modifier.animateItemPlacement()) {
                                    TasksEmptyStateCard(taskState.tasks, searchText, onReset = { selectedFilter = TaskListFilter.All; searchText = "" })
                                }
                            }
                        } else {
                            sections.forEach { section ->
                                item(key = "section_${section.filter.title}") {
                                    Box(Modifier.animateItemPlacement()) {
                                        SectionTitle(title = section.filter.title, subtitle = "${section.tasks.size} 个任务")
                                    }
                                }
                                items(section.tasks, key = { it.id }) { task ->
                                    TasksTaskCard(
                                        task = task,
                                        updatingTaskId = taskState.updatingTaskId,
                                        isRefreshingTasks = taskState.isLoading,
                                        canManageTasks = canManageTasks,
                                        onEdit = { editorMode = TaskEditorMode.Edit(it) },
                                        onDelete = { deleteTarget = it },
                                        onTogglePause = { item ->
                                            if (gatewayId != null) {
                                                scope.launch { taskStore.toggleTask(gatewayId, item.id, !item.enabled) }
                                            }
                                        },
                                        modifier = Modifier.animateItemPlacement()
                                    )
                                }
                            }
                        }
                    } else if (visibleTasks.isEmpty()) {
                        item(key = "empty_state") {
                            Box(Modifier.animateItemPlacement()) {
                                TasksEmptyStateCard(taskState.tasks, searchText, onReset = { selectedFilter = TaskListFilter.All; searchText = "" })
                            }
                        }
                    } else {
                        items(visibleTasks, key = { it.id }) { task ->
                            TasksTaskCard(
                                task = task,
                                updatingTaskId = taskState.updatingTaskId,
                                isRefreshingTasks = taskState.isLoading,
                                canManageTasks = canManageTasks,
                                onEdit = { editorMode = TaskEditorMode.Edit(it) },
                                onDelete = { deleteTarget = it },
                                onTogglePause = { item ->
                                    if (gatewayId != null) {
                                        scope.launch { taskStore.toggleTask(gatewayId, item.id, !item.enabled) }
                                    }
                                },
                                modifier = Modifier.animateItemPlacement()
                            )
                        }
                    }
                }
            }
        }

        taskState.errorMessage?.let { message ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .padding(bottom = 72.dp),
                action = { TextButton(onClick = taskStore::clearError) { Text("关闭") } }
            ) {
                Text(message)
            }
        }
    }

    editorMode?.let { mode ->
        TaskEditorSheet(
            mode = mode,
            isSubmitting = taskState.updatingTaskId == mode.updatingId,
            onDismiss = { editorMode = null },
            onSubmit = { draft ->
                if (gatewayId == null) return@TaskEditorSheet
                scope.launch {
                    val success = when (mode) {
                        TaskEditorMode.Create -> taskStore.createTask(gatewayId, draft)
                        is TaskEditorMode.Edit -> taskStore.updateTask(gatewayId, mode.task, draft)
                    }
                    if (success) editorMode = null
                }
            }
        )
    }

    deleteTarget?.let { task ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除「${task.title}」？") },
            text = { Text("删除后这个定时任务不会再执行。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        if (gatewayId != null) scope.launch { taskStore.deleteTask(gatewayId, task.id) }
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("保留") } }
        )
    }
}

@Composable
private fun TaskScreenBackdrop() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFF2F5FA),
                        Color(0xFFFFFFFF)
                    ),
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(1000f, 1000f)
                )
            )
    ) {
        // topTrailing Accent Glow
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(320.dp)
                .offset(x = 60.dp, y = (-60).dp)
                .graphicsLayer(alpha = 0.45f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(AccentBlue.copy(alpha = 0.25f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset.Unspecified,
                        radius = Float.POSITIVE_INFINITY
                    ),
                    CircleShape
                )
                .blur(80.dp)
        )
        
        // bottomLeading AccentSoft Glow
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(340.dp)
                .offset(x = (-80).dp, y = 80.dp)
                .graphicsLayer(alpha = 0.4f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(AccentBlueSoft.copy(alpha = 0.22f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset.Unspecified,
                        radius = Float.POSITIVE_INFINITY
                    ),
                    CircleShape
                )
                .blur(90.dp)
        )

        // Middle Subtle Glow for depth
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(500.dp)
                .offset(x = 100.dp, y = 150.dp)
                .graphicsLayer(alpha = 0.25f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(AccentBlue.copy(alpha = 0.1f), Color.Transparent)
                    ),
                    CircleShape
                )
                .blur(120.dp)
        )
    }
}

@Composable
private fun MaintenanceBanner(
    title: String,
    message: String,
    icon: ImageVector,
    tint: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.08f)),
        border = androidx.compose.foundation.BorderStroke(0.6.dp, tint.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(tint.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = tint)
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private enum class TaskListFilter(val title: String) {
    All("全部"),
    Active("运行中"),
    Paused("已暂停"),
    Completed("已完成");

    fun matches(task: TaskItem): Boolean = when (this) {
        All -> true
        Active -> task.enabled
        Paused -> !task.enabled && !TaskListPresentationLogic.isCompletedOnceTask(task)
        Completed -> TaskListPresentationLogic.isCompletedOnceTask(task)
    }
}

private data class TaskListSection(val filter: TaskListFilter, val tasks: List<TaskItem>)

private sealed interface TaskEditorMode {
    val title: String
    val subtitle: String
    val initialDraft: TaskDraft
    val updatingId: String

    data object Create : TaskEditorMode {
        override val title = "新建任务"
        override val subtitle = "设置任务内容、执行时间和重复规则。"
        override val initialDraft = TaskDraft(
            scheduleKind = "once",
            scheduleAt = TaskDateCodec.isoString(Instant.now().plusSeconds(15 * 60)),
            repeatAmount = "1",
            repeatUnit = "days"
        )
        override val updatingId = "new"
    }

    data class Edit(val task: TaskItem) : TaskEditorMode {
        override val title = "编辑任务"
        override val subtitle = "调整任务内容、执行时间和重复规则。"
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

private object TaskListPresentationLogic {
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
            shouldGroupVisibleTasks(selectedFilter, searchText) -> "按运行状态自动分组"
            term.isNotEmpty() -> "正在搜索「$term」"
            else -> "显示当前筛选结果"
        }
    }

    fun nextScheduledTask(tasks: List<TaskItem>): TaskItem? {
        return tasks.mapNotNull { task -> task.nextRunDate?.let { it to task } }.minByOrNull { it.first }?.second
    }

    fun taskBadgeTitle(task: TaskItem): String = when {
        task.enabled && task.nextRunDate != null -> "待执行"
        task.enabled -> "已启用"
        isCompletedOnceTask(task) -> "已完成"
        else -> "已暂停"
    }

    fun isCompletedOnceTask(task: TaskItem): Boolean = task.scheduleKind == "once" && task.nextRunDate == null

    private fun matchesSearch(task: TaskItem, term: String): Boolean {
        if (term.isEmpty()) return true
        return listOf(task.title, task.prompt, task.schedule, task.nextRunSummary, task.lastResultSummary)
            .any { it.contains(term, ignoreCase = true) }
    }
}

@Composable
private fun TasksOverviewCard(
    totalCount: Int,
    enabledCount: Int,
    pausedCount: Int,
    nextTask: TaskItem?,
    canCreateTask: Boolean,
    isRefreshingTasks: Boolean,
    onCreateTask: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "定时任务",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.onSurface,
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        )
                    ),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    nextTask?.nextRunSummary ?: "暂无待执行任务",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                    fontWeight = FontWeight.SemiBold
                )
            }
            IconButton(
                onClick = onCreateTask,
                enabled = canCreateTask && !isRefreshingTasks,
                modifier = Modifier
                    .size(48.dp)
                    .shadow(elevation = 8.dp, shape = CircleShape, spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape)
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            TaskSmallStatChip("全部", totalCount, AccentBlue)
            TaskSmallStatChip("运行中", enabledCount, SuccessGreen)
            if (pausedCount > 0) TaskSmallStatChip("已暂停", pausedCount, WarningOrange)
        }
    }
}

@Composable
private fun TaskSmallStatChip(title: String, value: Int, tint: Color) {
    Row(
        modifier = Modifier
            .clip(PillShape)
            .background(tint.copy(alpha = 0.08f))
            .border(0.5.dp, tint.copy(alpha = 0.12f), PillShape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(value.toString(), fontWeight = FontWeight.Black, color = tint, style = MaterialTheme.typography.bodyLarge)
        Text(title, fontWeight = FontWeight.Bold, color = tint, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TasksFilterStrip(
    tasks: List<TaskItem>,
    selectedFilter: TaskListFilter,
    onSelectFilter: (TaskListFilter) -> Unit
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TaskListFilter.entries.forEach { filter ->
            val count = tasks.count { filter.matches(it) }
            val selected = selectedFilter == filter
            IosFilterPill(
                title = filter.title,
                count = count,
                selected = selected,
                tint = filter.tint(),
                onClick = { onSelectFilter(filter) }
            )
        }
    }
}

@Composable
private fun IosFilterPill(
    title: String,
    count: Int,
    selected: Boolean,
    tint: Color,
    onClick: () -> Unit
) {
    val background = if (selected) tint else MaterialTheme.colorScheme.surface
    val foreground = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .then(
                if (selected) Modifier.shadow(8.dp, PillShape, spotColor = tint.copy(alpha = 0.4f))
                else Modifier.border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), PillShape)
            )
            .clip(PillShape)
            .background(background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = foreground
        )
        if (count > 0 || selected) {
            Text(
                count.toString(),
                modifier = Modifier
                    .clip(PillShape)
                    .background(if (selected) Color.White.copy(alpha = 0.22f) else tint.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = if (selected) Color.White else tint
            )
        }
    }
}

private fun TaskListFilter.tint(): Color = when (this) {
    TaskListFilter.All -> AccentBlue
    TaskListFilter.Active -> SuccessGreen
    TaskListFilter.Paused -> WarningOrange
    TaskListFilter.Completed -> AccentBlueSoft
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TasksTaskCard(
    task: TaskItem,
    updatingTaskId: String?,
    isRefreshingTasks: Boolean,
    canManageTasks: Boolean,
    onEdit: (TaskItem) -> Unit,
    onDelete: (TaskItem) -> Unit,
    onTogglePause: (TaskItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val busy = updatingTaskId == task.id
    val disabled = busy || isRefreshingTasks || !canManageTasks
    val tint = if (task.enabled) SuccessGreen else WarningOrange

    Surface(
        color = Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .shadow(12.dp, CardShape, clip = false, ambientColor = Color.Black.copy(alpha = 0.04f), spotColor = Color.Black.copy(alpha = 0.06f))
            .border(0.6.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f), CardShape)
            .clip(CardShape)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                        tint.copy(alpha = 0.05f)
                    )
                )
            )
            .clickable(
                enabled = canManageTasks,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onEdit(task) }
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(tint.copy(alpha = 0.12f), CircleShape)
                        .border(0.5.dp, tint.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (task.enabled) Icons.Default.AccessTime else Icons.Default.Timer,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        task.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            task.schedule,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                TaskBadge(TaskListPresentationLogic.taskBadgeTitle(task), tint)
            }

            Text(
                task.promptPreview,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        task.nextRunSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    TaskCircleButton(
                        icon = if (task.enabled) Icons.Default.Pause else Icons.Default.PlayArrow,
                        tint = if (task.enabled) WarningOrange else SuccessGreen,
                        contentDescription = if (task.enabled) "暂停" else "恢复",
                        enabled = !disabled,
                        busy = busy,
                        onClick = { onTogglePause(task) }
                    )
                    TaskCircleButton(
                        icon = Icons.Default.Edit,
                        tint = AccentBlue,
                        contentDescription = "编辑",
                        enabled = !disabled,
                        onClick = { onEdit(task) }
                    )
                    TaskCircleButton(
                        icon = Icons.Default.Delete,
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
                        contentDescription = "删除",
                        enabled = !disabled,
                        onClick = { onDelete(task) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskBadge(title: String, tint: Color) {
    Text(
        title,
        modifier = Modifier
            .clip(PillShape)
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Black,
        color = tint
    )
}

@Composable
private fun TaskCircleButton(
    icon: ImageVector,
    tint: Color,
    contentDescription: String,
    enabled: Boolean,
    busy: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = if (enabled) 0.12f else 0.06f))
            .border(0.5.dp, tint.copy(alpha = 0.1f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = tint)
        } else {
            Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun TasksEmptyStateCard(tasks: List<TaskItem>, searchText: String, onReset: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)),
        border = androidx.compose.foundation.BorderStroke(0.6.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                if (tasks.isEmpty()) "还没有定时任务" else "没有匹配的任务",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (tasks.isEmpty()) "创建第一个任务后，OpenClaw 会按计划自动执行。"
                else if (searchText.isNotBlank()) "换个关键词再试，或清空搜索。"
                else "当前筛选条件下没有任务。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
            if (tasks.isNotEmpty()) {
                Button(
                    onClick = onReset,
                    modifier = Modifier.padding(top = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                    shape = PillShape
                ) {
                    Text(
                        if (searchText.isNotBlank()) "清空搜索" else "查看全部",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskSearchBar(searchText: String, onSearchTextChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(25.dp, PillShape, clip = false, ambientColor = Color.Black.copy(alpha = 0.15f), spotColor = Color.Black.copy(alpha = 0.18f))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
                    )
                ),
                PillShape
            )
            .border(
                width = 0.8.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.05f)
                    )
                ),
                shape = PillShape
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp).graphicsLayer(scaleX = 1.1f, scaleY = 1.1f))
        Spacer(Modifier.width(12.dp))
        BasicTextField(
            value = searchText,
            onValueChange = onSearchTextChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                fontWeight = FontWeight.SemiBold
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Box {
                    if (searchText.isEmpty()) {
                        Text(
                            "搜索任务名称、内容或时间",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    innerTextField()
                }
            }
        )
        if (searchText.isNotEmpty()) {
            IconButton(onClick = { onSearchTextChange("") }, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.Close, contentDescription = "清空", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskEditorSheet(
    mode: TaskEditorMode,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (TaskDraft) -> Unit
) {
    var draft by remember(mode) { mutableStateOf(mode.initialDraft) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val normalizedDraft = remember(draft) { TaskStore.normalizeDraft(draft) }
    val validation = remember(normalizedDraft) { TaskStore.validateDraft(normalizedDraft) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = SheetShape,
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
        val topSafePadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = topSafePadding + 8.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(mode.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(mode.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Button(
                        onClick = { onSubmit(normalizedDraft) },
                        enabled = validation == null && !isSubmitting,
                        shape = PillShape,
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        if (isSubmitting) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp) else Text("保存")
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FieldLabel("任务内容")
                    IosTextField(
                        value = draft.prompt,
                        onValueChange = { draft = draft.copy(prompt = it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = "例如：每天整理今天的待办并发送给我",
                        minLines = 4,
                        maxLines = 8
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FieldLabel("任务名称")
                    IosTextField(
                        value = draft.title,
                        onValueChange = { draft = draft.copy(title = it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = "留空时自动从任务内容生成",
                        singleLine = true
                    )
                }
            }

            item {
                ExecutionSettingsCard(draft = draft, onDraftChange = { draft = it })
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FieldLabel("预览")
                    TasksTaskCard(
                        task = previewTask(normalizedDraft),
                        updatingTaskId = null,
                        isRefreshingTasks = false,
                        canManageTasks = false,
                        onEdit = {},
                        onDelete = {},
                        onTogglePause = {}
                    )
                }
            }

            if (validation != null) {
                item {
                    Text(validation, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun IosTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .clip(FieldShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
            .padding(horizontal = 18.dp, vertical = 17.dp),
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        textStyle = TextStyle(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = MaterialTheme.typography.bodyMedium.fontSize,
            fontWeight = FontWeight.Medium
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
                    )
                }
                innerTextField()
            }
        }
    )
}

@Composable
private fun ExecutionSettingsCard(draft: TaskDraft, onDraftChange: (TaskDraft) -> Unit) {
    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
            .animateContentSize()
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            FieldLabel("执行设置")
            Row(
                modifier = Modifier
                    .clip(PillShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ModeButton("一次性", Icons.Default.Bolt, draft.scheduleKind == "once", Modifier.weight(1f)) {
                    onDraftChange(draft.copy(scheduleKind = "once"))
                }
                ModeButton("重复", Icons.Default.Repeat, draft.scheduleKind == "repeat", Modifier.weight(1f)) {
                    onDraftChange(draft.copy(scheduleKind = "repeat"))
                }
            }

            SchedulePickerRow(
                label = if (draft.scheduleKind == "once") "执行时间" else "首次执行时间",
                value = draft.scheduleAt,
                onChange = { onDraftChange(draft.copy(scheduleAt = it)) }
            )

            if (draft.scheduleKind == "repeat") {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FieldLabel("间隔")
                        IosTextField(
                            value = draft.repeatAmount,
                            onValueChange = { value -> onDraftChange(draft.copy(repeatAmount = value.filter { it.isDigit() })) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = "1",
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FieldLabel("单位")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            listOf("minutes" to "分钟", "hours" to "小时", "days" to "天", "weeks" to "周").forEach { (value, title) ->
                                IosTinyPill(
                                    title = title,
                                    selected = draft.repeatUnit == value,
                                    onClick = { onDraftChange(draft.copy(repeatUnit = value)) },
                                )
                            }
                        }
                    }
                }
            }

            PresetStrip(draft = draft, onDraftChange = onDraftChange)
        }
    }
}

@Composable
private fun ModeButton(title: String, icon: ImageVector, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = PillShape,
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text(title, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun IosTinyPill(title: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        title,
        modifier = Modifier
            .clip(PillShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun SchedulePickerRow(label: String, value: String, onChange: (String) -> Unit) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val current = TaskDateCodec.instantFrom(value)?.atZone(zone)?.toLocalDateTime() ?: LocalDateTime.now().plusMinutes(15)
    val display = TaskDateCodec.displayString(value) ?: "选择时间"

    Row(
        modifier = Modifier
            .clip(FieldShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AccessTime, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(label, fontWeight = FontWeight.Bold)
                Text(display, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Button(
            onClick = {
                DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        val selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                val selected = LocalDateTime.of(selectedDate, LocalTime.of(hour, minute))
                                onChange(TaskDateCodec.isoString(selected.atZone(zone).toInstant()))
                            },
                            current.hour,
                            current.minute,
                            true
                        ).show()
                    },
                    current.year,
                    current.monthValue - 1,
                    current.dayOfMonth
                ).show()
            },
            shape = PillShape,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), contentColor = MaterialTheme.colorScheme.primary),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
        ) {
            Text("选择")
        }
    }
}

@Composable
private fun PresetStrip(draft: TaskDraft, onDraftChange: (TaskDraft) -> Unit) {
    val now = remember { LocalDateTime.now() }
    val zone = ZoneId.systemDefault()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel("快捷预设")
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PresetButton("15 分钟后", Icons.Default.AccessTime) {
                onDraftChange(draft.copy(scheduleKind = "once", scheduleAt = TaskDateCodec.isoString(Instant.now().plusSeconds(15 * 60))))
            }
            PresetButton("1 小时后", Icons.Default.Timer) {
                onDraftChange(draft.copy(scheduleKind = "once", scheduleAt = TaskDateCodec.isoString(Instant.now().plusSeconds(60 * 60))))
            }
            PresetButton("明天 9 点", Icons.Default.WbSunny) {
                val tomorrowNine = now.toLocalDate().plusDays(1).atTime(9, 0)
                onDraftChange(draft.copy(scheduleKind = "once", scheduleAt = TaskDateCodec.isoString(tomorrowNine.atZone(zone).toInstant())))
            }
            PresetButton("每天一次", Icons.Default.Repeat) {
                onDraftChange(draft.copy(scheduleKind = "repeat", repeatAmount = "1", repeatUnit = "days"))
            }
        }
    }
}

@Composable
private fun PresetButton(title: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(PillShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun previewTask(draft: TaskDraft): TaskItem {
    val title = draft.title.ifBlank { TaskStore.deriveTaskTitle(draft.prompt).ifBlank { "新任务" } }
    return TaskItem(
        id = "preview",
        title = title,
        prompt = draft.prompt,
        scheduleKind = draft.scheduleKind,
        scheduleAt = draft.scheduleAt,
        repeatAmount = draft.repeatAmount,
        repeatUnit = draft.repeatUnit,
        enabled = true,
        lastResult = "",
        nextRunAt = draft.scheduleAt,
        createdAt = "",
        updatedAt = ""
    )
}
