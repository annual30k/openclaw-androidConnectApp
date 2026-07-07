package com.rethinkingstudio.clawlink.ui.screens.office.logic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import com.rethinkingstudio.clawlink.R
import com.rethinkingstudio.clawlink.core.models.OfficeStation
import com.rethinkingstudio.clawlink.core.models.PointF
import com.rethinkingstudio.clawlink.ui.screens.office.logic.distance
import java.util.*

class OfficeRoutePlanner private constructor(context: Context? = null) {
    private data class GridCell(val row: Int, val col: Int)

    private val cellSize = 16
    private val brightnessThreshold = 0.5f
    private val deskPassageX = 412f
    private val deskTopLaneY = 288f
    private val middleCorridorX = 784f
    private val bottomCorridorY = 616f

    private var rows: Int = 0
    private var cols: Int = 0
    private var walkable: Array<BooleanArray> = emptyArray()

    init {
        if (context != null) {
            val grid = buildWalkableGrid(context)
            if (grid != null) {
                rows = grid.rows
                cols = grid.cols
                walkable = grid.walkable
            }
        }
        
        if (walkable.isEmpty()) {
            rows = 720 / cellSize
            cols = 1280 / cellSize
            walkable = Array(rows) { BooleanArray(cols) { true } }
        }
    }

    companion object {
        private var instance: OfficeRoutePlanner? = null
        
        fun getInstance(context: Context? = null): OfficeRoutePlanner {
            if (instance == null) {
                instance = OfficeRoutePlanner(context)
            }
            return instance!!
        }
    }

    fun route(
        from: PointF,
        sourceStation: OfficeStation? = null,
        to: PointF,
        targetStation: OfficeStation
    ): List<PointF> {
        fixedCorridorRoute(from, sourceStation, to, targetStation)?.let { return it }

        val startCell = nearestWalkableCell(from) ?: return listOf(from, to)
        val goalCell = nearestWalkableCell(to) ?: return listOf(from, to)
        val cellPath = shortestPath(startCell, goalCell) ?: return listOf(from, to)

        val waypoints = simplify(cellPath).map { centerPoint(it) }
        val route = mutableListOf<PointF>(from)
        
        for (point in waypoints.drop(1)) {
            if (route.last().distance(point) > 0.5f) {
                route.add(point)
            }
        }

        if (route.last().distance(to) > 0.5f) {
            route.add(to)
        } else if (route.isNotEmpty()) {
            route[route.size - 1] = to
        }

        return route
    }

    private fun fixedCorridorRoute(
        from: PointF,
        sourceStation: OfficeStation?,
        to: PointF,
        targetStation: OfficeStation
    ): List<PointF>? {
        if (targetStation != OfficeStation.DESK_LEFT && 
            targetStation != OfficeStation.BED && 
            targetStation != OfficeStation.SOFA) {
            return null
        }

        if (from.distance(to) < 0.5f) return listOf(from)

        val route = mutableListOf<PointF>(from)
        fun append(point: PointF) {
            if (route.last().distance(point) > 0.5f) {
                route.add(point)
            }
        }

        when (targetStation) {
            OfficeStation.DESK_LEFT -> {
                if (sourceStation == OfficeStation.BED || from.x > 900) {
                    append(PointF(from.x, bottomCorridorY))
                    append(PointF(deskPassageX, bottomCorridorY))
                } else if (sourceStation == OfficeStation.SOFA || from.x > 700) {
                    append(PointF(middleCorridorX, from.y))
                    append(PointF(middleCorridorX, bottomCorridorY))
                    append(PointF(deskPassageX, bottomCorridorY))
                } else {
                    append(PointF(deskPassageX, from.y))
                }
                append(PointF(deskPassageX, deskTopLaneY))
                append(PointF(to.x, deskTopLaneY))
                append(to)
            }
            OfficeStation.BED -> {
                if (sourceStation == OfficeStation.BED || from.x > 900) {
                    append(PointF(from.x, bottomCorridorY))
                } else if (sourceStation == OfficeStation.SOFA || from.x > 700) {
                    append(PointF(middleCorridorX, from.y))
                    append(PointF(middleCorridorX, bottomCorridorY))
                } else {
                    append(PointF(from.x, deskTopLaneY))
                    append(PointF(deskPassageX, deskTopLaneY))
                    append(PointF(deskPassageX, bottomCorridorY))
                }
                append(PointF(to.x, bottomCorridorY))
                append(to)
            }
            OfficeStation.SOFA -> {
                if (sourceStation == OfficeStation.BED || from.x > 900) {
                    append(PointF(from.x, bottomCorridorY))
                    append(PointF(middleCorridorX, bottomCorridorY))
                } else if (sourceStation == OfficeStation.DESK_LEFT || from.x < 700) {
                    append(PointF(from.x, deskTopLaneY))
                    append(PointF(deskPassageX, deskTopLaneY))
                    append(PointF(deskPassageX, bottomCorridorY))
                    append(PointF(middleCorridorX, bottomCorridorY))
                } else {
                    append(PointF(middleCorridorX, from.y))
                }
                append(PointF(middleCorridorX, to.y))
                append(to)
            }
            else -> return null
        }

        return route
    }

