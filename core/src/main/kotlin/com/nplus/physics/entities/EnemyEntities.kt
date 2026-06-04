package com.nplus.physics.entities

import com.nplus.SimGlobals
import com.nplus.physics.Ninja
import com.nplus.physics.Simulator
import com.nplus.physics.collision.ColUtils
import com.nplus.physics.collision.GridEntity
import com.nplus.physics.collision.Segment
import com.nplus.physics.math.MathUtils
import com.nplus.physics.math.Vec2
import kotlin.math.*

// ---------------------------------------------------------------------------
// Thwomp — falls toward the player when aligned, raises back to anchor
// ---------------------------------------------------------------------------

class ThwompEntity(private val objGrid: GridEntity, x: Float, y: Float,
                   private val fallDir: Int, private val isHorizontal: Boolean) : EntityBase() {
    private val pos    = Vec2(x, y)
    private val anchor = Vec2(x, y)
    private val r      = 12f * (3f / 4f)
    private val fallSpeed  = 12f * (5f / 14f) * (40f / SimGlobals.SIM_RATE)
    private val raiseSpeed = 12f * (1f / 7f)  * (40f / SimGlobals.SIM_RATE)
    private var state  = 0  // 0=idle, 1=falling, -1=raising
    private val n = Vec2()

    init { objGrid.entityAdd(pos, this) }

    override fun isCrushable() = true
    override fun isZapper()    = true

    override fun collideVsCirclePhysical(result: CollisionResultPhysical,
        pos: Vec2, vel: Vec2, normal: Vec2, radius: Float): Boolean {
        n.set(0f, 0f)
        val pen = ColUtils.penetrationSquareVsPoint(this.pos, r + radius, pos, n)
        if (pen != 0f) {
            result.isHardCollision = false
            result.nx = n.x; result.ny = n.y; result.pen = pen
            return true
        }
        return false
    }

    override fun collideVsCircleLogical(sim: Simulator, ninja: Ninja?, result: CollisionResultLogical,
        pos: Vec2, vel: Vec2, normal: Vec2, radius: Float, eps: Float): Boolean {
        n.set(0f, 0f)
        val pen = ColUtils.penetrationSquareVsPoint(this.pos, 0.1f + r + radius, pos, n)
        if (pen != 0f) {
            val isCrushingFace = if (isHorizontal) n.x * fallDir > 0f else n.y * fallDir > 0f
            if (isCrushingFace) {
                if (isHorizontal) sim.spawnZapThwompH(this.pos.x, this.pos.y, r * fallDir, r)
                else              sim.spawnZapThwompV(this.pos.x, this.pos.y, r, r * fallDir)
                if (ninja == null) {
                    result.vecX = n.x * 8f; result.vecY = n.y * 8f - 4f; return true
                }
                sim.eventPlayerWasKilled(ninja, SimGlobals.ENEMYTYPE_THWOMP,
                    pos.x - n.x * radius, pos.y - n.y * radius, n.x * 8f, n.y * 8f - 4f)
            } else if (ninja != null) {
                result.vecX = n.x; result.vecY = n.y; return true
            }
        }
        return false
    }

    override fun think(sim: Simulator) {
        if (state != 0) return
        val eg = sim.edgeGrid
        for (player in sim.players) {
            if (player.isDead()) continue
            val pp = player.getPos(); val pr = player.getRadius()
            val reach = 2f * (r + pr)
            if (isHorizontal) {
                if (abs(pp.y - pos.y) >= reach) continue
                val colAnchor = eg.getGridCoordFromWorld1D(pos.x - fallDir * r)
                val rowMin    = eg.getGridCoordFromWorld1D(pos.y - r)
                val rowMax    = eg.getGridCoordFromWorld1D(pos.y + r)
                val colWall   = eg.sweepHorizontal(rowMin, rowMax, colAnchor, fallDir)
                val colPlayer = eg.getGridCoordFromWorld1D(pp.x)
                val lo = min(colAnchor, colWall); val hi = max(colAnchor, colWall)
                if (colPlayer in lo..hi) { state = 1; break }
            } else {
                if (abs(pp.x - pos.x) >= reach) continue
                val rowAnchor = eg.getGridCoordFromWorld1D(pos.y - fallDir * r)
                val colMin    = eg.getGridCoordFromWorld1D(pos.x - r)
                val colMax    = eg.getGridCoordFromWorld1D(pos.x + r)
                val rowWall   = eg.sweepVertical(colMin, colMax, rowAnchor, fallDir)
                val rowPlayer = eg.getGridCoordFromWorld1D(pp.y)
                val lo = min(rowAnchor, rowWall); val hi = max(rowAnchor, rowWall)
                if (rowPlayer in lo..hi) { state = 1; break }
            }
        }
    }

    override fun move(sim: Simulator) {
        if (state == 0) return
        val eg = sim.edgeGrid
        val moveDir = fallDir * state
        val speed = if (state == -1) raiseSpeed else fallSpeed
        if (isHorizontal) {
            var nx = pos.x + moveDir * speed
            if (state == -1 && (pos.x - anchor.x) * (nx - anchor.x) <= 0f) nx = anchor.x
            val colCur  = eg.getGridCoordFromWorld1D(pos.x + moveDir * r)
            val colNext = eg.getGridCoordFromWorld1D(nx  + moveDir * r)
            if (colCur != colNext) {
                val rMin = eg.getGridCoordFromWorld1D(pos.y - r)
                val rMax = eg.getGridCoordFromWorld1D(pos.y + r)
                if (!eg.isEmptyColumn(colCur, rMin, rMax, moveDir)) {
                    if (state != -1) state = -1; return
                }
            }
            pos.x = nx
            sim.objGrid.entityMove(pos, this)
            if (state == -1 && pos.x == anchor.x) state = 0
        } else {
            var ny = pos.y + moveDir * speed
            if (state == -1 && (pos.y - anchor.y) * (ny - anchor.y) <= 0f) ny = anchor.y
            val rowCur  = eg.getGridCoordFromWorld1D(pos.y + moveDir * r)
            val rowNext = eg.getGridCoordFromWorld1D(ny  + moveDir * r)
            if (rowCur != rowNext) {
                val cMin = eg.getGridCoordFromWorld1D(pos.x - r)
                val cMax = eg.getGridCoordFromWorld1D(pos.x + r)
                if (!eg.isEmptyRow(rowCur, cMin, cMax, moveDir)) {
                    if (state != -1) state = -1; return
                }
            }
            pos.y = ny
            sim.objGrid.entityMove(pos, this)
            if (state == -1 && pos.y == anchor.y) state = 0
        }
    }

    fun getPos(): Vec2 = pos
    fun getFallDir() = fallDir
    fun isHorizontal() = isHorizontal
    fun getState() = state
}

