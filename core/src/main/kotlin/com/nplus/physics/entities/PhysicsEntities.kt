package com.nplus.physics.entities

import com.nplus.SimGlobals
import com.nplus.physics.Ninja
import com.nplus.physics.Simulator
import com.nplus.physics.collision.ColUtils
import com.nplus.physics.collision.GridEntity
import com.nplus.physics.math.Vec2
import kotlin.math.*

// ---------------------------------------------------------------------------
// BounceBlock — spring-anchored square that deflects the ninja
// ---------------------------------------------------------------------------

class BounceBlockEntity(private val objGrid: GridEntity, x: Float, y: Float) : EntityBase() {
    private val pos    = Vec2(x, y)
    private val vel    = Vec2()
    private val anchor = Vec2(x, y)
    private val r      = 0.8f * 12f
    private val stiff  = 0.05f * (40f / SimGlobals.SIM_RATE).pow(2)
    private val damp   = if (SimGlobals.SIM_RATE == 60f) 0.98f else 0.99f
    private val mass   = 0.2f
    private var sleeping = true
    private val n = Vec2()

    init { objGrid.entityAdd(pos, this) }

    override fun collideVsCirclePhysical(result: CollisionResultPhysical,
        pos: Vec2, vel: Vec2, normal: Vec2, radius: Float): Boolean {
        n.set(0f, 0f)
        val pen = ColUtils.penetrationSquareVsPoint(this.pos, r + radius, pos, n)
        if (pen != 0f) {
            val push = (1f - mass) * pen
            this.pos.x -= push * n.x; this.vel.x -= push * n.x
            this.pos.y -= push * n.y; this.vel.y -= push * n.y
            result.isHardCollision = false
            result.nx = n.x; result.ny = n.y; result.pen = mass * pen
            sleeping = false
            return true
        }
        return false
    }

    override fun collideVsCircleLogical(sim: Simulator, ninja: Ninja?, result: CollisionResultLogical,
        pos: Vec2, vel: Vec2, normal: Vec2, radius: Float, eps: Float): Boolean {
        if (ninja != null) {
            n.set(0f, 0f)
            val pen = ColUtils.penetrationSquareVsPoint(this.pos, eps + r + radius, pos, n)
            if (pen != 0f) {
                result.vecX = n.x; result.vecY = n.y; return true
            }
        }
        return false
    }

    override fun think(sim: Simulator) {
        if (!sleeping) {
            val dx = anchor.x - pos.x; val dy = anchor.y - pos.y
            if (vel.lenSq() < 0.05f && dx * dx + dy * dy < 0.05f) {
                pos.set(anchor); vel.set(0f, 0f); sleeping = true
            }
        }
    }

    override fun move(sim: Simulator) {
        vel.scale(damp); pos.x += vel.x; pos.y += vel.y
        val sx = (anchor.x - pos.x) * stiff; val sy = (anchor.y - pos.y) * stiff
        pos.x += sx; pos.y += sy; vel.x += sx; vel.y += sy
        sim.objGrid.entityMove(pos, this)
    }

    fun getPos(): Vec2 = pos
    fun isSleeping() = sleeping
}

// ---------------------------------------------------------------------------
// OnewayPlatform — single-sided platform; only blocks from one direction
// ---------------------------------------------------------------------------

class OnewayPlatformEntity(objGrid: GridEntity, x: Float, y: Float, nx: Float, ny: Float) : EntityBase() {
    private val pos    = Vec2(x, y)
    private val normal = Vec2(nx, ny)
    private val r      = 12f

    init { objGrid.entityAdd(pos, this) }

    override fun collideVsCirclePhysical(result: CollisionResultPhysical,
        pos: Vec2, vel: Vec2, normal: Vec2, radius: Float): Boolean {
        val pen = calculatePenetration(pos, vel, normal, radius, 0f)
        if (pen >= 0f) {
            result.isHardCollision = true
            result.nx = this.normal.x; result.ny = this.normal.y; result.pen = pen
            return true
        }
        return false
    }

    override fun collideVsCircleLogical(sim: Simulator, ninja: Ninja?, result: CollisionResultLogical,
        pos: Vec2, vel: Vec2, normal: Vec2, radius: Float, eps: Float): Boolean {
        if (ninja != null) {
            val pen = calculatePenetration(pos, vel, normal, radius, eps)
            if (pen >= 0f) { result.vecX = this.normal.x; result.vecY = this.normal.y; return true }
        }
        return false
    }

    private fun calculatePenetration(pos: Vec2, vel: Vec2, old: Vec2, radius: Float, eps: Float): Float {
        val dx = pos.x - this.pos.x; val dy = pos.y - this.pos.y
        val perpPen = r + radius - abs(-normal.y * dx + normal.x * dy)
        if (perpPen <= 0f) return -1f
        val normPen = radius + eps - abs(normal.x * dx + normal.y * dy)
        if (normPen <= 0f) return -1f
        val velDot = normal.x * vel.x + normal.y * vel.y
        if (velDot > 0f) return -1f
        val odx = old.x - this.pos.x; val ody = old.y - this.pos.y
        val oldPen = radius - (normal.x * odx + normal.y * ody)
        if (oldPen > 1.1f) return -1f
        val curPen = radius - (normal.x * dx + normal.y * dy)
        return if (curPen < 0f) -1f else curPen
    }

    fun getPos(): Vec2 = pos
    fun getNormal(): Vec2 = normal
}

private fun Float.pow(exp: Float): Float = this.toDouble().pow(exp.toDouble()).toFloat()
