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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class WsEvent(
    val type: String,
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
                sendSubscribe()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val element = json.parseToJsonElement(text)
                    val obj = element as? JsonObject
                    val type = obj?.get("type")?.jsonPrimitive?.content ?: "unknown"
                    _events.tryEmit(WsEvent(type, element))
                } catch (_: Exception) {
                    _events.tryEmit(WsEvent("error", null))
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
                _events.tryEmit(WsEvent("error", null))
                if (reconnectEnabled.get()) {
                    attemptReconnect()
                }
            }
        })
    }

    private fun sendSubscribe() {
        send("""{"type":"subscribe"}""")
    }

    fun sendChatMessage(sessionKey: String, content: String, attachmentIds: List<String> = emptyList()) {
        val attachments = if (attachmentIds.isNotEmpty()) {
            ""","attachmentIds":[${attachmentIds.joinToString(",") { "\"$it\"" }}]"""
        } else ""
        send("""{"type":"chat_message","sessionKey":"$sessionKey","content":"${content.replace("\"", "\\\"")}"$attachments}""")
    }

    fun sendCommand(sessionKey: String, command: String) {
        send("""{"type":"command","sessionKey":"$sessionKey","command":"$command"}""")
    }

    fun sendModelSelect(providerId: String, modelId: String, sessionKey: String? = null) {
        val session = sessionKey?.let { ""","sessionKey":"$it""" } ?: ""
        send("""{"type":"model_select","providerId":"$providerId","modelId":"$modelId"$session}""")
    }

    fun sendPing() {
        send("""{"type":"ping"}""")
    }

    private fun send(text: String) {
        if (isConnected.get()) {
            webSocket?.send(text)
        }
    }

    private fun attemptReconnect() {
        if (!reconnectEnabled.get() || reconnectAttempts >= maxReconnectAttempts) return
        cancelReconnect()
        reconnectAttempts++
        _connectionState.value = WsConnectionState.reconnecting
        val backoffMs = minOf(1000L * (1 shl reconnectAttempts), 30_000L)
        reconnectJob = scope.launch {
            delay(backoffMs)
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
        _connectionState.value = WsConnectionState.disconnected
    }

    fun destroy() {
        disconnect()
        okHttpClient.dispatcher.executorService.shutdown()
        scope.cancel()
    }
}
