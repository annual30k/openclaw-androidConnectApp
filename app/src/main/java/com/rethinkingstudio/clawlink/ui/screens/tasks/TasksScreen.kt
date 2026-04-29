package com.rethinkingstudio.clawlink.ui.screens.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rethinkingstudio.clawlink.core.models.tasks.TaskItem
import com.rethinkingstudio.clawlink.core.state.gateway.GatewayStore
import com.rethinkingstudio.clawlink.core.state.task.TaskStore
import com.rethinkingstudio.clawlink.ui.components.ClawLinkScaffold
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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

    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(gatewayId) {
        if (gatewayId != null) taskStore.loadTasks(gatewayId)
    }

    ClawLinkScaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tasks (${taskState.activeTasks.size} active)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (gatewayId != null) scope.launch { taskStore.loadTasks(gatewayId) }
                    }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, "Create Task")
                    }
                }
            )
        }
    ) { padding ->
        if (taskState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (taskState.activeTasks.isNotEmpty()) {
                    item {
                        Text(
                            "Active",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    items(taskState.activeTasks, key = { it.id }) { task ->
                        TaskRow(
                            task = task,
                            onToggle = { enabled ->
                                if (gatewayId != null) {
                                    scope.launch { taskStore.toggleTask(gatewayId, task.id, enabled) }
                                }
                            },
                            onDelete = {
                                if (gatewayId != null) {
                                    scope.launch { taskStore.deleteTask(gatewayId, task.id) }
                                }
                            }
                        )
                    }
                }

                if (taskState.pausedTasks.isNotEmpty()) {
                    item {
                        Text(
                            "Paused",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(taskState.pausedTasks, key = { it.id }) { task ->
                        TaskRow(
                            task = task,
                            onToggle = { enabled ->
                                if (gatewayId != null) {
                                    scope.launch { taskStore.toggleTask(gatewayId, task.id, enabled) }
                                }
                            },
                            onDelete = {
                                if (gatewayId != null) {
                                    scope.launch { taskStore.deleteTask(gatewayId, task.id) }
                                }
                            }
                        )
                    }
                }

                if (taskState.tasks.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No tasks yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        if (taskState.errorMessage != null) {
            Snackbar(
                modifier = Modifier.padding(16.dp),
                action = {
                    TextButton(onClick = { taskStore.clearError() }) { Text("Dismiss") }
                }
            ) { Text(taskState.errorMessage ?: "") }
        }
    }

    if (showCreateDialog) {
        CreateTaskDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { title, prompt, scheduleKind, scheduleAt, repeatAmount, repeatUnit ->
                if (gatewayId != null) {
                    scope.launch {
                        taskStore.createTask(gatewayId, title, prompt, scheduleKind, scheduleAt, repeatAmount, repeatUnit)
                    }
                }
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun TaskRow(
    task: TaskItem,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(task.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text(
                        task.template,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    task.nextRunAt?.let {
                        Text("Next: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Switch(checked = task.enabled, onCheckedChange = onToggle)
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, "Details")
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                DetailLine("Schedule", task.scheduleKind)
                task.scheduleAt?.let { DetailLine("At", it) }
                task.repeatAmount?.let { DetailLine("Repeat", "$it ${task.repeatUnit ?: ""}") }
                DetailLine("Last Result", task.lastResult.ifEmpty { "(none)" })
                DetailLine("Created", task.createdAt)
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text("$label: ", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CreateTaskDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, prompt: String, scheduleKind: String, scheduleAt: String?, repeatAmount: String?, repeatUnit: String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var scheduleKind by remember { mutableStateOf("repeat") }
    var repeatAmount by remember { mutableStateOf("") }
    var repeatUnit by remember { mutableStateOf("hours") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Prompt") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = repeatAmount,
                        onValueChange = { repeatAmount = it },
                        label = { Text("Every") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = repeatUnit,
                        onValueChange = { repeatUnit = it },
                        label = { Text("Unit") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onCreate(
                        title.trim(),
                        prompt.trim(),
                        scheduleKind,
                        null,
                        repeatAmount.trim().ifEmpty { null },
                        repeatUnit.trim().ifEmpty { null }
                    )
                },
                enabled = title.isNotBlank() && prompt.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
