package com.rethinkingstudio.clawlink.core.state.backup

import com.rethinkingstudio.clawlink.core.models.backups.BackupDraft
import com.rethinkingstudio.clawlink.core.models.backups.BackupItem
import com.rethinkingstudio.clawlink.core.network.RelayAPIClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BackupState(
    val backups: List<BackupItem> = emptyList(),
    val maxBackups: Int = 5,
    val storagePath: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class BackupStore(
    private val apiClient: RelayAPIClient
) {
    private val _state = MutableStateFlow(BackupState())
    val state: StateFlow<BackupState> = _state.asStateFlow()

    suspend fun loadBackups(gatewayId: String) {
        _state.value = _state.value.copy(isLoading = true)
        try {
            val response = apiClient.fetchBackups(gatewayId)
            _state.value = _state.value.copy(
                backups = response.backups,
                maxBackups = response.maxBackups,
                storagePath = response.storagePath,
                isLoading = false
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(isLoading = false, errorMessage = e.message)
        }
    }

    suspend fun createBackup(gatewayId: String, draft: BackupDraft) {
        try {
            val response = apiClient.createBackup(gatewayId, draft)
            _state.value = _state.value.copy(
                backups = response.backups,
                maxBackups = response.maxBackups,
                storagePath = response.storagePath
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(errorMessage = e.message)
        }
    }

    suspend fun deleteBackup(gatewayId: String, backupId: String) {
        try {
            val response = apiClient.deleteBackup(gatewayId, backupId)
            _state.value = _state.value.copy(
                backups = response.backups,
                maxBackups = response.maxBackups,
                storagePath = response.storagePath
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(errorMessage = e.message)
        }
    }

    suspend fun restoreBackup(gatewayId: String, backupId: String) {
        try {
            val response = apiClient.restoreBackup(gatewayId, backupId)
            _state.value = _state.value.copy(
                backups = response.backups,
                maxBackups = response.maxBackups,
                storagePath = response.storagePath
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(errorMessage = e.message)
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }
}
