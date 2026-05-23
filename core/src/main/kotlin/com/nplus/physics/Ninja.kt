package com.nplus.physics

import com.nplus.physics.collision.ColUtils
import com.nplus.physics.collision.Segment
import com.nplus.physics.entities.CollisionResultLogical
import com.nplus.physics.entities.CollisionResultPhysical
import com.nplus.physics.entities.EntityBase
import com.nplus.physics.input.InputSource
import com.nplus.physics.math.Vec2
import com.nplus.physics.ninja.Ragdoll
import com.nplus.SimGlobals
import kotlin.math.*

/**
 * Port of AS3 Ninja — the player physics body.
 * All rendering queries go through [NinjaGfxState]; no libGDX imports here.
 *
 * Physics constants are derived from [SimGlobals.SIM_RATE] exactly as in AS3
 * so that changing the tick rate preserves feel.
 */
class Ninja(
    val playerIndex: Int,
    var inputSource: InputSource,
    startX: Float, startY: Float,
    val gfxColor: Int = 0,
    /** Callback for physics-timed sound cues (jump, land, death sounds). No-op by default. */
    private val onSound: (name: String) -> Unit = {}
) {
    // --- State enum ---

    enum class State {
        STANDING, RUNNING, SKIDDING, JUMPING, FALLING, WALL_SLIDING,
        DEAD, AWAITING_DEATH, CELEBRATING, DISABLED;

        val isGroundState: Boolean
            get() = this == STANDING || this == RUNNING || this == SKIDDING
    }

    // --- Physics constants (verbatim formulas from AS3) ---

    val r = 10f
    private val impulseScale     = 40f / SimGlobals.SIM_RATE
    private val maxSpeedAir      = r * 0.5f  * (40f / SimGlobals.SIM_RATE)
    private val maxSpeedGround   = r * 0.5f  * (40f / SimGlobals.SIM_RATE)
    private val groundAccel      = 0.15f * (40f / SimGlobals.SIM_RATE).pow(2)
    private val airAccel         = 0.1f  * (40f / SimGlobals.SIM_RATE).pow(2)
    private val normGrav         = 0.15f * (40f / SimGlobals.SIM_RATE).pow(2)
    private val jumpGrav         = 0.025f* (40f / SimGlobals.SIM_RATE).pow(2)
    private val normDrag         = 0.99f .pow(40f / SimGlobals.SIM_RATE)
    private val winDrag          = 0.8f  .pow(40f / SimGlobals.SIM_RATE)
    private val wallFriction     = 0.87f .pow(40f / SimGlobals.SIM_RATE)
    private val skidFriction     = 0.92f .pow(40f / SimGlobals.SIM_RATE)
    private val standFriction    = 0.8f  .pow(40f / SimGlobals.SIM_RATE)
    private val maxJumpTime      = 30f   * (SimGlobals.SIM_RATE / 40f)
    private val terminalVel      = r * 0.9f * (40f / SimGlobals.SIM_RATE)
    private val jumpAmt          = 1f
    private val jumpYBias        = 2f
    private val crushThreshold   = 0.05f

    // Current gravity/drag (changed on jump)
    private var g = normGrav
    private var d = normDrag

    // --- Physics state ---

    private val pos    = Vec2(startX, startY)
    private val vel    = Vec2()
    private val oldPos = Vec2()
    private val oldVel = Vec2()

    var state: State = State.DISABLED
        private set

    private var facingDir = 1
    private var lastFacing = 1f  // sticky: holds last vel.x direction, never resets (matches AS3)

    // Jump
    private var jumpTimer  = 0f
    private var wasJumpDown = false

    // Contact state (updated each tick in postCollision)
    private var inAir    = true
    private var wasInAir = false
    private var nearWall = false
    private val wallNormal  = Vec2()
    private val floorNormal = Vec2(0f, -1f)

    // Floor contact accumulator (reset in preCollision, incremented in respondToCollision)
    private var floorCount = 1
    private val floorVec   = Vec2()

    // Crush detection
    private val crushVec   = Vec2()
    private var crushDist  = 0f
    private var crushFlag  = false

    // Death info
    private var deathType  = SimGlobals.DEATHTYPE_TIME
    private val deathPos   = Vec2()
    private val deathForce = Vec2()

    // Scratch lists (reused every frame)
    private val objList   = mutableListOf<EntityBase>()
    private val segList   = mutableListOf<Segment>()
    private val wallListX = mutableListOf<Float>()
    private val wallListY = mutableListOf<Float>()

    private val resultLog  = CollisionResultLogical()
    private val resultPhys = CollisionResultPhysical()
    private val cp         = Vec2()
    private val tempV      = Vec2()

    val ragdoll = Ragdoll()

    // --- Public read-only accessors ---

    fun getPos(): Vec2 = pos.copy()
    fun getVel(): Vec2 = vel.copy()
    fun getRadius() = r
    fun isDead()    = state == State.DEAD || state == State.DISABLED

    // --- GFX state snapshot for renderer (no rendering imports here) ---

    data class GfxState(
        val posX: Float, val posY: Float,
        val state: State,
        val inAir: Boolean,
        val facing: Float,
        val orientation: Float,
        val animSpeed: Float,
        val wallNormalX: Float,
        val floorNormalX: Float
    )

    fun snapshotGfxState(): GfxState {
        when {
            vel.x < -0.01f -> lastFacing = -1f
            vel.x >  0.01f -> lastFacing =  1f
        }
        val facing = lastFacing
        return when (state) {
            State.WALL_SLIDING -> GfxState(
                pos.x, pos.y, state, inAir,
                -wallNormal.x, 0f, vel.y, wallNormal.x, floorNormal.x)
            State.DISABLED, State.DEAD, State.AWAITING_DEATH ->
                GfxState(pos.x, pos.y, state, inAir, facing.toFloat(), 0f, 0f, 0f, 0f)
            else -> {
                val orn = if (inAir) 0f
                          else atan2(floorNormal.y, floorNormal.x) + 0.5f * PI.toFloat()
                val speed = if (inAir) vel.y
                            else abs(vel.x * -floorNormal.y + vel.y * floorNormal.x)
                GfxState(pos.x, pos.y, state, inAir, facing, orn, speed, wallNormal.x, floorNormal.x)
            }
        }
    }

    // --- Simulator-called lifecycle methods ---

    fun enable()  { state = State.STANDING }
    fun disable() { state = State.DISABLED }

    /** Apply gravity and drag, advance position. */
    fun integrate() {
        if (state == State.DISABLED) return
        if (state == State.DEAD) {
            ragdoll.integrate(normGrav)
        } else {
            vel.x *= d; vel.y *= d
            vel.y += g
            pos.x += vel.x; pos.y += vel.y
        }
    }

    /** Save pre-collision velocity; reset floor/crush accumulators. */
    fun preCollision() {
        if (state == State.DISABLED) return
        if (state == State.DEAD) { ragdoll.preCollision(); return }
        oldVel.set(vel)
        floorCount = 0; floorVec.set(0f, 0f)
        crushVec.set(0f, 0f); crushDist = 0f; crushFlag = false
    }

    /** Resolve ragdoll Verlet constraints. */
    fun solveInternalConstraints() {
        if (state == State.DEAD) ragdoll.solveConstraints()
    }

    /** Determine on-ground/in-air/near-wall state; check fall and crush deaths. */
    fun postCollision(sim: Simulator) {
        if (state == State.DISABLED) return
        if (state == State.DEAD) { ragdoll.postCollision(sim); return }

        oldPos.set(pos)
        val eps = 0.1f
        wallListX.clear(); wallListY.clear()
        resultLog.clear()

        // Logical entity interactions (triggers, damage, etc.)
        sim.objGrid.gatherNeighbourhood(pos, objList)
        for (entity in objList) {
            if (entity.collideVsCircleLogical(sim, this, resultLog, pos, vel, oldPos, r, eps)) {
                if (resultLog.vecY == 0f) {
                    wallListX.add(resultLog.vecX)
                    wallListY.add(resultLog.vecY)
                }
            }
        }

        // Segment proximity — collect horizontal walls for wall-slide detection
        val searchR = r + eps
        sim.segGrid.gatherCellContentsFromWorldspaceRegion(
            pos.x - searchR, pos.y - searchR,
            pos.x + searchR, pos.y + searchR, segList)
        for (seg in segList) {
            seg.getClosestPoint(pos, cp)
            val dx = pos.x - cp.x; val dy = pos.y - cp.y
            val dist = sqrt(dx * dx + dy * dy)
            if (dy == 0f && dist <= searchR && dist != 0f) {
                wallListX.add(dx / dist)
                wallListY.add(dy / dist)
            }
        }

        wasInAir = inAir
        inAir    = true
        nearWall = false

        if (wallListX.isNotEmpty()) {
            nearWall = true
            wallNormal.set(wallListX[0], 0f)
        }

        if (floorCount > 0) {
            inAir = false
            val fLen = floorVec.len()
            if (fLen == 0f) {
                floorNormal.set(0f, -1f)
            } else {
                floorNormal.set(floorVec.x / fLen, floorVec.y / fLen)
            }
            if (wasInAir) {
                var impact = oldVel.x * floorNormal.x + oldVel.y * floorNormal.y
                impact -= 2f * abs(floorNormal.y) * impulseScale
                if (impact < -terminalVel) {
                    vel.set(oldVel)
                    sim.eventPlayerWasKilled(this, SimGlobals.ENEMYTYPE_FALL, pos.x, pos.y, 0f, 0f)
                }
            }
        }

        if (crushFlag && crushDist > 0f) {
            if (crushVec.len() / crushDist < crushThreshold) {
                sim.eventPlayerWasKilled(this, SimGlobals.ENEMYTYPE_CRUSH, pos.x, pos.y, 0f, 0f)
            }
        }
    }

    /** Physical collisions with entities (BounceBlocks, Thwomps, etc.). */
    fun collideVsObjects(sim: Simulator) {
        if (state == State.DEAD) { ragdoll.collideVsObjects(sim); return }
        resultPhys.clear()
        sim.objGrid.gatherNeighbourhood(pos, objList)
        for (entity in objList) {
            if (entity.collideVsCirclePhysical(resultPhys, pos, vel, oldPos, r)) {
                respondToCollision(resultPhys.nx, resultPhys.ny, resultPhys.pen,
                    resultPhys.isHardCollision, entity.isCrushable())
            }
        }
    }

    /** Iterative segment collision — up to 32 iterations per tick. */
    fun collideVsTiles(sim: Simulator) {
        if (state == State.DEAD) { ragdoll.collideVsTiles(sim); return }
        val maxIter = 32
        var iter = 0
        cp.set(0f, 0f)
        while (true) {
            val side = ColUtils.getSingleClosestPointSigned(sim.segGrid, pos, r, cp)
            if (side == 0) break
            val dx = pos.x - cp.x; val dy = pos.y - cp.y
            val dist = sqrt(dx * dx + dy * dy)
            val pen  = r - side * dist
            if (pen < 1e-7f) break
            if (dist == 0f) return
            respondToCollision(dx / dist, dy / dist, side * pen, true, false)
            if (++iter >= maxIter) break
        }
    }

    /** Input + state-machine update. [frame] is the current physics tick counter. */
    fun think(sim: Simulator, frame: Int) {
        inputSource.tick(frame)
        val right    = inputSource.isRightDown
        val left     = inputSource.isLeftDown
        val jumpHeld = inputSource.isJumpDown
        val jumpTap  = jumpHeld && !wasJumpDown
        wasJumpDown  = jumpHeld

        if (state == State.DISABLED || state == State.DEAD) return

        if (state == State.AWAITING_DEATH) {
            ragdoll.activate(pos, vel, deathPos, deathForce, null, null)
            if (deathType == SimGlobals.DEATHTYPE_EXPLOSIVE || deathType == SimGlobals.DEATHTYPE_SUICIDE) {
                ragdoll.explode(sim)
            }
            sim.spawnBloodSpurt(deathPos.x, deathPos.y, deathForce.x, deathForce.y,
                3 + floor(Math.random() * 4).toInt())
            onSound(when (deathType) {
                SimGlobals.DEATHTYPE_EXPLOSIVE -> if (Math.random() < 0.5) "explode1" else "explode2"
                SimGlobals.DEATHTYPE_FALL      -> "fall"
                SimGlobals.DEATHTYPE_LASER     -> "laser"
                SimGlobals.DEATHTYPE_ELECTRIC  -> if (Math.random() < 0.5) "zap1" else "zap2"
                else                           -> if (Math.random() < 0.5) "shot1" else "shot2"
            })
            state = State.DEAD
            return
        }

        if (state == State.CELEBRATING) {
            d = if (inAir) normDrag else winDrag
            return
        }

        var vx = vel.x; val vy = vel.y
        val dir = (if (right) 1 else 0) - (if (left) 1 else 0)

        if (inAir) {
            val accelX = vx + dir * airAccel
            if (abs(accelX) < maxSpeedAir) vx = accelX
            vel.x = vx

            if (state.isGroundState) {
                actionFall(); return
            }

            if (state == State.JUMPING) {
                jumpTimer++
                if (!jumpHeld || jumpTimer > maxJumpTime) { actionFall(); return }
                return
            }

            // FALLING or WALL_SLIDING — check wall interactions
            if (nearWall) {
                if (jumpTap) {
                    val wallMult: Float; val wallBias: Float
                    if (state == State.WALL_SLIDING && dir * wallNormal.x < 0) {
                        wallMult = 1f; wallBias = 0.5f
                    } else {
                        wallMult = 1.5f; wallBias = 0.7f
                    }
                    sim.spawnJumpDust(pos.x - wallNormal.x * r,
                                      pos.y - wallNormal.y * r,
                                      wallNormal.x * 90f)
                    actionJump(wallNormal.x * wallMult, wallNormal.y - wallBias, sim)
                    return
                }
                if (state == State.WALL_SLIDING) {
                    if (dir * wallNormal.x > 0) { actionFall(); return }
                    // pressing into wall — wall friction
                    sim.spawnWallDust(pos, r, wallNormal, min(4f, abs(vy)))
                    vel.y *= wallFriction
                    return
                }
                // Entering wall-slide: must be falling and pressing toward wall
                if (vy > 0f && dir * wallNormal.x < 0) { actionWallSlide(); return }
            } else if (state == State.WALL_SLIDING) {
                actionFall(); return
            }

        } else {
            // On ground
            val accelX = vx + dir * groundAccel
            if (abs(accelX) < maxSpeedGround) vx = accelX
            vel.x = vx

            if (state.isGroundState.not()) {
                // Just landed from air/jump/wallslide
                val landAngle = (90f + atan2(floorNormal.y, floorNormal.x) / PI.toFloat() * 180f)
                val landSpeed = abs(vel.x) + vel.y
                sim.spawnLandDust(pos.x - floorNormal.x * r,
                                  pos.y - floorNormal.y * r, landAngle, landSpeed)
                onSound("land")
                if (vx * dir > 0f) { actionRun(dir); return }
                actionSkid(); return
            }

            if (jumpTap) {
                val jumpAngle = 90f + atan2(floorNormal.y, floorNormal.x) / PI.toFloat() * 180f
                sim.spawnJumpDust(pos.x - floorNormal.x * r,
                                  pos.y - floorNormal.y * r, jumpAngle)
                if (dir * floorNormal.x < 0) {
                    actionJump(0f, -0.7f, sim); return
                }
                actionJump(floorNormal.x, floorNormal.y, sim); return
            }

            when (state) {
                State.RUNNING -> {
                    val fn = floorNormal
                    val tangentSpeed = vx * -fn.y + vy * fn.x
                    val absTangent   = abs(tangentSpeed)
                    if (dir * vx * absTangent <= 0f) { actionSkid(); return }
                    // Running uphill correction
                    if (dir * fn.x < 0) {
                        val corrX = -abs(fn.x); var corrY = fn.y
                        if (fn.x < 0) corrY = -corrY
                        val scale = 0.5f * abs(fn.y)
                        val newVx = vx + corrY * scale * groundAccel
                        val newVy = vy + corrX * scale * groundAccel
                        if (abs(accelX) < maxSpeedGround) {
                            vel.x = newVx; vel.y = newVy
                        }
                    }
                }
                State.SKIDDING -> {
                    val fn = floorNormal
                    val tangentSpeed = vx * -fn.y + vy * fn.x
                    val absTangent   = abs(tangentSpeed)
                    val tangentDir   = vx * absTangent
                    if (dir * tangentDir > 0f) { actionRun(dir); return }
                    if (absTangent < 0.1f && abs(fn.x) < 0.001f) { actionStand(); return }
                    val slidingDir = if (tangentDir < 0f) -1f else 1f
                    val slideAngle = atan2(fn.x, -fn.y) * (180f / PI.toFloat())
                    sim.spawnFloorDust(pos, r, fn, slideAngle, slidingDir, absTangent)
                    if (vy < 0f && fn.x != 0f) {
                        // Slope skid: conserve speed
                        val delta = abs(vx * skidFriction - vx)
                        val loss  = abs(delta * fn.y) * (fn.y * fn.y)
                        val speed = sqrt(vx * vx + vy * vy)
                        val newSpeed = speed - loss
                        vel.x = (vx / speed) * newSpeed
                        vel.y = (vy / speed) * newSpeed
                    } else {
                        vel.x = vx * skidFriction
                    }
                }
                State.STANDING -> {
                    if (dir != 0) { actionRun(dir); return }
                    val fn = floorNormal
                    val absTangent = abs(vx * -fn.y + vy * fn.x)
                    if (absTangent >= 0.1f) { actionSkid(); return }
                    vel.x = vx * standFriction
                }
                else -> {}
            }
        }
    }

    // --- External simulation events ---

    /** Launched by a launchpad — apply impulse and go airborne. */
    fun simLaunch(dirX: Float, dirY: Float) {
        if (state == State.AWAITING_DEATH) return
        pos.x += dirX * impulseScale; pos.y += dirY * impulseScale
        vel.x = dirX * impulseScale;  vel.y = dirY * impulseScale
        floorCount = 0
        if (state != State.CELEBRATING) actionFall()
    }

    /** Kill the ninja. Returns false if already dead/disabled. */
    fun simKill(enemyType: Int, kx: Float, ky: Float, fx: Float, fy: Float): Boolean {
        if (state == State.AWAITING_DEATH || state == State.DISABLED) return false
        deathPos.set(kx, ky); deathForce.set(fx, fy)
        deathType = SimGlobals.ETYPE_TO_DTYPE[enemyType]
        actionDie()
        return true
    }

    /** Trigger win celebration. Returns false if can't celebrate now. */
    fun simWin(): Boolean {
        if (state == State.DISABLED || state == State.CELEBRATING ||
            state == State.AWAITING_DEATH || state == State.DEAD) return false
        actionWin()
        return true
    }

    // --- Debug ---

    fun debugRespawn(spawnPos: Vec2) {
        state = State.STANDING
        pos.set(spawnPos); vel.set(0f, 0f)
        g = normGrav; d = normDrag
    }

    fun debugSetPosVel(p: Vec2, v: Vec2) {
        if (state == State.DEAD) ragdoll.testingSetPosVel(p, v)
        else { pos.set(p); vel.set(v) }
    }

    // --- Private state transitions ---

    private fun actionJump(nx: Float, ny: Float, sim: Simulator) {
        exitCurrentState()
        state = State.JUMPING
        g = jumpGrav
        if (vel.x * nx < 0f) vel.x = 0f
        if (vel.y * ny < 0f) vel.y = 0f
        pos.x += nx * jumpAmt * impulseScale
        pos.y += ny * (jumpAmt + jumpYBias) * impulseScale
        vel.x += nx * jumpAmt * impulseScale
        vel.y += ny * (jumpAmt + jumpYBias) * impulseScale
        jumpTimer = 0f
        onSound("jump")
    }

    private fun actionFall()      { exitCurrentState(); state = State.FALLING }
    private fun actionWallSlide() { exitCurrentState(); state = State.WALL_SLIDING }
    private fun actionSkid()      { exitCurrentState(); state = State.SKIDDING }
    private fun actionRun(dir: Int) {
        facingDir = dir
        exitCurrentState(); state = State.RUNNING
    }
    private fun actionStand()     { exitCurrentState(); state = State.STANDING }
    private fun actionDie()       { exitCurrentState(); state = State.AWAITING_DEATH }
    private fun actionWin()       { exitCurrentState(); state = State.CELEBRATING }

    private fun exitCurrentState() {
        if (state == State.JUMPING) g = normGrav
    }

    // --- Collision response ---

    private fun respondToCollision(nx: Float, ny: Float, pen: Float, isHard: Boolean, isCrush: Boolean) {
        pos.x += pen * nx; pos.y += pen * ny
        if (isCrush) crushFlag = true
        if (isHard || isCrush) {
            crushVec.x += pen * nx; crushVec.y += pen * ny
            crushDist  += abs(pen)
        }
        if (isHard) {
            val velDotN = vel.x * nx + vel.y * ny
            if (velDotN < 0f) {
                vel.x -= velDotN * nx; vel.y -= velDotN * ny
            }
        } else {
            vel.x += pen * nx; vel.y += pen * ny
        }
        if (ny < 0f) {
            floorCount++
            floorVec.x += nx; floorVec.y += ny
        }
    }
}

// Extension helper — avoids java.lang.Math.pow overhead for Float scalars
private fun Float.pow(exp: Float): Float = this.toDouble().pow(exp.toDouble()).toFloat()
