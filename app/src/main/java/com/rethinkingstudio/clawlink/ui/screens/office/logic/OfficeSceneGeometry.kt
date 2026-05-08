package com.rethinkingstudio.clawlink.ui.screens.office.logic

import com.rethinkingstudio.clawlink.core.models.PointF
import kotlin.math.abs
import kotlin.math.hypot

fun PointF.distance(other: PointF): Float {
    return hypot(other.x - x, other.y - y)
}

fun PointF.lerp(other: PointF, progress: Float): PointF {
    val t = progress.coerceIn(0f, 1f)
    return PointF(
        x = x + (other.x - x) * t,
        y = y + (other.y - y) * t
    )
}

val PointF.Companion.Zero: PointF get() = PointF(0f, 0f)


object OfficeMotionPath {
    fun simplified(path: List<PointF>): List<PointF> {
        if (path.size <= 2) return path

        val simplified = mutableListOf<PointF>(path[0])
        for (point in path.drop(1)) {
            while (simplified.size >= 2) {
                val a = simplified[simplified.size - 2]
                val b = simplified[simplified.size - 1]
                if (isCollinear(a, b, point)) {
                    simplified.removeAt(simplified.size - 1)
                } else {
                    break
                }
            }
            simplified.add(point)
        }
        return simplified
    }

    fun joined(leading: List<PointF>, trailing: List<PointF>): List<PointF> {
        if (leading.isEmpty()) return trailing
        if (trailing.isEmpty()) return leading

        val result = leading.toMutableList()
        for (point in trailing) {
            if (result.last().distance(point) > 0.5f) {
                result.add(point)
            }
        }
        return result
    }

    private fun isCollinear(a: PointF, b: PointF, c: PointF): Boolean {
        val abX = b.x - a.x
        val abY = b.y - a.y
        val bcX = c.x - b.x
        val bcY = c.y - b.y
        return abs(abX * bcY - abY * bcX) < 0.5f
    }
}

fun Int.positiveModulo(modulus: Int): Int {
    if (modulus <= 0) return 0
    val remainder = this % modulus
    return if (remainder >= 0) remainder else remainder + modulus
}
