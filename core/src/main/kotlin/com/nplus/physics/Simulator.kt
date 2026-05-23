package com.nplus.physics

import com.nplus.SimGlobals
import com.nplus.physics.collision.GridEdges
import com.nplus.physics.collision.GridEntity
import com.nplus.physics.collision.GridSegment
import com.nplus.physics.entities.EntityBase
import com.nplus.physics.math.MathUtils
import com.nplus.physics.math.Vec2

/**
 * Port of AS3 Simulator — the physics world.
 *
 * Grid layout mirrors the AS3 constants exactly:
 *   - 31 tile columns × 23 tile rows (from edat.as)
 *   - +2 border cells on each axis → 33 × 25 grid
 *   - 24 px cell size
 *
 * Particle and sound methods are open no-ops here; the GameScreen overrides them
 * once the AudioManager and ParticleManager are wired (steps 13–14).
 */
open class Simulator(
    val segGrid:  GridSegment,
    val edgeGrid: GridEdges,
    val objGrid:  GridEntity,
    private val entities: List<EntityBase>,
    val players:  List<Ninja>,
    /** Full 33×25 tile grid (boundary + interior) used by the renderer. Default = all empty. */
    val tileGrid: IntArray = IntArray(GRID_NUM_COLS * GRID_NUM_ROWS)
) {
    // --- Grid geometry constants (verbatim from AS3 edat + Simulator) ---
    companion object {
        const val TILE_COLS:    Int   = 31        // edat.num_cols
        const val TILE_ROWS:    Int   = 23        // edat.num_rows
        const val GRID_NUM_COLS: Int  = TILE_COLS + 2
        const val GRID_NUM_ROWS: Int  = TILE_ROWS + 2
        const val GRID_CELL_SIZE: Float = 24f
        const val GRID_CELL_HALF: Float = GRID_CELL_SIZE / 2f

        // Collision sub-step count (4 in AS3)
        const val COLLISION_ITERATIONS: Int = 4
    }

    private var frameNum = 0
    private val goldCollectedThisTick = IntArray(players.size)

    private var wonFlag = false

    init {
        MathUtils.setRandomSeed(1)
    }

    // -----------------------------------------------------------------------
    // Core tick — matches AS3 Tick() order exactly (renderer param removed)
    // -----------------------------------------------------------------------

    /**
     * Advance the simulation by one physics tick (1 / SIM_RATE seconds).
     * Call order: entities move → think → players integrate + collide → think.
     */
    fun tick() {
        goldCollectedThisTick.fill(0)

        for (entity in entities) entity.move(this)
        for (entity in entities) entity.think(this)

        for (player in players) {
            player.integrate()
            player.preCollision()
        }

        repeat(COLLISION_ITERATIONS) {
            for (player in players) player.solveInternalConstraints()
            for (player in players) player.collideVsObjects(this)
            for (player in players) player.collideVsTiles(this)
        }

        for (player in players) player.postCollision(this)
        for (player in players) player.think(this, frameNum)

        frameNum++
    }

    // -----------------------------------------------------------------------
    // APP_ interface — called by the GameScreen to query or control the sim
    // -----------------------------------------------------------------------

    fun appIsGameDone()     = wonFlag || areAllPlayersDead()
    fun appDidPlayerWin()   = wonFlag
    fun appIsPlayerDead(i: Int) = players[i].isDead()

    fun appGetGoldCollectedThisTick(playerIndex: Int): Int =
        goldCollectedThisTick[playerIndex]

    fun appEnablePlayer(i: Int)  = players[i].enable()
    fun appDisablePlayer(i: Int) = players[i].disable()

    fun appEnableAllPlayers()  { for (p in players) p.enable() }
    fun appDisableAllPlayers() { for (p in players) p.disable() }

    fun appTimeUp() {
        for (p in players) killPlayer(p, SimGlobals.ENEMYTYPE_TIME, 0f, 0f, 0f, 0f)
    }

    fun appSuicide(playerIndex: Int) {
        killPlayer(players[playerIndex], SimGlobals.ENEMYTYPE_SUICIDE, 0f, 0f, 0f, 0f)
    }

    fun appIsPlaybackFinished(): Boolean =
        players.all { it.inputSource.isReplayFinished }

    fun appGetReplayStrings(): List<String> =
        players.map { it.inputSource.dumpReplayString() }

    // -----------------------------------------------------------------------
    // Event_ interface — called by entities during the tick
    // -----------------------------------------------------------------------

    fun eventPlayerWasKilled(
        ninja: Ninja, enemyType: Int,
        x: Float, y: Float, forceX: Float, forceY: Float
    ) = killPlayer(ninja, enemyType, x, y, forceX, forceY)

    fun eventLaunchpadHitPlayer(ninja: Ninja, dirX: Float, dirY: Float) {
        ninja.simLaunch(dirX, dirY)
    }

    fun eventExitHitPlayer(ninja: Ninja) {
        wonFlag = true
        for (p in players) p.simWin()
    }

    /** Returns false if the level is already won (gold shouldn't register then). */
    fun eventGoldHitPlayer(ninja: Ninja): Boolean {
        if (wonFlag) return false
        goldCollectedThisTick[ninja.playerIndex]++
        return true
    }

    // -----------------------------------------------------------------------
    // DEBUG helpers (no rendering — those live in GameRenderer)
    // -----------------------------------------------------------------------

    fun debugSetAllPosVel(pos: Vec2, vel: Vec2) {
        for (p in players) p.debugSetPosVel(pos, vel)
    }

    fun debugRespawnPlayer(playerIndex: Int) {
        val p = players[playerIndex]
        if (p.isDead()) p.debugRespawn(p.getPos())
    }

    fun debugKillWithForce(playerIndex: Int, origin: Vec2) {
        val p = players[playerIndex]
        if (!p.isDead()) {
            val pos  = p.getPos()
            val dir  = origin.to(pos)
            val dist = dir.len().coerceAtMost(10f)
            dir.normalize()
            p.simKill(SimGlobals.ENEMYTYPE_DEBUG,
                pos.x - dir.x * p.r, pos.y - dir.y * p.r,
                dir.x * dist, dir.y * dist)
        }
    }

    fun debugToggleExploded(playerIndex: Int) {
        val p = players[playerIndex]
        if (p.isDead()) {
            if (p.ragdoll.isExploded) p.ragdoll.unexplode()
            else                      p.ragdoll.explode(this)
        }
    }

    fun debugGrabRagdoll(playerIndex: Int, pos: Vec2, vel: Vec2) {
        val p = players[playerIndex]
        if (p.isDead()) p.ragdoll.testingSetPosVel(pos, vel)
    }

    // -----------------------------------------------------------------------
    // Renderer data accessors (read-only views, no libGDX dependency here)
    // -----------------------------------------------------------------------

    /** Read-only entity list for the renderer. */
    fun entityList(): List<EntityBase> = entities

    // -----------------------------------------------------------------------
    // Particle + sound hooks (open — GameScreen overrides with real impls)
    // -----------------------------------------------------------------------

    // --- Entity-specific particle and sound hooks ---

    open fun spawnExplosion(x: Float, y: Float) {}
    open fun spawnZap(x: Float, y: Float, angleDeg: Float) {}
    open fun spawnZapThwompH(x: Float, y: Float, vx: Float, vy: Float) {}
    open fun spawnZapThwompV(x: Float, y: Float, vx: Float, vy: Float) {}
    open fun spawnRocketSmoke(x: Float, y: Float, angleDeg: Float) {}
    open fun spawnTurretBullet(fromX: Float, fromY: Float, toX: Float, toY: Float) {}
    open fun spawnChainBullet(fromX: Float, fromY: Float, toX: Float, toY: Float) {}
    open fun spawnLaserCharge(x: Float, y: Float) {}
    open fun playSoundGold() {}

    open fun spawnJumpDust(x: Float, y: Float, angle: Float) {}
    open fun spawnLandDust(x: Float, y: Float, angle: Float, speed: Float) {}
    open fun spawnWallDust(pos: Vec2, r: Float, wallNormal: Vec2, speed: Float) {}
    open fun spawnFloorDust(pos: Vec2, r: Float, floorNormal: Vec2, angle: Float, dir: Float, speed: Float) {}
    open fun spawnBloodSpurt(x: Float, y: Float, vx: Float, vy: Float, count: Int) {}
    open fun spawnRagBloodSpurt(x: Float, y: Float, vx: Float, vy: Float) {}
    open fun spawnRagDust(pos: Vec2, r: Float, vx: Float, vy: Float, scale: Float) {}
    open fun playSoundRagdoll(name: String) {}

    // -----------------------------------------------------------------------
    // Private
    // -----------------------------------------------------------------------

    private fun killPlayer(
        ninja: Ninja, enemyType: Int,
        x: Float, y: Float, fx: Float, fy: Float
    ) {
        ninja.simKill(enemyType, x, y, fx, fy)
    }

    private fun areAllPlayersDead() = players.all { it.isDead() }
}
