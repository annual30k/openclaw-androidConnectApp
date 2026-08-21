package com.rethinkingstudio.clawlink.core.network

import java.net.URLEncoder

enum class HTTPMethod { GET, POST, PATCH, DELETE, PUT }

data class APIEndpoint(
    val method: HTTPMethod,
    val path: String,
    val queryItems: List<Pair<String, String>> = emptyList()
)

enum class APIOrigin(val value: String) { mobile("mobile"), host("host") }

object APIEndpoints {
    private fun pathSegment(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    private fun queryValue(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    object Auth {
        val register = APIEndpoint(HTTPMethod.POST, "/api/auth/register")
        val verifyEmail = APIEndpoint(HTTPMethod.POST, "/api/auth/verify-email")
        val login = APIEndpoint(HTTPMethod.POST, "/api/auth/login")
        val requestPasswordReset = APIEndpoint(HTTPMethod.POST, "/api/auth/password-reset/request")
        val confirmPasswordReset = APIEndpoint(HTTPMethod.POST, "/api/auth/password-reset/confirm")
        val changePassword = APIEndpoint(HTTPMethod.POST, "/api/auth/change-password")
        val deleteAccount = APIEndpoint(HTTPMethod.DELETE, "/api/auth/account")
        val pairGateway = APIEndpoint(HTTPMethod.POST, "/api/mobile/pair")
    }

    object Mobile {
        object Gateway {
            fun list() = APIEndpoint(HTTPMethod.GET, "/api/mobile/gateways")
            fun update(gatewayID: String) = APIEndpoint(HTTPMethod.PATCH, "/api/mobile/gateways/${pathSegment(gatewayID)}")
            fun delete(gatewayID: String) = APIEndpoint(HTTPMethod.DELETE, "/api/mobile/gateways/${pathSegment(gatewayID)}")
            fun models(gatewayID: String) = APIEndpoint(HTTPMethod.GET, "/api/mobile/gateways/${pathSegment(gatewayID)}/models")
            fun skills(gatewayID: String) = APIEndpoint(HTTPMethod.GET, "/api/mobile/gateways/${pathSegment(gatewayID)}/skills")
            fun slashCommands(gatewayID: String, query: String, limit: Int, offset: Int = 0) = APIEndpoint(
                HTTPMethod.GET,
                "/api/mobile/gateways/${pathSegment(gatewayID)}/slash-commands",
                listOf(
                    "query" to queryValue(query),
                    "limit" to limit.toString(),
                    "offset" to offset.toString()
                )
            )
            fun skill(gatewayID: String, skillKey: String) = APIEndpoint(HTTPMethod.PATCH, "/api/mobile/gateways/${pathSegment(gatewayID)}/skills/${pathSegment(skillKey)}")
            fun selectModel(gatewayID: String) = APIEndpoint(HTTPMethod.POST, "/api/mobile/gateways/${pathSegment(gatewayID)}/models/select")
            fun defaultModel(gatewayID: String) = APIEndpoint(HTTPMethod.POST, "/api/mobile/gateways/${pathSegment(gatewayID)}/models/default")
            fun approveSensitiveAction(gatewayID: String) = APIEndpoint(HTTPMethod.POST, "/api/mobile/gateways/${pathSegment(gatewayID)}/approve-sensitive-action")
            fun restart(gatewayID: String) = APIEndpoint(HTTPMethod.POST, "/api/mobile/gateways/${pathSegment(gatewayID)}/restart")
            fun remoteRestart(gatewayID: String) = APIEndpoint(HTTPMethod.POST, "/api/mobile/gateways/${pathSegment(gatewayID)}/remote-restart")
            fun executeAdvancedAction(gatewayID: String) = APIEndpoint(HTTPMethod.POST, "/api/mobile/gateways/${pathSegment(gatewayID)}/execute-advanced-action")
        }

        object Chat {
            fun history(
                gatewayID: String,
                sessionKey: String,
                limit: Int,
                cursor: String? = null,
                direction: String = "older"
            ): APIEndpoint {
                val queries = mutableListOf(
                    "sessionKey" to queryValue(sessionKey),
                    "limit" to limit.toString()
                )
                cursor?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    queries.add("cursor" to queryValue(it))
                }
                queries.add("direction" to queryValue(direction))
                return APIEndpoint(
                    HTTPMethod.GET,
                    "/api/mobile/gateways/${pathSegment(gatewayID)}/chat/history",
                    queries
                )
            }
            fun sync(
                gatewayID: String,
                sessionKey: String,
                limit: Int = 100,
                cursor: String? = null
            ): APIEndpoint {
                val queries = mutableListOf(
                    "sessionKey" to queryValue(sessionKey),
                    "limit" to limit.toString()
                )
                cursor?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    queries.add("cursor" to queryValue(it))
                }
                return APIEndpoint(
                    HTTPMethod.GET,
                    "/api/mobile/gateways/${pathSegment(gatewayID)}/chat/sync",
                    queries
                )
            }
            fun ready(gatewayID: String) = APIEndpoint(HTTPMethod.GET, "/api/mobile/gateways/${pathSegment(gatewayID)}/chat/ready")
            fun toolDetail(
                gatewayID: String,
                sessionKey: String,
                toolCallId: String,
                cursor: String? = null,
                limit: Int = 20_000
            ): APIEndpoint {
                val queries = mutableListOf(
                    "sessionKey" to queryValue(sessionKey),
                    "limit" to limit.toString()
                )
                cursor?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    queries.add("cursor" to queryValue(it))
                }
                return APIEndpoint(
                    HTTPMethod.GET,
                    "/api/mobile/gateways/${pathSegment(gatewayID)}/chat/tools/${pathSegment(toolCallId)}/detail",
                    queries
                )
            }
            fun sessions(gatewayID: String, limit: Int = 50, activeMinutes: Int? = null, includeGlobal: Boolean = true, includeUnknown: Boolean = false): APIEndpoint {
                val queries = mutableListOf("limit" to limit.toString(), "includeGlobal" to includeGlobal.toString(), "includeUnknown" to includeUnknown.toString())
                if (activeMinutes != null) queries.add("activeMinutes" to activeMinutes.toString())
                return APIEndpoint(HTTPMethod.GET, "/api/mobile/gateways/${pathSegment(gatewayID)}/chat/sessions", queries)
            }
            fun deleteSession(gatewayID: String, sessionKey: String, deleteTranscript: Boolean = false) = APIEndpoint(
                HTTPMethod.DELETE, "/api/mobile/gateways/${pathSegment(gatewayID)}/chat/sessions",
                listOf("sessionKey" to queryValue(sessionKey), "deleteTranscript" to deleteTranscript.toString())
            )
        }

