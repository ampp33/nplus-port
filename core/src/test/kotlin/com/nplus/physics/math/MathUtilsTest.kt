package com.nplus.physics.math

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.math.PI

class MathUtilsTest : FunSpec({

    val eps = 1e-5f

    // Reset seed before each test so tests are independent
    beforeEach { MathUtils.setRandomSeed(1) }

    test("PRNG with seed 1 produces deterministic sequence") {
        MathUtils.setRandomSeed(1)
        val a = MathUtils.random()
        MathUtils.setRandomSeed(1)
        val b = MathUtils.random()
        a shouldBe b
    }

    test("PRNG random values are in [0, 1)") {
        MathUtils.setRandomSeed(42)
        repeat(1000) {
            val r = MathUtils.random()
            (r >= 0f) shouldBe true
            (r < 1f) shouldBe true
        }
    }

    test("PRNG seed 0 is clamped to 1") {
        MathUtils.setRandomSeed(0)
        MathUtils.getRandomSeed() shouldBe 1
    }

    test("PRNG seed > 2147483646 is clamped to 1") {
        MathUtils.setRandomSeed(2147483647)
        MathUtils.getRandomSeed() shouldBe 1
    }

    test("degToRad and radToDeg are inverses") {
        val deg = 45f
        MathUtils.radToDeg(MathUtils.degToRad(deg)) shouldBe deg.plusOrMinus(eps)
    }

    test("degToRad 180 = PI") {
        MathUtils.degToRad(180f) shouldBe PI.toFloat().plusOrMinus(eps)
    }

    test("wrapAngleShortest keeps angle in (-PI, PI]") {
        val pi = PI.toFloat()
        MathUtils.wrapAngleShortest(3f * pi) shouldBe pi.plusOrMinus(eps)
        MathUtils.wrapAngleShortest(-3f * pi) shouldBe (-pi).plusOrMinus(eps)
        MathUtils.wrapAngleShortest(0f) shouldBe 0f.plusOrMinus(eps)
    }

    test("wrapAnglePos keeps angle in [0, 2PI)") {
        val twoPi = 2f * PI.toFloat()
        val result = MathUtils.wrapAnglePos(-PI.toFloat())
        (result >= 0f) shouldBe true
        (result <= twoPi) shouldBe true
    }
})
