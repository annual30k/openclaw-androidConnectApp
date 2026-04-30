package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.ChatSessionItem
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import com.rethinkingstudio.clawlink.core.domain.NotificationPort
import com.rethinkingstudio.clawlink.core.network.RelayAPIClient
import com.rethinkingstudio.clawlink.core.network.transport.RelayWebSocketClient
import com.rethinkingstudio.clawlink.core.network.transport.WsEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val sessions: List<ChatSessionItem> = emptyList(),
    val currentSessionKey: String = "",
    val isLoading: Boolean = false,
    val isStreaming: Boolean = false,
    val errorMessage: String? = null,
    val showInvocationProcess: Boolean = true
)

class ChatStore(
    private val apiClient: RelayAPIClient,
    private val wsClient: RelayWebSocketClient,
    private val notificationPort: NotificationPort
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var streamingMessageId: String? = null
    private var streamingContent = StringBuilder()

    init {
        wsClient.events
            .onEach { event -> handleWsEvent(event) }
            .launchIn(scope)
    }

    private fun handleWsEvent(event: WsEvent) {
        when (event.type) {
            "delta" -> handleDelta(event.payload)
            "final" -> handleFinal(event.payload)
            "status" -> { /* gateway status handled by GatewayStore */ }
            "usage" -> { /* token usage */ }
            "error" -> handleError(event.payload)
            "subscribed" -> { /* connection confirmed */ }
        }
    }

    private fun handleDelta(payload: JsonElement?) {
        val obj = payload as? JsonObject ?: return
        val content = obj["content"]?.jsonPrimitive?.content ?: ""
        val runId = obj["runId"]?.jsonPrimitive?.content ?: ""

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
            messages[idx] = messages[idx].copy(content = streamingContent.toString())
            _state.value = _state.value.copy(messages = messages)
        }
    }

    private fun handleFinal(payload: JsonElement?) {
        val obj = payload as? JsonObject ?: return
        val runId = obj["runId"]?.jsonPrimitive?.content ?: ""
        val sessionKey = obj["sessionKey"]?.jsonPrimitive?.content ?: _state.value.currentSessionKey
        val content = obj["content"]?.jsonPrimitive?.content ?: streamingContent.toString()
        val contentBlocks = parseContentBlocks(obj["contentBlocks"]?.jsonArray)

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
                role = MessageRole.assistant,
                state = MessageState.completed,
                content = content,
                contentBlocks = contentBlocks,
                createdAt = "",
                runId = runId,
                sortTimestamp = System.currentTimeMillis() / 1000.0
            )
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
        val msg = obj?.get("message")?.jsonPrimitive?.content ?: "Unknown error"
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
                    fileId = obj["fileId"]?.jsonPrimitive?.content,
                    fileName = obj["fileName"]?.jsonPrimitive?.content,
                    mimeType = obj["mimeType"]?.jsonPrimitive?.content,
                    toolCallId = obj["tool_call_id"]?.jsonPrimitive?.content ?: obj["toolCallId"]?.jsonPrimitive?.content,
                    toolUseId = obj["tool_use_id"]?.jsonPrimitive?.content ?: obj["toolUseId"]?.jsonPrimitive?.content,
                    status = obj["status"]?.jsonPrimitive?.content,
                    isError = obj["is_error"]?.jsonPrimitive?.booleanOrNull ?: obj["isError"]?.jsonPrimitive?.booleanOrNull
                )
            } catch (_: Exception) { null }
        }
    }

    fun sendMessage(content: String, attachmentIds: List<String> = emptyList()) {
        val sessionKey = _state.value.currentSessionKey
        if (sessionKey.isBlank()) return

        val msg = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.user,
            state = MessageState.completed,
            content = content,
            createdAt = "",
            runId = "",
            sortTimestamp = System.currentTimeMillis() / 1000.0
        )
        _state.value = _state.value.copy(messages = _state.value.messages + msg)
        wsClient.sendChatMessage(sessionKey, content, attachmentIds)
    }

    suspend fun uploadAttachment(
        gatewayId: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        sha256: String
    ): String {
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
        apiClient.completeMobileFileUpload(init.uploadId, chunkIndex)
        return init.fileId
    }

    fun sendCommand(command: String) {
        val sessionKey = _state.value.currentSessionKey
        if (sessionKey.isNotBlank()) {
            wsClient.sendCommand(sessionKey, command)
        }
    }

    suspend fun loadHistory(gatewayId: String, sessionKey: String, limit: Int = 50) {
        _state.value = _state.value.copy(isLoading = true)
        try {
            val items = apiClient.fetchChatHistory(gatewayId, sessionKey, limit)
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
            _state.value = _state.value.copy(messages = messages, isLoading = false)
        } catch (e: Exception) {
            _state.value = _state.value.copy(isLoading = false, errorMessage = e.message)
        }
    }

    suspend fun loadSessions(gatewayId: String) {
        try {
            val sessions = apiClient.fetchChatSessions(gatewayId)
            _state.value = _state.value.copy(sessions = sessions)
        } catch (_: Exception) {}
    }

    fun selectSession(sessionKey: String) {
        _state.value = _state.value.copy(currentSessionKey = sessionKey, messages = emptyList())
    }

    fun newSession() {
        val key = "session_${System.currentTimeMillis()}"
        _state.value = _state.value.copy(currentSessionKey = key, messages = emptyList())
    }

    fun toggleShowInvocation() {
        _state.value = _state.value.copy(showInvocationProcess = !_state.value.showInvocationProcess)
    }

    fun clearMessages() {
        _state.value = _state.value.copy(messages = emptyList())
        streamingMessageId = null
        streamingContent.clear()
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
}
