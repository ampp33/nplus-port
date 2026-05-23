package com.nplus.physics.entities

import com.nplus.SimGlobals
import com.nplus.physics.Ninja
import com.nplus.physics.Simulator
import com.nplus.physics.collision.ColUtils
import com.nplus.physics.collision.GridEdges
import com.nplus.physics.collision.GridEntity
import com.nplus.physics.math.MathUtils
import com.nplus.physics.math.Vec2
import kotlin.math.*

// ---------------------------------------------------------------------------
// DroneBase — grid-following movement shared by all drone types
// ---------------------------------------------------------------------------

abstract class DroneBase(
    objGrid: GridEntity, x: Float, y: Float,
    protected var speed: Float,
    protected var facingDir: Int,   // 0=R, 1=D, 2=L, 3=U
    protected val moveType: Int     // 0=surface_cw, 1=surface_ccw, 2=wander_cw, 3=wander_ccw
) : EntityBase() {

    val pos             = Vec2(x, y)   // public: renderer reads position
    protected val r     = 12f * (3f / 4f)
    var gfxOrn          = DIR_TO_RAD[facingDir]   // public: renderer reads orientation
    private   var nextGoal  = Vec2(x, y)
    private   val STEP_SIZE = 24f

    init { objGrid.entityAdd(pos, this) }

    override fun move(sim: Simulator) {
        moveForward(sim.edgeGrid, sim.objGrid, sim.players)
        val targetOrn  = DIR_TO_RAD[facingDir]
        val delta      = MathUtils.wrapAngleShortest(targetOrn - gfxOrn)
        gfxOrn = MathUtils.wrapAnglePos(gfxOrn + 0.3f * delta)
    }

    private fun moveForward(edgeGrid: GridEdges, objGrid: GridEntity, players: List<Ninja>) {
        val dv = DIR_TO_VEC[facingDir]
        val vx = dv[0] * speed; val vy = dv[1] * speed
        val nx = pos.x + vx;   val ny = pos.y + vy
        val dx = nextGoal.x - pos.x; val dy = nextGoal.y - pos.y
        val gx = nextGoal.x - nx;    val gy = nextGoal.y - ny
        val distToGoal = sqrt(dx*dx + dy*dy)
        val dotGoal    = dx * gx + dy * gy
        if (distToGoal < 0.000001f || dotGoal < 0f) {
            pos.set(nextGoal)
            if (chooseNextDir(edgeGrid, players)) {
                val overshoot = max(0f, speed - distToGoal)
                val nv = DIR_TO_VEC[facingDir]
                pos.x += nv[0] * overshoot; pos.y += nv[1] * overshoot
            }
        } else {
            pos.x += vx; pos.y += vy
        }
        objGrid.entityMove(pos, this)
    }

    protected open fun chooseNextDir(edgeGrid: GridEdges, players: List<Ninja>): Boolean {
        for (prio in 0..3) {
            val candidate = (facingDir + MOVELIST[moveType][prio]) % 4
            if (testDir(edgeGrid, candidate)) { facingDir = candidate; return true }
        }
        return false
    }

    protected fun testDir(edgeGrid: GridEdges, dir: Int, outGoal: Vec2 = nextGoal): Boolean {
        val dv = DIR_TO_VEC[dir]
        val nx = pos.x + dv[0] * STEP_SIZE
        val ny = pos.y + dv[1] * STEP_SIZE
        val ok = if (dv[1] == 0f) {  // horizontal
            val colA = edgeGrid.getGridCoordFromWorld1D(pos.x + dv[0] * r)
            val colB = edgeGrid.getGridCoordFromWorld1D(nx  + dv[0] * r)
            val rMin = edgeGrid.getGridCoordFromWorld1D(pos.y - r)
            val rMax = edgeGrid.getGridCoordFromWorld1D(pos.y + r)
            edgeGrid.scanHorizontalDirected(rMin, rMax, colA, colB, dv[0].toInt())
        } else {  // vertical
            val rowA = edgeGrid.getGridCoordFromWorld1D(pos.y + dv[1] * r)
            val rowB = edgeGrid.getGridCoordFromWorld1D(ny  + dv[1] * r)
            val cMin = edgeGrid.getGridCoordFromWorld1D(pos.x - r)
            val cMax = edgeGrid.getGridCoordFromWorld1D(pos.x + r)
            edgeGrid.scanVerticalDirected(cMin, cMax, rowA, rowB, dv[1].toInt())
        }
        if (ok) { outGoal.set(nx, ny) }
        return ok
    }

    companion object {
        val DIR_TO_VEC = arrayOf(
            floatArrayOf( 1f,  0f),
            floatArrayOf( 0f,  1f),
            floatArrayOf(-1f,  0f),
            floatArrayOf( 0f, -1f),
        )
        val DIR_TO_RAD = floatArrayOf(0f, PI.toFloat()*0.5f, PI.toFloat(), PI.toFloat()*1.5f)

        // Priority rotation amounts for each move type (ROT_0=0, ROT_90=1, ROT_180=2, ROT_270=3)
        private val MOVELIST = arrayOf(
            intArrayOf(1, 0, 3, 2),  // SURFACE_CW:  turn-right, straight, turn-left, 180
            intArrayOf(3, 0, 1, 2),  // SURFACE_CCW: turn-left, straight, turn-right, 180
            intArrayOf(0, 1, 3, 2),  // WANDER_CW:   straight, turn-right, turn-left, 180
            intArrayOf(0, 3, 1, 2),  // WANDER_CCW:  straight, turn-left, turn-right, 180
        )
    }
}