// ---------------------------------------------------------------------------
// FloorGuard — slides along the floor toward the player when line-of-sight clear
// ---------------------------------------------------------------------------

class FloorGuardEntity(private val objGrid: GridEntity, x: Float, y: Float) : EntityBase() {
    private val pos    = Vec2(x, y)
    private val r      = 12f * 0.5f
    private val speed  = 12f * (3f / 7f) * (40f / SimGlobals.SIM_RATE)
    private val margin = 0f
    private var state  = 0  // 0=idle, 1=right, -1=left

    init { objGrid.entityAdd(pos, this) }

    override fun isZapper() = true

    override fun collideVsCircleLogical(sim: Simulator, ninja: Ninja?, result: CollisionResultLogical,
        pos: Vec2, vel: Vec2, normal: Vec2, radius: Float, eps: Float): Boolean {
        if (ColUtils.overlapCircleVsCircle(this.pos, r, pos, radius)) {
            val dx = pos.x - this.pos.x; val dy = pos.y - this.pos.y
            val d = sqrt(dx * dx + dy * dy)
            val nx = dx / d; val ny = dy / d
            sim.spawnZap(this.pos.x + nx * r, this.pos.y + ny * r, atan2(ny, nx) / PI.toFloat() * 180f)
            if (ninja == null) { result.vecX = nx * 10f; result.vecY = ny * 10f; return true }
            sim.eventPlayerWasKilled(ninja, SimGlobals.ENEMYTYPE_FLOORGUARD,
                pos.x - nx * radius, pos.y - ny * radius, nx * 10f, ny * 10f)
        }
        return false
    }

