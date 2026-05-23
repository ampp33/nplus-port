package com.nplus.physics.collision

import com.nplus.physics.math.Vec2
import kotlin.math.*

open class SegmentLinear(x0: Float, y0: Float, x1: Float, y1: Float) : Segment {

    protected val p0 = Vec2(x0, y0)
    protected val p1 = Vec2(x1, y1)
    private val aabb = Aabb(min(x0, x1), min(y0, y1), max(x0, x1), max(y0, y1))

    override fun getAabb(): Aabb = aabb

    override fun getClosestPoint(point: Vec2, out: Vec2) {
        val dx = p1.x - p0.x; val dy = p1.y - p0.y
        val px = point.x - p0.x; val py = point.y - p0.y
        val dot = dx * px + dy * py
        val lenSq = dx * dx + dy * dy
        when {
            dot <= 0f    -> out.set(p0)
            dot >= lenSq -> out.set(p1)
            else         -> out.set(p0.x + (dot / lenSq) * dx, p0.y + (dot / lenSq) * dy)
        }
    }

    override fun getClosestPointIsBackfacing(point: Vec2, out: Vec2): Boolean {
        val dx = p1.x - p0.x; val dy = p1.y - p0.y
        val px = point.x - p0.x; val py = point.y - p0.y
        val dot = dx * px + dy * py
        val lenSq = dx * dx + dy * dy
        when {
            dot <= 0f    -> out.set(p0)
            dot >= lenSq -> out.set(p1)
            else         -> out.set(p0.x + (dot / lenSq) * dx, p0.y + (dot / lenSq) * dy)
        }
        // Positive cross product means point is to the left (frontfacing); negative = backfacing.
        return px * -dy + py * dx < 0f
    }

    override fun intersectWithRay(pos: Vec2, vel: Vec2, radius: Float, outPoint: Vec2, outNormal: Vec2): Float {
        val t0 = ColUtils.timeOfIntersectionCircleVsCircle(pos, vel, p0, VEC_ZERO, radius)
        val t1 = ColUtils.timeOfIntersectionCircleVsCircle(pos, vel, p1, VEC_ZERO, radius)
        val t2 = ColUtils.timeOfIntersectionPointVsLineseg(pos, vel, p0, p1, radius)
        val t = minOf(t0, t1, t2)
        if (t in 0f..1f) {
            val rpx = pos.x + t * vel.x
            val rpy = pos.y + t * vel.y
            if (radius > 0f) {
                scratchCp.set(0f, 0f)
                getClosestPoint(Vec2(rpx, rpy), scratchCp)
                var nx = rpx - scratchCp.x; var ny = rpy - scratchCp.y
                val nl = sqrt(nx * nx + ny * ny)
                nx /= nl; ny /= nl
                outPoint.set(scratchCp); outNormal.set(nx, ny)
            } else {
                var nx = -(p1.y - p0.y); var ny = p1.x - p0.x
                val nl = sqrt(nx * nx + ny * ny)
                nx /= nl; ny /= nl
                if (nx * vel.x + ny * vel.y > 0f) { nx = -nx; ny = -ny }
                outPoint.set(rpx, rpy); outNormal.set(nx, ny)
            }
        }
        return t
    }

    companion object {
        @JvmStatic protected val VEC_ZERO = Vec2(0f, 0f)
        @JvmStatic protected val scratchCp = Vec2()
    }
}
