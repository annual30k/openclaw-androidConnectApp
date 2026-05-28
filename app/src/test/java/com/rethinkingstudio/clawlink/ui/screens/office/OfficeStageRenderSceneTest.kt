package com.rethinkingstudio.clawlink.ui.screens.office

import com.rethinkingstudio.clawlink.core.models.OfficeActivityKind
import com.rethinkingstudio.clawlink.core.models.OfficeAgentSnapshot
import com.rethinkingstudio.clawlink.core.models.OfficeSceneSnapshot
import com.rethinkingstudio.clawlink.core.models.gateway.AggregateStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class OfficeStageRenderSceneTest {
    @Test
    fun renderSceneKeepsOnlyFocusAgentWhileSelectedGatewayIsWorking() {
        val selected = agent(
            id = "selected",
            activityKind = OfficeActivityKind.EXECUTING,
            isSelected = true
        )
        val idle = agent(
            id = "idle",
            activityKind = OfficeActivityKind.IDLE
        )
        val scene = OfficeSceneSnapshot(agents = listOf(idle, selected))

        val renderScene = officeStageRenderScene(scene)

        assertEquals(listOf("selected"), renderScene.agents.map { it.id })
    }

    @Test
    fun renderSceneFallsBackToFirstAgentWhenSelectionIsMissing() {
        val first = agent(id = "first", activityKind = OfficeActivityKind.IDLE)
        val second = agent(id = "second", activityKind = OfficeActivityKind.EXECUTING)
        val scene = OfficeSceneSnapshot(agents = listOf(first, second))

        val renderScene = officeStageRenderScene(scene)

        assertEquals(listOf("first"), renderScene.agents.map { it.id })
    }

    private fun agent(
        id: String,
        activityKind: OfficeActivityKind,
        isSelected: Boolean = false,
        aggregateStatus: AggregateStatus = AggregateStatus.online
    ) = OfficeAgentSnapshot(
        id = id,
        gatewayId = id,
        displayName = id,
        platform = "macOS",
        currentModel = "gpt",
        contextUsage = "--",
        aggregateStatus = aggregateStatus,
        mobileControlStatus = null,
        activityKind = activityKind,
        activityTitle = activityKind.name,
        activityDetail = "",
        activityPhase = null,
        activityToolName = null,
        activityToolCallId = null,
        activityProgress = null,
        activityUpdatedAt = null,
        isSelected = isSelected,
        motionSeed = 0.1
    )
}
