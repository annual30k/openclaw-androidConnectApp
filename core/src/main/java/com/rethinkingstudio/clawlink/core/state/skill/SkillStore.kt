package com.rethinkingstudio.clawlink.core.state.skill

import com.rethinkingstudio.clawlink.core.models.skills.SkillFilter
import com.rethinkingstudio.clawlink.core.models.skills.SkillItem
import com.rethinkingstudio.clawlink.core.network.RelayAPIClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SkillState(
    val skills: List<SkillItem> = emptyList(),
    val filter: SkillFilter = SkillFilter(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val filteredSkills: List<SkillItem> get() = skills.filter { filter.matches(it) }
    val enabledCount: Int get() = skills.count { it.enabled }
    val totalCount: Int get() = skills.size
}

class SkillStore(
    private val apiClient: RelayAPIClient
) {
    private val _state = MutableStateFlow(SkillState())
    val state: StateFlow<SkillState> = _state.asStateFlow()

    suspend fun loadSkills(gatewayId: String) {
        _state.value = _state.value.copy(isLoading = true)
        try {
            val skills = apiClient.fetchSkills(gatewayId)
            _state.value = _state.value.copy(skills = skills, isLoading = false)
        } catch (e: Exception) {
            _state.value = _state.value.copy(isLoading = false, errorMessage = e.message)
        }
    }

    suspend fun updateSkill(gatewayId: String, skillKey: String, enabled: Boolean? = null, apiKey: String? = null) {
        try {
            apiClient.updateSkill(gatewayId, skillKey, enabled, apiKey)
            val skills = _state.value.skills.map {
                if (it.key == skillKey && enabled != null) it.copy(enabled = enabled) else it
            }
            _state.value = _state.value.copy(skills = skills)
        } catch (e: Exception) {
            _state.value = _state.value.copy(errorMessage = e.message)
        }
    }

    fun updateFilter(filter: SkillFilter) {
        _state.value = _state.value.copy(filter = filter)
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }
}
