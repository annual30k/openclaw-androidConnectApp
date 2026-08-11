package com.rethinkingstudio.clawlink.core.network.transport

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
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

private const val RELAY_WS_LOG_TAG = "RelayWS"

data class WsEvent(
    val type: String,
    val event: String? = null,
    val payload: JsonElement? = null,
    val id: String? = null
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

    private val eventBroadcaster = ReliableWsEventBroadcaster<WsEvent>()
    val events: Flow<WsEvent> = eventBroadcaster.events

    private val _connectionState = MutableStateFlow(WsConnectionState.disconnected)
    val connectionState: StateFlow<WsConnectionState> = _connectionState.asStateFlow()

    private var baseUrl: String = ""
    private var accessToken: String = ""
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5
    private val pendingOutbound = PendingOutboundQueue(capacity = 32)
    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    fun connect(url: String, token: String) {
        if (shouldIgnoreRelayWsConnectRequest(
                currentBaseUrl = baseUrl,
                currentAccessToken = accessToken,
                nextBaseUrl = url,
                nextAccessToken = token,
                isConnected = isConnected.get(),
                connectionState = _connectionState.value
            )
        ) {
            return
        }
        cancelReconnect()
        reconnectEnabled.set(true)
        baseUrl = url
        accessToken = token
        _connectionState.value = WsConnectionState.connecting
        val wsUrl = url.replace("http://", "ws://").replace("https://", "wss://") + "/mobile/ws?accessToken=$token"
        val request = Request.Builder().url(wsUrl).build()
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (this@RelayWebSocketClient.webSocket !== webSocket) {
                    webSocket.close(1000, "Stale connection")
                    return
                }
                isConnected.set(true)
                reconnectAttempts = 0
                cancelReconnect()
                _connectionState.value = WsConnectionState.connected
                flushPendingOutbound(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (this@RelayWebSocketClient.webSocket !== webSocket) return
                try {
                    val element = json.parseToJsonElement(text)
                    val obj = element as? JsonObject
                    val type = obj?.get("type")?.jsonPrimitive?.content ?: "unknown"
                    val event = obj?.get("event")?.jsonPrimitive?.content
                    val id = obj?.get("id")?.jsonPrimitive?.content
                    eventBroadcaster.publish(WsEvent(type, event, element, id))
                } catch (_: Exception) {
                    eventBroadcaster.publish(WsEvent("error", null, null))
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (this@RelayWebSocketClient.webSocket !== webSocket) return
                isConnected.set(false)
                this@RelayWebSocketClient.webSocket = null
                handleSocketTermination()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (this@RelayWebSocketClient.webSocket !== webSocket) return
                isConnected.set(false)
                this@RelayWebSocketClient.webSocket = null
                eventBroadcaster.publish(WsEvent("error", null, null))
                handleSocketTermination()
            }
        })
    }

    fun sendChatMessage(
        gatewayId: String,
        sessionKey: String,
        content: String,
        attachments: List<RelayChatSendAttachmentPayload> = emptyList(),
        idempotencyKey: String? = null,
        requestId: String = UUID.randomUUID().toString()
    ): String {
        val payload = buildChatSendPayload(
            gatewayId = gatewayId,
            sessionKey = sessionKey,
            content = content,
            attachments = attachments,
            idempotencyKey = idempotencyKey ?: requestId,
            requestId = requestId
        )
        send(payload.toString(), requestId)
        return requestId
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
        send(payload.toString(), requestId)
    }

    fun sendVoiceMessage(
        gatewayId: String,
        sessionKey: String,
        audio: VoiceSendAudioPayload,
        message: String? = null,
        languageHint: String? = null,
        idempotencyKey: String? = null,
        requestId: String = UUID.randomUUID().toString()
    ): String {
        val payload = buildChatVoiceSendPayload(
            gatewayId = gatewayId,
            sessionKey = sessionKey,
            requestId = requestId,
            idempotencyKey = idempotencyKey ?: requestId,
            audio = audio,
            message = message,
            languageHint = languageHint
        )
        send(payload.toString(), requestId)
        return requestId
    }

    fun sendCommand(
        gatewayId: String,
        sessionKey: String,
        command: String,
        requestId: String = UUID.randomUUID().toString()
    ): String {
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
        send(payload.toString(), requestId)
        return requestId
    }

    fun resetSession(
        gatewayId: String,
        sessionKey: String,
        requestId: String = UUID.randomUUID().toString()
    ): String {
        val payload = buildSessionResetPayload(
            gatewayId = gatewayId,
            sessionKey = sessionKey,
            requestId = requestId
        )
        send(payload.toString(), requestId)
        return requestId
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
        send(payload.toString(), requestId)
    }

    fun sendPing() {
        send("""{"type":"ping"}""")
    }

    private fun send(text: String, requestId: String? = null) {
        logDebug("WS SEND: $text")
        val offerResult = pendingOutbound.offer(PendingOutboundMessage(text, requestId))
        if (offerResult == PendingOutboundOfferResult.rejectedFull) {
            logWarning("WS outbound queue full; rejecting request explicitly")
            publishOutboundFailure(requestId, "outbound_queue_full", "Too many messages are waiting for Relay connection")
            return
        }
        val socket = webSocket
        if (socket != null && isConnected.get()) {
            flushPendingOutbound(socket)
            return
        }

        logWarning("WS not connected; queued outbound message")
        if (
            baseUrl.isNotBlank()
            && accessToken.isNotBlank()
            && shouldStartRelayWsReconnectNow(_connectionState.value)
        ) {
            reconnectAttempts = 0
            reconnectEnabled.set(true)
            connect(baseUrl, accessToken)
        }
    }

    private fun flushPendingOutbound(socket: WebSocket) {
        val queuedCount = pendingOutbound.size()
        if (queuedCount > 0) {
            logDebug("WS flush queued outbound count=$queuedCount")
        }
        val flushed = pendingOutbound.flush { message -> socket.send(message.text) }
        if (!flushed) {
            // OkHttp 返回 false 表示消息没有进入其发送队列；保留本地队首并强制重连，
            // 不能把一次本地调用误当成已经送达 Relay。
            logWarning("WS rejected outbound message; preserving queue for reconnect")
            isConnected.set(false)
            socket.cancel()
        }
    }

    private fun publishOutboundFailure(requestId: String?, code: String, message: String) {
        if (requestId.isNullOrBlank()) return
        val envelope = buildJsonObject {
            put("type", JsonPrimitive("res"))
            put("id", JsonPrimitive(requestId))
            put("runId", JsonPrimitive(requestId))
            put("ok", JsonPrimitive(false))
            put("error", buildJsonObject {
                put("code", JsonPrimitive(code))
                put("message", JsonPrimitive(message))
            })
        }
        eventBroadcaster.publish(WsEvent("res", payload = envelope, id = requestId))
    }

    private fun logDebug(message: String) {
        runCatching {
            if (android.util.Log.isLoggable(RELAY_WS_LOG_TAG, android.util.Log.DEBUG)) {
                android.util.Log.d(RELAY_WS_LOG_TAG, message)
            }
        }
    }

    private fun logWarning(message: String) {
        runCatching { android.util.Log.w(RELAY_WS_LOG_TAG, message) }
    }

    private val reconnectDelays = listOf(1_000L, 2_000L, 4_000L, 8_000L, 15_000L)

    private fun handleSocketTermination() {
        if (reconnectEnabled.get()) {
            // 可恢复的网络断开必须直接进入 reconnecting，不能先发布 offline。
            // GatewayStore 会把该状态展示给聊天页；中间 offline 会被误当成链路故障弹窗。
            attemptReconnect()
        } else {
            _connectionState.value = WsConnectionState.disconnected
        }
    }

    private fun attemptReconnect() {
        if (!reconnectEnabled.get()) return
        if (reconnectAttempts >= maxReconnectAttempts) {
            _connectionState.value = WsConnectionState.disconnected
            pendingOutbound.drain().forEach { message ->
                publishOutboundFailure(
                    message.requestId,
                    "relay_reconnect_exhausted",
                    "Unable to reconnect to Relay; send the message again after the connection recovers"
                )
            }
            return
        }
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
        // 主动退出/切换账号也不能让已经被本地 API 接受的请求静默消失。
        pendingOutbound.drain().forEach { message ->
            publishOutboundFailure(
                message.requestId,
                "relay_client_disconnected",
                "Relay connection closed before the queued request was sent"
            )
        }
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
        eventBroadcaster.close()
        okHttpClient.dispatcher.executorService.shutdown()
        scope.cancel()
    }
}

internal fun shouldIgnoreRelayWsConnectRequest(
    currentBaseUrl: String,
    currentAccessToken: String,
    nextBaseUrl: String,
    nextAccessToken: String,
    isConnected: Boolean,
    connectionState: WsConnectionState
): Boolean {
    val sameEndpoint = currentBaseUrl == nextBaseUrl && currentAccessToken == nextAccessToken
    if (!sameEndpoint) return false
    return isConnected || connectionState == WsConnectionState.connecting
}

internal fun shouldStartRelayWsReconnectNow(connectionState: WsConnectionState): Boolean {
    return connectionState != WsConnectionState.connecting
}
