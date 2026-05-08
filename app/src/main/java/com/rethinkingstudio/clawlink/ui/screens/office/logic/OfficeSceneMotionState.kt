package com.rethinkingstudio.clawlink.ui.screens.office.logic

import com.rethinkingstudio.clawlink.core.models.OfficeAgentSnapshot
import com.rethinkingstudio.clawlink.core.models.OfficeSceneSnapshot
import com.rethinkingstudio.clawlink.core.models.OfficeStation
import com.rethinkingstudio.clawlink.core.models.PointF
import com.rethinkingstudio.clawlink.ui.screens.office.logic.distance
import com.rethinkingstudio.clawlink.ui.screens.office.logic.lerp
import com.rethinkingstudio.clawlink.ui.screens.office.logic.Zero

class OfficeSceneMotionState {
    data class AgentMotion(
        val path: List<PointF>,
        val segmentLengths: List<Float>,
        val totalDistance: Float,
        val sourceStation: OfficeStation?,
        val targetStation: OfficeStation?,
        val startTime: Long,
        val duration: Long
    ) {
        val startPosition: PointF get() = path.firstOrNull() ?: PointF.Zero
        val targetPosition: PointF get() = path.lastOrNull() ?: PointF.Zero

        companion object {
            fun build(
                path: List<PointF>,
                sourceStation: OfficeStation?,
                targetStation: OfficeStation?,
                startTime: Long,
                speed: Float
            ): AgentMotion? {
                val cleanedPath = OfficeMotionPath.simplified(path)
                if (cleanedPath.size < 2 || speed <= 0) return null

                val segmentLengths = mutableListOf<Float>()
                var totalDistance = 0f
                for (i in 0 until cleanedPath.size - 1) {
                    val length = cleanedPath[i].distance(cleanedPath[i + 1])
                    segmentLengths.add(length)
                    totalDistance += length
                }

                if (totalDistance <= 0) return null

                return AgentMotion(
                    path = cleanedPath,
                    segmentLengths = segmentLengths,
                    totalDistance = totalDistance,
                    sourceStation = sourceStation,
                    targetStation = targetStation,
                    startTime = startTime,
                    duration = (totalDistance / speed * 1000).toLong()
                )
            }
        }

        fun positionAt(time: Long): PointF {
            val first = path.firstOrNull() ?: return PointF.Zero
            if (path.size < 2 || duration <= 0) return targetPosition
            if (time <= startTime) return first
            if (time >= startTime + duration) return targetPosition

            val elapsed = (time - startTime).toFloat() / duration
            val traveled = totalDistance * elapsed
            return positionAlong(traveled)
        }

        fun directionAt(time: Long): Float? {
            if (path.size < 2) return null
            val traveled = traveledDistanceAt(time)
            val segmentIdx = segmentIndexAlong(traveled)
            horizontalDirectionFor(segmentIdx)?.let { return it }

            val overallDX = targetPosition.x - startPosition.x
            if (kotlin.math.abs(overallDX) < 0.5f) return null
            return if (overallDX >= 0) 1f else -1f
        }

        fun reversedRouteToStart(time: Long): List<PointF> {
            if (path.size < 2) return path
            val traveled = traveledDistanceAt(time)
            val currentPos = positionAlong(traveled)
            val currentIdx = segmentIndexAlong(traveled)

            val route = mutableListOf(currentPos)
            appendUnique(path[currentIdx], route)

            if (currentIdx > 0) {
                for (i in currentIdx - 1 downTo 0) {
                    appendUnique(path[i], route)
                }
            }
            return route
        }

        private fun traveledDistanceAt(time: Long): Float {
            return when {
                time <= startTime -> 0f
                time >= startTime + duration || duration <= 0L -> totalDistance
                else -> totalDistance * (time - startTime).toFloat() / duration
            }
        }

        private fun appendUnique(point: PointF, route: MutableList<PointF>) {
            if (route.lastOrNull()?.distance(point) ?: Float.MAX_VALUE > 0.5f) {
                route.add(point)
            }
        }

        private fun positionAlong(traveled: Float): PointF {
            val first = path.firstOrNull() ?: return PointF.Zero
            if (path.size < 2) return first

            var remaining = traveled.coerceIn(0f, totalDistance)
            for (i in 0 until path.size - 1) {
                val segmentLength = segmentLengths[i]
                if (segmentLength <= 0) continue

                val start = path[i]
                val end = path[i + 1]
                if (remaining <= segmentLength) {
                    val progress = remaining / segmentLength
                    return start.lerp(end, progress)
                }
                remaining -= segmentLength
            }
            return path.lastOrNull() ?: first
        }

        private fun segmentIndexAlong(traveled: Float): Int {
            if (path.size < 2) return 0
            var remaining = traveled.coerceIn(0f, totalDistance)
            for (i in segmentLengths.indices) {
                val length = segmentLengths[i]
                if (length <= 0) continue
                if (remaining <= length) return i
                remaining -= length
            }
            return (path.size - 2).coerceAtLeast(0)
        }

        private fun horizontalDirectionFor(index: Int): Float? {
            if (index < 0 || index >= path.size - 1) return null
            val dx = path[index + 1].x - path[index].x
            if (kotlin.math.abs(dx) < 0.5f) return null
            return if (dx >= 0) 1f else -1f
        }
    }

