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

    // Aliases for VoiceSetupScreen compatibility
    val voiceReplyEnabled: StateFlow<Boolean> get() = _assistantVoiceRepliesEnabled

    private val _voiceReplyVoiceIdentifier = MutableStateFlow(normalizeVoiceIdentifier(prefs.getString(KEY_VOICE_IDENTIFIER, null)))
    val voiceReplyVoiceIdentifier: StateFlow<String> = _voiceReplyVoiceIdentifier.asStateFlow()

    private val _voiceReplyRatePercent = MutableStateFlow(loadVoiceReplyRatePercent())
    val voiceReplyRatePercent: StateFlow<Int> = _voiceReplyRatePercent.asStateFlow()

    fun setShowsToolInvocationProcess(enabled: Boolean) {
        _showsToolInvocationProcess.value = enabled
        prefs.edit().putBoolean(KEY_SHOW_TOOLS, enabled).apply()
    }

    fun setAssistantVoiceRepliesEnabled(enabled: Boolean) {
        _assistantVoiceRepliesEnabled.value = enabled
        prefs.edit().putBoolean(KEY_VOICE_REPLIES, enabled).apply()
    }

    fun setVoiceReplyEnabled(enabled: Boolean) = setAssistantVoiceRepliesEnabled(enabled)

    fun setVoiceReplyVoiceIdentifier(identifier: String?) {
        val normalized = normalizeVoiceIdentifier(identifier)
        _voiceReplyVoiceIdentifier.value = normalized
        prefs.edit().putString(KEY_VOICE_IDENTIFIER, normalized).apply()
    }

    fun setVoiceReplyRatePercent(rate: Int) {
        val clamped = clampVoiceReplyRatePercent(rate)
        _voiceReplyRatePercent.value = clamped
        prefs.edit().putInt(KEY_VOICE_RATE, clamped).apply()
    }

    companion object {
        const val VOICE_REPLY_DEFAULT_RATE_PERCENT = 0
        const val VOICE_REPLY_MIN_RATE_PERCENT = -50
        const val VOICE_REPLY_MAX_RATE_PERCENT = 50

        private const val KEY_SHOW_TOOLS = "show_tool_invocation_process"
        private const val KEY_VOICE_REPLIES = "assistant_voice_replies_enabled"
        private const val KEY_VOICE_IDENTIFIER = "voice_reply_voice_identifier"
        private const val KEY_VOICE_RATE = "voice_reply_rate_percent"

        fun normalizeVoiceIdentifier(identifier: String?): String {
            val trimmed = identifier?.trim().orEmpty()
            return if (trimmed.isBlank() || trimmed == "auto") "" else trimmed
        }

        fun clampVoiceReplyRatePercent(rate: Int): Int {
            return rate.coerceIn(VOICE_REPLY_MIN_RATE_PERCENT, VOICE_REPLY_MAX_RATE_PERCENT)
        }
    }

    private fun loadVoiceReplyRatePercent(): Int {
        if (!prefs.contains(KEY_VOICE_RATE)) return VOICE_REPLY_DEFAULT_RATE_PERCENT
        val stored = prefs.getInt(KEY_VOICE_RATE, VOICE_REPLY_DEFAULT_RATE_PERCENT)
        return if (stored in VOICE_REPLY_MIN_RATE_PERCENT..VOICE_REPLY_MAX_RATE_PERCENT) {
            stored
        } else {
            VOICE_REPLY_DEFAULT_RATE_PERCENT
        }
    }
}
