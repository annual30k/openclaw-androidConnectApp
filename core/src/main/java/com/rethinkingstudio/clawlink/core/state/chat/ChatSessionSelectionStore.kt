package com.rethinkingstudio.clawlink.core.state.chat

import android.content.Context
import android.content.SharedPreferences

class ChatSessionSelectionStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(gatewayId: String): String? {
        val key = preferenceKey(gatewayId)
        if (key.isBlank()) return null
        return prefs.getString(key, null)?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun save(gatewayId: String, sessionKey: String) {
        val key = preferenceKey(gatewayId)
        val normalizedSessionKey = sessionKey.trim().ifBlank { DEFAULT_SESSION_KEY }
        if (key.isBlank()) return
        prefs.edit().putString(key, normalizedSessionKey).apply()
    }

    fun clear(gatewayId: String, sessionKey: String) {
        val key = preferenceKey(gatewayId)
        if (key.isBlank()) return
        val normalizedSessionKey = sessionKey.trim().ifBlank { DEFAULT_SESSION_KEY }
        if (prefs.getString(key, null)?.trim() == normalizedSessionKey) {
            prefs.edit().remove(key).apply()
        }
    }

    private fun preferenceKey(gatewayId: String): String {
        val normalizedGatewayId = gatewayId.trim()
        return if (normalizedGatewayId.isBlank()) "" else "$KEY_PREFIX$normalizedGatewayId"
    }

    private companion object {
        const val PREFS_NAME = "clawlink_chat_session_selection_v2"
        const val KEY_PREFIX = "last_session:"
        const val DEFAULT_SESSION_KEY = "main"
    }
}
