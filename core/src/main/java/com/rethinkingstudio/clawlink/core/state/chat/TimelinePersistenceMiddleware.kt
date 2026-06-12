package com.rethinkingstudio.clawlink.core.state.chat

import android.content.Context
import android.content.SharedPreferences
import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object TimelinePersistenceMiddleware {
    private const val PREFS_NAME = "clawlink_chat_timeline_pending"
    private const val KEY_SNAPSHOT = "snapshot"
    private const val SNAPSHOT_SCHEMA_VERSION = 6
    @Volatile private var prefs: SharedPreferences? = null

    @Serializable
    private data class TimelineSnapshotEnvelope(
        val schemaVersion: Int,
        val state: ChatTimelineState
    )

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    internal fun encodeSnapshot(state: ChatTimelineState): String {
        return json.encodeToString(
            TimelineSnapshotEnvelope(
                schemaVersion = SNAPSHOT_SCHEMA_VERSION,
                state = state.canonicalSnapshotState()
            )
        )
    }

    internal fun decodeSnapshot(raw: String): ChatTimelineState? {
        return try {
            val envelope = json.decodeFromString(TimelineSnapshotEnvelope.serializer(), raw)
            envelope.state
                .takeIf { envelope.schemaVersion == SNAPSHOT_SCHEMA_VERSION }
                ?.canonicalSnapshotState()
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    internal fun persistSnapshot(state: ChatTimelineState) {
        prefs?.edit()?.putString(KEY_SNAPSHOT, encodeSnapshot(state))?.apply()
    }

    internal fun restoreSnapshot(): ChatTimelineState? {
        val raw = prefs?.getString(KEY_SNAPSHOT, null) ?: return null
        return decodeSnapshot(raw)
    }

    internal fun clearSnapshot() {
        prefs?.edit()?.remove(KEY_SNAPSHOT)?.apply()
    }
}

private fun ChatTimelineState.canonicalSnapshotState(): ChatTimelineState {
    val canonicalMessages = messages.filter { it.hasCanonicalTimelineKeysForSnapshot() }
    if (canonicalMessages.size == messages.size) return this
    return copy(
        messages = canonicalMessages,
        activeRunId = null,
        activeRunsByTurnId = emptyMap(),
        activeTurnByRunId = emptyMap(),
        seenPartSeqKeys = emptySet(),
        messagePartSeqByKey = emptyMap(),
        messagePartsById = emptyMap(),
        attachmentsById = emptyMap(),
        toolsById = emptyMap()
    )
}

private fun ChatMessage.hasCanonicalTimelineKeysForSnapshot(): Boolean {
    return timelineOrderKey.trim().isNotEmpty() &&
        timelineIdentityKey.trim().isNotEmpty() &&
        timelineItemKind.trim().isNotEmpty()
}
