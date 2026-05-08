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
import com.rethinkingstudio.clawlink.ui.screens.office.logic.OfficeNPCCast
import com.rethinkingstudio.clawlink.ui.screens.office.logic.OfficeRoutePlanner
import com.rethinkingstudio.clawlink.ui.screens.office.logic.OfficeSceneMotionState
import com.rethinkingstudio.clawlink.ui.screens.office.logic.positiveModulo
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun PixelOfficeScene(
    scene: OfficeSceneSnapshot,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
    showsOccupants: Boolean = true
) {
    val context = LocalContext.current
    val routePlanner = remember { OfficeRoutePlanner.getInstance(context) }
    val officeNpcs = remember(routePlanner) { OfficeNPCCast.routed(routePlanner) }
    val motionState = remember { OfficeSceneMotionState() }
    var timeMillis by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { timeMillis = it }
        }
    }

    val bitmaps = remember { mutableMapOf<Int, Bitmap>() }
    DisposableEffect(Unit) {
        val ids = listOf(
            R.drawable.office_background,
            R.drawable.office_serverroom_sheet,
            R.drawable.office_plants_sheet,
            R.drawable.office_cats_sheet,
            R.drawable.office_sofa,
            R.drawable.office_coffee_machine_shadow,
            R.drawable.office_coffee_machine_sheet,
            R.drawable.office_desk,
            R.drawable.office_posters_sheet,
            R.drawable.star_idle_sheet,
            R.drawable.star_working_sheet,
            R.drawable.star_walking_sheet,
            R.drawable.star_sync_sheet
        )
        val options = BitmapFactory.Options().apply { inScaled = false }
        ids.forEach { id ->
            BitmapFactory.decodeResource(context.resources, id, options)?.let { bitmaps[id] = it }
        }
        onDispose {
            bitmaps.values.forEach { it.recycle() }
            bitmaps.clear()
        }
    }

    SideEffect {
        motionState.reconcile(
            scene = scene,
            time = timeMillis,
            targetProvider = { resolveTargetPosition(it) },
            stationProvider = { resolveStation(it) },
            routePlanner = routePlanner
        )
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val stageWidth = 1280f
        val stageHeight = 720f
        val scale = minOf(size.width / stageWidth, size.height / stageHeight)
        val offsetX = (size.width - stageWidth * scale) / 2f
        val offsetY = (size.height - stageHeight * scale) / 2f

        drawRect(OfficeSceneLetterboxColor, size = size)
        withTransform({
            translate(offsetX, offsetY)
            scale(scale, scale, Offset.Zero)
        }) {
            drawStage(bitmaps, scene, motionState, officeNpcs, timeMillis, reduceMotion, showsOccupants)
        }
    }
}

private val OfficeSceneLetterboxColor = Color(0xFFEDE3CC)

