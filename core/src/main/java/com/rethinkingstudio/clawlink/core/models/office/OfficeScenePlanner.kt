package com.rethinkingstudio.clawlink.core.models.office

import com.rethinkingstudio.clawlink.core.models.*
import com.rethinkingstudio.clawlink.core.models.gateway.AggregateStatus
import com.rethinkingstudio.clawlink.core.models.gateway.GatewaySummary
import java.util.*

object OfficeScenePlanner {
    fun scene(
        gateways: List<GatewaySummary>,
        selectedGatewayId: String?,
        pendingRuns: Map<String, List<Any>> = emptyMap() // Simplified pending runs for now
    ): OfficeSceneSnapshot {
        val agents = gateways
            .map { snapshot(it, selectedGatewayId, pendingRuns[it.id]?.isNotEmpty() ?: false) }
            .sortedWith(compareByDescending<OfficeAgentSnapshot> { it.isSelected }
                .thenBy { activityPriority(it.activityKind, it.aggregateStatus) }
                .thenBy { it.displayName })
        return OfficeSceneSnapshot(agents)
    }

    private fun snapshot(
        gateway: GatewaySummary,
        selectedGatewayId: String?,
        hasPendingRun: Boolean
    ): OfficeAgentSnapshot {
        val activityKind = resolveActivityKind(gateway, hasPendingRun)
        val activityTitle = resolveActivityTitle(gateway, activityKind)
        val activityDetail = resolveActivityDetail(gateway, activityKind)

        return OfficeAgentSnapshot(
            id = gateway.id,
            gatewayId = gateway.id,
            displayName = gateway.displayName,
            platform = gateway.platform,
            currentModel = gateway.currentModel,
            contextUsage = gateway.contextUsage,
            aggregateStatus = gateway.aggregateStatus,
            mobileControlStatus = normalizedValue(gateway.mobileControlStatus),
            activityKind = activityKind,
            activityTitle = activityTitle,
            activityDetail = activityDetail,
            activityPhase = normalizedValue(gateway.officeActivityPhase),
            activityToolName = normalizedValue(gateway.officeActivityToolName),
            activityToolCallId = normalizedValue(gateway.officeActivityToolCallId),
            activityProgress = gateway.officeActivityProgress,
            activityUpdatedAt = normalizedValue(gateway.officeActivityUpdatedAt),
            isSelected = gateway.id == selectedGatewayId,
            motionSeed = stableMotionSeed(gateway.id)
        )
    }

    private fun resolveActivityKind(gateway: GatewaySummary, hasPendingRun: Boolean): OfficeActivityKind {
        if (gateway.aggregateStatus == AggregateStatus.offline) {
            return OfficeActivityKind.SLEEPING
        }

        val mobileControlStatus = normalizedValue(gateway.mobileControlStatus)?.lowercase() ?: ""
        if (isSleepLike(mobileControlStatus)) {
            return OfficeActivityKind.SLEEPING
        }

        if (hasPendingRun) {
            return OfficeActivityKind.EXECUTING
        }

        normalizedValue(gateway.officeActivityKind)?.lowercase()?.let { explicit ->
            explicitActivityKind(explicit)?.let { return it }
        }

        val phase = normalizedValue(gateway.officeActivityPhase)?.lowercase() ?: ""
        val title = normalizedValue(gateway.officeActivityTitle)?.lowercase() ?: ""
        val detail = normalizedValue(gateway.officeActivityDetail)?.lowercase() ?: ""
        val toolName = normalizedValue(gateway.officeActivityToolName)?.lowercase() ?: ""
        val toolCallId = normalizedValue(gateway.officeActivityToolCallId)?.lowercase() ?: ""

        if (isSleepLike(phase) || isSleepLike(title) || isSleepLike(detail)) {
            return OfficeActivityKind.SLEEPING
        }

        if (phase.contains("error") || phase.contains("fail") ||
            title.contains("error") || detail.contains("error") ||
            title.contains("异常") || detail.contains("异常") ||
            title.contains("失败") || detail.contains("失败")
        ) {
            return OfficeActivityKind.ERROR
        }

        if (phase.contains("sync") || title.contains("sync") || detail.contains("sync") ||
            title.contains("同步") || detail.contains("同步")
        ) {
            return OfficeActivityKind.SYNCING
        }

        if (toolName.contains("search") || toolName.contains("browse") ||
            toolName.contains("web") || toolName.contains("research") ||
            title.contains("检索") || detail.contains("检索") ||
            title.contains("搜索") || detail.contains("搜索")
        ) {
            return OfficeActivityKind.RESEARCHING
        }

        if (phase.contains("execut") || phase.contains("running") ||
            title.contains("execut") || detail.contains("execut") ||
            title.contains("执行") || detail.contains("执行") ||
            title.contains("运行") || detail.contains("运行")
        ) {
            return OfficeActivityKind.EXECUTING
        }

        if (toolName.contains("code") || toolName.contains("shell") ||
            toolName.contains("run") || toolName.contains("exec") ||
            toolName.contains("terminal") || toolCallId.isNotEmpty()
        ) {
            return OfficeActivityKind.EXECUTING
        }

        if (phase.contains("stream") || phase.contains("delta") ||
            phase.contains("progress") || phase.contains("writing") ||
            title.contains("写作") || detail.contains("写作") ||
            title.contains("生成") || detail.contains("生成") ||
            title.contains("回复") || detail.contains("回复")
        ) {
            return OfficeActivityKind.WRITING
        }

        if (gateway.aggregateStatus == AggregateStatus.partial) {
            return OfficeActivityKind.SYNCING
        }

        return if (gateway.aggregateStatus == AggregateStatus.connecting) OfficeActivityKind.SYNCING else OfficeActivityKind.IDLE
    }

