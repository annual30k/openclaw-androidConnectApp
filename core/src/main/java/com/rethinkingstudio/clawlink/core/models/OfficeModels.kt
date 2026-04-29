package com.rethinkingstudio.clawlink.core.models

import kotlinx.serialization.Serializable

@Serializable
data class OfficeActivity(
    val kind: String? = null,
    val title: String? = null,
    val detail: String? = null,
    val phase: String? = null,
    val toolName: String? = null,
    val toolCallId: String? = null,
    val progress: Double? = null,
    val updatedAt: String? = null
)

data class OfficeNPC(
    val id: String,
    val displayName: String,
    val x: Float,
    val y: Float,
    val spriteSheet: String,
    val animationState: String = "idle"
)

data class OfficeSceneConfig(
    val backgroundAsset: String = "OfficeBackground",
    val npcPositions: List<OfficeNPC> = emptyList(),
    val width: Int = 320,
    val height: Int = 180
)