    override fun think(sim: Simulator) {
        if (state != 0) return
        val eg = sim.edgeGrid
        for (player in sim.players) {
            if (player.isDead()) continue
            val pp = player.getPos()
            val floorY = pos.y + r
            if (pp.y < floorY - 24f || pp.y > floorY) continue
            val colPlayer = eg.getGridCoordFromWorld1D(pp.x)
            val colThis   = eg.getGridCoordFromWorld1D(pos.x)
            val rowThis   = eg.getGridCoordFromWorld1D(pos.y)
            val dir = if (pp.x >= pos.x) 1 else -1
            if (!eg.scanHorizontal(rowThis, rowThis, colThis, colPlayer)) continue
            // Mirror the move() wall/edge check: only trigger if the guard can actually
            // take at least one step. Prevents looping sound when stuck against a wall —
            // move() resets state=0 on collision, think() would otherwise re-trigger
            // immediately on the next tick while the guard is still blocked.
            val halfSize = r + margin
            val nextX    = pos.x + dir * speed
            val colEdge  = eg.getGridCoordFromWorld1D(pos.x + dir * halfSize)
            val colEdgeN = eg.getGridCoordFromWorld1D(nextX  + dir * halfSize)
            if (colEdge != colEdgeN) {
                val row = eg.getGridCoordFromWorld1D(pos.y)
                if (!eg.isEmpty(colEdge, row, dir, 0) || !eg.isSolidIgnoreDoors(colEdgeN, row, 0, 1))
                    continue  // still blocked — don't trigger or play sound
            }
            state = dir
            sim.playSoundEntity("guard_chase")
            break
        }
    }

    override fun move(sim: Simulator) {
        if (state == 0) return
        val eg = sim.edgeGrid
        val halfSize = r + margin
        val nextX = pos.x + state * speed
        val edgeOffset = state * halfSize
        val colCur  = eg.getGridCoordFromWorld1D(pos.x  + edgeOffset)
        val colNext = eg.getGridCoordFromWorld1D(nextX + edgeOffset)
        if (colCur != colNext) {
            val row = eg.getGridCoordFromWorld1D(pos.y)
            if (!eg.isEmpty(colCur, row, state, 0) || !eg.isSolidIgnoreDoors(colNext, row, 0, 1)) {
                pos.x = eg.getWorldCoordFromGridEdge1D(colCur, state).toFloat() - state * (halfSize + 0.01f)
                state = 0
            }
        }
        if (state != 0) pos.x = nextX
        sim.objGrid.entityMove(pos, this)
    }

    fun getPos(): Vec2 = pos
    fun getState() = state
}

// ---------------------------------------------------------------------------
// Turret — stationary, raycasts to player, fires after locking on
// ---------------------------------------------------------------------------

class TurretEntity(x: Float, y: Float) : EntityBase() {
    private val pos     = Vec2(x, y)
    private val aimPos  = Vec2(x, y)
    private var aimRegion = 0

    private val firetime     = 60f  * (SimGlobals.SIM_RATE / 40f)
    private val prefireDelay = 10f  * (SimGlobals.SIM_RATE / 40f)
    private val postfireDelay= 10f  * (SimGlobals.SIM_RATE / 40f)
    private val threshold2   = floatArrayOf(9216f, 1764f, 576f)
    private val aimSpeed     = floatArrayOf(0.03f, 0.035f, 0.05f, 0.05f).map { it * (40f/SimGlobals.SIM_RATE) }
    private val timerStep    = floatArrayOf(0f, 0.5f, 1.5f, 3.5f)
    private val predScale    = SimGlobals.SIM_RATE / 40f

    private var shotTimer   = 0f
    private var curState    = 0  // 0=idle, 1=targeting, 2=prefire, 3=postfire
    private var targetIndex = -1
    private val hitPos = Vec2(); private val hitN = Vec2()
    private val tmpP   = Vec2(); private val tmpN  = Vec2()

    // Turret never added to objGrid — uses raycasting for detection

