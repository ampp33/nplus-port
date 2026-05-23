package com.nplus.physics.collision

import com.nplus.physics.math.Vec2
import kotlin.math.*

/** Port of AS3 colutils — all static geometry query functions. */
object ColUtils {

    // Scratch vec used internally; physics is single-threaded so this is safe.
    private val cp = Vec2()
    private val segScratch = ArrayList<Segment>(32)

    /**
     * Returns the signed distance (positive = outside) between [circle] (radius [r])
     * and the nearest segment in [grid]. Writes closest point to [outClosest].
     * Return value: -1 = backfacing nearest, +1 = frontfacing nearest, 0 = no segments found.
     */
    fun getSingleClosestPointSigned(
        grid: GridSegment, circle: Vec2, r: Float, outClosest: Vec2
    ): Int {
        segScratch.clear()
        grid.gatherCellContentsFromWorldspaceRegion(
            circle.x - r, circle.y - r,
            circle.x + r, circle.y + r,
            segScratch
        )
        var side = 0
        var best = 99999999f
        for (seg in segScratch) {
            val backfacing = seg.getClosestPointIsBackfacing(circle, cp)
            val dx = cp.x - circle.x
            val dy = cp.y - circle.y
            var distSq = dx * dx + dy * dy
            if (!backfacing) distSq -= 0.1f
            if (distSq < best) {
                outClosest.set(cp)
                best = distSq
                side = if (backfacing) -1 else 1
            }
        }
        return side
    }

    /**
     * AABB (square) vs point penetration. [centre] is the square centre, [half] is half-extent.
     * Writes collision normal to [outNormal]. Returns penetration depth (0 = no overlap).
     */
    fun penetrationSquareVsPoint(centre: Vec2, half: Float, point: Vec2, outNormal: Vec2): Float {
        val dx = point.x - centre.x
        val dy = point.y - centre.y
        val penY = half - abs(dy)
        if (penY > 0f) {
            val penX = half - abs(dx)
            if (penX > 0f) {
                return if (penY <= penX) {
                    outNormal.set(0f, if (dy <= 0f) -1f else 1f); penY
                } else {
                    outNormal.set(if (dx <= 0f) -1f else 1f, 0f); penX
                }
            }
        }
        return 0f
    }

    fun overlapCircleVsSegment(
        circlePos: Vec2, r: Float,
        segA: Vec2, segB: Vec2, segLen: Float
    ): Boolean {
        val dx = circlePos.x - segA.x
        val dy = circlePos.y - segA.y
        val edX = (segB.x - segA.x) / segLen
        val edY = (segB.y - segA.y) / segLen
        val proj = dx * edX + dy * edY
        val cx: Float; val cy: Float
        when {
            proj <= 0f -> { cx = segA.x; cy = segA.y }
            proj >= segLen -> { cx = segB.x; cy = segB.y }
            else -> { cx = segA.x + proj * edX; cy = segA.y + proj * edY }
        }
        val diffX = circlePos.x - cx
        val diffY = circlePos.y - cy
        return diffX * diffX + diffY * diffY < r * r
    }

    fun overlapCircleVsCircle(posA: Vec2, rA: Float, posB: Vec2, rB: Float): Boolean {
        val dx = posB.x - posA.x
        val dy = posB.y - posA.y
        val sumR = rA + rB
        return dx * dx + dy * dy < sumR * sumR
    }

    /**
     * Returns normalised time [0,1] of first intersection of moving circle A (radius [r])
     * vs stationary circle B (zero radius). Returns 2 if no intersection within [0,1].
     */
    fun timeOfIntersectionCircleVsCircle(
        posA: Vec2, velA: Vec2,
        posB: Vec2, velB: Vec2,
        r: Float
    ): Float {
        val rvx = velA.x - velB.x
        val rvy = velA.y - velB.y
        val rpx = posA.x - posB.x
        val rpy = posA.y - posB.y
        val a = rvx * rvx + rvy * rvy
        val b = 2f * (rpx * rvx + rpy * rvy)
        val c = rpx * rpx + rpy * rpy - r * r
        val eps = 0.0001f
        if (c <= 0f) return -1f
        if (abs(a) < eps) return 2f
        if (b >= 0f) return 2f
        val disc = b * b - 4f * a * c
        if (disc < 0f) return 2f
        val q = -0.5f * (b - sqrt(disc))
        return min(q / a, c / q)
    }

