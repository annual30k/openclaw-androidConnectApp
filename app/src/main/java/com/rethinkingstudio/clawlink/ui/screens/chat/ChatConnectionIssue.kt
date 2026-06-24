package com.rethinkingstudio.clawlink.ui.screens.chat

import com.rethinkingstudio.clawlink.core.models.gateway.AggregateStatus
import com.rethinkingstudio.clawlink.core.models.gateway.GatewayType

internal fun chatConnectionIssueMessage(
    hasSelectedGateway: Boolean,
    appRelayStatus: AggregateStatus,
    isChatChainReady: Boolean,
    selectedGatewayType: GatewayType
): String? {
    return when {
        !hasSelectedGateway -> null
        appRelayStatus == AggregateStatus.offline -> "无法连接到 Relay 服务器，请确认服务已启动且地址可访问。"
        !isChatChainReady -> if (selectedGatewayType == GatewayType.hermes) {
            "当前链路未全通，请确认 Relay 已连接到主机且 Hermes Agent 已启动。"
        } else {
            "当前链路未全通，请确认 Relay 已连接到主机且 OpenClaw 已启动。"
        }
        else -> null
    }
}
