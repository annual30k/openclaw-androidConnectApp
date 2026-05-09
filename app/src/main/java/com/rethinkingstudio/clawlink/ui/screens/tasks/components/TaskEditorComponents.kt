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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskEditorSheet(
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
                    TextButton(onClick = onDismiss) { Text(choose("Cancel", "取消")) }
                    Button(
                        onClick = { onSubmit(normalizedDraft) },
                        enabled = validation == null && !isSubmitting,
                        shape = PillShape,
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        if (isSubmitting) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp) else Text(choose("Save", "保存"))
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FieldLabel(choose("Task content", "任务内容"))
                    IosTextField(
                        value = draft.prompt,
                        onValueChange = { draft = draft.copy(prompt = it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = choose("For example: summarize today's todos and send them to me every day", "例如：每天整理今天的待办并发送给我"),
                        minLines = 4,
                        maxLines = 8
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FieldLabel(choose("Task name", "任务名称"))
                    IosTextField(
                        value = draft.title,
                        onValueChange = { draft = draft.copy(title = it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = choose("Leave blank to generate from task content", "留空时自动从任务内容生成"),
                        singleLine = true
                    )
                }
            }

            item {
                ExecutionSettingsCard(draft = draft, onDraftChange = { draft = it })
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FieldLabel(choose("Preview", "预览"))
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
internal fun IosTextField(
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
internal fun ExecutionSettingsCard(draft: TaskDraft, onDraftChange: (TaskDraft) -> Unit) {
    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
            .animateContentSize()
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            FieldLabel(choose("Execution settings", "执行设置"))
            Row(
                modifier = Modifier
                    .clip(PillShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ModeButton(choose("Once", "一次性"), Icons.Default.Bolt, draft.scheduleKind == "once", Modifier.weight(1f)) {
                    onDraftChange(draft.copy(scheduleKind = "once"))
                }
                ModeButton(choose("Repeat", "重复"), Icons.Default.Repeat, draft.scheduleKind == "repeat", Modifier.weight(1f)) {
                    onDraftChange(draft.copy(scheduleKind = "repeat"))
                }
            }

            SchedulePickerRow(
                label = if (draft.scheduleKind == "once") choose("Run time", "执行时间") else choose("First run time", "首次执行时间"),
                value = draft.scheduleAt,
                onChange = { onDraftChange(draft.copy(scheduleAt = it)) }
            )

            if (draft.scheduleKind == "repeat") {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FieldLabel(choose("Interval", "间隔"))
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
                        FieldLabel(choose("Unit", "单位"))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            listOf(
                                "minutes" to choose("Minutes", "分钟"),
                                "hours" to choose("Hours", "小时"),
                                "days" to choose("Days", "天"),
                                "weeks" to choose("Weeks", "周")
                            ).forEach { (value, title) ->
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
internal fun ModeButton(title: String, icon: ImageVector, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
internal fun IosTinyPill(title: String, selected: Boolean, onClick: () -> Unit) {
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
internal fun SchedulePickerRow(label: String, value: String, onChange: (String) -> Unit) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val current = TaskDateCodec.instantFrom(value)?.atZone(zone)?.toLocalDateTime() ?: LocalDateTime.now().plusMinutes(15)
    val display = TaskDateCodec.displayString(value) ?: choose("Choose time", "选择时间")

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
            Text(choose("Choose", "选择"))
        }
    }
}

@Composable
internal fun PresetStrip(draft: TaskDraft, onDraftChange: (TaskDraft) -> Unit) {
    val now = remember { LocalDateTime.now() }
    val zone = ZoneId.systemDefault()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel(choose("Quick presets", "快捷预设"))
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PresetButton(choose("In 15 minutes", "15 分钟后"), Icons.Default.AccessTime) {
                onDraftChange(draft.copy(scheduleKind = "once", scheduleAt = TaskDateCodec.isoString(Instant.now().plusSeconds(15 * 60))))
            }
            PresetButton(choose("In 1 hour", "1 小时后"), Icons.Default.Timer) {
                onDraftChange(draft.copy(scheduleKind = "once", scheduleAt = TaskDateCodec.isoString(Instant.now().plusSeconds(60 * 60))))
            }
            PresetButton(choose("Tomorrow 9 AM", "明天 9 点"), Icons.Default.WbSunny) {
                val tomorrowNine = now.toLocalDate().plusDays(1).atTime(9, 0)
                onDraftChange(draft.copy(scheduleKind = "once", scheduleAt = TaskDateCodec.isoString(tomorrowNine.atZone(zone).toInstant())))
            }
            PresetButton(choose("Once daily", "每天一次"), Icons.Default.Repeat) {
                onDraftChange(draft.copy(scheduleKind = "repeat", repeatAmount = "1", repeatUnit = "days"))
            }
        }
    }
}

@Composable
internal fun PresetButton(title: String, icon: ImageVector, onClick: () -> Unit) {
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
internal fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

internal fun previewTask(draft: TaskDraft): TaskItem {
    val title = draft.title.ifBlank { TaskStore.deriveTaskTitle(draft.prompt).ifBlank { choose("New task", "新任务") } }
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
