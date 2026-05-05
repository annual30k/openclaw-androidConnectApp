package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.ChatSessionItem
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import com.rethinkingstudio.clawlink.core.domain.NotificationPort
import com.rethinkingstudio.clawlink.core.network.RelayAPIClient
import com.rethinkingstudio.clawlink.core.network.dto.RelayFileTransferItem
import com.rethinkingstudio.clawlink.core.network.transport.RelayWebSocketClient
import com.rethinkingstudio.clawlink.core.network.transport.WsEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val sessions: List<ChatSessionItem> = emptyList(),
    val currentGatewayId: String? = null,
    val currentSessionKey: String = "",
    val isLoading: Boolean = false,
    val isSwitchingSession: Boolean = false,
    val isStreaming: Boolean = false,
    val isStoppingRun: Boolean = false,
    val errorMessage: String? = null,
    val showInvocationProcess: Boolean = true
)

class ChatStore(
    private val apiClient: RelayAPIClient,
    private val wsClient: RelayWebSocketClient,
    private val notificationPort: NotificationPort
) {
    val relayBaseUrl: String get() = apiClient.baseUrl
    val accessToken: String get() = apiClient.accessToken

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var streamingMessageId: String? = null
    private var streamingContent = StringBuilder()
    private val abortRequestIds = mutableSetOf<String>()
    private val locallyStoppedRunIds = mutableSetOf<String>()
    private var ignoreRunlessStoppedEventsUntilMs: Long = 0

    init {
        wsClient.events
            .onEach { event -> handleWsEvent(event) }
            .launchIn(scope)
    }

    private fun handleWsEvent(event: WsEvent) {
        pruneLocallyStoppedRuns()
        when (event.type) {
            "event" -> {
                // Relay server wraps chat events as {type: "event", event: "chat", payload: {...}}
                when (event.event) {
                    "chat" -> handleChatPayload(event.payload)
                    "file" -> handleChatPayload(event.payload)
                    "presence" -> { /* handled by GatewayStore */ }
                    "model_selected" -> { /* model selection update */ }
                }
            }
            "cmd", "res" -> {
                // Command response: {type: "res", ok: true/false, ...}
                val obj = event.payload?.jsonObject
                val responseId = obj?.get("id")?.jsonPrimitive?.content
                if (responseId != null && abortRequestIds.remove(responseId)) {
                    // This is an abort ACK
                    val isSuccess = obj?.get("ok")?.jsonPrimitive?.booleanOrNull != false
                    _state.value = _state.value.copy(isStoppingRun = false)
                    if (!isSuccess && _state.value.isStreaming) {
                        val errorMsg = obj?.get("error")?.jsonObject?.get("message")?.jsonPrimitive?.content
                            ?: obj?.string("message")
                            ?: "停止失败，请稍后重试。"
                        _state.value = _state.value.copy(errorMessage = errorMsg)
                    }
                } else if (obj?.get("ok")?.jsonPrimitive?.booleanOrNull == false) {
                    handleError(obj)
                }
            }
            "error" -> handleError(event.payload)
        }
    }

    private fun handleChatPayload(payload: JsonElement?) {
        val obj = payload as? JsonObject ?: return
        val payloadObj = obj["payload"]?.jsonObject ?: obj

        // Determine phase from payload
        val phase = payloadObj["state"]?.jsonPrimitive?.content
            ?: payloadObj["phase"]?.jsonPrimitive?.content
            ?: ""

        when (phase) {
            "streaming", "delta", "in_progress" -> handleDelta(payloadObj)
            "completed", "complete", "done", "final" -> {
                _state.value = _state.value.copy(isStoppingRun = false)
                handleFinal(payloadObj)
            }
            "error", "failed", "fail", "aborted" -> {
                _state.value = _state.value.copy(isStoppingRun = false)
                handleError(payloadObj)
            }
        }
    }

    private fun handleDelta(payload: JsonElement?) {
        val obj = payload as? JsonObject ?: return
        val content = ChatPayloadText.extract(obj)
        val runId = obj["runId"]?.jsonPrimitive?.content ?: ""
        if (shouldIgnoreLocallyStoppedEvent(runId)) {
            return
        }

        if (streamingMessageId == null) {
            streamingMessageId = UUID.randomUUID().toString()
            streamingContent.clear()
            val msg = ChatMessage(
                id = streamingMessageId!!,
                role = MessageRole.assistant,
                state = MessageState.streaming,
                content = "",
                createdAt = "",
                runId = runId,
                sortTimestamp = System.currentTimeMillis() / 1000.0
            )
            _state.value = _state.value.copy(
                messages = _state.value.messages + msg,
                isStreaming = true
            )
        }

        streamingContent.append(content)
        val messages = _state.value.messages.toMutableList()
        val idx = messages.indexOfFirst { it.id == streamingMessageId }
        if (idx >= 0) {
            val existing = messages[idx]
            // If the message only contains a transient status text, replace it instead of appending
            val updatedContent = if (existing.content.startsWith("正在连接") || existing.content.startsWith("正在同步")) {
                content
            } else {
                existing.content + content
            }
            messages[idx] = existing.copy(
                content = updatedContent,
                runId = runId.ifBlank { existing.runId }
            )
            _state.value = _state.value.copy(messages = messages)
            streamingContent.setLength(0)
            streamingContent.append(updatedContent)
        }
    }

    private fun handleFinal(payload: JsonElement?) {
        val obj = payload as? JsonObject ?: return
        val runId = obj["runId"]?.jsonPrimitive?.content ?: ""
        if (shouldIgnoreLocallyStoppedEvent(runId)) {
            return
        }
        val sessionKey = obj["sessionKey"]?.jsonPrimitive?.content ?: _state.value.currentSessionKey
        val content = ChatPayloadText.extract(obj).ifBlank { streamingContent.toString() }
        val contentBlocks = parseContentBlocks(
            obj["contentBlocks"] as? JsonArray
                ?: ((obj["message"] as? JsonObject)?.get("content") as? JsonArray)
        )
        val role = try {
            MessageRole.valueOf(
                obj.string("role")
                    ?: ((obj["message"] as? JsonObject)?.string("role"))
                    ?: "assistant"
            )
        } catch (_: Exception) {
            MessageRole.assistant
        }

        if (streamingMessageId != null) {
            val messages = _state.value.messages.toMutableList()
            val idx = messages.indexOfFirst { it.id == streamingMessageId }
            if (idx >= 0) {
                messages[idx] = messages[idx].copy(
                    content = content,
                    contentBlocks = contentBlocks,
                    state = MessageState.completed
                )
                _state.value = _state.value.copy(messages = messages, isStreaming = false)
            }
        } else {
            val msg = ChatMessage(
                id = UUID.randomUUID().toString(),
                role = role,
                state = MessageState.completed,
                content = content,
                contentBlocks = contentBlocks,
                createdAt = "",
                runId = runId,
                sortTimestamp = System.currentTimeMillis() / 1000.0
            )
            val fileIds = contentBlocks.mapNotNull { it.fileId?.trim()?.takeIf { id -> id.isNotEmpty() } }
            if (fileIds.isNotEmpty()) {
                val messages = _state.value.messages.toMutableList()
                val existingIndex = messages.indexOfFirst { existing ->
                    existing.fileContentBlocks.any { it.fileId in fileIds }
                }
                if (existingIndex >= 0) {
                    messages[existingIndex] = msg.copy(
                        id = messages[existingIndex].id,
                        sortTimestamp = messages[existingIndex].sortTimestamp ?: msg.sortTimestamp
                    )
                    _state.value = _state.value.copy(messages = messages, isStreaming = false)
                    streamingContent.clear()
                    streamingMessageId = null
                    return
                }
            }
            _state.value = _state.value.copy(
                messages = _state.value.messages + msg,
                isStreaming = false
            )
        }

        val preview = buildNotificationPreview(content, contentBlocks)
        if (sessionKey.isNotBlank() && preview.isNotBlank()) {
            notificationPort.showReplyNotification(
                sessionKey = sessionKey,
                title = "PocketClaw reply",
                body = preview
            )
        }

        streamingMessageId = null
        streamingContent.clear()
    }

    private fun handleError(payload: JsonElement?) {
        val obj = payload as? JsonObject
        val runId = obj?.string("runId", "run_id")
        if (shouldIgnoreLocallyStoppedEvent(runId.orEmpty())) {
            return
        }
        val errorObj = obj?.get("error") as? JsonObject
        val msg = errorObj?.string("message")
            ?: obj?.string("message", "errorMessage")
            ?: "Unknown error"
        _state.value = _state.value.copy(errorMessage = msg, isStreaming = false)
    }

    private fun parseContentBlocks(array: kotlinx.serialization.json.JsonArray?): List<RelayChatContentBlock> {
        if (array == null) return emptyList()
        return array.mapNotNull { element ->
            try {
                val obj = element.jsonObject
                val type = obj["type"]?.jsonPrimitive?.content ?: return@mapNotNull null
                RelayChatContentBlock(
                    type = type,
                    text = obj["text"]?.jsonPrimitive?.content,
                    name = obj["name"]?.jsonPrimitive?.content ?: obj["tool_name"]?.jsonPrimitive?.content,
                    fileId = obj.string("fileId", "file_id"),
                    fileName = obj.string("fileName", "file_name", "name"),
                    mimeType = obj.string("mimeType", "mime_type"),
                    sizeBytes = obj.int("sizeBytes", "size_bytes"),
                    durationMs = obj.int("durationMs", "duration_ms"),
                    imageWidth = obj.int("imageWidth", "image_width"),
                    imageHeight = obj.int("imageHeight", "image_height"),
                    downloadUrl = obj.string("downloadUrl", "download_url"),
                    downloadPath = obj.string("downloadPath", "download_path"),
                    thumbnailUrl = obj.string("thumbnailUrl", "thumbnail_url"),
                    expiresAt = obj.string("expiresAt", "expires_at"),
                    senderDisplayName = obj.string("senderDisplayName", "sender_display_name"),
                    transcript = obj.string("transcript"),
                    gatewayId = obj.string("gatewayId", "gateway_id"),
                    sessionKey = obj.string("sessionKey", "session_key"),
                    arguments = obj["arguments"]?.let { com.rethinkingstudio.clawlink.core.models.chat.RelayJSONValue.fromJsonElement(it) },
                    args = obj["args"]?.let { com.rethinkingstudio.clawlink.core.models.chat.RelayJSONValue.fromJsonElement(it) },
                    result = obj["result"]?.let { com.rethinkingstudio.clawlink.core.models.chat.RelayJSONValue.fromJsonElement(it) },
                    partialResult = obj["partialResult"]?.let { com.rethinkingstudio.clawlink.core.models.chat.RelayJSONValue.fromJsonElement(it) },
                    content = obj["content"]?.let { com.rethinkingstudio.clawlink.core.models.chat.RelayJSONValue.fromJsonElement(it) },
                    output = obj["output"]?.let { com.rethinkingstudio.clawlink.core.models.chat.RelayJSONValue.fromJsonElement(it) },
                    error = obj["error"]?.let { com.rethinkingstudio.clawlink.core.models.chat.RelayJSONValue.fromJsonElement(it) },
                    toolCallId = obj["tool_call_id"]?.jsonPrimitive?.content ?: obj["toolCallId"]?.jsonPrimitive?.content,
                    toolUseId = obj["tool_use_id"]?.jsonPrimitive?.content ?: obj["toolUseId"]?.jsonPrimitive?.content,
                    toolName = obj.string("toolName", "tool_name"),
                    status = obj["status"]?.jsonPrimitive?.content,
                    isError = obj["is_error"]?.jsonPrimitive?.booleanOrNull ?: obj["isError"]?.jsonPrimitive?.booleanOrNull
                )
            } catch (_: Exception) { null }
        }
    }

    private fun JsonObject.string(vararg keys: String): String? {
        return keys.firstNotNullOfOrNull { key ->
            (this[key] as? kotlinx.serialization.json.JsonPrimitive)
                ?.contentOrNull
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }
    }

    private fun JsonObject.int(vararg keys: String): Int? {
        return keys.firstNotNullOfOrNull { key ->
            val primitive = this[key] as? kotlinx.serialization.json.JsonPrimitive ?: return@firstNotNullOfOrNull null
            primitive.intOrNull ?: primitive.longOrNull?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt()
        }
    }

    fun sendMessage(
        content: String,
        gatewayId: String,
        attachmentIds: List<String> = emptyList(),
        attachmentBlocks: List<RelayChatContentBlock> = emptyList()
    ) {
        val sessionKey = _state.value.currentSessionKey
        if (sessionKey.isBlank()) return

        val clientRunId = UUID.randomUUID().toString()
        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.user,
            state = MessageState.completed,
            content = content.trim().takeIf { it.isNotEmpty() && it != " " } ?: "",
            contentBlocks = attachmentBlocks,
            createdAt = "",
            runId = "local-user-$clientRunId",
            sortTimestamp = System.currentTimeMillis() / 1000.0
        )
        
        val assistantMsgId = UUID.randomUUID().toString()
        val assistantMsg = ChatMessage(
            id = assistantMsgId,
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "正在连接...",
            createdAt = "",
            runId = clientRunId,
            sortTimestamp = System.currentTimeMillis() / 1000.0 + 0.001
        )
        
        streamingMessageId = assistantMsgId
        streamingContent.setLength(0)
        streamingContent.append(assistantMsg.content)

        _state.value = _state.value.copy(
            messages = _state.value.messages + userMsg + assistantMsg,
            isStreaming = true
        )
        wsClient.sendChatMessage(gatewayId, sessionKey, content, clientRunId)
    }

    suspend fun uploadAttachment(
        gatewayId: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        sha256: String
    ): RelayFileTransferItem {
        val sessionKey = _state.value.currentSessionKey
        if (sessionKey.isBlank()) throw IllegalStateException("No active chat session")
        val init = apiClient.initMobileFileUpload(
            gatewayId = gatewayId,
            sessionKey = sessionKey,
            fileName = fileName,
            mimeType = mimeType,
            sizeBytes = bytes.size.toLong(),
            sha256 = sha256
        )
        val chunkSize = init.chunkSize.coerceAtLeast(1)
        var offset = 0
        var chunkIndex = 0
        while (offset < bytes.size) {
            val end = minOf(offset + chunkSize, bytes.size)
            apiClient.uploadMobileFileChunk(init.uploadId, chunkIndex, bytes.copyOfRange(offset, end))
            offset = end
            chunkIndex += 1
        }
        return apiClient.completeMobileFileUpload(init.uploadId, chunkIndex).payload
    }

    fun sendCommand(gatewayId: String, command: String) {
        val sessionKey = _state.value.currentSessionKey
        if (sessionKey.isNotBlank()) {
            wsClient.sendCommand(gatewayId, sessionKey, command)
        }
    }

    fun abortRun() {
        if (!_state.value.isStreaming) return
        if (_state.value.isStoppingRun) return

        val gatewayId = _state.value.currentGatewayId
        val sessionKey = _state.value.currentSessionKey

        if (gatewayId.isNullOrBlank()) {
            _state.value = _state.value.copy(errorMessage = "网关未选择，请重新配对")
            return
        }
        if (sessionKey.isBlank()) {
            _state.value = _state.value.copy(errorMessage = "会话已失效，请重新配对")
            return
        }

        val activeRunId = _state.value.messages.lastOrNull { it.state == MessageState.streaming }?.runId

        val requestId = UUID.randomUUID().toString()
        abortRequestIds.add(requestId)
        _state.value = _state.value.copy(isStoppingRun = true)

        android.util.Log.d("ChatStore", "Stopping run: $activeRunId for gateway: $gatewayId, session: $sessionKey")

        wsClient.abortChatRun(gatewayId, sessionKey, activeRunId, requestId)
        completeCurrentStreamingMessageLocally(activeRunId)
    }

    private fun completeCurrentStreamingMessageLocally(runId: String?) {
        val messages = _state.value.messages.toMutableList()
        val index = messages.indexOfLast { it.state == MessageState.streaming }
        if (index >= 0) {
            val existing = messages[index]
            val resolvedRunId = runId?.takeIf { it.isNotBlank() } ?: existing.runId
            if (resolvedRunId.isNotBlank()) {
                locallyStoppedRunIds.add(resolvedRunId)
            }
            val trimmed = existing.content.trim()
            if (trimmed.isBlank() || trimmed.startsWith("正在连接") || trimmed.startsWith("正在同步")) {
                messages.removeAt(index)
            } else {
                messages[index] = existing.copy(state = MessageState.completed, runId = resolvedRunId)
            }
        } else if (!runId.isNullOrBlank()) {
            locallyStoppedRunIds.add(runId)
        }

        ignoreRunlessStoppedEventsUntilMs = System.currentTimeMillis() + stoppedRunlessEventIgnoreWindowMs
        streamingMessageId = null
        streamingContent.clear()
        _state.value = _state.value.copy(
            messages = messages,
            isStreaming = false,
            isStoppingRun = false
        )
    }

    suspend fun loadHistory(gatewayId: String, sessionKey: String, limit: Int = 50) {
        val normalizedGatewayId = gatewayId.trim()
        val normalizedSessionKey = sessionKey.trim().ifBlank { defaultSessionKey }
        if (normalizedGatewayId.isBlank()) {
            _state.value = _state.value.copy(isLoading = false, isSwitchingSession = false)
            return
        }
        _state.value = _state.value.copy(
            currentGatewayId = normalizedGatewayId,
            currentSessionKey = normalizedSessionKey,
            isLoading = true
        )
        try {
            val items = apiClient.fetchChatHistory(normalizedGatewayId, normalizedSessionKey, limit)
            val messages = items.map { item ->
                ChatMessage(
                    id = item.id,
                    role = try { MessageRole.valueOf(item.role) } catch (_: Exception) { MessageRole.system },
                    content = extractContent(item),
                    contentBlocks = item.contentBlocks ?: emptyList(),
                    createdAt = item.createdAt ?: "",
                    runId = "",
                    sortTimestamp = null
                )
            }
            val current = _state.value
            if (current.currentGatewayId == normalizedGatewayId && current.currentSessionKey == normalizedSessionKey) {
                _state.value = current.copy(messages = messages, isLoading = false, isSwitchingSession = false)
            }
        } catch (e: CancellationException) {
            _state.value = _state.value.copy(isLoading = false, isSwitchingSession = false)
            throw e
        } catch (e: Exception) {
            _state.value = _state.value.copy(isLoading = false, isSwitchingSession = false, errorMessage = e.message)
        }
    }

    fun connectWebSocket() {
        val url = apiClient.baseUrl
        val token = apiClient.accessToken
        if (url.isNotBlank() && token.isNotBlank()) {
            wsClient.connect(url, token)
        }
    }

    fun suspendWebSocket() {
        wsClient.suspendConnection()
    }

    fun resumeWebSocket() {
        wsClient.resumeConnection()
    }

    suspend fun loadSessions(gatewayId: String) {
        val normalizedGatewayId = gatewayId.trim()
        if (normalizedGatewayId.isBlank()) {
            _state.value = _state.value.copy(isSwitchingSession = false)
            return
        }
        try {
            val sessions = apiClient.fetchChatSessions(normalizedGatewayId)
            val currentState = _state.value
            val current = currentState.currentSessionKey
            val isNewGateway = currentState.currentGatewayId != normalizedGatewayId
            val selected = when {
                !isNewGateway && current.isNotBlank() && !current.startsWith("session_") && sessions.any { it.sessionKey == current } -> current
                !isNewGateway && current.isNotBlank() && sessions.isEmpty() -> current
                else -> (sessions.find { it.sessionKey == defaultSessionKey } ?: sessions.firstOrNull())?.sessionKey ?: defaultSessionKey
            }
            
            _state.value = currentState.copy(
                sessions = sessions,
                currentGatewayId = normalizedGatewayId,
                currentSessionKey = selected,
                messages = if (isNewGateway || selected != current) emptyList() else currentState.messages,
                isSwitchingSession = isNewGateway || selected != current,
                errorMessage = null
            )
        } catch (e: CancellationException) {
            val currentState = _state.value
            _state.value = currentState.copy(
                currentGatewayId = normalizedGatewayId,
                currentSessionKey = currentState.currentSessionKey.ifBlank { defaultSessionKey },
                isSwitchingSession = false
            )
            throw e
        } catch (e: Exception) {
            android.util.Log.w("ChatStore", "Failed to load chat sessions for $normalizedGatewayId", e)
            val currentState = _state.value
            val selected = currentState.currentSessionKey.ifBlank { defaultSessionKey }
            _state.value = currentState.copy(
                currentGatewayId = normalizedGatewayId,
                currentSessionKey = selected,
                isSwitchingSession = false,
                errorMessage = e.message
            )
        }
    }

    fun beginGatewaySwitch(gatewayId: String) {
        val normalizedGatewayId = gatewayId.trim().takeIf { it.isNotEmpty() } ?: return
        val current = _state.value
        if (current.currentGatewayId == normalizedGatewayId) return
        _state.value = current.copy(
            currentGatewayId = normalizedGatewayId,
            currentSessionKey = defaultSessionKey,
            sessions = listOf(ChatSessionItem(sessionKey = defaultSessionKey, lastActivityAt = null)),
            messages = emptyList(),
            isSwitchingSession = true,
            isStreaming = false,
            isStoppingRun = false,
            errorMessage = null
        )
        streamingMessageId = null
        streamingContent.clear()
        abortRequestIds.clear()
        locallyStoppedRunIds.clear()
        ignoreRunlessStoppedEventsUntilMs = 0
    }

    fun selectSession(sessionKey: String) {
        val normalized = sessionKey.trim().ifBlank { "main" }
        if (_state.value.currentSessionKey == normalized) return
        _state.value = _state.value.copy(
            currentSessionKey = normalized,
            messages = emptyList(),
            isSwitchingSession = true,
            errorMessage = null
        )
    }

    fun newSession() {
        val key = "session_${System.currentTimeMillis()}"
        _state.value = _state.value.copy(currentSessionKey = key, messages = emptyList())
    }

    fun toggleShowInvocation() {
        _state.value = _state.value.copy(showInvocationProcess = !_state.value.showInvocationProcess)
    }

    fun clearMessages() {
        _state.value = _state.value.copy(messages = emptyList(), isSwitchingSession = false, isStoppingRun = false, isStreaming = false)
        streamingMessageId = null
        streamingContent.clear()
        abortRequestIds.clear()
        locallyStoppedRunIds.clear()
        ignoreRunlessStoppedEventsUntilMs = 0
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    private fun extractContent(item: com.rethinkingstudio.clawlink.core.network.dto.ChatHistoryItem): String {
        val content = item.content ?: return ""
        return when {
            content is kotlinx.serialization.json.JsonPrimitive && content.isString -> content.content
            else -> try {
                content.jsonObject["text"]?.jsonPrimitive?.content ?: ""
            } catch (_: Exception) { "" }
        }
    }

    private fun buildNotificationPreview(content: String, contentBlocks: List<RelayChatContentBlock>): String {
        val plainText = content.trim().ifBlank {
            contentBlocks.firstNotNullOfOrNull { block ->
                block.text?.trim()?.takeIf { it.isNotBlank() }
                    ?: block.transcript?.trim()?.takeIf { it.isNotBlank() }
            } ?: ""
        }
        if (plainText.isBlank()) return ""
        return plainText.replace(Regex("\\s+"), " ").take(140)
    }

    private fun shouldIgnoreLocallyStoppedEvent(runId: String): Boolean {
        pruneLocallyStoppedRuns()
        val normalizedRunId = runId.trim()
        if (normalizedRunId.isNotEmpty() && locallyStoppedRunIds.contains(normalizedRunId)) {
            return true
        }
        return normalizedRunId.isEmpty()
            && streamingMessageId == null
            && System.currentTimeMillis() < ignoreRunlessStoppedEventsUntilMs
    }

    private fun pruneLocallyStoppedRuns() {
        if (System.currentTimeMillis() >= ignoreRunlessStoppedEventsUntilMs) {
            ignoreRunlessStoppedEventsUntilMs = 0
        }
        if (locallyStoppedRunIds.size > maxLocallyStoppedRunIds) {
            locallyStoppedRunIds.clear()
        }
    }

    private companion object {
        const val defaultSessionKey = "main"
        const val stoppedRunlessEventIgnoreWindowMs = 15_000L
        const val maxLocallyStoppedRunIds = 64
    }
}

