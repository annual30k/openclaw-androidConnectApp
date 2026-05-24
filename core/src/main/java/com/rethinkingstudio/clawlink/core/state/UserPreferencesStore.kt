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

    fun setShowsToolInvocationProcess(enabled: Boolean) {
        _showsToolInvocationProcess.value = enabled
        prefs.edit().putBoolean(KEY_SHOW_TOOLS, enabled).apply()
    }

    companion object {
        private const val KEY_SHOW_TOOLS = "show_tool_invocation_process"
    }
}
