package com.rethinkingstudio.clawlink.core.network

import com.rethinkingstudio.clawlink.core.models.SessionCredentials
import com.rethinkingstudio.clawlink.core.models.backups.BackupDraft
import com.rethinkingstudio.clawlink.core.models.backups.BackupItem
import com.rethinkingstudio.clawlink.core.models.catalog.ModelItem
import com.rethinkingstudio.clawlink.core.models.chat.ChatSessionItem
import com.rethinkingstudio.clawlink.core.models.chat.ToolDetailResponse
import com.rethinkingstudio.clawlink.core.models.gateway.GatewaySummary
import com.rethinkingstudio.clawlink.core.models.skills.SkillItem
import com.rethinkingstudio.clawlink.core.models.tasks.TaskDraft
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
    private val gatewayLoadHeaders = mapOf(
        "X-ClawLink-Client" to "android",
        "X-ClawLink-Fast-Gateway-Retry" to "1"
    )

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
            socketTimeoutMillis = 30_000
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

    private suspend inline fun <reified T> request(
        endpoint: APIEndpoint,
        body: Any? = null,
        token: String? = null,
        extraHeaders: Map<String, String> = emptyMap()
    ): T {
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
            extraHeaders.forEach { (name, value) ->
                header(name, value)
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

    suspend fun register(
        name: String,
        email: String,
        password: String,
        deviceId: String,
        legalConsent: LegalConsentRequest
    ): RegistrationResult {
        val req = AuthRequest(
            name = name,
            email = email,
            password = password,
            deviceId = deviceId,
            legalConsent = legalConsent
        )
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

    suspend fun requestPasswordReset(email: String, deviceId: String): PasswordResetResponse {
        val req = PasswordResetRequest(email = email, deviceId = deviceId)
        return request(APIEndpoints.Auth.requestPasswordReset, req)
    }

    suspend fun confirmPasswordReset(email: String, code: String, newPassword: String) {
        val req = PasswordResetConfirmRequest(email = email, code = code, newPassword = newPassword)
        request<EmptyResponse>(APIEndpoints.Auth.confirmPasswordReset, req)
    }

    suspend fun changePassword(currentPassword: String, newPassword: String) {
        val req = ChangePasswordRequest(currentPassword = currentPassword, newPassword = newPassword)
        request<EmptyResponse>(APIEndpoints.Auth.changePassword, req)
    }

    suspend fun deleteAccount() {
        request<EmptyResponse>(APIEndpoints.Auth.deleteAccount)
    }

    suspend fun pairGateway(gatewayId: String?, accessCode: String, gatewayType: String?, deviceId: String): SessionCredentials {
        val req = PairRequest(gatewayId = gatewayId, accessCode = accessCode, gatewayType = gatewayType, deviceId = deviceId)
        val response: LoginResponse = request(APIEndpoints.Auth.pairGateway, req)
        return SessionCredentials(accessToken = response.accessToken, relayBaseURL = baseUrl)
    }

    // ── Gateways ─────────────────────────────────────────────────────────

    suspend fun fetchGateways(): List<GatewaySummary> {
        val response: GatewayListResponse = request(APIEndpoints.Mobile.Gateway.list())
        return response.gateways.map { it.toGatewaySummary() }
    }

    suspend fun deleteGateway(gatewayId: String) {
        request<EmptyResponse>(APIEndpoints.Mobile.Gateway.delete(gatewayId))
    }

    suspend fun fetchModels(gatewayId: String): List<ModelItem> {
        val response: ModelListResponse = request(APIEndpoints.Mobile.Gateway.models(gatewayId))
        return response.items
    }

    suspend fun selectModel(gatewayId: String, providerId: String, modelId: String, modelAlias: String, modelName: String, sessionKey: String? = null) {
        val req = SelectModelRequest(providerId, modelId, modelAlias, modelName, sessionKey)
        request<EmptyResponse>(APIEndpoints.Mobile.Gateway.selectModel(gatewayId), req)
    }

    suspend fun setDefaultModel(gatewayId: String, providerId: String, modelId: String, modelAlias: String) {
        val req = DefaultModelRequest(providerId, modelId, modelAlias)
        request<EmptyResponse>(APIEndpoints.Mobile.Gateway.defaultModel(gatewayId), req)
    }

    suspend fun fetchSkills(gatewayId: String): List<SkillItem> {
        val response: SkillsListResponse = request(APIEndpoints.Mobile.Gateway.skills(gatewayId))
        return response.skills
    }

    suspend fun fetchSlashCommands(gatewayId: String, query: String, limit: Int = 16, offset: Int = 0): SlashCommandListResponse {
        return request(APIEndpoints.Mobile.Gateway.slashCommands(gatewayId, query, limit, offset))
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

    suspend fun fetchChatHistoryPage(
        gatewayId: String,
        sessionKey: String,
        limit: Int = 500,
        cursor: String? = null,
        direction: String = "older"
    ): ChatHistoryResponse {
        return request(
            endpoint = APIEndpoints.Mobile.Chat.history(gatewayId, sessionKey, limit, cursor, direction),
            extraHeaders = gatewayLoadHeaders
        )
    }

    suspend fun fetchChatHistory(gatewayId: String, sessionKey: String, limit: Int = 500): List<ChatHistoryItem> {
        return fetchChatHistoryPage(gatewayId, sessionKey, limit).items
    }

    suspend fun checkChatReady(gatewayId: String): Boolean {
        val response: GatewayChatReadyResponse = request(APIEndpoints.Mobile.Chat.ready(gatewayId))
        return response.ready
    }

    suspend fun fetchToolDetail(
        gatewayId: String,
        sessionKey: String,
        toolCallId: String,
        cursor: String? = null,
        limit: Int = 20_000
    ): ToolDetailResponse {
        return request(
            endpoint = APIEndpoints.Mobile.Chat.toolDetail(gatewayId, sessionKey, toolCallId, cursor, limit),
            extraHeaders = gatewayLoadHeaders
        )
    }

    suspend fun fetchChatSessions(gatewayId: String, limit: Int = 50, activeMinutes: Int? = null): List<ChatSessionItem> {
        val response: ChatSessionListResponse = request(
            endpoint = APIEndpoints.Mobile.Chat.sessions(gatewayId, limit, activeMinutes),
            extraHeaders = gatewayLoadHeaders
        )
        return response.items
    }

    suspend fun deleteChatSession(gatewayId: String, sessionKey: String, deleteTranscript: Boolean = false): Boolean {
        val response: ChatSessionDeleteResponse = request(APIEndpoints.Mobile.Chat.deleteSession(gatewayId, sessionKey, deleteTranscript))
        return response.ok && response.deleted
    }

    suspend fun initMobileFileUpload(
        gatewayId: String,
        sessionKey: String,
        fileName: String,
        mimeType: String,
        sizeBytes: Long,
        sha256: String,
        durationMs: Int? = null,
        imageWidth: Int? = null,
        imageHeight: Int? = null,
        senderDisplayName: String? = null,
        clientCreatedAt: String? = null,
        sourceRunId: String? = null
    ): FileUploadInitResponse {
        val req = FileUploadInitRequest(
            sessionKey = sessionKey,
            fileName = fileName,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            sha256 = sha256,
            durationMs = durationMs,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            senderDisplayName = senderDisplayName,
            clientCreatedAt = clientCreatedAt,
            sourceRunId = sourceRunId
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

    suspend fun createTask(gatewayId: String, draft: TaskDraft): TaskItem {
        val response: TaskDetailResponse = request(APIEndpoints.Mobile.Task.create(gatewayId), draft)
        return response.task
    }

    suspend fun updateTask(gatewayId: String, taskId: String, draft: TaskDraft): TaskItem {
        val response: TaskDetailResponse = request(APIEndpoints.Mobile.Task.update(gatewayId, taskId), draft)
        return response.task
    }

    suspend fun setTaskEnabled(gatewayId: String, taskId: String, enabled: Boolean): TaskItem {
        val body = mutableMapOf<String, Any?>()
        body["enabled"] = enabled
        val response: TaskDetailResponse = request(APIEndpoints.Mobile.Task.update(gatewayId, taskId), body)
        return response.task
    }

    suspend fun deleteTask(gatewayId: String, taskId: String) {
        request<EmptyResponse>(APIEndpoints.Mobile.Task.delete(gatewayId, taskId))
    }

    // ── Backups ──────────────────────────────────────────────────────────

    suspend fun fetchBackups(gatewayId: String): BackupListResponse {
        val response = httpClient.request(buildUrl(APIEndpoints.Mobile.Backup.list(gatewayId))) {
            method = HttpMethod.Get
            if (accessToken.isNotBlank()) header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        return try {
            response.body<BackupListResponse>()
        } catch (_: Exception) {
            val list = try { response.body<List<BackupItem>>() } catch (_: Exception) { emptyList() }
            BackupListResponse(backups = list)
        }
    }

    suspend fun createBackup(gatewayId: String, draft: BackupDraft): BackupMutationResponse {
        return request(APIEndpoints.Mobile.Backup.create(gatewayId), draft)
    }

    suspend fun updateBackup(gatewayId: String, backupId: String, draft: BackupDraft): BackupMutationResponse {
        return request(APIEndpoints.Mobile.Backup.update(gatewayId, backupId), draft)
    }

    suspend fun deleteBackup(gatewayId: String, backupId: String): BackupMutationResponse {
        return request(APIEndpoints.Mobile.Backup.delete(gatewayId, backupId))
    }

    suspend fun restoreBackup(gatewayId: String, backupId: String): BackupMutationResponse {
        return request(APIEndpoints.Mobile.Backup.restore(gatewayId, backupId))
    }

    // ── Logs ─────────────────────────────────────────────────────────────

    suspend fun fetchLogs(gatewayId: String, limit: Int = 300, source: String? = null): LogTailResponse {
        val endpoint = APIEndpoints.Mobile.Log.tail(gatewayId)
        val response = httpClient.request(buildUrl(endpoint)) {
            method = HttpMethod.Get
            parameter("limit", limit)
            source?.let { parameter("source", it) }
            if (accessToken.isNotBlank()) header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        return response.body()
    }

    fun close() {
        httpClient.close()
    }
}