    override fun think(sim: Simulator) {
        if (curState == 0) {
            val idx = EntityHelpers.tryToAcquireTarget(pos, sim.players, sim.segGrid)
            if (idx >= 0) { aimPos.set(pos); shotTimer = 0f; curState = 1; targetIndex = idx }
        } else {
            val target = sim.players.getOrNull(targetIndex) ?: return
            when (curState) {
                1 -> { // targeting
                    if (!isTargetVisible(sim)) { curState = 0; targetIndex = -1 }
                    else {
                        updateAim(target.getPos(), target.getVel())
                        if (shotTimer > firetime) { shotTimer = 0f; curState = 2; sim.playSoundEntity("turret_prefire") }
                    }
                }
                2 -> { // prefire
                    shotTimer++
                    if (shotTimer >= prefireDelay) {
                        // AS3: always transition to postfire; only fire if target is still alive
                        if (!target.isDead()) {
                            val dx = aimPos.x - pos.x; val dy = aimPos.y - pos.y
                            val len = sqrt(dx*dx + dy*dy); val nx = dx/len; val ny = dy/len
                            val dist = sim.segGrid.getRaycastDistance(pos.x, pos.y, nx, ny, hitPos, hitN)
                            for (i in sim.players.indices) {
                                val p = sim.players[i]; if (p.isDead()) continue
                                if (ColUtils.overlapCircleVsSegment(p.getPos(), p.getRadius(), pos, hitPos, dist)) {
                                    val proj = nx * (p.getPos().x - pos.x) + ny * (p.getPos().y - pos.y)
                                    sim.eventPlayerWasKilled(p, SimGlobals.ENEMYTYPE_TURRET,
                                        pos.x + proj*nx, pos.y + proj*ny, nx*8f, ny*8f)
                                }
                            }
                            sim.spawnTurretBullet(pos.x, pos.y, hitPos.x, hitPos.y, hitN.x, hitN.y)
                        }
                        shotTimer = 0f; curState = 3
                    }
                }
                3 -> { // postfire
                    shotTimer++
                    if (shotTimer >= postfireDelay) {
                        if (isTargetVisible(sim)) { shotTimer = 0f; curState = 1 }
                        else { curState = 0; targetIndex = -1 }
                    }
                }
            }
        }
    }

    private fun isTargetVisible(sim: Simulator): Boolean {
        val t = sim.players.getOrNull(targetIndex) ?: return false
        return !t.isDead() && sim.segGrid.raycastVsPlayer(pos, t.getPos(), t.getRadius(), tmpP, tmpN)
    }

    private fun updateAim(targetPos: Vec2, targetVel: Vec2) {
        val px = targetPos.x + targetVel.x * predScale
        val py = targetPos.y + targetVel.y * predScale
        val dx = px - aimPos.x; val dy = py - aimPos.y
        val distSq = dx*dx + dy*dy
        // AS3: start at 0, increment while distSq <= threshold[i] (thresholds are decreasing).
        // count { distSq <= it } is equivalent: counts 0..3 matching entries.
        aimRegion = threshold2.count { distSq <= it }
        shotTimer += timerStep[aimRegion]
        aimPos.x += aimSpeed[aimRegion] * dx; aimPos.y += aimSpeed[aimRegion] * dy
    }

    fun getPos(): Vec2 = pos
    fun getAimPos(): Vec2 = aimPos
    fun getState() = curState
    fun getAimRegion() = aimRegion
    /** 0.0 (just entered prefire) → 1.0 (fully charged, about to fire). Only meaningful in state 2. */
    fun getPrefireProgress(): Float = (shotTimer / prefireDelay).coerceIn(0f, 1f)
}

// ---------------------------------------------------------------------------
// Rocket Launcher — fires a homing rocket at visible players
// ---------------------------------------------------------------------------

class RocketEntity(private val objGrid: GridEntity, x: Float, y: Float) : EntityBase() {
    private val pos        = Vec2(x, y)
    private val rocketPos  = Vec2(x, y)
    private val rocketDir  = Vec2(1f, 0f)
    private val rocketVel  = Vec2()
    private val oldPos     = Vec2()
    private val hitPos     = Vec2(); private val hitN = Vec2()

    private val maxSpeed    = 12f * (2f/7f) * (40f/SimGlobals.SIM_RATE)
    private val accelStart  = 0.1f * (40f/SimGlobals.SIM_RATE).pow(2)
    private val accelRate   = 1.1f.pow(40f/SimGlobals.SIM_RATE)
    private val turnRate    = 0.1f * (40f/SimGlobals.SIM_RATE)
    private val prefireDelay= 10f  * (SimGlobals.SIM_RATE/40f)
    private val predScale   = SimGlobals.SIM_RATE / 40f

    private var rocketSpeed = 0f; private var rocketAccel = accelStart
    private var shotTimer   = 0f
    private var state       = 0  // 0=idle, 1=prefire, 2=homing
    private var targetIndex = -1
    private val nearSegs    = mutableListOf<Segment>()

    // NOT in objGrid initially; adds itself when homing starts

