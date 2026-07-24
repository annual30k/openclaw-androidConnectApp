package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import com.rethinkingstudio.clawlink.core.network.dto.ChatHistoryItem
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal data class HistoryPageRequest(
    val gatewayId: String,
    val sessionKey: String,
    val limit: Int,
    val cursor: String?,
    val direction: String
)

@Suppress("UNCHECKED_CAST")
internal fun ChatStore.setStateForTest(state: ChatState) {
    val field = ChatStore::class.java.getDeclaredField("_state")
    field.isAccessible = true
    val flow = field.get(this) as MutableStateFlow<ChatState>
    flow.value = state
}

internal fun ChatStore.setTimelineStateForTest(state: ChatTimelineState) {
    val field = ChatStore::class.java.getDeclaredField("timelineState")
    field.isAccessible = true
    field.set(this, state)
}

internal fun ChatStore.setRunScopesForTest(scopes: LinkedHashMap<String, ChatRunScope>) {
    val field = ChatStore::class.java.getDeclaredField("chatRunScopes")
    field.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val value = field.get(this) as MutableMap<String, ChatRunScope>
    value.clear()
    value.putAll(scopes)
}

internal fun ChatStore.setStreamingMessageIdForTest(messageId: String?) {
    val field = ChatStore::class.java.getDeclaredField("streamingMessageId")
    field.isAccessible = true
    field.set(this, messageId)
}

internal fun ChatStore.timelineStateForTest(): ChatTimelineState {
    val field = ChatStore::class.java.getDeclaredField("timelineState")
    field.isAccessible = true
    return field.get(this) as ChatTimelineState
}

internal fun ChatStore.invokeHandleFinalForTest(envelope: JsonObject, payload: JsonElement) {
    val method = ChatStore::class.java.getDeclaredMethod(
        "handleFinal",
        JsonObject::class.java,
        JsonElement::class.java
    )
    method.isAccessible = true
    method.invoke(this, envelope, payload)
}

internal fun chatHistoryItems(indices: IntRange): List<ChatHistoryItem> {
    return indices.map { index ->
        ChatHistoryItem(
            id = "history-$index",
            role = "assistant",
            content = JsonPrimitive("message $index"),
            createdAt = Instant.EPOCH.plusSeconds(index.toLong()).toString()
        )
    }
}

internal fun canonicalMessage(
    id: String,
    role: MessageRole,
    content: String,
    order: String,
    identity: String,
    runId: String
): ChatMessage {
    return ChatMessage(
        id = id,
        role = role,
        state = MessageState.completed,
        content = content,
        contentBlocks = listOf(RelayChatContentBlock(type = "text", text = content)),
        createdAt = "2026-06-10T00:00:00.000Z",
        runId = runId,
        timelineOrderKey = order,
        timelineIdentityKey = identity,
        timelineItemKind = if (role == MessageRole.user) "message:user" else "message:assistant"
    )
}