        object Task {
            fun list(gatewayID: String) = APIEndpoint(HTTPMethod.GET, "/api/mobile/gateways/${pathSegment(gatewayID)}/tasks")
            fun create(gatewayID: String) = APIEndpoint(HTTPMethod.POST, "/api/mobile/gateways/${pathSegment(gatewayID)}/tasks")
            fun update(gatewayID: String, taskID: String) = APIEndpoint(HTTPMethod.PATCH, "/api/mobile/gateways/${pathSegment(gatewayID)}/tasks/${pathSegment(taskID)}")
            fun delete(gatewayID: String, taskID: String) = APIEndpoint(HTTPMethod.DELETE, "/api/mobile/gateways/${pathSegment(gatewayID)}/tasks/${pathSegment(taskID)}")
        }

        object Backup {
            fun list(gatewayID: String) = APIEndpoint(HTTPMethod.GET, "/api/mobile/gateways/${pathSegment(gatewayID)}/backups")
            fun create(gatewayID: String) = APIEndpoint(HTTPMethod.POST, "/api/mobile/gateways/${pathSegment(gatewayID)}/backups")
            fun update(gatewayID: String, backupID: String) = APIEndpoint(HTTPMethod.PATCH, "/api/mobile/gateways/${pathSegment(gatewayID)}/backups/${pathSegment(backupID)}")
            fun delete(gatewayID: String, backupID: String) = APIEndpoint(HTTPMethod.DELETE, "/api/mobile/gateways/${pathSegment(gatewayID)}/backups/${pathSegment(backupID)}")
            fun restore(gatewayID: String, backupID: String) = APIEndpoint(HTTPMethod.POST, "/api/mobile/gateways/${pathSegment(gatewayID)}/backups/${pathSegment(backupID)}/restore")
        }

        object File {
            fun initUpload(origin: APIOrigin, gatewayID: String) = APIEndpoint(HTTPMethod.POST, "/api/${origin.value}/gateways/${pathSegment(gatewayID)}/files/init")
            fun uploadChunk(origin: APIOrigin, uploadID: String, chunkIndex: Int) = APIEndpoint(HTTPMethod.PUT, "/api/${origin.value}/files/$uploadID/chunks/$chunkIndex")
            fun completeUpload(origin: APIOrigin, uploadID: String) = APIEndpoint(HTTPMethod.POST, "/api/${origin.value}/files/$uploadID/complete")
            fun download(fileID: String) = APIEndpoint(HTTPMethod.GET, "/api/mobile/files/${pathSegment(fileID)}")
        }

        object Log {
            fun tail(gatewayID: String) = APIEndpoint(HTTPMethod.GET, "/api/mobile/gateways/${pathSegment(gatewayID)}/logs")
        }
    }

    object Socket {
        const val mobile = "/mobile/ws"
    }
}