    override fun collideVsCircleLogical(sim: Simulator, ninja: Ninja?, result: CollisionResultLogical,
        pos: Vec2, vel: Vec2, normal: Vec2, radius: Float, eps: Float): Boolean {
        if (state != 2) return false
        if (ColUtils.overlapCircleVsCircle(rocketPos, 0f, pos, radius)) {
            explode(sim)
            val dx = pos.x - rocketPos.x; val dy = pos.y - rocketPos.y
            if (ninja == null) { result.vecX = dx*4f; result.vecY = dy*4f; return true }
            sim.eventPlayerWasKilled(ninja, SimGlobals.ENEMYTYPE_ROCKET,
                rocketPos.x, rocketPos.y, dx, dy)
        }
        return false
    }

    override fun think(sim: Simulator) {
        when (state) {
            0 -> {
                val idx = EntityHelpers.tryToAcquireTarget(pos, sim.players, sim.segGrid)
                if (idx >= 0) { shotTimer = 0f; state = 1; targetIndex = idx }
            }
            1 -> {
                if (sim.players[targetIndex].isDead()) { state = 0; return }
                shotTimer++
                if (shotTimer >= prefireDelay) {
                    rocketPos.set(pos); rocketAccel = accelStart; rocketSpeed = 0f
                    val tp = sim.players[targetIndex].getPos()
                    val dx = tp.x - pos.x; val dy = tp.y - pos.y
                    val d = sqrt(dx*dx + dy*dy)
                    if (d != 0f) rocketDir.set(dx/d, dy/d) else rocketDir.set(1f, 0f)
                    objGrid.entityAdd(rocketPos, this); state = 2
                    sim.playSoundEntity("rocket_fire")
                }
            }
            2 -> {
                if (rocketSpeed < maxSpeed) { rocketAccel *= accelRate; rocketSpeed += rocketAccel }
                else rocketSpeed = maxSpeed
                oldPos.set(rocketPos)
                rocketVel.set(rocketSpeed * rocketDir.x, rocketSpeed * rocketDir.y)
                rocketPos.x += rocketVel.x; rocketPos.y += rocketVel.y
                objGrid.entityMove(rocketPos, this)
                // Expand bounding box by 1 unit so segments stored at cell boundaries
                // (e.g. ceiling at y=24 stored in row 0, but worldToGrid(24)=1) are found
                // even when the rocket lands exactly on the boundary value.
                // Required: segEps > maxSpeed so the nearest boundary cell is always included.
                val segEps = maxSpeed + 0.5f
                sim.segGrid.gatherCellContentsFromWorldspaceRegion(
                    min(oldPos.x, rocketPos.x) - segEps, min(oldPos.y, rocketPos.y) - segEps,
                    max(oldPos.x, rocketPos.x) + segEps, max(oldPos.y, rocketPos.y) + segEps, nearSegs)
                for (seg in nearSegs) {
                    val t = seg.intersectWithRay(oldPos, rocketVel, 0f, hitPos, hitN)
                    if (t == -1f || t < 2f) { explode(sim); return }
                }
                // Steer toward target
                if (!sim.players[targetIndex].isDead()) {
                    val tp = sim.players[targetIndex]
                    val px = tp.getPos().x + tp.getVel().x * predScale
                    val py = tp.getPos().y + tp.getVel().y * predScale
                    val rpx = rocketPos.x + rocketVel.x * predScale
                    val rpy = rocketPos.y + rocketVel.y * predScale
                    val dx = px - rpx; val dy = py - rpy
                    val d = sqrt(dx*dx + dy*dy); if (d == 0f) return
                    val nx = dx/d; val ny = dy/d
                    val perp = -rocketDir.y * nx + rocketDir.x * ny
                    // Save perpendicular components before modifying dir to avoid mutation ordering bug
                    val perpX = -rocketDir.y
                    val perpY =  rocketDir.x
                    rocketDir.x += turnRate * perp * perpX
                    rocketDir.y += turnRate * perp * perpY
                    val len = rocketDir.len()
                    if (len != 0f) { rocketDir.x /= len; rocketDir.y /= len }
                }
                sim.spawnRocketSmoke(rocketPos.x, rocketPos.y,
                    atan2(rocketDir.y, rocketDir.x) / PI.toFloat() * 180f)
            }
        }
    }

    private fun explode(sim: Simulator) {
        if (state == 2) objGrid.entityRemove(this)
        state = 0; targetIndex = -1
        sim.spawnExplosion(rocketPos.x, rocketPos.y)
    }

    fun getPos(): Vec2 = pos
    fun getRocketPos(): Vec2 = rocketPos
    fun getRocketDir(): Vec2 = rocketDir
    fun getState() = state
}

private fun Float.pow(exp: Float): Float = this.toDouble().pow(exp.toDouble()).toFloat()
