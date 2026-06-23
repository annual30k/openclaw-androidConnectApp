package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.gateway.AggregateStatus
import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.gateway.GatewaySummary
import com.rethinkingstudio.clawlink.core.models.gateway.GatewayType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatContextUsagePresentationTest {
    @Test
    fun hermesNewSessionWithoutUsageShowsResetContextInsteadOfGatewaySummary() {
        val state = ChatState(
            currentGatewayId = "gw_hermes",
            currentSessionKey = "session_new",
            contextUsageLinesByGatewayAndSession = mapOf(
                "gw_hermes" to mapOf("session_old" to "796k/272k (293%)")
            )
        )

        val line = state.visibleContextUsageLine(
            gateway = gateway(type = GatewayType.hermes, contextUsage = "796k/272k (293%)")
        )

        assertEquals("0/272k (0%)", line)
    }

    @Test
    fun hermesExistingSessionWithoutCachedUsageFallsBackToGatewaySummary() {
        val state = ChatState(
            currentGatewayId = "gw_hermes",
            currentSessionKey = "session_existing",
            messages = listOf(
                ChatMessage(
                    id = "assistant-history",
                    role = MessageRole.assistant,
                    content = "今天到目前为止，我主要做了这几件事。"
                )
            ),
            contextUsageLinesByGatewayAndSession = mapOf(
                "gw_hermes" to mapOf("session_old" to "0/272k (0%)")
            )
        )

        val line = state.visibleContextUsageLine(
            gateway = gateway(type = GatewayType.hermes, contextUsage = "33.6k/272k (12%)")
        )

        assertEquals("33.6k/272k (12%)", line)
    }

    @Test
    fun hermesResetOnlySessionWithoutCachedUsageKeepsZeroContext() {
        val state = ChatState(
            currentGatewayId = "gw_hermes",
            currentSessionKey = "session_reset",
            messages = listOf(
                ChatMessage(
                    id = "reset-notice",
                    role = MessageRole.assistant,
                    content = "新会话已开始。有什么需要我帮你处理的？"
                )
            )
        )

        val line = state.visibleContextUsageLine(
            gateway = gateway(type = GatewayType.hermes, contextUsage = "33.6k/272k (12%)")
        )

        assertEquals("0/272k (0%)", line)
    }

    @Test
    fun hermesShowsOnlyCurrentSessionContextUsage() {
        val state = ChatState(
            currentGatewayId = "gw_hermes",
            currentSessionKey = "session_new",
            contextUsageLinesByGatewayAndSession = mapOf(
                "gw_hermes" to mapOf(
                    "session_old" to "796k/272k (293%)",
                    "session_new" to "1.2k/272k (0%)"
                )
            )
        )

        val line = state.visibleContextUsageLine(
            gateway = gateway(type = GatewayType.hermes, contextUsage = "796k/272k (293%)")
        )

        assertEquals("1.2k/272k (0%)", line)
    }

    @Test
    fun hermesSessionUsageWithoutLimitKeepsGatewayCapacity() {
        val state = ChatState(
            currentGatewayId = "gw_hermes",
            currentSessionKey = "main",
            contextUsageLinesByGatewayAndSession = mapOf(
                "gw_hermes" to mapOf("main" to "18k")
            )
        )

        val line = state.visibleContextUsageLine(
            gateway = gateway(type = GatewayType.hermes, contextUsage = "0/272k (0%)")
        )

        assertEquals("18k/272k (7%)", line)
    }

    @Test
    fun openClawUsesSessionContextUsageWhenAvailable() {
        val state = ChatState(
            currentGatewayId = "gw_openclaw",
            currentSessionKey = "main",
            contextUsageLinesByGatewayAndSession = mapOf(
                "gw_openclaw" to mapOf("main" to "45.6k/272k (17%)")
            )
        )

        val line = state.visibleContextUsageLine(
            gateway = gateway(
                id = "gw_openclaw",
                type = GatewayType.openclaw,
                contextUsage = "718.7k/272k (264%)"
            )
        )

        assertEquals("45.6k/272k (17%)", line)
    }

    @Test
    fun openClawDoesNotLetZeroSessionUsageHideDetailedGatewayUsage() {
        val state = ChatState(
            currentGatewayId = "gw_openclaw",
            currentSessionKey = "main",
            contextUsageLinesByGatewayAndSession = mapOf(
                "gw_openclaw" to mapOf("main" to "0/272k (0%)")
            )
        )

        val line = state.visibleContextUsageLine(
            gateway = gateway(
                id = "gw_openclaw",
                type = GatewayType.openclaw,
                contextUsage = "718.7k/272k (264%)"
            )
        )

        assertEquals("718.7k/272k (264%)", line)
    }

    @Test
    fun chatPayloadStoresSessionScopedUsageLine() {
        val state = ChatState(currentGatewayId = "gw_hermes", currentSessionKey = "session_a")
        val payload = buildJsonObject {
            put("state", JsonPrimitive("completed"))
            put("gatewayId", JsonPrimitive("gw_hermes"))
            put("sessionKey", JsonPrimitive("session_a"))
            put("usage", buildJsonObject {
                put("prompt_tokens", JsonPrimitive(1200))
            })
            put("contextLimit", JsonPrimitive(272000))
        }

        val updated = state.withContextUsageFromPayload(JsonObject(emptyMap()), payload)

        assertEquals(
            "1.2k/272k (0%)",
            updated.visibleContextUsageLine(gateway(type = GatewayType.hermes, contextUsage = "796k/272k (293%)"))
        )
    }

    @Test
    fun contextUsagePayloadTreatsContextUsageNumberAsUsedTokens() {
        val state = ChatState(currentGatewayId = "gw_openclaw", currentSessionKey = "main")
        val payload = buildJsonObject {
            put("gatewayId", JsonPrimitive("gw_openclaw"))
            put("sessionKey", JsonPrimitive("main"))
            put("contextUsage", JsonPrimitive(45600))
            put("contextLimit", JsonPrimitive(272000))
        }

        val updated = state.withContextUsageFromPayload(JsonObject(emptyMap()), payload)

        assertEquals(
            "45.6k/272k (17%)",
            updated.visibleContextUsageLine(
                gateway(
                    id = "gw_openclaw",
                    type = GatewayType.openclaw,
                    contextUsage = "0/272k (0%)"
                )
            )
        )
    }

    @Test
    fun hermesContextUsagePayloadMatchesHermesSessionIdToPrefixedSessionKey() {
        val state = ChatState(currentGatewayId = "gw_hermes", currentSessionKey = "hermes:20260521_080012_48f5ae")
        val payload = buildJsonObject {
            put("gatewayId", JsonPrimitive("gw_hermes"))
            put("hermesSessionId", JsonPrimitive("20260521_080012_48f5ae"))
            put("contextUsage", JsonPrimitive(45600))
            put("contextLimit", JsonPrimitive(1_100_000))
        }

        val updated = state.withContextUsageFromPayload(JsonObject(emptyMap()), payload)

        assertEquals(
            "45.6k/1.1m (4%)",
            updated.visibleContextUsageLine(
                gateway(
                    id = "gw_hermes",
                    type = GatewayType.hermes,
                    contextUsage = "0/1.1m (0%)"
                )
            )
        )
    }

    @Test
    fun newSessionResetPayloadStoresZeroSessionScopedUsageLine() {
        val state = ChatState(
            currentGatewayId = "gw_hermes",
            currentSessionKey = "session_a",
            contextUsageLinesByGatewayAndSession = mapOf(
                "gw_hermes" to mapOf("session_a" to "796k/272k (293%)")
            )
        )
        val payload = buildJsonObject {
            put("state", JsonPrimitive("completed"))
            put("gatewayId", JsonPrimitive("gw_hermes"))
            put("sessionKey", JsonPrimitive("session_a"))
            put("contextLimit", JsonPrimitive(272000))
            put("message", buildJsonObject {
                put("role", JsonPrimitive("assistant"))
                put("content", JsonPrimitive("新会话已开始。有什么需要我帮你处理的？"))
            })
        }

        val updated = state.withContextUsageFromPayload(JsonObject(emptyMap()), payload)

        assertEquals(
            "0/272k (0%)",
            updated.visibleContextUsageLine(gateway(type = GatewayType.hermes, contextUsage = "796k/272k (293%)"))
        )
    }

    private fun gateway(
        id: String = "gw_hermes",
        type: GatewayType,
        contextUsage: String
    ): GatewaySummary = GatewaySummary(
        gatewayId = id,
        displayName = "Mac Hermes Agent",
        platform = "macOS",
        gatewayType = type,
        aggregateStatus = AggregateStatus.online,
        lastSeenAt = "now",
        currentModel = "gpt-5.5",
        contextUsage = contextUsage,
        contextLimit = 272000
    )
}
