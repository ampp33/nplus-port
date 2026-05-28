package com.nplus.physics.ninja

import com.nplus.physics.Simulator
import com.nplus.physics.collision.ColUtils
import com.nplus.physics.entities.CollisionResultLogical
import com.nplus.physics.entities.CollisionResultPhysical
import com.nplus.physics.entities.EntityBase
import com.nplus.physics.math.MathUtils
import com.nplus.physics.math.Vec2
import kotlin.math.*

/** Port of AS3 Ragdoll — Verlet particle ragdoll activated on ninja death. */
class Ragdoll {

    // --- Static constants (verbatim from AS3) ---
    companion object {
        private const val HACKY_SCALE = 0.2f

        private val PARTICLE_RADIUS  = floatArrayOf(2.49f, 2.49f, 1.99f, 1.99f, 2.99f, 2.99f)
        private val PARTICLE_DRAG    = floatArrayOf(0.99f, 0.995f, 0.995f, 0.99f, 0.99f, 0.995f)
        private val STICK_WEIGHT     = floatArrayOf(0.4f, 0.2f, 0.26f, 0.32f, 0.37f)
        private val STICK_MIN_RATIO  = floatArrayOf(0.8f, 0.6f, 0.6f, 0.6f, 0.6f)
        private val STICK_MAX_LEN    = floatArrayOf(
            30f * HACKY_SCALE, 40f * HACKY_SCALE, 40f * HACKY_SCALE,
            60f * HACKY_SCALE, 60f * HACKY_SCALE
        )

        // Default pose used when no graphics pose is available
        private val DEBUG_POS: Array<Vec2> = arrayOf(
            Vec2(0f,                                  -STICK_MAX_LEN[0]),
            Vec2(0f,                                   0f),
            Vec2(STICK_MAX_LEN[1],                    -STICK_MAX_LEN[0]),
            Vec2(-STICK_MAX_LEN[2],                   -STICK_MAX_LEN[0]),
            Vec2(sqrt(0.5f) * STICK_MAX_LEN[3],       sqrt(0.5f) * STICK_MAX_LEN[3]),
            Vec2(-sqrt(0.5f) * STICK_MAX_LEN[4],      sqrt(0.5f) * STICK_MAX_LEN[3])
        )
        private val DEBUG_VEL: Array<Vec2> = Array(6) { Vec2() }

        private const val STATE_UNEXPLODED = 0
        private const val STATE_EXPLODED   = 1
    }

    private var state = STATE_UNEXPLODED
    private var explosionAccumulator = 0f

    // Particles: [unexploded(6), exploded(10)]
    private val particles: Array<Array<RagParticle>> = arrayOf(
        Array(6)  { i -> RagParticle(PARTICLE_RADIUS[i], PARTICLE_DRAG[i]) },
        Array(10) { i ->
            val idx = if (i < 6) i else (i - 6).coerceAtMost(1)
            RagParticle(PARTICLE_RADIUS[idx], PARTICLE_DRAG[idx])
        }
    )

    // Sticks: [unexploded(5), exploded(5)]
    private val sticks: Array<Array<RagStick>>

    private val objList    = mutableListOf<EntityBase>()
    private val resultLog  = CollisionResultLogical()
    private val resultPhys = CollisionResultPhysical()
    private val cp         = Vec2()

    init {
        val up = particles[STATE_UNEXPLODED]
        sticks = arrayOf(
            arrayOf(
                RagStick(up[1], up[0], STICK_WEIGHT[0], STICK_MIN_RATIO[0], STICK_MAX_LEN[0]),
                RagStick(up[0], up[2], STICK_WEIGHT[1], STICK_MIN_RATIO[1], STICK_MAX_LEN[1]),
                RagStick(up[0], up[3], STICK_WEIGHT[2], STICK_MIN_RATIO[2], STICK_MAX_LEN[2]),
                RagStick(up[1], up[4], STICK_WEIGHT[3], STICK_MIN_RATIO[3], STICK_MAX_LEN[3]),
                RagStick(up[1], up[5], STICK_WEIGHT[4], STICK_MIN_RATIO[4], STICK_MAX_LEN[4])
            ),
            run {
                val ex = particles[STATE_EXPLODED]
                arrayOf(
                    RagStick(ex[1], ex[0], STICK_WEIGHT[0], STICK_MIN_RATIO[0], STICK_MAX_LEN[0]),
                    RagStick(ex[6], ex[2], STICK_WEIGHT[1], STICK_MIN_RATIO[1], STICK_MAX_LEN[1]),
                    RagStick(ex[7], ex[3], STICK_WEIGHT[2], STICK_MIN_RATIO[2], STICK_MAX_LEN[2]),
                    RagStick(ex[8], ex[4], STICK_WEIGHT[3], STICK_MIN_RATIO[3], STICK_MAX_LEN[3]),
                    RagStick(ex[9], ex[5], STICK_WEIGHT[4], STICK_MIN_RATIO[4], STICK_MAX_LEN[4])
                )
            }
        )
    }

    // --- Public API ---