    private var motions = mutableMapOf<String, AgentMotion>()
    private var currentPositions = mutableMapOf<String, PointF>()
    private var settledStations = mutableMapOf<String, OfficeStation>()

    fun reconcile(
        scene: OfficeSceneSnapshot,
        time: Long,
        targetProvider: (OfficeAgentSnapshot) -> PointF,
        stationProvider: (OfficeAgentSnapshot) -> OfficeStation,
        routePlanner: OfficeRoutePlanner
    ) {
        val speed = 200f // pixels per second
        val activeIds = scene.agents.map { it.id }.toSet()

        currentPositions.keys.retainAll(activeIds)
        motions.keys.retainAll(activeIds)
        settledStations.keys.retainAll(activeIds)

        for (id in motions.keys.toList()) {
            val motion = motions[id] ?: continue
            if (time >= motion.startTime + motion.duration) {
                currentPositions[id] = motion.targetPosition
                motion.targetStation?.let { settledStations[id] = it }
                motions.remove(id)
            }
        }

        for (agent in scene.agents) {
            val finalTarget = targetProvider(agent)
            val station = stationProvider(agent)

            if (currentPositions[agent.id] == null) {
                currentPositions[agent.id] = finalTarget
                settledStations[agent.id] = station
                continue
            }

            val current = currentPositions[agent.id]!!
            val motion = motions[agent.id]
            val lastTarget = motion?.targetPosition ?: current

            val targetChanged = lastTarget.distance(finalTarget) > 0.5f
            val stationChanged = motion?.targetStation != station

            if (targetChanged || stationChanged) {
                val travelStart = motion?.positionAt(time) ?: current
                val path: List<PointF>
                val sourceStation: OfficeStation?

                if (motion != null && motion.targetStation != station && station != OfficeStation.DESK_LEFT && time < motion.startTime + motion.duration) {
                    sourceStation = motion.sourceStation
                    val returnPath = motion.reversedRouteToStart(time)
                    val returnEnd = returnPath.lastOrNull() ?: travelStart
                    if (returnEnd.distance(finalTarget) > 0.5f) {
                        val continuation = routePlanner.route(returnEnd, sourceStation, finalTarget, station)
                        path = OfficeMotionPath.joined(returnPath, continuation)
                    } else {
                        path = returnPath
                    }
                } else {
                    sourceStation = motion?.sourceStation ?: settledStations[agent.id]
                    path = routePlanner.route(travelStart, sourceStation, finalTarget, station)
                }

                val newMotion = AgentMotion.build(path, sourceStation, station, time, speed)
                if (newMotion != null) {
                    motions[agent.id] = newMotion
                } else {
                    motions.remove(agent.id)
                    currentPositions[agent.id] = finalTarget
                    settledStations[agent.id] = station
                }
            }
        }
    }

    fun positionFor(id: String, fallbackTarget: PointF, time: Long, reduceMotion: Boolean = false): PointF {
        if (reduceMotion) return fallbackTarget
        return motions[id]?.positionAt(time) ?: currentPositions[id] ?: fallbackTarget
    }

    fun isTransitioning(id: String, time: Long): Boolean {
        val motion = motions[id] ?: return false
        return time >= motion.startTime && time < motion.startTime + motion.duration
    }

    fun movementDirection(id: String, time: Long): Float? {
        return motions[id]?.directionAt(time)
    }

    fun movingTowardsStation(id: String): OfficeStation? {
        return motions[id]?.targetStation
    }
}