    private fun resolveActivityTitle(gateway: GatewaySummary, kind: OfficeActivityKind): String {
        val title = normalizedValue(gateway.officeActivityTitle) ?: kind.name.lowercase().replaceFirstChar { it.uppercase() }
        
        if ((kind == OfficeActivityKind.EXECUTING || kind == OfficeActivityKind.WRITING) && 
            (title == "待命" || title == "待命中")) {
            return "工作中" // Simplified L("office.activity.working")
        }
        
        return title
    }

    private fun resolveActivityDetail(gateway: GatewaySummary, kind: OfficeActivityKind): String {
        val detail = normalizedValue(gateway.officeActivityDetail)
        
        if (detail != null && (kind == OfficeActivityKind.EXECUTING || kind == OfficeActivityKind.WRITING) && 
            (detail == "等待下一次任务" || detail == "等待下一条任务")) {
            // Skip
        } else if (detail != null) {
            return detail
        }

        return when (kind) {
            OfficeActivityKind.IDLE -> {
                if (gateway.currentModel != "--" && gateway.currentModel.isNotBlank()) {
                    "模型 ${gateway.currentModel} 正在待命"
                } else {
                    "等待下一次任务"
                }
            }
            OfficeActivityKind.WRITING -> "正在组织回复和状态"
            OfficeActivityKind.RESEARCHING -> "正在查找资料和上下文"
            OfficeActivityKind.EXECUTING -> "正在执行工具和动作"
            OfficeActivityKind.SYNCING -> "正在同步运行状态"
            OfficeActivityKind.SLEEPING -> "Gateway 已离线，正在床上休息"
            OfficeActivityKind.OFFLINE -> "主机暂时离线"
            OfficeActivityKind.ERROR -> "需要检查连接或任务状态"
        }
    }

    private fun stableMotionSeed(gatewayId: String): Double {
        var seed = 1469598103934665603L
        for (byte in gatewayId.toByteArray()) {
            seed = (seed xor byte.toLong()) * 1099511628211L
        }
        return (seed.coerceAtLeast(0) % 10000).toDouble() / 10000.0
    }

    private fun activityPriority(kind: OfficeActivityKind, status: AggregateStatus): Int {
        return when (kind) {
            OfficeActivityKind.EXECUTING -> 0
            OfficeActivityKind.WRITING -> 1
            OfficeActivityKind.RESEARCHING -> 2
            OfficeActivityKind.SYNCING -> 3
            OfficeActivityKind.ERROR -> 4
            OfficeActivityKind.SLEEPING -> 5
            OfficeActivityKind.IDLE -> when (status) {
                AggregateStatus.online -> 6
                AggregateStatus.connecting -> 7
                AggregateStatus.partial -> 8
                else -> 6
            }
            OfficeActivityKind.OFFLINE -> 9
        }
    }

    private fun normalizedValue(value: String?): String? {
        val trimmed = value?.trim() ?: ""
        return if (trimmed.isEmpty()) null else trimmed
    }

    private fun explicitActivityKind(rawValue: String): OfficeActivityKind? {
        val normalized = rawValue.lowercase()
        if (isSleepLike(normalized)) {
            return OfficeActivityKind.SLEEPING
        }
        return try {
            OfficeActivityKind.valueOf(normalized.uppercase())
        } catch (e: Exception) {
            null
        }
    }

    private fun isSleepLike(value: String): Boolean {
        return value.contains("sleep") || value.contains("休眠") || value.contains("睡")
    }
}