private fun DrawScope.drawStage(
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

private fun DrawScope.drawFurniture(
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

private fun DrawScope.drawNPCs(
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

private fun DrawScope.drawOfficeCourierNpc(
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

private fun DrawScope.drawOfficeFileCartNpc(
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

private fun DrawScope.drawAgents(
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

private data class SpriteSpec(
    val drawableId: Int,
    val frameWidth: Int,
    val frameHeight: Int,
    val frameStart: Int,
    val frameLimit: Int,
    val framesPerSecond: Double,
    val renderSize: Size
)

private fun spriteSpec(agent: OfficeAgentSnapshot, isMoving: Boolean): SpriteSpec {
    if (isMoving) {
        return SpriteSpec(R.drawable.star_walking_sheet, 256, 256, 0, 16, 16.0, Size(170f, 170f))
    }

    return when (agent.activityKind) {
        OfficeActivityKind.SLEEPING, OfficeActivityKind.OFFLINE ->
            SpriteSpec(R.drawable.star_sync_sheet, 256, 256, 1, 48, 6.0, Size(256f, 256f))
        OfficeActivityKind.IDLE, OfficeActivityKind.SYNCING ->
            SpriteSpec(R.drawable.star_idle_sheet, 256, 256, 0, 30, 8.0, Size(256f, 256f))
        OfficeActivityKind.WRITING, OfficeActivityKind.RESEARCHING, OfficeActivityKind.EXECUTING, OfficeActivityKind.ERROR ->
            SpriteSpec(R.drawable.star_working_sheet, 300, 300, 0, 38, 10.0, Size(270f, 270f))
    }
}

private fun DrawScope.drawDeskOverlay(bitmaps: Map<Int, Bitmap>) {
    bitmaps[R.drawable.office_desk]?.let {
        drawContext.canvas.nativeCanvas.drawBitmap(it, null, centerRect(218f, 417f, 276f, 214f, 1.0f), null)
    }
}

private fun DrawScope.drawSpriteFrame(
    bitmap: Bitmap?,
    frameWidth: Int,
    frameHeight: Int,
    fixedFrameIndex: Int?,
    timeMillis: Long,
    framesPerSecond: Double,
    dstRect: Rect,
    reduceMotion: Boolean
) {
    if (bitmap == null) return
    val totalFrames = ((bitmap.width / frameWidth) * (bitmap.height / frameHeight)).coerceAtLeast(1)
    val frameIndex = fixedFrameIndex ?: frameIndex(timeMillis, framesPerSecond, totalFrames, reduceMotion)
    val srcRect = sourceRect(bitmap, frameWidth, frameHeight, frameIndex.coerceIn(0, totalFrames - 1))
    drawContext.canvas.nativeCanvas.drawBitmap(bitmap, srcRect, dstRect, null)
}

private fun DrawScope.drawCharacterFrame(
    bitmap: Bitmap?,
    frameWidth: Int,
    frameHeight: Int,
    frameStart: Int,
    frameLimit: Int,
    framesPerSecond: Double,
    timeMillis: Long,
    position: PointF,
    direction: Float,
    dstSize: Size,
    tint: Color?,
    mirrorOnlyWhenMoving: Boolean
) {
    if (bitmap == null) return
    val frameOffset = frameIndex(timeMillis, framesPerSecond, frameLimit, reduceMotion = false)
    val frame = frameStart + frameOffset
    val srcRect = sourceRect(bitmap, frameWidth, frameHeight, frame)
    val dstRect = Rect(
        (position.x - dstSize.width / 2f).roundToInt(),
        (position.y - dstSize.height / 2f).roundToInt(),
        (position.x + dstSize.width / 2f).roundToInt(),
        (position.y + dstSize.height / 2f).roundToInt()
    )
    val paint = tint?.let {
        Paint().apply {
            isAntiAlias = false
            colorFilter = PorterDuffColorFilter(it.toArgb(), PorterDuff.Mode.MULTIPLY)
        }
    }

    drawOval(
        color = Color.Black.copy(alpha = 0.20f),
        topLeft = Offset(position.x - 22f, position.y - 6f),
        size = Size(44f, 12f)
    )

    val shouldMirror = if (mirrorOnlyWhenMoving) direction < 0f else direction < 0f
    if (shouldMirror) {
        val canvas = drawContext.canvas.nativeCanvas
        canvas.save()
        canvas.scale(-1f, 1f, position.x, position.y)
        canvas.drawBitmap(bitmap, srcRect, dstRect, paint)
        canvas.restore()
    } else {
        drawContext.canvas.nativeCanvas.drawBitmap(bitmap, srcRect, dstRect, paint)
    }
}

private fun DrawScope.drawActivityLabel(
    text: String,
    center: PointF,
    renderSize: Size,
    selected: Boolean,
    tint: Color
) {
    val paint = Paint().apply {
        isAntiAlias = false
        color = android.graphics.Color.rgb(46, 38, 30)
        textSize = 19f
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
    }
    val bubbleWidth = max(if (selected) 94f else 78f, paint.measureText(text) + 34f)
    val bubbleHeight = 28f
    val left = center.x - bubbleWidth / 2f
    val top = center.y - renderSize.height / 2f - bubbleHeight + 4f
    val rect = Rect(left.roundToInt(), top.roundToInt(), (left + bubbleWidth).roundToInt(), (top + bubbleHeight).roundToInt())

    drawPixelFrame(rect, Color(0xFFF6EEDC), if (selected) tint else Color(0xFF33281F))
    drawPixelFrame(Rect(rect.left + 7, rect.centerY() - 3, rect.left + 13, rect.centerY() + 3), tint, Color(0xFF33281F))
    drawPixelFrame(Rect(rect.centerX() - 5, rect.bottom - 2, rect.centerX() + 5, rect.bottom + 6), Color(0xFFF6EEDC), Color(0xFF33281F))
    drawContext.canvas.nativeCanvas.drawText(text, rect.centerX().toFloat() + 5f, rect.centerY().toFloat() + 7f, paint)
}

private fun DrawScope.drawOfficeTitlePlaque() {
    val rect = Rect(558, 8, 722, 30)
    drawPixelFrame(rect, Color(0xFF63452E), Color(0xFF38261A))
    drawPixelFrame(Rect(rect.left + 8, rect.centerY() - 3, rect.left + 14, rect.centerY() + 3), Color(0xFFF5C943), Color(0xFF1A120C))
    drawPixelFrame(Rect(rect.right - 14, rect.centerY() - 3, rect.right - 8, rect.centerY() + 3), Color(0xFFF5C943), Color(0xFF1A120C))
    val paint = Paint().apply {
        isAntiAlias = false
        color = android.graphics.Color.rgb(247, 222, 71)
        textSize = 16f
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
    }
    drawContext.canvas.nativeCanvas.drawText("像素办公室", rect.centerX().toFloat(), rect.centerY().toFloat() + 6f, paint)
}

private fun DrawScope.drawPixelFrame(rect: Rect, fillColor: Color, borderColor: Color) {
    drawRect(borderColor, Offset(rect.left.toFloat(), rect.top.toFloat()), Size(rect.width().toFloat(), rect.height().toFloat()))
    drawRect(fillColor, Offset((rect.left + 1).toFloat(), (rect.top + 1).toFloat()), Size((rect.width() - 2).coerceAtLeast(1).toFloat(), (rect.height() - 2).coerceAtLeast(1).toFloat()))
}

private fun sourceRect(bitmap: Bitmap, frameWidth: Int, frameHeight: Int, frameIndex: Int): Rect {
    val framesPerRow = (bitmap.width / frameWidth).coerceAtLeast(1)
    val totalFrames = (framesPerRow * (bitmap.height / frameHeight).coerceAtLeast(1)).coerceAtLeast(1)
    val safeIndex = frameIndex.positiveModulo(totalFrames)
    val row = safeIndex / framesPerRow
    val col = safeIndex % framesPerRow
    return Rect(col * frameWidth, row * frameHeight, (col + 1) * frameWidth, (row + 1) * frameHeight)
}

private fun frameIndex(
    timeMillis: Long,
    framesPerSecond: Double,
    frameCount: Int,
    reduceMotion: Boolean
): Int {
    if (frameCount <= 1 || reduceMotion) return 0
    return ((timeMillis / 1000.0 * framesPerSecond).toInt()).positiveModulo(frameCount)
}

private fun centerRect(centerX: Float, centerY: Float, width: Float, height: Float, scale: Float): Rect {
    val scaledWidth = width * scale
    val scaledHeight = height * scale
    return Rect(
        (centerX - scaledWidth / 2f).roundToInt(),
        (centerY - scaledHeight / 2f).roundToInt(),
        (centerX + scaledWidth / 2f).roundToInt(),
        (centerY + scaledHeight / 2f).roundToInt()
    )
}

private fun resolveTargetPosition(agent: OfficeAgentSnapshot): PointF {
    val station = resolveStation(agent)
    val anchors = when (station) {
        OfficeStation.DESK_LEFT -> listOf(PointF(242f, 324f), PointF(232f, 340f), PointF(254f, 308f))
        OfficeStation.DESK_CENTER -> listOf(PointF(659f, 397f), PointF(704f, 358f), PointF(620f, 360f))
        OfficeStation.COFFEE_DESK -> listOf(PointF(659f, 397f), PointF(704f, 432f), PointF(616f, 428f))
        OfficeStation.SOFA -> listOf(PointF(798f, 272f))
        OfficeStation.BED -> listOf(PointF(1157f, 592f), PointF(1120f, 570f), PointF(1190f, 620f))
        OfficeStation.SERVER_RACK -> listOf(PointF(1007f, 221f), PointF(1060f, 190f), PointF(952f, 188f))
        OfficeStation.CORNER -> listOf(PointF(94f, 557f), PointF(146f, 520f), PointF(60f, 560f))
    }
    val seed = (agent.motionSeed * 10_000).roundToInt()
    return anchors[seed.positiveModulo(anchors.size)]
}

private fun resolveStation(agent: OfficeAgentSnapshot): OfficeStation {
    if (agent.aggregateStatus == AggregateStatus.offline) {
        return OfficeStation.BED
    }

    return when (agent.activityKind) {
        OfficeActivityKind.SLEEPING, OfficeActivityKind.OFFLINE -> OfficeStation.BED
        OfficeActivityKind.IDLE, OfficeActivityKind.SYNCING -> OfficeStation.SOFA
        OfficeActivityKind.WRITING, OfficeActivityKind.RESEARCHING, OfficeActivityKind.EXECUTING, OfficeActivityKind.ERROR -> OfficeStation.DESK_LEFT
    }
}

private fun bubbleText(agent: OfficeAgentSnapshot): String {
    if (agent.aggregateStatus == AggregateStatus.offline) {
        return "离线"
    }
    return agent.activityTitle.ifBlank {
        when (agent.activityKind) {
            OfficeActivityKind.IDLE -> "待命"
            OfficeActivityKind.WRITING -> "写作中"
            OfficeActivityKind.RESEARCHING -> "检索中"
            OfficeActivityKind.EXECUTING -> "执行中"
            OfficeActivityKind.SYNCING -> "同步中"
            OfficeActivityKind.SLEEPING -> "睡觉中"
            OfficeActivityKind.OFFLINE -> "离线"
            OfficeActivityKind.ERROR -> "异常"
        }
    }
}

private fun officeTint(agent: OfficeAgentSnapshot): Color {
    if (agent.aggregateStatus == AggregateStatus.offline) return Color(0xFFE05A5A)
    return when (agent.activityKind) {
        OfficeActivityKind.IDLE, OfficeActivityKind.SYNCING, OfficeActivityKind.SLEEPING -> Color(0xFF61A7E8)
        OfficeActivityKind.WRITING -> Color(0xFF6EE7B7)
        OfficeActivityKind.RESEARCHING -> Color(0xFFF2C94C)
        OfficeActivityKind.EXECUTING -> Color(0xFF4CAF50)
        OfficeActivityKind.OFFLINE, OfficeActivityKind.ERROR -> Color(0xFFE05A5A)
    }
}

private fun Color.toArgb(): Int {
    return (alpha * 255.0f + 0.5f).toInt() shl 24 or
        ((red * 255.0f + 0.5f).toInt() shl 16) or
        ((green * 255.0f + 0.5f).toInt() shl 8) or
        (blue * 255.0f + 0.5f).toInt()
}