// ---------------------------------------------------------------------------
// DroneZap — basic electrifying drone
// ---------------------------------------------------------------------------

open class DroneZap(objGrid: GridEntity, x: Float, y: Float, dir: Int, moveType: Int)
    : DroneBase(objGrid, x, y, 12f * (1f/14f) * 2f * (40f/SimGlobals.SIM_RATE), dir, moveType) {

    override fun isZapper() = true

    override fun collideVsCircleLogical(sim: Simulator, ninja: Ninja?, result: CollisionResultLogical,
        pos: Vec2, vel: Vec2, normal: Vec2, radius: Float, eps: Float): Boolean {
        if (ColUtils.overlapCircleVsCircle(this.pos, r, pos, radius)) {
            val dx = pos.x - this.pos.x; val dy = pos.y - this.pos.y
            val d = sqrt(dx*dx + dy*dy); val nx = dx/d; val ny = dy/d
            sim.spawnZap(this.pos.x + nx*r, this.pos.y + ny*r, atan2(ny, nx)/PI.toFloat()*180f)
            if (ninja == null) { result.vecX = nx*12f; result.vecY = ny*12f - 4f; return true }
            sim.eventPlayerWasKilled(ninja, SimGlobals.ENEMYTYPE_ZAP,
                pos.x - nx*radius, pos.y - ny*radius, nx*12f, ny*12f - 4f)
        }
        return false
    }
}

// ---------------------------------------------------------------------------
// DroneChaser — chases when the player is on the same row/column
// ---------------------------------------------------------------------------

class DroneChaser(objGrid: GridEntity, x: Float, y: Float, dir: Int, moveType: Int)
    : DroneZap(objGrid, x, y, dir, moveType) {

    private var chasing = false
    private val speedRegular = speed
    private val speedChasing = speed * 2f
    private var oldChaseDir  = -1

    override fun think(sim: Simulator) {}  // chaser does not use shooter think

    override fun chooseNextDir(edgeGrid: GridEdges, players: List<Ninja>): Boolean {
        if (chasing) {
            if (testDir(edgeGrid, facingDir)) return true
            // Hit wall — revert
            val revert = (facingDir - MOVELIST_SURFACE_CW[0] + 4) % 4
            speed = speedRegular; chasing = false; oldChaseDir = facingDir; facingDir = revert
        }
        val baseDir = if (oldChaseDir >= 0) { val d = oldChaseDir; oldChaseDir = -1; d } else facingDir
        for (player in players) {
            if (player.isDead()) continue
            val pp = player.getPos()
            for (offset in -1..1) {
                val candidate = ((baseDir + offset + 4) % 4)
                val dv = DroneBase.DIR_TO_VEC[candidate]
                val relX = pp.x - pos.x; val relY = pp.y - pos.y
                if (dv[0] * relX + dv[1] * relY <= 0f) continue
                if (abs(-dv[1] * relX + dv[0] * relY) > 12f) continue
                // Check path is clear
                val isH = (dv[1] == 0f)
                val dir1 = if (isH) dv[0].toInt() else dv[1].toInt()
                val clearPath: Boolean
                if (isH) {
                    val colThis   = edgeGrid.getGridCoordFromWorld1D(pos.x)
                    val colTarget = edgeGrid.getGridCoordFromWorld1D(pp.x)
                    val rowThis   = edgeGrid.getGridCoordFromWorld1D(pos.y)
                    val colWall   = edgeGrid.sweepHorizontal(rowThis, rowThis, colThis, dir1)
                    clearPath = colTarget in minOf(colThis, colWall)..maxOf(colThis, colWall)
                } else {
                    val rowThis   = edgeGrid.getGridCoordFromWorld1D(pos.y)
                    val rowTarget = edgeGrid.getGridCoordFromWorld1D(pp.y)
                    val colThis   = edgeGrid.getGridCoordFromWorld1D(pos.x)
                    val rowWall   = edgeGrid.sweepVertical(colThis, colThis, rowThis, dir1)
                    clearPath = rowTarget in minOf(rowThis, rowWall)..maxOf(rowThis, rowWall)
                }
                if (!clearPath) continue
                if (testDir(edgeGrid, candidate)) {
                    facingDir = candidate; speed = speedChasing; chasing = true; return true
                }
            }
        }
        return super.chooseNextDir(edgeGrid, players)
    }

    fun isChasing() = chasing

    companion object {
        private val MOVELIST_SURFACE_CW = intArrayOf(1, 0, 3, 2)
    }
}

