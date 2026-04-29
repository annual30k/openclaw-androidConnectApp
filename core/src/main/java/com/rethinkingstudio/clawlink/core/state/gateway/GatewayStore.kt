package com.rethinkingstudio.clawlink.core.state.gateway

import com.rethinkingstudio.clawlink.core.models.gateway.GatewaySummary
import com.rethinkingstudio.clawlink.core.domain.CredentialStore
import com.rethinkingstudio.clawlink.core.network.RelayAPIClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GatewayState(
    val gateways: List<GatewaySummary> = emptyList(),
    val selectedGatewayId: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val selectedGateway: GatewaySummary? get() = gateways.find { it.id == selectedGatewayId }
}

class GatewayStore(
    private val apiClient: RelayAPIClient,
    private val credentialStore: CredentialStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(GatewayState())
    val state: StateFlow<GatewayState> = _state.asStateFlow()

    val selectedGatewayId: String? get() = _state.value.selectedGatewayId

    suspend fun loadGateways() {
        _state.value = _state.value.copy(isLoading = true, errorMessage = null)
        try {
            val gateways = apiClient.fetchGateways()
            val persistedSelected = credentialStore.loadLastGatewayId()
            val currentSelected = _state.value.selectedGatewayId
            val selectedId = when {
                currentSelected != null && gateways.any { it.id == currentSelected } -> currentSelected
                persistedSelected != null && gateways.any { it.id == persistedSelected } -> persistedSelected
                else -> gateways.firstOrNull()?.id
            }
            _state.value = _state.value.copy(gateways = gateways, selectedGatewayId = selectedId, isLoading = false)
            if (selectedId != null && selectedId != currentSelected) {
                scope.launch { credentialStore.saveLastGatewayId(selectedId) }
            }
        } catch (e: Exception) {
            _state.value = _state.value.copy(isLoading = false, errorMessage = e.message)
        }
    }

    fun selectGateway(gatewayId: String) {
        _state.value = _state.value.copy(selectedGatewayId = gatewayId)
        scope.launch { credentialStore.saveLastGatewayId(gatewayId) }
    }

    fun updateGatewayStatus(gatewayId: String, gateway: GatewaySummary) {
        val list = _state.value.gateways.map { if (it.id == gatewayId) gateway else it }
        _state.value = _state.value.copy(gateways = list)
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }
}