    fun timeOfIntersectionPointVsLineseg(
        pos: Vec2, vel: Vec2,
        segA: Vec2, segB: Vec2,
        r: Float
    ): Float {
        var edX = segB.x - segA.x
        var edY = segB.y - segA.y
        val len = sqrt(edX * edX + edY * edY)
        edX /= len; edY /= len
        val nX = -edY; val nY = edX
        val dpX = pos.x - segA.x
        val dpY = pos.y - segA.y
        val dist = nX * dpX + nY * dpY
        val velDotN = nX * vel.x + nY * vel.y
        val along = edX * dpX + edY * dpY
        val absDist = abs(dist)
        val pen = absDist - r
        if (pen < 0f) {
            return if (along < 0f || along > len) 2f else -1f
        }
        if (dist * velDotN >= 0f) return 2f
        val t = pen / abs(velDotN)
        val alongAtT = along + t * (edX * vel.x + edY * vel.y)
        return if (alongAtT < 0f || alongAtT > len) 2f else t
    }

    fun timeOfIntersectionCircleVsArc(
        pos: Vec2, vel: Vec2,
        arcCentre: Vec2, arcP0: Vec2, arcP1: Vec2,
        r: Float
    ): Float {
        val dx = arcP0.x - arcCentre.x
        val dy = arcP0.y - arcCentre.y
        val arcR = sqrt(dx * dx + dy * dy)
        val t0 = toiCircleVsArcHelper(pos, vel, arcCentre, arcP0, arcP1, arcR + r)
        val t1 = toiCircleVsArcHelper(pos, vel, arcCentre, arcP0, arcP1, arcR - r)
        return min(t0, t1)
    }

    private fun toiCircleVsArcHelper(
        pos: Vec2, vel: Vec2,
        arcCentre: Vec2, arcP0: Vec2, arcP1: Vec2,
        testR: Float
    ): Float {
        val rx = pos.x - arcCentre.x
        val ry = pos.y - arcCentre.y
        val a = vel.dot(vel)
        val b = 2f * (rx * vel.x + ry * vel.y)
        val c = rx * rx + ry * ry - testR * testR
        val eps = 0.0001f
        if (abs(a) < eps) return 2f
        val disc = b * b - 4f * a * c
        if (disc < 0f) return 2f
        val q = -0.5f * (b - sqrt(disc))
        var t0 = q / a
        var t1 = c / q

        // Check both times are within arc angular range
        val e0x = arcP0.x - arcCentre.x; val e0y = arcP0.y - arcCentre.y
        val e1x = arcP1.x - arcCentre.x; val e1y = arcP1.y - arcCentre.y
        val spanX = arcP1.x - arcP0.x;   val spanY = arcP1.y - arcP0.y
        val crossSpanE0 = spanX * -e0y + spanY * e0x
        val crossSpanE1 = spanX * -e1y + spanY * e1x

        if (t0 < 0f) t0 = 2f
        if (t1 < 0f) t1 = 2f

        if (t0 <= 1f) {
            val hx = pos.x + t0 * vel.x - arcCentre.x
            val hy = pos.y + t0 * vel.y - arcCentre.y
            val c0 = hx * -e0y + hy * e0x
            val c1 = hx * -e1y + hy * e1x
            if (c0 * crossSpanE0 <= 0f || c1 * crossSpanE1 >= 0f) t0 = 2f
        }
        if (t1 <= 1f) {
            val hx = pos.x + t1 * vel.x - arcCentre.x
            val hy = pos.y + t1 * vel.y - arcCentre.y
            val c0 = hx * -e0y + hy * e0x
            val c1 = hx * -e1y + hy * e1x
            if (c0 * crossSpanE0 <= 0f || c1 * crossSpanE1 >= 0f) t1 = 2f
        }
        return min(t0, t1)
    }
}
