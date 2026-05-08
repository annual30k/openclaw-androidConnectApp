package com.rethinkingstudio.clawlink.core.state.task

import com.rethinkingstudio.clawlink.core.models.tasks.TaskDateCodec
import com.rethinkingstudio.clawlink.core.models.tasks.TaskDraft
import com.rethinkingstudio.clawlink.core.models.tasks.TaskItem
import com.rethinkingstudio.clawlink.core.network.RelayAPIClient
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TaskState(
    val tasks: List<TaskItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val editingTask: TaskItem? = null,
    val updatingTaskId: String? = null
) {
    val activeTasks: List<TaskItem> get() = tasks.filter { it.enabled }
    val pausedTasks: List<TaskItem> get() = tasks.filter { !it.enabled }
}

class TaskStore(
    private val apiClient: RelayAPIClient
) {
    private val _state = MutableStateFlow(TaskState())
    val state: StateFlow<TaskState> = _state.asStateFlow()

    suspend fun loadTasks(gatewayId: String) {
        _state.value = _state.value.copy(isLoading = true)
        try {
            val tasks = apiClient.fetchTasks(gatewayId)
            _state.value = _state.value.copy(tasks = sortTasks(tasks), isLoading = false, errorMessage = null)
        } catch (e: Exception) {
            _state.value = _state.value.copy(isLoading = false, errorMessage = e.message)
        }
    }

    suspend fun toggleTask(gatewayId: String, taskId: String, enabled: Boolean) {
        if (_state.value.updatingTaskId != null && _state.value.updatingTaskId != taskId) return
        _state.value = _state.value.copy(updatingTaskId = taskId, errorMessage = null)
        try {
            val updated = apiClient.setTaskEnabled(gatewayId, taskId, enabled)
            _state.value = _state.value.copy(tasks = replaceTask(updated, _state.value.tasks))
        } catch (e: Exception) {
            _state.value = _state.value.copy(errorMessage = e.message)
        } finally {
            if (_state.value.updatingTaskId == taskId) {
                _state.value = _state.value.copy(updatingTaskId = null)
            }
        }
    }

    suspend fun deleteTask(gatewayId: String, taskId: String) {
        if (_state.value.updatingTaskId != null && _state.value.updatingTaskId != taskId) return
        _state.value = _state.value.copy(updatingTaskId = taskId, errorMessage = null)
        try {
            apiClient.deleteTask(gatewayId, taskId)
            _state.value = _state.value.copy(tasks = _state.value.tasks.filter { it.id != taskId })
        } catch (e: Exception) {
            _state.value = _state.value.copy(errorMessage = e.message)
        } finally {
            if (_state.value.updatingTaskId == taskId) {
                _state.value = _state.value.copy(updatingTaskId = null)
            }
        }
    }

    suspend fun createTask(gatewayId: String, draft: TaskDraft): Boolean {
        val normalized = normalizeDraft(draft)
        validateDraft(normalized)?.let {
            _state.value = _state.value.copy(errorMessage = it)
            return false
        }
        _state.value = _state.value.copy(updatingTaskId = "new", errorMessage = null)
        try {
            val task = apiClient.createTask(gatewayId, normalized)
            _state.value = _state.value.copy(tasks = replaceTask(task, _state.value.tasks))
            return true
        } catch (e: Exception) {
            _state.value = _state.value.copy(errorMessage = e.message)
            return false
        } finally {
            if (_state.value.updatingTaskId == "new") {
                _state.value = _state.value.copy(updatingTaskId = null)
            }
        }
    }

    suspend fun updateTask(gatewayId: String, task: TaskItem, draft: TaskDraft): Boolean {
        if (_state.value.updatingTaskId != null && _state.value.updatingTaskId != task.id) return false
        val normalized = normalizeDraft(draft)
        validateDraft(normalized)?.let {
            _state.value = _state.value.copy(errorMessage = it)
            return false
        }
        _state.value = _state.value.copy(updatingTaskId = task.id, errorMessage = null)
        try {
            val updated = apiClient.updateTask(gatewayId, task.id, normalized)
            _state.value = _state.value.copy(tasks = replaceTask(updated, _state.value.tasks))
            return true
        } catch (e: Exception) {
            _state.value = _state.value.copy(errorMessage = e.message)
            return false
        } finally {
            if (_state.value.updatingTaskId == task.id) {
                _state.value = _state.value.copy(updatingTaskId = null)
            }
        }
    }

    fun setEditingTask(task: TaskItem?) {
        _state.value = _state.value.copy(editingTask = task)
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    companion object {
        fun normalizeDraft(draft: TaskDraft): TaskDraft {
            val prompt = draft.prompt.trim()
            val title = draft.title.trim().ifEmpty { deriveTaskTitle(prompt) }
            val scheduleKind = draft.scheduleKind.trim().ifEmpty { "once" }
            return draft.copy(
                title = title,
                prompt = prompt,
                scheduleKind = scheduleKind,
                scheduleAt = draft.scheduleAt.trim(),
                repeatAmount = if (scheduleKind == "repeat") draft.repeatAmount.trim().ifEmpty { "1" } else "",
                repeatUnit = if (scheduleKind == "repeat") draft.repeatUnit.trim().ifEmpty { "days" } else ""
            )
        }

        fun validateDraft(draft: TaskDraft): String? {
            if (draft.prompt.trim().isEmpty()) return choose("Enter the task prompt.", "请输入任务内容")
            return when (draft.scheduleKind) {
                "once" -> if (draft.scheduleAt.isBlank()) choose("Choose a run time.", "请选择执行时间") else null
                "repeat" -> when {
                    draft.scheduleAt.isBlank() -> choose("Choose the first run time.", "请选择首次执行时间")
                    draft.repeatAmount.toIntOrNull()?.let { it > 0 } != true -> choose("Repeat interval must be greater than 0.", "重复间隔必须大于 0")
                    draft.repeatUnit.isBlank() -> choose("Choose a time unit.", "请选择时间单位")
                    else -> null
                }
                else -> choose("Choose a schedule mode.", "请选择执行模式")
            }
        }

        fun deriveTaskTitle(prompt: String): String {
            val normalized = prompt.trim().replace(Regex("\\s+"), " ")
            if (normalized.isEmpty()) return ""
            val end = normalized.indexOfFirst { it in "。！？!?;；,，、\n" }
            val sentence = if (end >= 0) normalized.substring(0, end) else normalized
            return sentence.trim().take(40)
        }

        fun sortTasks(input: List<TaskItem>): List<TaskItem> {
            return input.sortedWith(
                compareBy<TaskItem> {
                    when {
                        it.scheduleKind == "once" && it.nextRunDate == null -> 2
                        it.enabled -> 0
                        else -> 1
                    }
                }.thenBy { it.nextRunDate ?: java.time.Instant.MAX }
                    .thenByDescending { TaskDateCodec.instantFrom(it.updatedAt) ?: java.time.Instant.MIN }
                    .thenBy { it.title.lowercase() }
            )
        }

        fun replaceTask(task: TaskItem, bucket: List<TaskItem>): List<TaskItem> {
            val next = bucket.toMutableList()
            val index = next.indexOfFirst { it.id == task.id }
            if (index >= 0) next[index] = task else next.add(task)
            return sortTasks(next)
        }
    }
}