    private data class GridResult(val walkable: Array<BooleanArray>, val rows: Int, val cols: Int)

    private fun buildWalkableGrid(context: Context): GridResult? {
        val options = BitmapFactory.Options().apply { inScaled = false }
        val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.office_background, options) ?: return null
        
        val width = bitmap.width
        val height = bitmap.height
        val rows = height / cellSize
        val cols = width / cellSize
        val grid = Array(rows) { BooleanArray(cols) }

        val deskRect = RectF(80f, 310f, 356f, 524f)
        val sofaRect = RectF(670f, 144f, 890f, 344f)
        val coffeeTableRect = RectF(544f, 282f, 744f, 462f)
        val serverRoomRect = RectF(931f, 17f, 1111f, 268f)
        val partitionRect = RectF(375f, 0f, 415f, 620f)
        val catBedRect = RectF(18f, 482f, 170f, 632f)
        val bedroomWallRect = RectF(206f, 578f, 560f, 660f)
        val bedroomInteriorRect = RectF(330f, 620f, 560f, 720f)

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val sampleX = col * cellSize + cellSize / 2
                val sampleY = row * cellSize + cellSize / 2
                
                if (sampleX >= width || sampleY >= height) continue
                
                val pixel = bitmap.getPixel(sampleX, sampleY)
                val red = (pixel shr 16 and 0xff) / 255f
                val green = (pixel shr 8 and 0xff) / 255f
                val blue = (pixel and 0xff) / 255f
                val brightness = (red + green + blue) / 3f
                
                val cellRect = RectF(
                    (col * cellSize).toFloat(),
                    (row * cellSize).toFloat(),
                    ((col + 1) * cellSize).toFloat(),
                    ((row + 1) * cellSize).toFloat()
                )

                val isFurniture = RectF.intersects(cellRect, deskRect) ||
                        RectF.intersects(cellRect, sofaRect) ||
                        RectF.intersects(cellRect, coffeeTableRect) ||
                        RectF.intersects(cellRect, serverRoomRect) ||
                        RectF.intersects(cellRect, catBedRect) ||
                        RectF.intersects(cellRect, bedroomWallRect) ||
                        RectF.intersects(cellRect, bedroomInteriorRect)
                
                val isPartition = RectF.intersects(cellRect, partitionRect)

