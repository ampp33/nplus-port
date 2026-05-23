package com.nplus.physics.collision

import com.nplus.physics.math.Vec2
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe

class ColUtilsTest : FunSpec({

    val eps = 1e-4f

    // --- Circle vs Circle ToI ---

    test("circles moving apart return 2 (no hit)") {
        val t = ColUtils.timeOfIntersectionCircleVsCircle(
            Vec2(0f, 0f), Vec2(-1f, 0f),
            Vec2(2f, 0f), Vec2(0f, 0f),
            0.5f
        )
        (t > 1f) shouldBe true
    }

    test("circles already overlapping return -1") {
        val t = ColUtils.timeOfIntersectionCircleVsCircle(
            Vec2(0f, 0f), Vec2(1f, 0f),
            Vec2(0f, 0f), Vec2(0f, 0f),
            1f
        )
        t shouldBe -1f
    }

    test("head-on circle collision hits within 0..1") {
        // Circle at x=0 moving at vel=(2,0) toward point at x=1, r=0.5.
        // Touching when |posA(t) - posB| = 0.5 → t=0.25 (verified analytically).
        val t = ColUtils.timeOfIntersectionCircleVsCircle(
            Vec2(0f, 0f), Vec2(2f, 0f),
            Vec2(1f, 0f), Vec2(0f, 0f),
            0.5f
        )
        (t in 0f..1f) shouldBe true
        t shouldBe 0.25f.plusOrMinus(eps)
    }

    // --- Overlap Circle vs Circle ---

    test("overlapping circles return true") {
        ColUtils.overlapCircleVsCircle(Vec2(0f,0f), 1f, Vec2(1f,0f), 1f) shouldBe true
    }

    test("non-overlapping circles return false") {
        ColUtils.overlapCircleVsCircle(Vec2(0f,0f), 0.4f, Vec2(1f,0f), 0.4f) shouldBe false
    }

    // --- Square vs Point penetration ---

    test("point outside square returns 0 penetration") {
        val n = Vec2()
        val pen = ColUtils.penetrationSquareVsPoint(Vec2(0f,0f), 1f, Vec2(2f, 0f), n)
        pen shouldBe 0f
    }

    test("point inside square returns correct penetration and normal") {
        val n = Vec2()
        // Point at (0.1, 0) inside square centred at origin with half=1
        val pen = ColUtils.penetrationSquareVsPoint(Vec2(0f,0f), 1f, Vec2(0.1f, 0f), n)
        (pen > 0f) shouldBe true
        // X-axis penetration (0.9) < Y-axis penetration (1), so normal should be X-aligned
        n.y shouldBe 0f
        kotlin.math.abs(n.x) shouldBe 1f
    }

    // --- Segment closest point ---

    test("SegmentLinear closest point to endpoint") {
        val seg = SegmentLinear(0f, 0f, 10f, 0f)
        val out = Vec2()
        seg.getClosestPoint(Vec2(-1f, 0f), out)
        out.x shouldBe 0f.plusOrMinus(eps)
        out.y shouldBe 0f.plusOrMinus(eps)
    }

    test("SegmentLinear closest point to midpoint") {
        val seg = SegmentLinear(0f, 0f, 10f, 0f)
        val out = Vec2()
        seg.getClosestPoint(Vec2(5f, 3f), out)
        out.x shouldBe 5f.plusOrMinus(eps)
        out.y shouldBe 0f.plusOrMinus(eps)
    }

    test("SegmentLinear backfacing from above a horizontal segment") {
        // Horizontal segment p0=(0,0) → p1=(10,0). Point above (y>0) is frontfacing.
        val seg = SegmentLinear(0f, 0f, 10f, 0f)
        val out = Vec2()
        val bf = seg.getClosestPointIsBackfacing(Vec2(5f, 3f), out)
        bf shouldBe false
    }

    test("SegmentLinear backfacing from below a horizontal segment") {
        val seg = SegmentLinear(0f, 0f, 10f, 0f)
        val out = Vec2()
        val bf = seg.getClosestPointIsBackfacing(Vec2(5f, -3f), out)
        bf shouldBe true
    }

    test("SegmentLinearDoubleSided is never backfacing") {
        val seg = SegmentLinearDoubleSided(0f, 0f, 10f, 0f)
        val out = Vec2()
        val bf = seg.getClosestPointIsBackfacing(Vec2(5f, -3f), out)
        bf shouldBe false
    }

    // --- GridSegment gather ---

    test("GridSegment gathers segments in region") {
        val grid = GridSegment(10, 10, 24f)
        val seg = SegmentLinear(12f, 12f, 36f, 12f)
        grid.addSegToCell(0, 0, seg)
        val out = mutableListOf<Segment>()
        grid.gatherCellContentsFromWorldspaceRegion(0f, 0f, 48f, 48f, out)
        out.contains(seg) shouldBe true
    }
})