    /** Activates ragdoll. [pose] / [poseVel] are the 6 limb positions/velocities from graphics;
     *  pass null to use the internal debug pose. */
    fun activate(
        pos: Vec2, vel: Vec2,
        deathPos: Vec2, deathForce: Vec2,
        pose: Array<Vec2>?, poseVel: Array<Vec2>?
    ) {
        state = STATE_UNEXPLODED
        explosionAccumulator = 0f
        val pp = pose ?: DEBUG_POS
        val pv = poseVel ?: DEBUG_VEL
        val up = particles[STATE_UNEXPLODED]
        for (i in 0..5) {
            up[i].setState(pos.x + pp[i].x, pos.y + pp[i].y,
                           vel.x + pv[i].x, vel.y + pv[i].y)
        }
        shoveRagdoll(deathPos, deathForce)
    }

    fun explode(sim: Simulator) {
        if (state != STATE_EXPLODED) {
            state = STATE_EXPLODED
            initExplodedParticles()
            val ex = particles[STATE_EXPLODED]
            for (i in 6..9) {
                sim.spawnBloodSpurt(ex[i].pos.x, ex[i].pos.y,
                    Math.random().toFloat() * 8f - 4f,
                    Math.random().toFloat() * 8f - 4f, 3)
            }
        }
    }

    fun unexplode() {
        if (state != STATE_UNEXPLODED) {
            state = STATE_UNEXPLODED
            val up = particles[STATE_UNEXPLODED]
            val ex = particles[STATE_EXPLODED]
            for (i in 0..5) up[i].copyState(ex[i])
        }
    }

    fun integrate(normGrav: Float) {
        for (p in particles[state]) p.preIntegrate(normGrav)
    }

    fun preCollision() {}

    fun solveConstraints() {
        for (s in sticks[state]) s.solve()
    }

    fun postCollision(sim: Simulator) {
        for (p in particles[state]) p.postIntegrate()
        val eps = 0.1f
        resultLog.clear()
        for (p in particles[state]) {
            sim.objGrid.gatherNeighbourhood(p.pos, objList)
            for (entity in objList) {
                if (entity.collideVsCircleLogical(sim, null, resultLog,
                        p.pos, p.vel, p.pos, p.r, eps)) {
                    p.vel.x += resultLog.vecX
                    p.vel.y += resultLog.vecY
                    handleRagdollEntityContact(sim, entity, p)
                }
            }
        }
    }

    fun collideVsObjects(sim: Simulator) {
        resultPhys.clear()
        for (p in particles[state]) {
            sim.objGrid.gatherNeighbourhood(p.solverPos, objList)
            for (entity in objList) {
                if (entity.collideVsCirclePhysical(resultPhys, p.solverPos, p.vel, p.pos, p.r)) {
                    ragRespondToCollision(sim, p, resultPhys.nx, resultPhys.ny, resultPhys.pen)
                }
            }
        }
    }

    fun collideVsTiles(sim: Simulator) {
        val maxIter = 32
        for (p in particles[state]) {
            var iter = 0
            cp.set(0f, 0f)
            while (true) {
                val side = ColUtils.getSingleClosestPointSigned(sim.segGrid, p.solverPos, p.r * 4f, cp)
                if (side == 0) break
                val dx = p.solverPos.x - cp.x; val dy = p.solverPos.y - cp.y
                val dist = sqrt(dx * dx + dy * dy)
                val pen = p.r - side * dist
                if (pen < 1e-7f) break
                if (dist == 0f) return
                ragRespondToCollision(sim, p, dx / dist, dy / dist, side * pen)
                if (++iter >= maxIter) break
            }
        }
    }

    data class StickRenderData(
        val x0: Float, val y0: Float, val x1: Float, val y1: Float,
        /** atan2(y1-y0, x1-x0) in radians */
        val ornRad: Float,
        /** clamp(dist/maxLen, 0, 1) */
        val normLen: Float,
        /** 1-based frame index: 1 + floor(100 * normLen), clamped to 1..101 */
        val frame: Int
    )

    fun getStickRenderData(index: Int): StickRenderData {
        val s = sticks[state][index]
        val dx = s.p1.pos.x - s.p0.pos.x
        val dy = s.p1.pos.y - s.p0.pos.y
        val dist = sqrt(dx * dx + dy * dy)
        val norm = (dist / s.maxLen).coerceIn(0f, 1f)
        val frame = (1 + floor(100f * norm).toInt()).coerceIn(1, 101)
        return StickRenderData(s.p0.pos.x, s.p0.pos.y, s.p1.pos.x, s.p1.pos.y,
            atan2(dy, dx), norm, frame)
    }

    fun getParticlePos(index: Int): Vec2 = particles[state][index].pos

    fun getStickCount(): Int = sticks[state].size

    fun testingSetPosVel(pos: Vec2, vel: Vec2) {
        particles[state][0].pos.set(pos)
        particles[state][0].vel.set(vel)
    }

    val isExploded: Boolean get() = state == STATE_EXPLODED

    // --- Private helpers ---

