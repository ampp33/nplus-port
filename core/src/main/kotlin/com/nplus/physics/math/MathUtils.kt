package com.nplus.physics.math

import kotlin.math.*

/**
 * Port of AS3 mathutils. The PRNG is a Park-Miller LCG (multiplier 16807, modulus 2^31-1).
 * Seed arithmetic uses Long to avoid signed 32-bit overflow.
 */
object MathUtils {

    private var rndSeed: Long = 1L

    fun setRandomSeed(seed: Int) {
        rndSeed = if (seed == 0 || seed > 2147483646) 1L else seed.toLong()
    }

    fun generateNewRandomSeed() {
        setRandomSeed((1 + (Math.random() * 2147483645.0)).toInt())
    }

    fun getRandomSeed(): Int = rndSeed.toInt()

    fun random(): Float = generate().toFloat() / 2147483647f

    private fun generate(): Long {
        rndSeed = rndSeed * 16807L % 2147483647L
        return rndSeed
    }

    // --- Angle utilities ---

    fun degToRad(degrees: Float): Float = degrees * (PI.toFloat() / 180f)
    fun radToDeg(radians: Float): Float = radians * (180f / PI.toFloat())

    fun wrapAngleShortest(angle: Float): Float {
        var a = angle
        val pi = PI.toFloat()
        while (abs(a) > pi) a += if (a < 0f) 2f * pi else -2f * pi
        return a
    }

    fun wrapAnglePos(angle: Float): Float {
        var a = angle
        val twoPi = 2f * PI.toFloat()
        while (a < 0f || a > twoPi) a += if (a < 0f) twoPi else -twoPi
        return a
    }

    fun wrapAngleDirected(angle: Float, dir: Float): Float {
        if (dir > 0f) return wrapAnglePos(angle)
        if (dir < 0f) {
            var a = angle
            val twoPi = 2f * PI.toFloat()
            while (a > 0f || a < -twoPi) a += if (a > 0f) -twoPi else twoPi
            return a
        }
        return 0f
    }

    /**
     * Interpolate from [from] toward [to] by fraction [t].
     * [dir] = 0 → shortest arc; > 0 → positive direction; < 0 → negative direction.
     */
    fun interpolateOrn(from: Float, to: Float, t: Float, dir: Float = 0f): Float {
        val a = wrapAnglePos(from)
        val b = wrapAnglePos(to)
        var delta = b - a
        delta = if (dir == 0f) wrapAngleShortest(delta) else wrapAngleDirected(delta, dir)
        return a + delta * t
    }
}
