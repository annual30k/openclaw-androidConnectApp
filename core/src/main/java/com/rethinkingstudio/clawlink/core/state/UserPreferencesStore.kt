package com.rethinkingstudio.clawlink.core.state

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UserPreferencesStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("clawlink_prefs", Context.MODE_PRIVATE)

    private val _showsToolInvocationProcess = MutableStateFlow(prefs.getBoolean(KEY_SHOW_TOOLS, true))
    val showsToolInvocationProcess: StateFlow<Boolean> = _showsToolInvocationProcess.asStateFlow()

    private val _assistantVoiceRepliesEnabled = MutableStateFlow(prefs.getBoolean(KEY_VOICE_REPLIES, false))
    val assistantVoiceRepliesEnabled: StateFlow<Boolean> = _assistantVoiceRepliesEnabled.asStateFlow()

    fun setShowsToolInvocationProcess(enabled: Boolean) {
        _showsToolInvocationProcess.value = enabled
        prefs.edit().putBoolean(KEY_SHOW_TOOLS, enabled).apply()
    }

    fun setAssistantVoiceRepliesEnabled(enabled: Boolean) {
        _assistantVoiceRepliesEnabled.value = enabled
        prefs.edit().putBoolean(KEY_VOICE_REPLIES, enabled).apply()
    }

    companion object {
        private const val KEY_SHOW_TOOLS = "show_tool_invocation_process"
        private const val KEY_VOICE_REPLIES = "assistant_voice_replies_enabled"
    }
}
