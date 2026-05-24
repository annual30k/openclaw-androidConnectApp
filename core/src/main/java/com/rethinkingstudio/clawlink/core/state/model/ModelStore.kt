package com.rethinkingstudio.clawlink.core.state.model

import com.rethinkingstudio.clawlink.core.models.catalog.ModelItem
import com.rethinkingstudio.clawlink.core.network.RelayAPIClient
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ModelState(
    val currentGatewayId: String? = null,
    val models: List<ModelItem> = emptyList(),
    val selectedModelId: String? = null,
    val isLoading: Boolean = false,
    val isUpdatingDefault: Boolean = false,
    val updatingDefaultModelKey: String? = null,
    val errorMessage: String? = null
) {
    val selectedModel: ModelItem? get() = models.find { it.isSelected }
    val selectedModelDisplay: String get() = selectedModel?.displayTitle ?: choose("No model", "未选择模型")
    val defaultModel: ModelItem? get() = models.find { it.isDefault }
    val groupedModels: Map<String, List<ModelItem>> get() = models.groupBy { it.provider.ifBlank { it.providerId } }
}

class ModelStore(
    private val apiClient: RelayAPIClient
) {
    private val _state = MutableStateFlow(ModelState())
    val state: StateFlow<ModelState> = _state.asStateFlow()
    private val modelsByGateway = mutableMapOf<String, List<ModelItem>>()

    suspend fun loadModels(gatewayId: String) {
        val normalizedGatewayId = gatewayId.trim()
        val cachedModels = modelsByGateway[normalizedGatewayId].orEmpty()
        _state.value = _state.value.copy(
            currentGatewayId = normalizedGatewayId,
            models = cachedModels,
            isLoading = true,
            errorMessage = null
        )
        try {
            val previousSelected = cachedModels.firstOrNull { it.isSelected }
            val models = mergeFetchedModels(apiClient.fetchModels(normalizedGatewayId), previousSelected)
            modelsByGateway[normalizedGatewayId] = models
            if (_state.value.currentGatewayId == normalizedGatewayId) {
                _state.value = _state.value.copy(models = models, isLoading = false)
            }
        } catch (e: Exception) {
            if (_state.value.currentGatewayId == normalizedGatewayId) {
                _state.value = _state.value.copy(isLoading = false, errorMessage = e.message)
            }
        }
    }

    suspend fun selectModel(gatewayId: String, model: ModelItem, sessionKey: String? = null): Boolean {
        val normalizedGatewayId = gatewayId.trim()
        val previousModels = modelsByGateway[normalizedGatewayId]
            ?: _state.value.models.takeIf { _state.value.currentGatewayId == normalizedGatewayId }
            ?: emptyList()
        val sourceModels = previousModels.ifEmpty { listOf(model) }
        val optimisticModels = sourceModels.map {
            it.copy(isSelected = it.providerId == model.providerId && it.modelId == model.modelId)
        }
        val previousSelectedModelId = previousModels.firstOrNull { it.isSelected }?.modelId

        modelsByGateway[normalizedGatewayId] = optimisticModels
        if (_state.value.currentGatewayId == normalizedGatewayId) {
            _state.value = _state.value.copy(
                models = optimisticModels,
                selectedModelId = model.modelId,
                errorMessage = null
            )
        }

        return try {
            apiClient.selectModel(normalizedGatewayId, model.providerId, model.modelId, model.modelAlias, model.modelName, sessionKey)
            runCatching {
                loadModels(normalizedGatewayId)
            }.onFailure { e ->
                android.util.Log.w("ModelStore", "Failed to refresh models after selecting ${model.modelId}", e)
            }
            true
        } catch (e: Exception) {
            modelsByGateway[normalizedGatewayId] = previousModels
            if (_state.value.currentGatewayId == normalizedGatewayId) {
                _state.value = _state.value.copy(
                    models = previousModels,
                    selectedModelId = previousSelectedModelId,
                    errorMessage = e.message
                )
            }
            false
        }
    }

    suspend fun setDefaultModel(
        gatewayId: String,
        model: ModelItem,
        waitForGatewayRecovery: suspend () -> Boolean = { true }
    ): Boolean {
        val key = modelKey(model)
        _state.value = _state.value.copy(isUpdatingDefault = true, updatingDefaultModelKey = key, errorMessage = null)
        return try {
            apiClient.setDefaultModel(gatewayId, model.providerId, model.modelId, model.modelAlias)
            val models = _state.value.models.map {
                it.copy(isDefault = it.providerId == model.providerId && it.modelId == model.modelId)
            }
            _state.value = _state.value.copy(models = models)
            val didRecover = waitForGatewayRecovery()
            if (!didRecover) {
                _state.value = _state.value.copy(
                    isUpdatingDefault = false,
                    updatingDefaultModelKey = null,
                    errorMessage = choose(
                        "Timed out waiting for OpenClaw to recover after setting the default model. The wait lock has been released. Please try again later.",
                        "设置默认模型后等待 OpenClaw 恢复超时，已解除等待锁定，请稍后重试。"
                    )
                )
                return false
            }
            _state.value = _state.value.copy(isUpdatingDefault = false, updatingDefaultModelKey = null)
            true
        } catch (e: Exception) {
            _state.value = _state.value.copy(isUpdatingDefault = false, updatingDefaultModelKey = null, errorMessage = e.message)
            false
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    companion object {
        fun mergeFetchedModels(fetchedModels: List<ModelItem>, previousSelectedModel: ModelItem?): List<ModelItem> {
            if (fetchedModels.isEmpty()) return fetchedModels
            var nextModels = fetchedModels
            if (nextModels.none { it.isSelected } && previousSelectedModel != null) {
                nextModels = nextModels.map { model ->
                    model.copy(isSelected = model.providerId == previousSelectedModel.providerId && model.modelId == previousSelectedModel.modelId)
                }
            }
            if (nextModels.none { it.isSelected }) {
                val defaultModel = nextModels.firstOrNull { it.isDefault }
                if (defaultModel != null) {
                    nextModels = nextModels.map { model ->
                        model.copy(isSelected = model.providerId == defaultModel.providerId && model.modelId == defaultModel.modelId)
                    }
                }
            }
            return nextModels
        }

        fun modelKey(model: ModelItem): String = "${model.providerId}||${model.modelId}"
    }
}
