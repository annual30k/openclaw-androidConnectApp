package com.rethinkingstudio.clawlink.ui.screens.office.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.core.models.OfficeActivityKind
import com.rethinkingstudio.clawlink.core.models.OfficeAgentSnapshot
import com.rethinkingstudio.clawlink.core.models.OfficeSceneSnapshot
import com.rethinkingstudio.clawlink.core.models.OfficeStation
import com.rethinkingstudio.clawlink.core.models.PointF
import com.rethinkingstudio.clawlink.core.models.gateway.AggregateStatus
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
import com.rethinkingstudio.clawlink.ui.screens.office.logic.OfficeNPCCast
import com.rethinkingstudio.clawlink.ui.screens.office.logic.OfficeRoutePlanner
import com.rethinkingstudio.clawlink.ui.screens.office.logic.OfficeSceneMotionState
import com.rethinkingstudio.clawlink.ui.screens.office.logic.positiveModulo
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

internal val OfficeSceneLetterboxColor = Color(0xFFEDE3CC)

internal fun DrawScope.drawStage(
    bitmaps: Map<Int, Bitmap>,
    scene: OfficeSceneSnapshot,
    motionState: OfficeSceneMotionState,
    officeNpcs: List<com.rethinkingstudio.clawlink.ui.screens.office.logic.OfficeNPC>,
    timeMillis: Long,
    reduceMotion: Boolean,
    showsOccupants: Boolean
) {
    bitmaps[R.drawable.office_background]?.let {
        drawContext.canvas.nativeCanvas.drawBitmap(it, null, Rect(0, 0, 1280, 720), null)
    }

    drawFurniture(bitmaps, scene, timeMillis, reduceMotion, showsOccupants)
    if (showsOccupants) {
        drawNPCs(officeNpcs, timeMillis)
        drawAgents(bitmaps, scene, motionState, timeMillis, reduceMotion)
    }
    drawDeskOverlay(bitmaps)
    drawOfficeTitlePlaque()
}

internal fun DrawScope.drawFurniture(
    bitmaps: Map<Int, Bitmap>,
    scene: OfficeSceneSnapshot,
    timeMillis: Long,
    reduceMotion: Boolean,
    showsOccupants: Boolean
) {
    drawSpriteFrame(bitmaps[R.drawable.office_posters_sheet], 160, 160, 14, timeMillis, 1.0, centerRect(252f, 66f, 160f, 160f, 0.95f), reduceMotion)
    drawSpriteFrame(bitmaps[R.drawable.office_plants_sheet], 160, 160, 3, timeMillis, 1.0, centerRect(230f, 185f, 160f, 160f, 0.92f), reduceMotion)
    drawSpriteFrame(bitmaps[R.drawable.office_plants_sheet], 160, 160, 1, timeMillis, 1.0, centerRect(565f, 178f, 160f, 160f, 0.92f), reduceMotion)

    bitmaps[R.drawable.office_coffee_machine_shadow]?.let {
        drawContext.canvas.nativeCanvas.drawBitmap(it, null, centerRect(659f, 397f, 230f, 230f, 0.85f), null)
    }
    drawSpriteFrame(bitmaps[R.drawable.office_coffee_machine_sheet], 230, 230, null, timeMillis, 12.0, centerRect(659f, 397f, 230f, 230f, 0.85f), reduceMotion)

    val shouldDrawDeskUnderlay = !scene.agents.any {
        it.activityKind != OfficeActivityKind.IDLE &&
            it.activityKind != OfficeActivityKind.SLEEPING &&
            it.activityKind != OfficeActivityKind.OFFLINE &&
            it.activityKind != OfficeActivityKind.SYNCING
    }
    if (shouldDrawDeskUnderlay) {
        drawDeskOverlay(bitmaps)
    }

    drawSpriteFrame(bitmaps[R.drawable.office_plants_sheet], 160, 160, 9, timeMillis, 1.0, centerRect(310f, 390f, 160f, 160f, 0.82f), reduceMotion)

    bitmaps[R.drawable.office_sofa]?.let {
        drawContext.canvas.nativeCanvas.drawBitmap(it, null, centerRect(798f, 272f, 256f, 256f, 1.0f), null)
    }

    drawSpriteFrame(bitmaps[R.drawable.office_serverroom_sheet], 180, 251, null, timeMillis, 5.5, centerRect(1021f, 142f, 180f, 251f, 1.0f), reduceMotion)
    if (showsOccupants) {
        drawSpriteFrame(bitmaps[R.drawable.office_cats_sheet], 160, 160, 6, timeMillis, 1.0, centerRect(94f, 557f, 160f, 160f, 0.94f), reduceMotion)
    }
}