    private fun shoveRagdoll(origin: Vec2, force: Vec2) {
        val near = 12f; val baseScale = 0.5f; val extraScale = 1.5f
        for (p in particles[STATE_UNEXPLODED]) {
            val dx = p.pos.x - origin.x; val dy = p.pos.y - origin.y
            val dist = sqrt(dx * dx + dy * dy)
            val t = min(1f, dist / near)
            val scale = baseScale + (1f - t) * extraScale
            p.vel.x += force.x * scale
            p.vel.y += force.y * scale
        }
    }

    private fun initExplodedParticles() {
        val up = particles[STATE_UNEXPLODED]; val ex = particles[STATE_EXPLODED]
        for (i in 0..5) ex[i].copyState(up[i])
        ex[6].copyState(up[0]); ex[7].copyState(up[0])
        ex[8].copyState(up[1]); ex[9].copyState(up[1])
        ex[6].vel.x += 2f; ex[7].vel.y += 4f
        ex[8].vel.y -= 6f; ex[9].vel.x -= 8f
    }

    private fun ragRespondToCollision(sim: Simulator, p: RagParticle, nx: Float, ny: Float, pen: Float) {
        p.solverPos.x += pen * nx
        p.solverPos.y += pen * ny
        val dvx = p.solverPos.x - p.pos.x
        val dvy = p.solverPos.y - p.pos.y
        val normalComp  = dvx * nx + dvy * ny
        val tangentComp = dvx * -ny + dvy * nx
        var bounce = 0f; var restitution = 0.05f; var friction = 0f
        if (normalComp < 0f) {
            bounce = 2f; restitution = 0.15f; friction = 1f
            if (normalComp < -3f) {
                sim.spawnRagBloodSpurt(p.solverPos.x, p.solverPos.y,
                    -normalComp * nx, -normalComp * ny)
                val r = Math.random()
                sim.playSoundRagdoll(when {
                    r < 0.33 -> "hard1"; r < 0.66 -> "hard2"; else -> "hard3"
                })
            } else {
                if (tangentComp * tangentComp > 0.7f) {
                    sim.spawnRagDust(p.solverPos, p.r,
                        tangentComp * -ny, tangentComp * nx, tangentComp * tangentComp)
                }
                if (normalComp < -2f) {
                    sim.playSoundRagdoll(if (Math.random() < 0.5) "med1" else "med2")
                } else if (normalComp < -1.2f) {
                    sim.playSoundRagdoll(if (Math.random() < 0.5) "soft1" else "soft2")
                }
            }
        }
        p.pos.x += friction * pen * nx + bounce * normalComp * nx + restitution * tangentComp * -ny
        p.pos.y += friction * pen * ny + bounce * normalComp * ny + restitution * tangentComp * nx
    }

    private fun handleRagdollEntityContact(sim: Simulator, entity: EntityBase, p: RagParticle) {
        // Sound/explosion effects — entity type checks will be refined in step 11
        // (EntityBase.isMine(), isZapper(), etc.)
        if (entity.isMine() && state == STATE_UNEXPLODED) {
            explosionAccumulator += MathUtils.random() * 0.6f
            if (Math.random() < explosionAccumulator) explode(sim)
        } else if (entity.isZapper()) {
            sim.playSoundRagdoll(if (Math.random() < 0.5) "zap1" else "zap2")
        }
    }
}

// ---------------------------------------------------------------------------
// File-private support classes (visible only within this module)
// ---------------------------------------------------------------------------

internal class RagParticle(val r: Float, val drag: Float) {
    val pos       = Vec2()
    val vel       = Vec2()
    val solverPos = Vec2()

    fun preIntegrate(gravity: Float) {
        vel.x *= drag; vel.y *= drag
        vel.y += gravity
        solverPos.set(pos.x + vel.x, pos.y + vel.y)
    }

    fun postIntegrate() {
        vel.set(solverPos.x - pos.x, solverPos.y - pos.y)
        pos.set(solverPos)
    }

    fun setState(px: Float, py: Float, vx: Float, vy: Float) {
        pos.set(px, py); vel.set(vx, vy)
    }

    fun copyState(other: RagParticle) { pos.set(other.pos); vel.set(other.vel) }
}

internal class RagStick(
    val p0: RagParticle, val p1: RagParticle,
    private val w0: Float, minRatio: Float, val maxLen: Float
) {
    private val w1 = 1f - w0
    private val minLen = maxLen * minRatio

    fun solve() {
        val dx = p1.solverPos.x - p0.solverPos.x
        val dy = p1.solverPos.y - p0.solverPos.y
        val dist = sqrt(dx * dx + dy * dy)
        if (dist in minLen..maxLen) return
        val target = dist.coerceIn(minLen, maxLen)
        val excess = dist - target
        val nx = if (dist != 0f) dx / dist else 1f
        val ny = if (dist != 0f) dy / dist else 0f
        p0.solverPos.x += w0 * excess * nx
        p0.solverPos.y += w0 * excess * ny
        p1.solverPos.x -= w1 * excess * nx
        p1.solverPos.y -= w1 * excess * ny
    }
}
