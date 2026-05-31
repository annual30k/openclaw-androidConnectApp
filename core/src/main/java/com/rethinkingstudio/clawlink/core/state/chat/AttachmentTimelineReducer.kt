package com.rethinkingstudio.clawlink.core.state.chat

internal object AttachmentTimelineReducer {
    fun reduce(state: ChatTimelineState, event: TimelineEvent): ChatTimelineState {
        return when (event) {
            is TimelineEvent.AttachmentStateChanged -> reduce(state, event, rememberEvent = true)
            else -> state
        }
    }

    fun reduce(
        state: ChatTimelineState,
        event: TimelineEvent.AttachmentStateChanged,
        rememberEvent: Boolean
    ): ChatTimelineState {
        if (rememberEvent && event.eventId != null && event.eventId in state.seenEventIds) return state
        val existing = state.attachmentsById[event.attachmentId]
        val attachment = TimelineAttachmentState(
            attachmentId = event.attachmentId,
            messageId = event.messageId ?: existing?.messageId,
            state = event.state,
            url = event.url ?: existing?.url
        )
        val nextSeen = if (rememberEvent) {
            event.eventId?.takeIf { it.isNotBlank() }?.let { state.seenEventIds + it } ?: state.seenEventIds
        } else {
            state.seenEventIds
        }
        return state.copy(
            seenEventIds = nextSeen,
            attachmentsById = state.attachmentsById + (event.attachmentId to attachment)
        )
    }
}