internal fun DrawScope.drawNPCs(
    officeNpcs: List<com.rethinkingstudio.clawlink.ui.screens.office.logic.OfficeNPC>,
    timeMillis: Long
) {
    officeNpcs.forEach { npc ->
        val snapshot = npc.snapshotAt(timeMillis / 1000.0)
        val npcTime = timeMillis + npc.id * 310L
        if (npc.id == 0) {
            drawOfficeCourierNpc(
                position = snapshot.position,
                isWaiting = snapshot.isWaiting,
                direction = snapshot.facingDirection,
                timeMillis = npcTime
            )
        } else {
            drawOfficeFileCartNpc(
                position = snapshot.position,
                isWaiting = snapshot.isWaiting,
                direction = snapshot.facingDirection,
                timeMillis = npcTime
            )
        }
    }
}

internal fun DrawScope.drawOfficeCourierNpc(
    position: PointF,
    isWaiting: Boolean,
    direction: Float,
    timeMillis: Long
) {
    val step = if (isWaiting) 0 else ((timeMillis / 120L) % 4L).toInt()
    val idleStep = if (isWaiting) ((timeMillis / 460L) % 2L).toInt() else 0
    val x = position.x.roundToInt()
    val y = position.y.roundToInt() - idleStep
    val outline = Color(0xFF3B2A1F)
    val shadow = Color(0x6633281F)
    val cream = Color(0xFFF0DDB8)
    val panel = Color(0xFFB9895A)
    val panelDark = Color(0xFF7D5239)
    val tealDark = Color(0xFF224B4A)
    val amber = Color(0xFFF2C94C)
    val metal = Color(0xFF6C716D)
    val tire = Color(0xFF2D2D2A)
    val armA = if (step == 1 || step == 2) 1 else -1
    val wheelMark = when (step) {
        0 -> -2
        1 -> 0
        2 -> 2
        else -> 0
    }

    drawRect(shadow, Offset(x - 28f, y - 7f), Size(56f, 8f))
    drawRect(shadow.copy(alpha = 0.18f), Offset(x - 20f, y - 10f), Size(40f, 4f))

    withTransform({
        if (direction < 0f) {
            scale(-1f, 1f, Offset(x.toFloat(), y.toFloat()))
        }
    }) {
        // Drawn in chunky 4px cells so the NPC reads like a sprite, not vector UI.
        drawPixelFrame(Rect(x - 24, y - 52, x + 20, y - 16), outline, outline)
        drawPixelFrame(Rect(x - 20, y - 48, x + 16, y - 20), panel, panel)
        drawRect(panelDark, Offset(x - 16f, y - 44f), Size(28f, 8f))
        drawPixelFrame(Rect(x - 12, y - 40, x + 12, y - 28), tealDark, outline)
        drawRect(amber, Offset(x - 8f, y - 36f), Size(4f, 4f))
        drawRect(amber, Offset(x + 4f, y - 36f), Size(4f, 4f))
        drawRect(cream, Offset(x - 16f, y - 26f), Size(28f, 6f))

        drawPixelFrame(Rect(x + 18, y - 42 + armA * 4, x + 30, y - 34 + armA * 4), metal, outline)
        drawPixelFrame(Rect(x + 28, y - 38 + armA * 4, x + 40, y - 30 + armA * 4), cream, outline)
        drawPixelFrame(Rect(x - 34, y - 38 - armA * 4, x - 22, y - 30 - armA * 4), metal, outline)
        drawPixelFrame(Rect(x - 42, y - 34 - armA * 4, x - 30, y - 26 - armA * 4), cream, outline)

        drawPixelFrame(Rect(x - 24, y - 18, x + 20, y - 8), metal, outline)
        drawPixelFrame(Rect(x - 24, y - 10, x - 8, y + 2), tire, outline)
        drawPixelFrame(Rect(x + 4, y - 10, x + 20, y + 2), tire, outline)
        drawRect(amber, Offset(x - 18f + wheelMark, y - 6f), Size(6f, 4f))
        drawRect(amber, Offset(x + 10f + wheelMark, y - 6f), Size(6f, 4f))
        drawPixelFrame(Rect(x - 4, y - 64, x + 4, y - 52), metal, outline)
        drawPixelFrame(Rect(x - 12, y - 72, x + 12, y - 60), amber, outline)
    }
}

