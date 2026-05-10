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
import com.rethinkingstudio.clawlink.ui.components.ClawLinkAlertActionRole
import com.rethinkingstudio.clawlink.ui.components.ClawLinkAlertDialog
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId


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
    val canManageTasks = gatewayId != null && gatewayState.isSelectedGatewayChatChainReady

    var editorMode by remember { mutableStateOf<TaskEditorMode?>(null) }
    var deleteTarget by remember { mutableStateOf<TaskItem?>(null) }
    var selectedFilter by remember { mutableStateOf(TaskListFilter.All) }
    var searchText by remember { mutableStateOf("") }

    LaunchedEffect(gatewayId, gatewayState.isSelectedGatewayChatChainReady) {
        if (canManageTasks) taskStore.loadTasks(gatewayId)
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
                    title = { Text(choose("Scheduled Tasks", "定时任务"), fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = choose("Back", "返回"))
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { if (canManageTasks) scope.launch { taskStore.loadTasks(gatewayId) } },
                            enabled = canManageTasks && !taskState.isLoading
                        ) {
                            if (taskState.isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = choose("Refresh", "刷新"))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onBackground
                    )
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
                        if (gatewayState.restartingGatewayId != null && gatewayState.isSelectedGatewayChatChainReady) {
                            MaintenanceBanner(
                                title = choose("Gateway maintenance", "网关维护中"),
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
                        Box(Modifier.animateItem()) {
                            SectionTitle(
                                title = choose("Task list", "任务列表"),
                                subtitle = TaskListPresentationLogic.listSectionSubtitle(selectedFilter, searchText)
                            )
                        }
                    }

                    if (TaskListPresentationLogic.shouldGroupVisibleTasks(selectedFilter, searchText)) {
                        val sections = TaskListPresentationLogic.groupedSections(sortedTasks)
                        if (sections.isEmpty()) {
                            item(key = "empty_state") {
                                Box(Modifier.animateItem()) {
                                    TasksEmptyStateCard(taskState.tasks, searchText, onReset = { selectedFilter = TaskListFilter.All; searchText = "" })
                                }
                            }
                        } else {
                            sections.forEach { section ->
                                item(key = "section_${section.filter.title}") {
                                    Box(Modifier.animateItem()) {
                                        SectionTitle(title = section.filter.title, subtitle = choose("${section.tasks.size} tasks", "${section.tasks.size} 个任务"))
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
                                            if (canManageTasks) {
                                                scope.launch { taskStore.toggleTask(gatewayId, item.id, !item.enabled) }
                                            }
                                        },
                                        modifier = Modifier.animateItem()
                                    )
                                }
                            }
                        }
                    } else if (visibleTasks.isEmpty()) {
                        item(key = "empty_state") {
                            Box(Modifier.animateItem()) {
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
                                    if (canManageTasks) {
                                        scope.launch { taskStore.toggleTask(gatewayId, item.id, !item.enabled) }
                                    }
                                },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }
        }

        TaskSearchBar(
            searchText = searchText,
            onSearchTextChange = { searchText = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp)
        )

        if (gatewayState.isSelectedGatewayChatChainReady) taskState.errorMessage?.let { message ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .padding(bottom = 72.dp),
                action = { TextButton(onClick = taskStore::clearError) { Text(choose("Close", "关闭")) } }
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
                if (!canManageTasks) return@TaskEditorSheet
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
        ClawLinkAlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = choose("Delete \"${task.title}\"?", "删除「${task.title}」？"),
            message = choose("This scheduled task will not run after deletion.", "删除后这个定时任务不会再执行。"),
            confirmText = choose("Delete", "删除"),
            confirmRole = ClawLinkAlertActionRole.Destructive,
            onConfirm = {
                deleteTarget = null
                if (canManageTasks) scope.launch { taskStore.deleteTask(gatewayId, task.id) }
            },
            dismissText = choose("Keep", "保留"),
            onDismissAction = { deleteTarget = null }
        )
    }
}
