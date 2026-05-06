package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.ChatSessionItem
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import com.rethinkingstudio.clawlink.core.models.chat.AttachmentUploadPhase
import com.rethinkingstudio.clawlink.core.models.chat.ComposerAttachmentDraft
import com.rethinkingstudio.clawlink.core.models.chat.ComposerAttachmentUploadItem
import com.rethinkingstudio.clawlink.core.domain.NotificationPort
import com.rethinkingstudio.clawlink.core.network.RelayAPIClient
import com.rethinkingstudio.clawlink.core.network.dto.RelayFileTransferItem
import com.rethinkingstudio.clawlink.core.network.transport.RelayWebSocketClient
import com.rethinkingstudio.clawlink.core.network.transport.WsEvent
import com.rethinkingstudio.clawlink.core.state.chat.RemoteImageCache
import com.rethinkingstudio.clawlink.core.state.chat.RemoteImageSizeCache
import com.rethinkingstudio.clawlink.core.state.chat.RemoteAttachmentCache
import android.graphics.BitmapFactory
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
import java.io.File
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
                    sameFileMessage(existing, msg)
                }
                if (existingIndex >= 0) {
                    val mergedMessage = mergeCompletedFileMessage(
                        existing = messages[existingIndex],
                        completed = msg.copy(
                            id = messages[existingIndex].id,
                            sortTimestamp = messages[existingIndex].sortTimestamp ?: msg.sortTimestamp
                        )
                    )
                    messages[existingIndex] = mergedMessage
                    _state.value = _state.value.copy(messages = messages, isStreaming = false)
                    removeDuplicateFileMessages(mergedMessage)
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

    fun beginComposerAttachmentUploadMessages(
        attachments: List<ComposerAttachmentDraft>,
        gatewayId: String,
        sessionKey: String,
        senderDisplayName: String?,
        messageSortBaseTimestamp: Double
    ) {
        if (attachments.isEmpty()) return

        val messages = _state.value.messages.toMutableList()
        attachments.forEachIndexed { index, attachment ->
            val sortTimestamp = messageSortBaseTimestamp + (index * 0.001)
            val statusText = ComposerAttachmentUploadItem(
                gatewayId = gatewayId,
                attachment = attachment,
                progress = 0.0,
                phase = AttachmentUploadPhase.uploading,
                failureMessage = null
            ).statusText
            val message = ChatMessage(
                id = attachment.id,
                role = MessageRole.user,
                state = MessageState.streaming,
                content = sanitizeChatDisplayText(attachment.fileName),
                contentBlocks = listOf(
                    makeComposerAttachmentUploadContentBlock(
                        attachment = attachment,
                        gatewayId = gatewayId,
                        sessionKey = sessionKey,
                        senderDisplayName = senderDisplayName,
                        statusText = statusText,
                        downloadUrlString = attachment.fileUri
                    )
                ),
                createdAt = java.time.Instant.ofEpochMilli((sortTimestamp * 1000).toLong()).toString(),
                runId = composerAttachmentUploadRunId(attachment),
                sortTimestamp = sortTimestamp
            )

            val existingIndex = messages.indexOfFirst { it.id == attachment.id }
            if (existingIndex >= 0) {
                messages[existingIndex] = message
            } else {
                messages.add(message)
            }
        }

        _state.value = _state.value.copy(messages = messages)
    }

    fun updateComposerAttachmentUploadMessage(
        attachment: ComposerAttachmentDraft,
        gatewayId: String,
        sessionKey: String,
        progress: Double,
        phase: AttachmentUploadPhase,
        failureMessage: String? = null,
        senderDisplayName: String? = null
    ) {
        val messages = _state.value.messages.toMutableList()
        val index = messages.indexOfFirst { it.id == attachment.id }
        if (index < 0) return

        val existing = messages[index]
        if (existing.transferContentBlocks().any { !it.fileId.isNullOrBlank() }) {
            return
        }
        val uploadPlaceholder = existing.copy(
            contentBlocks = listOf(
                makeComposerAttachmentUploadContentBlock(
                    attachment = attachment,
                    gatewayId = gatewayId,
                    sessionKey = sessionKey,
                    senderDisplayName = senderDisplayName ?: existing.transferContentBlocks().firstOrNull()?.senderDisplayName,
                    statusText = null,
                    downloadUrlString = attachment.fileUri
                )
            )
        )
        val completedDuplicateIndex = messages.indexOfFirst { message ->
            message.id != existing.id && samePendingUploadMessage(uploadPlaceholder, message)
        }
        if (completedDuplicateIndex >= 0) {
            val completedDuplicate = messages[completedDuplicateIndex]
            messages[index] = mergeCompletedFileMessage(
                existing = existing,
                completed = completedDuplicate.copy(
                    id = existing.id,
                    sortTimestamp = existing.sortTimestamp ?: completedDuplicate.sortTimestamp
                )
            )
            messages.removeAt(completedDuplicateIndex)
            _state.value = _state.value.copy(messages = messages)
            return
        }
        val uploadItem = ComposerAttachmentUploadItem(
            gatewayId = gatewayId,
            attachment = attachment,
            progress = progress,
            phase = phase,
            failureMessage = failureMessage
        )
        messages[index] = ChatMessage(
            id = existing.id,
            role = existing.role,
            state = phase.toMessageState(),
            content = sanitizeChatDisplayText(attachment.fileName),
            contentBlocks = listOf(
                makeComposerAttachmentUploadContentBlock(
                    attachment = attachment,
                    gatewayId = gatewayId,
                    sessionKey = sessionKey,
                    senderDisplayName = senderDisplayName ?: existing.fileContentBlocks.firstOrNull()?.senderDisplayName,
                    statusText = uploadItem.statusText,
                    downloadUrlString = attachment.fileUri
                )
            ),
            createdAt = existing.createdAt,
            runId = existing.runId.ifBlank { composerAttachmentUploadRunId(attachment) },
            sortTimestamp = existing.sortTimestamp
        )

        _state.value = _state.value.copy(messages = messages)
    }

    @Suppress("ReturnCount")
    fun completeComposerAttachmentUploadMessage(
        attachment: ComposerAttachmentDraft,
        record: RelayFileTransferItem,
        gatewayId: String,
        sessionKey: String,
        completionSortTimestamp: Double
    ): Boolean {
        val messages = _state.value.messages.toMutableList()
        val fileRunId = record.fileId.trim().takeIf { it.isNotEmpty() }?.let { fileMessageRunId(it) }
        val index = messages.indexOfFirst { message ->
            message.id == attachment.id ||
                (fileRunId != null && message.runId == fileRunId) ||
                (fileRunId != null && message.fileContentBlocks.any { it.fileId == record.fileId })
        }
        if (index < 0) return false

        val existing = messages[index]
        val finalBlock = makeFileContentBlock(record)
        val attachmentFile = File(attachment.fileUri)
        val attachmentCacheKey = finalBlock.chatAttachmentCacheKey()
        if (attachmentCacheKey != null) {
            runCatching {
                if (attachmentFile.exists()) {
                    RemoteAttachmentCache.put(
                        key = attachmentCacheKey,
                        fileName = attachment.fileName,
                        bytes = attachmentFile.readBytes()
                    )
                }
            }
        }
        if (finalBlock.isImageFileBlock) {
            val cacheKey = finalBlock.chatImageCacheKey()
            if (cacheKey != null) {
                runCatching {
                    BitmapFactory.decodeFile(attachmentFile.absolutePath)
                        ?.let { bitmap ->
                            RemoteImageCache.put(cacheKey, bitmap)
                            RemoteImageSizeCache.put(cacheKey, bitmap.width.toFloat() to bitmap.height.toFloat())
                        }
                }
            }
        }
        val completedMessage = ChatMessage(
            id = existing.id,
            role = if (record.origin.equals("mobile", ignoreCase = true)) MessageRole.user else MessageRole.assistant,
            state = MessageState.completed,
            content = sanitizeChatDisplayText(record.fileName),
            contentBlocks = listOf(finalBlock),
            createdAt = java.time.Instant.ofEpochMilli((completionSortTimestamp * 1000).toLong()).toString(),
            runId = if (record.fileId.isNotBlank()) fileMessageRunId(record.fileId) else existing.runId,
            sortTimestamp = existing.sortTimestamp ?: completionSortTimestamp
        )
        val finalMessage = mergeCompletedFileMessage(existing = existing, completed = completedMessage)
        messages[index] = finalMessage
        val dedupedMessages = messages.filterIndexed { messageIndex, message ->
            val isSameUploadPlaceholder = message.id == attachment.id ||
                message.runId == composerAttachmentUploadRunId(attachment)
            messageIndex == index || (!isSameUploadPlaceholder && !sameFileMessage(message, finalMessage))
        }
        _state.value = _state.value.copy(messages = dedupedMessages)
        return true
    }

    private fun removeDuplicateFileMessages(fileMessage: ChatMessage) {
        val currentMessages = _state.value.messages
        val canonicalIndex = currentMessages.indexOfFirst { it.id == fileMessage.id }
        if (canonicalIndex < 0) return
        val deduped = currentMessages.filterIndexed { index, message ->
            index == canonicalIndex || !sameFileMessage(message, fileMessage)
        }
        if (deduped.size != currentMessages.size) {
            _state.value = _state.value.copy(messages = deduped)
        }
    }

    private fun mergeCompletedFileMessage(existing: ChatMessage, completed: ChatMessage): ChatMessage {
        val existingLocalBlocks = existing.transferContentBlocks()
        if (existingLocalBlocks.isEmpty() || completed.contentBlocks.isEmpty()) {
            return completed
        }

        val mergedBlocks = completed.contentBlocks.map { completedBlock ->
            val localBlock = existingLocalBlocks.firstOrNull { existingBlock ->
                existingBlock.fileId.isNullOrBlank() &&
                    existingBlock.isImageFileBlock &&
                    completedBlock.isImageFileBlock &&
                    sameTransferIdentity(existingBlock, completedBlock)
            }
            val localPreviewPath = localBlock?.fileDownloadURLString
                ?.trim()
                ?.takeIf { it.isNotEmpty() && File(it).exists() }
            if (localPreviewPath != null) {
                completedBlock.copy(
                    downloadUrl = localPreviewPath,
                    downloadPath = completedBlock.fileDownloadURLString
                )
            } else {
                completedBlock
            }
        }

        return completed.copy(contentBlocks = mergedBlocks)
    }

    private fun sameTransferIdentity(left: RelayChatContentBlock, right: RelayChatContentBlock): Boolean {
        val leftName = left.fileDisplayName?.trim().orEmpty()
        val rightName = right.fileDisplayName?.trim().orEmpty()
        if (leftName.isBlank() || !leftName.equals(rightName, ignoreCase = true)) return false

        val leftMime = left.mimeType?.trim().orEmpty()
        val rightMime = right.mimeType?.trim().orEmpty()
        if (leftMime.isNotBlank() && rightMime.isNotBlank() && !leftMime.equals(rightMime, ignoreCase = true)) {
            return false
        }

        val leftSize = left.sizeBytes?.takeIf { it > 0 }
        val rightSize = right.sizeBytes?.takeIf { it > 0 }
        return leftSize == null || rightSize == null || leftSize == rightSize
    }

    private fun makeComposerAttachmentUploadContentBlock(
        attachment: ComposerAttachmentDraft,
        gatewayId: String,
        sessionKey: String,
        senderDisplayName: String?,
        statusText: String?,
        downloadUrlString: String
    ): RelayChatContentBlock {
        return RelayChatContentBlock(
            type = when {
                attachment.mimeType.trim().lowercase().startsWith("audio/") -> "voice"
                attachment.mimeType.trim().lowercase().startsWith("image/") -> "image"
                else -> "file"
            },
            text = attachment.fileName,
            name = attachment.fileName,
            fileName = attachment.fileName,
            mimeType = attachment.mimeType,
            sizeBytes = attachment.sizeBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            durationMs = attachment.durationMs?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt(),
            imageWidth = attachment.imageWidth,
            imageHeight = attachment.imageHeight,
            downloadUrl = downloadUrlString,
            senderDisplayName = senderDisplayName,
            gatewayId = gatewayId,
            sessionKey = sessionKey,
            status = statusText
        )
    }

    private fun makeFileContentBlock(record: RelayFileTransferItem): RelayChatContentBlock {
        val normalizedMime = record.mimeType.trim().lowercase()
        return RelayChatContentBlock(
            type = when {
                normalizedMime.startsWith("audio/") -> "voice"
                normalizedMime.startsWith("image/") -> "image"
                else -> "file"
            },
            text = record.fileName,
            name = record.fileName,
            fileId = record.fileId,
            fileName = record.fileName,
            mimeType = record.mimeType,
            sizeBytes = record.sizeBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            durationMs = record.durationMs,
            imageWidth = record.imageWidth,
            imageHeight = record.imageHeight,
            downloadUrl = record.downloadPath,
            downloadPath = record.downloadPath,
            expiresAt = record.expiresAt,
            senderDisplayName = record.senderDisplayName,
            gatewayId = record.gatewayId,
            sessionKey = record.sessionKey,
            status = record.status
        )
    }

    private fun AttachmentUploadPhase.toMessageState(): MessageState {
        return when (this) {
            AttachmentUploadPhase.failed -> MessageState.failed
            AttachmentUploadPhase.uploading -> MessageState.streaming
            AttachmentUploadPhase.completed -> MessageState.completed
        }
    }

    private fun fileMessageRunId(fileId: String): String {
        return "file-$fileId"
    }

    private fun composerAttachmentUploadRunId(attachment: ComposerAttachmentDraft): String {
        return "upload-${attachment.id}"
    }

    private fun sanitizeChatDisplayText(text: String): String {
        return text.trim().ifBlank { "attachment" }
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
        sha256: String,
        durationMs: Int? = null,
        imageWidth: Int? = null,
        imageHeight: Int? = null,
        onProgress: ((Double) -> Unit)? = null
    ): RelayFileTransferItem {
        val sessionKey = _state.value.currentSessionKey
        if (sessionKey.isBlank()) throw IllegalStateException("No active chat session")
        val init = apiClient.initMobileFileUpload(
            gatewayId = gatewayId,
            sessionKey = sessionKey,
            fileName = fileName,
            mimeType = mimeType,
            sizeBytes = bytes.size.toLong(),
            sha256 = sha256,
            durationMs = durationMs,
            imageWidth = imageWidth,
            imageHeight = imageHeight
        )
        val chunkSize = init.chunkSize.coerceAtLeast(1)
        var offset = 0
        var chunkIndex = 0
        while (offset < bytes.size) {
            val end = minOf(offset + chunkSize, bytes.size)
            apiClient.uploadMobileFileChunk(init.uploadId, chunkIndex, bytes.copyOfRange(offset, end))
            offset = end
            chunkIndex += 1
            onProgress?.invoke((offset.toDouble() / bytes.size.toDouble()).coerceIn(0.0, 1.0))
        }
        if (bytes.isNotEmpty()) {
            onProgress?.invoke(1.0)
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
            val historyMessages = items.map { item ->
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
            val messages = if (current.currentGatewayId == normalizedGatewayId && current.currentSessionKey == normalizedSessionKey) {
                mergeHistoryWithCurrentMessages(historyMessages, current.messages)
            } else {
                historyMessages
            }
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
                !isNewGateway && current.isNotBlank() -> current
                sessions.any { it.sessionKey == defaultSessionKey } -> defaultSessionKey
                sessions.isNotEmpty() -> sessions.first().sessionKey
                else -> defaultSessionKey
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

    fun clearSessionImageCaches(gatewayId: String, sessionKey: String) {
        val normalizedGatewayId = gatewayId.trim()
        val normalizedSessionKey = sessionKey.trim().ifBlank { defaultSessionKey }
        if (normalizedGatewayId.isBlank()) return
        RemoteImageCache.clearSession(normalizedGatewayId, normalizedSessionKey)
        RemoteImageSizeCache.clearSession(normalizedGatewayId, normalizedSessionKey)
        RemoteAttachmentCache.clearSession(normalizedGatewayId, normalizedSessionKey)
    }

    suspend fun deleteSession(gatewayId: String, sessionKey: String, deleteTranscript: Boolean = false): Boolean {
        val normalizedGatewayId = gatewayId.trim()
        val normalizedSessionKey = sessionKey.trim().ifBlank { defaultSessionKey }
        if (normalizedGatewayId.isBlank() || normalizedSessionKey.isBlank()) return false
        val deleted = apiClient.deleteChatSession(normalizedGatewayId, normalizedSessionKey, deleteTranscript)
        if (deleted) {
            clearSessionImageCaches(normalizedGatewayId, normalizedSessionKey)
        }
        return deleted
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

    private fun mergeHistoryWithCurrentMessages(
        historyMessages: List<ChatMessage>,
        currentMessages: List<ChatMessage>
    ): List<ChatMessage> {
        if (currentMessages.isEmpty()) return historyMessages

        val merged = historyMessages.toMutableList()
        currentMessages.forEach { message ->
            if (!message.hasFileContent && !message.runId.startsWith("file-")) {
                return@forEach
            }

            val matchIndex = merged.indexOfFirst { existing ->
                existing.runId == message.runId || sameFileMessage(existing, message)
            }

            if (matchIndex < 0) {
                merged.add(message)
            } else {
                merged[matchIndex] = mergeFileMessage(existing = merged[matchIndex], candidate = message)
            }
        }

        return merged
    }

    private fun sameFileMessage(existing: ChatMessage, candidate: ChatMessage): Boolean {
        val existingCanonicalRunId = canonicalFileRunId(existing)
        val candidateCanonicalRunId = canonicalFileRunId(candidate)
        if (!existingCanonicalRunId.isNullOrBlank() && existingCanonicalRunId == candidateCanonicalRunId) {
            return true
        }

        val existingFileIds = existing.transferContentBlocks().mapNotNull { block ->
            block.fileId?.trim()?.takeIf { it.isNotEmpty() }
        }.toSet()
        val candidateFileIds = candidate.transferContentBlocks().mapNotNull { block ->
            block.fileId?.trim()?.takeIf { it.isNotEmpty() }
        }.toSet()
        if (existingFileIds.isNotEmpty() &&
            candidateFileIds.isNotEmpty() &&
            existingFileIds.intersect(candidateFileIds).isNotEmpty()
        ) {
            return true
        }

        return samePendingUploadMessage(existing, candidate) || samePendingUploadMessage(candidate, existing)
    }

    private fun canonicalFileRunId(message: ChatMessage): String? {
        message.transferContentBlocks().firstNotNullOfOrNull { block ->
            block.fileId?.trim()?.takeIf { it.isNotEmpty() }
        }?.let { return fileMessageRunId(it) }

        return message.runId.trim().takeIf { it.startsWith("file-") }
    }

    private fun ChatMessage.transferContentBlocks(): List<RelayChatContentBlock> {
        return fileContentBlocks + voiceContentBlocks
    }

    private fun samePendingUploadMessage(pending: ChatMessage, completed: ChatMessage): Boolean {
        val isLocalUploadPlaceholder = pending.runId.startsWith("upload-") || pending.state == MessageState.streaming
        if (!isLocalUploadPlaceholder) return false

        val pendingBlock = pending.transferContentBlocks().firstOrNull() ?: return false
        if (!pendingBlock.fileId.isNullOrBlank()) return false

        val completedBlock = completed.transferContentBlocks().firstOrNull { !it.fileId.isNullOrBlank() } ?: return false
        val pendingName = pendingBlock.fileDisplayName?.trim().orEmpty()
        val completedName = completedBlock.fileDisplayName?.trim().orEmpty()
        if (pendingName.isBlank() || !pendingName.equals(completedName, ignoreCase = true)) return false

        val pendingMime = pendingBlock.mimeType?.trim().orEmpty()
        val completedMime = completedBlock.mimeType?.trim().orEmpty()
        if (pendingMime.isNotBlank() && completedMime.isNotBlank() && !pendingMime.equals(completedMime, ignoreCase = true)) {
            return false
        }

        val pendingSize = pendingBlock.sizeBytes?.takeIf { it > 0 }
        val completedSize = completedBlock.sizeBytes?.takeIf { it > 0 }
        if (pendingSize != null && completedSize != null && pendingSize != completedSize) {
            return false
        }

        val pendingGatewayId = pendingBlock.gatewayId?.trim().orEmpty()
        val completedGatewayId = completedBlock.gatewayId?.trim().orEmpty()
        if (pendingGatewayId.isNotBlank() && completedGatewayId.isNotBlank() && pendingGatewayId != completedGatewayId) {
            return false
        }

        val pendingSessionKey = pendingBlock.sessionKey?.trim().orEmpty()
        val completedSessionKey = completedBlock.sessionKey?.trim().orEmpty()
        return pendingSessionKey.isBlank() || completedSessionKey.isBlank() || pendingSessionKey == completedSessionKey
    }

    private fun mergeFileMessage(existing: ChatMessage, candidate: ChatMessage): ChatMessage {
        if (existing.contentBlocks.isNotEmpty() || candidate.contentBlocks.isEmpty()) {
            return existing
        }

        return candidate.copy(
            id = existing.id,
            sortTimestamp = existing.sortTimestamp ?: candidate.sortTimestamp
        )
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
