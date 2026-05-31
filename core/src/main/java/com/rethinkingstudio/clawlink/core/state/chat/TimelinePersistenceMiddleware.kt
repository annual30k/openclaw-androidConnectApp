package com.rethinkingstudio.clawlink.core.state.chat

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object TimelinePersistenceMiddleware {
    private const val PREFS_NAME = "clawlink_chat_timeline_pending"
    private const val KEY_SNAPSHOT = "snapshot"
    @Volatile private var prefs: SharedPreferences? = null

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    internal fun encodeSnapshot(state: ChatTimelineState): String {
        return json.encodeToString(state)
    }

    internal fun decodeSnapshot(raw: String): ChatTimelineState? {
        return try {
            json.decodeFromString(ChatTimelineState.serializer(), raw)
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