internal fun DrawScope.drawOfficeFileCartNpc(
    position: PointF,
    isWaiting: Boolean,
    direction: Float,
    timeMillis: Long
) {
    val step = if (isWaiting) 0 else ((timeMillis / 115L) % 4L).toInt()
    val idleStep = if (isWaiting) ((timeMillis / 520L) % 2L).toInt() else 0
    val x = position.x.roundToInt()
    val y = position.y.roundToInt() - idleStep
    val outline = Color(0xFF3B2A1F)
    val shadow = Color(0x5533281F)
    val wood = Color(0xFFA86F45)
    val woodDark = Color(0xFF6F4932)
    val paper = Color(0xFFF3E5C4)
    val paperEdge = Color(0xFFB99A6E)
    val green = Color(0xFF5E8C5A)
    val amber = Color(0xFFF2C94C)
    val metal = Color(0xFF596160)
    val tire = Color(0xFF2F2C28)
    val wheelA = when (step) {
        0 -> -3
        1 -> 0
        2 -> 3
        else -> 0
    }
    val handleBob = if (step == 1 || step == 2 || idleStep == 1) -4 else 0

    drawRect(shadow, Offset(x - 34f, y - 7f), Size(68f, 8f))
    drawRect(shadow.copy(alpha = 0.18f), Offset(x - 22f, y - 10f), Size(44f, 4f))

    withTransform({
        if (direction < 0f) {
            scale(-1f, 1f, Offset(x.toFloat(), y.toFloat()))
        }
    }) {
        // Office supply cart: not another robot, so it does not compete with the patrol helper.
        drawPixelFrame(Rect(x - 30, y - 50, x + 28, y - 14), outline, outline)
        drawPixelFrame(Rect(x - 26, y - 46, x + 24, y - 18), wood, wood)
        drawRect(woodDark, Offset(x - 22f, y - 42f), Size(42f, 6f))
        drawRect(woodDark, Offset(x - 22f, y - 28f), Size(42f, 4f))
        drawPixelFrame(Rect(x - 18, y - 64, x - 2, y - 44), paper, outline)
        drawPixelFrame(Rect(x - 2, y - 68, x + 16, y - 44), paper, outline)
        drawRect(paperEdge, Offset(x + 2f, y - 60f), Size(10f, 4f))
        drawPixelFrame(Rect(x + 12, y - 58, x + 30, y - 42), green, outline)
        drawRect(amber, Offset(x + 18f, y - 52f), Size(6f, 6f))

        drawPixelFrame(Rect(x - 38, y - 42 + handleBob, x - 28, y - 34 + handleBob), metal, outline)
        drawPixelFrame(Rect(x + 28, y - 42 - handleBob, x + 38, y - 34 - handleBob), metal, outline)

        drawPixelFrame(Rect(x - 30, y - 16, x + 28, y - 8), metal, outline)
        drawPixelFrame(Rect(x - 28, y - 10, x - 12, y + 2), tire, outline)
        drawPixelFrame(Rect(x + 12, y - 10, x + 28, y + 2), tire, outline)
        drawRect(amber, Offset(x - 22f + wheelA, y - 6f), Size(6f, 4f))
        drawRect(amber, Offset(x + 18f + wheelA, y - 6f), Size(6f, 4f))
    }
}

internal fun DrawScope.drawAgents(
    bitmaps: Map<Int, Bitmap>,
    scene: OfficeSceneSnapshot,
    motionState: OfficeSceneMotionState,
    timeMillis: Long,
    reduceMotion: Boolean
) {
    val normalAgents = scene.agents.filter { !it.isSelected }
    val selectedAgents = scene.agents.filter { it.isSelected }
    val focusAgentId = scene.focusAgent?.id

    (normalAgents + selectedAgents).forEach { agent ->
        val target = resolveTargetPosition(agent)
        val position = motionState.positionFor(agent.id, target, timeMillis, reduceMotion)
        val isMoving = motionState.isTransitioning(agent.id, timeMillis)
        val direction = motionState.movementDirection(agent.id, timeMillis) ?: 1f
        val sprite = spriteSpec(agent, isMoving)

        drawCharacterFrame(
            bitmap = bitmaps[sprite.drawableId],
            frameWidth = sprite.frameWidth,
            frameHeight = sprite.frameHeight,
            frameStart = sprite.frameStart,
            frameLimit = sprite.frameLimit,
            framesPerSecond = sprite.framesPerSecond,
            timeMillis = (timeMillis + (agent.motionSeed * 830).roundToInt()).toLong(),
            position = position,
            direction = direction,
            dstSize = sprite.renderSize,
            tint = null,
            mirrorOnlyWhenMoving = isMoving
        )

        if (agent.id == focusAgentId || isMoving) {
            val label = if (isMoving) {
                when (motionState.movingTowardsStation(agent.id)) {
                    OfficeStation.DESK_LEFT, OfficeStation.DESK_CENTER, OfficeStation.COFFEE_DESK -> "努力中"
                    OfficeStation.SOFA -> "辛苦了"
                    OfficeStation.BED -> "好困啊"
                    else -> bubbleText(agent)
                }
            } else {
                bubbleText(agent)
            }
            drawActivityLabel(label, position, sprite.renderSize, agent.isSelected, officeTint(agent))
        }
    }
}

