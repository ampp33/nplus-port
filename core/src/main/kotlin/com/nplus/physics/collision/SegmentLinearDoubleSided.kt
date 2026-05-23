package com.nplus.physics.collision

import com.nplus.physics.math.Vec2

/** Port of AS3 Segment_Linear_DoubleSided — identical to SegmentLinear but never backfacing. */
class SegmentLinearDoubleSided(x0: Float, y0: Float, x1: Float, y1: Float)
    : SegmentLinear(x0, y0, x1, y1) {

    override fun getClosestPointIsBackfacing(point: Vec2, out: Vec2): Boolean {
        super.getClosestPointIsBackfacing(point, out)
        return false
    }
}
