package com.rethinkingstudio.clawlink.core.models.skills

import kotlinx.serialization.Serializable

@Serializable
data class SkillItem(
    val key: String,
    val displayName: String,
    val description: String? = null,
    val version: String? = null,
    val enabled: Boolean = true,
    val installedAt: String? = null,
    val source: String? = null,
    val hasApiKey: Boolean = false,
    val envKeys: List<String>? = null,
    val commands: List<SkillCommand>? = null,
    val status: String? = null,
    val statusDetail: String? = null
)

@Serializable
data class SkillCommand(
    val name: String,
    val description: String? = null
)

data class SkillFilter(
    val query: String = "",
    val showEnabledOnly: Boolean = false
) {
    fun matches(skill: SkillItem): Boolean {
        if (showEnabledOnly && !skill.enabled) return false
        if (query.isNotBlank()) {
            val q = query.lowercase()
            return skill.displayName.lowercase().contains(q) ||
                (skill.description?.lowercase()?.contains(q) == true) ||
                skill.key.lowercase().contains(q)
        }
        return true
    }
}
