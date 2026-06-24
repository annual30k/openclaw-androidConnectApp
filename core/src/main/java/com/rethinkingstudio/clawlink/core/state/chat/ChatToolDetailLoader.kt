package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.network.RelayAPIClient
import com.rethinkingstudio.clawlink.core.network.RelayAPIError
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
import kotlinx.coroutines.CancellationException

internal suspend fun loadChatToolDetail(
    apiClient: RelayAPIClient,
    gatewayId: String,
    sessionKey: String,
    toolCallId: String,
    cursor: String?,
    limit: Int,
    getState: () -> ChatState,
    setState: (ChatState) -> Unit
): Boolean {
    val normalizedGatewayId = gatewayId.trim()
    val normalizedSessionKey = sessionKey.trim().ifBlank { defaultSessionKey }
    val normalizedToolCallId = toolCallId.trim()
    if (normalizedGatewayId.isBlank() || normalizedSessionKey.isBlank() || normalizedToolCallId.isBlank()) return false

    val cacheKey = toolDetailCacheKey(normalizedGatewayId, normalizedSessionKey, normalizedToolCallId)
    val existing = getState().toolDetailCacheByKey[cacheKey]
    // 工具详情可被多个 UI 入口重复触发；以规范化后的三元组做稳定身份，避免并发或重入请求覆盖已完成结果。
    if (existing?.isLoading == true || existing?.response != null || existing?.issueMessage != null) {
        return existing?.response != null
    }

    setState(
        getState().copy(
            toolDetailCacheByKey = getState().toolDetailCacheByKey + (cacheKey to ToolDetailCacheEntry.Loading)
        )
    )

    return try {
        val response = apiClient.fetchToolDetail(
            gatewayId = normalizedGatewayId,
            sessionKey = normalizedSessionKey,
            toolCallId = normalizedToolCallId,
            cursor = cursor,
            limit = limit
        )
        val entry = if (response.expired || !response.hasFullDetail) {
            ToolDetailCacheEntry.unavailable(fullToolOutputUnavailableMessage)
        } else {
            ToolDetailCacheEntry.loaded(response)
        }
        setState(
            getState().copy(
                toolDetailCacheByKey = getState().toolDetailCacheByKey + (cacheKey to entry)
            )
        )
        entry.response != null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val unavailable = e is RelayAPIError.ServerError && e.statusCode in listOf(404, 410)
        val message = if (unavailable) {
            fullToolOutputUnavailableMessage
        } else {
            e.message?.takeIf { it.isNotBlank() } ?: choose("Failed to load tool output.", "加载工具输出失败。")
        }
        setState(
            getState().copy(
                toolDetailCacheByKey = getState().toolDetailCacheByKey + (cacheKey to ToolDetailCacheEntry.unavailable(message))
            )
        )
        false
    }
}

private const val fullToolOutputUnavailableMessage = "完整输出不可用"