// ---------------------------------------------------------------------------
// DroneShooterBase — drone that stops to fire at the player
// ---------------------------------------------------------------------------

abstract class DroneShooterBase(
    objGrid: GridEntity, x: Float, y: Float, speed: Float, dir: Int, moveType: Int,
    private val prefireDelay: Int, private val postfireDelay: Int
) : DroneBase(objGrid, x, y, speed, dir, moveType) {

    private var firingState  = 0  // 0=idle, 1=prefire, 2=firing, 3=postfire
    private var firingTimer  = 0
    private var targetIndex  = -1
    private val zeroV = Vec2()

    protected fun getFiringState() = firingState

    final override fun move(sim: Simulator) { if (firingState == 0) super.move(sim) }

    final override fun think(sim: Simulator) {
        when (firingState) {
            0 -> {
                val idx = EntityHelpers.tryToAcquireTarget(pos, sim.players, sim.segGrid)
                if (idx >= 0) {
                    firingTimer = 0; firingState = 1; targetIndex = idx
                    startPrefiring(sim, sim.players[idx].getPos())
                }
            }
            1 -> {
                if (sim.players[targetIndex].isDead()) { firingTimer = 0; firingState = 3; startPostfiring(sim) }
                else {
                    updatePrefiring(sim, sim.players[targetIndex].getPos()); firingTimer++
                    if (firingTimer >= prefireDelay)
                        startFiring(sim, sim.players[targetIndex].getPos(), sim.players[targetIndex].getVel())
                }
            }
            2 -> { if (updateFiring(sim)) { firingTimer = 0; firingState = 3; startPostfiring(sim) } }
            3 -> {
                firingTimer++
                if (firingTimer >= postfireDelay) {
                    val visible = sim.players.indices.any { i ->
                        !sim.players[i].isDead() && sim.segGrid.raycastVsPlayer(
                            pos, sim.players[i].getPos(), sim.players[i].getRadius(), zeroV, zeroV)
                    }
                    if (visible) { firingTimer = 0; firingState = 1 }
                    else { firingState = 0; targetIndex = -1 }
                }
            }
        }
    }

    protected open fun startPrefiring(sim: Simulator, target: Vec2) {}
    protected open fun updatePrefiring(sim: Simulator, target: Vec2) {}
    protected open fun startFiring(sim: Simulator, target: Vec2, targetVel: Vec2) { firingState = 2 }
    protected open fun updateFiring(sim: Simulator): Boolean = true
    protected open fun startPostfiring(sim: Simulator) {}
}

// ---------------------------------------------------------------------------
// DroneLaser — fires a sweeping laser beam
// ---------------------------------------------------------------------------

