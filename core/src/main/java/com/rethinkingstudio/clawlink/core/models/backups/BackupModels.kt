package com.rethinkingstudio.clawlink.core.models.backups

import kotlinx.serialization.Serializable

@Serializable
data class BackupItem(
    val id: String,
    val title: String,
    val detail: String = "",
    val filename: String = "",
    val sizeBytes: Long? = null,
    val createdAt: String,
    val updatedAt: String = ""
) {
    val displayLabel: String get() = title.ifBlank { "Backup ${createdAt.take(10)}" }
}

@Serializable
data class BackupDraft(
    var title: String = "",
    var detail: String = "",
    var filename: String = ""
)
