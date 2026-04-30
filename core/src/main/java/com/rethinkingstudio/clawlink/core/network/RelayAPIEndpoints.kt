package com.rethinkingstudio.clawlink.core.network

enum class HTTPMethod { GET, POST, PATCH, DELETE, PUT }

data class APIEndpoint(
    val method: HTTPMethod,
    val path: String,
    val queryItems: List<Pair<String, String>> = emptyList()
)

enum class APIOrigin(val value: String) { mobile("mobile"), host("host") }

object APIEndpoints {
    object Auth {
        val register = APIEndpoint(HTTPMethod.POST, "/api/auth/register")
        val verifyEmail = APIEndpoint(HTTPMethod.POST, "/api/auth/verify-email")
        val login = APIEndpoint(HTTPMethod.POST, "/api/auth/login")
        val deleteAccount = APIEndpoint(HTTPMethod.DELETE, "/api/auth/account")
        val pairGateway = APIEndpoint(HTTPMethod.POST, "/api/mobile/pair")
    }

    object Mobile {
        object Gateway {
            fun list() = APIEndpoint(HTTPMethod.GET, "/api/mobile/gateways")
            fun update(gatewayID: String) = APIEndpoint(HTTPMethod.PATCH, "/api/mobile/gateways/$gatewayID")
            fun delete(gatewayID: String) = APIEndpoint(HTTPMethod.DELETE, "/api/mobile/gateways/$gatewayID")
            fun models(gatewayID: String) = APIEndpoint(HTTPMethod.GET, "/api/mobile/gateways/$gatewayID/models")
            fun skills(gatewayID: String) = APIEndpoint(HTTPMethod.GET, "/api/mobile/gateways/$gatewayID/skills")
            fun skill(gatewayID: String, skillKey: String) = APIEndpoint(HTTPMethod.PATCH, "/api/mobile/gateways/$gatewayID/skills/$skillKey")
            fun selectModel(gatewayID: String) = APIEndpoint(HTTPMethod.POST, "/api/mobile/gateways/$gatewayID/models/select")
            fun defaultModel(gatewayID: String) = APIEndpoint(HTTPMethod.POST, "/api/mobile/gateways/$gatewayID/models/default")
            fun approveSensitiveAction(gatewayID: String) = APIEndpoint(HTTPMethod.POST, "/api/mobile/gateways/$gatewayID/approve-sensitive-action")
            fun restart(gatewayID: String) = APIEndpoint(HTTPMethod.POST, "/api/mobile/gateways/$gatewayID/restart")
            fun remoteRestart(gatewayID: String) = APIEndpoint(HTTPMethod.POST, "/api/mobile/gateways/$gatewayID/remote-restart")
            fun executeAdvancedAction(gatewayID: String) = APIEndpoint(HTTPMethod.POST, "/api/mobile/gateways/$gatewayID/execute-advanced-action")
        }

        object Chat {
            fun history(gatewayID: String, sessionKey: String, limit: Int) = APIEndpoint(
                HTTPMethod.GET, "/api/mobile/gateways/$gatewayID/chat/history",
                listOf("sessionKey" to sessionKey, "limit" to limit.toString())
            )
            fun ready(gatewayID: String) = APIEndpoint(HTTPMethod.GET, "/api/mobile/gateways/$gatewayID/chat/ready")
            fun sessions(gatewayID: String, limit: Int = 50, activeMinutes: Int? = null, includeGlobal: Boolean = true, includeUnknown: Boolean = false): APIEndpoint {
                val queries = mutableListOf("limit" to limit.toString(), "includeGlobal" to includeGlobal.toString(), "includeUnknown" to includeUnknown.toString())
                if (activeMinutes != null) queries.add("activeMinutes" to activeMinutes.toString())
                return APIEndpoint(HTTPMethod.GET, "/api/mobile/gateways/$gatewayID/chat/sessions", queries)
            }
            fun deleteSession(gatewayID: String, sessionKey: String, deleteTranscript: Boolean = false) = APIEndpoint(
                HTTPMethod.DELETE, "/api/mobile/gateways/$gatewayID/chat/sessions",
                listOf("sessionKey" to sessionKey, "deleteTranscript" to deleteTranscript.toString())
            )
        }

        object Task {
            fun list(gatewayID: String) = APIEndpoint(HTTPMethod.GET, "/api/mobile/gateways/$gatewayID/tasks")
            fun create(gatewayID: String) = APIEndpoint(HTTPMethod.POST, "/api/mobile/gateways/$gatewayID/tasks")
            fun update(gatewayID: String, taskID: String) = APIEndpoint(HTTPMethod.PATCH, "/api/mobile/gateways/$gatewayID/tasks/$taskID")
            fun delete(gatewayID: String, taskID: String) = APIEndpoint(HTTPMethod.DELETE, "/api/mobile/gateways/$gatewayID/tasks/$taskID")
        }

        object Backup {
            fun list(gatewayID: String) = APIEndpoint(HTTPMethod.GET, "/api/mobile/gateways/$gatewayID/backups")
            fun create(gatewayID: String) = APIEndpoint(HTTPMethod.POST, "/api/mobile/gateways/$gatewayID/backups")
            fun update(gatewayID: String, backupID: String) = APIEndpoint(HTTPMethod.PATCH, "/api/mobile/gateways/$gatewayID/backups/$backupID")
            fun delete(gatewayID: String, backupID: String) = APIEndpoint(HTTPMethod.DELETE, "/api/mobile/gateways/$gatewayID/backups/$backupID")
            fun restore(gatewayID: String, backupID: String) = APIEndpoint(HTTPMethod.POST, "/api/mobile/gateways/$gatewayID/backups/$backupID/restore")
        }

        object File {
            fun initUpload(origin: APIOrigin, gatewayID: String) = APIEndpoint(HTTPMethod.POST, "/api/${origin.value}/gateways/$gatewayID/files/init")
            fun uploadChunk(origin: APIOrigin, uploadID: String, chunkIndex: Int) = APIEndpoint(HTTPMethod.PUT, "/api/${origin.value}/files/$uploadID/chunks/$chunkIndex")
            fun completeUpload(origin: APIOrigin, uploadID: String) = APIEndpoint(HTTPMethod.POST, "/api/${origin.value}/files/$uploadID/complete")
            fun download(fileID: String) = APIEndpoint(HTTPMethod.GET, "/api/mobile/files/$fileID")
        }

        object Log {
            fun tail(gatewayID: String) = APIEndpoint(HTTPMethod.GET, "/api/mobile/gateways/$gatewayID/logs")
        }
    }

    object Socket {
        const val mobile = "/mobile/ws"
    }
}
