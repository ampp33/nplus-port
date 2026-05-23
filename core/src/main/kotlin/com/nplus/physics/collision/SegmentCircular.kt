package com.nplus.physics.collision

import com.nplus.physics.math.Vec2
import kotlin.math.*

/**
 * Port of AS3 Segment_Circular — a convex arc defined by centre (pC) and two endpoints (p0, p1).
 * The arc is always convex (outward-facing normal points away from pC).
 */
class SegmentCircular(
    cX: Float, cY: Float,
    x0: Float, y0: Float,
    x1: Float, y1: Float
) : Segment {

    private val pC = Vec2(cX, cY)
    private val p0 = Vec2(x0, y0)
    private val p1 = Vec2(x1, y1)
    private val aabb = Aabb(min(x0, x1), min(y0, y1), max(x0, x1), max(y0, y1))

    private val scratchCp   = Vec2()
    private val scratchRp   = Vec2()

    override fun getAabb(): Aabb = aabb

    override fun getClosestPoint(point: Vec2, out: Vec2) {
        val e0x = p0.x - pC.x; val e0y = p0.y - pC.y
        val e1x = p1.x - pC.x; val e1y = p1.y - pC.y
        val qx  = point.x - pC.x; val qy = point.y - pC.y
        val spanX = p1.x - p0.x;  val spanY = p1.y - p0.y

        // Angular position of query point relative to each arc endpoint
        val cross0 = qx * -e0y + qy * e0x
        val cross1 = qx * -e1y + qy * e1x
        val crossSpan0 = spanX * -e0y + spanY * e0x
        val crossSpan1 = spanX * -e1y + spanY * e1x

        val pastP0 = cross0 * crossSpan0 <= 0f
        val pastP1 = cross1 * crossSpan1 >= 0f

        when {
            pastP0 && pastP1 -> {
                if (qx * spanX + qy * spanY <= 0f) out.set(p0) else out.set(p1)
            }
            pastP0 -> out.set(p0)
            pastP1 -> out.set(p1)
            else -> {
                val arcR = sqrt(e0x * e0x + e0y * e0y)
                val qLen = sqrt(qx * qx + qy * qy)
                out.set(pC.x + (qx / qLen) * arcR, pC.y + (qy / qLen) * arcR)
            }
        }
    }

    override fun getClosestPointIsBackfacing(point: Vec2, out: Vec2): Boolean {
        val e0x = p0.x - pC.x; val e0y = p0.y - pC.y
        val e1x = p1.x - pC.x; val e1y = p1.y - pC.y
        val qx  = point.x - pC.x; val qy = point.y - pC.y
        val spanX = p1.x - p0.x;  val spanY = p1.y - p0.y

        val cross0 = qx * -e0y + qy * e0x
        val cross1 = qx * -e1y + qy * e1x
        val crossSpan0 = spanX * -e0y + spanY * e0x
        val crossSpan1 = spanX * -e1y + spanY * e1x

        val pastP0 = cross0 * crossSpan0 <= 0f
        val pastP1 = cross1 * crossSpan1 >= 0f

        var endpt = -1 // -1 = on arc, 0 = at p0, 1 = at p1
        when {
            pastP0 && pastP1 -> {
                if (qx * spanX + qy * spanY <= 0f) { out.set(p0); endpt = 0 }
                else { out.set(p1); endpt = 1 }
            }
            pastP0 -> { out.set(p0); endpt = 0 }
            pastP1 -> { out.set(p1); endpt = 1 }
            else -> {
                val arcR = sqrt(e0x * e0x + e0y * e0y)
                val qLen = sqrt(qx * qx + qy * qy)
                out.set(pC.x + (qx / qLen) * arcR, pC.y + (qy / qLen) * arcR)
            }
        }

        val dx = out.x - point.x; val dy = out.y - point.y
        return if (endpt < 0) {
            dx * -spanY + dy * spanX > 0f
        } else {
            var ex = if (endpt == 0) e0x else e1x
            var ey = if (endpt == 0) e0y else e1y
            // Ensure endpoint tangent normal points outward
            if (ex * -spanY + ey * spanX < 0f) { ex = -ex; ey = -ey }
            dx * ex + dy * ey > 0f
        }
    }

    override fun intersectWithRay(pos: Vec2, vel: Vec2, radius: Float, outPoint: Vec2, outNormal: Vec2): Float {
        val t0 = ColUtils.timeOfIntersectionCircleVsCircle(pos, vel, p0, VEC_ZERO, radius)
        val t1 = ColUtils.timeOfIntersectionCircleVsCircle(pos, vel, p1, VEC_ZERO, radius)
        // Check if already inside arc region
        val t2: Float
        getClosestPoint(pos, scratchCp)
        val cdx = pos.x - scratchCp.x; val cdy = pos.y - scratchCp.y
        t2 = if (cdx * cdx + cdy * cdy <= radius * radius) {
            -1f
        } else {
            ColUtils.timeOfIntersectionCircleVsArc(pos, vel, pC, p0, p1, radius)
        }
        val t = minOf(t0, t1, t2)
        if (t in 0f..1f) {
            val rpx = pos.x + t * vel.x; val rpy = pos.y + t * vel.y
            scratchRp.set(rpx, rpy)
            if (radius > 0f) {
                getClosestPoint(scratchRp, scratchCp)
                var nx = rpx - scratchCp.x; var ny = rpy - scratchCp.y
                val nl = sqrt(nx * nx + ny * ny)
                nx /= nl; ny /= nl
                outPoint.set(scratchCp); outNormal.set(nx, ny)
            } else {
                var nx = rpx - pC.x; var ny = rpy - pC.y
                val nl = sqrt(nx * nx + ny * ny)
                nx /= nl; ny /= nl
                if (nx * vel.x + ny * vel.y > 0f) { nx = -nx; ny = -ny }
                outPoint.set(scratchRp); outNormal.set(nx, ny)
            }
        }
        return t
    }

    companion object {
        private val VEC_ZERO = Vec2(0f, 0f)
    }
}
