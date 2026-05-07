package com.rethinkingstudio.clawlink.core.state.gateway

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
import com.rethinkingstudio.clawlink.core.models.gateway.GatewaySummary
import com.rethinkingstudio.clawlink.core.models.gateway.ConnectionPhase
import com.rethinkingstudio.clawlink.core.models.MaintenanceLogEntry
import java.util.UUID
import java.util.Date
import java.util.Locale
import java.text.SimpleDateFormat
import kotlinx.coroutines.delay

data class GatewayState(
    val gateways: List<GatewaySummary> = emptyList(),
    val selectedGatewayId: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val appRelayStatus: AggregateStatus = AggregateStatus.offline,
    val restartLogs: List<MaintenanceLogEntry> = emptyList(),
    val remoteRestartLogs: List<MaintenanceLogEntry> = emptyList(),
    val doctorFixLogs: List<MaintenanceLogEntry> = emptyList(),
    val restartRequestId: String? = null,
    val remoteRestartRequestId: String? = null,
    val doctorFixRequestId: String? = null,
    val restartingGatewayId: String? = null,
    val selectedChatSessionKey: String? = null,
    val isExecutingMaintenance: Boolean = false,
    val isWaitingForRecovery: Boolean = false,
    val maintenanceError: String? = null,
    val maintenanceStartedAt: Long? = null
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
                when (event.event) {
                    "presence" -> handlePresence(event.payload)
                    "chat_run_log", "chat_run_delta", "chat_run_complete", "chat_run_error", "doctor_fix_log" -> {
                        handleMaintenanceLog(event)
                        if (event.event == "chat_run_complete") {
                            handleRunComplete(event)
                        }
                    }
                }
            }
            "chat_run_log", "chat_run_delta", "chat_run_complete", "chat_run_error", "doctor_fix_log" -> {
                handleMaintenanceLog(event)
                if (event.type == "chat_run_complete" || event.event == "chat_run_complete" || 
                    event.type == "chat_run_error" || event.event == "chat_run_error") {
                    handleRunComplete(event)
                }
            }
            "hello" -> {
                handleHello(event.payload)
                updateAppRelayStatus()
            }
            "res" -> handleCommandResponse(event)
            else -> {}
        }
    }

    private fun handleCommandResponse(event: WsEvent) {
        val requestId = event.payload?.jsonObject?.get("id")?.jsonPrimitive?.content ?: return
        val currentIds = listOf(_state.value.restartRequestId, _state.value.remoteRestartRequestId, _state.value.doctorFixRequestId)
        if (requestId in currentIds) {
            val ok = event.payload.jsonObject["ok"]?.jsonPrimitive?.content == "true"
            if (!ok) {
                val error = event.payload.jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content ?: "Unknown error"
                _state.value = _state.value.copy(
                    isExecutingMaintenance = false,
                    maintenanceError = error
                )
            }
        }
    }

    private fun handleMaintenanceLog(event: WsEvent) {
        val fullObj = event.payload?.jsonObject ?: return
        val payload = fullObj["payload"]?.jsonObject ?: fullObj
        val requestId = payload["runId"]?.jsonPrimitive?.content 
            ?: payload["run_id"]?.jsonPrimitive?.content
            ?: payload["requestId"]?.jsonPrimitive?.content
            ?: payload["request_id"]?.jsonPrimitive?.content
            ?: event.id
            
        if (requestId == null) return
        
        val entry = createLogEntry(payload)
        if (entry.text.isEmpty() && event.type != "chat_run_complete" && event.event != "chat_run_complete") return

        val isCompleted = payload["status"]?.jsonPrimitive?.content == "completed" || 
                         payload["state"]?.jsonPrimitive?.content == "completed" ||
                         entry.text.contains("exited with code", ignoreCase = true)

        when (requestId) {
            _state.value.restartRequestId -> {
                _state.value = _state.value.copy(restartLogs = _state.value.restartLogs + entry)
                if (isCompleted) handleRunComplete(event)
            }
            _state.value.remoteRestartRequestId -> {
                _state.value = _state.value.copy(remoteRestartLogs = _state.value.remoteRestartLogs + createLogEntry(payload))
                if (isCompleted) handleRunComplete(event)
            }
            _state.value.doctorFixRequestId -> {
                _state.value = _state.value.copy(doctorFixLogs = _state.value.doctorFixLogs + entry)
                if (isCompleted) {
                    _state.value = _state.value.copy(isExecutingMaintenance = false)
                }
            }
        }
    }

    private fun handleRunComplete(event: WsEvent) {
        val fullObj = event.payload?.jsonObject ?: return
        val payload = fullObj["payload"]?.jsonObject ?: fullObj
        val runId = payload["runId"]?.jsonPrimitive?.content 
            ?: payload["run_id"]?.jsonPrimitive?.content
            ?: event.id
            ?: return
        if (runId == _state.value.doctorFixRequestId) {
            _state.value = _state.value.copy(isExecutingMaintenance = false)
        }
    }

    private fun createLogEntry(payload: JsonObject): MaintenanceLogEntry {
        val text = payload["text"]?.jsonPrimitive?.content
            ?: payload["delta"]?.jsonPrimitive?.content
            ?: payload["errorMessage"]?.jsonPrimitive?.content
            ?: payload["error_message"]?.jsonPrimitive?.content
            ?: payload["data"]?.jsonObject?.get("text")?.jsonPrimitive?.content
            ?: payload["data"]?.jsonObject?.get("delta")?.jsonPrimitive?.content
            ?: payload["data"]?.jsonObject?.get("error")?.jsonPrimitive?.content
            ?: payload["data"]?.jsonObject?.get("result")?.jsonPrimitive?.content
            ?: ""
        val stream = payload["stream"]?.jsonPrimitive?.content ?: "stdout"
        return MaintenanceLogEntry(
            timestamp = System.currentTimeMillis(),
            stream = stream,
            text = text
        )
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

    fun selectChatSession(sessionKey: String) {
        _state.value = _state.value.copy(selectedChatSessionKey = sessionKey)
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
        _state.value = _state.value.copy(restartingGatewayId = gatewayId)
        try {
            apiClient.restartGateway(gatewayId)
        } catch (e: Exception) {
            _state.value = _state.value.copy(errorMessage = "Failed to restart: ${e.message}")
        } finally {
            _state.value = _state.value.copy(restartingGatewayId = null)
        }
    }

    suspend fun executeAdvancedAction(gatewayId: String, method: String, kind: String) {
        val requestId = UUID.randomUUID().toString()
        val startedAt = System.currentTimeMillis()
        
        _state.value = when (kind) {
            "gateway.restart" -> _state.value.copy(
                restartRequestId = requestId,
                restartLogs = emptyList(),
                isExecutingMaintenance = true,
                maintenanceError = null,
                restartingGatewayId = gatewayId,
                maintenanceStartedAt = startedAt
            )
            "gateway.remoteRestart" -> _state.value.copy(
                remoteRestartRequestId = requestId,
                remoteRestartLogs = emptyList(),
                isExecutingMaintenance = true,
                maintenanceError = null,
                restartingGatewayId = gatewayId,
                maintenanceStartedAt = startedAt
            )
            "openclaw.doctorFix" -> _state.value.copy(
                doctorFixRequestId = requestId,
                doctorFixLogs = emptyList(),
                isExecutingMaintenance = true,
                maintenanceError = null,
                restartingGatewayId = gatewayId,
                maintenanceStartedAt = startedAt
            )
            else -> _state.value
        }

        try {
            if (kind.contains("restart", ignoreCase = true)) {
                try {
                    apiClient.approveSensitiveAction(gatewayId, method)
                } catch (e: Exception) {
                    // Ignore non-critical errors or non-sensitive method errors
                }
            }

            wsClient.executeCommand(gatewayId, method, requestId = requestId)
            
            if (kind.contains("restart", ignoreCase = true)) {
                _state.value = _state.value.copy(isWaitingForRecovery = true)
                startRecoveryMonitoring(gatewayId)
            }
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                isExecutingMaintenance = false,
                maintenanceError = e.message,
                restartingGatewayId = null
            )
        }
    }

    fun startRecoveryMonitoring(gatewayId: String) {
        scope.launch {
            var attempts = 0
            val startedAt = _state.value.maintenanceStartedAt ?: System.currentTimeMillis()
            val requestId = when {
                _state.value.restartingGatewayId == gatewayId -> {
                    if (_state.value.restartRequestId != null) _state.value.restartRequestId
                    else if (_state.value.remoteRestartRequestId != null) _state.value.remoteRestartRequestId
                    else _state.value.doctorFixRequestId
                }
                else -> null
            }
            
            while (_state.value.isWaitingForRecovery && attempts < 60) {
                delay(3000)
                attempts++
                
                val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                appendMaintenanceLog(requestId, "sys", "[$timestamp] [MONITOR] 正在轮询主机状态...")
                
                try {
                    val gateways = apiClient.fetchGateways()
                    val gateway = gateways.find { it.id == gatewayId }
                    
                    if (gateway != null) {
                        val statusLabel = when (gateway.aggregateStatus) {
                            AggregateStatus.online -> "在线"
                            AggregateStatus.partial -> "半可用"
                            AggregateStatus.connecting -> "连接中"
                            AggregateStatus.offline -> "离线"
                        }
                        appendMaintenanceLog(requestId, "sys", "  - 当前状态: $statusLabel")
                        
                        for (status in gateway.statuses) {
                            appendMaintenanceLog(requestId, "sys", "    - ${status.phase}: ${status.status} ${status.detail}")
                        }
                    }

                    val isInitialGracePeriod = System.currentTimeMillis() - startedAt < 10000
                    if (!isInitialGracePeriod && gatewayIsFullyOnline(gateway)) {
                        appendMaintenanceLog(requestId, "sys", "[$timestamp] [WAIT] 主机核心组件已就绪，正在进行最终就绪性检查...")
                        
                        // Double check if desktop chat is ready
                        val isChatReady = try {
                            apiClient.checkChatReady(gatewayId)
                        } catch (_: Exception) {
                            false
                        }
                        
                        if (isChatReady) {
                            appendMaintenanceLog(requestId, "sys", "[$timestamp] [OK] 网关已完全恢复。")
                            _state.value = _state.value.copy(
                                isWaitingForRecovery = false,
                                restartingGatewayId = null,
                                isExecutingMaintenance = false,
                                maintenanceStartedAt = null
                            )
                            loadGateways() // Refresh local state
                            break
                        } else {
                            appendMaintenanceLog(requestId, "sys", "  - 桌面端服务尚未完全就绪，继续等待...")
                        }
                    }
                } catch (e: Exception) {
                    appendMaintenanceLog(requestId, "sys", "  - 轮询出错: ${e.message}")
                }
            }
            if (_state.value.isWaitingForRecovery) {
                _state.value = _state.value.copy(
                    isWaitingForRecovery = false,
                    restartingGatewayId = null,
                    isExecutingMaintenance = false,
                    maintenanceError = "Recovery timed out",
                    maintenanceStartedAt = null
                )
            }
        }
    }

    private fun appendMaintenanceLog(requestId: String?, stream: String, text: String) {
        if (requestId == null) return
        val entry = MaintenanceLogEntry(
            timestamp = System.currentTimeMillis(),
            stream = stream,
            text = text
        )
        _state.value = when (requestId) {
            _state.value.restartRequestId -> _state.value.copy(restartLogs = _state.value.restartLogs + entry)
            _state.value.remoteRestartRequestId -> _state.value.copy(remoteRestartLogs = _state.value.remoteRestartLogs + entry)
            _state.value.doctorFixRequestId -> _state.value.copy(doctorFixLogs = _state.value.doctorFixLogs + entry)
            else -> _state.value
        }
    }

    private fun gatewayIsFullyOnline(gateway: GatewaySummary?): Boolean {
        if (gateway == null) return false
        if (gateway.aggregateStatus != AggregateStatus.online) return false

        val relayHostStatus = gateway.statuses.find { it.phase == ConnectionPhase.relayHost }
        if (relayHostStatus?.status != AggregateStatus.online) return false

        val hostGatewayStatus = gateway.statuses.find { it.phase == ConnectionPhase.hostGateway }
        if (hostGatewayStatus?.status != AggregateStatus.online) return false

        val detail = hostGatewayStatus.detail.trim().lowercase()
        val stillWaiting = detail.contains("等待 openclaw") ||
                detail.contains("waiting openclaw") ||
                detail.contains("relay_connected") ||
                detail.contains("connecting openclaw") ||
                detail.contains("正在连接 openclaw") ||
                detail.contains("openclaw 未连接") ||
                detail.contains("openclaw 连接异常") ||
                detail.contains("openclaw 重试中")

        return !stillWaiting
    }

    fun checkSelectedGatewayRestartRecovery() {
        val gatewayId = _state.value.restartingGatewayId ?: return
        startRecoveryMonitoring(gatewayId)
    }

    fun stopMaintenance() {
        _state.value = _state.value.copy(
            isExecutingMaintenance = false,
            isWaitingForRecovery = false,
            restartingGatewayId = null,
            restartRequestId = null,
            remoteRestartRequestId = null,
            doctorFixRequestId = null
        )
    }

    fun clearMaintenanceLogs(kind: String) {
        _state.value = when (kind) {
            "gateway.restart" -> _state.value.copy(restartRequestId = null, restartLogs = emptyList())
            "gateway.remoteRestart" -> _state.value.copy(remoteRestartRequestId = null, remoteRestartLogs = emptyList())
            "openclaw.doctorFix" -> _state.value.copy(doctorFixRequestId = null, doctorFixLogs = emptyList())
            else -> _state.value
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
