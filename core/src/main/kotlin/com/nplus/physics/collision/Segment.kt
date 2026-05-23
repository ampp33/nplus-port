package com.nplus.physics.collision

import com.nplus.physics.math.Vec2

/**
 * Port of AS3 Segment interface. DebugDraw methods are omitted — rendering is handled
 * separately by the GameRenderer in step 14.
 */
interface Segment {
    fun getAabb(): Aabb
    fun getClosestPoint(point: Vec2, out: Vec2)
    fun getClosestPointIsBackfacing(point: Vec2, out: Vec2): Boolean
    fun intersectWithRay(pos: Vec2, vel: Vec2, radius: Float, outPoint: Vec2, outNormal: Vec2): Float
}
