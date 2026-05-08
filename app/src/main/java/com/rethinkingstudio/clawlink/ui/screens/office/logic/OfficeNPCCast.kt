package com.rethinkingstudio.clawlink.ui.screens.office.logic

import androidx.compose.ui.graphics.Color
import com.rethinkingstudio.clawlink.core.models.OfficeStation
import com.rethinkingstudio.clawlink.core.models.PointF
import com.rethinkingstudio.clawlink.ui.screens.office.logic.Zero
import kotlin.math.sqrt

data class OfficeNPC(
    val id: Int,
    val bodyColor: Color,
    val faceColor: Color,
    val outlineColor: Color,
    val waypoints: List<PointF>,
    val speed: Float,
    val waitAtEachStop: Double,
    val timeOffset: Double
) {
    data class Snapshot(
        val position: PointF,
        val isWaiting: Boolean,
        val facingDirection: Float
    )

    fun snapshotAt(timeSeconds: Double): Snapshot {
        if (waypoints.size < 2) {
            return Snapshot(waypoints.firstOrNull() ?: PointF.Zero, true, 1f)
        }

        var totalCycleTime = 0.0
        val segments = mutableListOf<NPCSegment>()

        for (i in waypoints.indices) {
            val next = (i + 1) % waypoints.size
            val dx = (waypoints[next].x - waypoints[i].x).toDouble()
            val dy = (waypoints[next].y - waypoints[i].y).toDouble()
            val dist = sqrt(dx * dx + dy * dy)
            val travelTime = dist / speed
            segments.add(NPCSegment(waitAtEachStop, travelTime, waypoints[i], waypoints[next]))
            totalCycleTime += waitAtEachStop + travelTime
        }

        if (totalCycleTime <= 0) {
            return Snapshot(waypoints[0], true, 1f)
        }

        val adjustedTime = (timeSeconds + timeOffset).coerceAtLeast(0.0)
        val t = adjustedTime % totalCycleTime

        var elapsed = 0.0
        for (seg in segments) {
            val waitEnd = elapsed + seg.waitDuration
            if (t < waitEnd) {
                return Snapshot(seg.from, true, 1f)
            }

            val travelEnd = waitEnd + seg.travelDuration
            if (t < travelEnd) {
                val progress = ((t - waitEnd) / seg.travelDuration).toFloat()
                val pos = PointF(
                    seg.from.x + (seg.to.x - seg.from.x) * progress,
                    seg.from.y + (seg.to.y - seg.from.y) * progress
                )
                val facing = if (seg.to.x >= seg.from.x) 1f else -1f
                return Snapshot(pos, false, facing)
            }
            elapsed = travelEnd
        }

        return Snapshot(waypoints[0], true, 1f)
    }

    fun routed(routePlanner: OfficeRoutePlanner): OfficeNPC {
        if (waypoints.size < 2) return this

        val routedWaypoints = mutableListOf<PointF>()
        for (index in waypoints.indices) {
            val from = waypoints[index]
            val to = waypoints[(index + 1) % waypoints.size]
            val segment = routePlanner.route(
                from = from,
                sourceStation = null,
                to = to,
                targetStation = OfficeStation.CORNER
            )
            segment.forEach { point ->
                if ((routedWaypoints.lastOrNull()?.distance(point) ?: Float.MAX_VALUE) > 0.5f) {
                    routedWaypoints.add(point)
                }
            }
        }

        return copy(waypoints = routedWaypoints.ifEmpty { waypoints })
    }

    private data class NPCSegment(
        val waitDuration: Double,
        val travelDuration: Double,
        val from: PointF,
        val to: PointF
    )
}

object OfficeNPCCast {
    private fun markedActivityPoints(): List<PointF> = listOf(
        PointF(210f, 552f),
        PointF(74f, 430f),
        PointF(128f, 330f),
        PointF(248f, 292f),
        PointF(342f, 308f),
        PointF(348f, 520f),
        PointF(438f, 632f),
        PointF(560f, 602f),
        PointF(644f, 548f),
        PointF(780f, 500f),
        PointF(900f, 510f),
        PointF(1018f, 570f),
        PointF(928f, 626f),
        PointF(754f, 628f),
        PointF(600f, 570f),
        PointF(360f, 560f),
        PointF(210f, 552f)
    )

    val npcs = listOf(
        OfficeNPC(
            id = 0,
            bodyColor = Color(0xFF599EE0),
            faceColor = Color(0xFFE6EDF7),
            outlineColor = Color(0xFF264066),
            waypoints = markedActivityPoints(),
            speed = 34f,
            waitAtEachStop = 1.6,
            timeOffset = 0.0
        ),
        OfficeNPC(
            id = 1,
            bodyColor = Color(0xFF61BD6B),
            faceColor = Color(0xFFE6F7E6),
            outlineColor = Color(0xFF29572E),
            waypoints = markedActivityPoints().asReversed(),
            speed = 31f,
            waitAtEachStop = 1.8,
            timeOffset = 28.0
        )
    )

    fun routed(routePlanner: OfficeRoutePlanner): List<OfficeNPC> {
        return npcs.map { it.routed(routePlanner) }
    }
}
