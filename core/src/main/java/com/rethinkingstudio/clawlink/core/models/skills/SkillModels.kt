package com.rethinkingstudio.clawlink.core.models.skills

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class SkillItem(
    val key: String = "",
    val displayName: String = "",
    val description: String? = null,
    val version: String? = null,
    val enabled: Boolean = true,
    val installedAt: String? = null,
    val source: String? = null,
    val hasApiKey: Boolean = false,
    val envKeys: List<String>? = null,
    val commands: List<SkillCommand>? = null,
    val status: String? = null,
    val statusDetail: String? = null,
    @SerialName("skillKey") val skillKey: String? = null,
    val name: String? = null,
    val filePath: String? = null,
    val baseDir: String? = null,
    val primaryEnv: String? = null,
    val emoji: String? = null,
    val homepage: String? = null,
    val always: Boolean = false,
    val disabled: Boolean? = null,
    val blockedByAllowlist: Boolean = false,
    val eligible: Boolean? = null,
    val requirements: SkillRequirements = SkillRequirements(),
    val missing: SkillMissing = SkillMissing(),
    val configChecks: List<SkillStatusConfigCheck> = emptyList(),
    val install: List<SkillInstallOption> = emptyList()
) {
    val effectiveKey: String get() = skillKey?.ifBlank { null } ?: key
    val effectiveName: String get() = name?.ifBlank { null } ?: displayName.ifBlank { effectiveKey }
    val effectiveDescription: String get() = description.orEmpty()
    val isEnabled: Boolean get() = disabled?.let { !it } ?: enabled
}

@Serializable
data class SkillCommand(
    val name: String,
    val description: String? = null
)

@Serializable
data class SkillRequirements(
    val bins: List<String> = emptyList(),
    val anyBins: List<String> = emptyList(),
    val env: List<String> = emptyList(),
    val config: List<String> = emptyList(),
    val os: List<String> = emptyList()
) {
    val isEmpty: Boolean get() = bins.isEmpty() && anyBins.isEmpty() && env.isEmpty() && config.isEmpty() && os.isEmpty()
}

@Serializable
data class SkillMissing(
    val bins: List<String> = emptyList(),
    val anyBins: List<String> = emptyList(),
    val env: List<String> = emptyList(),
    val config: List<String> = emptyList(),
    val os: List<String> = emptyList()
) {
    val count: Int get() = bins.size + anyBins.size + env.size + config.size + os.size
    val isEmpty: Boolean get() = count == 0
}

@Serializable
data class SkillStatusConfigCheck(
    val path: String,
    val satisfied: Boolean
)

@Serializable
data class SkillInstallOption(
    val id: String,
    val kind: String,
    val label: String,
    val bins: List<String> = emptyList()
)

data class SkillFilter(
    val query: String = "",
    val showEnabledOnly: Boolean = false
) {
    fun matches(skill: SkillItem): Boolean {
        if (showEnabledOnly && !skill.isEnabled) return false
        if (query.isNotBlank()) {
            val q = query.lowercase()
            return skill.effectiveName.lowercase().contains(q) ||
                skill.effectiveDescription.lowercase().contains(q) ||
                skill.effectiveKey.lowercase().contains(q)
        }
        return true
    }
}
