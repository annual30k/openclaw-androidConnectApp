package com.rethinkingstudio.clawlink.core.state.gateway

import com.rethinkingstudio.clawlink.core.models.gateway.GatewaySummary
import com.rethinkingstudio.clawlink.core.domain.CredentialStore
import com.rethinkingstudio.clawlink.core.network.RelayAPIClient
import com.rethinkingstudio.clawlink.core.network.transport.RelayWebSocketClient
import com.rethinkingstudio.clawlink.core.network.transport.WsEvent
import com.rethinkingstudio.clawlink.core.network.dto.GatewaySummaryDTO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import com.rethinkingstudio.clawlink.core.models.gateway.AggregateStatus
import com.rethinkingstudio.clawlink.core.models.gateway.GatewayStatus

data class GatewayState(
    val gateways: List<GatewaySummary> = emptyList(),
    val selectedGatewayId: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val appRelayStatus: AggregateStatus = AggregateStatus.offline
) {
    val selectedGateway: GatewaySummary? get() = gateways.find { it.id == selectedGatewayId }
}

class GatewayStore(
    private val apiClient: RelayAPIClient,
    private val credentialStore: CredentialStore,
    private val wsClient: RelayWebSocketClient
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(GatewayState())
    val state: StateFlow<GatewayState> = _state.asStateFlow()

    init {
        wsClient.events
            .onEach { event -> handleWsEvent(event) }
            .launchIn(scope)
    }

    private fun handleWsEvent(event: WsEvent) {
        when (event.type) {
            "presence" -> handlePresence(event.payload)
            "event" -> {
                if (event.event == "presence") {
                    handlePresence(event.payload)
                }
            }
            "hello" -> {
                handleHello(event.payload)
                updateAppRelayStatus()
            }
        }
    }

    private fun handleHello(payload: JsonElement?) {
        try {
            val obj = payload as? JsonObject ?: return
            val gatewayArray = (obj["payload"] as? JsonObject)?.get("gateways") as? JsonArray ?: return
            val summaries = gatewayArray.mapNotNull { element ->
                (element as? JsonObject)?.let {
                    runCatching { json.decodeFromJsonElement<GatewaySummaryDTO>(it).toGatewaySummary() }.getOrNull()
                }
            }
            if (summaries.isEmpty()) return
            val currentSelected = _state.value.selectedGatewayId
            val selectedId = when {
                currentSelected != null && summaries.any { it.id == currentSelected } -> currentSelected
                else -> summaries.firstOrNull()?.id
            }
            _state.value = _state.value.copy(gateways = summaries, selectedGatewayId = selectedId)
        } catch (_: Exception) { }
    }

    private fun handlePresence(payload: JsonElement?) {
        try {
            val fullObj = payload?.jsonObject ?: return
            // Server sends gateway at top-level "gateway" key.
            val gatewayElement = fullObj["gateway"]
                ?: (fullObj["payload"] as? JsonObject)?.get("gateway")
                ?: fullObj.takeIf { it.containsKey("gatewayId") && it.containsKey("aggregateStatus") }
            val gatewayDto = gatewayElement?.let { element ->
                if (element is JsonObject) {
                    json.decodeFromJsonElement<GatewaySummaryDTO>(element)
                } else null
            }
            if (gatewayDto != null) {
                val summary = gatewayDto.toGatewaySummary()
                updateGatewayStatus(summary.id, summary)
            }
            updateAppRelayStatus()
        } catch (_: Exception) { }
    }

    private fun updateAppRelayStatus() {
        val gateways = _state.value.gateways
        _state.value = _state.value.copy(
            appRelayStatus = when {
                gateways.isNotEmpty() -> AggregateStatus.online
                else -> AggregateStatus.offline
            }
        )
    }

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
            updateAppRelayStatus()
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
        val currentList = _state.value.gateways
        val exists = currentList.any { it.id == gatewayId }
        val list = if (exists) {
            currentList.map { if (it.id == gatewayId) gateway else it }
        } else {
            currentList + gateway
        }
        _state.value = _state.value.copy(gateways = list)
    }

    fun updateGatewayAggregateStatus(gatewayId: String, status: AggregateStatus) {
        val list = _state.value.gateways.map {
            if (it.id == gatewayId) it.copy(aggregateStatus = status) else it
        }
        _state.value = _state.value.copy(gateways = list)
    }

    suspend fun updateGatewayName(gatewayId: String, newName: String) {
        try {
            apiClient.updateGateway(gatewayId, mapOf("display_name" to newName))
            val list = _state.value.gateways.map {
                if (it.id == gatewayId) it.copy(displayName = newName) else it
            }
            _state.value = _state.value.copy(gateways = list)
        } catch (e: Exception) {
            _state.value = _state.value.copy(errorMessage = "Failed to update name: ${e.message}")
        }
    }

    suspend fun restartGateway(gatewayId: String) {
        try {
            apiClient.restartGateway(gatewayId)
        } catch (e: Exception) {
            _state.value = _state.value.copy(errorMessage = "Failed to restart: ${e.message}")
        }
    }

    suspend fun remoteRestartGateway(gatewayId: String) {
        try {
            apiClient.remoteRestartGateway(gatewayId)
        } catch (e: Exception) {
            _state.value = _state.value.copy(errorMessage = "Failed to remote restart: ${e.message}")
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    companion object {
        fun selectedGatewayStatuses(
            selectedGateway: GatewaySummary?,
            appRelayStatus: AggregateStatus,
            appRelayDetail: String = if (appRelayStatus == AggregateStatus.online) "Session active" else "Session not established"
        ): List<GatewayStatus> {
            val relayHost = selectedGateway?.statuses?.find { it.phase == com.rethinkingstudio.clawlink.core.models.gateway.ConnectionPhase.relayHost }
                ?: GatewayStatus(
                    phase = com.rethinkingstudio.clawlink.core.models.gateway.ConnectionPhase.relayHost,
                    status = AggregateStatus.offline,
                    detail = "Relay is not connected to the host"
                )
            val hostGateway = selectedGateway?.statuses?.find { it.phase == com.rethinkingstudio.clawlink.core.models.gateway.ConnectionPhase.hostGateway }
                ?: GatewayStatus(
                    phase = com.rethinkingstudio.clawlink.core.models.gateway.ConnectionPhase.hostGateway,
                    status = AggregateStatus.offline,
                    detail = "OpenClaw is not connected"
                )

            return listOf(
                GatewayStatus(
                    phase = com.rethinkingstudio.clawlink.core.models.gateway.ConnectionPhase.appRelay,
                    status = appRelayStatus,
                    detail = appRelayDetail
                ),
                relayHost,
                hostGateway
            )
        }
    }
}
