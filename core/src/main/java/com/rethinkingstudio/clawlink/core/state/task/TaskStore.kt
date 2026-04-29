package com.rethinkingstudio.clawlink.core.state.task

import com.rethinkingstudio.clawlink.core.models.tasks.TaskItem
import com.rethinkingstudio.clawlink.core.network.RelayAPIClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TaskState(
    val tasks: List<TaskItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val editingTask: TaskItem? = null
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
            _state.value = _state.value.copy(tasks = tasks, isLoading = false)
        } catch (e: Exception) {
            _state.value = _state.value.copy(isLoading = false, errorMessage = e.message)
        }
    }

    suspend fun toggleTask(gatewayId: String, taskId: String, enabled: Boolean) {
        try {
            apiClient.updateTask(gatewayId, taskId, enabled = enabled)
            val tasks = _state.value.tasks.map {
                if (it.id == taskId) it.copy(enabled = enabled) else it
            }
            _state.value = _state.value.copy(tasks = tasks)
        } catch (e: Exception) {
            _state.value = _state.value.copy(errorMessage = e.message)
        }
    }

    suspend fun deleteTask(gatewayId: String, taskId: String) {
        try {
            apiClient.deleteTask(gatewayId, taskId)
            _state.value = _state.value.copy(tasks = _state.value.tasks.filter { it.id != taskId })
        } catch (e: Exception) {
            _state.value = _state.value.copy(errorMessage = e.message)
        }
    }

    suspend fun createTask(gatewayId: String, title: String, prompt: String, scheduleKind: String, scheduleAt: String? = null, repeatAmount: String? = null, repeatUnit: String? = null) {
        try {
            val task = apiClient.createTask(gatewayId, title, prompt, scheduleKind, scheduleAt, repeatAmount, repeatUnit)
            _state.value = _state.value.copy(tasks = _state.value.tasks + task)
        } catch (e: Exception) {
            _state.value = _state.value.copy(errorMessage = e.message)
        }
    }

    fun setEditingTask(task: TaskItem?) {
        _state.value = _state.value.copy(editingTask = task)
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }
}
