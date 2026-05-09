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
