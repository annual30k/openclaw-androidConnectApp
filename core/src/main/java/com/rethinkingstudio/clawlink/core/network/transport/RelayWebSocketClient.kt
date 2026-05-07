package com.rethinkingstudio.clawlink.core.network.transport

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class WsEvent(
    val type: String,
    val event: String? = null,
    val payload: JsonElement? = null
)

enum class WsConnectionState {
    disconnected, connecting, connected, reconnecting
}

class RelayWebSocketClient {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var webSocket: WebSocket? = null
    private val isConnected = AtomicBoolean(false)
    private val reconnectEnabled = AtomicBoolean(true)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var reconnectJob: Job? = null

    private val _events = MutableSharedFlow<WsEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<WsEvent> = _events.asSharedFlow()

    private val _connectionState = MutableStateFlow(WsConnectionState.disconnected)
    val connectionState: StateFlow<WsConnectionState> = _connectionState.asStateFlow()

    private var baseUrl: String = ""
    private var accessToken: String = ""
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5
    private val pendingOutbound = ArrayDeque<String>()
    private val maxPendingOutbound = 32
    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    fun connect(url: String, token: String) {
        cancelReconnect()
        reconnectEnabled.set(true)
        baseUrl = url
        accessToken = token
        _connectionState.value = WsConnectionState.connecting
        val wsUrl = url.replace("http://", "ws://").replace("https://", "wss://") + "/mobile/ws?accessToken=$token"
        val request = Request.Builder().url(wsUrl).build()
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected.set(true)
                reconnectAttempts = 0
                cancelReconnect()
                _connectionState.value = WsConnectionState.connected
                flushPendingOutbound(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val element = json.parseToJsonElement(text)
                    val obj = element as? JsonObject
                    val type = obj?.get("type")?.jsonPrimitive?.content ?: "unknown"
                    val event = obj?.get("event")?.jsonPrimitive?.content
                    _events.tryEmit(WsEvent(type, event, element))
                } catch (_: Exception) {
                    _events.tryEmit(WsEvent("error", null, null))
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected.set(false)
                this@RelayWebSocketClient.webSocket = null
                _connectionState.value = WsConnectionState.disconnected
                if (reconnectEnabled.get()) {
                    attemptReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected.set(false)
                this@RelayWebSocketClient.webSocket = null
                _connectionState.value = WsConnectionState.disconnected
                _events.tryEmit(WsEvent("error", null, null))
                if (reconnectEnabled.get()) {
                    attemptReconnect()
                }
            }
        })
    }

    fun sendChatMessage(
        gatewayId: String,
        sessionKey: String,
        content: String,
        idempotencyKey: String? = null,
        voiceReplyEnabled: Boolean = false,
        voiceReplyVoiceIdentifier: String? = null,
        voiceReplyRatePercent: Int? = null
    ) {
        val requestId = UUID.randomUUID().toString()
        val params = buildJsonObject {
            put("sessionKey", JsonPrimitive(sessionKey))
            put("message", JsonPrimitive(content))
            put("idempotencyKey", JsonPrimitive(idempotencyKey ?: requestId))
        }
        val payload = buildJsonObject {
            put("type", JsonPrimitive("cmd"))
            put("id", JsonPrimitive(requestId))
            put("gatewayId", JsonPrimitive(gatewayId))
            put("method", JsonPrimitive("chat.send"))
            if (voiceReplyEnabled) {
                put("voiceReplyEnabled", JsonPrimitive(true))
                val voice = voiceReplyVoiceIdentifier?.trim().orEmpty()
                if (voice.isNotBlank()) {
                    put("voiceReplyVoiceIdentifier", JsonPrimitive(voice))
                }
                voiceReplyRatePercent?.let { put("voiceReplyRatePercent", JsonPrimitive(it)) }
            }
            put("params", params)
        }
        send(payload.toString())
    }

    fun executeCommand(gatewayId: String, method: String, params: JsonObject? = null, requestId: String = UUID.randomUUID().toString()) {
        val payload = buildJsonObject {
            put("type", JsonPrimitive("cmd"))
            put("id", JsonPrimitive(requestId))
            put("gatewayId", JsonPrimitive(gatewayId))
            put("method", JsonPrimitive(method))
            if (params != null) {
                put("params", params)
            }
        }
        send(payload.toString())
    }

    fun syncVoiceReplyConfig(
        gatewayId: String,
        sessionKey: String,
        voiceReplyVoiceIdentifier: String?,
        voiceReplyRatePercent: Int
    ) {
        val requestId = UUID.randomUUID().toString()
        val params = buildJsonObject {
            put("sessionKey", JsonPrimitive(sessionKey))
            val voice = voiceReplyVoiceIdentifier?.trim().orEmpty()
            if (voice.isNotBlank()) {
                put("voiceReplyVoiceIdentifier", JsonPrimitive(voice))
            }
            put("voiceReplyRatePercent", JsonPrimitive(voiceReplyRatePercent))
        }
        val payload = buildJsonObject {
            put("type", JsonPrimitive("cmd"))
            put("id", JsonPrimitive(requestId))
            put("gatewayId", JsonPrimitive(gatewayId))
            put("method", JsonPrimitive("clawconnect.voiceReply.setConfig"))
            put("params", params)
        }
        send(payload.toString())
    }

    fun sendCommand(gatewayId: String, sessionKey: String, command: String) {
        val requestId = UUID.randomUUID().toString()
        val params = buildJsonObject {
            put("sessionKey", JsonPrimitive(sessionKey))
            put("message", JsonPrimitive(command))
            put("idempotencyKey", JsonPrimitive(requestId))
        }
        val payload = buildJsonObject {
            put("type", JsonPrimitive("cmd"))
            put("id", JsonPrimitive(requestId))
            put("gatewayId", JsonPrimitive(gatewayId))
            put("method", JsonPrimitive("chat.send"))
            put("params", params)
        }
        send(payload.toString())
    }

    fun abortChatRun(gatewayId: String, sessionKey: String, runId: String?, requestId: String = UUID.randomUUID().toString()) {
        val params = buildJsonObject {
            put("sessionKey", JsonPrimitive(sessionKey))
            if (!runId.isNullOrBlank()) {
                put("runId", JsonPrimitive(runId))
            }
        }
        val payload = buildJsonObject {
            put("type", JsonPrimitive("cmd"))
            put("id", JsonPrimitive(requestId))
            put("gatewayId", JsonPrimitive(gatewayId))
            put("method", JsonPrimitive("chat.abort"))
            put("params", params)
        }
        send(payload.toString())
    }

    fun sendPing() {
        send("""{"type":"ping"}""")
    }

    private fun send(text: String) {
        android.util.Log.d("RelayWS", "WS SEND: $text")
        val socket = webSocket
        if (socket != null && isConnected.get()) {
            socket.send(text)
            return
        }

        queuePendingOutbound(text)
        android.util.Log.w("RelayWS", "WS not connected; queued outbound message")
        if (
            baseUrl.isNotBlank()
            && accessToken.isNotBlank()
            && _connectionState.value != WsConnectionState.connecting
            && _connectionState.value != WsConnectionState.reconnecting
        ) {
            reconnectAttempts = 0
            reconnectEnabled.set(true)
            connect(baseUrl, accessToken)
        }
    }

    private fun queuePendingOutbound(text: String) {
        synchronized(pendingOutbound) {
            if (pendingOutbound.size >= maxPendingOutbound) {
                pendingOutbound.removeFirst()
            }
            pendingOutbound.addLast(text)
        }
    }

    private fun flushPendingOutbound(socket: WebSocket) {
        val messages = synchronized(pendingOutbound) {
            val queued = pendingOutbound.toList()
            pendingOutbound.clear()
            queued
        }
        for (message in messages) {
            android.util.Log.d("RelayWS", "WS FLUSH: $message")
            socket.send(message)
        }
    }

    private val reconnectDelays = listOf(1_000L, 2_000L, 4_000L, 8_000L, 15_000L)

    private fun attemptReconnect() {
        if (!reconnectEnabled.get() || reconnectAttempts >= maxReconnectAttempts) return
        cancelReconnect()
        val delayMs = reconnectDelays.getOrElse(reconnectAttempts) { 15_000L }
        reconnectAttempts++
        _connectionState.value = WsConnectionState.reconnecting
        reconnectJob = scope.launch {
            delay(delayMs)
            if (reconnectEnabled.get() && !isConnected.get()) {
                connect(baseUrl, accessToken)
            }
        }
    }

    private fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
    }

    fun disconnect() {
        isConnected.set(false)
        reconnectEnabled.set(false)
        cancelReconnect()
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        synchronized(pendingOutbound) { pendingOutbound.clear() }
        _connectionState.value = WsConnectionState.disconnected
    }

    fun suspendConnection() {
        if (!isConnected.get()) return
        isConnected.set(false)
        reconnectEnabled.set(false)
        cancelReconnect()
        webSocket?.close(1000, "Client suspend")
        webSocket = null
        _connectionState.value = WsConnectionState.disconnected
    }

    fun resumeConnection() {
        if (baseUrl.isNotBlank() && accessToken.isNotBlank()) {
            reconnectAttempts = 0
            reconnectEnabled.set(true)
            connect(baseUrl, accessToken)
        }
    }

    fun destroy() {
        disconnect()
        okHttpClient.dispatcher.executorService.shutdown()
        scope.cancel()
    }
}
