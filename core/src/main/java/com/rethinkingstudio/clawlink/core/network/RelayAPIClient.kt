package com.rethinkingstudio.clawlink.core.network

import com.rethinkingstudio.clawlink.core.models.SessionCredentials
import com.rethinkingstudio.clawlink.core.models.backups.BackupItem
import com.rethinkingstudio.clawlink.core.models.catalog.ModelItem
import com.rethinkingstudio.clawlink.core.models.chat.ChatSessionItem
import com.rethinkingstudio.clawlink.core.models.gateway.GatewaySummary
import com.rethinkingstudio.clawlink.core.models.skills.SkillItem
import com.rethinkingstudio.clawlink.core.models.tasks.TaskItem
import com.rethinkingstudio.clawlink.core.network.dto.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class RelayAPIClient(
    var baseUrl: String = "",
    var accessToken: String = ""
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
        }
    }

    val isConfigured: Boolean get() = baseUrl.isNotBlank()

    fun configure(credentials: SessionCredentials) {
        baseUrl = credentials.relayBaseURL
        accessToken = credentials.accessToken
    }

    fun updateToken(token: String) {
        accessToken = token
    }

    fun clearSession() {
        baseUrl = ""
        accessToken = ""
    }


    private fun buildUrl(endpoint: APIEndpoint): String {
        val url = baseUrl.trimEnd('/') + endpoint.path
        return if (endpoint.queryItems.isNotEmpty()) {
            url + "?" + endpoint.queryItems.joinToString("&") { "${it.first}=${it.second}" }
        } else url
    }

    private suspend inline fun <reified T> request(endpoint: APIEndpoint, body: Any? = null, token: String? = null): T {
        val url = buildUrl(endpoint)
        val response = httpClient.request(url) {
            method = when (endpoint.method) {
                HTTPMethod.GET -> HttpMethod.Get
                HTTPMethod.POST -> HttpMethod.Post
                HTTPMethod.PATCH -> HttpMethod.Patch
                HTTPMethod.DELETE -> HttpMethod.Delete
                HTTPMethod.PUT -> HttpMethod.Put
            }
            contentType(ContentType.Application.Json)
            val authToken = token ?: accessToken
            if (authToken.isNotBlank()) {
                header(HttpHeaders.Authorization, "Bearer $authToken")
            }
            if (body != null) {
                setBody(body)
            }
        }
        if (response.status.value >= 400) {
            val errorBody = try { response.body<APIErrorResponse>() } catch (_: Exception) { null }
            throw RelayAPIError.fromStatusCode(
                code = response.status.value,
                body = errorBody?.error,
                remainingAttempts = errorBody?.remainingAttempts,
                retryAfterSeconds = errorBody?.retryAfterSeconds
            )
        }
        return response.body()
    }

    private suspend inline fun <reified T> requestOptional(endpoint: APIEndpoint, body: Any? = null, token: String? = null): T? {
        return try { request(endpoint, body, token) } catch (_: Exception) { null }
    }

    private suspend fun requestBytes(endpoint: APIEndpoint, bytes: ByteArray, token: String? = null) {
        val response = httpClient.request(buildUrl(endpoint)) {
            method = when (endpoint.method) {
                HTTPMethod.GET -> HttpMethod.Get
                HTTPMethod.POST -> HttpMethod.Post
                HTTPMethod.PATCH -> HttpMethod.Patch
                HTTPMethod.DELETE -> HttpMethod.Delete
                HTTPMethod.PUT -> HttpMethod.Put
            }
            contentType(ContentType.Application.OctetStream)
            val authToken = token ?: accessToken
            if (authToken.isNotBlank()) {
                header(HttpHeaders.Authorization, "Bearer $authToken")
            }
            setBody(bytes)
        }
        if (response.status.value >= 400) {
            val errorBody = try { json.decodeFromString(APIErrorResponse.serializer(), response.bodyAsText()) } catch (_: Exception) { null }
            throw RelayAPIError.fromStatusCode(
                code = response.status.value,
                body = errorBody?.error,
                remainingAttempts = errorBody?.remainingAttempts,
                retryAfterSeconds = errorBody?.retryAfterSeconds
            )
        }
    }

    // ── Auth ─────────────────────────────────────────────────────────────

    sealed class RegistrationResult {
        data class Authenticated(val credentials: SessionCredentials) : RegistrationResult()
        data class VerificationRequired(val email: String, val expiresAt: String?) : RegistrationResult()
    }

    suspend fun register(name: String, email: String, password: String, deviceId: String): RegistrationResult {
        val req = AuthRequest(name = name, email = email, password = password, deviceId = deviceId)
        val response: RegisterResponse = request(APIEndpoints.Auth.register, req)
        val token = response.accessToken
        return if (!token.isNullOrBlank()) {
            RegistrationResult.Authenticated(SessionCredentials(accessToken = token, relayBaseURL = baseUrl))
        } else if (response.verificationRequired == true) {
            RegistrationResult.VerificationRequired(email = response.email ?: email, expiresAt = response.expiresAt)
        } else {
            throw RelayAPIError.InvalidResponse
        }
    }

    suspend fun verifyEmail(email: String, code: String, deviceId: String): SessionCredentials {
        val req = VerifyEmailRequest(email = email, code = code, deviceId = deviceId)
        val response: LoginResponse = request(APIEndpoints.Auth.verifyEmail, req)
        return SessionCredentials(accessToken = response.accessToken, relayBaseURL = baseUrl)
    }

    suspend fun authenticate(email: String, password: String, deviceId: String): SessionCredentials {
        val req = AuthRequest(email = email, password = password, deviceId = deviceId)
        val response: LoginResponse = request(APIEndpoints.Auth.login, req)
        return SessionCredentials(accessToken = response.accessToken, relayBaseURL = baseUrl)
    }

    suspend fun deleteAccount() {
        request<EmptyResponse>(APIEndpoints.Auth.deleteAccount)
    }

    suspend fun pairGateway(gatewayId: String?, accessCode: String, deviceId: String): SessionCredentials {
        val req = PairRequest(gatewayId = gatewayId, accessCode = accessCode, deviceId = deviceId)
        val response: LoginResponse = request(APIEndpoints.Auth.pairGateway, req)
        return SessionCredentials(accessToken = response.accessToken, relayBaseURL = baseUrl)
    }

    // ── Gateways ─────────────────────────────────────────────────────────

    suspend fun fetchGateways(): List<GatewaySummary> {
        val response: GatewayListResponse = request(APIEndpoints.Mobile.Gateway.list())
        return response.gateways.map { it.toGatewaySummary() }
    }

    suspend fun fetchModels(gatewayId: String): List<ModelItem> {
        val response: ModelListResponse = request(APIEndpoints.Mobile.Gateway.models(gatewayId))
        return response.items
    }

    suspend fun selectModel(gatewayId: String, providerId: String, modelId: String, modelAlias: String, modelName: String, sessionKey: String? = null) {
        val req = SelectModelRequest(providerId, modelId, modelAlias, modelName, sessionKey)
        request<EmptyResponse>(APIEndpoints.Mobile.Gateway.selectModel(gatewayId), req)
    }

    suspend fun fetchSkills(gatewayId: String): List<SkillItem> {
        val response: SkillsListResponse = request(APIEndpoints.Mobile.Gateway.skills(gatewayId))
        return response.skills
    }

    suspend fun updateSkill(gatewayId: String, skillKey: String, enabled: Boolean? = null, apiKey: String? = null, env: Map<String, String>? = null) {
        val req = SkillUpdateRequest(enabled, apiKey, env)
        request<EmptyResponse>(APIEndpoints.Mobile.Gateway.skill(gatewayId, skillKey), req)
    }

    suspend fun approveSensitiveAction(gatewayId: String, method: String): ApproveSensitiveActionResponse {
        val req = ApproveSensitiveActionRequest(method)
        return request(APIEndpoints.Mobile.Gateway.approveSensitiveAction(gatewayId), req)
    }

    suspend fun updateGateway(gatewayId: String, body: Map<String, String>) {
        request<EmptyResponse>(APIEndpoints.Mobile.Gateway.update(gatewayId), body)
    }

    suspend fun restartGateway(gatewayId: String) {
        request<EmptyResponse>(APIEndpoints.Mobile.Gateway.restart(gatewayId))
    }

    suspend fun remoteRestartGateway(gatewayId: String) {
        request<EmptyResponse>(APIEndpoints.Mobile.Gateway.remoteRestart(gatewayId))
    }

    suspend fun executeAdvancedAction(gatewayId: String, kind: String): AdvancedActionResponse {
        val req = mapOf("kind" to kind)
        return request(APIEndpoints.Mobile.Gateway.executeAdvancedAction(gatewayId), req)
    }

    // ── Chat ─────────────────────────────────────────────────────────────

    suspend fun fetchChatHistory(gatewayId: String, sessionKey: String, limit: Int = 50): List<ChatHistoryItem> {
        val response: ChatHistoryResponse = request(APIEndpoints.Mobile.Chat.history(gatewayId, sessionKey, limit))
        return response.items
    }

    suspend fun checkChatReady(gatewayId: String): Boolean {
        val response: GatewayChatReadyResponse = request(APIEndpoints.Mobile.Chat.ready(gatewayId))
        return response.ready
    }

    suspend fun fetchChatSessions(gatewayId: String, limit: Int = 50, activeMinutes: Int? = null): List<ChatSessionItem> {
        val response: ChatSessionListResponse = request(APIEndpoints.Mobile.Chat.sessions(gatewayId, limit, activeMinutes))
        return response.items
    }

    suspend fun deleteChatSession(gatewayId: String, sessionKey: String, deleteTranscript: Boolean = false): Boolean {
        val response: ChatSessionDeleteResponse = request(APIEndpoints.Mobile.Chat.deleteSession(gatewayId, sessionKey, deleteTranscript))
        return response.ok
    }

    suspend fun initMobileFileUpload(
        gatewayId: String,
        sessionKey: String,
        fileName: String,
        mimeType: String,
        sizeBytes: Long,
        sha256: String
    ): FileUploadInitResponse {
        val req = FileUploadInitRequest(
            sessionKey = sessionKey,
            fileName = fileName,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            sha256 = sha256
        )
        return request(APIEndpoints.Mobile.File.initUpload(APIOrigin.mobile, gatewayId), req)
    }

    suspend fun uploadMobileFileChunk(uploadId: String, chunkIndex: Int, bytes: ByteArray) {
        requestBytes(APIEndpoints.Mobile.File.uploadChunk(APIOrigin.mobile, uploadId, chunkIndex), bytes)
    }

    suspend fun completeMobileFileUpload(uploadId: String, totalChunks: Int): FileUploadCompleteResponse {
        return request(APIEndpoints.Mobile.File.completeUpload(APIOrigin.mobile, uploadId), FileUploadCompleteRequest(totalChunks))
    }

    // ── Tasks ────────────────────────────────────────────────────────────

    suspend fun fetchTasks(gatewayId: String): List<TaskItem> {
        val response: TaskListResponse = request(APIEndpoints.Mobile.Task.list(gatewayId))
        return response.items
    }

    suspend fun createTask(gatewayId: String, title: String, prompt: String, scheduleKind: String, scheduleAt: String? = null, repeatAmount: String? = null, repeatUnit: String? = null): TaskItem {
        val body = mapOf("title" to title, "prompt" to prompt, "scheduleKind" to scheduleKind, "scheduleAt" to scheduleAt, "repeatAmount" to repeatAmount, "repeatUnit" to repeatUnit)
        val response: TaskDetailResponse = request(APIEndpoints.Mobile.Task.create(gatewayId), body)
        return response.task
    }

    suspend fun updateTask(gatewayId: String, taskId: String, enabled: Boolean? = null, title: String? = null, prompt: String? = null): TaskItem {
        val body = mutableMapOf<String, Any?>()
        if (enabled != null) body["enabled"] = enabled
        if (title != null) body["title"] = title
        if (prompt != null) body["prompt"] = prompt
        val response: TaskDetailResponse = request(APIEndpoints.Mobile.Task.update(gatewayId, taskId), body)
        return response.task
    }

    suspend fun deleteTask(gatewayId: String, taskId: String) {
        request<EmptyResponse>(APIEndpoints.Mobile.Task.delete(gatewayId, taskId))
    }

    // ── Backups ──────────────────────────────────────────────────────────

    suspend fun fetchBackups(gatewayId: String): List<BackupItem> {
        val body: Map<String, List<BackupItem>>? = null
        val response = httpClient.request(buildUrl(APIEndpoints.Mobile.Backup.list(gatewayId))) {
            method = HttpMethod.Get
            if (accessToken.isNotBlank()) header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        return try {
            val wrapper = response.body<Map<String, List<BackupItem>>>()
            wrapper["backups"] ?: emptyList()
        } catch (_: Exception) {
            try { response.body<List<BackupItem>>() } catch (_: Exception) { emptyList() }
        }
    }

    suspend fun createBackup(gatewayId: String, label: String, note: String? = null): BackupItem {
        val body = mapOf("label" to label, "note" to note)
        val response: Map<String, BackupItem> = request(APIEndpoints.Mobile.Backup.create(gatewayId), body)
        return response["backup"] ?: throw RelayAPIError.InvalidResponse
    }

    suspend fun deleteBackup(gatewayId: String, backupId: String) {
        request<EmptyResponse>(APIEndpoints.Mobile.Backup.delete(gatewayId, backupId))
    }

    suspend fun restoreBackup(gatewayId: String, backupId: String) {
        request<EmptyResponse>(APIEndpoints.Mobile.Backup.restore(gatewayId, backupId))
    }

    // ── Logs ─────────────────────────────────────────────────────────────

    suspend fun fetchLogs(gatewayId: String, limit: Int = 200): LogTailResponse {
        val endpoint = APIEndpoints.Mobile.Log.tail(gatewayId)
        val url = buildUrl(endpoint) + (if (endpoint.queryItems.isEmpty()) "?" else "&") + "limit=$limit"
        val response = httpClient.request(url) {
            method = HttpMethod.Get
            if (accessToken.isNotBlank()) header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        return response.body()
    }

    fun close() {
        httpClient.close()
    }
}
