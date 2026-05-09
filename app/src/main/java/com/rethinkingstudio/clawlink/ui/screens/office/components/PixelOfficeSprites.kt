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

internal data class SpriteSpec(
    val drawableId: Int,
    val frameWidth: Int,
    val frameHeight: Int,
    val frameStart: Int,
    val frameLimit: Int,
    val framesPerSecond: Double,
    val renderSize: Size
)

internal fun spriteSpec(agent: OfficeAgentSnapshot, isMoving: Boolean): SpriteSpec {
    if (isMoving) {
        return SpriteSpec(R.drawable.star_walking_sheet, 256, 256, 0, 16, 16.0, Size(170f, 170f))
    }

    if (agent.aggregateStatus != AggregateStatus.online) {
        return SpriteSpec(R.drawable.star_sync_sheet, 256, 256, 1, 48, 6.0, Size(256f, 256f))
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

internal fun DrawScope.drawDeskOverlay(bitmaps: Map<Int, Bitmap>) {
    bitmaps[R.drawable.office_desk]?.let {
        drawContext.canvas.nativeCanvas.drawBitmap(it, null, centerRect(218f, 417f, 276f, 214f, 1.0f), null)
    }
}

internal fun DrawScope.drawSpriteFrame(
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

internal fun DrawScope.drawCharacterFrame(
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

internal fun DrawScope.drawActivityLabel(
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

internal fun DrawScope.drawOfficeTitlePlaque() {
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
    drawContext.canvas.nativeCanvas.drawText(choose("Pixel Office", "像素办公室"), rect.centerX().toFloat(), rect.centerY().toFloat() + 6f, paint)
}

internal fun DrawScope.drawPixelFrame(rect: Rect, fillColor: Color, borderColor: Color) {
    drawRect(borderColor, Offset(rect.left.toFloat(), rect.top.toFloat()), Size(rect.width().toFloat(), rect.height().toFloat()))
    drawRect(fillColor, Offset((rect.left + 1).toFloat(), (rect.top + 1).toFloat()), Size((rect.width() - 2).coerceAtLeast(1).toFloat(), (rect.height() - 2).coerceAtLeast(1).toFloat()))
}

internal fun sourceRect(bitmap: Bitmap, frameWidth: Int, frameHeight: Int, frameIndex: Int): Rect {
    val framesPerRow = (bitmap.width / frameWidth).coerceAtLeast(1)
    val totalFrames = (framesPerRow * (bitmap.height / frameHeight).coerceAtLeast(1)).coerceAtLeast(1)
    val safeIndex = frameIndex.positiveModulo(totalFrames)
    val row = safeIndex / framesPerRow
    val col = safeIndex % framesPerRow
    return Rect(col * frameWidth, row * frameHeight, (col + 1) * frameWidth, (row + 1) * frameHeight)
}

internal fun frameIndex(
    timeMillis: Long,
    framesPerSecond: Double,
    frameCount: Int,
    reduceMotion: Boolean
): Int {
    if (frameCount <= 1 || reduceMotion) return 0
    return ((timeMillis / 1000.0 * framesPerSecond).toInt()).positiveModulo(frameCount)
}

internal fun centerRect(centerX: Float, centerY: Float, width: Float, height: Float, scale: Float): Rect {
    val scaledWidth = width * scale
    val scaledHeight = height * scale
    return Rect(
        (centerX - scaledWidth / 2f).roundToInt(),
        (centerY - scaledHeight / 2f).roundToInt(),
        (centerX + scaledWidth / 2f).roundToInt(),
        (centerY + scaledHeight / 2f).roundToInt()
    )
}

internal fun resolveTargetPosition(agent: OfficeAgentSnapshot): PointF {
    val station = resolveStation(agent)
    val anchors = when (station) {
        OfficeStation.DESK_LEFT -> listOf(PointF(242f, 324f), PointF(232f, 340f), PointF(254f, 308f))
        OfficeStation.DESK_CENTER -> listOf(PointF(659f, 397f), PointF(704f, 358f), PointF(620f, 360f))
        OfficeStation.COFFEE_DESK -> listOf(PointF(659f, 397f), PointF(704f, 432f), PointF(616f, 428f))
        OfficeStation.SOFA -> listOf(PointF(798f, 272f))
        OfficeStation.BED -> listOf(PointF(1157f, 592f))
        OfficeStation.SERVER_RACK -> listOf(PointF(1007f, 221f), PointF(1060f, 190f), PointF(952f, 188f))
        OfficeStation.CORNER -> listOf(PointF(94f, 557f), PointF(146f, 520f), PointF(60f, 560f))
    }
    val seed = (agent.motionSeed * 10_000).roundToInt()
    return anchors[seed.positiveModulo(anchors.size)]
}

internal fun resolveStation(agent: OfficeAgentSnapshot): OfficeStation {
    if (agent.aggregateStatus != AggregateStatus.online) {
        return OfficeStation.BED
    }

    return when (agent.activityKind) {
        OfficeActivityKind.SLEEPING, OfficeActivityKind.OFFLINE -> OfficeStation.BED
        OfficeActivityKind.IDLE, OfficeActivityKind.SYNCING -> OfficeStation.SOFA
        OfficeActivityKind.WRITING, OfficeActivityKind.RESEARCHING, OfficeActivityKind.EXECUTING, OfficeActivityKind.ERROR -> OfficeStation.DESK_LEFT
    }
}

internal fun bubbleText(agent: OfficeAgentSnapshot): String {
    if (agent.aggregateStatus == AggregateStatus.offline) {
        return choose("Offline", "离线")
    }
    return agent.activityTitle.ifBlank {
        when (agent.activityKind) {
            OfficeActivityKind.IDLE -> choose("Idle", "待命")
            OfficeActivityKind.WRITING -> choose("Writing", "写作中")
            OfficeActivityKind.RESEARCHING -> choose("Researching", "检索中")
            OfficeActivityKind.EXECUTING -> choose("Executing", "执行中")
            OfficeActivityKind.SYNCING -> choose("Syncing", "同步中")
            OfficeActivityKind.SLEEPING -> choose("Sleeping", "睡觉中")
            OfficeActivityKind.OFFLINE -> choose("Offline", "离线")
            OfficeActivityKind.ERROR -> choose("Issue", "异常")
        }
    }
}

internal fun officeTint(agent: OfficeAgentSnapshot): Color {
    if (agent.aggregateStatus == AggregateStatus.offline) return Color(0xFFE05A5A)
    return when (agent.activityKind) {
        OfficeActivityKind.IDLE, OfficeActivityKind.SYNCING, OfficeActivityKind.SLEEPING -> Color(0xFF61A7E8)
        OfficeActivityKind.WRITING -> Color(0xFF6EE7B7)
        OfficeActivityKind.RESEARCHING -> Color(0xFFF2C94C)
        OfficeActivityKind.EXECUTING -> Color(0xFF4CAF50)
        OfficeActivityKind.OFFLINE, OfficeActivityKind.ERROR -> Color(0xFFE05A5A)
    }
}

internal fun Color.toArgb(): Int {
    return (alpha * 255.0f + 0.5f).toInt() shl 24 or
        ((red * 255.0f + 0.5f).toInt() shl 16) or
        ((green * 255.0f + 0.5f).toInt() shl 8) or
        (blue * 255.0f + 0.5f).toInt()
}