class DroneLaser(objGrid: GridEntity, x: Float, y: Float, dir: Int, moveType: Int)
    : DroneShooterBase(objGrid, x, y, 12f*(1f/14f)*0.5f*(40f/SimGlobals.SIM_RATE), dir, moveType,
        (30f*(SimGlobals.SIM_RATE/40f)).toInt(), (40f*(SimGlobals.SIM_RATE/40f)).toInt()) {

    private val laserDuration = 80
    private var laserTimer    = 0
    private val laserDir      = Vec2()
    private val laserHitPos   = Vec2()
    private val laserHitN     = Vec2()

    override fun startPrefiring(sim: Simulator, target: Vec2) {
        laserDir.set(target.x - pos.x, target.y - pos.y); laserDir.normalize()
        sim.segGrid.getRaycastDistance(pos.x, pos.y, laserDir.x, laserDir.y, laserHitPos, laserHitN)
    }

    override fun updatePrefiring(sim: Simulator, target: Vec2) {
        sim.segGrid.getRaycastDistance(pos.x, pos.y, laserDir.x, laserDir.y, laserHitPos, laserHitN)
        sim.spawnLaserCharge(pos.x, pos.y)
    }

    override fun startFiring(sim: Simulator, target: Vec2, targetVel: Vec2) {
        super.startFiring(sim, target, targetVel); laserTimer = 0
    }

    override fun updateFiring(sim: Simulator): Boolean {
        val dist = sim.segGrid.getRaycastDistance(pos.x, pos.y, laserDir.x, laserDir.y, laserHitPos, laserHitN)
        sim.spawnLaserCharge(pos.x, pos.y)
        for (p in sim.players) {
            if (!p.isDead() && ColUtils.overlapCircleVsSegment(p.getPos(), p.getRadius(), pos, laserHitPos, dist)) {
                val proj = laserDir.x*(p.getPos().x-pos.x) + laserDir.y*(p.getPos().y-pos.y)
                sim.eventPlayerWasKilled(p, SimGlobals.ENEMYTYPE_LASER,
                    pos.x+proj*laserDir.x, pos.y+proj*laserDir.y, laserDir.x*6f, laserDir.y*6f)
            }
        }
        return ++laserTimer >= laserDuration
    }

    fun getLaserHitPos(): Vec2 = laserHitPos
    fun getLaserTimer() = laserTimer
    fun getLaserDuration() = laserDuration
}

// ---------------------------------------------------------------------------
// DroneChaingun — fires a spread burst of bullets
// ---------------------------------------------------------------------------

class DroneChaingun(objGrid: GridEntity, x: Float, y: Float, dir: Int, moveType: Int)
    : DroneShooterBase(objGrid, x, y, 12f*(1f/14f)*0.75f*(40f/SimGlobals.SIM_RATE), dir, moveType,
        (35f*(SimGlobals.SIM_RATE/40f)).toInt(), (60f*(SimGlobals.SIM_RATE/40f)).toInt()) {

    private val maxBullets = 6
    private val spread     = 0.3f
    private val rate       = 6f * (SimGlobals.SIM_RATE / 40f)

    private var count = 0; private var timer = 0
    private val gunDir   = Vec2(); private val sweep = Vec2()
    private val hitPos   = Vec2(); private val hitN  = Vec2()

    override fun updatePrefiring(sim: Simulator, target: Vec2) {
        val dx = target.x - pos.x; val dy = target.y - pos.y
        val delta = MathUtils.wrapAngleShortest(atan2(dy, dx) - gfxOrn)
        gfxOrn += 0.2f * delta
    }

    override fun startFiring(sim: Simulator, target: Vec2, targetVel: Vec2) {
        super.startFiring(sim, target, targetVel); count = 0; timer = 0
        val dx = target.x - pos.x; val dy = target.y - pos.y
        val d = sqrt(dx*dx + dy*dy)
        if (d == 0f) { gunDir.set(1f, 0f); sweep.set(0f, 0f) }
        else {
            gunDir.set(dx/d, dy/d)
            val side = if (-gunDir.y*targetVel.x + gunDir.x*targetVel.y < 0f) 1f else -1f
            sweep.set(gunDir.y * side, -gunDir.x * side)
        }
    }

    override fun updateFiring(sim: Simulator): Boolean {
        timer++
        if (timer >= rate) {
            timer = 0
            if (count > maxBullets) return true
            count++
            val t = (count.toFloat() / maxBullets - 0.5f) * spread
            var bx = gunDir.x + t * sweep.x; var by = gunDir.y + t * sweep.y
            val bl = sqrt(bx*bx + by*by); bx /= bl; by /= bl
            gfxOrn = atan2(by, bx)
            val dist = sim.segGrid.getRaycastDistance(pos.x, pos.y, bx, by, hitPos, hitN)
            sim.spawnChainBullet(pos.x, pos.y, hitPos.x, hitPos.y)
            for (p in sim.players) {
                if (!p.isDead() && ColUtils.overlapCircleVsSegment(p.getPos(), p.getRadius(), pos, hitPos, dist)) {
                    val proj = bx*(p.getPos().x-pos.x) + by*(p.getPos().y-pos.y)
                    sim.eventPlayerWasKilled(p, SimGlobals.ENEMYTYPE_CHAINGUN,
                        pos.x+proj*bx, pos.y+proj*by, bx*5f, by*5f)
                }
            }
        }
        return false
    }
}