internal object ChatPayloadText {
    fun extract(obj: JsonObject): String {
        obj.stringValue("content", "text", "delta")?.let { return it }
        val data = obj["data"] as? JsonObject
        data?.stringValue("content", "text", "delta")?.let { return it }

        val message = obj["message"] as? JsonObject
        message?.stringValue("text")?.let { return it }
        val messageContent = message?.get("content")
        if (messageContent is JsonArray) {
            extractTextFromContentArray(messageContent)?.let { return it }
        }
        if (messageContent is kotlinx.serialization.json.JsonPrimitive) {
            messageContent.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        }

        val content = obj["content"]
        if (content is JsonArray) {
            extractTextFromContentArray(content)?.let { return it }
        }
        return ""
    }

    private fun extractTextFromContentArray(array: JsonArray): String? {
        return array.mapNotNull { element ->
            val block = element as? JsonObject ?: return@mapNotNull null
            val type = block["type"]?.jsonPrimitive?.contentOrNull
            if (type != null && type != "text" && type != "output_text") {
                return@mapNotNull null
            }
            block.rawStringValue("text", "content")
        }
            .joinToString("")
            .trim()
            .takeIf { it.isNotEmpty() }
    }

    private fun JsonObject.stringValue(vararg keys: String): String? {
        return keys.firstNotNullOfOrNull { key ->
            (this[key] as? kotlinx.serialization.json.JsonPrimitive)
                ?.contentOrNull
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }
    }

    private fun JsonObject.rawStringValue(vararg keys: String): String? {
        return keys.firstNotNullOfOrNull { key ->
            (this[key] as? kotlinx.serialization.json.JsonPrimitive)
                ?.contentOrNull
                ?.takeIf { it.isNotEmpty() }
        }
    }
}
