package com.rethinkingstudio.clawlink.core.domain

import com.rethinkingstudio.clawlink.core.models.SessionCredentials
import com.rethinkingstudio.clawlink.core.models.backups.BackupItem
import com.rethinkingstudio.clawlink.core.models.catalog.ModelItem
import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.ChatSessionItem
import com.rethinkingstudio.clawlink.core.models.gateway.GatewaySummary
import com.rethinkingstudio.clawlink.core.models.skills.SkillItem
import com.rethinkingstudio.clawlink.core.models.tasks.TaskItem
import com.rethinkingstudio.clawlink.core.network.dto.ChatHistoryItem

interface CredentialStore {
    suspend fun saveCredentials(credentials: SessionCredentials)
    suspend fun loadCredentials(): SessionCredentials?
    suspend fun clearCredentials()
    suspend fun saveLastGatewayId(gatewayId: String)
    suspend fun loadLastGatewayId(): String?
}

interface NotificationPort {
    fun showReplyNotification(sessionKey: String, title: String, body: String)
    fun cancelNotification(id: Int)
    fun cancelAll()
}
