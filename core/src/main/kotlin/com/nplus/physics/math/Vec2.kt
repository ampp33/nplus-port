package com.nplus.physics.math

import kotlin.math.sqrt
import kotlin.math.cos
import kotlin.math.sin

/**
 * Mutable 2D vector. Matches AS3 vec2 semantics: operator methods return new instances;
 * set/normalize/scale mutate in place (used by physics loop scratch-pad patterns).
 */
data class Vec2(var x: Float = 0f, var y: Float = 0f) {

    // --- Immutable operators ---

    operator fun plus(other: Vec2) = Vec2(x + other.x, y + other.y)
    operator fun minus(other: Vec2) = Vec2(x - other.x, y - other.y)
    operator fun times(scalar: Float) = Vec2(x * scalar, y * scalar)
    operator fun unaryMinus() = Vec2(-x, -y)

    // --- Query methods (return new Vec2 or scalar) ---

    fun dot(other: Vec2): Float = x * other.x + y * other.y
    fun perp(): Vec2 = Vec2(-y, x)

    /** Cross-product-z: -y*ox + x*oy */
    fun perpDot(other: Vec2): Float = -y * other.x + x * other.y

    fun lenSq(): Float = x * x + y * y
    fun len(): Float = sqrt(x * x + y * y)

    fun normalized(): Vec2 {
        val l = len()
        return if (l != 0f) Vec2(x / l, y / l) else Vec2()
    }

    /** Vector from this point to [other]. */
    fun to(other: Vec2): Vec2 = Vec2(other.x - x, other.y - y)

    // --- Mutating operations (AS3: Copy / Normalize / Scale) ---

    fun set(other: Vec2) { x = other.x; y = other.y }
    fun set(nx: Float, ny: Float) { x = nx; y = ny }

    fun normalize() {
        val l = sqrt(x * x + y * y)
        if (l != 0f) { x /= l; y /= l }
    }

    fun scale(f: Float) { x *= f; y *= f }

    override fun toString(): String = "($x,$y)"

    companion object {
        /** Unit vector pointing in [angle] radians. */
        fun fromDir(angle: Float): Vec2 = Vec2(cos(angle), sin(angle))

        /** Vector from [from] to [to]. */
        fun vecTo(from: Vec2, to: Vec2): Vec2 = Vec2(to.x - from.x, to.y - from.y)
    }
}

/** Allows `2f * vec` as well as `vec * 2f`. */
operator fun Float.times(v: Vec2): Vec2 = Vec2(this * v.x, this * v.y)
