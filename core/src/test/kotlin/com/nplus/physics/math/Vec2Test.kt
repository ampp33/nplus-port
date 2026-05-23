package com.nplus.physics.math

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlin.math.sqrt

class Vec2Test : FunSpec({

    val eps = 1e-5f

    test("plus returns new Vec2 with summed components") {
        val a = Vec2(1f, 2f)
        val b = Vec2(3f, 4f)
        val c = a + b
        c.x shouldBe 4f
        c.y shouldBe 6f
        // originals unchanged
        a.x shouldBe 1f
        b.x shouldBe 3f
    }

    test("minus returns new Vec2 with subtracted components") {
        val r = Vec2(5f, 3f) - Vec2(2f, 1f)
        r.x shouldBe 3f
        r.y shouldBe 2f
    }

    test("times scalar") {
        val r = Vec2(2f, -3f) * 4f
        r.x shouldBe 8f
        r.y shouldBe -12f
    }

    test("float times vec2 (commutative)") {
        val r = 3f * Vec2(2f, 5f)
        r.x shouldBe 6f
        r.y shouldBe 15f
    }

    test("unaryMinus") {
        val r = -Vec2(1f, -2f)
        r.x shouldBe -1f
        r.y shouldBe 2f
    }

    test("dot product") {
        Vec2(3f, 4f).dot(Vec2(2f, 1f)) shouldBe (3f * 2f + 4f * 1f)
    }

    test("perp rotates 90 degrees CCW") {
        val p = Vec2(1f, 0f).perp()
        p.x shouldBe 0f.plusOrMinus(eps)
        p.y shouldBe 1f.plusOrMinus(eps)
    }

    test("perpDot") {
        // perpDot of parallel vectors is 0
        Vec2(2f, 0f).perpDot(Vec2(5f, 0f)) shouldBe 0f.plusOrMinus(eps)
        // perpDot of perpendicular vectors equals product of lengths
        Vec2(1f, 0f).perpDot(Vec2(0f, 1f)) shouldBe 1f.plusOrMinus(eps)
    }

    test("lenSq and len") {
        val v = Vec2(3f, 4f)
        v.lenSq() shouldBe 25f
        v.len() shouldBe 5f.plusOrMinus(eps)
    }

    test("normalized returns unit vector") {
        val v = Vec2(3f, 4f).normalized()
        v.len() shouldBe 1f.plusOrMinus(eps)
        v.x shouldBe (3f / 5f).plusOrMinus(eps)
        v.y shouldBe (4f / 5f).plusOrMinus(eps)
    }

    test("normalized of zero vector returns zero vector without crash") {
        val v = Vec2(0f, 0f).normalized()
        v.x shouldBe 0f
        v.y shouldBe 0f
    }

    test("to produces vector from this to other") {
        val r = Vec2(1f, 1f).to(Vec2(4f, 5f))
        r.x shouldBe 3f
        r.y shouldBe 4f
    }

    test("set(other) mutates in place") {
        val v = Vec2(0f, 0f)
        v.set(Vec2(7f, 8f))
        v.x shouldBe 7f
        v.y shouldBe 8f
    }

    test("normalize mutates to unit length") {
        val v = Vec2(3f, 4f)
        v.normalize()
        v.len() shouldBe 1f.plusOrMinus(eps)
    }

    test("normalize of zero vector is safe") {
        val v = Vec2(0f, 0f)
        v.normalize()
        v.x shouldBe 0f
        v.y shouldBe 0f
    }

    test("scale mutates by factor") {
        val v = Vec2(2f, 3f)
        v.scale(2f)
        v.x shouldBe 4f
        v.y shouldBe 6f
    }

    test("fromDir produces correct unit vector") {
        val v = Vec2.fromDir(0f)
        v.x shouldBe 1f.plusOrMinus(eps)
        v.y shouldBe 0f.plusOrMinus(eps)
    }

    test("vecTo companion function") {
        val r = Vec2.vecTo(Vec2(1f, 2f), Vec2(4f, 6f))
        r.x shouldBe 3f
        r.y shouldBe 4f
    }
})
