package com.rethinkingstudio.clawlink.core.state.model

import com.rethinkingstudio.clawlink.core.models.catalog.ModelItem
import com.rethinkingstudio.clawlink.core.network.RelayAPIClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ModelState(
    val models: List<ModelItem> = emptyList(),
    val selectedModelId: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val selectedModel: ModelItem? get() = models.find { it.isSelected }
    val selectedModelDisplay: String get() = selectedModel?.displayName ?: "No model"
}

class ModelStore(
    private val apiClient: RelayAPIClient
) {
    private val _state = MutableStateFlow(ModelState())
    val state: StateFlow<ModelState> = _state.asStateFlow()

    suspend fun loadModels(gatewayId: String) {
        _state.value = _state.value.copy(isLoading = true)
        try {
            val models = apiClient.fetchModels(gatewayId)
            _state.value = _state.value.copy(models = models, isLoading = false)
        } catch (e: Exception) {
            _state.value = _state.value.copy(isLoading = false, errorMessage = e.message)
        }
    }

    suspend fun selectModel(gatewayId: String, model: ModelItem, sessionKey: String? = null) {
        try {
            apiClient.selectModel(gatewayId, model.providerId, model.modelId, model.modelAlias, model.modelName, sessionKey)
            val models = _state.value.models.map {
                it.copy(isSelected = it.modelId == model.modelId)
            }
            _state.value = _state.value.copy(models = models, selectedModelId = model.modelId)
        } catch (e: Exception) {
            _state.value = _state.value.copy(errorMessage = e.message)
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }
}
