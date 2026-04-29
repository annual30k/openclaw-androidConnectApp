package com.rethinkingstudio.clawlink.core.models.backups

import kotlinx.serialization.Serializable

@Serializable
data class BackupItem(
    val id: String,
    val label: String,
    val note: String? = null,
    val createdAt: String,
    val status: String = "ready",
    val sizeBytes: Long? = null,
    val gatewayId: String
) {
    val displayLabel: String get() = label.ifBlank { "Backup ${createdAt.take(10)}" }
    val isReady: Boolean get() = status == "ready"
}

@Serializable
data class BackupDraft(
    val label: String = "",
    val note: String = ""
)