                grid[row][col] = brightness > brightnessThreshold && !isFurniture && !isPartition
            }
        }
        bitmap.recycle()
        return GridResult(grid, rows, cols)
    }

    private fun centerPoint(cell: GridCell): PointF {
        return PointF(
            (cell.col * cellSize + cellSize / 2).toFloat(),
            (cell.row * cellSize + cellSize / 2).toFloat()
        )
    }

    private fun nearestWalkableCell(point: PointF): GridCell? {
        var bestCell: GridCell? = null
        var bestDistance = Float.MAX_VALUE

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                if (walkable[row][col]) {
                    val candidate = GridCell(row, col)
                    val distance = centerPoint(candidate).distance(point)
                    if (distance < bestDistance) {
                        bestDistance = distance
                        bestCell = candidate
                    }
                }
            }
        }
        return bestCell
    }

    private fun shortestPath(start: GridCell, goal: GridCell): List<GridCell>? {
        if (start == goal) return listOf(start)

        val openSet = mutableSetOf(start)
        val cameFrom = mutableMapOf<GridCell, GridCell>()
        val gScore = mutableMapOf(start to 0f)
        val fScore = mutableMapOf(start to heuristic(start, goal))

        while (openSet.isNotEmpty()) {
            val current = openSet.minByOrNull { fScore[it] ?: Float.MAX_VALUE } ?: break

            if (current == goal) {
                return reconstructPath(cameFrom, current)
            }

            openSet.remove(current)

            for ((neighbor, stepCost) in neighbors(current)) {
                val tentativeGScore = (gScore[current] ?: Float.MAX_VALUE) + stepCost
                if (tentativeGScore < (gScore[neighbor] ?: Float.MAX_VALUE)) {
                    cameFrom[neighbor] = current
                    gScore[neighbor] = tentativeGScore
                    fScore[neighbor] = tentativeGScore + heuristic(neighbor, goal)
                    openSet.add(neighbor)
                }
            }
        }
        return null
    }

    private fun reconstructPath(cameFrom: Map<GridCell, GridCell>, current: GridCell): List<GridCell> {
        val path = mutableListOf(current)
        var cursor = current
        while (cameFrom.containsKey(cursor)) {
            cursor = cameFrom[cursor]!!
            path.add(cursor)
        }
        return simplify(path.reversed())
    }

    private fun simplify(path: List<GridCell>): List<GridCell> {
        if (path.size <= 2) return path

        val simplified = mutableListOf(path[0])
        for (cell in path.drop(1)) {
            while (simplified.size >= 2) {
                val a = simplified[simplified.size - 2]
                val b = simplified[simplified.size - 1]
                if (isCollinear(a, b, cell)) {
                    simplified.removeAt(simplified.size - 1)
                } else {
                    break
                }
            }
            simplified.add(cell)
        }
        return simplified
    }

    private fun isCollinear(a: GridCell, b: GridCell, c: GridCell): Boolean {
        val abX = b.col - a.col
        val abY = b.row - a.row
        val bcX = c.col - b.col
        val bcY = c.row - b.row
        return abX * bcY == abY * bcX
    }

    private fun heuristic(a: GridCell, b: GridCell): Float {
        val dx = (a.col - b.col).toFloat()
        val dy = (a.row - b.row).toFloat()
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun neighbors(cell: GridCell): List<Pair<GridCell, Float>> {
        val candidates = listOf(
            Triple(-1, 0, 1f), Triple(1, 0, 1f), Triple(0, -1, 1f), Triple(0, 1, 1f),
            Triple(-1, -1, 1.4142f), Triple(-1, 1, 1.4142f), Triple(1, -1, 1.4142f), Triple(1, 1, 1.4142f)
        )

        val result = mutableListOf<Pair<GridCell, Float>>()
        for ((dr, dc, cost) in candidates) {
            val row = cell.row + dr
            val col = cell.col + dc
            if (row in 0 until rows && col in 0 until cols && walkable[row][col]) {
                if (dr != 0 && dc != 0) {
                    if (!walkable[cell.row][col] || !walkable[row][cell.col]) continue
                }
                result.add(GridCell(row, col) to cost)
            }
        }
        return result
    }
}
