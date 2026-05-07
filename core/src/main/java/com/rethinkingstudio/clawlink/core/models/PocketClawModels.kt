package com.rethinkingstudio.clawlink.core.models

import kotlinx.serialization.Serializable

@Serializable
data class SessionCredentials(
    val accessToken: String,
    val relayBaseURL: String
)

@Serializable
data class AdvancedAction(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val isDestructive: Boolean = false,
    val requiresConfirmation: Boolean = false
)

@Serializable
data class OpenClawDoctorFixLogEntry(
    val timestamp: String,
    val action: String,
    val detail: String,
    val success: Boolean
)

@Serializable
data class LogEntry(
    val line: String,
    val timestamp: String? = null,
    val level: String? = null
)
@Serializable
data class MaintenanceLogEntry(
    val timestamp: Long,
    val stream: String,
    val text: String
)
